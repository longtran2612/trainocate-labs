package vn.trainocate.moneytransfer.account.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.trainocate.moneytransfer.account.entity.AccountEntity;
import vn.trainocate.moneytransfer.account.readmodel.AccountReadModel;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceResponse {

    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal holdBalance;
    private String currency;

    /** REDIS or POSTGRESQL — where this balance was read from. */
    private String source;

    /** true if the data may be behind the DB (served as degraded mode). */
    private Boolean stale;

    /** When Redis was last synced with the DB (CDC timestamp). */
    private Instant lastSyncedAt;

    /** Explains why we fell back to PostgreSQL instead of Redis. */
    private String fallbackReason;

    public static BalanceResponse fromRedis(AccountReadModel m) {
        return BalanceResponse.builder()
                .balance(m.getBalance())
                .availableBalance(m.getAvailableBalance())
                .holdBalance(m.getHoldBalance())
                .currency(m.getCurrency())
                .source("REDIS")
                .stale(false)
                .lastSyncedAt(m.getLastSyncedAt())
                .build();
    }

    public static BalanceResponse fromPostgres(AccountEntity e, String fallbackReason) {
        return BalanceResponse.builder()
                .balance(e.getBalance())
                .availableBalance(e.getAvailableBalance())
                .holdBalance(e.getHoldBalance())
                .currency(e.getCurrency())
                .source("POSTGRESQL")
                .stale(false)
                .fallbackReason(fallbackReason)
                .build();
    }
}
