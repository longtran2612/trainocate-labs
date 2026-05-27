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

/**
 * Debezium CDC consumer — bridges the PostgreSQL write store to the Redis read model.
 *
 * <p>Flow: PostgreSQL WAL → Debezium → Kafka topic {@code account_db.public.accounts}
 * → this consumer → Redis {@code account:{accountNo}}.
 *
 * <p>Manual acknowledgment: always ack in finally block to prevent infinite redelivery.
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
        try {
            // Tombstone (DELETE event with ExtractNewRecordState SMT)
            if (message == null || "null".equalsIgnoreCase(message.trim())) {
                log.debug("Received tombstone on topic {}, skipping", TOPIC);
                return;
            }

            DebeziumAccountPayload payload = objectMapper.readValue(message, DebeziumAccountPayload.class);

            if (payload.getAccountNo() == null) {
                log.warn("CDC event missing account_no, skipping: {}", message);
                return;
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
                    .build();

            accountRedisRepository.save(readModel);
            log.info("Read model synced via Debezium CDC: accountNo={}, balance={}",
                    readModel.getAccountNo(), readModel.getBalance());

        } catch (Exception e) {
            log.error("Failed to process CDC event, read model NOT updated. Message: {}", message, e);
            // Do NOT re-throw — always ack to avoid redelivery loop
        } finally {
            ack.acknowledge();
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
