package vn.trainocate.moneytransfer.externaltransfer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalInquiryRequest {

    private String accountNo;
    private String bankCode;
    private String channel;
}
