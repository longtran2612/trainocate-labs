package vn.trainocate.moneytransfer.externaltransfer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTransferResponse {

    private String referenceId;
    private String status;
    private String napasRef;
    private String receiverName;
    private LocalDateTime completedAt;
}
