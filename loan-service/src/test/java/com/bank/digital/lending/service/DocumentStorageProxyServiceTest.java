package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.DocumentUploadResponse;
import com.bank.digital.lending.model.entity.LoanDocument;
import com.bank.digital.lending.model.enums.DocType;
import com.bank.digital.lending.repository.LoanDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentStorageProxyServiceTest {

    @Mock
    private LoanDocumentRepository documentRepository;

    @InjectMocks
    private DocumentStorageProxyService documentStorageService;

    @Test
    @DisplayName("Document Storage: Upload document generates metadata and local store entry")
    void testUploadDocument() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "salary_slip.pdf",
                "application/pdf",
                "Dummy PDF content".getBytes()
        );

        when(documentRepository.save(any(LoanDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentUploadResponse response = documentStorageService.uploadDocument(
                "CUST-1001",
                "APP-2001",
                DocType.INCOME_PROOF,
                file
        );

        assertNotNull(response);
        assertTrue(response.documentId().startsWith("DOC-"));
        assertEquals("CUST-1001", response.customerId());
        assertEquals("APP-2001", response.applicationId());
        assertEquals(DocType.INCOME_PROOF, response.docType());
        assertEquals("salary_slip.pdf", response.fileName());
        assertEquals("application/pdf", response.contentType());
        assertTrue(response.blobStoragePath().contains("https://bankstorage.blob.core.windows.net/"));

        verify(documentRepository, times(1)).save(any(LoanDocument.class));
    }

    @Test
    @DisplayName("Document Storage: Get document by ID")
    void testGetDocumentById() {
        LoanDocument doc = new LoanDocument(
                "DOC-A1B2C3D4",
                "APP-1001",
                "CUST-1001",
                DocType.IDENTITY_PROOF,
                "passport.pdf",
                "application/pdf",
                "https://bankstorage.blob.core.windows.net/loan-documents/passport.pdf",
                1024L
        );
        when(documentRepository.findById("DOC-A1B2C3D4")).thenReturn(Optional.of(doc));

        Optional<DocumentUploadResponse> result = documentStorageService.getDocument("DOC-A1B2C3D4");

        assertTrue(result.isPresent());
        assertEquals("DOC-A1B2C3D4", result.get().documentId());
        assertEquals("passport.pdf", result.get().fileName());
    }

    @Test
    @DisplayName("Document Storage: Get documents by Application ID")
    void testGetDocumentsByApplicationId() {
        LoanDocument doc1 = new LoanDocument("DOC-1", "APP-100", "CUST-1", DocType.IDENTITY_PROOF, "id.pdf", "application/pdf", "path1", 500L);
        LoanDocument doc2 = new LoanDocument("DOC-2", "APP-100", "CUST-1", DocType.INCOME_PROOF, "tax.pdf", "application/pdf", "path2", 800L);

        when(documentRepository.findByApplicationId("APP-100")).thenReturn(List.of(doc1, doc2));

        List<DocumentUploadResponse> docs = documentStorageService.getDocumentsByApplicationId("APP-100");

        assertEquals(2, docs.size());
        assertEquals("DOC-1", docs.get(0).documentId());
        assertEquals("DOC-2", docs.get(1).documentId());
    }

    @Test
    @DisplayName("Document Storage: Link uploaded documents to application")
    void testLinkDocumentsToApplication() {
        LoanDocument doc = new LoanDocument("DOC-1", null, "CUST-1", DocType.IDENTITY_PROOF, "id.pdf", "application/pdf", "path1", 500L);
        when(documentRepository.findById("DOC-1")).thenReturn(Optional.of(doc));

        documentStorageService.linkDocumentsToApplication(List.of("DOC-1"), "APP-999");

        assertEquals("APP-999", doc.getApplicationId());
        verify(documentRepository, times(1)).save(doc);
    }
}
