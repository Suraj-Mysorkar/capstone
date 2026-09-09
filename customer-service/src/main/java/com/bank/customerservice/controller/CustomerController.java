package com.bank.customerservice.controller;

import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerResponse;
import com.bank.customerservice.dto.CustomerUpdateRequest;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.AppUser;
import com.bank.customerservice.entity.OnboardingStatus;
import com.bank.customerservice.exception.ResourceNotFoundException;
import com.bank.customerservice.repository.AppUserRepository;
import com.bank.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * Exposed via Azure API Management at {@code /api/customers}. APIM validates the
 * caller and forwards their identity as {@code X-User-Id} / {@code X-User-Role}
 * headers (see {@link com.bank.customerservice.config.SecurityConfig}); the
 * {@code @PreAuthorize} guards below decide who may call what:
 *
 * <ul>
 *   <li><b>ROLE_CUSTOMER</b> — self-service portal (read / update a profile)</li>
 *   <li><b>ROLE_EMPLOYEE</b> — bank officer console (create, list, onboarding)</li>
 *   <li><b>ROLE_MANAGER</b>  — everything an employee can do, plus delete</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Profile management & onboarding status tracking")
public class CustomerController {

    /** Bank staff only. */
    private static final String STAFF =
            "hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')";
    /** The customer themselves, or bank staff. */
    private static final String CUSTOMER_OR_STAFF =
            "hasAnyRole('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER', 'CUSTOMER', 'EMPLOYEE', 'MANAGER')";
    /** Managers only. */
    private static final String MANAGER =
            "hasAnyRole('ROLE_MANAGER', 'MANAGER')";

    private final CustomerService customerService;
    private final AppUserRepository appUserRepository;

    /**
     * The signed-in customer's own profile. The portal login token carries no
     * email (only the customer id / {@code userId}), so after login the portal
     * calls this to look the email + profile up from the DB and fill the session.
     * Resolved purely from the APIM-forwarded identity headers — no id in the URL,
     * so a caller can only ever read their own record.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the signed-in customer's own profile (resolved from the APIM identity)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CustomerResponse> me(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        AppUser account = resolveSignedInAccount(userId, userName);
        String email = account.getEmail();
        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException("No email on record for the signed-in user.");
        }
        return ResponseEntity.ok(customerService.getByEmail(email));
    }

    private AppUser resolveSignedInAccount(String userId, String userName) {
        if (userId != null && !userId.isBlank()) {
            try {
                var byId = appUserRepository.findById(Long.parseLong(userId.trim()));
                if (byId.isPresent()) {
                    return byId.get();
                }
            } catch (NumberFormatException ignored) {
                // fall through to the login-id lookup
            }
        }
        if (userName != null && !userName.isBlank()) {
            var byLogin = appUserRepository.findFirstByLoginIdIgnoreCase(userName.trim());
            if (byLogin.isPresent()) {
                return byLogin.get();
            }
        }
        throw new ResourceNotFoundException("Signed-in user could not be resolved from the request identity.");
    }

    @PostMapping
    @Operation(summary = "Register a new customer (bank staff; the portal uses /auth/register)")
    @PreAuthorize(STAFF)
    public ResponseEntity<CustomerResponse> register(
            @RequestHeader(value = "X-User-Id", required = false) String userId,     // injected by APIM
            @RequestHeader(value = "X-User-Role", required = false) String userRole, // injected by APIM
            @Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerResponse created = customerService.register(request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer profile by id")
    @PreAuthorize(CUSTOMER_OR_STAFF)
    public ResponseEntity<CustomerResponse> getById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    @GetMapping(params = "email")
    @Operation(summary = "Look up a customer by email")
    @PreAuthorize(CUSTOMER_OR_STAFF)
    public ResponseEntity<CustomerResponse> getByEmail(
            @RequestParam String email,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return ResponseEntity.ok(customerService.getByEmail(email));
    }

    @GetMapping
    @Operation(summary = "List customers, optionally filtered by onboarding status")
    @PreAuthorize(STAFF)
    public ResponseEntity<Page<CustomerResponse>> list(
            @RequestParam(required = false) OnboardingStatus status,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            Pageable pageable) {
        return ResponseEntity.ok(customerService.list(status, pageable));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a customer profile")
    @PreAuthorize(CUSTOMER_OR_STAFF)
    public ResponseEntity<CustomerResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @PatchMapping("/{id}/onboarding-status")
    @Operation(summary = "Transition a customer's onboarding status")
    @PreAuthorize(STAFF)
    public ResponseEntity<CustomerResponse> updateOnboardingStatus(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody OnboardingStatusUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateOnboardingStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a customer profile")
    @PreAuthorize(MANAGER)
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
