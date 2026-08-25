package com.bank.customerservice.dto;

import com.bank.customerservice.entity.OnboardingStatus;
import jakarta.validation.constraints.NotNull;

public record OnboardingStatusUpdateRequest(

        @NotNull(message = "status is required")
        OnboardingStatus status,

        String reason
) {
}
