package vn.trainocate.moneytransfer.account.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryRequest {

    private String accountNo;
    private String mobile;
    private String cif;
    private String currency;
}
