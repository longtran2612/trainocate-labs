package vn.trainocate.moneytransfer.transaction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;
import vn.trainocate.moneytransfer.transaction.dto.request.CreateTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.ReverseTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransactionInfoRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.UpdateTransactionStatusRequest;
import vn.trainocate.moneytransfer.transaction.dto.response.ReverseTransactionResponse;
import vn.trainocate.moneytransfer.transaction.dto.response.TransactionResponse;
import vn.trainocate.moneytransfer.transaction.service.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

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
}
