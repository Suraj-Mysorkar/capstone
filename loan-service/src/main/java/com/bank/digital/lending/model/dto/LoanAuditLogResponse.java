package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.LoanStatus;
import java.time.LocalDateTime;

public record LoanAuditLogResponse(
    Long logId,
    String applicationId,
    LoanStatus previousStatus,
    LoanStatus newStatus,
    String changedBy,
    String comments,
    LocalDateTime timestamp
) {}
