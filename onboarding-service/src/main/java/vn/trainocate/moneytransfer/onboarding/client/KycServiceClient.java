package vn.trainocate.moneytransfer.onboarding.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

import java.util.UUID;

@FeignClient(name = "kyc-service")
public interface KycServiceClient {

    @PostMapping("/api/v1/kyc/verify")
    ApiResponse<KycVerifyResponse> verify(@RequestBody KycVerifyRequest request);

    // ── Local DTOs ────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    class KycVerifyRequest {
        private UUID userId;
        private String accountNo;
        private String idNumber;
        private String idType;
        private String fullName;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    class KycVerifyResponse {
        private String status;
        private String message;
        private String kycTier;
    }
}
