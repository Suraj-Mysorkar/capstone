package com.bank.customerservice.service;

import com.bank.customerservice.dto.AssignLoanManagerRequest;
import com.bank.customerservice.dto.LoanManagerAssignmentResponse;
import com.bank.customerservice.entity.AppUser;
import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.LoanManagerAssignment;
import com.bank.customerservice.event.EventPublisher;
import com.bank.customerservice.event.LoanManagerAssignedEvent;
import com.bank.customerservice.exception.ResourceNotFoundException;
import com.bank.customerservice.repository.AppUserRepository;
import com.bank.customerservice.repository.CustomerRepository;
import com.bank.customerservice.repository.LoanManagerAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanManagerAssignmentServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private LoanManagerAssignmentRepository assignmentRepository;
    @Mock private EventPublisher eventPublisher;

    private LoanManagerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new LoanManagerAssignmentService(
                appUserRepository, customerRepository, assignmentRepository, eventPublisher);
    }

    private AppUser manager(long id, String login, String name) {
        return AppUser.builder().userId(id).loginId(login).name(name)
                .email(login + "@bank.example.com").userRole("manager").build();
    }

    @Test
    void assign_picksLeastLoadedManager_persists_andNotifiesCustomer() {
        UUID customerId = UUID.randomUUID();
        when(assignmentRepository.findFirstByApplicationIdIgnoreCase("APP-1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(Customer.builder()
                .id(customerId).firstName("Jane").lastName("Doe").email("jane@example.com").build()));
        when(appUserRepository.findByUserRoleIgnoreCase("manager"))
                .thenReturn(List.of(manager(1, "mgr.a", "Manager A"), manager(2, "mgr.b", "Manager B")));
        when(assignmentRepository.countByManagerLogin("mgr.a")).thenReturn(3L);
        when(assignmentRepository.countByManagerLogin("mgr.b")).thenReturn(1L);
        when(assignmentRepository.save(any(LoanManagerAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanManagerAssignmentResponse res = service.assign(new AssignLoanManagerRequest(
                customerId, "APP-1", "PERSONAL_LOAN", new BigDecimal("500000"), null, null));

        assertThat(res.managerLogin()).isEqualTo("mgr.b");
        assertThat(res.customerEmail()).isEqualTo("jane@example.com");
        assertThat(res.notified()).isTrue();
        assertThat(res.message()).contains("Manager B").contains("APP-1");

        ArgumentCaptor<LoanManagerAssignedEvent> ev = ArgumentCaptor.forClass(LoanManagerAssignedEvent.class);
        verify(eventPublisher, times(1)).publishLoanManagerAssigned(ev.capture());
        assertThat(ev.getValue().customerEmail()).isEqualTo("jane@example.com");
        assertThat(ev.getValue().managerName()).isEqualTo("Manager B");
    }

    @Test
    void assign_isIdempotentPerApplicationId() {
        LoanManagerAssignment existing = LoanManagerAssignment.builder()
                .id(UUID.randomUUID()).applicationId("APP-9").managerLogin("mgr.a").managerName("Manager A")
                .customerEmail("j@example.com").assignedAt(Instant.now()).notified(true).build();
        when(assignmentRepository.findFirstByApplicationIdIgnoreCase("APP-9")).thenReturn(Optional.of(existing));

        LoanManagerAssignmentResponse res = service.assign(new AssignLoanManagerRequest(
                null, "APP-9", null, null, "j@example.com", null));

        assertThat(res.managerLogin()).isEqualTo("mgr.a");
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void assign_throwsWhenNoManagersConfigured() {
        when(assignmentRepository.findFirstByApplicationIdIgnoreCase(any())).thenReturn(Optional.empty());
        when(appUserRepository.findByUserRoleIgnoreCase("manager")).thenReturn(List.of());

        assertThatThrownBy(() -> service.assign(new AssignLoanManagerRequest(
                null, "APP-2", null, null, "k@example.com", "K")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assign_throwsWhenNoEmailResolvable() {
        UUID customerId = UUID.randomUUID();
        when(assignmentRepository.findFirstByApplicationIdIgnoreCase(any())).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(new AssignLoanManagerRequest(
                customerId, "APP-3", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
