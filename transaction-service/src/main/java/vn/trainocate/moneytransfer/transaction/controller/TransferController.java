package vn.trainocate.moneytransfer.transaction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;
import vn.trainocate.moneytransfer.transaction.dto.request.InquiryRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransferRequest;
import vn.trainocate.moneytransfer.transaction.service.TransferOrchestrationService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransferController {

    private final TransferOrchestrationService transferOrchestrationService;

    @PostMapping("/inquiry")
    public ApiResponse<Map<String, Object>> inquiry(@RequestBody InquiryRequest request) {
        return ApiResponse.success(transferOrchestrationService.inquiry(request));
    }

    @PostMapping("/transfer")
    public ApiResponse<Map<String, Object>> transfer(@RequestBody TransferRequest request) {
        return ApiResponse.success(transferOrchestrationService.transfer(request));
    }
}
