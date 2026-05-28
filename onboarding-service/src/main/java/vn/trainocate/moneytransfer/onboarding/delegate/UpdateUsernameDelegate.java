package vn.trainocate.moneytransfer.onboarding.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.onboarding.client.AuthServiceClient;
import vn.trainocate.moneytransfer.onboarding.client.AuthServiceClient.UpdateUsernameRequest;

import java.util.UUID;

@Slf4j
@Component("updateUsernameDelegate")
@RequiredArgsConstructor
public class UpdateUsernameDelegate implements JavaDelegate {

    private final AuthServiceClient authClient;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        log.info("[ONBOARDING] UPDATE_USERNAME: processInstanceId={}", processInstanceId);

        UUID userId    = UUID.fromString((String) execution.getVariable("userId"));
        String accountNo = (String) execution.getVariable("accountNo");

        // Use accountNo as the new username (standard banking onboarding pattern)
        var response = authClient.updateUsername(new UpdateUsernameRequest(userId, accountNo));

        if (!response.isSuccess()) {
            throw new RuntimeException("Update username failed: " + response.getMessage());
        }

        log.info("[ONBOARDING] UPDATE_USERNAME done: userId={}, newUsername={}", userId, accountNo);
    }
}
