package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.AmortizationScheduleEntry;
import com.bank.digital.lending.model.dto.EMICalculationRequest;
import com.bank.digital.lending.model.dto.EMICalculationResponse;
import com.bank.digital.lending.model.entity.LoanScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class EMICalculatorProxyService {

    private static final Logger log = LoggerFactory.getLogger(EMICalculatorProxyService.class);

    @Value("${azure.enabled:false}")
    private boolean azureEnabled;

    @Value("${azure.functions.emi-calc.url:https://bank-lending-functions.azurewebsites.net/api/v1/calculate-emi}")
    private String emiFunctionUrl;

    private final LoanSchemeService schemeService;

    public EMICalculatorProxyService(LoanSchemeService schemeService) {
        this.schemeService = schemeService;
    }

    public EMICalculationResponse calculateEMI(EMICalculationRequest request) {
        BigDecimal principal = request.loanAmount();
        int tenureMonths = request.tenureMonths();
        BigDecimal annualInterestRate = request.interestRate();

        if (annualInterestRate == null && request.schemeId() != null) {
            annualInterestRate = schemeService.getSchemeEntityById(request.schemeId())
                    .map(LoanScheme::getBaseInterestRate)
                    .orElse(new BigDecimal("10.50"));
        } else if (annualInterestRate == null) {
            annualInterestRate = new BigDecimal("10.50");
        }

        // Mock Logger for Azure Function Call
        log.info("================================================================================");
        log.info("[MOCK AZURE FUNCTION CALL] Executing Azure Function at endpoint: {}", emiFunctionUrl);
        log.info("[MOCK AZURE FUNCTION CALL] HTTP POST /api/v1/calculate-emi");
        log.info("[MOCK AZURE FUNCTION CALL] Request Payload: { principal: {}, tenureMonths: {}, interestRate: {}% }",
                principal, tenureMonths, annualInterestRate);

        BigDecimal monthlyEMI = computeMonthlyEMI(principal, annualInterestRate, tenureMonths);
        BigDecimal totalAmountPayable = monthlyEMI.multiply(BigDecimal.valueOf(tenureMonths)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterestPayable = totalAmountPayable.subtract(principal).setScale(2, RoundingMode.HALF_UP);

        List<AmortizationScheduleEntry> schedule = generateAmortizationSchedule(principal, annualInterestRate, tenureMonths, monthlyEMI);

        log.info("[MOCK AZURE FUNCTION CALL] Execution Successful (200 OK)");
        log.info("[MOCK AZURE FUNCTION CALL] Result: Monthly EMI = {}, Total Interest = {}, Total Payable = {}",
                monthlyEMI, totalInterestPayable, totalAmountPayable);
        log.info("================================================================================");

        return new EMICalculationResponse(
                principal,
                tenureMonths,
                annualInterestRate,
                monthlyEMI,
                totalInterestPayable,
                totalAmountPayable,
                schedule
        );
    }

    public BigDecimal computeMonthlyEMI(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        // monthly interest rate r = annualRate / (12 * 100)
        double r = annualRate.doubleValue() / (12.0 * 100.0);
        double p = principal.doubleValue();
        int n = tenureMonths;

        // EMI = [P * r * (1+r)^n] / [(1+r)^n - 1]
        double factor = Math.pow(1.0 + r, n);
        double emi = (p * r * factor) / (factor - 1.0);

        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
    }

    private List<AmortizationScheduleEntry> generateAmortizationSchedule(BigDecimal principal, BigDecimal annualRate,
                                                                        int tenureMonths, BigDecimal monthlyEMI) {
        List<AmortizationScheduleEntry> entries = new ArrayList<>(tenureMonths);
        BigDecimal balance = principal;
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);

        for (int m = 1; m <= tenureMonths; m++) {
            BigDecimal interestPaid = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalPaid = monthlyEMI.subtract(interestPaid).setScale(2, RoundingMode.HALF_UP);

            BigDecimal endingBalance = balance.subtract(principalPaid).setScale(2, RoundingMode.HALF_UP);
            if (m == tenureMonths || endingBalance.compareTo(BigDecimal.ZERO) < 0) {
                endingBalance = BigDecimal.ZERO;
            }

            entries.add(new AmortizationScheduleEntry(
                    m,
                    balance,
                    monthlyEMI,
                    principalPaid,
                    interestPaid,
                    endingBalance
            ));

            balance = endingBalance;
        }

        return entries;
    }
}
