package vn.trainocate.moneytransfer.onboarding.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.onboarding.client.AuthServiceClient;
import vn.trainocate.moneytransfer.onboarding.client.AuthServiceClient.RegisterRequest;
import vn.trainocate.moneytransfer.onboarding.client.AuthServiceClient.RegisterResponse;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

@Slf4j
@Component("registerUserDelegate")
@RequiredArgsConstructor
public class RegisterUserDelegate implements JavaDelegate {

    private final AuthServiceClient authClient;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        log.info("[ONBOARDING] REGISTER_USER: processInstanceId={}", processInstanceId);

        String password = (String) execution.getVariable("password");
        String phone    = (String) execution.getVariable("phone");
        String email    = (String) execution.getVariable("email");

        ApiResponse<RegisterResponse> response = authClient.register(
                new RegisterRequest(password, phone, email));

        if (!response.isSuccess() || response.getData() == null) {
            throw new RuntimeException("Register user failed: " + response.getMessage());
        }

        RegisterResponse data = response.getData();
        execution.setVariable("userId", data.getUserId().toString());
        execution.setVariable("username", data.getUsername());

        log.info("[ONBOARDING] REGISTER_USER done: userId={}, username={}",
                data.getUserId(), data.getUsername());
    }
}
