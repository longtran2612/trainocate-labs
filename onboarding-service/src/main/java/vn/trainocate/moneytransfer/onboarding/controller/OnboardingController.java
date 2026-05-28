package vn.trainocate.moneytransfer.onboarding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.onboarding.dto.ApiResponse;
import vn.trainocate.moneytransfer.onboarding.dto.request.OnboardingRequest;
import vn.trainocate.moneytransfer.onboarding.dto.response.OnboardingStartResponse;
import vn.trainocate.moneytransfer.onboarding.dto.response.OnboardingStatusResponse;
import vn.trainocate.moneytransfer.onboarding.service.OnboardingService;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /**
     * Start the onboarding workflow.
     * Returns immediately with processInstanceId — process executes asynchronously.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<OnboardingStartResponse> start(@Valid @RequestBody OnboardingRequest request) {
        return ApiResponse.success(onboardingService.start(request));
    }

    /**
     * Query the current status of an onboarding process.
     * Poll this endpoint until status = "COMPLETED" or "FAILED".
     */
    @GetMapping("/{processInstanceId}")
    public ApiResponse<OnboardingStatusResponse> getStatus(
            @PathVariable String processInstanceId) {
        return ApiResponse.success(onboardingService.getStatus(processInstanceId));
    }

    /**
     * Retry the failed step of an onboarding process.
     * Only the step that failed is re-executed; completed steps are not affected.
     */
    @PostMapping("/{processInstanceId}/retry")
    public ApiResponse<Void> retry(@PathVariable String processInstanceId) {
        onboardingService.retry(processInstanceId);
        return ApiResponse.success(null);
    }
}
