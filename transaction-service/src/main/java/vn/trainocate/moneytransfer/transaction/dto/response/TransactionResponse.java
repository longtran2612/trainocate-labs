package vn.trainocate.moneytransfer.transaction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private UUID txId;
    private String referenceId;
    private String txType;
    private String status;
    private String senderAccount;
    private String receiverAccount;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private String description;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
}
