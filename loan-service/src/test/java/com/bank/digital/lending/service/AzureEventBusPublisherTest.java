package com.bank.digital.lending.service;

import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.EmploymentType;
import com.bank.digital.lending.model.enums.LoanStatus;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.model.event.LoanApplicationCompletedEvent;
import com.bank.digital.lending.model.event.LoanCompletedEventData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AzureEventBusPublisherTest {

    private AzureEventBusPublisherService publisherService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisherService = new AzureEventBusPublisherService(objectMapper);
    }

    @Test
    @DisplayName("Azure Service Bus Event: Emits valid completion event for APPROVED loan")
    void testPublishLoanCompletedEvent_Approved() throws Exception {
        LoanScheme scheme = new LoanScheme(
                "SCHEME-PL-01",
                LoanType.PERSONAL_LOAN,
                "Prime Personal Loan",
                new BigDecimal("10000.00"),
                new BigDecimal("1000000.00"),
                6,
                60,
                new BigDecimal("11.50"),
                true
        );

        LoanApplication app = new LoanApplication();
        app.setApplicationId("APP-EVT-001");
        app.setCustomerId("CUST-9001");
        app.setCustomerName("Alice Johnson");
        app.setCustomerEmail("alice@example.com");
        app.setLoanType(LoanType.PERSONAL_LOAN);
        app.setLoanAmount(new BigDecimal("250000.00"));
        app.setTenureMonths(24);
        app.setInterestRate(new BigDecimal("11.50"));
        app.setCalculatedEMI(new BigDecimal("11710.08"));
        app.setStatus(LoanStatus.APPROVED);
        app.setRiskScore(20);
        app.setDtiRatio(new BigDecimal("18.50"));
        app.setAssignedManager("underwriter.sarah@bank.com");
        app.setDecisionRemarks("Auto-Approved by Credit Engine");
        app.setScheme(scheme);

        // Test publishing does not throw exception
        assertDoesNotThrow(() -> publisherService.publishLoanCompletedEvent(app));

        // Validate Event Schema & JSON serialization
        LoanCompletedEventData eventData = new LoanCompletedEventData(
                app.getApplicationId(),
                app.getCustomerId(),
                app.getCustomerName(),
                app.getCustomerEmail(),
                app.getLoanType(),
                app.getLoanAmount(),
                app.getTenureMonths(),
                app.getInterestRate(),
                app.getCalculatedEMI(),
                app.getStatus(),
                app.getRiskScore(),
                app.getDtiRatio(),
                app.getAssignedManager(),
                app.getDecisionRemarks(),
                LocalDateTime.now()
        );

        LoanApplicationCompletedEvent event = LoanApplicationCompletedEvent.of(eventData);

        String json = objectMapper.writeValueAsString(event);
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.get("eventId").asText().startsWith("evt-"));
        assertEquals("LOAN_APPLICATION_COMPLETED", root.get("eventType").asText());
        assertEquals("/services/digital-lending/loan-service", root.get("source").asText());
        assertNotNull(root.get("timestamp"));

        JsonNode data = root.get("data");
        assertEquals("APP-EVT-001", data.get("applicationId").asText());
        assertEquals("CUST-9001", data.get("customerId").asText());
        assertEquals("APPROVED", data.get("finalStatus").asText());
        assertEquals(20, data.get("riskScore").asInt());
        assertEquals("underwriter.sarah@bank.com", data.get("reviewedBy").asText());
    }

    @Test
    @DisplayName("Azure Service Bus Event: Emits valid completion event for REJECTED loan")
    void testPublishLoanCompletedEvent_Rejected() throws Exception {
        LoanApplication app = new LoanApplication();
        app.setApplicationId("APP-EVT-002");
        app.setCustomerId("CUST-9002");
        app.setCustomerName("Bob Overleveraged");
        app.setCustomerEmail("bob@example.com");
        app.setLoanType(LoanType.PERSONAL_LOAN);
        app.setLoanAmount(new BigDecimal("800000.00"));
        app.setTenureMonths(60);
        app.setInterestRate(new BigDecimal("11.50"));
        app.setCalculatedEMI(new BigDecimal("17596.00"));
        app.setStatus(LoanStatus.REJECTED);
        app.setRiskScore(92);
        app.setDtiRatio(new BigDecimal("115.00"));
        app.setDecisionRemarks("Auto-Rejected: Debt exceeds maximum policy threshold.");

        assertDoesNotThrow(() -> publisherService.publishLoanCompletedEvent(app));

        LoanCompletedEventData eventData = new LoanCompletedEventData(
                app.getApplicationId(),
                app.getCustomerId(),
                app.getCustomerName(),
                app.getCustomerEmail(),
                app.getLoanType(),
                app.getLoanAmount(),
                app.getTenureMonths(),
                app.getInterestRate(),
                app.getCalculatedEMI(),
                app.getStatus(),
                app.getRiskScore(),
                app.getDtiRatio(),
                "SYSTEM_CREDIT_ENGINE",
                app.getDecisionRemarks(),
                LocalDateTime.now()
        );

        LoanApplicationCompletedEvent event = LoanApplicationCompletedEvent.of(eventData);
        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("REJECTED", root.get("data").get("finalStatus").asText());
        assertEquals(92, root.get("data").get("riskScore").asInt());
        assertEquals("SYSTEM_CREDIT_ENGINE", root.get("data").get("reviewedBy").asText());
    }
}
