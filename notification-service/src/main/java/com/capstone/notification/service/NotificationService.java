package com.capstone.notification.service;

import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.web.client.RestClient;

import com.capstone.notification.dto.EmailDto;
import com.capstone.notification.model.CustomerRegisterNotificationDTO;
import com.capstone.notification.model.DocumentNotificationDTO;
import com.capstone.notification.model.LoanStatusNotificationDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;

public class NotificationService {

    private static final String DEFAULT_LOGIC_APP_URL =
            "https://prod-17.southindia.logic.azure.com:443/workflows/a4b29c1d5e814824900b41a17fa24844/triggers/When_a_HTTP_request_is_received/paths/invoke?api-version=2016-10-01&sp=%2Ftriggers%2FWhen_a_HTTP_request_is_received%2Frun&sv=1.0&sig=F--JabvW3Uwr-JsZU76HgaWWTcekahkC6HBwTEImtys";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    private URI getLogicAppUri() {
        String envUrl = System.getenv("AZURE_LOGIC_APPS_WEBHOOK_URL");
        if (envUrl == null || envUrl.isBlank()) {
            envUrl = System.getenv("LOGIC_APPS_URL");
        }
        if (envUrl == null || envUrl.isBlank()) {
            envUrl = DEFAULT_LOGIC_APP_URL;
        }
        return URI.create(envUrl);
    }

    /**
     * Dispatches the built EmailDto to Azure Logic Apps Webhook for customer email transmission.
     */
    public void dispatchEmail(EmailDto email, ExecutionContext context) {
        if (email.getEmailTo() == null || email.getEmailTo().isBlank()) {
            context.getLogger().warning("Recipient email is null or empty. Defaulting to customer-support@bank.com for testing.");
            email.setEmailTo("customer-support@bank.com");
        }

        try {
            String payload = objectMapper.writeValueAsString(email);
            context.getLogger().info("[NOTIFICATION-SERVICE] Dispatching Email Payload to Logic App:\n" + payload);

            URI targetUri = getLogicAppUri();
            restClient.post()
                    .uri(targetUri)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            context.getLogger().info("[NOTIFICATION-SERVICE] ✅ Successfully sent email to " + email.getEmailTo() + " via Logic App!");
        } catch (Exception e) {
            context.getLogger().warning("[NOTIFICATION-SERVICE] Could not deliver email via Logic App: " + e.getMessage());
            // Log local email preview for testability
            context.getLogger().info("[MOCK EMAIL PREVIEW]\nTO: " + email.getEmailTo()
                    + "\nSUBJECT: " + email.getEmailSubject()
                    + "\nBODY:\n" + email.getEmailBody());
        }
    }

    /**
     * 1. Handles New Customer Registrations
     */
    public void sendCustomerRegistrationNotification(CustomerRegisterNotificationDTO customerRegEvent, ExecutionContext context) {
        String name = customerRegEvent.getCustomerName() != null ? customerRegEvent.getCustomerName() : "Valued Customer";
        String status = customerRegEvent.getStatus() != null ? customerRegEvent.getStatus() : "ACTIVE";

        String subject = "Welcome to Digital Banking - Registration Successful";
        String body = String.format(
                "Dear %s,\n\n"
                        + "Welcome to Digital Banking.\n\n"
                        + "We are pleased to confirm that your customer account has been "
                        + "successfully registered.\n\n"
                        + "Account Details\n"
                        + "----------------\n"
                        + "Account Status : %s\n\n"
                        + "You can now securely access the Digital Banking portal to apply "
                        + "for loans, track your applications, and upload the required documents.\n\n"
                        + "If you have any questions or require assistance, please contact "
                        + "our Customer Support team.\n\n"
                        + "Kind regards,\n"
                        + "Digital Banking Customer Experience Team",
                name, status
        );

        dispatchEmail(new EmailDto(customerRegEvent.getEmail(), subject, body), context);
    }

    /**
     * 2. Handles Initial Loan Application Submission
     */
    public void sendLoanApplicationReceivedNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();
        String amountFormatted = formatCurrency(dto.getAmount());

