package vn.trainocate.moneytransfer.externaltransfer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.externaltransfer.dto.ApiResponse;
import vn.trainocate.moneytransfer.externaltransfer.dto.request.ExternalInquiryRequest;
import vn.trainocate.moneytransfer.externaltransfer.dto.request.ExternalTransferRequest;
import vn.trainocate.moneytransfer.externaltransfer.dto.response.ExternalInquiryResponse;
import vn.trainocate.moneytransfer.externaltransfer.dto.response.ExternalTransferResponse;
import vn.trainocate.moneytransfer.externaltransfer.service.ExternalTransferService;

@RestController
@RequestMapping("/api/v1/external")
@RequiredArgsConstructor
public class ExternalTransferController {

    private final ExternalTransferService externalTransferService;

    @PostMapping("/inquiry")
    public ApiResponse<ExternalInquiryResponse> inquiry(@RequestBody ExternalInquiryRequest request) {
        ExternalInquiryResponse response = externalTransferService.inquiry(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/transfer")
    public ApiResponse<ExternalTransferResponse> transfer(@RequestBody ExternalTransferRequest request) {
        ExternalTransferResponse response = externalTransferService.transfer(request);
        return ApiResponse.success(response);
    }
}
