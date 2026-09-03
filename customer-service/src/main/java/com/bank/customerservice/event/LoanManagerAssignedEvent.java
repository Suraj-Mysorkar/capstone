package com.bank.customerservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to the shared Event Grid topic ({@code com.bank.customer.loanmanagerassigned})
 * when a loan manager is assigned to a customer's loan application. Consumed by
 * the Notification Service, which emails the customer to tell them who is
 * handling their application.
 * <p>
 * {@code message} carries a ready-to-send human sentence so a consumer does not
 * have to reconstruct it.
 */
public record LoanManagerAssignedEvent(
        UUID customerId,
        String customerEmail,
        String customerName,
        String applicationId,
        String loanType,
        BigDecimal loanAmount,
        String managerName,
        String managerLogin,
        String managerEmail,
        String message,
        Instant occurredAt
) {
}
