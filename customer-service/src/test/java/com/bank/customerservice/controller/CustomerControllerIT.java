package com.bank.customerservice.controller;

import com.azure.core.models.CloudEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Event Grid client is mocked out; these tests exercise the HTTP + persistence
    // layers without making real network calls.
    @MockBean
    private EventGridPublisherClient<CloudEvent> eventGridPublisherClient;

    @Test
    @WithMockUser(authorities = {"ROLE_EMPLOYEE"})
    void registerCustomer_returns201() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane", "Doe", "jane.doe+it@example.com", "+15551234567",
                "123 Main St", null, "Springfield", "IL", "62701", "US");

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane.doe+it@example.com"))
                .andExpect(jsonPath("$.onboardingStatus").value("REGISTERED"));
    }

    @Test
    void registerCustomer_withoutIdentity_returns403() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane", "Doe", "jane.unauth@example.com", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CUSTOMER"})
    void registerCustomer_asCustomer_returns403() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane", "Doe", "jane.customer@example.com", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_EMPLOYEE"})
    void registerCustomer_invalidEmail_returns400() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane", "Doe", "not-an-email", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_EMPLOYEE"})
    void getById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/customers/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
