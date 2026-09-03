package com.bank.customerservice.event;

import com.azure.core.models.CloudEvent;
import com.azure.core.models.CloudEventDataFormat;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes domain events as CloudEvents to the shared Azure Event Grid topic
 * used by the "Messaging &amp; Events Layer" in the platform architecture.
 * <p>
 * Event types:
 *  - com.bank.customer.registered
 *  - com.bank.customer.statuschanged
 *  - com.bank.customer.loanmanagerassigned
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AzureEventGridPublisher implements EventPublisher {

    private static final String SOURCE = "/customer-service";
    private static final String TYPE_REGISTERED = "com.bank.customer.registered";
    private static final String TYPE_STATUS_CHANGED = "com.bank.customer.statuschanged";
    private static final String TYPE_LOAN_MANAGER_ASSIGNED = "com.bank.customer.loanmanagerassigned";

    private final EventGridPublisherClient<CloudEvent> eventGridClient;

    @Override
    public void publishCustomerRegistered(CustomerRegisteredEvent event) {
        CloudEvent cloudEvent = new CloudEvent(
                SOURCE,
                TYPE_REGISTERED,
                com.azure.core.util.BinaryData.fromObject(event),
                CloudEventDataFormat.JSON,
                "application/json"
        ).setSubject("customer/" + event.customerId());

        publish(cloudEvent, TYPE_REGISTERED, event.customerId().toString());
    }

    @Override
    public void publishCustomerStatusChanged(CustomerStatusChangedEvent event) {
        CloudEvent cloudEvent = new CloudEvent(
                SOURCE,
                TYPE_STATUS_CHANGED,
                com.azure.core.util.BinaryData.fromObject(event),
                CloudEventDataFormat.JSON,
                "application/json"
        ).setSubject("customer/" + event.customerId());

        publish(cloudEvent, TYPE_STATUS_CHANGED, event.customerId().toString());
    }

    @Override
    public void publishLoanManagerAssigned(LoanManagerAssignedEvent event) {
        String correlationId = event.customerId() != null
                ? event.customerId().toString()
                : (event.applicationId() != null ? event.applicationId() : event.customerEmail());

        CloudEvent cloudEvent = new CloudEvent(
                SOURCE,
                TYPE_LOAN_MANAGER_ASSIGNED,
                com.azure.core.util.BinaryData.fromObject(event),
                CloudEventDataFormat.JSON,
                "application/json"
        ).setSubject("customer/" + correlationId);

        // Let a failure propagate so the caller can record that the customer was
        // NOT notified (the assignment itself is already committed).
        if (!publish(cloudEvent, TYPE_LOAN_MANAGER_ASSIGNED, correlationId)) {
            throw new EventPublishException(TYPE_LOAN_MANAGER_ASSIGNED, correlationId);
        }
    }

    /** @return {@code true} if the event was accepted by Event Grid. */
    private boolean publish(CloudEvent cloudEvent, String type, String correlationId) {
        try {
            eventGridClient.sendEvents(List.of(cloudEvent));
            log.info("Published event type={} correlationId={} to Event Grid", type, correlationId);
            return true;
        } catch (Exception ex) {
            // Publishing failures should not fail the customer-facing request; the write
            // to Azure SQL has already committed. Log for alerting / manual replay.
            log.error("Failed to publish event type={} correlationId={}: {}",
                    type, correlationId, ex.getMessage(), ex);
            return false;
        }
    }

    /** Thrown by {@link #publishLoanManagerAssigned} when Event Grid rejects the event. */
    static class EventPublishException extends RuntimeException {
        EventPublishException(String type, String correlationId) {
            super("Event Grid rejected event type=" + type + " correlationId=" + correlationId);
        }
    }
}
