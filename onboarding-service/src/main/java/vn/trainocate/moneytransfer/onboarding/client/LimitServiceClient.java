package vn.trainocate.moneytransfer.onboarding.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

@FeignClient(name = "limit-service")
public interface LimitServiceClient {

    @PostMapping("/api/v1/limits/init")
    ApiResponse<Void> initLimits(@RequestBody LimitInitRequest request);

    // ── Local DTO ─────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    class LimitInitRequest {
        private String accountNo;
    }
}
