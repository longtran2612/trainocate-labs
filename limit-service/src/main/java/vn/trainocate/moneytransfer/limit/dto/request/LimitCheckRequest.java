package vn.trainocate.moneytransfer.limit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitCheckRequest {

    private String accountNo;
    private BigDecimal amount;
    private String transferType;
    @Builder.Default
    private String currency = "VND";
}
