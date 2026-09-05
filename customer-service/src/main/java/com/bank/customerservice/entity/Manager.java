package com.bank.customerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Manager profile entity mapped to the dedicated {@code managers} table.
 */
@Entity
@Table(name = "managers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Manager {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "employee_code", length = 50, unique = true)
    private String employeeCode;

    @Column(name = "max_approval_limit", precision = 18, scale = 2)
    private BigDecimal maxApprovalLimit;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
