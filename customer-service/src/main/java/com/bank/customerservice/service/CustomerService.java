package com.bank.customerservice.service;

import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerResponse;
import com.bank.customerservice.dto.CustomerUpdateRequest;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse register(CustomerRegistrationRequest request);

    CustomerResponse getById(UUID id);

    CustomerResponse getByEmail(String email);

    Page<CustomerResponse> list(OnboardingStatus status, Pageable pageable);

    CustomerResponse update(UUID id, CustomerUpdateRequest request);

    CustomerResponse updateOnboardingStatus(UUID id, OnboardingStatusUpdateRequest request);

    void delete(UUID id);

    void dispatchWelcomeNotification(String email, String name, String loginId);
}
