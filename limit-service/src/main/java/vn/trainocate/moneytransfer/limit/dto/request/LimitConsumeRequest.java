package vn.trainocate.moneytransfer.limit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LimitConsumeRequest {

    private String accountNo;
    private BigDecimal amount;
    private String transferType;
    private String txId;
}
