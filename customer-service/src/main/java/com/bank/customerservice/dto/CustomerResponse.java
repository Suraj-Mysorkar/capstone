package com.bank.customerservice.dto;

import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.OnboardingStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String countryCode,
        OnboardingStatus onboardingStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getFirstName(),
                c.getLastName(),
                c.getEmail(),
                c.getPhoneNumber(),
                c.getAddressLine1(),
                c.getAddressLine2(),
                c.getCity(),
                c.getState(),
                c.getPostalCode(),
                c.getCountryCode(),
                c.getOnboardingStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
