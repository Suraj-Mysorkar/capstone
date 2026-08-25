package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.LoanStatus;
import java.time.LocalDateTime;

public record LoanStatusResponse(
    String applicationId,
    String customerName,
    LoanStatus status,
    String stageDescription,
    Integer riskScore,
    String decisionRemarks,
    LocalDateTime lastUpdated
) {}
