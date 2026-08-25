package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.LoanApplicationResponse;
import com.bank.digital.lending.model.dto.ManagerDecisionRequest;
import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.ApprovalDecision;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.orchestration.LoanDurableOrchestrator;
import com.bank.digital.lending.repository.LoanApplicationRepository;
import com.bank.digital.lending.repository.LoanAuditLogRepository;
import com.bank.digital.lending.repository.LoanSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HumanInterventionApprovalTest {

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private LoanSchemeRepository schemeRepository;

    @Mock
    private LoanAuditLogRepository auditLogRepository;

    @Mock
    private EMICalculatorProxyService emiCalculatorProxy;

    @Mock
    private DocumentStorageProxyService documentStorageProxy;

    @Mock
    private LoanDurableOrchestrator durableOrchestrator;

    @Mock
    private AzureEventBusPublisherService eventBusPublisher;

    @InjectMocks
    private LoanApplicationService applicationService;

    private LoanApplication pendingApp;
    private LoanScheme testScheme;

    @BeforeEach
    void setUp() {
        testScheme = new LoanScheme(
                "SCHEME-PL-01",
                LoanType.PERSONAL_LOAN,
                "Prime Personal Loan",
                new BigDecimal("10000.00"),
                new BigDecimal("1000000.00"),
                6,
                60,
                new BigDecimal("11.50"),
                true
        );

        pendingApp = new LoanApplication();
        pendingApp.setApplicationId("APP-REVIEW-101");
        pendingApp.setCustomerId("CUST-5001");
        pendingApp.setCustomerName("Elena Rostova");
        pendingApp.setCustomerEmail("elena.rostova@example.com");
        pendingApp.setCustomerPhone("+14155557788");
        pendingApp.setMonthlyIncome(new BigDecimal("75000.00"));
        pendingApp.setExistingLiabilities(new BigDecimal("15000.00"));
        pendingApp.setEmploymentType(EmploymentType.SELF_EMPLOYED);
        pendingApp.setScheme(testScheme);
        pendingApp.setLoanType(LoanType.PERSONAL_LOAN);
        pendingApp.setLoanAmount(new BigDecimal("400000.00"));
        pendingApp.setTenureMonths(24);
        pendingApp.setInterestRate(new BigDecimal("11.50"));
        pendingApp.setCalculatedEMI(new BigDecimal("18736.13"));
        pendingApp.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);
        pendingApp.setRiskScore(48);
        pendingApp.setDtiRatio(new BigDecimal("44.98"));
        pendingApp.setOrchestrationInstanceId("ORCH-APP-REVIEW-101");
    }

    @Test
    @DisplayName("Human Intervention: Manager APPROVES application via webhook callback")
    void testManagerApprovesApplication() {
        when(applicationRepository.findById("APP-REVIEW-101")).thenReturn(Optional.of(pendingApp));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            LoanApplication app = invocation.getArgument(0);
            ApprovalDecision decision = invocation.getArgument(1);
            String remarks = invocation.getArgument(2);
            String managerId = invocation.getArgument(3);

            app.setStatus(LoanStatus.APPROVED);
            app.setAssignedManager(managerId);
            app.setDecisionRemarks("Approved by Operations Manager: " + remarks);
            return null;
        }).when(durableOrchestrator).processManagerApprovalEvent(any(), any(), any(), any());

        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "Verified 3 years audited income tax returns and strong collateral.",
                "underwriter.sarah@bank.com"
        );

        LoanApplicationResponse response = applicationService.processManagerDecision("APP-REVIEW-101", request);

        assertNotNull(response);
        assertEquals(LoanStatus.APPROVED, response.status());
        assertEquals("underwriter.sarah@bank.com", response.assignedManager());
        assertTrue(response.decisionRemarks().contains("Verified 3 years audited income tax returns"));

        verify(durableOrchestrator, times(1)).processManagerApprovalEvent(
                eq(pendingApp), eq(ApprovalDecision.APPROVE), anyString(), eq("underwriter.sarah@bank.com")
        );
        verify(auditLogRepository, times(1)).save(any());
        verify(eventBusPublisher, times(1)).publishLoanCompletedEvent(pendingApp);
    }

    @Test
    @DisplayName("Human Intervention: Manager REJECTS application via webhook callback")
    void testManagerRejectsApplication() {
        when(applicationRepository.findById("APP-REVIEW-101")).thenReturn(Optional.of(pendingApp));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            LoanApplication app = invocation.getArgument(0);
            ApprovalDecision decision = invocation.getArgument(1);
            String remarks = invocation.getArgument(2);
            String managerId = invocation.getArgument(3);

            app.setStatus(LoanStatus.REJECTED);
            app.setAssignedManager(managerId);
            app.setDecisionRemarks("Rejected by Operations Manager: " + remarks);
            return null;
        }).when(durableOrchestrator).processManagerApprovalEvent(any(), any(), any(), any());

        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.REJECT,
                "Insufficient bank statement transaction history for self-employed status.",
                "underwriter.sarah@bank.com"
        );

        LoanApplicationResponse response = applicationService.processManagerDecision("APP-REVIEW-101", request);

        assertNotNull(response);
        assertEquals(LoanStatus.REJECTED, response.status());
        assertEquals("underwriter.sarah@bank.com", response.assignedManager());
        assertTrue(response.decisionRemarks().contains("Rejected by Operations Manager"));

        verify(durableOrchestrator, times(1)).processManagerApprovalEvent(
                eq(pendingApp), eq(ApprovalDecision.REJECT), anyString(), eq("underwriter.sarah@bank.com")
        );
        verify(auditLogRepository, times(1)).save(any());
        verify(eventBusPublisher, times(1)).publishLoanCompletedEvent(pendingApp);
    }

    @Test
    @DisplayName("Human Intervention: Guardrail fails when application is not in MANUAL_REVIEW_REQUIRED status")
    void testManagerDecision_ThrowsExceptionWhenNotPendingReview() {
        pendingApp.setStatus(LoanStatus.APPROVED);
        when(applicationRepository.findById("APP-REVIEW-101")).thenReturn(Optional.of(pendingApp));

        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "Already processed",
                "manager@bank.com"
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                applicationService.processManagerDecision("APP-REVIEW-101", request)
        );

        assertTrue(exception.getMessage().contains("Application is not in MANUAL_REVIEW_REQUIRED status"));
        verifyNoInteractions(durableOrchestrator);
        verifyNoInteractions(eventBusPublisher);
    }

    @Test
    @DisplayName("Human Intervention: Fails when application ID is not found")
    void testManagerDecision_ApplicationNotFound() {
        when(applicationRepository.findById("APP-INVALID")).thenReturn(Optional.empty());

        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "Remarks",
                "manager@bank.com"
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                applicationService.processManagerDecision("APP-INVALID", request)
        );

        assertTrue(exception.getMessage().contains("Loan application not found with ID"));
    }
}
