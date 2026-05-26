package vn.trainocate.moneytransfer.kyc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycInfoResponse {

    private UUID kycId;
    private UUID userId;
    private String kycTier;
    private String fullName;
    private String idNumber;
    private String idType;
    private LocalDateTime verifiedAt;
    private String status;
}
