package vn.trainocate.moneytransfer.account.readmodel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Redis repository for the CQRS query-side read model.
 * Key format: "account:{accountNo}"
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AccountRedisRepository {

    private static final String KEY_PREFIX = "account:";

    private final RedisTemplate<String, AccountReadModel> accountRedisTemplate;

    public void save(AccountReadModel model) {
        String key = KEY_PREFIX + model.getAccountNo();
        accountRedisTemplate.opsForValue().set(key, model);
        log.debug("Saved read model to Redis: key={}", key);
    }

    public Optional<AccountReadModel> findByAccountNo(String accountNo) {
        AccountReadModel model = accountRedisTemplate.opsForValue().get(KEY_PREFIX + accountNo);
        return Optional.ofNullable(model);
    }

    public void deleteByAccountNo(String accountNo) {
        accountRedisTemplate.delete(KEY_PREFIX + accountNo);
        log.debug("Deleted read model from Redis: accountNo={}", accountNo);
    }
}
