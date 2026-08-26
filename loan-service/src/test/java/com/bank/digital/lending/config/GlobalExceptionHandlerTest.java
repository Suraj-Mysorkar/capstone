package com.bank.digital.lending.config;

import com.bank.digital.lending.controller.LoanApplicationController;
import com.bank.digital.lending.model.dto.EMICalculationRequest;
import com.bank.digital.lending.service.EMICalculatorProxyService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanApplicationController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoanApplicationService applicationService;

    @MockBean
    private EMICalculatorProxyService emiCalculatorProxy;

    @Test
    @DisplayName("Error Handler: 400 Bad Request for missing required fields (Validation)")
    void testValidationError_ReturnsBadRequest() throws Exception {
        // POST /apply with empty body triggers @Valid failure
        mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    @DisplayName("Error Handler: 404 Not Found for non-existent application")
    void testIllegalArgumentException_ReturnsNotFound() throws Exception {
        when(applicationService.getApplicationById("APP-NOTFOUND"))
                .thenThrow(new IllegalArgumentException("Loan application not found with ID: APP-NOTFOUND"));

        mockMvc.perform(get("/api/v1/loans/applications/APP-NOTFOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Resource Not Found / Invalid Argument"))
                .andExpect(jsonPath("$.message").value("Loan application not found with ID: APP-NOTFOUND"));
    }

    @Test
    @DisplayName("Error Handler: 409 Conflict for invalid state transition")
    void testIllegalStateException_ReturnsConflict() throws Exception {
        when(applicationService.applyForLoan(any()))
                .thenThrow(new IllegalStateException("Application is not in MANUAL_REVIEW_REQUIRED status. Current status: APPROVED"));

        // Build a valid enough apply request to pass validation
        String requestBody = """
                {
                  "customerId": "CUST-1",
                  "customerName": "Test User",
                  "customerEmail": "test@example.com",
                  "customerPhone": "+1234567890",
                  "monthlyIncome": 50000.00,
                  "employmentType": "SALARIED",
                  "schemeId": "SCHEME-PL-01",
                  "loanAmount": 100000.00,
                  "tenureMonths": 12
                }
                """;

        mockMvc.perform(post("/api/v1/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("State Conflict"));
    }

    @Test
    @DisplayName("Error Handler: 500 Internal Server Error for unexpected exceptions")
    void testGenericException_ReturnsInternalServerError() throws Exception {
        EMICalculationRequest request = new EMICalculationRequest(
                new BigDecimal("300000.00"),
                36,
                new BigDecimal("10.00"),
                null
        );

        when(emiCalculatorProxy.calculateEMI(any()))
                .thenThrow(new RuntimeException("Unexpected internal error during EMI calculation."));

        mockMvc.perform(post("/api/v1/loans/calculate-emi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }
}
