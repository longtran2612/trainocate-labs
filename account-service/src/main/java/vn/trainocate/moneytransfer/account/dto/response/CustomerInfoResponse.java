package vn.trainocate.moneytransfer.account.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInfoResponse {

    private String accountNo;
    private UUID userId;
    private String cif;
    private String fullName;
    private LocalDate dob;
    private String address;
    private String mobile;
    private String email;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal holdBalance;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
}
