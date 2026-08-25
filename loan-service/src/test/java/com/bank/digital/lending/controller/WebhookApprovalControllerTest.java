package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.LoanApplicationResponse;
import com.bank.digital.lending.model.dto.ManagerDecisionRequest;
import com.bank.digital.lending.model.enums.ApprovalDecision;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.service.LoanApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookApprovalController.class)
class WebhookApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoanApplicationService applicationService;

    private LoanApplicationResponse buildApprovedResponse(String appId, String manager) {
        return new LoanApplicationResponse(
                appId,
                "CUST-5001",
                "Elena Rostova",
                "elena@example.com",
                "+14155557788",
                new BigDecimal("75000.00"),
                new BigDecimal("15000.00"),
                EmploymentType.SELF_EMPLOYED,
                "SCHEME-PL-01",
                "Prime Personal Loan",
                LoanType.PERSONAL_LOAN,
                new BigDecimal("400000.00"),
                24,
                new BigDecimal("11.50"),
                new BigDecimal("18736.13"),
                LoanStatus.APPROVED,
                48,
                new BigDecimal("44.98"),
                "ORCH-" + appId,
                manager,
                "Approved by Operations Manager: Income verified.",
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of()
        );
    }

    @Test
    @DisplayName("Webhook: POST /{id}/manager-callback returns 200 OK on valid APPROVE decision")
    void testManagerCallback_Approve_ReturnsOk() throws Exception {
        String appId = "APP-REVIEW-101";
        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "Income verified.",
                "underwriter.sarah@bank.com"
        );
        when(applicationService.processManagerDecision(eq(appId), any()))
                .thenReturn(buildApprovedResponse(appId, "underwriter.sarah@bank.com"));

        mockMvc.perform(post("/api/v1/loans/applications/" + appId + "/manager-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(appId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.assignedManager").value("underwriter.sarah@bank.com"));
    }

    @Test
    @DisplayName("Webhook: POST /{id}/manager-callback returns 400 Bad Request when fields missing")
    void testManagerCallback_ValidationError_ReturnsBadRequest() throws Exception {
        String invalidJson = "{\"decision\":\"APPROVE\"}"; // missing remarks and managerId

        mockMvc.perform(post("/api/v1/loans/applications/APP-123/manager-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Webhook: POST /{id}/manager-callback returns 409 when application is already finalized")
    void testManagerCallback_AlreadyProcessed_ReturnsConflict() throws Exception {
        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.APPROVE,
                "Repeat attempt",
                "manager@bank.com"
        );
        when(applicationService.processManagerDecision(eq("APP-DONE"), any()))
                .thenThrow(new IllegalStateException("Application is not in MANUAL_REVIEW_REQUIRED status. Current status: APPROVED"));

        mockMvc.perform(post("/api/v1/loans/applications/APP-DONE/manager-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("State Conflict"));
    }

    @Test
    @DisplayName("Webhook: POST /{id}/manager-callback returns 404 when application not found")
    void testManagerCallback_NotFound_Returns404() throws Exception {
        ManagerDecisionRequest request = new ManagerDecisionRequest(
                ApprovalDecision.REJECT,
                "Not found test",
                "manager@bank.com"
        );
        when(applicationService.processManagerDecision(eq("APP-UNKNOWN"), any()))
                .thenThrow(new IllegalArgumentException("Loan application not found with ID: APP-UNKNOWN"));

        mockMvc.perform(post("/api/v1/loans/applications/APP-UNKNOWN/manager-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Loan application not found with ID: APP-UNKNOWN"));
    }
}
