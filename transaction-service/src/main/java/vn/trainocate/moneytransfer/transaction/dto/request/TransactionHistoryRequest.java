package vn.trainocate.moneytransfer.transaction.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryRequest {

    private String accountNo;
}
