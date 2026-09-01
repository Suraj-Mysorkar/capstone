package com.capstone.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.dto.MonthlyMetricDto;
import com.capstone.dto.OperationsSummaryDto;
import com.capstone.service.ReportService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reporting & Analytics", description = "Endpoints for executive trends and internal operations dashboards")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PreAuthorize("hasRole('ROLE_EMPLOYEE')")
    @GetMapping("/operations/summary")
    public ResponseEntity<OperationsSummaryDto> getOperationsSummary(@RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
        OperationsSummaryDto summary = reportService.getOperationsSummary();
        return ResponseEntity.ok(summary);
    }

    @PreAuthorize("hasRole('ROLE_EMPLOYEE')")
    @GetMapping("/executives/metrics")
    public ResponseEntity<List<MonthlyMetricDto>> getExecutiveMetrics(@RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
        List<MonthlyMetricDto> metrics = reportService.getExecutiveMetrics();
        return ResponseEntity.ok(metrics);
    }
}