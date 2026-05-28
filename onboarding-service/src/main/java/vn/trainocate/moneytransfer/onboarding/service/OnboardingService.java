package vn.trainocate.moneytransfer.onboarding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.onboarding.dto.request.OnboardingRequest;
import vn.trainocate.moneytransfer.onboarding.dto.response.OnboardingStartResponse;
import vn.trainocate.moneytransfer.onboarding.dto.response.OnboardingStatusResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final String PROCESS_KEY = "onboarding-process";

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ManagementService managementService;

    /**
     * Starts the onboarding BPMN process.
     * Returns immediately with the processInstanceId — execution is async (job executor).
     */
    public OnboardingStartResponse start(OnboardingRequest request) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("password", request.getPassword());
        vars.put("phone",    request.getPhone());
        vars.put("email",    request.getEmail());
        vars.put("cif",      request.getCif());
        vars.put("fullName", request.getFullName());
        vars.put("dob",      request.getDob());
        vars.put("address",  request.getAddress());
        vars.put("mobile",   request.getMobile());
        vars.put("currency", request.getCurrency());
        vars.put("idNumber", request.getIdNumber());
        vars.put("idType",   request.getIdType());

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY, vars);

        log.info("[ONBOARDING] Process started: processInstanceId={}", instance.getId());
        return new OnboardingStartResponse(instance.getId(), "STARTED");
    }

    /**
     * Returns the current status of an onboarding process.
     * <ul>
     *   <li>RUNNING  — process is active, no incidents</li>
     *   <li>FAILED   — process is active but has an incident (step threw an exception)</li>
     *   <li>COMPLETED — process has finished successfully</li>
     * </ul>
     */
    public OnboardingStatusResponse getStatus(String processInstanceId) {
        // 1. Check active instance
        ProcessInstance active = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (active != null) {
            // Check for incidents
            List<Incident> incidents = runtimeService.createIncidentQuery()
                    .processInstanceId(processInstanceId)
                    .list();

            if (!incidents.isEmpty()) {
                Incident incident = incidents.get(0);
                return OnboardingStatusResponse.builder()
                        .processInstanceId(processInstanceId)
                        .status("FAILED")
                        .currentActivity(incident.getActivityId())
                        .errorMessage(incident.getIncidentMessage())
                        .variables(collectOutputVariables(processInstanceId))
                        .build();
            }

            // Find current activity via the activity instance tree
            String currentActivity = null;
            ActivityInstance rootActivity = runtimeService.getActivityInstance(processInstanceId);
            if (rootActivity != null && rootActivity.getChildActivityInstances().length > 0) {
                currentActivity = rootActivity.getChildActivityInstances()[0].getActivityId();
            }

            return OnboardingStatusResponse.builder()
                    .processInstanceId(processInstanceId)
                    .status("RUNNING")
                    .currentActivity(currentActivity)
                    .variables(collectOutputVariables(processInstanceId))
                    .build();
        }

        // 2. Check history for completed process
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historic != null && historic.getEndTime() != null) {
            return OnboardingStatusResponse.builder()
                    .processInstanceId(processInstanceId)
                    .status("COMPLETED")
                    .variables(collectHistoricOutputVariables(processInstanceId))
                    .build();
        }

        // 3. Not found
        return OnboardingStatusResponse.builder()
                .processInstanceId(processInstanceId)
                .status("NOT_FOUND")
                .build();
    }

    /**
     * Retries the failed step for a process instance with an incident.
     * Camunda re-runs only the failed service task — completed steps are NOT repeated.
     */
    public void retry(String processInstanceId) {
        List<Incident> incidents = runtimeService.createIncidentQuery()
                .processInstanceId(processInstanceId)
                .list();

        if (incidents.isEmpty()) {
            throw new IllegalStateException(
                    "No incident found for processInstanceId=" + processInstanceId);
        }

        for (Incident incident : incidents) {
            // For failedJob incidents, configuration = jobId
            String jobId = incident.getConfiguration();
            managementService.setJobRetries(jobId, 1);
            log.info("[ONBOARDING] Retry triggered: processInstanceId={}, jobId={}, activity={}",
                    processInstanceId, jobId, incident.getActivityId());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    /** Collects key output variables from the active process instance. */
    private Map<String, Object> collectOutputVariables(String processInstanceId) {
        Map<String, Object> result = new HashMap<>();
        for (String key : List.of("userId", "username", "accountNo", "kycTier")) {
            try {
                Object value = runtimeService.getVariable(processInstanceId, key);
                if (value != null) result.put(key, value);
            } catch (Exception ignored) {
                // variable not yet set — skip
            }
        }
        return result.isEmpty() ? null : result;
    }

    /** Collects key output variables from the historic (completed) process instance. */
    private Map<String, Object> collectHistoricOutputVariables(String processInstanceId) {
        Map<String, Object> result = new HashMap<>();
        historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .stream()
                .filter(v -> List.of("userId", "username", "accountNo", "kycTier").contains(v.getName()))
                .forEach(v -> result.put(v.getName(), v.getValue()));
        return result.isEmpty() ? null : result;
    }
}
