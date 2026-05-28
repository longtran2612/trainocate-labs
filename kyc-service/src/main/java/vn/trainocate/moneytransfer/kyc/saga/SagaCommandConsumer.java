package vn.trainocate.moneytransfer.kyc.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.kyc.dto.request.KycStatusRequest;
import vn.trainocate.moneytransfer.kyc.dto.response.KycStatusResponse;
import vn.trainocate.moneytransfer.kyc.service.KycService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCommandConsumer {

    private static final String REPLY_TOPIC = "saga-kyc-reply";

    private final KycService kycService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "saga-kyc-command", groupId = "kyc-service-saga",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        try {
            SagaCommand command = objectMapper.readValue(message, SagaCommand.class);
            log.info("[SAGA] Received command: sagaId={}, step={}, action={}",
                    command.getSagaId(), command.getStepName(), command.getAction());

            SagaReply reply = handleCommand(command);
            String replyJson = objectMapper.writeValueAsString(reply);
            kafkaTemplate.send(REPLY_TOPIC, command.getSagaId(), replyJson);

            log.info("[SAGA] Sent reply: sagaId={}, step={}, status={}",
                    reply.getSagaId(), reply.getStepName(), reply.getStatus());
        } catch (Exception e) {
            log.error("[SAGA] Failed to process command: {}", message, e);
            trySendErrorReply(message, e);
        } finally {
            ack.acknowledge();
        }
    }

    private SagaReply handleCommand(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        String accountNo = (String) payload.get("accountNo");

        try {
            KycStatusRequest request = new KycStatusRequest();
            request.setAccountNo(accountNo);

            KycStatusResponse response = kycService.getKycStatus(request);

            if (!"VERIFIED".equals(response.getStatus())) {
                return SagaReply.builder()
                        .sagaId(command.getSagaId())
                        .txId(command.getTxId())
                        .stepName(command.getStepName())
                        .status("FAILED")
                        .errorCode("KYC_NOT_VERIFIED")
                        .errorMessage("KYC not verified for sender account")
                        .build();
            }

            Map<String, Object> replyPayload = new HashMap<>();
            replyPayload.put("status", response.getStatus());
            replyPayload.put("kycTier", response.getKycTier());

            return SagaReply.builder()
                    .sagaId(command.getSagaId())
                    .txId(command.getTxId())
                    .stepName(command.getStepName())
                    .status("SUCCESS")
                    .payload(replyPayload)
                    .build();

        } catch (Exception e) {
            return SagaReply.builder()
                    .sagaId(command.getSagaId())
                    .txId(command.getTxId())
                    .stepName(command.getStepName())
                    .status("FAILED")
                    .errorCode(e instanceof vn.trainocate.moneytransfer.kyc.exception.BusinessException be
                            ? be.getCode() : "INTERNAL_ERROR")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private void trySendErrorReply(String message, Exception error) {
        try {
            SagaCommand command = objectMapper.readValue(message, SagaCommand.class);
            SagaReply reply = SagaReply.builder()
                    .sagaId(command.getSagaId())
                    .txId(command.getTxId())
                    .stepName(command.getStepName())
                    .status("FAILED")
                    .errorCode("INTERNAL_ERROR")
                    .errorMessage(error.getMessage())
                    .build();
            kafkaTemplate.send(REPLY_TOPIC, command.getSagaId(), objectMapper.writeValueAsString(reply));
        } catch (Exception ignored) {
            log.error("[SAGA] Failed to send error reply", ignored);
        }
    }
}
