package vn.trainocate.moneytransfer.transaction.dto.request;

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
    private String bankCode;
    private String mobile;
    private String cif;
    private String currency;
    private String channel;
}
