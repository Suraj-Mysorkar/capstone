package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.LoanType;
import java.math.BigDecimal;

public record LoanSchemeDTO(
    String schemeId,
    LoanType loanType,
    String schemeName,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    Integer minTenureMonths,
    Integer maxTenureMonths,
    BigDecimal baseInterestRate,
    Boolean isActive
) {}
