package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.LoanApplicationResponse;
import com.bank.digital.lending.model.dto.ManagerDecisionRequest;
import com.bank.digital.lending.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans/applications")
@Tag(name = "Human Review Webhook Callback", description = "Callback receiver for Azure Logic Apps manager approvals and underwriting decisions")
@CrossOrigin(origins = "*")
public class WebhookApprovalController {

    private final LoanApplicationService applicationService;

    public WebhookApprovalController(LoanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // Duplicate manager-callback endpoint removed to resolve ambiguous mapping
}
