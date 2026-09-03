package com.bank.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service registration from the customer portal: profile fields plus the
 * password the customer chooses. The password is stored in the shared
 * {@code Users} table with {@code user_role = 'customer'}.
 */
public record PortalRegisterRequest(

        @NotBlank(message = "firstName is required") @Size(max = 100)
        String firstName,

        @NotBlank(message = "lastName is required") @Size(max = 100)
        String lastName,

        @NotBlank(message = "email is required") @Email(message = "email must be a valid email address")
        String email,

        @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$", message = "phoneNumber is invalid")
        String phoneNumber,

        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,

        @Size(min = 2, max = 2, message = "countryCode must be an ISO-3166 alpha-2 code")
        String countryCode,

        @NotBlank(message = "password is required")
        @Size(min = 6, max = 100, message = "password must be 6-100 characters")
        String password
) {
    public CustomerRegistrationRequest toCustomerRegistration() {
        return new CustomerRegistrationRequest(
                firstName, lastName, email, phoneNumber,
                addressLine1, addressLine2, city, state, postalCode, countryCode);
    }
}
