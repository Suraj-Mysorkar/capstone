package com.bank.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalLoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) {
}
