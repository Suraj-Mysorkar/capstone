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

    @PostMapping("/{id}/manager-callback")
    @Operation(summary = "Process Operations Manager Approval Decision (Logic App Webhook)",
               description = "Receives approval/rejection decision from Logic App actionable card, resumes Durable Function instance, and publishes completion event")
    public ResponseEntity<LoanApplicationResponse> processManagerCallback(
            @PathVariable("id") String id,
            @Valid @RequestBody ManagerDecisionRequest request) {

        LoanApplicationResponse response = applicationService.processManagerDecision(id, request);
        return ResponseEntity.ok(response);
    }
}
