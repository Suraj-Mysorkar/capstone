package com.bank.customerservice.service;

import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerResponse;
import com.bank.customerservice.dto.CustomerUpdateRequest;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.OnboardingStatus;
import com.bank.customerservice.event.CustomerRegisteredEvent;
import com.bank.customerservice.event.CustomerStatusChangedEvent;
import com.bank.customerservice.event.EventPublisher;
import com.bank.customerservice.exception.DuplicateResourceException;
import com.bank.customerservice.exception.ResourceNotFoundException;
import com.bank.customerservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final EventPublisher eventPublisher;
    private final OnboardingStatusTransitionValidator transitionValidator;

    @Override
    @Transactional
    public CustomerResponse register(CustomerRegistrationRequest request) {
        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A customer with email " + request.email() + " already exists");
        }

        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .addressLine1(request.addressLine1())
                .addressLine2(request.addressLine2())
                .city(request.city())
                .state(request.state())
                .postalCode(request.postalCode())
                .countryCode(request.countryCode())
                .onboardingStatus(OnboardingStatus.REGISTERED)
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Registered new customer id={}", saved.getId());

        eventPublisher.publishCustomerRegistered(
                CustomerRegisteredEvent.of(saved.getId(), saved.getEmail(), saved.getFirstName() + " " +  saved.getLastName(), "Register"));

        // Dispatch Welcome Email directly to Notification Service
        dispatchWelcomeNotification(saved);

        return CustomerResponse.from(saved);
    }

    private void dispatchWelcomeNotification(Customer saved) {
        try {
            org.springframework.web.client.RestClient restClient = org.springframework.web.client.RestClient.create();
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "eventType", "CUSTOMER_REGISTERED",
                    "data", java.util.Map.of(
                            "customerName", saved.getFirstName() + " " + saved.getLastName(),
                            "email", saved.getEmail(),
                            "status", "REGISTERED"
                    )
            );
            restClient.post()
                    .uri("https://team6-notification-service.azurewebsites.net/api/notify")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[CUSTOMER-SERVICE] ✅ Direct welcome email triggered for: {}", saved.getEmail());
        } catch (Exception e) {
            log.warn("[CUSTOMER-SERVICE] Direct welcome notification fallback (Event Grid active): {}", e.getMessage());
        }
    }

    @Override
    public CustomerResponse getById(UUID id) {
        return CustomerResponse.from(findOrThrow(id));
    }

    @Override
    public CustomerResponse getByEmail(String email) {
        Customer customer = customerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No customer found with email " + email));
        return CustomerResponse.from(customer);
    }

    @Override
    public Page<CustomerResponse> list(OnboardingStatus status, Pageable pageable) {
        Page<Customer> page = (status != null)
                ? customerRepository.findByOnboardingStatus(status, pageable)
                : customerRepository.findAll(pageable);
        return page.map(CustomerResponse::from);
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerUpdateRequest request) {
        Customer customer = findOrThrow(id);

        if (StringUtils.hasText(request.firstName())) customer.setFirstName(request.firstName());
        if (StringUtils.hasText(request.lastName())) customer.setLastName(request.lastName());
        if (StringUtils.hasText(request.email()) && !request.email().equalsIgnoreCase(customer.getEmail())) {
            if (customerRepository.existsByEmailIgnoreCase(request.email())) {
                throw new DuplicateResourceException("A customer with email " + request.email() + " already exists");
            }
            customer.setEmail(request.email());
        }
        if (StringUtils.hasText(request.phoneNumber())) customer.setPhoneNumber(request.phoneNumber());
        if (StringUtils.hasText(request.addressLine1())) customer.setAddressLine1(request.addressLine1());
        if (StringUtils.hasText(request.addressLine2())) customer.setAddressLine2(request.addressLine2());
        if (StringUtils.hasText(request.city())) customer.setCity(request.city());
        if (StringUtils.hasText(request.state())) customer.setState(request.state());
        if (StringUtils.hasText(request.postalCode())) customer.setPostalCode(request.postalCode());
        if (StringUtils.hasText(request.countryCode())) customer.setCountryCode(request.countryCode());

        Customer saved = customerRepository.save(customer);
        log.info("Updated profile for customer id={}", saved.getId());
        return CustomerResponse.from(saved);
    }

    @Override
    @Transactional
    public CustomerResponse updateOnboardingStatus(UUID id, OnboardingStatusUpdateRequest request) {
        Customer customer = findOrThrow(id);
        OnboardingStatus previous = customer.getOnboardingStatus();

        transitionValidator.validate(previous, request.status());
        customer.setOnboardingStatus(request.status());
        Customer saved = customerRepository.save(customer);

        log.info("Customer id={} onboarding status {} -> {}", id, previous, request.status());

        eventPublisher.publishCustomerStatusChanged(
                CustomerStatusChangedEvent.of(saved.getId(), previous, request.status(), request.reason()));

        return CustomerResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Customer customer = findOrThrow(id);
        customerRepository.delete(customer);
        log.info("Deleted customer id={}", id);
    }

    private Customer findOrThrow(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No customer found with id " + id));
    }
}
