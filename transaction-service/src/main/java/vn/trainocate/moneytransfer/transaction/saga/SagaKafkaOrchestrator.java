package vn.trainocate.moneytransfer.transaction.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.transaction.dto.request.TransferRequest;
import vn.trainocate.moneytransfer.transaction.dto.response.TransactionResponse;
import vn.trainocate.moneytransfer.transaction.entity.SagaStateEntity;
import vn.trainocate.moneytransfer.transaction.entity.SagaStepEntity;
import vn.trainocate.moneytransfer.transaction.exception.BusinessException;
import vn.trainocate.moneytransfer.transaction.repository.SagaStateRepository;
import vn.trainocate.moneytransfer.transaction.repository.SagaStepRepository;
import vn.trainocate.moneytransfer.transaction.saga.dto.SagaCommand;
import vn.trainocate.moneytransfer.transaction.saga.dto.SagaReply;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SagaKafkaOrchestrator {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SagaStateRepository sagaStateRepository;
    private final SagaStepRepository sagaStepRepository;

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pendingFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SagaContext> sagaContexts = new ConcurrentHashMap<>();

    // ── Step Definitions ──────────────────────────────────────────────────────

    record StepDef(SagaStepName name, String topic, boolean compensable) {}

    private static final List<StepDef> FORWARD_STEPS = List.of(
            new StepDef(SagaStepName.KYC_CHECK,      "saga-kyc-command",     false),
            new StepDef(SagaStepName.LIMIT_CHECK,     "saga-limit-command",   false),
            new StepDef(SagaStepName.BALANCE_CHECK,   "saga-account-command", false),
            new StepDef(SagaStepName.DEBIT_SENDER,    "saga-account-command", true),
            new StepDef(SagaStepName.CONSUME_LIMIT,   "saga-limit-command",   true),
            new StepDef(SagaStepName.CREDIT_RECEIVER, "saga-account-command", false)
    );

    private static final Map<SagaStepName, StepDef> COMPENSATION_MAP = Map.of(
            SagaStepName.DEBIT_SENDER,  new StepDef(SagaStepName.REFUND_SENDER,  "saga-account-command", false),
            SagaStepName.CONSUME_LIMIT, new StepDef(SagaStepName.RELEASE_LIMIT,  "saga-limit-command",   false)
    );

    public SagaKafkaOrchestrator(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper,
                                  SagaStateRepository sagaStateRepository,
                                  SagaStepRepository sagaStepRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sagaStateRepository = sagaStateRepository;
        this.sagaStepRepository = sagaStepRepository;
    }

    // ── Start Saga ────────────────────────────────────────────────────────────

    public CompletableFuture<Map<String, Object>> startSaga(TransactionResponse tx, TransferRequest request) {
        SagaStateEntity saga = sagaStateRepository.save(
                SagaStateEntity.builder()
                        .txId(tx.getTxId())
                        .sagaType("INTERNAL_TRANSFER")
                        .status(SagaStatus.RUNNING)
                        .build());

        String sagaId = saga.getSagaId().toString();

        SagaContext ctx = new SagaContext(sagaId, tx.getTxId().toString(), saga.getSagaId(), request);
        sagaContexts.put(sagaId, ctx);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingFutures.put(sagaId, future);

        log.info("[SAGA] Started INTERNAL_TRANSFER saga via Kafka: sagaId={}, txId={}", sagaId, tx.getTxId());

        publishNextCommand(ctx);

        return future;
    }

    // ── Kafka Reply Listeners ─────────────────────────────────────────────────

    @KafkaListener(topics = "saga-kyc-reply", groupId = "transaction-service-saga",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onKycReply(String message, Acknowledgment ack) {
        processReply(message, ack);
    }

    @KafkaListener(topics = "saga-account-reply", groupId = "transaction-service-saga",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onAccountReply(String message, Acknowledgment ack) {
        processReply(message, ack);
    }

    @KafkaListener(topics = "saga-limit-reply", groupId = "transaction-service-saga",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onLimitReply(String message, Acknowledgment ack) {
        processReply(message, ack);
    }

    // ── Reply Processing (State Machine) ──────────────────────────────────────

    private void processReply(String message, Acknowledgment ack) {
        try {
            SagaReply reply = objectMapper.readValue(message, SagaReply.class);
            String sagaId = reply.getSagaId();

            SagaContext ctx = sagaContexts.get(sagaId);
            if (ctx == null) {
                log.warn("[SAGA] Received reply for unknown/completed saga: sagaId={}", sagaId);
                return;
            }

            log.info("[SAGA] Received reply: sagaId={}, step={}, status={}", sagaId, reply.getStepName(), reply.getStatus());

            if (ctx.isCompensating()) {
                handleCompensationReply(ctx, reply);
            } else {
                handleForwardReply(ctx, reply);
            }
        } catch (Exception e) {
            log.error("[SAGA] Failed to process reply: {}", message, e);
        } finally {
            ack.acknowledge();
        }
    }

    private void handleForwardReply(SagaContext ctx, SagaReply reply) {
        SagaStepName stepName = SagaStepName.valueOf(reply.getStepName());

        if ("SUCCESS".equals(reply.getStatus())) {
            recordStep(ctx.getSagaEntityId(), stepName, "COMPLETED", null);

            StepDef currentStep = FORWARD_STEPS.get(ctx.getCurrentStepIndex());
            if (currentStep.compensable()) {
                ctx.getCompensationStack().push(currentStep.name());
            }

            if (reply.getPayload() != null) {
                ctx.getStepResults().put(reply.getStepName(), reply.getPayload());
            }

            ctx.setCurrentStepIndex(ctx.getCurrentStepIndex() + 1);

            if (ctx.getCurrentStepIndex() >= FORWARD_STEPS.size()) {
                completeSaga(ctx, SagaStatus.COMPLETED, reply);
            } else {
                publishNextCommand(ctx);
            }
        } else {
            recordStep(ctx.getSagaEntityId(), stepName, "FAILED", reply.getErrorMessage());
            startCompensation(ctx, stepName.name(), reply.getErrorMessage());
        }
    }

    private void handleCompensationReply(SagaContext ctx, SagaReply reply) {
        SagaStepName stepName = SagaStepName.valueOf(reply.getStepName());
        boolean success = "SUCCESS".equals(reply.getStatus());

        recordStep(ctx.getSagaEntityId(), stepName,
                success ? "COMPENSATED" : "FAILED",
                success ? null : reply.getErrorMessage());

        if (!success) {
            ctx.setCompensationFailed(true);
            log.error("[SAGA] Compensation step {} FAILED: sagaId={}", stepName, ctx.getSagaId());
        }

        ctx.setCompensationIndex(ctx.getCompensationIndex() + 1);

        if (ctx.getCompensationIndex() < ctx.getToCompensate().size()) {
            publishCompensationCommand(ctx);
        } else {
            SagaStatus finalStatus = ctx.isCompensationFailed()
                    ? SagaStatus.COMPENSATION_FAILED
                    : SagaStatus.COMPENSATED;
            completeSaga(ctx, finalStatus, null);
        }
    }

    // ── Compensation ──────────────────────────────────────────────────────────

    private void startCompensation(SagaContext ctx, String failedStepName, String errorMessage) {
        ctx.setCompensating(true);

        SagaStateEntity saga = sagaStateRepository.findById(ctx.getSagaEntityId()).orElse(null);
        if (saga != null) {
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.setFailedStep(failedStepName);
            saga.setFailureReason(errorMessage);
            sagaStateRepository.save(saga);
        }

        List<SagaStepName> toCompensate = new ArrayList<>();
        Deque<SagaStepName> stack = ctx.getCompensationStack();
        while (!stack.isEmpty()) {
            toCompensate.add(stack.pop());
        }
        ctx.setToCompensate(toCompensate);
        ctx.setCompensationIndex(0);

        if (toCompensate.isEmpty()) {
            log.info("[SAGA] No compensable steps to undo: sagaId={}", ctx.getSagaId());
            completeSaga(ctx, SagaStatus.COMPENSATED, null);
            return;
        }

        log.info("[SAGA] Starting compensation for {} steps: sagaId={}, steps={}",
                toCompensate.size(), ctx.getSagaId(), toCompensate);

        publishCompensationCommand(ctx);
    }

    // ── Publish Commands ──────────────────────────────────────────────────────

    private void publishNextCommand(SagaContext ctx) {
        StepDef step = FORWARD_STEPS.get(ctx.getCurrentStepIndex());
        Map<String, Object> payload = buildPayload(step.name(), ctx);

        SagaCommand command = SagaCommand.builder()
                .sagaId(ctx.getSagaId())
                .txId(ctx.getTxId())
                .stepName(step.name().name())
                .action("EXECUTE")
                .payload(payload)
                .build();

        sendCommand(step.topic(), ctx.getSagaId(), command);
        log.info("[SAGA] Published command: sagaId={}, step={}, topic={}", ctx.getSagaId(), step.name(), step.topic());
    }

    private void publishCompensationCommand(SagaContext ctx) {
        SagaStepName originalStep = ctx.getToCompensate().get(ctx.getCompensationIndex());
        StepDef compensationDef = COMPENSATION_MAP.get(originalStep);
        if (compensationDef == null) {
            log.warn("[SAGA] No compensation defined for step {}", originalStep);
            ctx.setCompensationIndex(ctx.getCompensationIndex() + 1);
            if (ctx.getCompensationIndex() < ctx.getToCompensate().size()) {
                publishCompensationCommand(ctx);
            } else {
                completeSaga(ctx, ctx.isCompensationFailed() ? SagaStatus.COMPENSATION_FAILED : SagaStatus.COMPENSATED, null);
            }
            return;
        }

        Map<String, Object> payload = buildPayload(compensationDef.name(), ctx);

        SagaCommand command = SagaCommand.builder()
                .sagaId(ctx.getSagaId())
                .txId(ctx.getTxId())
                .stepName(compensationDef.name().name())
                .action("COMPENSATE")
                .payload(payload)
                .build();

        sendCommand(compensationDef.topic(), ctx.getSagaId(), command);
        log.info("[SAGA] Published compensation command: sagaId={}, step={}, topic={}",
                ctx.getSagaId(), compensationDef.name(), compensationDef.topic());
    }

    private void sendCommand(String topic, String key, SagaCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(topic, key, json);
        } catch (JsonProcessingException e) {
            log.error("[SAGA] Failed to serialize command: {}", command, e);
            throw new RuntimeException("Failed to serialize saga command", e);
        }
    }

    // ── Payload Builder ───────────────────────────────────────────────────────

    private Map<String, Object> buildPayload(SagaStepName stepName, SagaContext ctx) {
        TransferRequest req = ctx.getRequest();
        Map<String, Object> payload = new HashMap<>();

        switch (stepName) {
            case KYC_CHECK -> payload.put("accountNo", req.getSenderAccountNo());

            case LIMIT_CHECK -> {
                payload.put("accountNo", req.getSenderAccountNo());
                payload.put("amount", req.getAmount());
                payload.put("transferType", "INTERNAL");
            }

            case BALANCE_CHECK -> {
                payload.put("accountNo", req.getSenderAccountNo());
                payload.put("amount", req.getAmount());
            }

            case DEBIT_SENDER -> {
                payload.put("accountNo", req.getSenderAccountNo());
                payload.put("amount", req.getAmount());
                payload.put("referenceId", req.getReferenceId());
                payload.put("description", "Transfer to " + req.getReceiverAccountNo());
            }

            case CONSUME_LIMIT -> {
                payload.put("accountNo", req.getSenderAccountNo());
                payload.put("amount", req.getAmount());
                payload.put("transferType", "INTERNAL");
                payload.put("txId", ctx.getTxId());
            }

            case CREDIT_RECEIVER -> {
                payload.put("accountNo", req.getReceiverAccountNo());
                payload.put("amount", req.getAmount());
                payload.put("referenceId", req.getReferenceId());
                payload.put("description", req.getDescription() != null
                        ? req.getDescription() : "Transfer from " + req.getSenderAccountNo());
            }

            case REFUND_SENDER -> {
                payload.put("accountNo", req.getSenderAccountNo());
                payload.put("amount", req.getAmount());
                payload.put("referenceId", "REFUND-" + req.getReferenceId());
                payload.put("description", "Refund for failed transfer " + req.getReferenceId());
            }

            case RELEASE_LIMIT -> {
                payload.put("accountNo", req.getSenderAccountNo());
                payload.put("amount", req.getAmount());
                payload.put("transferType", "INTERNAL");
                payload.put("txId", ctx.getTxId());
            }
        }

        return payload;
    }

    // ── Complete / Fail Saga ──────────────────────────────────────────────────

    private void completeSaga(SagaContext ctx, SagaStatus status, SagaReply lastReply) {
        SagaStateEntity saga = sagaStateRepository.findById(ctx.getSagaEntityId()).orElse(null);
        if (saga != null) {
            saga.setStatus(status);
            sagaStateRepository.save(saga);
        }

        CompletableFuture<Map<String, Object>> future = pendingFutures.remove(ctx.getSagaId());
        sagaContexts.remove(ctx.getSagaId());

        if (future == null) return;

        if (status == SagaStatus.COMPLETED) {
            Map<String, Object> result = new HashMap<>();
            result.put("referenceId", ctx.getRequest().getReferenceId());
            result.put("status", "COMPLETED");
            result.put("sagaId", ctx.getSagaId());

            Map<String, Object> creditResult = ctx.getStepResults().get("CREDIT_RECEIVER");
            if (creditResult != null) {
                result.put("receiverName", creditResult.getOrDefault("fullName", ctx.getRequest().getReceiverAccountNo()));
            }

            future.complete(result);
            log.info("[SAGA] Saga COMPLETED: sagaId={}, txId={}", ctx.getSagaId(), ctx.getTxId());
        } else {
            String errorMsg = String.format("Transfer saga %s for txId=%s", status.name().toLowerCase(), ctx.getTxId());
            future.completeExceptionally(new BusinessException("SAGA_" + status.name(), errorMsg));
            log.warn("[SAGA] Saga {}: sagaId={}, txId={}", status, ctx.getSagaId(), ctx.getTxId());
        }
    }

    // ── Stale Saga Cleanup ────────────────────────────────────────────────────

    public void cleanupStale(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        List<String> staleIds = new ArrayList<>();

        sagaContexts.forEach((sagaId, ctx) -> {
            if (ctx.getCreatedAt().isBefore(cutoff)) {
                staleIds.add(sagaId);
            }
        });

        for (String sagaId : staleIds) {
            SagaContext ctx = sagaContexts.remove(sagaId);
            CompletableFuture<Map<String, Object>> future = pendingFutures.remove(sagaId);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(
                        new BusinessException("SAGA_TIMEOUT", "Saga timed out: sagaId=" + sagaId));
            }
            if (ctx != null) {
                log.warn("[SAGA] Cleaned up stale saga: sagaId={}, txId={}", sagaId, ctx.getTxId());
            }
        }
    }

    // ── Persist Step ──────────────────────────────────────────────────────────

    public void recordStep(UUID sagaEntityId, SagaStepName stepName, String status, String errorMsg) {
        SagaStateEntity saga = sagaStateRepository.getReferenceById(sagaEntityId);
        sagaStepRepository.save(SagaStepEntity.builder()
                .saga(saga)
                .stepName(stepName)
                .status(status)
                .errorMessage(errorMsg)
                .executedAt(LocalDateTime.now())
                .build());
    }

    // ── Saga Context (in-memory tracking per saga) ────────────────────────────

    @lombok.Data
    static class SagaContext {
        private final String sagaId;
        private final String txId;
        private final UUID sagaEntityId;
        private final TransferRequest request;
        private final Instant createdAt = Instant.now();
        private final Deque<SagaStepName> compensationStack = new ArrayDeque<>();
        private final Map<String, Map<String, Object>> stepResults = new HashMap<>();

        private int currentStepIndex = 0;
        private boolean compensating = false;
        private boolean compensationFailed = false;
        private List<SagaStepName> toCompensate = new ArrayList<>();
        private int compensationIndex = 0;
    }
}
