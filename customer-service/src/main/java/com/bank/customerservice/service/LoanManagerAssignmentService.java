package com.bank.customerservice.service;

import com.bank.customerservice.dto.AssignLoanManagerRequest;
import com.bank.customerservice.dto.LoanManagerAssignmentResponse;
import com.bank.customerservice.entity.AppUser;
import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.LoanManagerAssignment;
import com.bank.customerservice.event.LoanManagerAssignedEvent;
import com.bank.customerservice.event.EventPublisher;
import com.bank.customerservice.exception.ResourceNotFoundException;
import com.bank.customerservice.repository.AppUserRepository;
import com.bank.customerservice.repository.CustomerRepository;
import com.bank.customerservice.repository.LoanManagerAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Assigns one of the loan managers ({@code Users.user_role = 'manager'}, seeded
 * by {@link com.bank.customerservice.config.LoanManagerSeeder}) to a customer
 * when they apply for a loan, and notifies the customer through the notification
 * service.
 *
 * <p>The manager carrying the fewest current assignments is picked, so the queue
 * stays balanced. Assignment is idempotent per {@code applicationId}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanManagerAssignmentService {

    static final String MANAGER_ROLE = "manager";

    private final AppUserRepository appUserRepository;
    private final CustomerRepository customerRepository;
    private final LoanManagerAssignmentRepository assignmentRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public LoanManagerAssignmentResponse assign(AssignLoanManagerRequest request) {
        String applicationId = trimToNull(request.applicationId());

        // 1. Idempotency — one manager per loan application.
        if (applicationId != null) {
            var existing = assignmentRepository.findFirstByApplicationIdIgnoreCase(applicationId);
            if (existing.isPresent()) {
                LoanManagerAssignment a = existing.get();
                log.info("Loan application {} already has manager {} — returning existing assignment",
                        applicationId, a.getManagerLogin());
                return LoanManagerAssignmentResponse.from(a, buildMessage(a.getManagerName(), a.getApplicationId()));
            }
        }

        // 2. Resolve the customer's contact details (from the profile when we can).
        String email = trimToNull(request.customerEmail());
        String name = trimToNull(request.customerName());
        UUID customerId = request.customerId();
        if (customerId != null) {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer != null) {
                if (email == null) email = customer.getEmail();
                if (name == null) name = (customer.getFirstName() + " " + customer.getLastName()).trim();
            } else {
                log.warn("assign-manager: no customer_profiles row for id={}; using supplied contact details", customerId);
            }
        }
        if (email == null) {
            throw new ResourceNotFoundException(
                    "Cannot assign a loan manager: unknown customerId and no customerEmail supplied.");
        }

        // 3. Pick the least-loaded manager.
        List<AppUser> managers = appUserRepository.findByUserRoleIgnoreCase(MANAGER_ROLE);
        if (managers.isEmpty()) {
            throw new IllegalStateException(
                    "No loan managers are configured (no Users rows with user_role='manager').");
        }
        AppUser chosen = managers.stream()
                .min(Comparator.comparingLong(m -> assignmentRepository.countByManagerLogin(m.getLoginId())))
                .orElse(managers.get(0));
        String managerName = displayName(chosen);

        // 4. Persist the assignment.
        LoanManagerAssignment assignment = LoanManagerAssignment.builder()
                .customerId(customerId)
                .customerEmail(email)
                .customerName(name)
                .applicationId(applicationId)
                .loanType(trimToNull(request.loanType()))
                .loanAmount(request.loanAmount())
                .managerUserId(chosen.getUserId())
                .managerLogin(chosen.getLoginId())
                .managerName(chosen.getName())
                .managerEmail(chosen.getEmail())
                .notified(false)
                .build();
        assignment = assignmentRepository.save(assignment);
        log.info("Assigned loan manager {} ({}) to customer {} / application {}",
                chosen.getLoginId(), managerName, email, applicationId);

        // 5. Notify the customer via the notification service (Event Grid CloudEvent).
        String message = buildMessage(managerName, applicationId);
        try {
            eventPublisher.publishLoanManagerAssigned(new LoanManagerAssignedEvent(
                    customerId,
                    email,
                    name,
                    applicationId,
                    assignment.getLoanType(),
                    assignment.getLoanAmount(),
                    managerName,
                    chosen.getLoginId(),
                    chosen.getEmail(),
                    message,
                    Instant.now()
            ));
            assignment.setNotified(true);
            assignment = assignmentRepository.save(assignment);
        } catch (Exception ex) {
            // The assignment is committed regardless; the notification is best-effort.
            log.error("Failed to publish manager-assigned notification for assignment {}: {}",
                    assignment.getId(), ex.getMessage(), ex);
        }

        return LoanManagerAssignmentResponse.from(assignment, message);
    }

    @Transactional(readOnly = true)
    public List<LoanManagerAssignmentResponse> forCustomer(UUID customerId) {
        return assignmentRepository.findByCustomerIdOrderByAssignedAtDesc(customerId).stream()
                .map(a -> LoanManagerAssignmentResponse.from(a, buildMessage(a.getManagerName(), a.getApplicationId())))
                .toList();
    }

    private static String buildMessage(String managerName, String applicationId) {
        String who = StringUtils.hasText(managerName) ? managerName : "a relationship manager";
        String app = StringUtils.hasText(applicationId) ? " " + applicationId : "";
        return "A relationship manager (" + who + ") has been assigned to your loan application" + app
                + " and will guide you through verification and approval.";
    }

    private static String displayName(AppUser user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getLoginId();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
