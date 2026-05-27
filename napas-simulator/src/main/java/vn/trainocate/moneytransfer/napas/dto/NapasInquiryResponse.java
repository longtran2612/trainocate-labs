package vn.trainocate.moneytransfer.napas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NapasInquiryResponse {

    private String accountNo;
    private String accountName;
    private String bankCode;
    private String bankName;
    private String responseCode;
    private String responseMessage;
}
