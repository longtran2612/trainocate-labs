package vn.trainocate.moneytransfer.account.query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.account.readmodel.AccountReadModel;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether the Redis read model is fresh enough to serve balance queries.
 *
 * When the CDC pipeline (Kafka / Debezium / consumer) is down, Redis entries stop being
 * updated. Queries must fall back to PostgreSQL rather than silently serving stale balance.
 */
@Slf4j
@Service
public class ReadModelFreshnessService {

    @Value("${account.read-model.max-staleness-ms:30000}")
    private long maxStalenessMs;

    /**
     * Returns true only when the read model was synced recently enough.
     * A missing or old {@code lastSyncedAt} means the CDC pipeline has been silent.
     */
    public boolean isFresh(AccountReadModel model) {
        if (model == null || model.getLastSyncedAt() == null) {
            return false;
        }
        Duration age = Duration.between(model.getLastSyncedAt(), Instant.now());
        boolean fresh = age.toMillis() <= maxStalenessMs;
        if (!fresh) {
            log.debug("Read model stale: accountNo={}, age={}ms, threshold={}ms",
                    model.getAccountNo(), age.toMillis(), maxStalenessMs);
        }
        return fresh;
    }
}
