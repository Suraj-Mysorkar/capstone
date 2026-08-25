package com.bank.customerservice.entity;

/**
 * Lifecycle states for customer onboarding, as tracked by the Customer Service
 * and surfaced to the Loan Service / Reporting Dashboard.
 */
public enum OnboardingStatus {
    REGISTERED,
    DOCUMENTS_PENDING,
    DOCUMENTS_SUBMITTED,
    KYC_IN_REVIEW,
    KYC_APPROVED,
    KYC_REJECTED,
    ONBOARDING_COMPLETE,
    SUSPENDED
}
