package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LoanApplicationResponse(
    String applicationId,
    String customerId,
    String customerName,
    String customerEmail,
    String customerPhone,
    BigDecimal monthlyIncome,
    BigDecimal existingLiabilities,
    EmploymentType employmentType,
    String schemeId,
    String schemeName,
    LoanType loanType,
    BigDecimal loanAmount,
    Integer tenureMonths,
    BigDecimal interestRate,
    BigDecimal calculatedEMI,
    LoanStatus status,
    Integer riskScore,
    BigDecimal dtiRatio,
    String orchestrationInstanceId,
    String assignedManager,
    String decisionRemarks,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<DocumentUploadResponse> documents
) {}
