package vn.trainocate.moneytransfer.limit.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.limit.dto.request.LimitCheckRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitConsumeRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitReleaseRequest;
import vn.trainocate.moneytransfer.limit.dto.response.LimitCheckResponse;
import vn.trainocate.moneytransfer.limit.dto.response.LimitConsumeResponse;
import vn.trainocate.moneytransfer.limit.exception.BusinessException;
import vn.trainocate.moneytransfer.limit.service.LimitService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCommandConsumer {

    private static final String REPLY_TOPIC = "saga-limit-reply";

    private final LimitService limitService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "saga-limit-command", groupId = "limit-service-saga",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            SagaCommand command = objectMapper.readValue(message, SagaCommand.class);
            log.info("[SAGA] Received limit command: sagaId={}, step={}, action={}",
                    command.getSagaId(), command.getStepName(), command.getAction());

            SagaReply reply = handleCommand(command);
            String replyJson = objectMapper.writeValueAsString(reply);
            kafkaTemplate.send(REPLY_TOPIC, command.getSagaId(), replyJson);

            log.info("[SAGA] Sent limit reply: sagaId={}, step={}, status={}",
                    reply.getSagaId(), reply.getStepName(), reply.getStatus());
        } catch (Exception e) {
            log.error("[SAGA] Failed to process limit command: {}", message, e);
            trySendErrorReply(message, e);
        } finally {
            ack.acknowledge();
        }
    }

    private SagaReply handleCommand(SagaCommand command) {
        return switch (command.getStepName()) {
            case "LIMIT_CHECK"   -> handleLimitCheck(command);
            case "CONSUME_LIMIT" -> handleLimitConsume(command);
            case "RELEASE_LIMIT" -> handleLimitRelease(command);
            default -> SagaReply.builder()
                    .sagaId(command.getSagaId())
                    .txId(command.getTxId())
                    .stepName(command.getStepName())
                    .status("FAILED")
                    .errorCode("UNKNOWN_STEP")
                    .errorMessage("Unknown step: " + command.getStepName())
                    .build();
        };
    }

    // ── Step Handlers ─────────────────────────────────────────────────────────

    private SagaReply handleLimitCheck(SagaCommand command) {
        try {
            Map<String, Object> payload = command.getPayload();
            String accountNo = (String) payload.get("accountNo");
            BigDecimal amount = parseBigDecimal(payload.get("amount"));
            String transferType = (String) payload.getOrDefault("transferType", "INTERNAL");

            LimitCheckResponse response = limitService.limitCheck(
                    LimitCheckRequest.builder()
                            .accountNo(accountNo)
                            .amount(amount)
                            .transferType(transferType)
                            .build());

            if (!response.isAllowed()) {
                return failReply(command, "LIMIT_EXCEEDED",
                        response.getReason() != null ? response.getReason() : "Transfer limit exceeded");
            }

            Map<String, Object> replyPayload = new HashMap<>();
            replyPayload.put("remainingDaily", response.getRemainingDaily());
            replyPayload.put("remainingMonthly", response.getRemainingMonthly());

            return successReply(command, replyPayload);

        } catch (BusinessException e) {
            return failReply(command, e.getCode(), e.getMessage());
        } catch (Exception e) {
            return failReply(command, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private SagaReply handleLimitConsume(SagaCommand command) {
        try {
            Map<String, Object> payload = command.getPayload();
            String accountNo = (String) payload.get("accountNo");
            BigDecimal amount = parseBigDecimal(payload.get("amount"));
            String transferType = (String) payload.getOrDefault("transferType", "INTERNAL");
            String txId = (String) payload.get("txId");

            LimitConsumeResponse response = limitService.limitConsume(
                    new LimitConsumeRequest(accountNo, amount, transferType, txId));

            Map<String, Object> replyPayload = new HashMap<>();
            replyPayload.put("remainingDaily", response.getRemainingDaily());
            replyPayload.put("remainingMonthly", response.getRemainingMonthly());
            replyPayload.put("status", response.getStatus());

            return successReply(command, replyPayload);

        } catch (BusinessException e) {
            return failReply(command, e.getCode(), e.getMessage());
        } catch (Exception e) {
            return failReply(command, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private SagaReply handleLimitRelease(SagaCommand command) {
        try {
            Map<String, Object> payload = command.getPayload();
            String accountNo = (String) payload.get("accountNo");
            BigDecimal amount = parseBigDecimal(payload.get("amount"));
            String transferType = (String) payload.getOrDefault("transferType", "INTERNAL");
            String txId = (String) payload.get("txId");

            LimitReleaseRequest request = new LimitReleaseRequest();
            request.setAccountNo(accountNo);
            request.setAmount(amount);
            request.setTransferType(transferType);
            request.setTxId(txId);
            limitService.limitRelease(request);

            return successReply(command, Map.of("status", "RELEASED"));

        } catch (BusinessException e) {
            return failReply(command, e.getCode(), e.getMessage());
        } catch (Exception e) {
            return failReply(command, "INTERNAL_ERROR", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SagaReply successReply(SagaCommand command, Map<String, Object> payload) {
        return SagaReply.builder()
                .sagaId(command.getSagaId())
                .txId(command.getTxId())
                .stepName(command.getStepName())
                .status("SUCCESS")
                .payload(payload)
                .build();
    }

    private SagaReply failReply(SagaCommand command, String errorCode, String errorMessage) {
        return SagaReply.builder()
                .sagaId(command.getSagaId())
                .txId(command.getTxId())
                .stepName(command.getStepName())
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private void trySendErrorReply(String message, Exception error) {
        try {
            SagaCommand command = objectMapper.readValue(message, SagaCommand.class);
            SagaReply reply = SagaReply.builder()
                    .sagaId(command.getSagaId())
                    .txId(command.getTxId())
                    .stepName(command.getStepName())
                    .status("FAILED")
                    .errorCode("INTERNAL_ERROR")
                    .errorMessage(error.getMessage())
                    .build();
            kafkaTemplate.send(REPLY_TOPIC, command.getSagaId(), objectMapper.writeValueAsString(reply));
        } catch (Exception ignored) {
            log.error("[SAGA] Failed to send error reply", ignored);
        }
    }
}
