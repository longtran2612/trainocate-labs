package vn.trainocate.moneytransfer.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Use a temp username (UUID) — will be replaced by accountNo after account creation
        String tempUsername = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        userRepository.findByUsername(tempUsername).ifPresent(u -> {
            throw new BusinessException("AUTH_DUPLICATE", "Username collision, please retry");
        });

        UserEntity user = UserEntity.builder()
                .username(tempUsername)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);
        log.info("User registered: userId={}, tempUsername={}", user.getUserId(), tempUsername);

        return RegisterResponse.builder()
                .userId(user.getUserId())
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

        user.setUsername(request.getNewUsername());
        userRepository.save(user);
        log.info("Username updated: userId={}, newUsername={}", request.getUserId(), request.getNewUsername());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "Invalid username or password"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("AUTH_USER_LOCKED", "User account is " + user.getStatus().toLowerCase());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Invalid username or password");
        }

        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(user.getUserId());

        Claims claims = jwtService.validateToken(accessToken);
        LocalDateTime expiredAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(claims.getExpiration().getTime()), ZoneId.systemDefault());

        user.setAccessToken(accessToken);
        user.setRefreshToken(refreshToken);
        user.setTokenExpiredAt(expiredAt);
        userRepository.save(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiredAt(expiredAt)
                .userId(user.getUserId())
                .build();
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtService.validateToken(request.getRefreshToken());
        } catch (Exception ex) {
            throw new BusinessException("AUTH_INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
        }

        String tokenType = claims.get("type", String.class);
        if (!"REFRESH".equals(tokenType)) {
            throw new BusinessException("AUTH_INVALID_REFRESH_TOKEN", "Token is not a refresh token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "User not found"));

        if (!request.getRefreshToken().equals(user.getRefreshToken())) {
            throw new BusinessException("AUTH_INVALID_REFRESH_TOKEN", "Refresh token does not match");
        }

        String newAccessToken = jwtService.generateAccessToken(user.getUserId(), user.getUsername());
        String newRefreshToken = jwtService.generateRefreshToken(user.getUserId());

        Claims newClaims = jwtService.validateToken(newAccessToken);
        LocalDateTime expiredAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(newClaims.getExpiration().getTime()), ZoneId.systemDefault());

        user.setAccessToken(newAccessToken);
        user.setRefreshToken(newRefreshToken);
        user.setTokenExpiredAt(expiredAt);
        userRepository.save(user);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiredAt(expiredAt)
                .userId(user.getUserId())
                .build();
    }

    @Transactional
    public void logout(String accessToken) {
        UserEntity user = userRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> new BusinessException("AUTH_INVALID_TOKEN", "Token not found or already logged out"));

        user.setAccessToken(null);
        user.setRefreshToken(null);
        user.setTokenExpiredAt(null);
        userRepository.save(user);
    }

    public ValidateTokenResponse validateToken(ValidateTokenRequest request) {
        try {
            Claims claims = jwtService.validateToken(request.getToken());
            UUID userId = UUID.fromString(claims.getSubject());
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(claims.getExpiration().getTime()), ZoneId.systemDefault());

            return ValidateTokenResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .expiresAt(expiresAt)
                    .build();
        } catch (Exception ex) {
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .build();
        }
    }
}
