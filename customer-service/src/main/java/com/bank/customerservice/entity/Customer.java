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
 * Customer profile, persisted in Azure SQL Database.
 * Maps to the "Profiles &amp; Loans" store shared with the Loan Service.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_email", columnList = "email", unique = true),
        @Index(name = "idx_customers_status", columnList = "onboardingStatus")
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

    /** External subject identifier from Microsoft Entra ID (JWT `sub` claim). */
    @Column(length = 255, unique = true)
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
