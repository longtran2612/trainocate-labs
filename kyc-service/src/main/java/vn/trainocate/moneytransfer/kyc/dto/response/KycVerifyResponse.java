package vn.trainocate.moneytransfer.kyc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerifyResponse {

    private String status;
    private String message;
    private String kycTier;
}
