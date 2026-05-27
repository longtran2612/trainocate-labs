package vn.trainocate.moneytransfer.limit.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LimitReleaseRequest {
    private String accountNo;
    private BigDecimal amount;
    private String transferType;
    private String txId;
}
