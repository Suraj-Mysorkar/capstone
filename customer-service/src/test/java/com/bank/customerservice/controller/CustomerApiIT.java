package com.bank.customerservice.controller;

import com.azure.core.models.CloudEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.bank.customerservice.dto.CustomerRegistrationRequest;
import com.bank.customerservice.dto.CustomerUpdateRequest;
import com.bank.customerservice.dto.OnboardingStatusUpdateRequest;
import com.bank.customerservice.entity.AppUser;
import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.OnboardingStatus;
import com.bank.customerservice.repository.AppUserRepository;
import com.bank.customerservice.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * MockMvc against the in-memory H2 database.
 * <p>
 * Authorization is role based (the roles Azure APIM forwards as {@code X-User-Role}):
 * {@code ROLE_CUSTOMER} / {@code ROLE_EMPLOYEE} / {@code ROLE_MANAGER}. The Event
 * Grid client is mocked so no network calls are made.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    AppUserRepository appUsers;

    @Autowired
    CustomerRepository customers;

    @MockBean
    EventGridPublisherClient<CloudEvent> eventGridPublisherClient;

    private static final String CUSTOMER = "ROLE_CUSTOMER";
    private static final String EMPLOYEE = "ROLE_EMPLOYEE";
    private static final String MANAGER = "ROLE_MANAGER";

    private CustomerRegistrationRequest sample(String email) {
        return new CustomerRegistrationRequest(
                "Jane", "Doe", email, "+15551234567",
                "123 Main St", null, "Springfield", "IL", "62701", "US");
    }

    /** Registers a customer (as bank staff) and returns its generated id. */
    private String register(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/customers")
                        .with(user("officer").authorities(() -> EMPLOYEE))
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
    void register_deniedWithoutIdentity() throws Exception {
        mockMvc.perform(post("/api/customers").contentType("application/json")
                        .content(json.writeValueAsString(sample("noauth@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_forbiddenForCustomerRole() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(user("cust").authorities(() -> CUSTOMER))
                        .contentType("application/json")
                        .content(json.writeValueAsString(sample("customer-role@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_validationError() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(user("officer").authorities(() -> EMPLOYEE))
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
                        .with(user("officer").authorities(() -> EMPLOYEE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(sample(email))))
                .andExpect(status().isConflict());
    }

    // ── GET /api/customers/{id} and ?email= ─────────────────────────────────

    @Test
    void getById_andByEmail() throws Exception {
        String id = register("lookup@example.com");

        mockMvc.perform(get("/api/customers/{id}", id).with(user("cust").authorities(() -> CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lookup@example.com"))
                .andExpect(jsonPath("$.onboardingStatus").value("REGISTERED"));

        mockMvc.perform(get("/api/customers").param("email", "lookup@example.com")
                        .with(user("officer").authorities(() -> EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID())
                        .with(user("officer").authorities(() -> EMPLOYEE)))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/customers (list + filter, paged) ───────────────────────────

    @Test
    void list_pagedAndFilteredByStatus() throws Exception {
        register("list1@example.com");

        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "5")
                        .with(user("officer").authorities(() -> EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable").exists());

        mockMvc.perform(get("/api/customers").param("status", "REGISTERED")
                        .with(user("officer").authorities(() -> EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].onboardingStatus").value("REGISTERED"));
    }

    @Test
    void list_forbiddenForCustomerRole() throws Exception {
        mockMvc.perform(get("/api/customers").with(user("cust").authorities(() -> CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/customers/{id} ──────────────────────────────────────────

    @Test
    void updateProfile() throws Exception {
        String id = register("update@example.com");
        CustomerUpdateRequest patch = new CustomerUpdateRequest(
                "Janet", null, null, null, null, null, "Chicago", null, null, null);

        mockMvc.perform(patch("/api/customers/{id}", id)
                        .with(user("cust").authorities(() -> CUSTOMER))
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
                        .with(user("officer").authorities(() -> EMPLOYEE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("DOCUMENTS_PENDING"));
    }

    @Test
    void onboardingStatus_forbiddenForCustomerRole() throws Exception {
        String id = register("status-cust@example.com");
        var body = new OnboardingStatusUpdateRequest(OnboardingStatus.DOCUMENTS_PENDING, null);

        mockMvc.perform(patch("/api/customers/{id}/onboarding-status", id)
                        .with(user("cust").authorities(() -> CUSTOMER))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onboardingStatus_illegalTransitionUnprocessable() throws Exception {
        String id = register("status-bad@example.com");
        var body = new OnboardingStatusUpdateRequest(OnboardingStatus.ONBOARDING_COMPLETE, null);

        mockMvc.perform(patch("/api/customers/{id}/onboarding-status", id)
                        .with(user("officer").authorities(() -> EMPLOYEE))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── DELETE /api/customers/{id} ─────────────────────────────────────────

    @Test
    void delete_requiresManagerRole() throws Exception {
        String id = register("delete@example.com");

        mockMvc.perform(delete("/api/customers/{id}", id).with(user("officer").authorities(() -> EMPLOYEE)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/customers/{id}", id).with(user("boss").authorities(() -> MANAGER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/customers/{id}", id).with(user("officer").authorities(() -> EMPLOYEE)))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/customers/me (self, resolved from the APIM identity) ────────

    @Test
    void me_returnsOwnProfileResolvedFromUserId() throws Exception {
        Customer profile = customers.save(Customer.builder()
                .firstName("Mia").lastName("Self").email("mia.self@example.com")
                .phoneNumber("+15550000001").onboardingStatus(OnboardingStatus.REGISTERED).build());
        AppUser account = appUsers.save(AppUser.builder()
                .loginId("miahandle").loginPassword("pw").name("Mia Self")
                .email("mia.self@example.com").userRole("customer").customerId(profile.getId()).build());

        mockMvc.perform(get("/api/customers/me")
                        .with(user("mia").authorities(() -> CUSTOMER))
                        .header("X-User-Id", String.valueOf(account.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile.getId().toString()))
                .andExpect(jsonPath("$.email").value("mia.self@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+15550000001"));
    }

    @Test
    void me_deniedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/customers/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_unknownUser_returns404() throws Exception {
        mockMvc.perform(get("/api/customers/me")
                        .with(user("ghost").authorities(() -> CUSTOMER))
                        .header("X-User-Id", "987654321"))
                .andExpect(status().isNotFound());
    }
}
