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
                "<html>"
                        + "<body style=\"font-family: Arial, sans-serif; "
                        + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                        + "<p>Dear %s,</p>"

                        + "<p>Welcome to Digital Banking.</p>"

                        + "<p>"
                        + "We are pleased to confirm that your customer account "
                        + "has been successfully registered."
                        + "</p>"

                        + "<h3>Account Details</h3>"

                        + "<table style=\"border-collapse: collapse; width: 100%%; "
                        + "max-width: 600px;\">"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Account Status</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "</table>"

                        + "<p>"
                        + "You can now securely access the Digital Banking portal "
                        + "to apply for loans, track your applications, and upload "
                        + "the required documents."
                        + "</p>"

                        + "<p>"
                        + "If you have any questions or require assistance, "
                        + "please contact our Customer Support team."
                        + "</p>"

                        + "<p>"
                        + "Kind regards,<br>"
                        + "<strong>Digital Banking Customer Experience Team</strong>"
                        + "</p>"

                        + "</body>"
                        + "</html>",
                        
                escapeHtml(name),
                escapeHtml(status)
        );

        dispatchEmail(new EmailDto(customerRegEvent.getEmail(), subject, body), context);
    }

    /**
     * 2. Handles Initial Loan Application Submission
     */
    public void sendLoanApplicationReceivedNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String loanType = dto.getLoanType();
        String amountFormatted = formatCurrency(dto.getAmount());
        String emiFormatted = formatCurrency(dto.getCalculatedEMI());

        String subject = String.format("Loan Application Received - %s", appId);
        String body = String.format(
                "<html>"
                        + "<body style=\"font-family: Arial, sans-serif; "
                        + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                        + "<p>Dear %s,</p>"

                        + "<p>"
                        + "Thank you for submitting your <strong>%s</strong> "
                        + "application through Digital Lending."
                        + "</p>"

                        + "<p>"
                        + "We are pleased to confirm that your application has "
                        + "been successfully received and is currently being processed."
                        + "</p>"

                        + "<h3>Application Details</h3>"

                        + "<table style=\"border-collapse: collapse; width: 100%%; "
                        + "max-width: 650px;\">"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application ID</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Loan Type</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Requested Amount</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Tenure</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%d Months</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Estimated Monthly EMI</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application Status</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "</table>"

                        + "<p>"
                        + "Your application has now entered our automated validation "
                        + "and credit assessment process. We will notify you by email "
                        + "when there is an update regarding your application."
                        + "</p>"

                        + "<p>"
                        + "No further action is required from you at this stage."
                        + "</p>"

                        + "<p>"
                        + "Thank you for choosing Digital Lending."
                        + "</p>"

                        + "<p>"
                        + "Kind regards,<br>"
                        + "<strong>Digital Lending Origination Team</strong>"
                        + "</p>"

                        + "</body>"
                        + "</html>",

                escapeHtml(name), escapeHtml(loanType), escapeHtml(appId), escapeHtml(loanType), amountFormatted,
                dto.getTenureMonths(), emiFormatted, escapeHtml(dto.getStatus())
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
                "<html>"
                        + "<body style=\"font-family: Arial, sans-serif; "
                        + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                        + "<p>Dear %s,</p>"

                        + "<p>"
                        + "We confirm that your <strong>%s</strong> document has "
                        + "been successfully received for your loan application."
                        + "</p>"

                        + "<h3>Document Details</h3>"

                        + "<table style=\"border-collapse: collapse; width: 100%%; "
                        + "max-width: 650px;\">"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application ID</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Document ID</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Document Type</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Document Status</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">"
                        + "Received - Under Verification"
                        + "</td>"
                        + "</tr>"

                        + "</table>"

                        + "<p>"
                        + "Your submitted document is currently being reviewed by "
                        + "our document verification process. Your loan application "
                        + "will proceed to the next stage once the required documents "
                        + "have been successfully verified."
                        + "</p>"

                        + "<p>"
                        + "No further action is required from you at this stage unless "
                        + "additional information or documentation is requested."
                        + "</p>"

                        + "<p>"
                        + "Thank you for your cooperation."
                        + "</p>"

                        + "<p>"
                        + "Kind regards,<br>"
                        + "<strong>Digital Lending Verification Team</strong>"
                        + "</p>"

                        + "</body>"
                        + "</html>",

                escapeHtml(name), escapeHtml(docType), escapeHtml(appId), escapeHtml(dto.getDocumentId()),
                escapeHtml(docType)
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
                "<html>"
                    + "<body style=\"font-family: Arial, sans-serif; "
                    + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                    + "<p>Dear %s,</p>"

                    + "<p>"
                    + "We are pleased to inform you that your "
                    + "<strong>%s</strong> document has been successfully "
                    + "reviewed and verified by our Operations Underwriting team."
                    + "</p>"

                    + "<h3>Review Details</h3>"

                    + "<table style=\"border-collapse: collapse; width: 100%%; "
                    + "max-width: 650px;\">"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Application ID</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Document ID</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Document Name</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Verification Status</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">"
                    + "VERIFIED / APPROVED"
                    + "</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Verified By</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Manager Remarks</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "</table>"

                    + "<p>"
                    + "Your application has moved forward to the next stage "
                    + "of credit processing."
                    + "</p>"

                    + "<p>"
                    + "Thank you for banking with Digital Lending."
                    + "</p>"

                    + "<p>"
                    + "Kind regards,<br>"
                    + "<strong>Digital Lending Operations &amp; "
                    + "Underwriting Team</strong>"
                    + "</p>"

                    + "</body>"
                    + "</html>",

            escapeHtml(name),
            escapeHtml(docName),
            escapeHtml(appId),
            escapeHtml(dto.getDocumentId()),
            escapeHtml(docName),
            escapeHtml(verifiedBy),
            escapeHtml(remarks)
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
                "<html>"
                    + "<body style=\"font-family: Arial, sans-serif; "
                    + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                    + "<p>Dear %s,</p>"

                    + "<p>"
                    + "Our Operations Underwriting team has completed the review "
                    + "of your submitted <strong>%s</strong> document for "
                    + "Application <strong>%s</strong>."
                    + "</p>"

                    + "<p>"
                    + "Additional information or an updated document is required "
                    + "before your application can proceed to the next stage."
                    + "</p>"

                    + "<h3>Review Details</h3>"

                    + "<table style=\"border-collapse: collapse; width: 100%%; "
                    + "max-width: 650px;\">"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Application ID</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Document ID</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Document Name</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Review Status</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">"
                    + "ACTION REQUIRED / REVISION NEEDED"
                    + "</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Reviewed By</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "<tr>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                    + "font-weight: bold;\">Manager Remarks</td>"
                    + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                    + "</tr>"

                    + "</table>"

                    + "<h3>Next Steps</h3>"

                    + "<ol>"
                    + "<li>"
                    + "Please log in to the Digital Banking portal to upload "
                    + "the updated or requested documents."
                    + "</li>"
                    + "<li>"
                    + "Alternatively, you may contact our Customer Experience "
                    + "team for assistance with submitting the requested documents."
                    + "</li>"
                    + "</ol>"

                    + "<p>"
                    + "Your application will proceed once the required documents "
                    + "have been received and successfully verified."
                    + "</p>"

                    + "<p>"
                    + "If you have any questions, please contact our "
                    + "Customer Experience team."
                    + "</p>"

                    + "<p>"
                    + "Kind regards,<br>"
                    + "<strong>Digital Lending Operations &amp; "
                    + "Underwriting Team</strong>"
                    + "</p>"

                    + "</body>"
                    + "</html>",

            escapeHtml(name),
            escapeHtml(docName),
            escapeHtml(appId),
            escapeHtml(appId),
            escapeHtml(dto.getDocumentId()),
            escapeHtml(docName),
            escapeHtml(verifiedBy),
            escapeHtml(remarks)
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
                "<html>"
                        + "<body style=\"font-family: Arial, sans-serif; "
                        + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                        + "<p>Dear %s,</p>"

                        + "<p>"
                        + "We are writing to inform you that your loan application "
                        + "is currently undergoing a further review by our underwriting team."
                        + "</p>"

                        + "<h3>Application Details</h3>"

                        + "<table style=\"border-collapse: collapse; width: 100%%; "
                        + "max-width: 650px;\">"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application ID</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Loan Type</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Requested Amount</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application Status</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">"
                        + "Manual Review Required"
                        + "</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Review Remarks</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "</table>"

                        + "<p>"
                        + "As part of our standard lending process, your application "
                        + "requires additional review before a final decision can be made."
                        + "</p>"

                        + "<p>"
                        + "<strong>No further action is required from you at this stage.</strong> "
                        + "Our underwriting team will complete the review and notify you "
                        + "once a decision has been reached."
                        + "</p>"

                        + "<p>"
                        + "We appreciate your patience and understanding."
                        + "</p>"

                        + "<p>"
                        + "Kind regards,<br>"
                        + "<strong>Digital Lending Underwriting Team</strong>"
                        + "</p>"

                        + "</body>"
                        + "</html>",

                escapeHtml(name), escapeHtml(appId), escapeHtml(dto.getLoanType()),
                formatCurrency(dto.getAmount()), escapeHtml(remarks)
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
                "<html>"
                        + "<body style=\"font-family: Arial, sans-serif; "
                        + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                        + "<p>Dear %s,</p>"

                        + "<p>"
                        + "We are pleased to inform you that your <strong>%s</strong> "
                        + "loan application (%s) has been successfully approved."
                        + "</p>"

                        + "<h3>Approved Loan Details</h3>"

                        + "<table style=\"border-collapse: collapse; width: 100%%; "
                        + "max-width: 650px;\">"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application ID</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Approved Amount</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Tenure</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%d Months</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Interest Rate</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%.2f%% p.a.</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Monthly EMI</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Remarks</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "</table>"

                        + "<h3>Next Steps</h3>"

                        + "<p>"
                        + "Please log in to the Digital Banking portal to review and "
                        + "digitally sign your sanction letter. Once the required "
                        + "documentation and acceptance are completed, your loan will "
                        + "proceed towards disbursement."
                        + "</p>"

                        + "<p>"
                        + "We appreciate your trust in Digital Lending and look forward "
                        + "to serving you."
                        + "</p>"

                        + "<p>"
                        + "Congratulations once again on your loan approval."
                        + "</p>"

                        + "<p>"
                        + "Kind regards,<br>"
                        + "<strong>Digital Lending Credit &amp; Disbursal Team</strong>"
                        + "</p>"

                        + "</body>"
                        + "</html>",

                escapeHtml(name),
                escapeHtml(dto.getLoanType()),
                escapeHtml(appId),
                escapeHtml(appId),
                formatCurrency(dto.getAmount()),
                dto.getTenureMonths(),
                dto.getInterestRate(),
                formatCurrency(dto.getCalculatedEMI()),
                escapeHtml(remarks)
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
                "<html>"
                        + "<body style=\"font-family: Arial, sans-serif; "
                        + "font-size: 14px; color: #333333; line-height: 1.6;\">"

                        + "<p>Dear %s,</p>"

                        + "<p>"
                        + "Thank you for your interest in Digital Lending."
                        + "</p>"

                        + "<p>"
                        + "Following a careful assessment of your loan application, "
                        + "we regret to inform you that your application could not "
                        + "be approved at this time."
                        + "</p>"

                        + "<h3>Application Details</h3>"

                        + "<table style=\"border-collapse: collapse; width: 100%%; "
                        + "max-width: 650px;\">"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application ID</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Loan Type</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Requested Amount</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Application Status</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">"
                        + "Not Approved"
                        + "</td>"
                        + "</tr>"

                        + "<tr>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd; "
                        + "font-weight: bold;\">Remarks</td>"
                        + "<td style=\"padding: 8px; border: 1px solid #dddddd;\">%s</td>"
                        + "</tr>"

                        + "</table>"

                        + "<p>"
                        + "This decision has been made following an assessment against "
                        + "our applicable lending and risk criteria."
                        + "</p>"

                        + "<p>"
                        + "If applicable, you may submit a new application after the "
                        + "relevant waiting period. If you require further information "
                        + "or assistance, please contact our Customer Support team."
                        + "</p>"

                        + "<p>"
                        + "We appreciate your understanding."
                        + "</p>"

                        + "<p>"
                        + "Kind regards,<br>"
                        + "<strong>Digital Lending Underwriting Team</strong>"
                        + "</p>"

                        + "</body>"
                        + "</html>",

                escapeHtml(name),
                escapeHtml(appId),
                escapeHtml(dto.getLoanType()),
                formatCurrency(dto.getAmount()),
                escapeHtml(remarks)
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

     /**
     * Escapes dynamic values before inserting them into HTML email content.
     */
    private String escapeHtml(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
