package vn.trainocate.moneytransfer.limit.dto.response;

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
public class LimitInfoResponse {

    private String accountNo;
    private String kycTier;
    private String transferType;
    private BigDecimal singleLimit;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal usedDaily;
    private BigDecimal usedMonthly;
    private LocalDateTime resetAt;
}
