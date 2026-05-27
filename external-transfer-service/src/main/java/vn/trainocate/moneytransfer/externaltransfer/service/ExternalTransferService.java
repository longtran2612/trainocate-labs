package vn.trainocate.moneytransfer.externaltransfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.externaltransfer.client.AccountClient;
import vn.trainocate.moneytransfer.externaltransfer.client.KycClient;
import vn.trainocate.moneytransfer.externaltransfer.client.LimitClient;
import vn.trainocate.moneytransfer.externaltransfer.client.NapasClient;
import vn.trainocate.moneytransfer.externaltransfer.dto.ApiResponse;
import vn.trainocate.moneytransfer.externaltransfer.dto.request.ExternalInquiryRequest;
import vn.trainocate.moneytransfer.externaltransfer.dto.request.ExternalTransferRequest;
import vn.trainocate.moneytransfer.externaltransfer.dto.response.ExternalInquiryResponse;
import vn.trainocate.moneytransfer.externaltransfer.dto.response.ExternalTransferResponse;
import vn.trainocate.moneytransfer.externaltransfer.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTransferService {

    private final AccountClient accountClient;
    private final KycClient kycClient;
    private final LimitClient limitClient;
    private final NapasClient napasClient;
    private final ObjectMapper objectMapper;

    public ExternalInquiryResponse inquiry(ExternalInquiryRequest request) {
        log.info("Processing external inquiry for accountNo={}, bankCode={}", request.getAccountNo(), request.getBankCode());

        // Call NAPAS simulator for account name lookup
        Map<String, Object> napasRequest = Map.of(
                "bankCode", request.getBankCode(),
                "accountNo", request.getAccountNo());
        Map<String, Object> napasResult = extractData(napasClient.inquiry(napasRequest));

        String responseCode = String.valueOf(napasResult.get("responseCode"));
        if (!"00".equals(responseCode)) {
            throw new BusinessException("NAPAS_INQUIRY_FAILED",
                    String.valueOf(napasResult.getOrDefault("responseMessage", "NAPAS inquiry failed")));
        }

        return ExternalInquiryResponse.builder()
                .accountNo(String.valueOf(napasResult.get("accountNo")))
                .fullName(String.valueOf(napasResult.get("accountName")))
                .bankName(String.valueOf(napasResult.get("bankName")))
                .bankCode(String.valueOf(napasResult.get("bankCode")))
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
                kycClient.getKycStatus(Map.of("accountNo", request.getSenderAccountNo())));
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

        // Step 7: Call NAPAS simulator to process external transfer
        Map<String, Object> napasRequest = new HashMap<>();
        napasRequest.put("referenceId", request.getReferenceId());
        napasRequest.put("senderBankCode", "970406");
        napasRequest.put("senderAccountNo", request.getSenderAccountNo());
        napasRequest.put("receiverBankCode", request.getReceiverBankCode());
        napasRequest.put("receiverAccountNo", request.getReceiverAccountNo());
        if (request.getReceiverName() != null) napasRequest.put("receiverName", request.getReceiverName());
        napasRequest.put("amount", request.getAmount());
        napasRequest.put("currency", request.getCurrency() != null ? request.getCurrency() : "VND");
        if (request.getDescription() != null) napasRequest.put("description", request.getDescription());

        Map<String, Object> napasResult = extractData(napasClient.transfer(napasRequest));
        String napasResponseCode = String.valueOf(napasResult.get("responseCode"));
        String napasRef = String.valueOf(napasResult.get("napasRef"));

        if (!"00".equals(napasResponseCode)) {
            // NAPAS rejected — refund the sender
            log.warn("NAPAS transfer FAILED: ref={}, napasRef={}, code={}, msg={}",
                    request.getReferenceId(), napasRef, napasResponseCode, napasResult.get("responseMessage"));

            // Refund: credit back to sender
            Map<String, Object> refundRequest = new HashMap<>();
            refundRequest.put("accountNo", request.getSenderAccountNo());
            refundRequest.put("amount", request.getAmount());
            refundRequest.put("referenceId", "REFUND-" + request.getReferenceId());
            refundRequest.put("description", "Refund for failed NAPAS transfer " + request.getReferenceId());
            try {
                extractData(accountClient.credit(refundRequest));
                log.info("Refund successful for accountNo={}", request.getSenderAccountNo());
            } catch (Exception e) {
                log.error("Refund FAILED for accountNo={}, manual intervention required", request.getSenderAccountNo(), e);
            }

            throw new BusinessException("NAPAS_TRANSFER_FAILED",
                    String.valueOf(napasResult.getOrDefault("responseMessage", "NAPAS transfer failed")));
        }

        String receiverName = String.valueOf(napasResult.getOrDefault("receiverName", ""));
        log.info("External transfer completed: ref={}, napasRef={}", request.getReferenceId(), napasRef);

        return ExternalTransferResponse.builder()
                .referenceId(request.getReferenceId())
                .status("COMPLETED")
                .napasRef(napasRef)
                .receiverName(receiverName)
                .completedAt(LocalDateTime.now())
                .build();
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