        String subject = String.format("Loan Application Received - %s", appId);
        String body = String.format(
                "Dear %s,\n\n"
                        + "Thank you for submitting your %s application through Digital Lending.\n\n"
                        + "We are pleased to confirm that your application has been "
                        + "successfully received and is currently being processed.\n\n"
                        + "Application Details\n"
                        + "-------------------\n"
                        + "Application ID        : %s\n"
                        + "Loan Type             : %s\n"
                        + "Requested Amount      : %s\n"
                        + "Tenure                : %d Months\n"
                        + "Estimated Monthly EMI : %s\n"
                        + "Application Status    : %s\n\n"
                        + "Your application has now entered our automated validation and "
                        + "credit assessment process. We will notify you by email when "
                        + "there is an update regarding your application.\n\n"
                        + "No further action is required from you at this stage.\n\n"
                        + "Thank you for choosing Digital Lending.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Origination Team",
                name, dto.getLoanType(), appId, amountFormatted, dto.getTenureMonths(),
                formatCurrency(dto.getCalculatedEMI()), dto.getStatus()
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 3. Handles Document Uploads
     */
    public void sendDocumentUploadNotification(DocumentNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId() != null ? dto.getApplicationId() : "N/A";
        String docType = dto.getDocumentType() != null ? dto.getDocumentType() : "Document";

        String subject = String.format("Documents Received for Loan Application %s", appId);
        String body = String.format(
                "Dear %s,\n\n"
                        + "We confirm that your %s document has been successfully "
                        + "received for your loan application.\n\n"
                        + "Document Details\n"
                        + "-----------------\n"
                        + "Application ID : %s\n"
                        + "Document ID    : %s\n"
                        + "Document Type  : %s\n"
                        + "Document Status: Received - Under Verification\n\n"
                        + "Your submitted document is currently being reviewed by our "
                        + "document verification process. Your loan application will "
                        + "proceed to the next stage once the required documents have "
                        + "been successfully verified.\n\n"
                        + "No further action is required from you at this stage unless "
                        + "additional information or documentation is requested.\n\n"
                        + "Thank you for your cooperation.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Verification Team",
                name, docType, appId, dto.getDocumentId(), docType
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 3b. Handles Document Review Completed / Verified by Operations Manager
     */
    public void sendDocumentReviewCompletedNotification(DocumentNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId() != null && !dto.getApplicationId().isBlank() ? dto.getApplicationId() : "N/A";
        String docName = dto.getDocumentName() != null ? dto.getDocumentName() : (dto.getDocumentType() != null ? dto.getDocumentType() : "Document");
        String remarks = dto.getRemarks() != null && !dto.getRemarks().isBlank() ? dto.getRemarks() : "Document meets all compliance and underwriting criteria.";
        String verifiedBy = dto.getVerifiedBy() != null ? dto.getVerifiedBy() : "Operations Manager";

        String subject = String.format("✅ Document Verified - %s (%s)", docName, appId);
        String body = String.format(
                "Dear %s,\n\n"
                        + "We are pleased to inform you that your %s document has been "
                        + "successfully reviewed and verified by our Operations Underwriting team.\n\n"
                        + "Review Details\n"
                        + "--------------\n"
                        + "Application ID     : %s\n"
                        + "Document ID        : %s\n"
                        + "Document Name      : %s\n"
                        + "Verification Status: VERIFIED / APPROVED\n"
                        + "Verified By        : %s\n"
                        + "Manager Remarks    : %s\n\n"
                        + "Your application has moved forward to the next stage of credit processing.\n\n"
                        + "Thank you for banking with Digital Lending.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Operations & Underwriting Team",
                name, docName, appId, dto.getDocumentId(), docName, verifiedBy, remarks
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 3c. Handles Document Review Failed / Additional Documents Requested by Operations Manager
     */
    public void sendDocumentReviewFailedNotification(DocumentNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId() != null && !dto.getApplicationId().isBlank() ? dto.getApplicationId() : "N/A";
        String docName = dto.getDocumentName() != null ? dto.getDocumentName() : (dto.getDocumentType() != null ? dto.getDocumentType() : "Document");
        String remarks = dto.getRemarks() != null && !dto.getRemarks().isBlank()
                ? dto.getRemarks()
                : "The submitted document requires revision or additional supporting records.";
        String verifiedBy = dto.getVerifiedBy() != null ? dto.getVerifiedBy() : "Operations Manager";

        String subject = String.format("⚠️ Action Required: Document Review Update - %s", appId);
        String body = String.format(
                "Dear %s,\n\n"
                        + "Our Operations Underwriting team has completed the review of your submitted "
                        + "document '%s' for Application %s and requires your attention.\n\n"
                        + "Manager Feedback & Required Actions:\n"
                        + "-------------------------------------\n"
                        + "Review Status   : ACTION REQUIRED / REVISION NEEDED\n"
                        + "Reviewed By     : %s\n"
                        + "Manager Notes   : %s\n\n"
                        + "Next Steps to Resume Application Processing:\n"
                        + "1. Please log in to the Digital Banking portal (https://lively-grass-0d6cbb800.7.azurestaticapps.net/#/documents) "
                        + "to upload the updated or requested documents.\n"
                        + "2. Alternatively, you may reply directly to this email with the requested documents attached, "
                        + "and our Operations Manager will upload them on your behalf.\n\n"
                        + "If you have any questions, please contact our Customer Experience team.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Operations & Underwriting Team",
                name, docName, appId, verifiedBy, remarks
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 4. Handles Manual Review Escalations
     */
    public void sendManualReviewNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String subject = String.format("Application Update - Review in Progress - %s", appId);

        String remarks = dto.getDecisionRemarks() != null
                ? dto.getDecisionRemarks()
                : "Your application requires additional review before a final decision can be made.";

        String body = String.format(
                "Dear %s,\n\n"
                        + "We are writing to inform you that your loan application "
                        + "is currently undergoing a further review by our underwriting team.\n\n"
                        + "Application Details\n"
                        + "-------------------\n"
                        + "Application ID    : %s\n"
                        + "Loan Type         : %s\n"
                        + "Requested Amount  : %s\n"
                        + "Application Status: Manual Review Required\n"
                        + "Review Remarks    : %s\n\n"
                        + "As part of our standard lending process, your application "
                        + "requires additional review before a final decision can be made.\n\n"
                        + "No further action is required from you at this stage. "
                        + "Our underwriting team will complete the review and notify "
                        + "you once a decision has been reached.\n\n"
                        + "We appreciate your patience and understanding.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Underwriting Team",
                name, appId, dto.getLoanType(), formatCurrency(dto.getAmount()), remarks
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 5. Handles Loan Approval (Auto or Manual)
     */
    public void sendLoanApprovalNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String subject = String.format("🎉 Congratulations! Your Loan Application %s Has Been Approved", appId);

         String remarks = dto.getDecisionRemarks() != null
                ? dto.getDecisionRemarks()
                : "Your application has been approved based on the applicable lending criteria.";

        String body = String.format(
                "Dear %s,\n\n"
                        + "We are pleased to inform you that your %s loan application "
                        + "(%s) has been successfully approved.\n\n"
                        + "Approved Loan Details\n"
                        + "---------------------\n"
                        + "Application ID : %s\n"
                        + "Approved Amount: %s\n"
                        + "Tenure         : %d Months\n"
                        + "Interest Rate  : %.2f%% p.a.\n"
                        + "Monthly EMI    : %s\n"
                        + "Remarks        : %s\n\n"
                        + "Next Steps\n"
                        + "----------\n"
                        + "Please log in to the Digital Banking portal to review and "
                        + "digitally sign your sanction letter. Once the required "
                        + "documentation and acceptance are completed, your loan will "
                        + "proceed towards disbursement.\n\n"
                        + "We appreciate your trust in Digital Lending and look forward "
                        + "to serving you.\n\n"
                        + "Congratulations once again on your loan approval.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Credit & Disbursal Team",
                name, dto.getLoanType(), appId, appId, formatCurrency(dto.getAmount()),
                dto.getTenureMonths(), dto.getInterestRate(), formatCurrency(dto.getCalculatedEMI()),
                remarks
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 6. Handles Loan Rejection
     */
    public void sendLoanRejectionNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String subject = String.format("Loan Application Status Update - %s", appId);

        String remarks = dto.getDecisionRemarks() != null
                ? dto.getDecisionRemarks()
                : "Your application does not meet the current lending guidelines.";

        String body = String.format(
                "Dear %s,\n\n"
                        + "Thank you for your interest in Digital Lending.\n\n"
                        + "Following a careful assessment of your loan application, "
                        + "we regret to inform you that your application could not "
                        + "be approved at this time.\n\n"
                        + "Application Details\n"
                        + "-------------------\n"
                        + "Application ID     : %s\n"
                        + "Loan Type          : %s\n"
                        + "Requested Amount   : %s\n"
                        + "Application Status : Not Approved\n"
                        + "Remarks            : %s\n\n"
                        + "This decision has been made following an assessment against "
                        + "our applicable lending and risk criteria.\n\n"
                        + "If applicable, you may submit a new application after the "
                        + "relevant waiting period. If you require further information "
                        + "or assistance, please contact our Customer Support team.\n\n"
                        + "We appreciate your understanding.\n\n"
                        + "Kind regards,\n"
                        + "Digital Lending Underwriting Team",
                name, appId, dto.getLoanType(), formatCurrency(dto.getAmount()), remarks
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 7. Router for any general Loan Application Event based on status / event type
     */
    public void sendLoanApplicationNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String status = (dto.getStatus() != null ? dto.getStatus() : "").toUpperCase();

        if (status.contains("APPROV")) {
            sendLoanApprovalNotification(dto, context);
        } else if (status.contains("REJECT")) {
            sendLoanRejectionNotification(dto, context);
        } else if (status.contains("MANUAL_REVIEW") || status.contains("REVIEW")) {
            sendManualReviewNotification(dto, context);
        } else if (status.contains("SUBMIT") || status.contains("VALIDAT") || status.contains("CREDIT")) {
            sendLoanApplicationReceivedNotification(dto, context);
        } else {
            sendLoanApplicationReceivedNotification(dto, context);
        }
    }

    private String formatCurrency(double amount) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        return currencyFormat.format(amount);
    }
}
