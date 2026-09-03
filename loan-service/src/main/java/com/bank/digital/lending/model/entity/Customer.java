package com.bank.digital.lending.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Customer_ID")
    private Long customerId;

    @Column(name = "Full_Name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "DOB")
    private LocalDate dob;

    @Column(name = "National_ID", length = 50)
    private String nationalId;

    @Column(name = "Mobile_Number", nullable = false, length = 20)
    private String mobileNumber;

    @Column(name = "Email", nullable = false, length = 255)
    private String email;

    @Column(name = "Address", length = 255)
    private String address;

    @Column(name = "Employment_Details", length = 255)
    private String employmentDetails;

    @Column(name = "Income_Details", precision = 18, scale = 2)
    private BigDecimal incomeDetails;

    @Column(name = "Onboarding_Status", length = 30)
    private String onboardingStatus;

    @Column(name = "Created_At")
    private LocalDateTime createdAt;

    @Column(name = "loginid", length = 100)
    private String loginId;

    @Column(name = "loginpassword", length = 255)
    private String loginPassword;

    public Customer() {
        this.createdAt = LocalDateTime.now();
        this.onboardingStatus = "APPROVED";
    }

    public Customer(String fullName, String email, String mobileNumber, BigDecimal incomeDetails, String employmentDetails) {
        this();
        this.fullName = fullName != null ? fullName : "Applicant";
        this.email = email != null ? email : "customer@bank.com";
        this.mobileNumber = (mobileNumber != null && !mobileNumber.isBlank()) ? mobileNumber : "N/A";
        this.incomeDetails = incomeDetails;
        this.employmentDetails = employmentDetails != null ? employmentDetails : "SALARIED";
        this.dob = LocalDate.of(1990, 1, 1);
        this.nationalId = "NAT-" + (System.currentTimeMillis() % 1000000);
        this.loginId = "u" + Long.toString(System.currentTimeMillis() % 1_000_000_000L, 36);
        this.loginPassword = "Password@123";
    }

    // Getters and Setters

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmploymentDetails() {
        return employmentDetails;
    }

    public void setEmploymentDetails(String employmentDetails) {
        this.employmentDetails = employmentDetails;
    }

    public BigDecimal getIncomeDetails() {
        return incomeDetails;
    }

    public void setIncomeDetails(BigDecimal incomeDetails) {
        this.incomeDetails = incomeDetails;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getLoginPassword() {
        return loginPassword;
    }

    public void setLoginPassword(String loginPassword) {
        this.loginPassword = loginPassword;
    }
}
