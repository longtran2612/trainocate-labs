package vn.trainocate.moneytransfer.limit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.limit.dto.request.LimitCheckRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitConsumeRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitInfoRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitInitRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitReleaseRequest;
import vn.trainocate.moneytransfer.limit.dto.response.LimitCheckResponse;
import vn.trainocate.moneytransfer.limit.dto.response.LimitConsumeResponse;
import vn.trainocate.moneytransfer.limit.dto.response.LimitInfoResponse;
import vn.trainocate.moneytransfer.limit.entity.LimitEntity;
import vn.trainocate.moneytransfer.limit.exception.BusinessException;
import vn.trainocate.moneytransfer.limit.repository.LimitRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LimitService {

    private static final BigDecimal DEFAULT_SINGLE_LIMIT = new BigDecimal("5000000");
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("20000000");
    private static final BigDecimal DEFAULT_MONTHLY_LIMIT = new BigDecimal("100000000");

    private final LimitRepository limitRepository;

    @Transactional
    public LimitCheckResponse limitCheck(LimitCheckRequest request) {
        LimitEntity entity = findOrCreateLimit(request.getAccountNo(), request.getTransferType());

        resetDailyIfNeeded(entity);

        BigDecimal remainingDaily = entity.getDailyLimit().subtract(entity.getUsedDaily());
        BigDecimal remainingMonthly = entity.getMonthlyLimit().subtract(entity.getUsedMonthly());
        BigDecimal remainingSingle = entity.getSingleLimit();

        if (request.getAmount().compareTo(entity.getSingleLimit()) > 0) {
            return LimitCheckResponse.builder()
                    .allowed(false)
                    .reason("Amount exceeds single transaction limit")
                    .remainingDaily(remainingDaily)
                    .remainingMonthly(remainingMonthly)
                    .remainingSingle(remainingSingle)
                    .build();
        }

        if (request.getAmount().compareTo(remainingDaily) > 0) {
            return LimitCheckResponse.builder()
                    .allowed(false)
                    .reason("Amount exceeds remaining daily limit")
                    .remainingDaily(remainingDaily)
                    .remainingMonthly(remainingMonthly)
                    .remainingSingle(remainingSingle)
                    .build();
        }

        if (request.getAmount().compareTo(remainingMonthly) > 0) {
            return LimitCheckResponse.builder()
                    .allowed(false)
                    .reason("Amount exceeds remaining monthly limit")
                    .remainingDaily(remainingDaily)
                    .remainingMonthly(remainingMonthly)
                    .remainingSingle(remainingSingle)
                    .build();
        }

        return LimitCheckResponse.builder()
                .allowed(true)
                .remainingDaily(remainingDaily)
                .remainingMonthly(remainingMonthly)
                .remainingSingle(remainingSingle)
                .build();
    }

    public java.util.List<LimitInfoResponse> limitInfo(LimitInfoRequest request) {
        java.util.List<LimitEntity> entities = limitRepository.findAllByAccountNo(request.getAccountNo());
        if (entities.isEmpty()) {
            throw new BusinessException("LIMIT_NOT_FOUND", "Limit record not found for account");
        }

        return entities.stream().map(entity -> LimitInfoResponse.builder()
                .accountNo(entity.getAccountNo())
                .kycTier(entity.getKycTier())
                .transferType(entity.getTransferType())
                .singleLimit(entity.getSingleLimit())
                .dailyLimit(entity.getDailyLimit())
                .monthlyLimit(entity.getMonthlyLimit())
                .usedDaily(entity.getUsedDaily())
                .usedMonthly(entity.getUsedMonthly())
                .resetAt(entity.getResetAt())
                .build()).toList();
    }

    @Transactional
    public LimitConsumeResponse limitConsume(LimitConsumeRequest request) {
        LimitEntity entity = findOrCreateLimit(request.getAccountNo(), request.getTransferType());

        resetDailyIfNeeded(entity);

        entity.setUsedDaily(entity.getUsedDaily().add(request.getAmount()));
        entity.setUsedMonthly(entity.getUsedMonthly().add(request.getAmount()));
        limitRepository.save(entity);

        log.info("Limit consumed: accountNo={}, amount={}, txId={}", request.getAccountNo(), request.getAmount(), request.getTxId());

        BigDecimal remainingDaily = entity.getDailyLimit().subtract(entity.getUsedDaily());
        BigDecimal remainingMonthly = entity.getMonthlyLimit().subtract(entity.getUsedMonthly());

        return LimitConsumeResponse.builder()
                .status("CONSUMED")
                .remainingDaily(remainingDaily)
                .remainingMonthly(remainingMonthly)
                .build();
    }

    /**
     * Compensation for limitConsume — subtract the amount back from usedDaily/usedMonthly.
     * Called by the Saga orchestrator when a transfer fails after limit was consumed.
     */
    @Transactional
    public void limitRelease(LimitReleaseRequest request) {
        LimitEntity entity = findOrCreateLimit(request.getAccountNo(), request.getTransferType());

        BigDecimal newUsedDaily = entity.getUsedDaily().subtract(request.getAmount()).max(BigDecimal.ZERO);
        BigDecimal newUsedMonthly = entity.getUsedMonthly().subtract(request.getAmount()).max(BigDecimal.ZERO);

        entity.setUsedDaily(newUsedDaily);
        entity.setUsedMonthly(newUsedMonthly);
        limitRepository.save(entity);

        log.info("Limit released (saga compensation): accountNo={}, amount={}, txId={}",
                request.getAccountNo(), request.getAmount(), request.getTxId());
    }

    @Transactional
    public void initLimits(LimitInitRequest request) {
        findOrCreateLimit(request.getAccountNo(), "INTERNAL");
        findOrCreateLimit(request.getAccountNo(), "EXTERNAL");
        log.info("Limits initialized for accountNo={}", request.getAccountNo());
    }

    private LimitEntity findOrCreateLimit(String accountNo, String transferType) {
        return limitRepository.findByAccountNoAndTransferType(accountNo, transferType)
                .orElseGet(() -> {
                    LimitEntity newLimit = LimitEntity.builder()
                            .accountNo(accountNo)
                            .kycTier("TIER_1")
                            .transferType(transferType)
                            .singleLimit(DEFAULT_SINGLE_LIMIT)
                            .dailyLimit(DEFAULT_DAILY_LIMIT)
                            .monthlyLimit(DEFAULT_MONTHLY_LIMIT)
                            .usedDaily(BigDecimal.ZERO)
                            .usedMonthly(BigDecimal.ZERO)
                            .resetAt(LocalDate.now().atStartOfDay())
                            .build();
                    log.info("Auto-created limit for accountNo={}, transferType={}", accountNo, transferType);
                    return limitRepository.save(newLimit);
                });
    }

    private void resetDailyIfNeeded(LimitEntity entity) {
        if (entity.getResetAt().toLocalDate().isBefore(LocalDate.now())) {
            entity.setUsedDaily(BigDecimal.ZERO);
            entity.setResetAt(LocalDate.now().atStartOfDay());
            limitRepository.save(entity);
            log.info("Daily limit reset for accountNo={}", entity.getAccountNo());
        }
    }
}
