package vn.trainocate.moneytransfer.account.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitCreditResponse {

    private String txRef;
    private BigDecimal newBalance;
    private LocalDateTime timestamp;
}
