package vn.trainocate.moneytransfer.externaltransfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.externaltransfer.client.AccountClient;
import vn.trainocate.moneytransfer.externaltransfer.client.KycClient;
import vn.trainocate.moneytransfer.externaltransfer.client.LimitClient;
import vn.trainocate.moneytransfer.externaltransfer.dto.ApiResponse;
import vn.trainocate.moneytransfer.externaltransfer.dto.request.ExternalInquiryRequest;
import vn.trainocate.moneytransfer.externaltransfer.dto.request.ExternalTransferRequest;
import vn.trainocate.moneytransfer.externaltransfer.dto.response.ExternalInquiryResponse;
import vn.trainocate.moneytransfer.externaltransfer.dto.response.ExternalTransferResponse;
import vn.trainocate.moneytransfer.externaltransfer.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTransferService {

    private final AccountClient accountClient;
    private final KycClient kycClient;
    private final LimitClient limitClient;
    private final ObjectMapper objectMapper;

    public ExternalInquiryResponse inquiry(ExternalInquiryRequest request) {
        log.info("Processing external inquiry for accountNo={}, bankCode={}", request.getAccountNo(), request.getBankCode());

        String bankName = mapBankName(request.getBankCode());

        return ExternalInquiryResponse.builder()
                .accountNo(request.getAccountNo())
                .fullName("MOCK - " + request.getAccountNo())
                .bankName(bankName)
                .bankCode(request.getBankCode())
                .status("ACTIVE")
                .build();
    }

    public ExternalTransferResponse transfer(ExternalTransferRequest request) {
        log.info("Processing external transfer: referenceId={}, sender={}, receiver={}, bankCode={}, amount={}",
                request.getReferenceId(), request.getSenderAccountNo(),
                request.getReceiverAccountNo(), request.getReceiverBankCode(), request.getAmount());

        // Step 1: Get sender customer info (resolve accountNo -> userId for KYC)
        Map<String, Object> customerInfo = extractData(
                accountClient.getCustomerInfo(Map.of("accountNo", request.getSenderAccountNo())));
        String userId = String.valueOf(customerInfo.get("userId"));
        log.info("Sender resolved: accountNo={}, userId={}", request.getSenderAccountNo(), userId);

        // Step 2: Check KYC status
        Map<String, Object> kycResponse = extractData(
                kycClient.getKycStatus(Map.of("userId", userId)));
        String kycStatus = String.valueOf(kycResponse.get("status"));
        if (!"VERIFIED".equals(kycStatus)) {
            throw new BusinessException("KYC_NOT_VERIFIED", "KYC verification is required before making transfers");
        }
        log.info("KYC verified for userId={}", userId);

        // Step 3: Check transfer limit (EXTERNAL type)
        Map<String, Object> limitResponse = extractData(
                limitClient.limitCheck(Map.of(
                        "accountNo", request.getSenderAccountNo(),
                        "amount", request.getAmount(),
                        "transferType", "EXTERNAL")));
        Boolean allowed = (Boolean) limitResponse.get("allowed");
        if (allowed == null || !allowed) {
            String reason = String.valueOf(limitResponse.getOrDefault("reason", "Transfer limit exceeded"));
            throw new BusinessException("LIMIT_EXCEEDED", reason);
        }
        log.info("Limit check passed for accountNo={}", request.getSenderAccountNo());

        // Step 4: Check balance
        Map<String, Object> balanceResponse = extractData(
                accountClient.checkBalance(Map.of("accountNo", request.getSenderAccountNo())));
        BigDecimal availableBalance = new BigDecimal(String.valueOf(balanceResponse.get("availableBalance")));
        if (availableBalance.compareTo(request.getAmount()) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Available balance is insufficient for this transfer");
        }
        log.info("Balance check passed: available={}, requested={}", availableBalance, request.getAmount());

        // Step 5: Debit sender
        extractData(accountClient.debit(Map.of(
                "accountNo", request.getSenderAccountNo(),
                "amount", request.getAmount(),
                "referenceId", request.getReferenceId())));
        log.info("Debit successful for accountNo={}", request.getSenderAccountNo());

        // Step 6: Consume limit
        extractData(limitClient.limitConsume(Map.of(
                "accountNo", request.getSenderAccountNo(),
                "amount", request.getAmount(),
                "transferType", "EXTERNAL",
                "referenceId", request.getReferenceId())));
        log.info("Limit consumed for accountNo={}", request.getSenderAccountNo());

        // Step 7: External transfer — no credit (goes to NAPAS), leave in PENDING
        String napasRef = "NAPAS-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("External transfer pending NAPAS processing: referenceId={}, napasRef={}", request.getReferenceId(), napasRef);

        return ExternalTransferResponse.builder()
                .referenceId(request.getReferenceId())
                .status("PENDING")
                .napasRef(napasRef)
                .estimatedCompletion(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private String mapBankName(String bankCode) {
        if (bankCode == null) return "Unknown Bank";
        return switch (bankCode.toUpperCase()) {
            case "VCB" -> "Vietcombank";
            case "TCB" -> "Techcombank";
            case "MBB" -> "MBBank";
            default -> bankCode + " Bank";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(ApiResponse response) {
        if (response == null || !response.isSuccess()) {
            String code = response != null ? response.getCode() : "SERVICE_ERROR";
            String message = response != null ? response.getMessage() : "Service call failed";
            throw new BusinessException(code, message);
        }
        return objectMapper.convertValue(response.getData(), Map.class);
    }
}
