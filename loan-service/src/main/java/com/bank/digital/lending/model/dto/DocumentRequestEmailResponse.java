package com.bank.digital.lending.model.dto;

import java.util.List;

public record DocumentRequestEmailResponse(
        String applicationId,
        String customerId,
        String customerName,
        String customerEmail,
        List<String> requiredDocuments,
        String message,
        boolean emailSent
) {
}
