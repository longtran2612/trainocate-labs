package vn.trainocate.moneytransfer.onboarding.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnboardingStatusResponse {

    /** "RUNNING" | "COMPLETED" | "FAILED" */
    private String status;

    private String processInstanceId;

    /** Name of the BPMN task currently active (e.g. "registerUserTask") */
    private String currentActivity;

    /** Key output variables: userId, accountNo, kycTier */
    private Map<String, Object> variables;

    /** Error detail when status = FAILED */
    private String errorMessage;
}
