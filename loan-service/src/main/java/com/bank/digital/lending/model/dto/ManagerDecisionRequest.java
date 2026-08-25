package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.ApprovalDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManagerDecisionRequest(
    @NotNull(message = "Decision (APPROVE or REJECT) is required")
    ApprovalDecision decision,

    @NotBlank(message = "Manager decision remarks are mandatory")
    String remarks,

    @NotBlank(message = "Manager ID or Email is required")
    String managerId
) {}
