package vn.trainocate.moneytransfer.onboarding.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.onboarding.client.KycServiceClient;
import vn.trainocate.moneytransfer.onboarding.client.KycServiceClient.KycVerifyRequest;
import vn.trainocate.moneytransfer.onboarding.client.KycServiceClient.KycVerifyResponse;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

import java.util.UUID;

@Slf4j
@Component("verifyKycDelegate")
@RequiredArgsConstructor
public class VerifyKycDelegate implements JavaDelegate {

    private final KycServiceClient kycClient;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        log.info("[ONBOARDING] VERIFY_KYC: processInstanceId={}", processInstanceId);

        UUID userId    = UUID.fromString((String) execution.getVariable("userId"));
        String accountNo = (String) execution.getVariable("accountNo");
        String idNumber  = (String) execution.getVariable("idNumber");
        String idType    = (String) execution.getVariable("idType");
        String fullName  = (String) execution.getVariable("fullName");

        ApiResponse<KycVerifyResponse> response = kycClient.verify(
                new KycVerifyRequest(userId, accountNo, idNumber, idType, fullName));

        if (!response.isSuccess() || response.getData() == null) {
            throw new RuntimeException("KYC verification failed: " + response.getMessage());
        }

        KycVerifyResponse data = response.getData();
        execution.setVariable("kycTier", data.getKycTier());

        log.info("[ONBOARDING] VERIFY_KYC done: kycTier={}", data.getKycTier());
    }
}
