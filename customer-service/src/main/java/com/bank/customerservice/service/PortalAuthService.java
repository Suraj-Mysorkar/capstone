package com.bank.customerservice.service;

import com.bank.customerservice.dto.CustomerResponse;
import com.bank.customerservice.dto.PortalAuthResponse;
import com.bank.customerservice.dto.PortalLoginRequest;
import com.bank.customerservice.dto.PortalRegisterRequest;
import com.bank.customerservice.entity.AppUser;
import com.bank.customerservice.exception.DuplicateResourceException;
import com.bank.customerservice.exception.InvalidCredentialsException;
import com.bank.customerservice.exception.ResourceNotFoundException;
import com.bank.customerservice.repository.AppUserRepository;
import com.bank.customerservice.repository.CustomerRepository;
import com.bank.customerservice.security.PortalJwtIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Username / password auth for the customer self-service portal, backed by the
 * shared {@code Users} table (role {@code customer}). Registration also creates
 * the customer profile via {@link CustomerService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalAuthService {

    private static final String CUSTOMER_ROLE = "customer";

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;
    private final PortalJwtIssuer jwtIssuer;

    @Transactional
    public PortalAuthResponse register(PortalRegisterRequest request) {
        String email = request.email().trim();

        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with email " + email + " already exists — sign in instead.");
        }

        // 1. Customer profile (customer_profiles). Reuse the existing flow so the
        //    CustomerRegistered event still fires. Tolerate a pre-existing profile safely
        //    without throwing exceptions inside the transaction.
        CustomerResponse profile;
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            profile = customerService.getByEmail(email);
        } else {
            profile = customerService.register(request.toCustomerRegistration());
        }

        // 2. Credentials row in the shared Users table. loginid is a narrow column
        //    (NVARCHAR(20)) sized for employee handles, so store a short generated
        //    handle there — the customer signs in with their email (matched on the
        //    wider `email` column).
        AppUser user = AppUser.builder()
                .loginId(generateHandle())
                .loginPassword(request.password())
                .name(trim((request.firstName() + " " + request.lastName()).trim(), 100))
                .email(email)
                .userRole(CUSTOMER_ROLE)
                .build();
        user = appUserRepository.save(user);
        log.info("Portal account created: userId={}, handle={}, email={}, role={}",
                user.getUserId(), user.getLoginId(), email, CUSTOMER_ROLE);

        return buildResponse(user, profile);
    }

    private String generateHandle() {
        for (int i = 0; i < 5; i++) {
            String handle = ("c" + Long.toString(System.currentTimeMillis(), 36)
                    + Integer.toString((int) (Math.random() * 1296), 36));
            handle = handle.length() > 20 ? handle.substring(0, 20) : handle;
            if (!appUserRepository.existsByLoginIdIgnoreCase(handle)) {
                return handle;
            }
        }
        return "c" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }

    private static String trim(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) : s;
    }

    @Transactional(readOnly = true)
    public PortalAuthResponse login(PortalLoginRequest request) {
        String username = request.username().trim();
        AppUser user = appUserRepository.findByEmailIgnoreCase(username)
                .or(() -> appUserRepository.findByLoginIdIgnoreCase(username))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password."));

        if (user.getLoginPassword() == null || !user.getLoginPassword().trim().equals(request.password().trim())) {
            throw new InvalidCredentialsException("Invalid username or password.");
        }

        CustomerResponse profile = null;
        if (user.getEmail() != null) {
            try {
                profile = customerService.getByEmail(user.getEmail());
            } catch (ResourceNotFoundException ignored) {
                // login user without a customer profile (e.g. an employee) — still allowed to sign in
            }
        }
        return buildResponse(user, profile);
    }

    private PortalAuthResponse buildResponse(AppUser user, CustomerResponse profile) {
        String customerId = profile != null ? String.valueOf(profile.id()) : null;
        String onboarding = profile != null && profile.onboardingStatus() != null
                ? profile.onboardingStatus().name() : null;
        String role = user.getUserRole() != null ? user.getUserRole() : CUSTOMER_ROLE;
        // Customers sign in with their email; show that as the username, not the
        // internal loginid handle.
        String displayUsername = user.getEmail() != null ? user.getEmail() : user.getLoginId();

        String token = jwtIssuer.issue(
                displayUsername, user.getName(), role, user.getUserId(), user.getEmail(), customerId);

        return new PortalAuthResponse(
                token,
                displayUsername,
                user.getName(),
                user.getEmail(),
                role,
                user.getUserId(),
                customerId,
                onboarding,
                "valid"
        );
    }
}
