package com.bank.digital.lending.model.dto;

import java.math.BigDecimal;

public record AmortizationScheduleEntry(
    int monthNumber,
    BigDecimal beginningBalance,
    BigDecimal emiAmount,
    BigDecimal principalPaid,
    BigDecimal interestPaid,
    BigDecimal endingBalance
) {}
