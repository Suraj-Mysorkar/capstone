package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.*;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.service.EMICalculatorProxyService;
import com.bank.digital.lending.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loan Applications", description = "Endpoints for applying, tracking, and calculating retail loans")
@CrossOrigin(origins = "*")
public class LoanApplicationController {

    private final LoanApplicationService applicationService;
    private final EMICalculatorProxyService emiCalculatorProxy;

    public LoanApplicationController(LoanApplicationService applicationService,
                                     EMICalculatorProxyService emiCalculatorProxy) {
        this.applicationService = applicationService;
        this.emiCalculatorProxy = emiCalculatorProxy;
    }

    @PostMapping("/calculate-emi")
    @Operation(summary = "Calculate Loan EMI & Amortization Schedule",
               description = "Invokes Azure Function (with mock fallback) to calculate monthly EMI and breakdown")
    public ResponseEntity<EMICalculationResponse> calculateEMI(@Valid @RequestBody EMICalculationRequest request) {
        return ResponseEntity.ok(emiCalculatorProxy.calculateEMI(request));
    }

    @PostMapping("/apply")
    @Operation(summary = "Submit a new Loan Application",
               description = "Submits applicant information and triggers the Azure Durable Function stateful workflow")
    public ResponseEntity<LoanApplicationResponse> applyForLoan(@Valid @RequestBody LoanApplicationRequest request) {
        LoanApplicationResponse response = applicationService.applyForLoan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/applications")
    @Operation(summary = "List Loan Applications",
               description = "Retrieves loan applications, with optional filter by status (e.g. MANUAL_REVIEW_REQUIRED, APPROVED, REJECTED)")
    public ResponseEntity<List<LoanApplicationResponse>> listApplications(
            @RequestParam(name = "status", required = false) LoanStatus status) {
        return ResponseEntity.ok(applicationService.listApplications(status));
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Get Loan Application Details",
               description = "Retrieves full application details, risk score, documents, and audit logs")
    public ResponseEntity<LoanApplicationResponse> getApplicationById(@PathVariable("id") String id) {
        return applicationService.getApplicationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/applications/{id}/status")
    @Operation(summary = "Track Application Status",
               description = "Lightweight tracking query for customer dashboard / progress timeline")
    public ResponseEntity<LoanStatusResponse> getApplicationStatus(@PathVariable("id") String id) {
        return applicationService.getApplicationStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/applications/{id}/audit-logs")
    @Operation(summary = "Get Application Audit Trail",
               description = "Retrieves complete state transition history, actor details, timestamps, and underwriting remarks")
    public ResponseEntity<List<LoanAuditLogResponse>> getAuditLogs(@PathVariable("id") String id) {
        return ResponseEntity.ok(applicationService.getAuditLogs(id));
    }
}
