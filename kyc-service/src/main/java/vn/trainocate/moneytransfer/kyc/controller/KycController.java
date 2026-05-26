package vn.trainocate.moneytransfer.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.kyc.dto.ApiResponse;
import vn.trainocate.moneytransfer.kyc.dto.request.KycInfoRequest;
import vn.trainocate.moneytransfer.kyc.dto.request.KycStatusRequest;
import vn.trainocate.moneytransfer.kyc.dto.request.KycTierRequest;
import vn.trainocate.moneytransfer.kyc.dto.request.KycVerifyRequest;
import vn.trainocate.moneytransfer.kyc.dto.response.KycInfoResponse;
import vn.trainocate.moneytransfer.kyc.dto.response.KycStatusResponse;
import vn.trainocate.moneytransfer.kyc.dto.response.KycTierResponse;
import vn.trainocate.moneytransfer.kyc.dto.response.KycVerifyResponse;
import vn.trainocate.moneytransfer.kyc.service.KycService;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping("/get-kyc-info")
    public ApiResponse<KycInfoResponse> getKycInfo(@RequestBody KycInfoRequest request) {
        return ApiResponse.success(kycService.getKycInfo(request));
    }

    @PostMapping("/get-kyc-tier")
    public ApiResponse<KycTierResponse> getKycTier(@RequestBody KycTierRequest request) {
        return ApiResponse.success(kycService.getKycTier(request));
    }

    @PostMapping("/get-kyc-status")
    public ApiResponse<KycStatusResponse> getKycStatus(@RequestBody KycStatusRequest request) {
        return ApiResponse.success(kycService.getKycStatus(request));
    }

    @PostMapping("/verify")
    public ApiResponse<KycVerifyResponse> verify(@RequestBody KycVerifyRequest request) {
        return ApiResponse.success(kycService.verify(request));
    }
}
