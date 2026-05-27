package vn.trainocate.moneytransfer.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.kyc.dto.request.KycInfoRequest;
import vn.trainocate.moneytransfer.kyc.dto.request.KycStatusRequest;
import vn.trainocate.moneytransfer.kyc.dto.request.KycTierRequest;
import vn.trainocate.moneytransfer.kyc.dto.request.KycVerifyRequest;
import vn.trainocate.moneytransfer.kyc.dto.response.KycInfoResponse;
import vn.trainocate.moneytransfer.kyc.dto.response.KycStatusResponse;
import vn.trainocate.moneytransfer.kyc.dto.response.KycTierResponse;
import vn.trainocate.moneytransfer.kyc.dto.response.KycVerifyResponse;
import vn.trainocate.moneytransfer.kyc.entity.KycEntity;
import vn.trainocate.moneytransfer.kyc.exception.BusinessException;
import vn.trainocate.moneytransfer.kyc.repository.KycRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final KycRepository kycRepository;

    public KycInfoResponse getKycInfo(KycInfoRequest request) {
        KycEntity entity = findKycEntity(request.getAccountNo(), request.getUserId());

        return KycInfoResponse.builder()
                .kycId(entity.getKycId())
                .userId(entity.getUserId())
                .kycTier(entity.getKycTier())
                .fullName(entity.getFullName())
                .idNumber(entity.getIdNumber())
                .idType(entity.getIdType())
                .verifiedAt(entity.getVerifiedAt())
                .status(entity.getStatus())
                .build();
    }

    public KycTierResponse getKycTier(KycTierRequest request) {
        KycEntity entity = kycRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new BusinessException("KYC_NOT_FOUND", "KYC record not found for user"));

        return mapTierToLimits(entity.getKycTier());
    }

    public KycStatusResponse getKycStatus(KycStatusRequest request) {
        KycEntity entity = findKycEntity(request.getAccountNo(), request.getUserId());

        return KycStatusResponse.builder()
                .status(entity.getStatus())
                .verifiedAt(entity.getVerifiedAt())
                .kycTier(entity.getKycTier())
                .build();
    }

    @Transactional
    public KycVerifyResponse verify(KycVerifyRequest request) {
        KycEntity entity = kycRepository.findByUserId(request.getUserId())
                .orElse(KycEntity.builder()
                        .userId(request.getUserId())
                        .build());

        entity.setFullName(request.getFullName());
        entity.setIdNumber(request.getIdNumber());
        entity.setIdType(request.getIdType());
        if (request.getAccountNo() != null) {
            entity.setAccountNo(request.getAccountNo());
        }
        entity.setStatus("VERIFIED");
        entity.setKycTier("TIER_1");
        entity.setVerifiedAt(LocalDateTime.now());

        kycRepository.save(entity);

        log.info("KYC verified for userId={}, tier=TIER_1", request.getUserId());

        return KycVerifyResponse.builder()
                .status("VERIFIED")
                .message("KYC verification completed successfully")
                .kycTier("TIER_1")
                .build();
    }

    private KycEntity findKycEntity(String accountNo, java.util.UUID userId) {
        if (accountNo != null && !accountNo.isBlank()) {
            return kycRepository.findByAccountNo(accountNo)
                    .orElseThrow(() -> new BusinessException("KYC_NOT_FOUND", "KYC record not found for account"));
        }
        if (userId != null) {
            return kycRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException("KYC_NOT_FOUND", "KYC record not found for user"));
        }
        throw new BusinessException("KYC_INVALID_REQUEST", "accountNo or userId is required");
    }

    private KycTierResponse mapTierToLimits(String tier) {
        return switch (tier) {
            case "TIER_1" -> KycTierResponse.builder()
                    .kycTier("TIER_1")
                    .singleLimit(new BigDecimal("5000000"))
                    .dailyLimit(new BigDecimal("20000000"))
                    .monthlyLimit(new BigDecimal("100000000"))
                    .build();
            case "TIER_2" -> KycTierResponse.builder()
                    .kycTier("TIER_2")
                    .singleLimit(new BigDecimal("50000000"))
                    .dailyLimit(new BigDecimal("200000000"))
                    .monthlyLimit(new BigDecimal("500000000"))
                    .build();
            case "TIER_3" -> KycTierResponse.builder()
                    .kycTier("TIER_3")
                    .singleLimit(new BigDecimal("500000000"))
                    .dailyLimit(new BigDecimal("2000000000"))
                    .monthlyLimit(new BigDecimal("999999999999"))
                    .build();
            default -> KycTierResponse.builder()
                    .kycTier("TIER_0")
                    .singleLimit(BigDecimal.ZERO)
                    .dailyLimit(BigDecimal.ZERO)
                    .monthlyLimit(BigDecimal.ZERO)
                    .build();
        };
    }
}
