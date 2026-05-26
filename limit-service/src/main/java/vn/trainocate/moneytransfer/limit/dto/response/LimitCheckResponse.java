package vn.trainocate.moneytransfer.limit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LimitCheckResponse {

    private boolean allowed;
    private String reason;
    private BigDecimal remainingDaily;
    private BigDecimal remainingMonthly;
    private BigDecimal remainingSingle;
}
