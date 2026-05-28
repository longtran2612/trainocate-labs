package vn.trainocate.moneytransfer.account.readmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CQRS Read Model — stored in Redis as JSON.
 * Updated asynchronously via Debezium CDC → Kafka → AccountEventConsumer.
 * Redis key: "account:{accountNo}"
 *
 * Freshness fields allow staleness detection when CDC pipeline is down.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReadModel {

    private String accountNo;
    private String userId;
    private String cif;
    private String fullName;
    private String address;
    private String mobile;
    private String email;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal holdBalance;
    private String currency;
    private String status;

    /** DB row version — used for idempotent consumer (skip old/duplicate events). */
    private Long version;

    /** When PostgreSQL committed the change that produced this read model. */
    private Instant lastDbUpdatedAt;

    /** When this read model was written to Redis (CDC or direct fallback). */
    private Instant lastSyncedAt;

    /** "CDC" when synced via Debezium, "DIRECT" when warm-filled from PostgreSQL fallback. */
    private String source;
}
