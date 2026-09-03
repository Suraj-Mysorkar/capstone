package com.bank.customerservice.dto;

/**
 * Auth response for the customer portal. Shape mirrors what capstone-ui's
 * AuthContext expects: a token plus identity/role fields.
 */
public record PortalAuthResponse(
        String token,
        String username,
        String name,
        String email,
        String role,
        Long userId,
        String customerId,
        String onboardingStatus,
        String status
) {
}
