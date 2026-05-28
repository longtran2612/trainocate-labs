package vn.trainocate.moneytransfer.kyc.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaReply {

    private String sagaId;
    private String txId;
    private String stepName;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> payload;
}
