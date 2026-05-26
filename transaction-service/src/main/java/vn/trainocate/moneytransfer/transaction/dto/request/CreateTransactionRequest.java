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
public class CreateTransactionRequest {

    private String referenceId;
    private String txType;
    private String senderAccount;
    private String receiverAccount;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private String description;
}
