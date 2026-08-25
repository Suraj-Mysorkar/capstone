package com.bank.digital.lending.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record EMICalculationResponse(
    BigDecimal principalAmount,
    Integer tenureMonths,
    BigDecimal annualInterestRate,
    BigDecimal monthlyEMI,
    BigDecimal totalInterestPayable,
    BigDecimal totalAmountPayable,
    List<AmortizationScheduleEntry> amortizationSchedule
) {}
