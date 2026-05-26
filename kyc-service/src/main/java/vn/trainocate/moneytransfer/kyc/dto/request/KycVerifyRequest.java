package vn.trainocate.moneytransfer.kyc.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KycVerifyRequest {

    private UUID userId;
    private String idNumber;
    private String idType;
    private String fullName;
}
