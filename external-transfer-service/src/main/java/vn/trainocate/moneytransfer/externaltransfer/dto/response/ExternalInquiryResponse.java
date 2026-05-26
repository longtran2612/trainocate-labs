package vn.trainocate.moneytransfer.externaltransfer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalInquiryResponse {

    private String accountNo;
    private String fullName;
    private String bankName;
    private String bankCode;
    private String status;
}
