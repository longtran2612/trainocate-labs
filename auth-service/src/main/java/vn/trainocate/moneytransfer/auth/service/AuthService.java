package vn.trainocate.moneytransfer.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.auth.client.KeycloakAdminClient;
import vn.trainocate.moneytransfer.auth.dto.request.LoginRequest;
import vn.trainocate.moneytransfer.auth.dto.request.RefreshTokenRequest;
import vn.trainocate.moneytransfer.auth.dto.request.RegisterRequest;
import vn.trainocate.moneytransfer.auth.dto.request.UpdateUsernameRequest;
import vn.trainocate.moneytransfer.auth.dto.request.ValidateTokenRequest;
import vn.trainocate.moneytransfer.auth.dto.response.LoginResponse;
import vn.trainocate.moneytransfer.auth.dto.response.RegisterResponse;
import vn.trainocate.moneytransfer.auth.dto.response.ValidateTokenResponse;
import vn.trainocate.moneytransfer.auth.entity.UserEntity;
import vn.trainocate.moneytransfer.auth.exception.BusinessException;
import vn.trainocate.moneytransfer.auth.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Generate a temp username (UUID-based, 16 chars) — replaced by accountNo later via updateUsername
        String tempUsername = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Create user in Keycloak first — get the assigned Keycloak UUID
        String keycloakUserId = keycloakAdminClient.createUser(
                tempUsername, request.getPassword(), request.getEmail(), null);

        // Persist a local user record pointing to the Keycloak UUID
        UUID userId = UUID.fromString(keycloakUserId);
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .username(tempUsername)
                .passwordHash("{keycloak}")   // not used for auth — Keycloak is the authority
                .phone(request.getPhone())
                .email(request.getEmail())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        log.info("User registered: userId={}, tempUsername={}", userId, tempUsername);

        return RegisterResponse.builder()
                .userId(userId)
                .username(tempUsername)
                .build();
    }

    @Transactional
    public void updateUsername(UpdateUsernameRequest request) {
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "User not found"));

        userRepository.findByUsername(request.getNewUsername()).ifPresent(existing -> {
            if (!existing.getUserId().equals(request.getUserId())) {
                throw new BusinessException("AUTH_DUPLICATE_USERNAME", "Username already taken");
            }
        });

        // Update username in Keycloak
        keycloakAdminClient.updateUsername(user.getUserId().toString(), request.getNewUsername());

        // Update local record
        user.setUsername(request.getNewUsername());
        userRepository.save(user);
        log.info("Username updated: userId={}, newUsername={}", request.getUserId(), request.getNewUsername());
    }

    public LoginResponse login(LoginRequest request) {
        // Verify user exists locally and is active before forwarding to Keycloak
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "Invalid username or password"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("AUTH_USER_LOCKED", "User account is " + user.getStatus().toLowerCase());
        }

        // Delegate authentication to Keycloak — returns Keycloak access + refresh tokens
        Map<String, Object> tokenResponse = keycloakAdminClient.loginUser(request.getUsername(), request.getPassword());

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        // Parse expiry from the Keycloak JWT without verifying the signature
        LocalDateTime expiredAt = parseExpiryFromJwt(accessToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiredAt(expiredAt)
                .userId(user.getUserId())
                .build();
    }

    public LoginResponse refreshToken(RefreshTokenRequest request) {
        Map<String, Object> tokenResponse = keycloakAdminClient.refreshToken(request.getRefreshToken());

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        LocalDateTime expiredAt = parseExpiryFromJwt(accessToken);

        // Resolve userId from the new access token sub claim
        UUID userId = parseUserIdFromJwt(accessToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiredAt(expiredAt)
                .userId(userId)
                .build();
    }

    public void logout(String accessToken) {
        // Resolve the Keycloak user UUID from the Bearer token, then revoke all sessions
        String keycloakUserId = parseUserIdFromJwt(accessToken).toString();
        keycloakAdminClient.revokeUserSessions(keycloakUserId);
    }

    public ValidateTokenResponse validateToken(ValidateTokenRequest request) {
        try {
            Map<String, Object> claims = decodeJwtPayload(request.getToken());
            UUID userId = UUID.fromString((String) claims.get("sub"));
            LocalDateTime expiresAt = parseExpiryFromClaims(claims);

            return ValidateTokenResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .expiresAt(expiresAt)
                    .build();
        } catch (Exception ex) {
            log.debug("Token validation failed: {}", ex.getMessage());
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .build();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new BusinessException("AUTH_INVALID_TOKEN", "Malformed JWT");
            }
            // Add padding so Base64 decoder won't complain about missing '='
            String padded = parts[1] + "==";
            byte[] decoded = Base64.getUrlDecoder().decode(padded);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, Map.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AUTH_INVALID_TOKEN", "Failed to decode token");
        }
    }

    private UUID parseUserIdFromJwt(String token) {
        Map<String, Object> claims = decodeJwtPayload(token);
        String sub = (String) claims.get("sub");
        if (sub == null) {
            throw new BusinessException("AUTH_INVALID_TOKEN", "Token missing sub claim");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("AUTH_INVALID_TOKEN", "Token sub is not a valid UUID");
        }
    }

    private LocalDateTime parseExpiryFromJwt(String token) {
        return parseExpiryFromClaims(decodeJwtPayload(token));
    }

    private LocalDateTime parseExpiryFromClaims(Map<String, Object> claims) {
        Number exp = (Number) claims.get("exp");
        if (exp == null) return null;
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(exp.longValue()), ZoneId.systemDefault());
    }
}
