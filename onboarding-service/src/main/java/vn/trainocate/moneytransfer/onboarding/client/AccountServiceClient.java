package vn.trainocate.moneytransfer.onboarding.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

import java.time.LocalDate;
import java.util.UUID;

@FeignClient(name = "account-service")
public interface AccountServiceClient {

    @PostMapping("/api/v1/accounts")
    ApiResponse<CreateAccountResponse> createAccount(@RequestBody CreateAccountRequest request);

    // ── Local DTOs ────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    class CreateAccountRequest {
        private UUID userId;
        private String cif;
        private String fullName;
        private LocalDate dob;
        private String address;
        private String mobile;
        private String email;
        private String currency;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    class CreateAccountResponse {
        private String accountNo;
    }
}
