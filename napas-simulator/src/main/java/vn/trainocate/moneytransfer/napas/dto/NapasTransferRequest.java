package vn.trainocate.moneytransfer.napas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NapasTransferRequest {

    private String referenceId;
    private String senderBankCode;
    private String senderAccountNo;
    private String senderName;
    private String receiverBankCode;
    private String receiverAccountNo;
    private String receiverName;
    private BigDecimal amount;
    private String currency;
    private String description;
}
