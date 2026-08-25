package com.bank.digital.lending.model.event;

import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanCompletedEventData(
    String applicationId,
    String customerId,
    String customerName,
    String customerEmail,
    LoanType loanType,
    BigDecimal loanAmount,
    Integer tenureMonths,
    BigDecimal interestRate,
    BigDecimal calculatedEMI,
    LoanStatus finalStatus,
    Integer riskScore,
    BigDecimal dtiRatio,
    String reviewedBy,
    String decisionRemarks,
    LocalDateTime completedAt
) {}
