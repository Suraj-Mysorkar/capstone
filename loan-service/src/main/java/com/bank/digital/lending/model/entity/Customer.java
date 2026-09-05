package com.bank.digital.lending.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "id")
    private java.util.UUID customerId = java.util.UUID.randomUUID();

    @Column(name = "Full_Name", length = 100)
    private String fullName;

    @Column(name = "firstName", length = 100)
    private String firstName;

    @Column(name = "lastName", length = 100)
    private String lastName;

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

    @Column(length = 255)
    private String identityProviderSubject;

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

    public java.util.UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(java.util.UUID customerId) {
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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getIdentityProviderSubject() {
        return identityProviderSubject;
    }

    public void setIdentityProviderSubject(String identityProviderSubject) {
        this.identityProviderSubject = identityProviderSubject;
    }
}
