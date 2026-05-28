package vn.trainocate.moneytransfer.onboarding.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

import java.util.UUID;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @PostMapping("/api/v1/auth/register")
    ApiResponse<RegisterResponse> register(@RequestBody RegisterRequest request);

    @PostMapping("/api/v1/auth/update-username")
    ApiResponse<Void> updateUsername(@RequestBody UpdateUsernameRequest request);

    // ── Local DTOs ────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    class RegisterRequest {
        private String password;
        private String phone;
        private String email;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    class RegisterResponse {
        private UUID userId;
        private String username;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    class UpdateUsernameRequest {
        private UUID userId;
        private String newUsername;
    }
}
