package vn.trainocate.moneytransfer.account.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.account.command.AccountCommandService;
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
import vn.trainocate.moneytransfer.account.query.AccountQueryService;

import java.util.List;

/**
 * REST controller — routes requests to Command or Query side (CQRS).
 *
 * <ul>
 *   <li><b>Commands</b> (writes): POST /, PUT /, POST /debit, POST /credit
 *       → {@link AccountCommandService} → PostgreSQL → Debezium CDC → Kafka → Redis
 *   <li><b>Queries</b> (reads): GET /, POST /inquiry, /check-balance, /get-customer-info
 *       → {@link AccountQueryService} → Redis (or PostgreSQL fallback)
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    // ── Command Side (write) ────────────────────────────────────────────────
    private final AccountCommandService accountCommandService;

    // ── Query Side (read) ───────────────────────────────────────────────────
    private final AccountQueryService accountQueryService;

    // ── Commands ─────────────────────────────────────────────────────────────

    @PostMapping
    public ApiResponse<CustomerInfoResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.success(accountCommandService.createAccount(request));
    }

    @PutMapping
    public ApiResponse<CustomerInfoResponse> updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return ApiResponse.success(accountCommandService.updateAccount(request));
    }

    @PostMapping("/debit")
    public ApiResponse<DebitCreditResponse> debit(@RequestBody DebitRequest request) {
        return ApiResponse.success(accountCommandService.debit(request));
    }

    @PostMapping("/credit")
    public ApiResponse<DebitCreditResponse> credit(@RequestBody CreditRequest request) {
        return ApiResponse.success(accountCommandService.credit(request));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<List<CustomerInfoResponse>> getAllAccounts() {
        return ApiResponse.success(accountQueryService.getAllAccounts());
    }

    @PostMapping("/get-customer-info")
    public ApiResponse<CustomerInfoResponse> getCustomerInfo(@RequestBody CustomerInfoRequest request) {
        return ApiResponse.success(accountQueryService.getCustomerInfo(request));
    }

    @PostMapping("/check-balance")
    public ApiResponse<BalanceResponse> checkBalance(@RequestBody CheckBalanceRequest request) {
        return ApiResponse.success(accountQueryService.checkBalance(request));
    }

    @PostMapping("/inquiry")
    public ApiResponse<InquiryResponse> inquiry(@RequestBody InquiryRequest request) {
        return ApiResponse.success(accountQueryService.inquiry(request));
    }
}
