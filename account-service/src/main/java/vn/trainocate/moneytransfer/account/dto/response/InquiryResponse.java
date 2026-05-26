package vn.trainocate.moneytransfer.account.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryResponse {

    private String accountNo;
    private String fullName;
    private String status;
}
