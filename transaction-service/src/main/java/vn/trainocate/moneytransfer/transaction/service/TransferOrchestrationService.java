package vn.trainocate.moneytransfer.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.transaction.client.ExternalTransferClient;
import vn.trainocate.moneytransfer.transaction.client.InternalTransferClient;
import vn.trainocate.moneytransfer.transaction.saga.SagaKafkaOrchestrator;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;
import vn.trainocate.moneytransfer.transaction.dto.request.CreateTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.InquiryRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransferRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.UpdateTransactionStatusRequest;
import vn.trainocate.moneytransfer.transaction.dto.response.TransactionResponse;
import vn.trainocate.moneytransfer.transaction.exception.BusinessException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferOrchestrationService {

    private static final String VIKKIBANK_CODE = "970406";

    private final InternalTransferClient internalTransferClient;
    private final ExternalTransferClient externalTransferClient;
    private final TransactionService transactionService;
    private final SagaKafkaOrchestrator sagaKafkaOrchestrator;
    private final ObjectMapper objectMapper;

    @Value("${app.our-bank-code:" + VIKKIBANK_CODE + "}")
    private String ourBankCode;

    @Value("${saga.timeout-seconds:30}")
    private int sagaTimeoutSeconds;

    @SuppressWarnings("unchecked")
    public Map<String, Object> inquiry(InquiryRequest request) {
        log.info("Processing inquiry: accountNo={}, bankCode={}", request.getAccountNo(), request.getBankCode());

        ApiResponse response;
        if (isOurBank(request.getBankCode())) {
            Map<String, Object> internalRequest = new HashMap<>();
            internalRequest.put("accountNo", request.getAccountNo());
            if (request.getMobile() != null) internalRequest.put("mobile", request.getMobile());
            if (request.getCif() != null) internalRequest.put("cif", request.getCif());
            if (request.getCurrency() != null) internalRequest.put("currency", request.getCurrency());

            response = internalTransferClient.inquiry(internalRequest);
        } else {
            Map<String, Object> externalRequest = new HashMap<>();
            externalRequest.put("accountNo", request.getAccountNo());
            externalRequest.put("bankCode", request.getBankCode());
            if (request.getChannel() != null) externalRequest.put("channel", request.getChannel());

            response = externalTransferClient.inquiry(externalRequest);
        }

        return extractData(response);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> transfer(TransferRequest request) {
        log.info("Processing transfer: referenceId={}, bankCode={}, sender={}, receiver={}, amount={}",
                request.getReferenceId(), request.getBankCode(),
                request.getSenderAccountNo(), request.getReceiverAccountNo(), request.getAmount());

        boolean internal = isOurBank(request.getBankCode());
        String txType = internal ? "INTERNAL_TRANSFER" : "EXTERNAL_TRANSFER";

        TransactionResponse tx = transactionService.createTransaction(CreateTransactionRequest.builder()
                .referenceId(request.getReferenceId())
                .txType(txType)
                .senderAccount(request.getSenderAccountNo())
                .receiverAccount(request.getReceiverAccountNo())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .build());
        log.info("Transaction created: txId={}, type={}", tx.getTxId(), txType);

        try {
            Map<String, Object> transferResult;

            if (internal) {
                CompletableFuture<Map<String, Object>> sagaFuture = sagaKafkaOrchestrator.startSaga(tx, request);

                try {
                    transferResult = sagaFuture.get(sagaTimeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    transactionService.updateStatus(UpdateTransactionStatusRequest.builder()
                            .txId(tx.getTxId()).status("FAILED").build());
                    throw new BusinessException("SAGA_TIMEOUT",
                            "Transfer timed out after " + sagaTimeoutSeconds + " seconds");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof BusinessException be) throw be;
                    throw new BusinessException("SAGA_FAILED", "Transfer failed: " + cause.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("SAGA_INTERRUPTED", "Transfer interrupted");
                }

                transactionService.updateStatus(UpdateTransactionStatusRequest.builder()
                        .txId(tx.getTxId()).status("COMPLETED").build());
                log.info("Internal transfer saga completed via Kafka: txId={}", tx.getTxId());
            } else {
                Map<String, Object> externalRequest = new HashMap<>();
                externalRequest.put("referenceId", request.getReferenceId());
                externalRequest.put("senderAccountNo", request.getSenderAccountNo());
                externalRequest.put("receiverAccountNo", request.getReceiverAccountNo());
                externalRequest.put("receiverBankCode", request.getBankCode());
                if (request.getReceiverName() != null) externalRequest.put("receiverName", request.getReceiverName());
                externalRequest.put("amount", request.getAmount());
                externalRequest.put("currency", request.getCurrency());
                if (request.getDescription() != null) externalRequest.put("description", request.getDescription());
                if (request.getChannel() != null) externalRequest.put("channel", request.getChannel());
                if (request.getPin() != null) externalRequest.put("pin", request.getPin());

                transferResult = extractData(externalTransferClient.transfer(externalRequest));

                transactionService.updateStatus(UpdateTransactionStatusRequest.builder()
                        .txId(tx.getTxId()).status("COMPLETED").build());
                log.info("External transfer completed: txId={}, napasRef={}", tx.getTxId(), transferResult.get("napasRef"));
            }

            transferResult.put("txId", tx.getTxId().toString());
            return transferResult;

        } catch (BusinessException e) {
            log.error("Transfer failed: txId={}, code={}", tx.getTxId(), e.getCode(), e);
            transactionService.updateStatus(UpdateTransactionStatusRequest.builder()
                    .txId(tx.getTxId()).status("FAILED").build());
            throw e;
        } catch (Exception e) {
            log.error("Transfer failed unexpectedly: txId={}", tx.getTxId(), e);
            transactionService.updateStatus(UpdateTransactionStatusRequest.builder()
                    .txId(tx.getTxId()).status("FAILED").build());
            throw new BusinessException("TRANSFER_FAILED", "Transfer processing failed: " + e.getMessage());
        }
    }

    private boolean isOurBank(String bankCode) {
        return bankCode == null || bankCode.isBlank() || ourBankCode.equals(bankCode);
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
