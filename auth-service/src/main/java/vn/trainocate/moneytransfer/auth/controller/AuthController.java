package vn.trainocate.moneytransfer.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.auth.dto.ApiResponse;
import vn.trainocate.moneytransfer.auth.dto.request.LoginRequest;
import vn.trainocate.moneytransfer.auth.dto.request.RefreshTokenRequest;
import vn.trainocate.moneytransfer.auth.dto.request.RegisterRequest;
import vn.trainocate.moneytransfer.auth.dto.request.UpdateUsernameRequest;
import vn.trainocate.moneytransfer.auth.dto.request.ValidateTokenRequest;
import vn.trainocate.moneytransfer.auth.dto.response.LoginResponse;
import vn.trainocate.moneytransfer.auth.dto.response.RegisterResponse;
import vn.trainocate.moneytransfer.auth.dto.response.ValidateTokenResponse;
import vn.trainocate.moneytransfer.auth.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/update-username")
    public ApiResponse<Void> updateUsername(@RequestBody UpdateUsernameRequest request) {
        authService.updateUsername(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh-token")
    public ApiResponse<LoginResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authService.logout(token);
        return ApiResponse.success(null);
    }

    @PostMapping("/validate-token")
    public ApiResponse<ValidateTokenResponse> validateToken(@RequestBody ValidateTokenRequest request) {
        ValidateTokenResponse response = authService.validateToken(request);
        return ApiResponse.success(response);
    }
}
