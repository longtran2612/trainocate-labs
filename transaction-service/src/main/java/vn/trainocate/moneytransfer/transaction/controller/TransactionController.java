package vn.trainocate.moneytransfer.transaction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;
import vn.trainocate.moneytransfer.transaction.dto.request.CreateTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.ReverseTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransactionHistoryRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransactionInfoRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.UpdateTransactionStatusRequest;
import vn.trainocate.moneytransfer.transaction.dto.response.ReverseTransactionResponse;
import vn.trainocate.moneytransfer.transaction.dto.response.TransactionResponse;
import vn.trainocate.moneytransfer.transaction.entity.SagaStateEntity;
import vn.trainocate.moneytransfer.transaction.repository.SagaStateRepository;
import vn.trainocate.moneytransfer.transaction.service.TransactionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final SagaStateRepository sagaStateRepository;

    @PostMapping("/create-transaction")
    public ApiResponse<TransactionResponse> createTransaction(@RequestBody CreateTransactionRequest request) {
        return ApiResponse.success(transactionService.createTransaction(request));
    }

    @PostMapping("/transaction-info")
    public ApiResponse<TransactionResponse> getTransactionInfo(@RequestBody TransactionInfoRequest request) {
        return ApiResponse.success(transactionService.getTransactionInfo(request));
    }

    @PostMapping("/reverse-transaction")
    public ApiResponse<ReverseTransactionResponse> reverseTransaction(@RequestBody ReverseTransactionRequest request) {
        return ApiResponse.success(transactionService.reverseTransaction(request));
    }

    @PostMapping("/update-status")
    public ApiResponse<TransactionResponse> updateStatus(@RequestBody UpdateTransactionStatusRequest request) {
        return ApiResponse.success(transactionService.updateStatus(request));
    }

    @PostMapping("/history")
    public ApiResponse<List<TransactionResponse>> getHistory(@RequestBody TransactionHistoryRequest request) {
        return ApiResponse.success(transactionService.getHistory(request));
    }

    /** Get saga state for a given txId — useful for debugging saga compensation */
    @PostMapping("/saga-state")
    public ApiResponse<SagaStateEntity> getSagaState(@RequestBody java.util.Map<String, String> request) {
        UUID txId = UUID.fromString(request.get("txId"));
        SagaStateEntity saga = sagaStateRepository.findByTxId(txId)
                .orElseThrow(() -> new vn.trainocate.moneytransfer.transaction.exception.BusinessException(
                        "SAGA_NOT_FOUND", "No saga found for txId: " + txId));
        return ApiResponse.success(saga);
    }
}
