package vn.trainocate.moneytransfer.externaltransfer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTransferRequest {

    private String referenceId;
    private String senderAccountNo;
    private String receiverAccountNo;
    private String receiverBankCode;
    private String receiverName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String channel;
    private String pin;
}
