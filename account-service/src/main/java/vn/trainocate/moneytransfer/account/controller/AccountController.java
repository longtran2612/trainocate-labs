package vn.trainocate.moneytransfer.account.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.account.dto.ApiResponse;
import vn.trainocate.moneytransfer.account.dto.request.CheckBalanceRequest;
import vn.trainocate.moneytransfer.account.dto.request.CreateAccountRequest;
import vn.trainocate.moneytransfer.account.dto.request.CreditRequest;
import vn.trainocate.moneytransfer.account.dto.request.CustomerInfoRequest;
import vn.trainocate.moneytransfer.account.dto.request.DebitRequest;
import vn.trainocate.moneytransfer.account.dto.request.InquiryRequest;
import vn.trainocate.moneytransfer.account.dto.request.UpdateAccountRequest;
import vn.trainocate.moneytransfer.account.dto.response.BalanceResponse;
import vn.trainocate.moneytransfer.account.dto.response.CustomerInfoResponse;
import vn.trainocate.moneytransfer.account.dto.response.DebitCreditResponse;
import vn.trainocate.moneytransfer.account.dto.response.InquiryResponse;
import vn.trainocate.moneytransfer.account.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ApiResponse<List<CustomerInfoResponse>> getAllAccounts() {
        List<CustomerInfoResponse> response = accountService.getAllAccounts();
        return ApiResponse.success(response);
    }

    @PostMapping
    public ApiResponse<CustomerInfoResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        CustomerInfoResponse response = accountService.createAccount(request);
        return ApiResponse.success(response);
    }

    @PutMapping
    public ApiResponse<CustomerInfoResponse> updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        CustomerInfoResponse response = accountService.updateAccount(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/get-customer-info")
    public ApiResponse<CustomerInfoResponse> getCustomerInfo(@RequestBody CustomerInfoRequest request) {
        CustomerInfoResponse response = accountService.getCustomerInfo(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/check-balance")
    public ApiResponse<BalanceResponse> checkBalance(@RequestBody CheckBalanceRequest request) {
        BalanceResponse response = accountService.checkBalance(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/inquiry")
    public ApiResponse<InquiryResponse> inquiry(@RequestBody InquiryRequest request) {
        InquiryResponse response = accountService.inquiry(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/debit")
    public ApiResponse<DebitCreditResponse> debit(@RequestBody DebitRequest request) {
        DebitCreditResponse response = accountService.debit(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/credit")
    public ApiResponse<DebitCreditResponse> credit(@RequestBody CreditRequest request) {
        DebitCreditResponse response = accountService.credit(request);
        return ApiResponse.success(response);
    }
}
