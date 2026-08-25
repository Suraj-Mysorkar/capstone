package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.DocumentUploadResponse;
import com.bank.digital.lending.model.enums.DocType;
import com.bank.digital.lending.service.DocumentStorageProxyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentStorageController.class)
class DocumentStorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentStorageProxyService documentStorageProxy;

    @Test
    @DisplayName("Controller: POST /api/v1/loans/documents/upload returns 201 Created")
    void testUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "identity.pdf",
                "application/pdf",
                "test data".getBytes()
        );

        DocumentUploadResponse response = new DocumentUploadResponse(
                "DOC-12345678",
                "APP-1001",
                "CUST-1001",
                DocType.IDENTITY_PROOF,
                "identity.pdf",
                "application/pdf",
                "https://bankstorage.blob.core.windows.net/loan-documents/identity.pdf",
                1024L,
                LocalDateTime.now()
        );

        when(documentStorageProxy.uploadDocument(eq("CUST-1001"), eq("APP-1001"), eq(DocType.IDENTITY_PROOF), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/loans/documents/upload")
                        .file(file)
                        .param("customerId", "CUST-1001")
                        .param("applicationId", "APP-1001")
                        .param("docType", "IDENTITY_PROOF"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value("DOC-12345678"))
                .andExpect(jsonPath("$.fileName").value("identity.pdf"))
                .andExpect(jsonPath("$.docType").value("IDENTITY_PROOF"));
    }

    @Test
    @DisplayName("Controller: GET /api/v1/loans/documents/{id} returns 200 OK when found")
    void testGetDocument_Found() throws Exception {
        DocumentUploadResponse response = new DocumentUploadResponse(
                "DOC-12345678",
                "APP-1001",
                "CUST-1001",
                DocType.IDENTITY_PROOF,
                "identity.pdf",
                "application/pdf",
                "https://bankstorage.blob.core.windows.net/loan-documents/identity.pdf",
                1024L,
                LocalDateTime.now()
        );

        when(documentStorageProxy.getDocument("DOC-12345678")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/loans/documents/DOC-12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("DOC-12345678"))
                .andExpect(jsonPath("$.fileName").value("identity.pdf"));
    }

    @Test
    @DisplayName("Controller: GET /api/v1/loans/documents/{id} returns 404 when not found")
    void testGetDocument_NotFound() throws Exception {
        when(documentStorageProxy.getDocument("DOC-NOTFOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/loans/documents/DOC-NOTFOUND"))
                .andExpect(status().isNotFound());
    }
}
