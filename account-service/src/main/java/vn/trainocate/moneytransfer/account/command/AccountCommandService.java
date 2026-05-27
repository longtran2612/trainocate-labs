package vn.trainocate.moneytransfer.account.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.account.dto.request.CreateAccountRequest;
import vn.trainocate.moneytransfer.account.dto.request.CreditRequest;
import vn.trainocate.moneytransfer.account.dto.request.DebitRequest;
import vn.trainocate.moneytransfer.account.dto.request.UpdateAccountRequest;
import vn.trainocate.moneytransfer.account.dto.response.CustomerInfoResponse;
import vn.trainocate.moneytransfer.account.dto.response.DebitCreditResponse;
import vn.trainocate.moneytransfer.account.entity.AccountEntity;
import vn.trainocate.moneytransfer.account.exception.BusinessException;
import vn.trainocate.moneytransfer.account.repository.AccountRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CQRS Command Side — handles all write operations against PostgreSQL.
 *
 * <p>After each write, PostgreSQL WAL emits a change event which Debezium
 * captures and publishes to Kafka. The {@link vn.trainocate.moneytransfer.account.event.AccountEventConsumer}
 * then updates the Redis read model asynchronously (eventual consistency).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCommandService {

    private final AccountRepository accountRepository;

    @Transactional
    public CustomerInfoResponse createAccount(CreateAccountRequest request) {
        accountRepository.findByCif(request.getCif()).ifPresent(existing -> {
            throw new BusinessException("DUPLICATE_CIF",
                    "Account with CIF " + request.getCif() + " already exists");
        });

        if (request.getMobile() != null) {
            accountRepository.findByMobile(request.getMobile()).ifPresent(existing -> {
                throw new BusinessException("DUPLICATE_MOBILE",
                        "Account with mobile " + request.getMobile() + " already exists");
            });
        }

        String accountNo = generateAccountNo();

        AccountEntity account = AccountEntity.builder()
                .userId(request.getUserId())
                .accountNo(accountNo)
                .cif(request.getCif())
                .fullName(request.getFullName())
                .dob(request.getDob())
                .address(request.getAddress())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .build();

        accountRepository.save(account);
        log.info("[COMMAND] Account created: accountNo={}, cif={} → Debezium CDC will sync to Redis",
                accountNo, request.getCif());

        return toCustomerInfoResponse(account);
    }

    @Transactional
    public CustomerInfoResponse updateAccount(UpdateAccountRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND",
                        "Account not found: " + request.getAccountNo()));

        if (request.getFullName() != null) account.setFullName(request.getFullName());
        if (request.getDob() != null) account.setDob(request.getDob());
        if (request.getAddress() != null) account.setAddress(request.getAddress());
        if (request.getEmail() != null) account.setEmail(request.getEmail());
        if (request.getMobile() != null) {
            accountRepository.findByMobile(request.getMobile()).ifPresent(existing -> {
                if (!existing.getAccountNo().equals(request.getAccountNo())) {
                    throw new BusinessException("DUPLICATE_MOBILE",
                            "Mobile " + request.getMobile() + " already used by another account");
                }
            });
            account.setMobile(request.getMobile());
        }

        accountRepository.save(account);
        log.info("[COMMAND] Account updated: accountNo={} → Debezium CDC will sync to Redis",
                request.getAccountNo());

        return toCustomerInfoResponse(account);
    }

    @Transactional
    public DebitCreditResponse debit(DebitRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND",
                        "Account not found: " + request.getAccountNo()));

        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE",
                    "Available balance is insufficient for this transaction");
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        log.info("[COMMAND] Debit executed: accountNo={}, amount={}, newBalance={} → CDC → Redis",
                request.getAccountNo(), request.getAmount(), account.getBalance());

        return DebitCreditResponse.builder()
                .txRef(UUID.randomUUID().toString())
                .newBalance(account.getBalance())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public DebitCreditResponse credit(CreditRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND",
                        "Account not found: " + request.getAccountNo()));

        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        accountRepository.save(account);

        log.info("[COMMAND] Credit executed: accountNo={}, amount={}, newBalance={} → CDC → Redis",
                request.getAccountNo(), request.getAmount(), account.getBalance());

        return DebitCreditResponse.builder()
                .txRef(UUID.randomUUID().toString())
                .newBalance(account.getBalance())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────

    private String generateAccountNo() {
        return accountRepository.findTopByOrderByAccountNoDesc()
                .map(latest -> String.valueOf(Long.parseLong(latest.getAccountNo()) + 1))
                .orElse("1000000001");
    }

    private CustomerInfoResponse toCustomerInfoResponse(AccountEntity account) {
        return CustomerInfoResponse.builder()
                .accountNo(account.getAccountNo())
                .userId(account.getUserId())
                .cif(account.getCif())
                .fullName(account.getFullName())
                .dob(account.getDob())
                .address(account.getAddress())
                .mobile(account.getMobile())
                .email(account.getEmail())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .holdBalance(account.getHoldBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
