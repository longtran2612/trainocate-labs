package vn.trainocate.moneytransfer.account.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.account.command.AccountCommandService;
import vn.trainocate.moneytransfer.account.dto.request.CheckBalanceRequest;
import vn.trainocate.moneytransfer.account.dto.request.CreditRequest;
import vn.trainocate.moneytransfer.account.dto.request.DebitRequest;
import vn.trainocate.moneytransfer.account.dto.response.BalanceResponse;
import vn.trainocate.moneytransfer.account.dto.response.DebitCreditResponse;
import vn.trainocate.moneytransfer.account.entity.AccountEntity;
import vn.trainocate.moneytransfer.account.exception.BusinessException;
import vn.trainocate.moneytransfer.account.query.AccountQueryService;
import vn.trainocate.moneytransfer.account.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kafka consumer for saga account commands (BALANCE_CHECK, DEBIT_SENDER, CREDIT_RECEIVER, REFUND_SENDER).
 *
 * <p>Uses the dedicated {@code sagaKafkaListenerContainerFactory} to isolate from the
 * Debezium CDC consumer (different consumer group: account-service-saga vs account-service-cqrs).
 *
 * <p>Magic account: receiverAccountNo == "FORCE_FAIL" in CREDIT_RECEIVER triggers a
 * simulated failure to demonstrate the saga compensation/rollback flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCommandConsumer {

    private static final String REPLY_TOPIC = "saga-account-reply";
    private static final String FORCE_FAIL_ACCOUNT = "FORCE_FAIL";

    private final AccountCommandService accountCommandService;
    private final AccountQueryService accountQueryService;
    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "saga-account-command", groupId = "account-service-saga",
                   containerFactory = "sagaKafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            SagaCommand command = objectMapper.readValue(message, SagaCommand.class);
            log.info("[SAGA] Received account command: sagaId={}, step={}, action={}",
                    command.getSagaId(), command.getStepName(), command.getAction());

            SagaReply reply = handleCommand(command);
            String replyJson = objectMapper.writeValueAsString(reply);
            kafkaTemplate.send(REPLY_TOPIC, command.getSagaId(), replyJson);

            log.info("[SAGA] Sent account reply: sagaId={}, step={}, status={}",
                    reply.getSagaId(), reply.getStepName(), reply.getStatus());
        } catch (Exception e) {
            log.error("[SAGA] Failed to process account command: {}", message, e);
            trySendErrorReply(message, e);
        } finally {
            ack.acknowledge();
        }
    }

    private SagaReply handleCommand(SagaCommand command) {
        return switch (command.getStepName()) {
            case "BALANCE_CHECK" -> handleBalanceCheck(command);
            case "DEBIT_SENDER"  -> handleDebit(command);
            case "CREDIT_RECEIVER" -> handleCredit(command);
            case "REFUND_SENDER"   -> handleRefund(command);
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

    private SagaReply handleBalanceCheck(SagaCommand command) {
        try {
            Map<String, Object> payload = command.getPayload();
            String accountNo = (String) payload.get("accountNo");
            BigDecimal requiredAmount = parseBigDecimal(payload.get("amount"));

            BalanceResponse balance = accountQueryService.checkBalance(
                    CheckBalanceRequest.builder().accountNo(accountNo).build());

            if (balance.getAvailableBalance().compareTo(requiredAmount) < 0) {
                return failReply(command, "INSUFFICIENT_BALANCE",
                        "Available balance " + balance.getAvailableBalance() + " is less than required " + requiredAmount);
            }

            Map<String, Object> replyPayload = new HashMap<>();
            replyPayload.put("availableBalance", balance.getAvailableBalance());
            replyPayload.put("balance", balance.getBalance());

            return successReply(command, replyPayload);

        } catch (BusinessException e) {
            return failReply(command, e.getCode(), e.getMessage());
        } catch (Exception e) {
            return failReply(command, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private SagaReply handleDebit(SagaCommand command) {
        try {
            Map<String, Object> payload = command.getPayload();
            String accountNo = (String) payload.get("accountNo");
            BigDecimal amount = parseBigDecimal(payload.get("amount"));
            String referenceId = (String) payload.get("referenceId");

            DebitCreditResponse response = accountCommandService.debit(
                    DebitRequest.builder()
                            .accountNo(accountNo)
                            .amount(amount)
                            .referenceId(referenceId)
                            .build());

            Map<String, Object> replyPayload = new HashMap<>();
            replyPayload.put("txRef", response.getTxRef());
            replyPayload.put("newBalance", response.getNewBalance());

            return successReply(command, replyPayload);

        } catch (BusinessException e) {
            return failReply(command, e.getCode(), e.getMessage());
        } catch (Exception e) {
            return failReply(command, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private SagaReply handleCredit(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        String accountNo = (String) payload.get("accountNo");

        // ── Magic account for rollback test scenario ───────────────────────────
        if (FORCE_FAIL_ACCOUNT.equals(accountNo)) {
            log.warn("[SAGA] FORCE_FAIL triggered for CREDIT_RECEIVER: sagaId={} — simulating failure to test compensation",
                    command.getSagaId());
            return failReply(command, "FORCE_FAIL_TEST",
                    "Simulated failure for rollback testing (accountNo=FORCE_FAIL)");
        }

        try {
            BigDecimal amount = parseBigDecimal(payload.get("amount"));
            String referenceId = (String) payload.get("referenceId");
            String description = (String) payload.get("description");

            DebitCreditResponse response = accountCommandService.credit(
                    CreditRequest.builder()
                            .accountNo(accountNo)
                            .amount(amount)
                            .referenceId(referenceId)
                            .description(description)
                            .build());

            Map<String, Object> replyPayload = new HashMap<>();
            replyPayload.put("txRef", response.getTxRef());
            replyPayload.put("newBalance", response.getNewBalance());

            // Include receiver's full name for the saga result
            Optional<AccountEntity> account = accountRepository.findByAccountNo(accountNo);
            account.ifPresent(a -> replyPayload.put("fullName", a.getFullName()));

            return successReply(command, replyPayload);

        } catch (BusinessException e) {
            return failReply(command, e.getCode(), e.getMessage());
        } catch (Exception e) {
            return failReply(command, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private SagaReply handleRefund(SagaCommand command) {
        try {
            Map<String, Object> payload = command.getPayload();
            String accountNo = (String) payload.get("accountNo");
            BigDecimal amount = parseBigDecimal(payload.get("amount"));
            String referenceId = (String) payload.get("referenceId");
            String description = (String) payload.get("description");

            accountCommandService.credit(
                    CreditRequest.builder()
                            .accountNo(accountNo)
                            .amount(amount)
                            .referenceId(referenceId)
                            .description(description)
                            .build());

            return successReply(command, Map.of("status", "REFUNDED"));

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
