package vn.trainocate.moneytransfer.account.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal holdBalance;
    private String currency;
}
