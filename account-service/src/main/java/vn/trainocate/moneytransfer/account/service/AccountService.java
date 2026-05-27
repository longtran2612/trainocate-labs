package vn.trainocate.moneytransfer.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.account.dto.request.CheckBalanceRequest;
import vn.trainocate.moneytransfer.account.dto.request.CreditRequest;
import vn.trainocate.moneytransfer.account.dto.request.CustomerInfoRequest;
import vn.trainocate.moneytransfer.account.dto.request.CreateAccountRequest;
import vn.trainocate.moneytransfer.account.dto.request.DebitRequest;
import vn.trainocate.moneytransfer.account.dto.request.InquiryRequest;
import vn.trainocate.moneytransfer.account.dto.request.UpdateAccountRequest;
import vn.trainocate.moneytransfer.account.dto.response.BalanceResponse;
import vn.trainocate.moneytransfer.account.dto.response.CustomerInfoResponse;
import vn.trainocate.moneytransfer.account.dto.response.DebitCreditResponse;
import vn.trainocate.moneytransfer.account.dto.response.InquiryResponse;
import vn.trainocate.moneytransfer.account.entity.AccountEntity;
import vn.trainocate.moneytransfer.account.exception.BusinessException;
import vn.trainocate.moneytransfer.account.repository.AccountRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public CustomerInfoResponse getCustomerInfo(CustomerInfoRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found with accountNo: " + request.getAccountNo()));

        return toCustomerInfoResponse(account);
    }

    public BalanceResponse checkBalance(CheckBalanceRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found with accountNo: " + request.getAccountNo()));

        return BalanceResponse.builder()
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .holdBalance(account.getHoldBalance())
                .currency(account.getCurrency())
                .build();
    }

    public InquiryResponse inquiry(InquiryRequest request) {
        AccountEntity account = null;

        if (request.getAccountNo() != null) {
            account = accountRepository.findByAccountNo(request.getAccountNo()).orElse(null);
        }
        if (account == null && request.getMobile() != null) {
            account = accountRepository.findByMobile(request.getMobile()).orElse(null);
        }
        if (account == null && request.getCif() != null) {
            account = accountRepository.findByCif(request.getCif()).orElse(null);
        }

        if (account == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "No account found matching the inquiry criteria");
        }

        return InquiryResponse.builder()
                .accountNo(account.getAccountNo())
                .fullName(account.getFullName())
                .status(account.getStatus())
                .build();
    }

    @Transactional
    public DebitCreditResponse debit(DebitRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found with accountNo: " + request.getAccountNo()));

        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Available balance is insufficient for this transaction");
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        return DebitCreditResponse.builder()
                .txRef(UUID.randomUUID().toString())
                .newBalance(account.getBalance())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public DebitCreditResponse credit(CreditRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found with accountNo: " + request.getAccountNo()));

        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        accountRepository.save(account);

        return DebitCreditResponse.builder()
                .txRef(UUID.randomUUID().toString())
                .newBalance(account.getBalance())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public List<CustomerInfoResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toCustomerInfoResponse)
                .toList();
    }

    @Transactional
    public CustomerInfoResponse createAccount(CreateAccountRequest request) {
        // Check duplicate CIF
        accountRepository.findByCif(request.getCif()).ifPresent(existing -> {
            throw new BusinessException("DUPLICATE_CIF", "Account with CIF " + request.getCif() + " already exists");
        });

        // Check duplicate mobile
        if (request.getMobile() != null) {
            accountRepository.findByMobile(request.getMobile()).ifPresent(existing -> {
                throw new BusinessException("DUPLICATE_MOBILE", "Account with mobile " + request.getMobile() + " already exists");
            });
        }

        // Generate account number
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
        log.info("Account created: accountNo={}, cif={}", accountNo, request.getCif());

        return toCustomerInfoResponse(account);
    }

    @Transactional
    public CustomerInfoResponse updateAccount(UpdateAccountRequest request) {
        AccountEntity account = accountRepository.findByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found with accountNo: " + request.getAccountNo()));

        if (request.getFullName() != null) {
            account.setFullName(request.getFullName());
        }
        if (request.getDob() != null) {
            account.setDob(request.getDob());
        }
        if (request.getAddress() != null) {
            account.setAddress(request.getAddress());
        }
        if (request.getMobile() != null) {
            // Check duplicate mobile (exclude current account)
            accountRepository.findByMobile(request.getMobile()).ifPresent(existing -> {
                if (!existing.getAccountNo().equals(request.getAccountNo())) {
                    throw new BusinessException("DUPLICATE_MOBILE", "Mobile " + request.getMobile() + " already used by another account");
                }
            });
            account.setMobile(request.getMobile());
        }
        if (request.getEmail() != null) {
            account.setEmail(request.getEmail());
        }

        accountRepository.save(account);
        log.info("Account updated: accountNo={}", request.getAccountNo());

        return toCustomerInfoResponse(account);
    }

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
