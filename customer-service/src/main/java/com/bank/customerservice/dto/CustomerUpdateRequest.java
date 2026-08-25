package com.bank.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Email
        String email,

        @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$", message = "phoneNumber is invalid")
        String phoneNumber,

        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,

        @Size(min = 2, max = 2)
        String countryCode
) {
}
