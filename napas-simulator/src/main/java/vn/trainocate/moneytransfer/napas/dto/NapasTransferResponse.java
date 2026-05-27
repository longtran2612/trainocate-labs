package vn.trainocate.moneytransfer.napas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NapasTransferResponse {

    private String napasRef;
    private String referenceId;
    private String responseCode;
    private String responseMessage;
    private String receiverName;
    private LocalDateTime processedAt;
}
