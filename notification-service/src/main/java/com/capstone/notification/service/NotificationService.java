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

        String subject = "Welcome to Digital Banking - Account Registered Successfully";
        String body = String.format(
                "Dear %s,\n\n"
                + "Welcome to Digital Banking! Your customer account has been registered successfully.\n"
                + "Account Status: %s\n\n"
                + "You can now log in to apply for loans, track existing applications, and securely upload documents.\n\n"
                + "Best Regards,\n"
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
                + "Thank you for applying for a %s Loan with Digital Lending.\n\n"
                + "── Application Summary ──\n"
                + "• Application ID   : %s\n"
                + "• Requested Amount : %s\n"
                + "• Tenure           : %d Months\n"
                + "• Estimated EMI    : %s/month\n"
                + "• Status           : %s\n\n"
                + "Your application has been received and entered our automated validation and credit assessment engine. "
                + "We will notify you as soon as the status is updated.\n\n"
                + "Best Regards,\n"
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
                + "We have successfully received your %s document for Loan Application %s.\n\n"
                + "── Document Details ──\n"
                + "• Document ID   : %s\n"
                + "• Document Type : %s\n"
                + "• Status        : Uploaded & Under Verification\n\n"
                + "Our automated document verification system is reviewing the submitted files. "
                + "Your loan application will advance to the next step once verified.\n\n"
                + "Best Regards,\n"
                + "Digital Lending Verification Team",
                name, docType, appId, dto.getDocumentId(), docType
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 4. Handles Manual Review Escalations
     */
    public void sendManualReviewNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String subject = String.format("Application Update: Manual Review in Progress - %s", appId);
        String body = String.format(
                "Dear %s,\n\n"
                + "Your loan application (%s) for %s is currently being reviewed by our Senior Underwriting Team.\n\n"
                + "── Review Information ──\n"
                + "• Application ID   : %s\n"
                + "• Current Status   : MANUAL_REVIEW_REQUIRED\n"
                + "• Review Stage     : Level 2 Operations / Credit Assessment\n"
                + "• Notes            : %s\n\n"
                + "No further action is required from you at this stage. An Underwriting Specialist will complete the review shortly.\n\n"
                + "Best Regards,\n"
                + "Digital Lending Underwriting Team",
                name, appId, formatCurrency(dto.getAmount()), appId,
                dto.getDecisionRemarks() != null ? dto.getDecisionRemarks() : "Standard manual risk assessment."
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 5. Handles Loan Approval (Auto or Manual)
     */
    public void sendLoanApprovalNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String subject = String.format("🎉 Congratulations! Your Loan Application %s is Approved", appId);
        String body = String.format(
                "Dear %s,\n\n"
                + "We are pleased to inform you that your %s Loan Application (%s) has been APPROVED!\n\n"
                + "── Approved Sanction Terms ──\n"
                + "• Sanctioned Amount: %s\n"
                + "• Tenure           : %d Months\n"
                + "• Interest Rate    : %.2f%% p.a.\n"
                + "• Monthly EMI      : %s/month\n"
                + "• Remarks          : %s\n\n"
                + "Next Step: Please log into the customer portal to digitally review and sign your sanction letter for instant disbursement.\n\n"
                + "Congratulations on your loan approval!\n\n"
                + "Best Regards,\n"
                + "Digital Lending Credit & Disbursal Team",
                name, dto.getLoanType(), appId, formatCurrency(dto.getAmount()),
                dto.getTenureMonths(), dto.getInterestRate(), formatCurrency(dto.getCalculatedEMI()),
                dto.getDecisionRemarks() != null ? dto.getDecisionRemarks() : "Approved based on credit criteria."
        );

        dispatchEmail(new EmailDto(dto.getCustomerEmail(), subject, body), context);
    }

    /**
     * 6. Handles Loan Rejection
     */
    public void sendLoanRejectionNotification(LoanStatusNotificationDTO dto, ExecutionContext context) {
        String name = dto.getCustomerName();
        String appId = dto.getApplicationId();

        String subject = String.format("Status Update regarding your Loan Application %s", appId);
        String body = String.format(
                "Dear %s,\n\n"
                + "Thank you for your interest in Digital Lending.\n\n"
                + "After careful evaluation of your application (%s) for %s against our credit and risk underwriting criteria, "
                + "we regret to inform you that we cannot approve your loan application at this time.\n\n"
                + "── Decision Details ──\n"
                + "• Status  : REJECTED\n"
                + "• Remarks : %s\n\n"
                + "You may re-apply after 90 days or contact customer support for further inquiries.\n\n"
                + "Best Regards,\n"
                + "Digital Lending Underwriting Team",
                name, appId, formatCurrency(dto.getAmount()),
                dto.getDecisionRemarks() != null ? dto.getDecisionRemarks() : "Does not meet current lending guidelines."
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
