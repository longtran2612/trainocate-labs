package vn.trainocate.moneytransfer.account.readmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CQRS Read Model — stored in Redis as JSON.
 * Updated asynchronously via Debezium CDC → Kafka → {@link vn.trainocate.moneytransfer.account.event.AccountEventConsumer}.
 * Redis key: "account:{accountNo}"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReadModel {

    private String accountNo;
    private String userId;
    private String cif;
    private String fullName;
    private String address;
    private String mobile;
    private String email;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal holdBalance;
    private String currency;
    private String status;
}
