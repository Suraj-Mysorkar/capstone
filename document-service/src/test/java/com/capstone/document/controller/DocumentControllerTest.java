package com.capstone.document.controller;

import com.capstone.document.dto.DocumentResponse;
import com.capstone.document.dto.DocumentStatusUpdateRequest;
import com.capstone.document.dto.DocumentVersionResponse;
import com.capstone.document.entity.DocumentTypeMaster;
import com.capstone.document.enums.DocumentStatus;
import com.capstone.document.enums.DocumentType;
import com.capstone.document.exception.DocumentNotFoundException;
import com.capstone.document.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.mock.web.MockMultipartFile;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(DocumentController.class)
@Import(DocumentControllerTest.TestSecurityConfig.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Autowired
    private ObjectMapper objectMapper;

    private DocumentResponse sampleResponse;
    private DocumentVersionResponse sampleVersionResponse;
    private DocumentTypeMaster sampleTypeMaster;

    /*
     * Test user IDs used for X-User-Id header.
     */
    private static final String EMPLOYEE_USER_ID = "1001";
    private static final String MANAGER_USER_ID = "2001";
    private static final String CUSTOMER_USER_ID = "3001";

    @BeforeEach
    void setUp() {

        sampleResponse = new DocumentResponse();

        sampleResponse.setDocumentId(1L);
        sampleResponse.setCustomerId("CUST-1001");
        sampleResponse.setApplicationId("APP-2001");
        sampleResponse.setDocumentType(DocumentType.IDENTITY_PROOF);
        sampleResponse.setDocumentName("Passport.pdf");
        sampleResponse.setOriginalFileName("passport.pdf");
        sampleResponse.setBlobPath(
            "APP-2001/IDENTITY_PROOF/passport.pdf"
        );
        sampleResponse.setBlobUrl(
            "https://azure-storage/blob.pdf"
        );
        sampleResponse.setContentType("application/pdf");
        sampleResponse.setFileSizeBytes(1024L);
        sampleResponse.setStatus(DocumentStatus.UPLOADED);
        sampleResponse.setLatestVersion(1);
        sampleResponse.setCreatedAt(LocalDateTime.now());
        sampleResponse.setUpdatedAt(LocalDateTime.now());

        sampleVersionResponse = new DocumentVersionResponse();

        sampleVersionResponse.setVersionId(10L);
        sampleVersionResponse.setDocumentId(1L);
        sampleVersionResponse.setVersionNumber(1);
        sampleVersionResponse.setFileName("passport.pdf");
        sampleVersionResponse.setContentType("application/pdf");
        sampleVersionResponse.setFileSize(1024L);
        sampleVersionResponse.setCreatedAt(LocalDateTime.now());

        sampleTypeMaster = new DocumentTypeMaster(
            1L,
            "IDENTITY_PROOF",
            "Identity Proof",
            "Aadhaar or Passport",
            true,
            10,
            "pdf,jpg,png",
            LocalDateTime.now()
        );
    }


    // =========================================================
    // EMPLOYEE ROLE TESTS
    // =========================================================

    @Test
    void testUploadDocument_Success() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "passport.pdf",
            "application/pdf",
            "test content".getBytes()
        );

        when(documentService.uploadDocument(
            eq("CUST-1001"),
            eq("APP-2001"),
            eq(DocumentType.IDENTITY_PROOF),
            eq("Passport.pdf"),
            any()
        )).thenReturn(sampleResponse);

        mockMvc.perform(
            multipart("/api/v1/documents")
                .file(file)
                .param("customerId", "CUST-1001")
                .param("applicationId", "APP-2001")
                .param("documentType", "IDENTITY_PROOF")
                .param("documentName", "Passport.pdf")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentId").value(1))
        .andExpect(jsonPath("$.customerId").value("CUST-1001"))
        .andExpect(jsonPath("$.documentType").value("IDENTITY_PROOF"))
        .andExpect(jsonPath("$.status").value("UPLOADED"));
    }


    @Test
    void testGetDocument_Success() throws Exception {

        when(documentService.getDocument(1L))
            .thenReturn(sampleResponse);

        mockMvc.perform(
            get("/api/v1/documents/1")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value(1))
        .andExpect(
            jsonPath("$.documentName").value("Passport.pdf")
        );
    }


    @Test
    void testGetDocument_NotFound() throws Exception {

        when(documentService.getDocument(99L))
            .thenThrow(
                new DocumentNotFoundException(
                    "Document not found with ID: 99"
                )
            );

        mockMvc.perform(
            get("/api/v1/documents/99")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.code").value("DOCUMENT_NOT_FOUND")
        )
        .andExpect(
            jsonPath("$.message")
                .value("Document not found with ID: 99")
        );
    }


    @Test
    void testGetCustomerDocuments() throws Exception {

        when(documentService.getCustomerDocuments("CUST-1001"))
            .thenReturn(List.of(sampleResponse));

        mockMvc.perform(
            get("/api/v1/documents/customer/CUST-1001")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].documentId").value(1)
        )
        .andExpect(
            jsonPath("$[0].customerId").value("CUST-1001")
        );
    }


    @Test
    void testGetApplicationDocuments() throws Exception {

        when(documentService.getApplicationDocuments("APP-2001"))
            .thenReturn(List.of(sampleResponse));

        mockMvc.perform(
            get("/api/v1/documents/application/APP-2001")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].documentId").value(1)
        )
        .andExpect(
            jsonPath("$[0].applicationId").value("APP-2001")
        );
    }


    @Test
    void testGetDocumentTypes() throws Exception {

        when(documentService.getAllDocumentTypes())
            .thenReturn(List.of(sampleTypeMaster));

        mockMvc.perform(
            get("/api/v1/documents/types")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].typeCode")
                .value("IDENTITY_PROOF")
        )
        .andExpect(
            jsonPath("$[0].categoryName")
                .value("Identity Proof")
        );
    }


    @Test
    void testGetSecureDownloadUrl() throws Exception {

        String sasUrl =
            "https://azure-blob.net/customer-documents/doc.pdf?sas=abc";

        when(documentService.getSecureDownloadUrl(1L))
            .thenReturn(sasUrl);

        mockMvc.perform(
            get("/api/v1/documents/1/sas-url")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(content().string(sasUrl));
    }


    @Test
    void testDownloadDocument() throws Exception {

        byte[] content =
            "Sample Document Binary".getBytes();

        Resource resource =
            new ByteArrayResource(content);

        ResponseEntity<Resource> responseEntity =
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"passport.pdf\""
                )
                .body(resource);

        when(documentService.downloadDocument(1L))
            .thenReturn(responseEntity);

        mockMvc.perform(
            get("/api/v1/documents/1/download")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.APPLICATION_PDF)
        )
        .andExpect(
            header().string(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"passport.pdf\""
            )
        )
        .andExpect(content().bytes(content));
    }


    @Test
    void testGetDocumentVersions() throws Exception {

        when(documentService.getDocumentVersions(1L))
            .thenReturn(List.of(sampleVersionResponse));

        mockMvc.perform(
            get("/api/v1/documents/1/versions")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].versionId").value(10)
        )
        .andExpect(
            jsonPath("$[0].versionNumber").value(1)
        );
    }


    @Test
    void testUploadNewVersion() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "passport_v2.pdf",
            "application/pdf",
            "version 2 content".getBytes()
        );

        when(documentService.uploadNewVersion(eq(1L), any()))
            .thenReturn(sampleVersionResponse);

        mockMvc.perform(
            multipart("/api/v1/documents/1/versions")
                .file(file)
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.versionId").value(10)
        )
        .andExpect(
            jsonPath("$.documentId").value(1)
        )
        .andExpect(
            jsonPath("$.versionNumber").value(1)
        );
    }


    @Test
    void testUpdateStatus() throws Exception {

        DocumentStatusUpdateRequest request =
            new DocumentStatusUpdateRequest(
                DocumentStatus.VERIFIED,
                "Verified by Agent"
            );

        sampleResponse.setStatus(
            DocumentStatus.VERIFIED
        );

        when(
            documentService.updateStatus(
                eq(1L),
                any(DocumentStatusUpdateRequest.class)
            )
        ).thenReturn(sampleResponse);

        mockMvc.perform(
            put("/api/v1/documents/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(request)
                )
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.status").value("VERIFIED")
        );
    }


    @Test
    void testDeleteDocument() throws Exception {

        doNothing()
            .when(documentService)
            .deleteDocument(1L);

        mockMvc.perform(
            delete("/api/v1/documents/1")
                .header("X-User-Id", EMPLOYEE_USER_ID)
                .header("X-User-Role", "EMPLOYEE")
                .with(employeeUser())
        )
        .andExpect(status().isNoContent());

        verify(
            documentService,
            times(1)
        ).deleteDocument(1L);
    }


    // =========================================================
    // MANAGER ROLE TESTS
    // =========================================================

    @Test
    void testManagerCanUploadDocument() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "passport.pdf",
            "application/pdf",
            "test content".getBytes()
        );

        when(documentService.uploadDocument(
            eq("CUST-1001"),
            eq("APP-2001"),
            eq(DocumentType.IDENTITY_PROOF),
            eq("Passport.pdf"),
            any()
        )).thenReturn(sampleResponse);

        mockMvc.perform(
            multipart("/api/v1/documents")
                .file(file)
                .param("customerId", "CUST-1001")
                .param("applicationId", "APP-2001")
                .param("documentType", "IDENTITY_PROOF")
                .param("documentName", "Passport.pdf")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentId").value(1))
        .andExpect(
            jsonPath("$.customerId").value("CUST-1001")
        );
    }


    @Test
    void testManagerCanGetDocument() throws Exception {

        when(documentService.getDocument(1L))
            .thenReturn(sampleResponse);

        mockMvc.perform(
            get("/api/v1/documents/1")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.documentId").value(1)
        );
    }


    @Test
    void testManagerCanGetCustomerDocuments()
        throws Exception {

        when(
            documentService.getCustomerDocuments("CUST-1001")
        ).thenReturn(List.of(sampleResponse));

        mockMvc.perform(
            get("/api/v1/documents/customer/CUST-1001")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].documentId").value(1)
        )
        .andExpect(
            jsonPath("$[0].customerId")
                .value("CUST-1001")
        );
    }


    @Test
    void testManagerCanGetApplicationDocuments()
        throws Exception {

        when(
            documentService.getApplicationDocuments("APP-2001")
        ).thenReturn(List.of(sampleResponse));

        mockMvc.perform(
            get("/api/v1/documents/application/APP-2001")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].documentId").value(1)
        )
        .andExpect(
            jsonPath("$[0].applicationId")
                .value("APP-2001")
        );
    }


    @Test
    void testManagerCanGetDocumentTypes()
        throws Exception {

        when(documentService.getAllDocumentTypes())
            .thenReturn(List.of(sampleTypeMaster));

        mockMvc.perform(
            get("/api/v1/documents/types")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].typeCode")
                .value("IDENTITY_PROOF")
        );
    }


    @Test
    void testManagerCanDownloadDocument()
        throws Exception {

        byte[] fileContent =
            "Sample Document Binary".getBytes();

        Resource resource =
            new ByteArrayResource(fileContent);

        ResponseEntity<Resource> responseEntity =
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"passport.pdf\""
                )
                .body(resource);

        when(documentService.downloadDocument(1L))
            .thenReturn(responseEntity);

        mockMvc.perform(
            get("/api/v1/documents/1/download")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.APPLICATION_PDF)
        )
        .andExpect(content().bytes(fileContent));
    }


    @Test
    void testManagerCanGetSecureDownloadUrl()
        throws Exception {

        String sasUrl =
            "https://azure-blob.net/customer-documents/doc.pdf?sas=abc";

        when(documentService.getSecureDownloadUrl(1L))
            .thenReturn(sasUrl);

        mockMvc.perform(
            get("/api/v1/documents/1/sas-url")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(sasUrl)
        );
    }


    @Test
    void testManagerCanGetDocumentVersions()
        throws Exception {

        when(documentService.getDocumentVersions(1L))
            .thenReturn(List.of(sampleVersionResponse));

        mockMvc.perform(
            get("/api/v1/documents/1/versions")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].versionId").value(10)
        )
        .andExpect(
            jsonPath("$[0].documentId").value(1)
        )
        .andExpect(
            jsonPath("$[0].versionNumber").value(1)
        );
    }


    @Test
    void testManagerCanUploadNewVersion()
        throws Exception {

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "passport_v2.pdf",
            "application/pdf",
            "version 2 content".getBytes()
        );

        when(
            documentService.uploadNewVersion(eq(1L), any())
        ).thenReturn(sampleVersionResponse);

        mockMvc.perform(
            multipart("/api/v1/documents/1/versions")
                .file(file)
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.versionId").value(10)
        )
        .andExpect(
            jsonPath("$.documentId").value(1)
        )
        .andExpect(
            jsonPath("$.versionNumber").value(1)
        );
    }


    @Test
    void testManagerCanUpdateDocumentStatus()
        throws Exception {

        DocumentStatusUpdateRequest request =
            new DocumentStatusUpdateRequest(
                DocumentStatus.VERIFIED,
                "Verified by Manager"
            );

        sampleResponse.setStatus(
            DocumentStatus.VERIFIED
        );

        when(
            documentService.updateStatus(
                eq(1L),
                any(DocumentStatusUpdateRequest.class)
            )
        ).thenReturn(sampleResponse);

        mockMvc.perform(
            put("/api/v1/documents/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(request)
                )
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.status").value("VERIFIED")
        );
    }


    @Test
    void testManagerCanDeleteDocument()
        throws Exception {

        doNothing()
            .when(documentService)
            .deleteDocument(1L);

        mockMvc.perform(
            delete("/api/v1/documents/1")
                .header("X-User-Id", MANAGER_USER_ID)
                .header("X-User-Role", "MANAGER")
                .with(managerUser())
        )
        .andExpect(status().isNoContent());

        verify(
            documentService,
            times(1)
        ).deleteDocument(1L);
    }


    // =========================================================
    // AUTHORIZATION TESTS
    // =========================================================

    @Test
    void testCustomerCannotAccessDocuments()
        throws Exception {

        mockMvc.perform(
            get("/api/v1/documents/1")
                .header("X-User-Id", CUSTOMER_USER_ID)
                .header("X-User-Role", "CUSTOMER")
                .with(customerUser())
        )
        .andExpect(status().isForbidden());

        verify(
            documentService,
            times(0)
        ).getDocument(any());
    }


    @Test
    void testUnauthenticatedUserCannotAccessDocuments()
        throws Exception {

        mockMvc.perform(
            get("/api/v1/documents/1")
        )
        .andExpect(status().isForbidden());

        verify(
            documentService,
            times(0)
        ).getDocument(any());
    }


    // =========================================================
    // SECURITY USERS
    // =========================================================

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
    employeeUser() {

        return user("employee1")
            .authorities(
                new SimpleGrantedAuthority(
                    "ROLE_EMPLOYEE"
                )
            );
    }


    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
    managerUser() {

        return user("manager1")
            .authorities(
                new SimpleGrantedAuthority(
                    "ROLE_MANAGER"
                )
            );
    }


    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
    customerUser() {

        return user("customer1")
            .authorities(
                new SimpleGrantedAuthority(
                    "ROLE_CUSTOMER"
                )
            );
    }


    // =========================================================
    // TEST SECURITY CONFIGURATION
    // =========================================================

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
            HttpSecurity http
        ) throws Exception {

            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()
                    .anyRequest().authenticated()
                );

            return http.build();
        }
    }
}