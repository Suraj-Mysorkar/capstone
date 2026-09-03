package com.capstone.document.service;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LoanIntegrationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanIntegrationEventPublisher.class);

    @Value("${loan-service.base-url:http://localhost:8080/api/v1/loans}")
    private String loanServiceBaseUrl;

    @Value("${azure.servicebus.topic-name:document-events-topic}")
    private String eventBusTopic;

    @Value("${azure.servicebus.queue-name:document-events-queue}")
    private String eventBusQueue;

    @Value("${azure.servicebus.connection-string:}")
    private String serviceBusConnectionString;

    private final RestClient restClient;

    public LoanIntegrationEventPublisher() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * Publishes DOCUMENT_UPLOADED event to Azure Event Bus and notifies Loan Service.
     */
    public void publishDocumentUploadedEvent(Long documentId,
                                            String applicationId,
                                            String customerId,
                                            String documentType,
                                            String blobUrl) {
        String eventId = "evt-" + UUID.randomUUID();
        String timestamp = Instant.now().toString();
        String docIdFormatted = "DOC-" + documentId;

        String jsonPayload = "{\n"
                + "  \"eventId\": \"" + eventId + "\",\n"
                + "  \"eventType\": \"DOCUMENT_UPLOADED\",\n"
                + "  \"timestamp\": \"" + timestamp + "\",\n"
                + "  \"data\": {\n"
                + "    \"documentId\": \"" + docIdFormatted + "\",\n"
                + "    \"applicationId\": \"" + applicationId + "\",\n"
                + "    \"customerId\": \"" + customerId + "\",\n"
                + "    \"documentType\": \"" + documentType + "\",\n"
                + "    \"blobUrl\": \"" + blobUrl + "\"\n"
                + "  }\n"
                + "}";

        // 1. Dispatch event to Azure Service Bus if connection string is configured
        if (serviceBusConnectionString != null && !serviceBusConnectionString.isBlank() && !serviceBusConnectionString.contains("placeholder")) {
            String sessionKey = (applicationId != null && !applicationId.isBlank()) ? applicationId : ("SESSION-" + eventId);

            // Try sending to topic
            try (ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                    .connectionString(serviceBusConnectionString)
                    .sender()
                    .topicName(eventBusTopic)
                    .buildClient()) {

                ServiceBusMessage message = new ServiceBusMessage(jsonPayload);
                message.setMessageId(eventId);
                message.setContentType("application/json");
                message.setSubject("DOCUMENT_UPLOADED");
                message.setSessionId(sessionKey);
                message.setPartitionKey(sessionKey);
                message.getApplicationProperties().put("eventType", "DOCUMENT_UPLOADED");
                message.getApplicationProperties().put("applicationId", applicationId);
                message.getApplicationProperties().put("customerId", customerId != null ? customerId : "");
                message.getApplicationProperties().put("documentType", documentType);

                senderClient.sendMessage(message);
                log.info("[AZURE SERVICE BUS] ✅ Message ID '{}' (Session: '{}') successfully published to topic '{}' — ACK RECEIVED!",
                        eventId, sessionKey, eventBusTopic);
            } catch (Exception topicEx) {
                log.error("[AZURE SERVICE BUS] Topic '{}' error: {}", eventBusTopic, topicEx.getMessage(), topicEx);
            }
        }

        // Mock / Audit Log
        log.info("================================================================================");
        log.info("[AZURE EVENT BUS] >>> 'DOCUMENT_UPLOADED' EVENT PROCESSED <<<");
        log.info("[AZURE EVENT BUS] Application ID: {}", applicationId);
        log.info("[AZURE EVENT BUS] Customer ID   : {}", customerId);
        log.info("[AZURE EVENT BUS] Document Type : {}", documentType);
        log.info("[AZURE EVENT BUS] Document ID   : {}", docIdFormatted);
        log.info("[AZURE EVENT BUS] Azure Blob URL: {}", blobUrl);
        log.info("================================================================================");

        // 2. Direct real-time callback to Loan Service to advance loan application workflow
        if (applicationId != null && !applicationId.isBlank()) {
            try {
                String targetUrl = loanServiceBaseUrl + "/applications/" + applicationId + "/document-uploaded";
                log.info("[DOCUMENT-SERVICE -> LOAN-SERVICE] Notifying loan-service at: {}", targetUrl);

                Map<String, Object> body = new java.util.LinkedHashMap<>();
                body.put("documentIds", List.of(docIdFormatted));
                body.put("customerId", customerId != null ? customerId : "UNKNOWN");
                body.put("documentType", documentType);
                body.put("documentName", documentType + ".pdf");
                body.put("blobUrl", blobUrl);
                body.put("blobPath", blobUrl);
                body.put("contentType", "application/pdf");
                body.put("fileSizeBytes", 0L);

                restClient.post()
                        .uri(targetUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();

                log.info("[DOCUMENT-SERVICE -> LOAN-SERVICE] ✅ Loan service received document upload notification. Workflow updated!");
            } catch (Exception e) {
                log.warn("[DOCUMENT-SERVICE -> LOAN-SERVICE] Could not directly reach loan-service (is it running?): {}", e.getMessage());
            }
        }
    }

    /**
     * Publishes DOCUMENT_REVIEW_COMPLETED or DOCUMENT_REVIEW_FAILED event to Azure Service Bus.
     */
    public void publishDocumentReviewEvent(Long documentId,
                                           String applicationId,
                                           String customerId,
                                           String documentName,
                                           String documentType,
                                           String status,
                                           String remarks,
                                           String verifiedBy,
                                           String customerEmail) {
        String eventId = "evt-" + UUID.randomUUID();
        String timestamp = Instant.now().toString();
        String docIdFormatted = "DOC-" + documentId;
        String eventType = "VERIFIED".equalsIgnoreCase(status) ? "DOCUMENT_REVIEW_COMPLETED" : "DOCUMENT_REVIEW_FAILED";

        String jsonPayload = "{\n"
                + "  \"eventId\": \"" + eventId + "\",\n"
                + "  \"eventType\": \"" + eventType + "\",\n"
                + "  \"timestamp\": \"" + timestamp + "\",\n"
                + "  \"data\": {\n"
                + "    \"documentId\": \"" + docIdFormatted + "\",\n"
                + "    \"applicationId\": \"" + (applicationId != null ? applicationId : "") + "\",\n"
                + "    \"customerId\": \"" + (customerId != null ? customerId : "") + "\",\n"
                + "    \"customerEmail\": \"" + (customerEmail != null && !customerEmail.isBlank() ? customerEmail : "itsarpitgupta@gmail.com") + "\",\n"
                + "    \"documentName\": \"" + (documentName != null ? documentName.replace("\"", "\\\"") : "Document") + "\",\n"
                + "    \"documentType\": \"" + documentType + "\",\n"
                + "    \"status\": \"" + status + "\",\n"
                + "    \"remarks\": \"" + (remarks != null ? remarks.replace("\"", "\\\"") : "") + "\",\n"
                + "    \"verifiedBy\": \"" + (verifiedBy != null ? verifiedBy.replace("\"", "\\\"") : "Operations Manager") + "\"\n"
                + "  }\n"
                + "}";

        if (serviceBusConnectionString != null && !serviceBusConnectionString.isBlank() && !serviceBusConnectionString.contains("placeholder")) {
            String sessionKey = (applicationId != null && !applicationId.isBlank()) ? applicationId : ("SESSION-" + eventId);

            try (ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                    .connectionString(serviceBusConnectionString)
                    .sender()
                    .topicName(eventBusTopic)
                    .buildClient()) {

                ServiceBusMessage message = new ServiceBusMessage(jsonPayload);
                message.setMessageId(eventId);
                message.setContentType("application/json");
                message.setSubject(eventType);
                message.setSessionId(sessionKey);
                message.setPartitionKey(sessionKey);
                message.getApplicationProperties().put("eventType", eventType);
                message.getApplicationProperties().put("applicationId", applicationId != null ? applicationId : "");
                message.getApplicationProperties().put("customerId", customerId != null ? customerId : "");
                message.getApplicationProperties().put("customerEmail", customerEmail != null && !customerEmail.isBlank() ? customerEmail : "itsarpitgupta@gmail.com");
                message.getApplicationProperties().put("status", status);

                senderClient.sendMessage(message);
                log.info("[AZURE SERVICE BUS] ✅ Message ID '{}' ('{}') published to topic '{}' for recipient '{}'",
                        eventId, eventType, eventBusTopic, customerEmail);
            } catch (Exception topicEx) {
                log.error("[AZURE SERVICE BUS] Topic '{}' error: {}", eventBusTopic, topicEx.getMessage());
            }
        }

        // Direct Real-time callback to Loan Service to advance loan application workflow
        if (applicationId != null && !applicationId.isBlank()) {
            try {
                String targetUrl = loanServiceBaseUrl + "/applications/" + applicationId + "/document-reviewed";
                log.info("[DOCUMENT-SERVICE -> LOAN-SERVICE] Notifying loan-service of review decision at: {}", targetUrl);

                Map<String, Object> body = new java.util.LinkedHashMap<>();
                body.put("documentId", docIdFormatted);
                body.put("documentType", documentType);
                body.put("status", status);
                body.put("remarks", remarks);
                body.put("verifiedBy", verifiedBy);
                body.put("customerEmail", customerEmail);

                restClient.post()
                        .uri(targetUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();

                log.info("[DOCUMENT-SERVICE -> LOAN-SERVICE] ✅ Loan service received document review decision. Application status updated!");
            } catch (Exception e) {
                log.warn("[DOCUMENT-SERVICE -> LOAN-SERVICE] Could not directly reach loan-service: {}", e.getMessage());
            }
        }

        // Direct Email Notification via Logic App to Customer
        dispatchDocumentReviewEmail(documentId, applicationId, customerId, documentName, documentType, status, remarks, verifiedBy, customerEmail);

        log.info("[DOCUMENT-REVIEW-EVENT] Dispatched event '{}' for document '{}' with remarks '{}'",
                eventType, docIdFormatted, remarks);
    }

    private void dispatchDocumentReviewEmail(Long documentId, String applicationId, String customerId,
                                             String documentName, String documentType, String status,
                                             String remarks, String verifiedBy, String customerEmail) {
        String recipient = (customerEmail != null && !customerEmail.isBlank()) ? customerEmail : "kanikagupta288@gmail.com";
        boolean isApproved = "VERIFIED".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status);

        String subject = (isApproved ? "✅ Document Approved: " : "❌ Document Action Required (Rejected): ")
                + (documentName != null ? documentName : "Document DOC-" + documentId);

        String headerBg = isApproved ? "linear-gradient(135deg, #059669 0%, #10b981 100%)" : "linear-gradient(135deg, #dc2626 0%, #ef4444 100%)";
        String statusLabel = isApproved ? "APPROVED / VERIFIED" : "REJECTED (ACTION REQUIRED)";
        String statusColor = isApproved ? "#10b981" : "#ef4444";

        String htmlBody = String.format(
                "<html><body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #333333; line-height: 1.6;\">"
                + "<div style=\"background: %s; padding: 18px 24px; border-radius: 8px 8px 0 0; color: #ffffff;\">"
                + "<h2 style=\"margin: 0; font-size: 18px;\">%s</h2>"
                + "<p style=\"margin: 4px 0 0; font-size: 13px; opacity: 0.9;\">Document Verification Update for Customer ID: %s</p>"
                + "</div>"
                + "<div style=\"padding: 20px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 8px 8px; background: #ffffff;\">"
                + "<p>Dear Customer,</p>"
                + "<p>Your uploaded document has been reviewed by the Loan Underwriting &amp; Verification Team.</p>"
                + "<table style=\"border-collapse: collapse; width: 100%%; max-width: 600px; margin-bottom: 16px;\">"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Document ID</td><td style=\"padding: 8px; border: 1px solid #ddd;\">DOC-%d</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Document Name</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Document Type</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Application ID</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Review Status</td><td style=\"padding: 8px; border: 1px solid #ddd; color: %s; font-weight: bold;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Reviewer Remarks</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Verified By</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "</table>"
                + (isApproved
                    ? "<p style=\"color: #059669; font-weight: bold;\">No further action is required for this document. Your loan application will now proceed to the next stage.</p>"
                    : "<div style=\"background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; padding: 12px; margin-bottom: 16px;\"><p style=\"color: #991b1b; margin: 0; font-weight: bold;\">Action Required: Please log in to the Customer Portal, navigate to 'My Documents', and upload a clear, revised image/file to continue your loan approval.</p></div>"
                  )
                + "<p>Kind regards,<br><strong>Digital Banking Operations &amp; Underwriting Team</strong></p>"
                + "</div></body></html>",
                headerBg,
                isApproved ? "Document Verified & Approved" : "Document Review Action Required (Rejected)",
                customerId != null ? customerId : "N/A",
                documentId,
                documentName != null ? documentName : "Document",
                documentType != null ? documentType : "KYC",
                applicationId != null ? applicationId : "N/A",
                statusColor,
                statusLabel,
                remarks != null && !remarks.isBlank() ? remarks : (isApproved ? "Document verified successfully." : "Document image unreadable or invalid. Please re-upload."),
                verifiedBy != null ? verifiedBy : "Operations Manager"
        );

        String logicAppUrl = "https://prod-17.southindia.logic.azure.com:443/workflows/a4b29c1d5e814824900b41a17fa24844/triggers/When_a_HTTP_request_is_received/paths/invoke?api-version=2016-10-01&sp=%2Ftriggers%2FWhen_a_HTTP_request_is_received%2Frun&sv=1.0&sig=F--JabvW3Uwr-JsZU76HgaWWTcekahkC6HBwTEImtys";

        try {
            String payload = String.format("{\"emailTo\":\"%s\",\"emailSubject\":\"%s\",\"emailBody\":\"%s\"}",
                    recipient.replace("\"", "\\\""),
                    subject.replace("\"", "\\\""),
                    htmlBody.replace("\"", "\\\"").replace("\n", "").replace("\r", ""));

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(logicAppUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            log.info("[DOCUMENT-SERVICE] ✅ Document review email sent to {} - Logic App status: {}", recipient, resp.statusCode());
        } catch (Exception ex) {
            log.warn("[DOCUMENT-SERVICE] Document review email dispatch note: {}", ex.getMessage());
        }
    }
}
