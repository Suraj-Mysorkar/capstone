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
        if (saved.getEmail() == null || saved.getEmail().isBlank()) return;
        String name = (saved.getFirstName() + " " + (saved.getLastName() != null ? saved.getLastName() : "")).trim();
        String subject = "Welcome to Digital Banking - Registration Successful";
        String body = String.format(
                "<html><body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #333333; line-height: 1.6;\">"
                + "<p>Dear %s,</p>"
                + "<p>Welcome to Digital Banking! We are pleased to confirm that your customer profile has been successfully registered.</p>"
                + "<h3>Account Information</h3>"
                + "<table style=\"border-collapse: collapse; width: 100%%; max-width: 600px; margin-bottom: 16px;\">"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Customer Name</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Registered Email</td><td style=\"padding: 8px; border: 1px solid #ddd;\">%s</td></tr>"
                + "<tr><td style=\"padding: 8px; border: 1px solid #ddd; font-weight: bold;\">Account Status</td><td style=\"padding: 8px; border: 1px solid #ddd;\">ACTIVE</td></tr>"
                + "</table>"
                + "<p>You can now securely access the Digital Banking customer portal to calculate EMIs, apply for loans, and upload required documents.</p>"
                + "<p>Kind regards,<br><strong>Digital Banking Customer Experience Team</strong></p>"
                + "</body></html>",
                name,
                name,
                saved.getEmail()
        );

        String logicAppUrl = "https://prod-17.southindia.logic.azure.com:443/workflows/a4b29c1d5e814824900b41a17fa24844/triggers/When_a_HTTP_request_is_received/paths/invoke?api-version=2016-10-01&sp=%2Ftriggers%2FWhen_a_HTTP_request_is_received%2Frun&sv=1.0&sig=F--JabvW3Uwr-JsZU76HgaWWTcekahkC6HBwTEImtys";
        try {
            String payload = String.format("{\"emailTo\":\"%s\",\"emailSubject\":\"%s\",\"emailBody\":\"%s\"}",
                    saved.getEmail().replace("\"", "\\\""),
                    subject.replace("\"", "\\\""),
                    body.replace("\"", "\\\"").replace("\n", "").replace("\r", ""));

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(logicAppUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            log.info("[CUSTOMER-SERVICE] ✅ Welcome email sent to {} - Logic App status: {}", saved.getEmail(), resp.statusCode());
        } catch (Exception ex) {
            log.warn("[CUSTOMER-SERVICE] Welcome email dispatch note: {}", ex.getMessage());
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
