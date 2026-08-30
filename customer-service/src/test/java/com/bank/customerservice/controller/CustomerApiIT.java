package com.bank.customerservice.controller;

import com.azure.core.models.CloudEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerUpdateRequest;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.OnboardingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end exercise of every REST endpoint exposed by the service, run through
 * MockMvc against the in-memory H2 database. The Entra ID JwtDecoder and the
 * Event Grid client are mocked so no network calls are made.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    EventGridPublisherClient<CloudEvent> eventGridPublisherClient;

    @MockBean
    JwtDecoder jwtDecoder;

    private static final String WRITE = "SCOPE_customers.write";
    private static final String READ = "SCOPE_customers.read";
    private static final String ADMIN = "ROLE_customer_admin";

    private CustomerRegistrationRequest sample(String email) {
        return new CustomerRegistrationRequest(
                "Jane", "Doe", email, "+15551234567",
                "123 Main St", null, "Springfield", "IL", "62701", "US");
    }

    /** Registers a customer and returns its generated id. */
    private String register(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/customers")
                        .with(user("writer").authorities(() -> WRITE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(sample(email))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith("/api/customers/")))
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    // ── unauthenticated / open endpoints ─────────────────────────────────────

    @Test
    void ping_isPublic() throws Exception {
        mockMvc.perform(get("/api/customers/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("customer-service"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocs_arePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void actuatorHealth_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── POST /api/customers ─────────────────────────────────────────────────

    @Test
    void register_created() throws Exception {
        register("api.register@example.com");
    }

    @Test
    void register_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/customers").contentType("application/json")
                        .content(json.writeValueAsString(sample("noauth@example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_forbiddenWithReadOnlyScope() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(user("reader").authorities(() -> READ))
                        .contentType("application/json")
                        .content(json.writeValueAsString(sample("readonly@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_validationError() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(user("writer").authorities(() -> WRITE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(sample("not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_duplicateEmailConflict() throws Exception {
        String email = "dupe@example.com";
        register(email);
        mockMvc.perform(post("/api/customers")
                        .with(user("writer").authorities(() -> WRITE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(sample(email))))
                .andExpect(status().isConflict());
    }

    // ── GET /api/customers/{id} and ?email= ─────────────────────────────────

    @Test
    void getById_andByEmail() throws Exception {
        String id = register("lookup@example.com");

        mockMvc.perform(get("/api/customers/{id}", id).with(user("r").authorities(() -> READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lookup@example.com"))
                .andExpect(jsonPath("$.onboardingStatus").value("REGISTERED"));

        mockMvc.perform(get("/api/customers").param("email", "lookup@example.com")
                        .with(user("r").authorities(() -> READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID())
                        .with(user("r").authorities(() -> READ)))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/customers (list + filter, paged) ───────────────────────────

    @Test
    void list_pagedAndFilteredByStatus() throws Exception {
        register("list1@example.com");

        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "5")
                        .with(user("r").authorities(() -> READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable").exists());

        mockMvc.perform(get("/api/customers").param("status", "REGISTERED")
                        .with(user("r").authorities(() -> READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].onboardingStatus").value("REGISTERED"));
    }

    // ── PATCH /api/customers/{id} ──────────────────────────────────────────

    @Test
    void updateProfile() throws Exception {
        String id = register("update@example.com");
        CustomerUpdateRequest patch = new CustomerUpdateRequest(
                "Janet", null, null, null, null, null, "Chicago", null, null, null);

        mockMvc.perform(patch("/api/customers/{id}", id)
                        .with(user("w").authorities(() -> WRITE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.city").value("Chicago"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    // ── PATCH /api/customers/{id}/onboarding-status ─────────────────────────

    @Test
    void onboardingStatus_legalTransitionSucceeds() throws Exception {
        String id = register("status-ok@example.com");
        var body = new OnboardingStatusUpdateRequest(OnboardingStatus.DOCUMENTS_PENDING, "docs requested");

        mockMvc.perform(patch("/api/customers/{id}/onboarding-status", id)
                        .with(user("w").authorities(() -> WRITE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("DOCUMENTS_PENDING"));
    }

    @Test
    void onboardingStatus_illegalTransitionUnprocessable() throws Exception {
        String id = register("status-bad@example.com");
        var body = new OnboardingStatusUpdateRequest(OnboardingStatus.ONBOARDING_COMPLETE, null);

        mockMvc.perform(patch("/api/customers/{id}/onboarding-status", id)
                        .with(user("w").authorities(() -> WRITE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── DELETE /api/customers/{id} ─────────────────────────────────────────

    @Test
    void delete_requiresAdminRole() throws Exception {
        String id = register("delete@example.com");

        mockMvc.perform(delete("/api/customers/{id}", id).with(user("w").authorities(() -> WRITE)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/customers/{id}", id).with(user("a").authorities(() -> ADMIN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/customers/{id}", id).with(user("r").authorities(() -> READ)))
                .andExpect(status().isNotFound());
    }
}
