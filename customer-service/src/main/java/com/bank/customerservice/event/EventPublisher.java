package com.bank.customerservice.event;

/**
 * Abstraction over the outbound eventing channel (Azure Event Grid).
 * Kept as an interface so it can be swapped for a no-op/mock implementation
 * in tests, or for Service Bus / Kafka in other environments.
 */
public interface EventPublisher {

    void publishCustomerRegistered(CustomerRegisteredEvent event);

    void publishCustomerStatusChanged(CustomerStatusChangedEvent event);
}
