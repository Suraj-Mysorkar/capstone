package com.bank.customerservice.controller;

import com.bank.customerservice.dto.PortalAuthResponse;
import com.bank.customerservice.dto.PortalLoginRequest;
import com.bank.customerservice.dto.PortalRegisterRequest;
import com.bank.customerservice.service.PortalAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Username / password auth for the customer self-service portal. Public (no
 * bearer token required) — mirrors the employee login used by capstone-ui.
 */
@RestController
@RequestMapping("/api/customers/auth")
@RequiredArgsConstructor
@Tag(name = "Portal Auth", description = "Customer self-service registration & login")
public class PortalAuthController {

    private final PortalAuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a customer account (profile + password)")
    public ResponseEntity<PortalAuthResponse> register(@Valid @RequestBody PortalRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with username (email) and password")
    public ResponseEntity<PortalAuthResponse> login(@Valid @RequestBody PortalLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
