package vn.trainocate.moneytransfer.account.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.account.readmodel.AccountReadModel;
import vn.trainocate.moneytransfer.account.readmodel.AccountRedisRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Debezium CDC consumer — bridges PostgreSQL WAL to the Redis read model.
 *
 * Flow: PostgreSQL WAL → Debezium → Kafka topic account_db.public.accounts
 *       → this consumer → Redis account:{accountNo}
 *
 * Ack policy: only ack AFTER successful Redis update.
 * On failure: throw so Spring Kafka retries via the configured DefaultErrorHandler.
 * Idempotency: skip events whose version <= current Redis version to handle at-least-once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventConsumer {

    private static final String TOPIC = "account_db.public.accounts";

    private final ObjectMapper objectMapper;
    private final AccountRedisRepository accountRedisRepository;

    @KafkaListener(topics = TOPIC, groupId = "account-service-cqrs",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        // Tombstone (DELETE event with ExtractNewRecordState SMT)
        if (message == null || "null".equalsIgnoreCase(message.trim())) {
            log.debug("Received tombstone on topic {}, skipping", TOPIC);
            ack.acknowledge();
            return;
        }

        try {
            DebeziumAccountPayload payload = objectMapper.readValue(message, DebeziumAccountPayload.class);

            if (payload.getAccountNo() == null) {
                log.warn("CDC event missing account_no, skipping: {}", message);
                ack.acknowledge();
                return;
            }

            // Idempotency: skip if incoming version is not newer than what Redis already has
            if (payload.getVersion() != null) {
                Optional<AccountReadModel> current = accountRedisRepository.findByAccountNo(payload.getAccountNo());
                if (current.isPresent() && current.get().getVersion() != null
                        && payload.getVersion() <= current.get().getVersion()) {
                    log.info("Skip old/duplicate CDC event: accountNo={}, eventVersion={}, redisVersion={}",
                            payload.getAccountNo(), payload.getVersion(), current.get().getVersion());
                    ack.acknowledge();
                    return;
                }
            }

            AccountReadModel readModel = AccountReadModel.builder()
                    .accountNo(payload.getAccountNo())
                    .userId(payload.getUserId())
                    .cif(payload.getCif())
                    .fullName(payload.getFullName())
                    .address(payload.getAddress())
                    .mobile(payload.getMobile())
                    .email(payload.getEmail())
                    .balance(parseBigDecimal(payload.getBalance()))
                    .availableBalance(parseBigDecimal(payload.getAvailableBalance()))
                    .holdBalance(parseBigDecimal(payload.getHoldBalance()))
                    .currency(payload.getCurrency())
                    .status(payload.getStatus())
                    .version(payload.getVersion())
                    .lastDbUpdatedAt(payload.getUpdatedAt() != null
                            ? Instant.ofEpochMilli(payload.getUpdatedAt()) : null)
                    .lastSyncedAt(Instant.now())
                    .source("CDC")
                    .build();

            // Only ack AFTER successful Redis write — if this throws, Spring Kafka retries
            accountRedisRepository.save(readModel);
            ack.acknowledge();

            log.info("Read model synced via CDC: accountNo={}, balance={}, version={}",
                    readModel.getAccountNo(), readModel.getBalance(), readModel.getVersion());

        } catch (Exception e) {
            log.error("Failed to process CDC event — will retry. Message: {}", message, e);
            // Re-throw so DefaultErrorHandler retries, then routes to DLT after exhaustion
            throw new RuntimeException("CDC processing failed", e);
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse BigDecimal from: {}", value);
            return BigDecimal.ZERO;
        }
    }
}
