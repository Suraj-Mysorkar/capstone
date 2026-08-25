package com.bank.digital.lending.orchestration;

import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.ApprovalDecision;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.service.CreditRiskScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanDurableOrchestratorTest {

    @Mock
    private CreditRiskScoringService creditRiskScoringService;

    @Mock
    private LogicAppApprovalConnector logicAppConnector;

    @InjectMocks
    private LoanDurableOrchestrator orchestrator;

    private LoanScheme standardScheme;
    private LoanApplication sampleApp;

    @BeforeEach
    void setUp() {
        standardScheme = new LoanScheme(
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

        sampleApp = new LoanApplication();
        sampleApp.setApplicationId("APP-ORCH-001");
        sampleApp.setCustomerId("CUST-101");
        sampleApp.setCustomerName("Alice Johnson");
        sampleApp.setCustomerEmail("alice@example.com");
        sampleApp.setMonthlyIncome(new BigDecimal("120000.00"));
        sampleApp.setExistingLiabilities(new BigDecimal("5000.00"));
        sampleApp.setEmploymentType(EmploymentType.SALARIED);
        sampleApp.setScheme(standardScheme);
        sampleApp.setLoanType(LoanType.PERSONAL_LOAN);
        sampleApp.setLoanAmount(new BigDecimal("200000.00"));
        sampleApp.setTenureMonths(24);
        sampleApp.setInterestRate(new BigDecimal("11.50"));
        sampleApp.setCalculatedEMI(new BigDecimal("9368.52"));
        sampleApp.setStatus(LoanStatus.SUBMITTED);
    }

    @Test
    @DisplayName("Durable Orchestration: Validation failure when loan amount exceeds scheme maximum")
    void testValidationFailure_AmountExceedsMax() {
        sampleApp.setLoanAmount(new BigDecimal("5000000.00")); // Max is 1,000,000

        orchestrator.runOrchestrationWorkflow(sampleApp, "/callback");

        assertEquals(LoanStatus.REJECTED, sampleApp.getStatus());
        assertTrue(sampleApp.getDecisionRemarks().contains("validation failed"));
        assertEquals("ORCH-APP-ORCH-001", sampleApp.getOrchestrationInstanceId());
        verifyNoInteractions(creditRiskScoringService);
        verifyNoInteractions(logicAppConnector);
    }

    @Test
    @DisplayName("Durable Orchestration: Validation failure when tenure is outside scheme limits")
    void testValidationFailure_TenureInvalid() {
        sampleApp.setTenureMonths(120); // Max is 60

        orchestrator.runOrchestrationWorkflow(sampleApp, "/callback");

        assertEquals(LoanStatus.REJECTED, sampleApp.getStatus());
        assertTrue(sampleApp.getDecisionRemarks().contains("validation failed"));
        verifyNoInteractions(creditRiskScoringService);
    }

    @Test
    @DisplayName("Durable Orchestration: Low risk path leads to Auto-APPROVED")
    void testWorkflow_LowRisk_AutoApproved() {
        CreditRiskScoringService.RiskAssessmentResult lowRiskResult = new CreditRiskScoringService.RiskAssessmentResult(
                18,
                new BigDecimal("12.00"),
                LoanStatus.APPROVED,
                "Auto-Approved by Credit Engine (Risk Score: 18/100, DTI: 12.00%)"
        );
        when(creditRiskScoringService.evaluateApplication(sampleApp)).thenReturn(lowRiskResult);

        orchestrator.runOrchestrationWorkflow(sampleApp, "/callback");

        assertEquals(LoanStatus.APPROVED, sampleApp.getStatus());
        assertEquals(18, sampleApp.getRiskScore());
        assertEquals(new BigDecimal("12.00"), sampleApp.getDtiRatio());
        assertTrue(sampleApp.getDecisionRemarks().contains("Auto-Approved"));
        verifyNoInteractions(logicAppConnector);
    }

    @Test
    @DisplayName("Durable Orchestration: High risk path leads to Auto-REJECTED")
    void testWorkflow_HighRisk_AutoRejected() {
        CreditRiskScoringService.RiskAssessmentResult highRiskResult = new CreditRiskScoringService.RiskAssessmentResult(
                85,
                new BigDecimal("95.00"),
                LoanStatus.REJECTED,
                "Auto-Rejected by Credit Engine: High debt burden or leverage (Risk Score: 85/100, DTI: 95.00%)"
        );
        when(creditRiskScoringService.evaluateApplication(sampleApp)).thenReturn(highRiskResult);

        orchestrator.runOrchestrationWorkflow(sampleApp, "/callback");

        assertEquals(LoanStatus.REJECTED, sampleApp.getStatus());
        assertEquals(85, sampleApp.getRiskScore());
        assertEquals(new BigDecimal("95.00"), sampleApp.getDtiRatio());
        assertTrue(sampleApp.getDecisionRemarks().contains("Auto-Rejected"));
        verifyNoInteractions(logicAppConnector);
    }

    @Test
    @DisplayName("Durable Orchestration: Medium risk triggers Human Review via Logic App")
    void testWorkflow_MediumRisk_TriggersLogicApp() {
        CreditRiskScoringService.RiskAssessmentResult medRiskResult = new CreditRiskScoringService.RiskAssessmentResult(
                45,
                new BigDecimal("42.50"),
                LoanStatus.MANUAL_REVIEW_REQUIRED,
                "Escalated for Underwriter Human Review: Moderate risk profile (Risk Score: 45/100, DTI: 42.50%)"
        );
        when(creditRiskScoringService.evaluateApplication(sampleApp)).thenReturn(medRiskResult);

        orchestrator.runOrchestrationWorkflow(sampleApp, "/api/v1/loans/applications/APP-ORCH-001/manager-callback");

        assertEquals(LoanStatus.MANUAL_REVIEW_REQUIRED, sampleApp.getStatus());
        assertEquals(45, sampleApp.getRiskScore());
        assertEquals(new BigDecimal("42.50"), sampleApp.getDtiRatio());
        verify(logicAppConnector, times(1)).triggerHumanReviewWorkflow(
                sampleApp, "/api/v1/loans/applications/APP-ORCH-001/manager-callback"
        );
    }

    @Test
    @DisplayName("Durable Orchestration: Resumes with Manager APPROVE event")
    void testProcessManagerApprovalEvent_Approve() {
        sampleApp.setOrchestrationInstanceId("ORCH-APP-ORCH-001");
        sampleApp.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);

        orchestrator.processManagerApprovalEvent(
                sampleApp,
                ApprovalDecision.APPROVE,
                "Income verification confirmed via tax portal.",
                "manager.john@bank.com"
        );

        assertEquals(LoanStatus.APPROVED, sampleApp.getStatus());
        assertEquals("manager.john@bank.com", sampleApp.getAssignedManager());
        assertTrue(sampleApp.getDecisionRemarks().contains("Approved by Operations Manager: Income verification confirmed"));
    }

    @Test
    @DisplayName("Durable Orchestration: Resumes with Manager REJECT event")
    void testProcessManagerApprovalEvent_Reject() {
        sampleApp.setOrchestrationInstanceId("ORCH-APP-ORCH-001");
        sampleApp.setStatus(LoanStatus.MANUAL_REVIEW_REQUIRED);

        orchestrator.processManagerApprovalEvent(
                sampleApp,
                ApprovalDecision.REJECT,
                "Fraud alert triggered on submitted bank statement.",
                "manager.john@bank.com"
        );

        assertEquals(LoanStatus.REJECTED, sampleApp.getStatus());
        assertEquals("manager.john@bank.com", sampleApp.getAssignedManager());
        assertTrue(sampleApp.getDecisionRemarks().contains("Rejected by Operations Manager: Fraud alert"));
    }
}
