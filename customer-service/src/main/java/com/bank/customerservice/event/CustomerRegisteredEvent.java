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
        String firstName,
        String lastName,
        Instant occurredAt
) {
    public static CustomerRegisteredEvent of(UUID customerId, String email, String firstName, String lastName) {
        return new CustomerRegisteredEvent(customerId, email, firstName, lastName, Instant.now());
    }
}
