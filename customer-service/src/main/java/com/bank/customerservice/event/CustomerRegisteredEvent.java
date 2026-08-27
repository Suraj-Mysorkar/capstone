package com.bank.customerservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the "Customer.Registered" Event Grid topic whenever a new
 * customer completes registration. Consumed by the Notification Service
 * and Event-Driven Services shown in the architecture diagram.
 */
public record CustomerRegisteredEvent(
        UUID customerId,
        String email,
        String customerName,
        String status,
        Instant occurredAt
) {
    public static CustomerRegisteredEvent of(UUID customerId, String email, String customerName, String status) {
        return new CustomerRegisteredEvent(customerId, email, customerName, status, Instant.now());
    }
}
