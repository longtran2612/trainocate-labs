package vn.trainocate.moneytransfer.transaction.saga.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaCommand {

    private String sagaId;
    private String txId;
    private String stepName;
    private String action;
    private Map<String, Object> payload;
}
