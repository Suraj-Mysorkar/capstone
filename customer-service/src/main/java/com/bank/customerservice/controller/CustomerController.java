package com.bank.customerservice.controller;

import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerResponse;
import com.bank.customerservice.dto.CustomerUpdateRequest;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.OnboardingStatus;
import com.bank.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * Exposed via Azure API Management at /api/customers (see API Gateway Layer
 * in the platform architecture). Reached by both the Angular/Vue web app and
 * the mobile banking app.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Profile management & onboarding status tracking")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Register a new customer")
    @PreAuthorize("hasAnyAuthority('SCOPE_customers.write', 'ROLE_customer_admin')")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerResponse created = customerService.register(request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer profile by id")
    @PreAuthorize("hasAnyAuthority('SCOPE_customers.read', 'ROLE_customer_admin')")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    @GetMapping(params = "email")
    @Operation(summary = "Look up a customer by email")
    @PreAuthorize("hasAnyAuthority('SCOPE_customers.read', 'ROLE_customer_admin')")
    public ResponseEntity<CustomerResponse> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(customerService.getByEmail(email));
    }

    @GetMapping
    @Operation(summary = "List customers, optionally filtered by onboarding status")
    @PreAuthorize("hasAnyAuthority('SCOPE_customers.read', 'ROLE_customer_admin')")
    public ResponseEntity<Page<CustomerResponse>> list(
            @RequestParam(required = false) OnboardingStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(customerService.list(status, pageable));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a customer profile")
    @PreAuthorize("hasAnyAuthority('SCOPE_customers.write', 'ROLE_customer_admin')")
    public ResponseEntity<CustomerResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @PatchMapping("/{id}/onboarding-status")
    @Operation(summary = "Transition a customer's onboarding status")
    @PreAuthorize("hasAnyAuthority('SCOPE_customers.write', 'ROLE_customer_admin')")
    public ResponseEntity<CustomerResponse> updateOnboardingStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OnboardingStatusUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateOnboardingStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a customer profile")
    @PreAuthorize("hasAuthority('ROLE_customer_admin')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
