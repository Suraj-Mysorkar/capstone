package com.bank.digital.lending.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Request to create (or upsert, keyed by email) a row in the shared
 * {@code Customers} table without submitting a loan application.
 *
 * Used by the customer self-service portal at registration time so a
 * newly-registered customer is immediately visible to the loan officer
 * console (capstone-ui), which lists {@code GET /api/v1/loans/customers}.
 */
public record CustomerRegistrationRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address format")
        String email,

        String mobileNumber,
        String address,
        String employmentDetails,
        BigDecimal incomeDetails,
        String onboardingStatus,
        /** Opaque id from the originating service (e.g. customer-service UUID), for traceability. */
        String externalRef
) {}
