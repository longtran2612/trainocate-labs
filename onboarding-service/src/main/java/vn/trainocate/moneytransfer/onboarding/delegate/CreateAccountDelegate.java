package vn.trainocate.moneytransfer.onboarding.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.onboarding.client.AccountServiceClient;
import vn.trainocate.moneytransfer.onboarding.client.AccountServiceClient.CreateAccountRequest;
import vn.trainocate.moneytransfer.onboarding.client.AccountServiceClient.CreateAccountResponse;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component("createAccountDelegate")
@RequiredArgsConstructor
public class CreateAccountDelegate implements JavaDelegate {

    private final AccountServiceClient accountClient;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        log.info("[ONBOARDING] CREATE_ACCOUNT: processInstanceId={}", processInstanceId);

        UUID userId   = UUID.fromString((String) execution.getVariable("userId"));
        String cif      = (String) execution.getVariable("cif");
        String fullName = (String) execution.getVariable("fullName");
        String dobStr   = (String) execution.getVariable("dob");
        String address  = (String) execution.getVariable("address");
        String mobile   = (String) execution.getVariable("mobile");
        String email    = (String) execution.getVariable("email");
        String currency = (String) execution.getVariable("currency");

        LocalDate dob = (dobStr != null && !dobStr.isBlank()) ? LocalDate.parse(dobStr) : null;

        ApiResponse<CreateAccountResponse> response = accountClient.createAccount(
                new CreateAccountRequest(userId, cif, fullName, dob, address, mobile, email, currency));

        if (!response.isSuccess() || response.getData() == null) {
            throw new RuntimeException("Create account failed: " + response.getMessage());
        }

        String accountNo = response.getData().getAccountNo();
        execution.setVariable("accountNo", accountNo);

        log.info("[ONBOARDING] CREATE_ACCOUNT done: accountNo={}", accountNo);
    }
}
