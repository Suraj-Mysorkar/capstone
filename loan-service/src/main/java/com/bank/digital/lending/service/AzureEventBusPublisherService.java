package com.bank.digital.lending.service;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.event.LoanApplicationCompletedEvent;
import com.bank.digital.lending.model.event.LoanCompletedEventData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Azure Service Bus Event Publisher.
 *
 * Dual-mode publisher:
 *  - azure.enabled=false (local dev): Logs the full JSON event payload to console via structured mock logger.
 *  - azure.enabled=true  (cloud):     Uses Azure Service Bus SDK (ServiceBusSenderClient) to publish the
 *    LoanApplicationCompletedEvent to the 'loan-events-topic' topic for downstream consumers
 *    (Notification Service, Disbursal Engine, Reporting).
 *
 * Authentication in cloud mode uses the Service Bus connection string from azure.servicebus.connection-string.
 * In production, replace with DefaultAzureCredential + fully-qualified namespace for Managed Identity auth.
 */
@Service
public class AzureEventBusPublisherService {

    private static final Logger log = LoggerFactory.getLogger(AzureEventBusPublisherService.class);

    @Value("${azure.enabled:false}")
    private boolean azureEnabled;

    @Value("${azure.servicebus.topic-name:loan-events-topic}")
    private String topicName;

    @Value("${azure.servicebus.connection-string:}")
    private String serviceBusConnectionString;

    private final ObjectMapper objectMapper;

    public AzureEventBusPublisherService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void publishLoanCompletedEvent(LoanApplication app) {
        LoanCompletedEventData eventData = new LoanCompletedEventData(
                app.getApplicationId(),
                app.getCustomerId(),
                app.getCustomerName(),
                app.getCustomerEmail(),
                app.getLoanType(),
                app.getLoanAmount(),
                app.getTenureMonths(),
                app.getInterestRate(),
                app.getCalculatedEMI(),
                app.getStatus(),
                app.getRiskScore(),
                app.getDtiRatio(),
                app.getAssignedManager() != null ? app.getAssignedManager() : "SYSTEM_CREDIT_ENGINE",
                app.getDecisionRemarks(),
                LocalDateTime.now()
        );

        LoanApplicationCompletedEvent event = LoanApplicationCompletedEvent.of(eventData);

        try {
            String jsonPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);

            if (azureEnabled) {
                // ─── AZURE CLOUD MODE ─────────────────────────────────────────────────────
                // Publishes the event to Azure Service Bus Topic using the SDK.
                // ServiceBusSenderClient is synchronous and thread-safe.
                // In production: replace connection-string auth with DefaultAzureCredential
                // + fully-qualified namespace for zero-secret Managed Identity auth.
                log.info("[AZURE SERVICE BUS] Publishing LoanApplicationCompletedEvent to topic '{}'...", topicName);

                try (ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                        .connectionString(serviceBusConnectionString)
                        .sender()
                        .topicName(topicName)
                        .buildClient()) {

                    ServiceBusMessage message = new ServiceBusMessage(jsonPayload);
                    message.setMessageId(event.eventId());
                    message.setContentType("application/json");
                    message.getApplicationProperties().put("eventType", event.eventType());
                    message.getApplicationProperties().put("applicationId", app.getApplicationId());
                    message.getApplicationProperties().put("finalStatus", app.getStatus().name());

                    senderClient.sendMessage(message);

                    log.info("[AZURE SERVICE BUS] Message ID '{}' published to topic '{}' — ACK received.", event.eventId(), topicName);
                }

            } else {
                // ─── LOCAL DEV / MOCK MODE ────────────────────────────────────────────────
                // Structured console logger simulates what the real SDK call would do.
                log.info("================================================================================");
                log.info("[MOCK AZURE SERVICE BUS] >>> DISPATCHING MESSAGE TO AZURE EVENT BUS <<<");
                log.info("[MOCK AZURE SERVICE BUS] Target Topic: '{}' | Event Type: '{}'", topicName, event.eventType());
                log.info("[MOCK AZURE SERVICE BUS] Message ID: {} | Source: {}", event.eventId(), event.source());
                log.info("[MOCK AZURE SERVICE BUS] Application: {} | Final Status: {}", app.getApplicationId(), app.getStatus());
                log.info("[MOCK AZURE SERVICE BUS] Event Payload:\n{}", jsonPayload);
                log.info("[MOCK AZURE SERVICE BUS] >>> MESSAGE PUBLISHED SUCCESSFULLY (ACK RECEIVED) <<<");
                log.info("================================================================================");
            }

        } catch (Exception e) {
            log.error("[AZURE SERVICE BUS] Failed to serialize or publish event for application {}: {}",
                    app.getApplicationId(), e.getMessage(), e);
        }
    }
}
