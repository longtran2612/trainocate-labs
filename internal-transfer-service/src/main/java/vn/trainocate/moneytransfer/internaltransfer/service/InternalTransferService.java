package vn.trainocate.moneytransfer.internaltransfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.internaltransfer.client.AccountClient;
import vn.trainocate.moneytransfer.internaltransfer.client.KycClient;
import vn.trainocate.moneytransfer.internaltransfer.client.LimitClient;
import vn.trainocate.moneytransfer.internaltransfer.dto.ApiResponse;
import vn.trainocate.moneytransfer.internaltransfer.dto.request.InternalInquiryRequest;
import vn.trainocate.moneytransfer.internaltransfer.dto.request.InternalTransferRequest;
import vn.trainocate.moneytransfer.internaltransfer.dto.response.InternalInquiryResponse;
import vn.trainocate.moneytransfer.internaltransfer.dto.response.InternalTransferResponse;
import vn.trainocate.moneytransfer.internaltransfer.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferService {

    private final AccountClient accountClient;
    private final KycClient kycClient;
    private final LimitClient limitClient;
    private final ObjectMapper objectMapper;

    public InternalInquiryResponse inquiry(InternalInquiryRequest request) {
        log.info("Processing internal inquiry for accountNo={}", request.getAccountNo());

        Map<String, Object> accountRequest = new HashMap<>();
        accountRequest.put("accountNo", request.getAccountNo());
        if (request.getMobile() != null) accountRequest.put("mobile", request.getMobile());
        if (request.getCif() != null) accountRequest.put("cif", request.getCif());
        if (request.getCurrency() != null) accountRequest.put("currency", request.getCurrency());

        Map<String, Object> data = extractData(accountClient.inquiry(accountRequest));

        return InternalInquiryResponse.builder()
                .fullName(String.valueOf(data.get("fullName")))
                .accountNo(String.valueOf(data.get("accountNo")))
                .status(String.valueOf(data.get("status")))
                .build();
    }

    public InternalTransferResponse transfer(InternalTransferRequest request) {
        log.info("Processing internal transfer: referenceId={}, sender={}, receiver={}, amount={}",
                request.getReferenceId(), request.getSenderAccountNo(),
                request.getReceiverAccountNo(), request.getAmount());

        // Step 1: Get sender customer info
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

        // Step 3: Check transfer limit
        Map<String, Object> limitResponse = extractData(
                limitClient.limitCheck(Map.of(
                        "accountNo", request.getSenderAccountNo(),
                        "amount", request.getAmount(),
                        "transferType", "INTERNAL")));
        Boolean allowed = (Boolean) limitResponse.get("allowed");
        if (allowed == null || !allowed) {
            String reason = String.valueOf(limitResponse.getOrDefault("reason", "Transfer limit exceeded"));
            throw new BusinessException("LIMIT_EXCEEDED", reason);
        }
        log.info("Limit check passed for accountNo={}", request.getSenderAccountNo());

        // Step 4: Inquiry receiver account
        InternalInquiryRequest inquiryRequest = InternalInquiryRequest.builder()
                .accountNo(request.getReceiverAccountNo())
                .currency(request.getCurrency())
                .build();
        InternalInquiryResponse receiverInfo = inquiry(inquiryRequest);
        log.info("Receiver account found: accountNo={}, name={}", receiverInfo.getAccountNo(), receiverInfo.getFullName());

        // Step 5: Debit sender.
        // account-service validates balance against PostgreSQL with a pessimistic lock —
        // no need for a separate checkBalance call against the Redis read model.
        Map<String, Object> debitResult = extractData(accountClient.debit(Map.of(
                "accountNo", request.getSenderAccountNo(),
                "amount", request.getAmount(),
                "referenceId", request.getReferenceId())));
        log.info("Debit successful for accountNo={}", request.getSenderAccountNo());

        BigDecimal senderNewBalance = debitResult.containsKey("newBalance")
                ? new BigDecimal(String.valueOf(debitResult.get("newBalance")))
                : null;

        // Step 6: Consume limit
        extractData(limitClient.limitConsume(Map.of(
                "accountNo", request.getSenderAccountNo(),
                "amount", request.getAmount(),
                "transferType", "INTERNAL",
                "referenceId", request.getReferenceId())));
        log.info("Limit consumed for accountNo={}", request.getSenderAccountNo());

        // Step 7: Credit receiver
        Map<String, Object> creditRequest = new HashMap<>();
        creditRequest.put("accountNo", receiverInfo.getAccountNo());
        creditRequest.put("amount", request.getAmount());
        creditRequest.put("referenceId", request.getReferenceId());
        creditRequest.put("description", request.getDescription() != null ? request.getDescription() : "");
        extractData(accountClient.credit(creditRequest));
        log.info("Credit successful for receiverAccountNo={}", request.getReceiverAccountNo());

        log.info("Internal transfer completed: referenceId={}", request.getReferenceId());

        return InternalTransferResponse.builder()
                .referenceId(request.getReferenceId())
                .status("COMPLETED")
                .receiverName(receiverInfo.getFullName())
                .completedAt(LocalDateTime.now())
                .senderNewBalance(senderNewBalance)
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
