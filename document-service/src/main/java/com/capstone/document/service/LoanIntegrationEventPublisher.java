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

        log.info("[DOCUMENT-REVIEW-EVENT] Dispatched event '{}' for document '{}' with remarks '{}'",
                eventType, docIdFormatted, remarks);
    }
}
