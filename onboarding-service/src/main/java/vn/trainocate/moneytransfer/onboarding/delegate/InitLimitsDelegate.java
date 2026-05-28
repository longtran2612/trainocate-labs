package vn.trainocate.moneytransfer.onboarding.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import vn.trainocate.moneytransfer.onboarding.client.LimitServiceClient;
import vn.trainocate.moneytransfer.onboarding.client.LimitServiceClient.LimitInitRequest;

@Slf4j
@Component("initLimitsDelegate")
@RequiredArgsConstructor
public class InitLimitsDelegate implements JavaDelegate {

    private final LimitServiceClient limitClient;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        log.info("[ONBOARDING] INIT_LIMITS: processInstanceId={}", processInstanceId);

        String accountNo = (String) execution.getVariable("accountNo");

        var response = limitClient.initLimits(new LimitInitRequest(accountNo));

        if (!response.isSuccess()) {
            throw new RuntimeException("Initialize limits failed: " + response.getMessage());
        }

        log.info("[ONBOARDING] INIT_LIMITS done: accountNo={}", accountNo);
    }
}
