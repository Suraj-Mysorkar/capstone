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

    @Transient
    private String fullName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "DOB")
    private LocalDate dob;

    @Column(name = "National_ID", length = 50)
    private String nationalId;

    @Column(name = "phone_number", length = 20)
    private String mobileNumber;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Transient
    private String address;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "identity_provider_subject", length = 255)
    private String identityProviderSubject;

    @Column(name = "Employment_Details", length = 255)
    private String employmentDetails;

    @Column(name = "Income_Details", precision = 18, scale = 2)
    private BigDecimal incomeDetails;

    @Column(name = "onboarding_status", length = 30)
    private String onboardingStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private String loginId;

    @Transient
    private String loginPassword;

    public Customer() {
        this.createdAt = LocalDateTime.now();
        this.onboardingStatus = "APPROVED";
    }

    public Customer(String fullName, String email, String mobileNumber, BigDecimal incomeDetails, String employmentDetails) {
        this();
        this.setFullName(fullName != null ? fullName : "Applicant");
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
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String fn = firstName != null ? firstName : "";
        String ln = lastName != null ? lastName : "";
        String combined = (fn + " " + ln).trim();
        return combined.isEmpty() ? "Customer" : combined;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            this.firstName = parts[0];
            this.lastName = parts.length > 1 ? parts[1] : "";
        }
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

    public String getPhoneNumber() {
        return mobileNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.mobileNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        if (address != null && !address.isBlank()) {
            return address;
        }
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isBlank()) sb.append(addressLine1);
        if (addressLine2 != null && !addressLine2.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append(addressLine2);
        if (city != null && !city.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append(city);
        if (state != null && !state.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append(state);
        if (postalCode != null && !postalCode.isBlank()) sb.append(sb.length() > 0 ? " - " : "").append(postalCode);
        return sb.toString();
    }

    public void setAddress(String address) {
        this.address = address;
        if (this.addressLine1 == null && address != null) {
            this.addressLine1 = address;
        }
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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
