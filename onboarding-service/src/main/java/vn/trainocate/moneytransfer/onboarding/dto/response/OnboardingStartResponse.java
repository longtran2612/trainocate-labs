package vn.trainocate.moneytransfer.onboarding.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStartResponse {

    private String processInstanceId;
    private String status;
}
