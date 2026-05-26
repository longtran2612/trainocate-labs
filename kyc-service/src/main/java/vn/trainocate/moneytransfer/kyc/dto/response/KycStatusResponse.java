package vn.trainocate.moneytransfer.kyc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {

    private String status;
    private LocalDateTime verifiedAt;
    private String kycTier;
}
