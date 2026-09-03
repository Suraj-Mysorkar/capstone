package com.bank.customerservice.controller;

import com.bank.customerservice.dto.AssignLoanManagerRequest;
import com.bank.customerservice.dto.LoanManagerAssignmentResponse;
import com.bank.customerservice.service.LoanManagerAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Loan manager assignment for the lending workflow.
 * <p>
 * {@code POST} is a server-to-server integration endpoint: the loan-service
 * calls it right after a customer submits a loan application, and customer-service
 * assigns one of its loan managers and notifies the customer. {@code GET} lets
 * the portal show the customer who is handling their application.
 * <p>
 * Public (no bearer token) — same trust model as {@code /api/customers/auth/**};
 * see {@code SecurityConfig.PUBLIC_PATHS}.
 */
@RestController
@RequestMapping("/api/customers/loan-manager-assignments")
@RequiredArgsConstructor
@Tag(name = "Loan Manager Assignment", description = "Assign a loan manager to a customer on loan application and notify them")
public class LoanManagerAssignmentController {

    private final LoanManagerAssignmentService assignmentService;

    @PostMapping
    @Operation(summary = "Assign a loan manager to a customer's loan application and notify the customer")
    public ResponseEntity<LoanManagerAssignmentResponse> assign(@Valid @RequestBody AssignLoanManagerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.assign(request));
    }

    @GetMapping
    @Operation(summary = "List a customer's loan manager assignments (most recent first)")
    public ResponseEntity<List<LoanManagerAssignmentResponse>> byCustomer(@RequestParam UUID customerId) {
        return ResponseEntity.ok(assignmentService.forCustomer(customerId));
    }
}
