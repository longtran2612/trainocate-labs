package vn.trainocate.moneytransfer.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountRequest {

    @NotBlank(message = "accountNo is required")
    private String accountNo;

    private String fullName;
    private LocalDate dob;
    private String address;
    private String mobile;
    private String email;
}
