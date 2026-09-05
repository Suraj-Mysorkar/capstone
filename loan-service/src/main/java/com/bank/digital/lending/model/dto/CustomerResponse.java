package com.bank.digital.lending.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerResponse(
        java.util.UUID customerId,
        String customerCode,
        String fullName,
        String email,
        String mobileNumber,
        String address,
        String employmentDetails,
        BigDecimal incomeDetails,
        String onboardingStatus,
        LocalDateTime createdAt
) {
}
