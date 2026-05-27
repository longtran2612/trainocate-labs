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

import java.util.List;

/**
 * CQRS Query Side — reads from the Redis read model (fast path).
 *
 * <p>On cache miss (Redis cold start or eviction), falls back to PostgreSQL
 * and warms the Redis cache automatically.
 *
 * <p>Lookups by mobile / CIF always use PostgreSQL (no secondary Redis index needed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final AccountRedisRepository accountRedisRepository;

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
                    accountRedisRepository.save(toReadModel(entity));   // warm cache
                    return toCustomerInfoFromEntity(entity);
                });
    }

    public BalanceResponse checkBalance(CheckBalanceRequest request) {
        return accountRedisRepository.findByAccountNo(request.getAccountNo())
                .map(m -> {
                    log.debug("[QUERY] Balance cache HIT for accountNo={}", request.getAccountNo());
                    return BalanceResponse.builder()
                            .balance(m.getBalance())
                            .availableBalance(m.getAvailableBalance())
                            .holdBalance(m.getHoldBalance())
                            .currency(m.getCurrency())
                            .build();
                })
                .orElseGet(() -> {
                    log.info("[QUERY] Balance cache MISS for accountNo={}, falling back to PostgreSQL",
                            request.getAccountNo());
                    AccountEntity entity = requireAccount(request.getAccountNo());
                    accountRedisRepository.save(toReadModel(entity));
                    return BalanceResponse.builder()
                            .balance(entity.getBalance())
                            .availableBalance(entity.getAvailableBalance())
                            .holdBalance(entity.getHoldBalance())
                            .currency(entity.getCurrency())
                            .build();
                });
    }

    public InquiryResponse inquiry(InquiryRequest request) {
        // Primary lookup by accountNo → try Redis first
        if (request.getAccountNo() != null) {
            return accountRedisRepository.findByAccountNo(request.getAccountNo())
                    .map(m -> InquiryResponse.builder()
                            .accountNo(m.getAccountNo())
                            .fullName(m.getFullName())
                            .status(m.getStatus())
                            .build())
                    .orElseGet(() -> {
                        AccountEntity entity = requireAccount(request.getAccountNo());
                        accountRedisRepository.save(toReadModel(entity));
                        return toInquiry(entity);
                    });
        }

        // Secondary lookups (mobile / CIF) — always PostgreSQL
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
        // Full-scan always from PostgreSQL (no Redis for list queries)
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
                .build();
    }
}
