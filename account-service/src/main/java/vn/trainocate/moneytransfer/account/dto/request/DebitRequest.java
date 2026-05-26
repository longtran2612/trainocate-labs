package vn.trainocate.moneytransfer.account.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitRequest {

    private String accountNo;
    private BigDecimal amount;
    private String holdId;
    private String referenceId;
}
