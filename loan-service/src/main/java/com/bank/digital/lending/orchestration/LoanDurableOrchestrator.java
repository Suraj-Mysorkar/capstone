package com.bank.digital.lending.orchestration;

import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.ApprovalDecision;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.service.CreditRiskScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Azure Durable Functions Orchestrator — Stateful Loan Processing Workflow.
 *
 * Dual-mode implementation:
 *
 * ── LOCAL DEV (azure.enabled=false) ──────────────────────────────────────────────────────────
 *   Simulates the three-step stateful Durable Function orchestration in-process using structured
 *   mock log output. The risk scoring, decision gateway, and Logic App escalation all run
 *   synchronously inside the same JVM for local testing.
 *
 * ── AZURE CLOUD (azure.enabled=true) ─────────────────────────────────────────────────────────
 *   Delegates orchestration to a real Azure Durable Function via the Durable Task HTTP Management
 *   API. The Spring Boot service acts as the Durable Functions CLIENT:
 *
 *   Step 1 — Start Orchestration:
 *     POST {durable-functions-base-url}/orchestrators/LoanProcessingOrchestrator
 *     Body: { applicationId, loanAmount, customerId, callbackUrl, ... }
 *     Azure Durable Functions runtime checkpoints every activity step internally.
 *
 *   Step 2 — Raise External Event (after manager approves via Logic App webhook):
 *     POST {durable-functions-base-url}/instances/{instanceId}/raiseEvent/ManagerDecisionEvent
 *     Body: { decision: "APPROVE", remarks: "...", managerId: "..." }
 *     The waiting orchestrator resumes from its checkpoint and executes the finalization activity.
 *
 *   Step 3 — Status Polling (optional):
 *     GET {durable-functions-base-url}/instances/{instanceId}
 *     Returns: { runtimeStatus, output, customStatus }
 *
 *   The Durable Function POSTs back to this service's webhook endpoint with the final decision
 *   via the callbackUrl provided at orchestration start.
 */
