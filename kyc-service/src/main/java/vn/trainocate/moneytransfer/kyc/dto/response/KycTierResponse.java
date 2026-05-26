package vn.trainocate.moneytransfer.kyc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycTierResponse {

    private String kycTier;
    private BigDecimal singleLimit;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}
