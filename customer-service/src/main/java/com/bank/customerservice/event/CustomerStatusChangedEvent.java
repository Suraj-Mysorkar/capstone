package com.bank.customerservice.event;

import com.bank.customerservice.entity.OnboardingStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the "Customer.StatusChanged" Event Grid topic whenever a
 * customer's onboarding status transitions. Consumed by Notification Service
 * for customer alerts and by the Reporting Dashboard Service for status feeds.
 */
public record CustomerStatusChangedEvent(
        UUID customerId,
        OnboardingStatus previousStatus,
        OnboardingStatus newStatus,
        String reason,
        Instant occurredAt
) {
    public static CustomerStatusChangedEvent of(UUID customerId, OnboardingStatus previous,
                                                 OnboardingStatus next, String reason) {
        return new CustomerStatusChangedEvent(customerId, previous, next, reason, Instant.now());
    }
}
