package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.EmploymentType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record LoanApplicationRequest(
    @NotBlank(message = "Customer ID is required")
    String customerId,

    @NotBlank(message = "Customer Name is required")
    String customerName,

    @NotBlank(message = "Customer Email is required")
    @Email(message = "Invalid email address format")
    String customerEmail,

    @NotBlank(message = "Customer Phone is required")
    String customerPhone,

    @NotNull(message = "Monthly Income is required")
    @DecimalMin(value = "1000.00", message = "Monthly income must be at least 1000.00")
    BigDecimal monthlyIncome,

    BigDecimal existingLiabilities,

    @NotNull(message = "Employment Type is required")
    EmploymentType employmentType,

    @NotBlank(message = "Scheme ID is required")
    String schemeId,

    @NotNull(message = "Loan Amount is required")
    @DecimalMin(value = "1000.00", message = "Loan amount must be at least 1000.00")
    BigDecimal loanAmount,

    @NotNull(message = "Tenure in months is required")
    @Min(value = 1, message = "Tenure must be at least 1 month")
    Integer tenureMonths,

    List<String> documentIds
) {}
