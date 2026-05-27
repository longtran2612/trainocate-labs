package vn.trainocate.moneytransfer.napas.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.napas.dto.ApiResponse;
import vn.trainocate.moneytransfer.napas.dto.NapasInquiryRequest;
import vn.trainocate.moneytransfer.napas.dto.NapasInquiryResponse;
import vn.trainocate.moneytransfer.napas.dto.NapasTransferRequest;
import vn.trainocate.moneytransfer.napas.dto.NapasTransferResponse;
import vn.trainocate.moneytransfer.napas.service.NapasSimulatorService;

@RestController
@RequestMapping("/api/v1/napas")
@RequiredArgsConstructor
public class NapasController {

    private final NapasSimulatorService napasSimulatorService;

    @PostMapping("/inquiry")
    public ApiResponse<NapasInquiryResponse> inquiry(@RequestBody NapasInquiryRequest request) {
        return ApiResponse.success(napasSimulatorService.inquiry(request));
    }

    @PostMapping("/transfer")
    public ApiResponse<NapasTransferResponse> transfer(@RequestBody NapasTransferRequest request) {
        return ApiResponse.success(napasSimulatorService.transfer(request));
    }
}
