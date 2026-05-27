package vn.trainocate.moneytransfer.transaction.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.transaction.client.AccountClient;
import vn.trainocate.moneytransfer.transaction.client.KycClient;
import vn.trainocate.moneytransfer.transaction.client.LimitClient;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;
import vn.trainocate.moneytransfer.transaction.dto.request.TransferRequest;
import vn.trainocate.moneytransfer.transaction.dto.response.TransactionResponse;
import vn.trainocate.moneytransfer.transaction.entity.SagaStateEntity;
import vn.trainocate.moneytransfer.transaction.entity.SagaStepEntity;
import vn.trainocate.moneytransfer.transaction.exception.BusinessException;
import vn.trainocate.moneytransfer.transaction.repository.SagaStateRepository;
import vn.trainocate.moneytransfer.transaction.repository.SagaStepRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final AccountClient accountClient;
    private final KycClient kycClient;
    private final LimitClient limitClient;
    private final SagaStateRepository sagaStateRepository;
    private final SagaStepRepository sagaStepRepository;
    private final ObjectMapper objectMapper;

    /**
     * Execute the Internal Transfer Saga.
     * Steps 1-3 are read-only (no compensation).
     * Steps 4-5 are compensable (debit + limit consume).
     * Step 6 is the terminal success step.
     *
     * On any failure from step 4 onward, compensating transactions run in reverse.
     */
    @Transactional
    public Map<String, Object> executeInternalTransferSaga(TransactionResponse tx, TransferRequest request) {
        log.info("[SAGA] Starting INTERNAL_TRANSFER saga: txId={}, referenceId={}", tx.getTxId(), request.getReferenceId());

        SagaStateEntity saga = sagaStateRepository.save(
                SagaStateEntity.builder()
                        .txId(tx.getTxId())
                        .sagaType("INTERNAL_TRANSFER")
                        .status(SagaStatus.RUNNING)
                        .build());

        // Track compensable steps in a stack for easy reverse execution
        Deque<SagaStepName> compensationStack = new ArrayDeque<>();

        try {
            // ── Step 1: KYC Check ──────────────────────────────────────────
            Map<String, Object> kycData = executeStep(saga, SagaStepName.KYC_CHECK, () -> {
                Map<String, Object> resp = extractData(
                        kycClient.getKycStatus(Map.of("accountNo", request.getSenderAccountNo())));
                String status = String.valueOf(resp.get("status"));
                if (!"VERIFIED".equals(status)) {
                    throw new BusinessException("KYC_NOT_VERIFIED", "KYC not verified for sender account");
                }
                return resp;
            });
            log.info("[SAGA] Step KYC_CHECK passed: txId={}", tx.getTxId());

            // ── Step 2: Limit Check ────────────────────────────────────────
            executeStep(saga, SagaStepName.LIMIT_CHECK, () -> {
                Map<String, Object> resp = extractData(
                        limitClient.limitCheck(Map.of(
                                "accountNo", request.getSenderAccountNo(),
                                "amount", request.getAmount(),
                                "transferType", "INTERNAL")));
                Boolean allowed = (Boolean) resp.get("allowed");
                if (allowed == null || !allowed) {
                    String reason = String.valueOf(resp.getOrDefault("reason", "Transfer limit exceeded"));
                    throw new BusinessException("LIMIT_EXCEEDED", reason);
                }
                return resp;
            });
            log.info("[SAGA] Step LIMIT_CHECK passed: txId={}", tx.getTxId());

            // ── Step 3: Balance Check ──────────────────────────────────────
            executeStep(saga, SagaStepName.BALANCE_CHECK, () -> {
                Map<String, Object> resp = extractData(
                        accountClient.checkBalance(Map.of("accountNo", request.getSenderAccountNo())));
                BigDecimal available = new BigDecimal(String.valueOf(resp.get("availableBalance")));
                if (available.compareTo(request.getAmount()) < 0) {
                    throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance");
                }
                return resp;
            });
            log.info("[SAGA] Step BALANCE_CHECK passed: txId={}", tx.getTxId());

            // ── Step 4: Debit Sender ───────────────────────────────────────
            executeStep(saga, SagaStepName.DEBIT_SENDER, () ->
                    extractData(accountClient.debit(Map.of(
                            "accountNo", request.getSenderAccountNo(),
                            "amount", request.getAmount(),
                            "referenceId", request.getReferenceId(),
                            "description", "Transfer to " + request.getReceiverAccountNo()))));
            compensationStack.push(SagaStepName.DEBIT_SENDER); // register for potential compensation
            log.info("[SAGA] Step DEBIT_SENDER completed: txId={}, sender={}, amount={}",
                    tx.getTxId(), request.getSenderAccountNo(), request.getAmount());

            // ── Step 5: Consume Limit ──────────────────────────────────────
            executeStep(saga, SagaStepName.CONSUME_LIMIT, () ->
                    extractData(limitClient.limitConsume(Map.of(
                            "accountNo", request.getSenderAccountNo(),
                            "amount", request.getAmount(),
                            "transferType", "INTERNAL",
                            "txId", tx.getTxId().toString()))));
            compensationStack.push(SagaStepName.CONSUME_LIMIT);
            log.info("[SAGA] Step CONSUME_LIMIT completed: txId={}", tx.getTxId());

            // ── Step 6: Credit Receiver (terminal) ────────────────────────
            Map<String, Object> creditData = executeStep(saga, SagaStepName.CREDIT_RECEIVER, () -> {
                Map<String, Object> creditReq = new HashMap<>();
                creditReq.put("accountNo", request.getReceiverAccountNo());
                creditReq.put("amount", request.getAmount());
                creditReq.put("referenceId", request.getReferenceId());
                creditReq.put("description", request.getDescription() != null
                        ? request.getDescription() : "Transfer from " + request.getSenderAccountNo());
                return extractData(accountClient.credit(creditReq));
            });
            log.info("[SAGA] Step CREDIT_RECEIVER completed: txId={}, receiver={}",
                    tx.getTxId(), request.getReceiverAccountNo());

            // ── Saga completed ─────────────────────────────────────────────
            saga.setStatus(SagaStatus.COMPLETED);
            sagaStateRepository.save(saga);
            log.info("[SAGA] INTERNAL_TRANSFER saga COMPLETED: txId={}", tx.getTxId());

            Map<String, Object> result = new HashMap<>();
            result.put("referenceId", request.getReferenceId());
            result.put("status", "COMPLETED");
            result.put("sagaId", saga.getSagaId().toString());
            result.put("receiverName", creditData.getOrDefault("fullName", request.getReceiverAccountNo()));
            return result;

        } catch (Exception e) {
            log.error("[SAGA] INTERNAL_TRANSFER saga FAILED at step, starting compensation: txId={}, error={}",
                    tx.getTxId(), e.getMessage());

            String failedStep = compensationStack.isEmpty() ? "READ_STEP" : "COMPENSABLE_STEP";
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.setFailedStep(failedStep);
            saga.setFailureReason(e.getMessage());
            sagaStateRepository.save(saga);

            // Compensate in reverse order
            runCompensations(saga, compensationStack, request, tx.getTxId().toString());

            // Re-throw original exception for the caller to handle
            if (e instanceof BusinessException) throw e;
            throw new BusinessException("SAGA_FAILED", "Transfer failed: " + e.getMessage());
        }
    }

    // ── Compensation Logic ─────────────────────────────────────────────────────

    private void runCompensations(SagaStateEntity saga, Deque<SagaStepName> stack,
                                   TransferRequest request, String txId) {
        boolean allCompensated = true;

        while (!stack.isEmpty()) {
            SagaStepName step = stack.pop();
            try {
                compensate(step, request, txId);
                recordStep(saga, mapToCompensationStep(step), "COMPENSATED", null);
                log.info("[SAGA] Compensation {} done: txId={}", step, txId);
            } catch (Exception ex) {
                allCompensated = false;
                recordStep(saga, mapToCompensationStep(step), "FAILED", ex.getMessage());
                log.error("[SAGA] Compensation {} FAILED: txId={}, error={}", step, txId, ex.getMessage());
            }
        }

        saga.setStatus(allCompensated ? SagaStatus.COMPENSATED : SagaStatus.COMPENSATION_FAILED);
        sagaStateRepository.save(saga);

        if (allCompensated) {
            log.info("[SAGA] All compensations completed: txId={}", txId);
        } else {
            log.error("[SAGA] Some compensations FAILED — manual intervention required: txId={}", txId);
        }
    }

    private void compensate(SagaStepName step, TransferRequest request, String txId) {
        switch (step) {
            case DEBIT_SENDER -> {
                // Refund: credit the sender back
                accountClient.credit(Map.of(
                        "accountNo", request.getSenderAccountNo(),
                        "amount", request.getAmount(),
                        "referenceId", "REFUND-" + request.getReferenceId(),
                        "description", "Refund for failed transfer " + request.getReferenceId()));
                log.info("[SAGA] Refunded sender {}: amount={}", request.getSenderAccountNo(), request.getAmount());
            }
            case CONSUME_LIMIT -> {
                // Release limit
                limitClient.limitRelease(Map.of(
                        "accountNo", request.getSenderAccountNo(),
                        "amount", request.getAmount(),
                        "transferType", "INTERNAL",
                        "txId", txId));
                log.info("[SAGA] Limit released for {}: amount={}", request.getSenderAccountNo(), request.getAmount());
            }
            default -> log.warn("[SAGA] No compensation defined for step {}", step);
        }
    }

    private SagaStepName mapToCompensationStep(SagaStepName step) {
        return switch (step) {
            case DEBIT_SENDER -> SagaStepName.REFUND_SENDER;
            case CONSUME_LIMIT -> SagaStepName.RELEASE_LIMIT;
            default -> step;
        };
    }

    // ── Helper: execute a step, persist result ─────────────────────────────────

    @FunctionalInterface
    interface StepAction {
        Map<String, Object> execute() throws Exception;
    }

    private Map<String, Object> executeStep(SagaStateEntity saga, SagaStepName stepName, StepAction action) {
        try {
            Map<String, Object> result = action.execute();
            recordStep(saga, stepName, "COMPLETED", null);
            return result;
        } catch (BusinessException e) {
            recordStep(saga, stepName, "FAILED", e.getMessage());
            throw e;
        } catch (Exception e) {
            recordStep(saga, stepName, "FAILED", e.getMessage());
            throw new BusinessException("STEP_FAILED", stepName + " failed: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStep(SagaStateEntity saga, SagaStepName stepName, String status, String errorMsg) {
        sagaStepRepository.save(SagaStepEntity.builder()
                .saga(saga)
                .stepName(stepName)
                .status(status)
                .errorMessage(errorMsg)
                .executedAt(LocalDateTime.now())
                .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(ApiResponse response) {
        if (response == null || !response.isSuccess()) {
            String code = response != null ? response.getCode() : "SERVICE_ERROR";
            String message = response != null ? response.getMessage() : "Service unavailable";
            throw new BusinessException(code, message);
        }
        return objectMapper.convertValue(response.getData(), Map.class);
    }
}
