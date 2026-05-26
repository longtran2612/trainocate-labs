package vn.trainocate.moneytransfer.internaltransfer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferResponse {

    private String referenceId;
    private String status;
    private String receiverName;
    private LocalDateTime completedAt;
}
