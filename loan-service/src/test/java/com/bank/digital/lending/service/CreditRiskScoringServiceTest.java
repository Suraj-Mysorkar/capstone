package com.bank.digital.lending.service;

import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreditRiskScoringServiceTest {

    private CreditRiskScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new CreditRiskScoringService();
    }

    @Test
    @DisplayName("Low Risk Applicant -> Auto APPROVED (Score <= 30)")
    void testEvaluateApplication_LowRisk() {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("APP-TEST-LOW");
        app.setMonthlyIncome(new BigDecimal("150000.00"));
        app.setExistingLiabilities(new BigDecimal("5000.00"));
        app.setEmploymentType(EmploymentType.SALARIED);
        app.setLoanAmount(new BigDecimal("200000.00"));
        app.setCalculatedEMI(new BigDecimal("18000.00"));

        CreditRiskScoringService.RiskAssessmentResult result = scoringService.evaluateApplication(app);

        assertNotNull(result);
        assertTrue(result.riskScore() <= 30, "Expected Risk Score <= 30, but got " + result.riskScore());
        assertEquals(LoanStatus.APPROVED, result.initialStatus());
        assertTrue(result.assessmentReason().contains("Auto-Approved"));
    }

    @Test
    @DisplayName("High Risk Applicant -> Auto REJECTED (Score >= 70)")
    void testEvaluateApplication_HighRisk() {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("APP-TEST-HIGH");
        app.setMonthlyIncome(new BigDecimal("30000.00"));
        app.setExistingLiabilities(new BigDecimal("15000.00"));
        app.setEmploymentType(EmploymentType.STUDENT);
        app.setLoanAmount(new BigDecimal("800000.00"));
        app.setCalculatedEMI(new BigDecimal("16000.00")); // Total debt 31k > 30k income -> DTI > 100%

        CreditRiskScoringService.RiskAssessmentResult result = scoringService.evaluateApplication(app);

        assertNotNull(result);
        assertTrue(result.riskScore() >= 70, "Expected Risk Score >= 70, but got " + result.riskScore());
        assertEquals(LoanStatus.REJECTED, result.initialStatus());
        assertTrue(result.assessmentReason().contains("Auto-Rejected"));
    }

    @Test
    @DisplayName("Medium Risk Applicant -> Escalated to MANUAL_REVIEW_REQUIRED (Score 31-69)")
    void testEvaluateApplication_MediumRisk() {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("APP-TEST-MED");
        app.setMonthlyIncome(new BigDecimal("80000.00"));
        app.setExistingLiabilities(new BigDecimal("15000.00"));
        app.setEmploymentType(EmploymentType.SELF_EMPLOYED);
        app.setLoanAmount(new BigDecimal("1200000.00"));
        app.setCalculatedEMI(new BigDecimal("22000.00")); // Total debt 37k / 80k = 46.25% DTI

        CreditRiskScoringService.RiskAssessmentResult result = scoringService.evaluateApplication(app);

        assertNotNull(result);
        assertTrue(result.riskScore() > 30 && result.riskScore() < 70,
                "Expected Risk Score between 31 and 69, but got " + result.riskScore());
        assertEquals(LoanStatus.MANUAL_REVIEW_REQUIRED, result.initialStatus());
        assertTrue(result.assessmentReason().contains("Underwriter Human Review"));
    }
}
