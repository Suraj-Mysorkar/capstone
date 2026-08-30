package com.bank.customerservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer profile, persisted in the shared Azure SQL Database (smzen-capstone-db).
 * <p>
 * Owns its own {@code customer_profiles} table — this is intentionally separate
 * from the {@code Customers} table written by loan-service / report-service, which
 * has a different primary-key type and column set.
 */
@Entity
@Table(name = "customer_profiles", indexes = {
        @Index(name = "idx_customer_profiles_email", columnList = "email", unique = true),
        @Index(name = "idx_customer_profiles_status", columnList = "onboardingStatus")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotBlank
    @Column(nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String lastName;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String addressLine1;

    @Column(length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 2)
    private String countryCode;

    /**
     * External subject identifier from Microsoft Entra ID (JWT {@code sub} claim).
     * Nullable — a profile is created at registration and linked to an identity
     * later, so many rows legitimately have {@code NULL} here.
     * <p>
     * Uniqueness among the non-null values is enforced by the filtered unique
     * index {@code idx_customer_profiles_identity_subject} in the V1 migration.
     * A plain {@code unique = true} is deliberately NOT used: databases that
     * treat NULLs as equal in a unique index (SQL Server, and H2 in MSSQL mode)
     * would then allow only a single unlinked profile.
     */
    @Column(length = 255)
    private String identityProviderSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OnboardingStatus onboardingStatus = OnboardingStatus.REGISTERED;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.onboardingStatus == null) {
            this.onboardingStatus = OnboardingStatus.REGISTERED;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
