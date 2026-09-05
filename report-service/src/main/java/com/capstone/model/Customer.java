package com.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "id")
    private java.util.UUID customerId = java.util.UUID.randomUUID();

    @Column(name = "Full_Name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "DOB", nullable = false)
    private LocalDate dob;

    @Column(name = "National_ID", length = 50, unique = true, nullable = false)
    private String nationalId;

    @Column(name = "Mobile_Number", length = 15, nullable = false)
    private String mobileNumber;

    @Column(name = "Email", length = 100, nullable = false)
    private String email;

    @Lob
    @Column(name = "Address", columnDefinition = "CLOB")
    private String address;

    @Lob
    @Column(name = "Employment_Details", columnDefinition = "CLOB")
    private String employmentDetails;

    @Column(name = "Income_Details", precision = 18, scale = 2)
    private BigDecimal incomeDetails;

    @Column(name = "Onboarding_Status", length = 20)
    private String onboardingStatus = "PENDING";

    @Column(name = "Created_At", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<LoanApplication> loanApplications;

    // Standard Getters & Setters
}
