package vn.trainocate.moneytransfer.internaltransfer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferRequest {

    private String referenceId;
    private String senderAccountNo;
    private String receiverAccountNo;
    private BigDecimal amount;
    @Builder.Default
    private String currency = "VND";
    private String description;
    private String pin;
}
