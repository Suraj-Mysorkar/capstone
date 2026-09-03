package com.capstone.notification;

import com.capstone.notification.model.CustomerRegisterNotificationDTO;
import com.capstone.notification.model.DocumentNotificationDTO;
import com.capstone.notification.model.LoanStatusNotificationDTO;
import com.capstone.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusTopicTrigger;

public class NotificationFunction {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationService notificationService = new NotificationService();

    /**
     * Trigger 1: Azure Service Bus Topic Trigger for Loan Lifecycle Events
     * Subscribes to 'loan-events-topic' / 'notification-sub'
     */
    @FunctionName("ProcessServiceBusLoanEvents")
    public void processServiceBusLoanEvents(
        @ServiceBusTopicTrigger(
            name = "msg",
            topicName = "loan-events-topic",
            subscriptionName = "notification-sub",
            connection = "AZURE_SERVICEBUS_CONNECTION_STRING"
        ) String messageContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("[SERVICE BUS] Received Loan Event Message from topic 'loan-events-topic'");
        handleGenericEvent(messageContent, context);
    }

    /**
     * Trigger 2: Azure Service Bus Topic Trigger for Document Upload Events
     * Subscribes to 'document-events-topic' / 'notification-sub'
     */
    @FunctionName("ProcessServiceBusDocumentEvents")
    public void processServiceBusDocumentEvents(
        @ServiceBusTopicTrigger(
            name = "msg",
            topicName = "document-events-topic",
            subscriptionName = "notification-sub",
            connection = "AZURE_SERVICEBUS_CONNECTION_STRING"
        ) String messageContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("[SERVICE BUS] Received Document Event Message from topic 'document-events-topic'");
        handleGenericEvent(messageContent, context);
    }

    /**
     * Trigger 3: Azure Event Grid Trigger for Loan Status Updates
     */
    @FunctionName("NotifyLoanStatus")
    public void processLoanStatus(
        @EventGridTrigger(name = "event") String eventContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("[EVENT GRID] Received Loan Status Event");
        handleGenericEvent(eventContent, context);
    }

    /**
     * Trigger 4: Azure Event Grid Trigger for Customer Registrations
     */
    @FunctionName("NotifyCustomerRegistration")
    public void processCustomerRegistration(
        @EventGridTrigger(name = "event") String eventContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("[EVENT GRID] Received Customer Registration Event");
        handleGenericEvent(eventContent, context);
    }

