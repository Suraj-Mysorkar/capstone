package com.bank.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(

        @NotBlank(message = "firstName is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$", message = "phoneNumber is invalid")
        String phoneNumber,

        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,

        @Size(min = 2, max = 2, message = "countryCode must be an ISO-3166 alpha-2 code")
        String countryCode
) {
}
