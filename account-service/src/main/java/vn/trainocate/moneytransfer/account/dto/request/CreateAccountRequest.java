package vn.trainocate.moneytransfer.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "cif is required")
    private String cif;

    @NotBlank(message = "fullName is required")
    private String fullName;

    private LocalDate dob;
    private String address;
    private String mobile;
    private String email;
    private String currency;
}
