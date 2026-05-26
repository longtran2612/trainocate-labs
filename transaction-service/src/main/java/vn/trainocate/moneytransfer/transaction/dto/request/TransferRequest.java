package vn.trainocate.moneytransfer.transaction.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    private String referenceId;
    private String senderAccountNo;
    private String receiverAccountNo;
    private String bankCode;
    private String receiverName;
    private BigDecimal amount;
    @Builder.Default
    private String currency = "VND";
    private String description;
    private String channel;
    private String pin;
}
