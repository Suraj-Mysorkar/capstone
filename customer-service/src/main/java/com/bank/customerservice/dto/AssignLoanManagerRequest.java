package com.bank.customerservice.dto;

import jakarta.validation.constraints.Email;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /api/customers/loan-manager-assignments}. Sent by the
 * loan-service (server to server) right after a customer submits a loan
 * application, so customer-service can assign one of its loan managers and
 * notify the customer.
 * <p>
 * Supply {@code customerId} (a {@code customer_profiles.id}) so the customer's
 * email / name can be resolved here; {@code customerEmail} / {@code customerName}
 * are accepted as a fallback when the profile is not known to this service.
 */
public record AssignLoanManagerRequest(
        UUID customerId,
        String applicationId,
        String loanType,
        BigDecimal loanAmount,
        @Email String customerEmail,
        String customerName
) {
}
