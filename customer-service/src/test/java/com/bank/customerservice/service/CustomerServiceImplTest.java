package com.bank.customerservice.service;

import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerResponse;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.OnboardingStatus;
import com.bank.customerservice.event.EventPublisher;
import com.bank.customerservice.exception.DuplicateResourceException;
import com.bank.customerservice.exception.InvalidStatusTransitionException;
import com.bank.customerservice.exception.ResourceNotFoundException;
import com.bank.customerservice.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EventPublisher eventPublisher;

    private final OnboardingStatusTransitionValidator transitionValidator = new OnboardingStatusTransitionValidator();

    @InjectMocks
    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerRepository, eventPublisher, transitionValidator);
    }

    @Test
    void register_createsCustomerAndPublishesEvent() {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane", "Doe", "jane.doe@example.com", "+15551234567",
                "123 Main St", null, "Springfield", "IL", "62701", "US");

        when(customerRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CustomerResponse response = customerService.register(request);

        assertThat(response.email()).isEqualTo("jane.doe@example.com");
        assertThat(response.onboardingStatus()).isEqualTo(OnboardingStatus.REGISTERED);
        verify(eventPublisher, times(1)).publishCustomerRegistered(any());
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane", "Doe", "jane.doe@example.com", null, null, null, null, null, null, null);

        when(customerRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateOnboardingStatus_allowsLegalTransition() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(id)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .onboardingStatus(OnboardingStatus.REGISTERED)
                .build();

        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.updateOnboardingStatus(
                id, new OnboardingStatusUpdateRequest(OnboardingStatus.DOCUMENTS_PENDING, "docs requested"));

        assertThat(response.onboardingStatus()).isEqualTo(OnboardingStatus.DOCUMENTS_PENDING);
        verify(eventPublisher, times(1)).publishCustomerStatusChanged(any());
    }

    @Test
    void updateOnboardingStatus_rejectsIllegalTransition() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(id)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .onboardingStatus(OnboardingStatus.REGISTERED)
                .build();

        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.updateOnboardingStatus(
                id, new OnboardingStatusUpdateRequest(OnboardingStatus.ONBOARDING_COMPLETE, null)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verifyNoInteractions(eventPublisher);
    }
}
