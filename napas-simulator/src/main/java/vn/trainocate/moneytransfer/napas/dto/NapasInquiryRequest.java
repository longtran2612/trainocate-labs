package vn.trainocate.moneytransfer.napas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NapasInquiryRequest {

    private String bankCode;
    private String accountNo;
}
