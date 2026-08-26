package com.bank.digital.lending.orchestration;

import com.bank.digital.lending.model.entity.LoanApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Azure Logic Apps Approval Connector — Human Review Escalation.
 *
 * Dual-mode implementation:
 *
 * ── LOCAL DEV (azure.enabled=false) ──────────────────────────────────────────────────────────
 *   Logs a detailed structured mock showing what would be sent to the Logic App trigger,
 *   including the applicant profile, risk score, DTI, and the callback URL.
 *
 * ── AZURE CLOUD (azure.enabled=true) ─────────────────────────────────────────────────────────
 *   Posts a JSON payload to the Logic App HTTP Trigger URL via WebClient.
 *   The Logic App workflow then:
 *     1. Sends an interactive Approve/Reject card to the Operations Manager via Outlook / Teams.
 *     2. The manager reviews applicant KYC, income, risk score and submits their decision.
 *     3. The Logic App POSTs the manager decision back to:
 *        POST /api/v1/loans/applications/{applicationId}/manager-callback
 *        which raises the 'ManagerDecisionEvent' on the waiting Durable Function instance.
 *
 *   Logic App trigger endpoint (configured in azure.logic-apps.manager-approval-webhook-url):
 *     POST https://prod-00.eastus.logic.azure.com/workflows/{workflow-id}/triggers/manual/paths/invoke
 *          ?api-version=2016-10-01&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig={sig}
 *
 *   Payload sent to Logic App:
 *   {
 *     "applicationId": "APP-XXXXX",
 *     "callbackUrl": "https://{service-host}/api/v1/loans/applications/APP-XXXXX/manager-callback",
 *     "applicantName": "...", "loanAmount": ..., "riskScore": ..., "dtiRatio": ...,
 *     "employmentType": "...", "monthlyIncome": ..., "decisionRemarks": "..."
 *   }
 */
@Service
public class LogicAppApprovalConnector {

    private static final Logger log = LoggerFactory.getLogger(LogicAppApprovalConnector.class);

    @Value("${azure.enabled:false}")
    private boolean azureEnabled;

    @Value("${azure.logic-apps.manager-approval-webhook-url:https://prod-00.eastus.logic.azure.com/workflows/loan-approval/triggers/manual/paths/invoke}")
    private String logicAppWebhookUrl;

    private final WebClient webClient;

    public LogicAppApprovalConnector(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Triggers the Azure Logic Apps Human Review workflow for a borderline loan application.
     *
     * @param app         the loan application requiring underwriter review
     * @param callbackUrl the URL the Logic App should POST the manager decision back to
     */
    public void triggerHumanReviewWorkflow(LoanApplication app, String callbackUrl) {
        if (azureEnabled) {
            // ── AZURE CLOUD: POST trigger payload to Logic App HTTP Trigger URL ──────────
            triggerAzureLogicApp(app, callbackUrl);
        } else {
            // ── LOCAL DEV: Structured mock logger ────────────────────────────────────────
            logMockLogicAppTrigger(app, callbackUrl);
        }
    }

    // ─── PRIVATE: Azure Cloud Logic App HTTP Trigger ─────────────────────────────────────────

    private void triggerAzureLogicApp(LoanApplication app, String callbackUrl) {
        // Payload posted to the Logic App HTTP trigger.
        // The Logic App uses these fields to populate the manager's approval card.
        Map<String, Object> triggerPayload = Map.ofEntries(
                Map.entry("applicationId", app.getApplicationId()),
                Map.entry("callbackUrl", callbackUrl),
                Map.entry("applicantName", app.getCustomerName()),
                Map.entry("applicantEmail", app.getCustomerEmail()),
                Map.entry("loanAmount", app.getLoanAmount()),
                Map.entry("tenureMonths", app.getTenureMonths()),
                Map.entry("interestRate", app.getInterestRate()),
                Map.entry("calculatedEMI", app.getCalculatedEMI()),
                Map.entry("riskScore", app.getRiskScore()),
                Map.entry("dtiRatio", app.getDtiRatio()),
                Map.entry("employmentType", app.getEmploymentType().name()),
                Map.entry("monthlyIncome", app.getMonthlyIncome()),
                Map.entry("decisionRemarks", app.getDecisionRemarks() != null ? app.getDecisionRemarks() : "")
        );

        try {
            log.info("[AZURE LOGIC APP] Triggering Human Review workflow for application '{}'", app.getApplicationId());
            log.info("[AZURE LOGIC APP] Logic App Trigger URL: {}", logicAppWebhookUrl);
            log.info("[AZURE LOGIC APP] Applicant: {} | Amount: {} | Risk Score: {}/100 | DTI: {}%",
                    app.getCustomerName(), app.getLoanAmount(), app.getRiskScore(), app.getDtiRatio());

            webClient.post()
                    .uri(logicAppWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(triggerPayload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("[AZURE LOGIC APP] Workflow triggered successfully — Interactive Approval Card dispatched to Operations Manager queue.");
            log.info("[AZURE LOGIC APP] Callback configured at: {}", callbackUrl);
            log.info("[AZURE LOGIC APP] Awaiting manager review decision (Approve / Reject / Request Documents)...");

        } catch (WebClientResponseException e) {
            log.error("[AZURE LOGIC APP] Failed to trigger Logic App for application '{}'. HTTP {}: {}",
                    app.getApplicationId(), e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[AZURE LOGIC APP] Error triggering Logic App for application '{}': {}",
                    app.getApplicationId(), e.getMessage(), e);
        }
    }

    // ─── PRIVATE: Local Mock Logger ──────────────────────────────────────────────────────────

    private void logMockLogicAppTrigger(LoanApplication app, String callbackUrl) {
        log.info("================================================================================");
        log.info("[MOCK AZURE LOGIC APP] >>> TRIGGERING HUMAN REVIEW & ESCALATION WORKFLOW <<<");
        log.info("[MOCK AZURE LOGIC APP] Logic App Webhook URL: {}", logicAppWebhookUrl);
        log.info("[MOCK AZURE LOGIC APP] Application ID: {} | Applicant: {} | Amount: ${}",
                app.getApplicationId(), app.getCustomerName(), app.getLoanAmount());
        log.info("[MOCK AZURE LOGIC APP] Risk Score: {}/100 | DTI: {}% | Employment: {}",
                app.getRiskScore(), app.getDtiRatio(), app.getEmploymentType());
        log.info("[MOCK AZURE LOGIC APP] Dispatching Interactive Outlook Actionable Card & MS Teams Card to Underwriting Queue.");
        log.info("[MOCK AZURE LOGIC APP] Callback Receiver Configured: POST {}", callbackUrl);
        log.info("[MOCK AZURE LOGIC APP] Awaiting Operations Manager review decision (Approve/Reject)...");
        log.info("================================================================================");
    }
}
