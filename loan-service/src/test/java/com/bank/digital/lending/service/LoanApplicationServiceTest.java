package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.LoanApplicationRequest;
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
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

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
    }

    @Test
    @DisplayName("Submit Loan Application -> Auto Approved & Event Bus Triggered")
    void testApplyForLoan_AutoApproved() {
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-101",
                "Alice Johnson",
                "alice@example.com",
                "+1234567890",
                new BigDecimal("120000.00"),
                new BigDecimal("5000.00"),
                EmploymentType.SALARIED,
                "SCHEME-PL-01",
                new BigDecimal("200000.00"),
                24,
                Collections.emptyList()
        );

        when(schemeRepository.findById("SCHEME-PL-01")).thenReturn(Optional.of(testScheme));
        when(emiCalculatorProxy.computeMonthlyEMI(any(), any(), anyInt())).thenReturn(new BigDecimal("9368.52"));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> {
            LoanApplication app = invocation.getArgument(0);
            return app;
        });

        doAnswer(invocation -> {
            LoanApplication app = invocation.getArgument(0);
            app.setStatus(LoanStatus.APPROVED);
            app.setRiskScore(22);
            app.setDecisionRemarks("Auto-Approved by Credit Engine");
            return null;
        }).when(durableOrchestrator).runOrchestrationWorkflow(any(), any());

        LoanApplicationResponse response = applicationService.applyForLoan(request);

        assertNotNull(response);
        assertEquals("Alice Johnson", response.customerName());
        assertEquals(LoanStatus.APPROVED, response.status());
        assertEquals(22, response.riskScore());
        verify(eventBusPublisher, times(1)).publishLoanCompletedEvent(any());
    }

    @Test
    @DisplayName("Process Manager Approval Webhook -> Final Status APPROVED & Event Bus Triggered")
    void testProcessManagerDecision_Approve() {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("APP-TEST-99");
        app.setCustomerName("Bob Smith");
        app.setCustomerEmail("bob@example.com");
        app.setLoanAmount(new BigDecimal("500000.00"));
        app.setTenureMonths(36);
        app.setScheme(testScheme);
        app.setLoanType(LoanType.PERSONAL_LOAN);
        app.setCalculatedEMI(new BigDecimal("16500.00"));
        app.setMonthlyIncome(new BigDecimal("60000.00"));
        app.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);
        app.setRiskScore(45);

        when(applicationRepository.findById("APP-TEST-99")).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenReturn(app);

        doAnswer(invocation -> {
            LoanApplication target = invocation.getArgument(0);
            target.setStatus(LoanStatus.APPROVED);
            target.setAssignedManager("manager.dave@bank.com");
            target.setDecisionRemarks("Approved by Operations Manager: High net worth verified");
            return null;
        }).when(durableOrchestrator).processManagerApprovalEvent(any(), any(), any(), any());

        ManagerDecisionRequest decisionReq = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "High net worth verified",
                "manager.dave@bank.com"
        );

        LoanApplicationResponse response = applicationService.processManagerDecision("APP-TEST-99", decisionReq);

        assertNotNull(response);
        assertEquals(LoanStatus.APPROVED, response.status());
        assertEquals("manager.dave@bank.com", response.assignedManager());
        verify(eventBusPublisher, times(1)).publishLoanCompletedEvent(any());
    }
}
