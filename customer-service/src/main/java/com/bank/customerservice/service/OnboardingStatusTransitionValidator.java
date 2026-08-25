package com.bank.customerservice.service;

import com.bank.customerservice.entity.OnboardingStatus;
import com.bank.customerservice.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.bank.customerservice.entity.OnboardingStatus.*;

/**
 * Enforces the legal onboarding status state machine so that, e.g., a
 * customer cannot move directly from REGISTERED to ONBOARDING_COMPLETE
 * without passing through document/KYC review.
 */
@Component
public class OnboardingStatusTransitionValidator {

    private static final Map<OnboardingStatus, Set<OnboardingStatus>> ALLOWED = new EnumMap<>(OnboardingStatus.class);

    static {
        ALLOWED.put(REGISTERED, EnumSet.of(DOCUMENTS_PENDING, SUSPENDED));
        ALLOWED.put(DOCUMENTS_PENDING, EnumSet.of(DOCUMENTS_SUBMITTED, SUSPENDED));
        ALLOWED.put(DOCUMENTS_SUBMITTED, EnumSet.of(KYC_IN_REVIEW, DOCUMENTS_PENDING, SUSPENDED));
        ALLOWED.put(KYC_IN_REVIEW, EnumSet.of(KYC_APPROVED, KYC_REJECTED, SUSPENDED));
        ALLOWED.put(KYC_APPROVED, EnumSet.of(ONBOARDING_COMPLETE, SUSPENDED));
        ALLOWED.put(KYC_REJECTED, EnumSet.of(DOCUMENTS_PENDING, SUSPENDED));
        ALLOWED.put(ONBOARDING_COMPLETE, EnumSet.of(SUSPENDED));
        ALLOWED.put(SUSPENDED, EnumSet.of(DOCUMENTS_PENDING, KYC_IN_REVIEW));
    }

    public void validate(OnboardingStatus current, OnboardingStatus next) {
        if (current == next) {
            throw new InvalidStatusTransitionException(
                    "Customer is already in status " + current);
        }
        Set<OnboardingStatus> allowedNext = ALLOWED.getOrDefault(current, Set.of());
        if (!allowedNext.contains(next)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from " + current + " to " + next +
                            ". Allowed next states: " + allowedNext);
        }
    }
}
