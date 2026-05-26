package vn.trainocate.moneytransfer.limit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitConsumeResponse {

    private String status;
    private BigDecimal remainingDaily;
    private BigDecimal remainingMonthly;
}
