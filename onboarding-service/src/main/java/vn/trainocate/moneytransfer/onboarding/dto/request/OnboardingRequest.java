package vn.trainocate.moneytransfer.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OnboardingRequest {

    // ── Auth: register ────────────────────────────────────────────
    @NotBlank
    private String password;

    @NotBlank
    private String phone;

    @NotBlank
    private String email;

    // ── Account: create ──────────────────────────────────────────
    @NotBlank
    private String cif;

    @NotBlank
    private String fullName;

    private String dob;       // ISO date string (yyyy-MM-dd), parsed to LocalDate in delegate

    private String address;
    private String mobile;
    private String currency;

    // ── KYC: verify ──────────────────────────────────────────────
    @NotBlank
    private String idNumber;

    @NotBlank
    private String idType;
}
