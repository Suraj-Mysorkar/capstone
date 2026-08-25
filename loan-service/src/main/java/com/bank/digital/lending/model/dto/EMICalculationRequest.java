package com.bank.digital.lending.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record EMICalculationRequest(
    @NotNull(message = "Principal loan amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum principal amount is 1000.00")
    BigDecimal loanAmount,

    @NotNull(message = "Tenure in months is required")
    @Min(value = 1, message = "Tenure must be at least 1 month")
    Integer tenureMonths,

    BigDecimal interestRate,

    String schemeId
) {}