    /**
     * Trigger 5: HTTP POST Trigger for Direct Notifications (/api/notify)
     */
    @FunctionName("ProcessHttpNotification")
    public com.microsoft.azure.functions.HttpResponseMessage processHttpNotification(
        @com.microsoft.azure.functions.annotation.HttpTrigger(
            name = "req",
            methods = {com.microsoft.azure.functions.HttpMethod.POST, com.microsoft.azure.functions.HttpMethod.GET},
            authLevel = com.microsoft.azure.functions.annotation.AuthorizationLevel.ANONYMOUS,
            route = "notify"
        ) com.microsoft.azure.functions.HttpRequestMessage<java.util.Optional<String>> request,
        final ExecutionContext context
    ) {
        String body = request.getBody().orElse("");
        context.getLogger().info("[HTTP NOTIFICATION] Received direct notification payload: " + body);
        handleGenericEvent(body, context);
        return request.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.OK)
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body("{\"status\":\"SUCCESS\",\"message\":\"Notification dispatched successfully\"}")
                .build();
    }

    /**
     * Unified event router for all incoming events across Service Bus and Event Grid
     */
    public void handleGenericEvent(String rawContent, ExecutionContext context) {
        if (rawContent == null || rawContent.isBlank()) {
            context.getLogger().warning("Empty event content received. Skipping.");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawContent);

            // Handle array root (e.g. Event Grid batch delivery)
            if (root.isArray() && root.size() > 0) {
                root = root.get(0);
            }

            String eventType = "";
            if (root.has("eventType")) {
                eventType = root.get("eventType").asText();
            } else if (root.has("type")) {
                eventType = root.get("type").asText();
            }

            // Extract data node if present (CloudEvent / Service Bus envelope), else root
            JsonNode dataNode = root.has("data") ? root.get("data") : root;

            context.getLogger().info("Processing Event Type: '" + eventType + "' | Payload: " + dataNode.toString());

            // 1. Customer Registration Event
            if ("CUSTOMER_REGISTERED".equalsIgnoreCase(eventType)
                    || (eventType.toLowerCase().contains("customer") && eventType.toLowerCase().contains("regist"))
                    || (!dataNode.has("applicationId") && dataNode.has("customerName") && dataNode.has("email") && !dataNode.has("documentId"))) {
                
                CustomerRegisterNotificationDTO custDto = objectMapper.treeToValue(dataNode, CustomerRegisterNotificationDTO.class);
                notificationService.sendCustomerRegistrationNotification(custDto, context);
                return;
            }

            // 2. Document Review Events (Verified / Failed / Action Required)
            if ("DOCUMENT_REVIEW_COMPLETED".equalsIgnoreCase(eventType)
                    || ("DOCUMENT_STATUS_UPDATED".equalsIgnoreCase(eventType) && "VERIFIED".equalsIgnoreCase(dataNode.path("status").asText()))) {
                DocumentNotificationDTO docDto = objectMapper.treeToValue(dataNode, DocumentNotificationDTO.class);
                notificationService.sendDocumentReviewCompletedNotification(docDto, context);
                return;
            }

            if ("DOCUMENT_REVIEW_FAILED".equalsIgnoreCase(eventType)
                    || "DOCUMENT_ACTION_REQUIRED".equalsIgnoreCase(eventType)
                    || ("DOCUMENT_STATUS_UPDATED".equalsIgnoreCase(eventType) && ("REJECTED".equalsIgnoreCase(dataNode.path("status").asText()) || "ACTION_REQUIRED".equalsIgnoreCase(dataNode.path("status").asText())))) {
                DocumentNotificationDTO docDto = objectMapper.treeToValue(dataNode, DocumentNotificationDTO.class);
                notificationService.sendDocumentReviewFailedNotification(docDto, context);
                return;
            }

            // 2b. Document Uploaded Event
            if ("DOCUMENT_UPLOADED".equalsIgnoreCase(eventType)
                    || "LOAN_DOCUMENT_UPLOADED".equalsIgnoreCase(eventType)
                    || dataNode.has("documentId")
                    || dataNode.has("blobUrl")) {
                
                DocumentNotificationDTO docDto = objectMapper.treeToValue(dataNode, DocumentNotificationDTO.class);
                notificationService.sendDocumentUploadNotification(docDto, context);
                return;
            }

            // 3. Loan Lifecycle Events (Submitted, Approved, Rejected, Manual Review)
            LoanStatusNotificationDTO loanDto = objectMapper.treeToValue(dataNode, LoanStatusNotificationDTO.class);

            if ("LOAN_APPLICATION_SUBMITTED".equalsIgnoreCase(eventType)) {
                notificationService.sendLoanApplicationReceivedNotification(loanDto, context);
            } else if ("LOAN_APPLICATION_APPROVED".equalsIgnoreCase(eventType)) {
                notificationService.sendLoanApprovalNotification(loanDto, context);
            } else if ("LOAN_APPLICATION_REJECTED".equalsIgnoreCase(eventType)) {
                notificationService.sendLoanRejectionNotification(loanDto, context);
            } else if ("LOAN_MANUAL_REVIEW_REQUIRED".equalsIgnoreCase(eventType)) {
                notificationService.sendManualReviewNotification(loanDto, context);
            } else {
                // Determine by status inside loanDto
                notificationService.sendLoanApplicationNotification(loanDto, context);
            }

        } catch (Exception e) {
            context.getLogger().severe("Failed to parse and route event: " + e.getMessage() + "\nPayload: " + rawContent);
        }
    }
}
