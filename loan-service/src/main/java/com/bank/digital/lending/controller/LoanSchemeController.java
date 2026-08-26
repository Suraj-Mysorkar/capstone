package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.LoanSchemeDTO;
import com.bank.digital.lending.service.LoanSchemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans/schemes")
@Tag(name = "Loan Schemes Catalog", description = "Endpoints for discovering active loan products and terms")
@CrossOrigin(origins = "*")
public class LoanSchemeController {

    private final LoanSchemeService schemeService;

    public LoanSchemeController(LoanSchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @GetMapping
    @Operation(summary = "List all active loan schemes", description = "Retrieves active Personal, Home, Vehicle, and Education loan products")
    public ResponseEntity<List<LoanSchemeDTO>> getActiveSchemes() {
        return ResponseEntity.ok(schemeService.getActiveSchemes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get loan scheme details by ID", description = "Retrieves specific loan limits and interest rate for a scheme")
    public ResponseEntity<LoanSchemeDTO> getSchemeById(@PathVariable("id") String id) {
        return schemeService.getSchemeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
