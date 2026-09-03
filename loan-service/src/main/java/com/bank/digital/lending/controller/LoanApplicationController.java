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

    @GetMapping("/customers")
    @Operation(summary = "List all registered customers",
               description = "Retrieves customer records from Customers table")
    public ResponseEntity<List<CustomerResponse>> listCustomers() {
        return ResponseEntity.ok(applicationService.listCustomers());
    }

    @PostMapping("/customers")
    @Operation(summary = "Register / upsert a customer (no loan application)",
               description = "Creates or updates a row in the shared Customers table, keyed by email. "
                       + "Used by the customer self-service portal so a newly-registered customer is "
                       + "immediately visible to the loan officer console.")
    public ResponseEntity<CustomerResponse> registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.registerOrUpdateCustomer(request));
    }

    // Endpoint to receive document upload notification and advance workflow
    @PostMapping("/applications/{id}/document-uploaded")
    @Operation(summary = "Notify service that documents have been uploaded",
               description = "Sets the documentProvided flag, links documents, and advances workflow (Auto-Approves if low risk, or routes to Manager if moderate risk)")
    public ResponseEntity<LoanApplicationResponse> documentUploaded(@PathVariable("id") String applicationId,
                                                                    @Valid @RequestBody com.bank.digital.lending.model.dto.DocumentUploadedRequest request) {
        LoanApplicationResponse response = applicationService.handleDocumentUploaded(applicationId, request);
        return ResponseEntity.ok(response);
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

    @PostMapping("/applications/{id}/decision")
    @Operation(summary = "Process Manager Decision", description = "Records manual approval or rejection by credit manager")
    public ResponseEntity<LoanApplicationResponse> processManagerDecision(@PathVariable("id") String applicationId,
                                                                         @Valid @RequestBody ManagerDecisionRequest request) {
        return ResponseEntity.ok(applicationService.processManagerDecision(applicationId, request));
    }
@PostMapping("/applications/{id}/manager-callback")
@Operation(summary = "Manager decision webhook callback",
          description = "Endpoint for Logic App / tests to submit manager decision after manual review")
public ResponseEntity<Void> managerCallback(@PathVariable("id") String applicationId,
                                            @Valid @RequestBody ManagerDecisionRequest request) {
    applicationService.processManagerDecision(applicationId, request);
    return ResponseEntity.ok().build();
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