@Service
public class LoanDurableOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LoanDurableOrchestrator.class);

    @Value("${azure.enabled:false}")
    private boolean azureEnabled;

    @Value("${azure.durable-functions.orchestrator-base-url:https://bank-lending-functions.azurewebsites.net/runtime/webhooks/durabletask}")
    private String durableFunctionsBaseUrl;

    @Value("${azure.durable-functions.function-key:}")
    private String durableFunctionKey;

    private final CreditRiskScoringService creditRiskScoringService;
    private final LogicAppApprovalConnector logicAppConnector;
    private final WebClient webClient;

    public LoanDurableOrchestrator(CreditRiskScoringService creditRiskScoringService,
                                   LogicAppApprovalConnector logicAppConnector,
                                   WebClient.Builder webClientBuilder) {
        this.creditRiskScoringService = creditRiskScoringService;
        this.logicAppConnector = logicAppConnector;
        this.webClient = webClientBuilder != null ? webClientBuilder.build() : WebClient.builder().build();
    }

    /**
     * Starts the loan processing orchestration for a new application.
     *
     * Cloud mode: POSTs to the Durable Functions HTTP API to start the
     *   'LoanProcessingOrchestrator' function with applicationId as the instanceId.
     *   The orchestrator internally runs: ValidateData -> ComputeCreditRisk -> DecisionGateway
     *   and optionally waitForExternalEvent('ManagerDecisionEvent') for borderline cases.
     *
     * Local mode: Executes the same 3-step logic synchronously in-process with mock loggers.
     */
    public void runOrchestrationWorkflow(LoanApplication app, String callbackUrl) {
        String instanceId = "ORCH-" + app.getApplicationId();
        app.setOrchestrationInstanceId(instanceId);

        if (azureEnabled) {
            // ── AZURE CLOUD: Start Durable Function orchestration via HTTP Management API ──
            startAzureDurableOrchestration(app, instanceId, callbackUrl);
        } else {
            // ── LOCAL DEV: In-process simulation with structured mock loggers ──
            runLocalOrchestrationSimulation(app, instanceId, callbackUrl);
        }
    }

    /**
     * Raises the 'ManagerDecisionEvent' external event on the waiting Durable Function instance.
     *
     * Cloud mode: POSTs the manager decision payload to the Durable Functions raiseEvent endpoint.
     *   The orchestrator resumes from its waitForExternalEvent checkpoint and runs finalization.
     *
     * Local mode: Directly applies the manager decision to the LoanApplication entity.
     */
    public void processManagerApprovalEvent(LoanApplication app, ApprovalDecision decision,
                                            String remarks, String managerId) {
        String instanceId = app.getOrchestrationInstanceId();

        log.info("================================================================================");
        log.info("[MOCK AZURE DURABLE FUNCTION] >>> RESUMING DURABLE ORCHESTRATION INSTANCE <<<");
        log.info("[MOCK AZURE DURABLE FUNCTION] Instance ID: {}", instanceId);
        log.info("[MOCK AZURE DURABLE FUNCTION] Received External Event 'ManagerDecisionEvent' from Manager: {}", managerId);
        log.info("[MOCK AZURE DURABLE FUNCTION] Manager Decision: {} | Remarks: {}", decision, remarks);

        if (azureEnabled) {
            // ── AZURE CLOUD: Raise external event on the waiting Durable Function instance ──
            if (!raiseManagerDecisionEvent(instanceId, decision, remarks, managerId)) {
                throw new IllegalStateException("Unable to resume Durable Function instance '" + instanceId + "'");
            }
        }

        // Apply decision to entity in both modes (local simulation + cloud confirmation)
        if (decision == ApprovalDecision.APPROVE) {
            app.setStatus(LoanStatus.APPROVED);
            app.setAssignedManager(managerId);
            app.setDecisionRemarks("Approved by Operations Manager: " + remarks);
            log.info("[MOCK AZURE DURABLE FUNCTION] Final Status updated to: APPROVED");
        } else {
            app.setStatus(LoanStatus.REJECTED);
            app.setAssignedManager(managerId);
            app.setDecisionRemarks("Rejected by Operations Manager: " + remarks);
            log.info("[MOCK AZURE DURABLE FUNCTION] Final Status updated to: REJECTED");
        }
        log.info("[MOCK AZURE DURABLE FUNCTION] Orchestration Instance '{}' Completed.", instanceId);
        log.info("================================================================================");
    }

    // ─── PRIVATE: Azure Cloud Durable Functions HTTP API Calls ──────────────────────────────────

    /**
     * Starts the Azure Durable Function orchestration via the HTTP Management API.
     * Endpoint: POST /runtime/webhooks/durabletask/orchestrators/LoanProcessingOrchestrator/{instanceId}
     */
    private void startAzureDurableOrchestration(LoanApplication app, String instanceId, String callbackUrl) {
        String startUrl = durableFunctionsBaseUrl
                + "/orchestrators/LoanProcessingOrchestrator/" + instanceId
                + "?code=" + durableFunctionKey;

        Map<String, Object> orchestrationInput = Map.of(
                "applicationId", app.getApplicationId(),
                "customerId", app.getCustomerId(),
                "customerName", app.getCustomerName(),
                "customerEmail", app.getCustomerEmail(),
                "loanAmount", app.getLoanAmount(),
                "tenureMonths", app.getTenureMonths(),
                "monthlyIncome", app.getMonthlyIncome(),
                "existingLiabilities", app.getExistingLiabilities() != null ? app.getExistingLiabilities() : 0,
                "employmentType", app.getEmploymentType().name(),
                "callbackUrl", callbackUrl
        );

        try {
            log.info("[AZURE DURABLE FUNCTION] Starting orchestration '{}' for application '{}'",
                    instanceId, app.getApplicationId());

            webClient.post()
                    .uri(startUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(orchestrationInput)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                    app.setDecisionRemarks("Durable orchestration started; awaiting validation and credit assessment.");
            log.info("[AZURE DURABLE FUNCTION] Orchestration instance '{}' started successfully.", instanceId);
            log.info("[AZURE DURABLE FUNCTION] Durable Function runtime now executing:");
            log.info("[AZURE DURABLE FUNCTION]   Activity 1: ValidateApplicationActivity");
            log.info("[AZURE DURABLE FUNCTION]   Activity 2: ComputeCreditRiskScoreActivity");
            log.info("[AZURE DURABLE FUNCTION]   Activity 3: DecisionGatewayActivity");

        } catch (WebClientResponseException e) {
            log.error("[AZURE DURABLE FUNCTION] Failed to start orchestration '{}'. HTTP {}: {}",
                    instanceId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new IllegalStateException("Unable to start Durable Function orchestration '" + instanceId + "'", e);
        } catch (Exception e) {
            log.error("[AZURE DURABLE FUNCTION] Error starting orchestration '{}': {}", instanceId, e.getMessage(), e);
            throw new IllegalStateException("Unable to start Durable Function orchestration '" + instanceId + "'", e);
        }
    }

    /**
     * Raises the 'ManagerDecisionEvent' external event on the waiting Durable Function instance.
     * Endpoint: POST /runtime/webhooks/durabletask/instances/{instanceId}/raiseEvent/ManagerDecisionEvent
     */
    private boolean raiseManagerDecisionEvent(String instanceId, ApprovalDecision decision,
                                              String remarks, String managerId) {
        String raiseEventUrl = durableFunctionsBaseUrl
                + "/instances/" + instanceId
                + "/raiseEvent/ManagerDecisionEvent"
                + "?code=" + durableFunctionKey;

        Map<String, Object> eventPayload = Map.of(
                "decision", decision.name(),
                "remarks", remarks,
                "managerId", managerId
        );

        try {
            log.info("[AZURE DURABLE FUNCTION] Raising external event 'ManagerDecisionEvent' on instance '{}'", instanceId);

            webClient.post()
                    .uri(raiseEventUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(eventPayload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("[AZURE DURABLE FUNCTION] External event raised — orchestrator is resuming from checkpoint.");
            return true;

        } catch (WebClientResponseException e) {
            log.error("[AZURE DURABLE FUNCTION] Failed to raise ManagerDecisionEvent on '{}'. HTTP {}: {}",
                    instanceId, e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[AZURE DURABLE FUNCTION] Error raising event on '{}': {}", instanceId, e.getMessage(), e);
        }
        return false;
    }

    // ─── PRIVATE: Local In-Process Orchestration Simulation ─────────────────────────────────────

    private void runLocalOrchestrationSimulation(LoanApplication app, String instanceId, String callbackUrl) {
        log.info("================================================================================");
        log.info("[MOCK AZURE DURABLE FUNCTION] >>> STARTING DURABLE ORCHESTRATION INSTANCE <<<");
        log.info("[MOCK AZURE DURABLE FUNCTION] Instance ID: {}", instanceId);
        log.info("[MOCK AZURE DURABLE FUNCTION] Workflow: 'LoanProcessingOrchestrator' | Application: {}", app.getApplicationId());

        // Step 1: ValidateApplicationActivity
        log.info("[MOCK AZURE DURABLE FUNCTION] [Step 1/3] Executing Activity: 'ValidateApplicationActivity'...");
        boolean isValid = validateApplicationData(app);
        if (!isValid) {
            log.warn("[MOCK AZURE DURABLE FUNCTION] [Step 1/3] Validation FAILED for application: {}", app.getApplicationId());
            app.setStatus(LoanStatus.REJECTED);
            app.setDecisionRemarks("Application data validation failed (scheme limits or negative values).");
            log.info("================================================================================");
            return;
        }
        log.info("[MOCK AZURE DURABLE FUNCTION] [Step 1/3] Validation PASSED. State checkpoint saved.");

        // Step 2: ComputeCreditRiskScoreActivity
        log.info("[MOCK AZURE DURABLE FUNCTION] [Step 2/3] Executing Activity: 'ComputeCreditRiskScoreActivity'...");
        CreditRiskScoringService.RiskAssessmentResult result = creditRiskScoringService.evaluateApplication(app);
        app.setRiskScore(result.riskScore());
        app.setDtiRatio(result.dtiRatio());
        log.info("[MOCK AZURE DURABLE FUNCTION] [Step 2/3] Risk Score Evaluated: {}/100, DTI: {}%", result.riskScore(), result.dtiRatio());

        // Step 3: DecisionGatewayActivity
        log.info("[MOCK AZURE DURABLE FUNCTION] [Step 3/3] Executing Activity: 'DecisionGatewayActivity'...");
        if (result.initialStatus() == LoanStatus.APPROVED) {
            app.setStatus(LoanStatus.APPROVED);
            app.setDecisionRemarks(result.assessmentReason());
            log.info("[MOCK AZURE DURABLE FUNCTION] Decision Gateway: Direct Auto-Approval. Status -> APPROVED");
        } else if (result.initialStatus() == LoanStatus.REJECTED) {
            app.setStatus(LoanStatus.REJECTED);
            app.setDecisionRemarks(result.assessmentReason());
            log.info("[MOCK AZURE DURABLE FUNCTION] Decision Gateway: Direct Auto-Rejection. Status -> REJECTED");
        } else {
            // Human Review Branch — trigger Logic App escalation
            app.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);
            app.setDecisionRemarks(result.assessmentReason());
            log.info("[MOCK AZURE DURABLE FUNCTION] Decision Gateway: Score {} requires Human Review.", result.riskScore());
            log.info("[MOCK AZURE DURABLE FUNCTION] Invoking Azure Logic Apps to dispatch Manager Approval Card...");
            logicAppConnector.triggerHumanReviewWorkflow(app, callbackUrl);
            log.info("[MOCK AZURE DURABLE FUNCTION] Instance '{}' entered stateful checkpoint: context.waitForExternalEvent('ManagerDecisionEvent', 7 Days).", instanceId);
            log.info("[MOCK AZURE DURABLE FUNCTION] Zero CPU billed during idle wait for manager action.");
        }
        log.info("================================================================================");
    }

    private boolean validateApplicationData(LoanApplication app) {
        LoanScheme scheme = app.getScheme();
        if (scheme == null) return false;

        if (app.getLoanAmount() == null || app.getLoanAmount().compareTo(scheme.getMinAmount()) < 0
                || app.getLoanAmount().compareTo(scheme.getMaxAmount()) > 0) {
            return false;
        }

        return app.getTenureMonths() != null && app.getTenureMonths() >= scheme.getMinTenureMonths()
                && app.getTenureMonths() <= scheme.getMaxTenureMonths();
    }
}
