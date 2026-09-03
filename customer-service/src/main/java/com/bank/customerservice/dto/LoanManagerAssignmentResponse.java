package com.bank.customerservice.dto;

import com.bank.customerservice.entity.LoanManagerAssignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanManagerAssignmentResponse(
        UUID id,
        UUID customerId,
        String customerEmail,
        String customerName,
        String applicationId,
        String loanType,
        BigDecimal loanAmount,
        String managerLogin,
        String managerName,
        String managerEmail,
        boolean notified,
        Instant assignedAt,
        String message
) {
    public static LoanManagerAssignmentResponse from(LoanManagerAssignment a, String message) {
        return new LoanManagerAssignmentResponse(
                a.getId(),
                a.getCustomerId(),
                a.getCustomerEmail(),
                a.getCustomerName(),
                a.getApplicationId(),
                a.getLoanType(),
                a.getLoanAmount(),
                a.getManagerLogin(),
                a.getManagerName(),
                a.getManagerEmail(),
                a.isNotified(),
                a.getAssignedAt(),
                message
        );
    }
}
