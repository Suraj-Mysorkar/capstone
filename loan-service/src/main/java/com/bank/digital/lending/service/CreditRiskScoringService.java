package com.bank.digital.lending.service;

import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CreditRiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(CreditRiskScoringService.class);

    public record RiskAssessmentResult(
            int riskScore,
            BigDecimal dtiRatio,
            LoanStatus initialStatus,
            String assessmentReason
    ) {}

    public RiskAssessmentResult evaluateApplication(LoanApplication application) {
        BigDecimal monthlyIncome = application.getMonthlyIncome();
        BigDecimal existingLiabilities = application.getExistingLiabilities() != null ?
                application.getExistingLiabilities() : BigDecimal.ZERO;
        BigDecimal newEMI = application.getCalculatedEMI();
        BigDecimal loanAmount = application.getLoanAmount();
        EmploymentType employmentType = application.getEmploymentType();

        // 1. Calculate DTI Ratio: ((Existing Liabilities + New EMI) / Monthly Income) * 100
        BigDecimal totalMonthlyObligations = existingLiabilities.add(newEMI);
        BigDecimal dtiRatio = totalMonthlyObligations
                .multiply(BigDecimal.valueOf(100))
                .divide(monthlyIncome, 2, RoundingMode.HALF_UP);

        // 2. Compute Risk Score Points
        int dtiPoints;
        if (dtiRatio.compareTo(BigDecimal.valueOf(25)) <= 0) {
            dtiPoints = 5;
        } else if (dtiRatio.compareTo(BigDecimal.valueOf(40)) <= 0) {
            dtiPoints = 18;
        } else if (dtiRatio.compareTo(BigDecimal.valueOf(55)) <= 0) {
            dtiPoints = 42;
        } else {
            dtiPoints = 75;
        }

        int employmentPoints;
        switch (employmentType) {
            case SALARIED -> employmentPoints = 5;
            case SELF_EMPLOYED -> employmentPoints = 15;
            case BUSINESS -> employmentPoints = 20;
            case STUDENT -> employmentPoints = 25;
            default -> employmentPoints = 15;
        }

        // Loan-to-Annual-Income multiple
        BigDecimal annualIncome = monthlyIncome.multiply(BigDecimal.valueOf(12));
        BigDecimal loanMultiple = loanAmount.divide(annualIncome, 2, RoundingMode.HALF_UP);
        int multiplePoints;
        if (loanMultiple.compareTo(BigDecimal.valueOf(2.5)) <= 0) {
            multiplePoints = 5;
        } else if (loanMultiple.compareTo(BigDecimal.valueOf(4.5)) <= 0) {
            multiplePoints = 15;
        } else {
            multiplePoints = 25;
        }

        int totalRiskScore = Math.min(95, Math.max(5, dtiPoints + employmentPoints + multiplePoints));

        // Decision Classification
        LoanStatus initialStatus;
        String reason;

        if (totalRiskScore <= 30) {
            initialStatus = LoanStatus.APPROVED;
            reason = String.format("Auto-Approved by Credit Engine (Risk Score: %d/100, DTI: %s%%)", totalRiskScore, dtiRatio);
        } else if (totalRiskScore >= 70) {
            initialStatus = LoanStatus.REJECTED;
            reason = String.format("Auto-Rejected by Credit Engine: High debt burden or leverage (Risk Score: %d/100, DTI: %s%%)", totalRiskScore, dtiRatio);
        } else {
            initialStatus = LoanStatus.MANUAL_REVIEW_REQUIRED;
            reason = String.format("Escalated for Underwriter Human Review: Moderate risk profile (Risk Score: %d/100, DTI: %s%%)", totalRiskScore, dtiRatio);
        }

        log.info("[CREDIT ASSESSMENT ENGINE] Evaluated App: {} | DTI: {}% | Score: {}/100 -> Status: {}",
                application.getApplicationId(), dtiRatio, totalRiskScore, initialStatus);

        return new RiskAssessmentResult(totalRiskScore, dtiRatio, initialStatus, reason);
    }
}
