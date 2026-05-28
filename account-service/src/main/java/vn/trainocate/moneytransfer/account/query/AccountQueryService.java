package vn.trainocate.moneytransfer.account.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.account.dto.request.CheckBalanceRequest;
import vn.trainocate.moneytransfer.account.dto.request.CustomerInfoRequest;
import vn.trainocate.moneytransfer.account.dto.request.InquiryRequest;
import vn.trainocate.moneytransfer.account.dto.response.BalanceResponse;
import vn.trainocate.moneytransfer.account.dto.response.CustomerInfoResponse;
import vn.trainocate.moneytransfer.account.dto.response.InquiryResponse;
import vn.trainocate.moneytransfer.account.entity.AccountEntity;
import vn.trainocate.moneytransfer.account.exception.BusinessException;
import vn.trainocate.moneytransfer.account.readmodel.AccountReadModel;
import vn.trainocate.moneytransfer.account.readmodel.AccountRedisRepository;
import vn.trainocate.moneytransfer.account.repository.AccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CQRS Query Side — reads from Redis read model when fresh; falls back to PostgreSQL
 * when Redis is stale (CDC pipeline down) or absent.
 *
 * Balance correctness policy:
 *   - checkBalance: Redis only if fresh (within staleness threshold); else PostgreSQL.
 *   - On PostgreSQL read: warm Redis so next call can hit cache.
 *   - Response always includes source + lastSyncedAt so callers know data provenance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final AccountRedisRepository accountRedisRepository;
    private final ReadModelFreshnessService freshnessService;

    public CustomerInfoResponse getCustomerInfo(CustomerInfoRequest request) {
        return accountRedisRepository.findByAccountNo(request.getAccountNo())
                .map(m -> {
                    log.debug("[QUERY] Cache HIT for accountNo={}", request.getAccountNo());
                    return mapToCustomerInfo(m);
                })
                .orElseGet(() -> {
                    log.info("[QUERY] Cache MISS for accountNo={}, falling back to PostgreSQL",
                            request.getAccountNo());
                    AccountEntity entity = requireAccount(request.getAccountNo());
                    warmCache(entity);
                    return toCustomerInfoFromEntity(entity);
                });
    }

    /**
     * Safe balance read with freshness guarantee.
     *
     * Returns Redis balance only when the read model was synced recently.
     * Falls back to PostgreSQL (source of truth) when Kafka/Debezium/consumer is down.
     */
    public BalanceResponse checkBalance(CheckBalanceRequest request) {
        Optional<AccountReadModel> cached = accountRedisRepository.findByAccountNo(request.getAccountNo());

        if (cached.isPresent() && freshnessService.isFresh(cached.get())) {
            log.debug("[QUERY] Balance HIT (fresh) for accountNo={}", request.getAccountNo());
            return BalanceResponse.fromRedis(cached.get());
        }

        String reason = cached.isPresent() ? "READ_MODEL_STALE" : "READ_MODEL_MISS";
        log.info("[QUERY] Balance fallback to PostgreSQL: accountNo={}, reason={}",
                request.getAccountNo(), reason);

        AccountEntity entity = requireAccount(request.getAccountNo());

        // Warm Redis but never let a Redis failure block the response
        try {
            accountRedisRepository.save(toReadModel(entity));
        } catch (Exception e) {
            log.warn("Redis warm-up failed after PostgreSQL fallback, accountNo={}", request.getAccountNo(), e);
        }

        return BalanceResponse.fromPostgres(entity, reason);
    }

    public InquiryResponse inquiry(InquiryRequest request) {
        if (request.getAccountNo() != null) {
            return accountRedisRepository.findByAccountNo(request.getAccountNo())
                    .map(m -> InquiryResponse.builder()
                            .accountNo(m.getAccountNo())
                            .fullName(m.getFullName())
                            .status(m.getStatus())
                            .build())
                    .orElseGet(() -> {
                        AccountEntity entity = requireAccount(request.getAccountNo());
                        warmCache(entity);
                        return toInquiry(entity);
                    });
        }

        AccountEntity account = null;
        if (request.getMobile() != null) {
            account = accountRepository.findByMobile(request.getMobile()).orElse(null);
        }
        if (account == null && request.getCif() != null) {
            account = accountRepository.findByCif(request.getCif()).orElse(null);
        }
        if (account == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND",
                    "No account found matching the inquiry criteria");
        }
        return toInquiry(account);
    }

    public List<CustomerInfoResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toCustomerInfoFromEntity)
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────────

    private AccountEntity requireAccount(String accountNo) {
        return accountRepository.findByAccountNo(accountNo)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND",
                        "Account not found with accountNo: " + accountNo));
    }

    private void warmCache(AccountEntity entity) {
        try {
            accountRedisRepository.save(toReadModel(entity));
        } catch (Exception e) {
            log.warn("Redis warm-up failed for accountNo={}", entity.getAccountNo(), e);
        }
    }

    private CustomerInfoResponse mapToCustomerInfo(AccountReadModel m) {
        return CustomerInfoResponse.builder()
                .accountNo(m.getAccountNo())
                .userId(m.getUserId() != null ? java.util.UUID.fromString(m.getUserId()) : null)
                .cif(m.getCif())
                .fullName(m.getFullName())
                .address(m.getAddress())
                .mobile(m.getMobile())
                .email(m.getEmail())
                .balance(m.getBalance())
                .availableBalance(m.getAvailableBalance())
                .holdBalance(m.getHoldBalance())
                .currency(m.getCurrency())
                .status(m.getStatus())
                .build();
    }

    private CustomerInfoResponse toCustomerInfoFromEntity(AccountEntity e) {
        return CustomerInfoResponse.builder()
                .accountNo(e.getAccountNo())
                .userId(e.getUserId())
                .cif(e.getCif())
                .fullName(e.getFullName())
                .dob(e.getDob())
                .address(e.getAddress())
                .mobile(e.getMobile())
                .email(e.getEmail())
                .balance(e.getBalance())
                .availableBalance(e.getAvailableBalance())
                .holdBalance(e.getHoldBalance())
                .currency(e.getCurrency())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private InquiryResponse toInquiry(AccountEntity e) {
        return InquiryResponse.builder()
                .accountNo(e.getAccountNo())
                .fullName(e.getFullName())
                .status(e.getStatus())
                .build();
    }

    private AccountReadModel toReadModel(AccountEntity e) {
        return AccountReadModel.builder()
                .accountNo(e.getAccountNo())
                .userId(e.getUserId() != null ? e.getUserId().toString() : null)
                .cif(e.getCif())
                .fullName(e.getFullName())
                .address(e.getAddress())
                .mobile(e.getMobile())
                .email(e.getEmail())
                .balance(e.getBalance())
                .availableBalance(e.getAvailableBalance())
                .holdBalance(e.getHoldBalance())
                .currency(e.getCurrency())
                .status(e.getStatus())
                .version(e.getVersion())
                .lastDbUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toInstant(
                        java.time.ZoneOffset.of("+07:00")) : null)
                .lastSyncedAt(Instant.now())
                .source("DIRECT")
                .build();
    }
}
