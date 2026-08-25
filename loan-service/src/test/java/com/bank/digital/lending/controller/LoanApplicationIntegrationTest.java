package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.EMICalculationRequest;
import com.bank.digital.lending.model.dto.LoanApplicationRequest;
import com.bank.digital.lending.model.dto.ManagerDecisionRequest;
import com.bank.digital.lending.model.enums.ApprovalDecision;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoanApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API: GET /api/v1/loans/schemes returns seeded schemes")
    void testGetSchemes() throws Exception {
        mockMvc.perform(get("/api/v1/loans/schemes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].schemeId", notNullValue()))
                .andExpect(jsonPath("$[0].baseInterestRate", notNullValue()));
    }

    @Test
    @DisplayName("API: POST /api/v1/loans/calculate-emi returns accurate financial math")
    void testCalculateEMI() throws Exception {
        EMICalculationRequest request = new EMICalculationRequest(
                new BigDecimal("300000.00"),
                36,
                new BigDecimal("10.00"),
                null
        );

        mockMvc.perform(post("/api/v1/loans/calculate-emi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalAmount", is(300000.00)))
                .andExpect(jsonPath("$.tenureMonths", is(36)))
                .andExpect(jsonPath("$.monthlyEMI", is(9680.16)))
                .andExpect(jsonPath("$.amortizationSchedule", hasSize(36)));
    }

    @Test
    @DisplayName("End-to-End Flow: Low Risk Application -> Auto APPROVED")
    void testApply_AutoApproved() throws Exception {
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-2001",
                "David Miller",
                "david.miller@example.com",
                "+14155552671",
                new BigDecimal("180000.00"),
                new BigDecimal("3000.00"),
                EmploymentType.SALARIED,
                "SCHEME-PL-01",
                new BigDecimal("150000.00"),
                24,
                Collections.emptyList()
        );

        MvcResult result = mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId", startsWith("APP-")))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.riskScore", lessThanOrEqualTo(30)))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String applicationId = jsonNode.get("applicationId").asText();

        // Verify tracking endpoint
        mockMvc.perform(get("/api/v1/loans/applications/" + applicationId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId", is(applicationId)))
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    @DisplayName("End-to-End Flow: Medium Risk Application -> MANUAL_REVIEW -> Manager Webhook Approval")
    void testApply_ManualReviewAndManagerApproval() throws Exception {
        // 1. Submit Medium Risk Application (DTI ~ 48%)
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-3001",
                "Elena Rostova",
                "elena.rostova@example.com",
                "+14155559821",
                new BigDecimal("75000.00"),
                new BigDecimal("15000.00"),
                EmploymentType.SELF_EMPLOYED,
                "SCHEME-PL-01",
                new BigDecimal("400000.00"),
                24,
                Collections.emptyList()
        );

        MvcResult applyResult = mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("MANUAL_REVIEW_REQUIRED")))
                .andExpect(jsonPath("$.riskScore", allOf(greaterThan(30), lessThan(70))))
                .andReturn();

        String applicationId = objectMapper.readTree(applyResult.getResponse().getContentAsString())
                .get("applicationId").asText();

        // 2. Simulate Manager Review via Logic App Webhook Callback
        ManagerDecisionRequest decisionReq = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "Credit history verified via bureau and 3 years tax filings reviewed.",
                "senior.underwriter@bank.com"
        );

        mockMvc.perform(post("/api/v1/loans/applications/" + applicationId + "/manager-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId", is(applicationId)))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.assignedManager", is("senior.underwriter@bank.com")))
                .andExpect(jsonPath("$.decisionRemarks", containsString("Approved by Operations Manager")));

        // 3. Verify final state in query endpoint
        mockMvc.perform(get("/api/v1/loans/applications/" + applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    @DisplayName("End-to-End Flow: High Risk Application -> Auto REJECTED (Score >= 70)")
    void testApply_AutoRejected_HighRisk() throws Exception {
        // Bob Overleveraged: 30k income, 25k liabilities, 800k loan, STUDENT employment -> DTI > 100% -> Score ~95
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-4001",
                "Bob Overleveraged",
                "bob.debt@example.com",
                "+14155559988",
                new BigDecimal("30000.00"),
                new BigDecimal("25000.00"),
                EmploymentType.STUDENT,
                "SCHEME-PL-01",
                new BigDecimal("800000.00"),
                60,
                Collections.emptyList()
        );

        mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId", startsWith("APP-")))
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.riskScore", greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$.decisionRemarks", containsString("Auto-Rejected")));
    }

    @Test
    @DisplayName("End-to-End Flow: Scheme validation failure -> REJECTED (Amount exceeds scheme max)")
    void testApply_SchemeLimitViolation_Rejected() throws Exception {
        // SCHEME-PL-01 max is 1,000,000. Requesting 5,000,000 -> validation failure -> REJECTED
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-4002",
                "Max Borrower",
                "max@example.com",
                "+14155554444",
                new BigDecimal("500000.00"),
                new BigDecimal("0.00"),
                EmploymentType.SALARIED,
                "SCHEME-PL-01",
                new BigDecimal("5000000.00"),  // Way above scheme max
                60,
                Collections.emptyList()
        );

        mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.decisionRemarks", containsString("validation failed")));
    }

    @Test
    @DisplayName("API: GET /api/v1/loans/applications?status=APPROVED filters correctly")
    void testListApplications_FilterByStatus() throws Exception {
        // First submit a low-risk application so there is at least one APPROVED entry
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-5001",
                "Priya Sinha",
                "priya.sinha@example.com",
                "+14155551111",
                new BigDecimal("200000.00"),
                new BigDecimal("2000.00"),
                EmploymentType.SALARIED,
                "SCHEME-PL-01",
                new BigDecimal("100000.00"),
                12,
                Collections.emptyList()
        );

        mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        // Now query filtered list
        mockMvc.perform(get("/api/v1/loans/applications")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].status", is("APPROVED")));
    }

    @Test
    @DisplayName("API: GET /api/v1/loans/applications/{id}/audit-logs returns ordered audit trail")
    void testGetAuditLogs_ForApprovedApplication() throws Exception {
        // Submit auto-approved application
        LoanApplicationRequest request = new LoanApplicationRequest(
                "CUST-6001",
                "Carlos Mendez",
                "carlos.mendez@example.com",
                "+14155556666",
                new BigDecimal("250000.00"),
                new BigDecimal("1000.00"),
                EmploymentType.SALARIED,
                "SCHEME-PL-01",
                new BigDecimal("150000.00"),
                24,
                Collections.emptyList()
        );

        MvcResult result = mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andReturn();

        String applicationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("applicationId").asText();

        // Query audit log endpoint
        mockMvc.perform(get("/api/v1/loans/applications/" + applicationId + "/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))  // SUBMITTED entry + APPROVED entry
                .andExpect(jsonPath("$[0].applicationId", is(applicationId)))
                .andExpect(jsonPath("$[0].newStatus", is("SUBMITTED")));
    }

    @Test
    @DisplayName("API: GET /api/v1/loans/applications/{id} returns 404 for unknown ID")
    void testGetApplicationById_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/loans/applications/APP-DOESNOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API: GET /api/v1/loans/schemes/{id} returns single scheme details")
    void testGetSchemeById() throws Exception {
        mockMvc.perform(get("/api/v1/loans/schemes/SCHEME-PL-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemeId", is("SCHEME-PL-01")))
                .andExpect(jsonPath("$.schemeName", is("Prime Flexi Personal Loan")))
                .andExpect(jsonPath("$.baseInterestRate", is(11.50)));
    }
}

