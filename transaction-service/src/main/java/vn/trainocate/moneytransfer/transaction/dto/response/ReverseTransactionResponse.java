package vn.trainocate.moneytransfer.transaction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseTransactionResponse {

    private UUID reverseTxId;
    private String status;
    private BigDecimal reversedAmount;
}
