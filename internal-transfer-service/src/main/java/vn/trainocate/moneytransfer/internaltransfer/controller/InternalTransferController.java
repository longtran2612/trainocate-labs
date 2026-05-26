package vn.trainocate.moneytransfer.internaltransfer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.internaltransfer.dto.ApiResponse;
import vn.trainocate.moneytransfer.internaltransfer.dto.request.InternalInquiryRequest;
import vn.trainocate.moneytransfer.internaltransfer.dto.request.InternalTransferRequest;
import vn.trainocate.moneytransfer.internaltransfer.dto.response.InternalInquiryResponse;
import vn.trainocate.moneytransfer.internaltransfer.dto.response.InternalTransferResponse;
import vn.trainocate.moneytransfer.internaltransfer.service.InternalTransferService;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalTransferController {

    private final InternalTransferService internalTransferService;

    @PostMapping("/inquiry")
    public ApiResponse<InternalInquiryResponse> inquiry(@RequestBody InternalInquiryRequest request) {
        InternalInquiryResponse response = internalTransferService.inquiry(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/transfer")
    public ApiResponse<InternalTransferResponse> transfer(@RequestBody InternalTransferRequest request) {
        InternalTransferResponse response = internalTransferService.transfer(request);
        return ApiResponse.success(response);
    }
}
