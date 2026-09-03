package com.bank.customerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records that a loan manager (a row in the shared {@code Users} table with
 * {@code user_role = 'manager'}) has been assigned to a customer for a specific
 * loan application.
 * <p>
 * customer-service owns this table ({@code loan_manager_assignments}). The
 * assignment is created when the loan-service tells us a customer has applied
 * for a loan (see {@code LoanManagerAssignmentController}); on creation the
 * customer is notified through the notification service via an Event Grid
 * CloudEvent ({@code com.bank.customer.loanmanagerassigned}).
 */
@Entity
@Table(name = "loan_manager_assignments", indexes = {
        @Index(name = "idx_lma_customer_id", columnList = "customer_id"),
        @Index(name = "idx_lma_application_id", columnList = "application_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanManagerAssignment {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /** customer_profiles.id — nullable when the caller only supplied an email. */
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** loan-service application id, e.g. {@code APP-1A2B3C4D}. */
    @Column(name = "application_id", length = 64)
    private String applicationId;

    @Column(name = "loan_type", length = 40)
    private String loanType;

    @Column(name = "loan_amount", precision = 18, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Column(name = "manager_login", nullable = false, length = 20)
    private String managerLogin;

    @Column(name = "manager_name", length = 150)
    private String managerName;

    @Column(name = "manager_email", length = 255)
    private String managerEmail;

    /** Whether the "manager assigned" customer notification was dispatched. */
    @Column(name = "notified", nullable = false)
    @Builder.Default
    private boolean notified = false;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.assignedAt == null) {
            this.assignedAt = Instant.now();
        }
    }
}
