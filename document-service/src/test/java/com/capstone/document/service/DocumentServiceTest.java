package com.capstone.document.service;

import com.capstone.document.dto.DocumentResponse;
import com.capstone.document.dto.DocumentStatusUpdateRequest;
import com.capstone.document.dto.DocumentVersionResponse;
import com.capstone.document.entity.Document;
import com.capstone.document.entity.DocumentTypeMaster;
import com.capstone.document.entity.DocumentVersion;
import com.capstone.document.enums.DocumentStatus;
import com.capstone.document.enums.DocumentType;
import com.capstone.document.exception.DocumentNotFoundException;
import com.capstone.document.exception.InvalidDocumentException;
import com.capstone.document.repository.DocumentRepository;
import com.capstone.document.repository.DocumentTypeMasterRepository;
import com.capstone.document.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private DocumentTypeMasterRepository documentTypeMasterRepository;

    @Mock
    private AzureBlobStorageService blobStorageService;

    @Mock
    private LoanIntegrationEventPublisher eventPublisher;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private Document sampleDocument;
    private DocumentVersion sampleVersion;
    private DocumentTypeMaster sampleTypeMaster;

    @BeforeEach
    void setUp() {
        sampleDocument = new Document();
        sampleDocument.setDocumentId(1L);
        sampleDocument.setCustomerId("CUST-1001");
        sampleDocument.setApplicationId("APP-2001");
        sampleDocument.setDocumentType(DocumentType.IDENTITY_PROOF);
        sampleDocument.setDocumentTypeCode("IDENTITY_PROOF");
        sampleDocument.setDocumentName("Passport.pdf");
        sampleDocument.setOriginalFileName("passport.pdf");
        sampleDocument.setBlobPath("APP-2001/IDENTITY_PROOF/passport.pdf");
        sampleDocument.setBlobUrl("https://team6capstoneblobstorage.blob.core.windows.net/customer-documents/APP-2001/IDENTITY_PROOF/passport.pdf");
        sampleDocument.setContentType("application/pdf");
        sampleDocument.setFileSizeBytes(1024L);
        sampleDocument.setStatus(DocumentStatus.UPLOADED);
        sampleDocument.setCreatedAt(LocalDateTime.now());
        sampleDocument.setUpdatedAt(LocalDateTime.now());

        sampleVersion = new DocumentVersion();
        sampleVersion.setVersionId(10L);
        sampleVersion.setDocument(sampleDocument);
        sampleVersion.setVersionNumber(1);
        sampleVersion.setFileName("passport.pdf");
        sampleVersion.setContentType("application/pdf");
        sampleVersion.setFileSize(1024L);
        sampleVersion.setBlobPath("APP-2001/IDENTITY_PROOF/passport.pdf");
        sampleVersion.setBlobUrl("https://team6capstoneblobstorage.blob.core.windows.net/customer-documents/APP-2001/IDENTITY_PROOF/passport.pdf");
        sampleVersion.setFileData("Dummy PDF Content".getBytes());
        sampleVersion.setCreatedAt(LocalDateTime.now());

        sampleTypeMaster = new DocumentTypeMaster(1L, "IDENTITY_PROOF", "Identity Proof", "Passport or Aadhaar", true, 10, "pdf,jpg,png", LocalDateTime.now());
    }

    @Test
    void testUploadDocument_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "passport.pdf",
                "application/pdf",
                "Dummy content".getBytes()
        );

        when(documentTypeMasterRepository.findByTypeCodeIgnoreCase("IDENTITY_PROOF"))
                .thenReturn(Optional.of(sampleTypeMaster));
        when(blobStorageService.uploadBlob(eq("APP-2001"), eq("IDENTITY_PROOF"), eq(file)))
                .thenReturn(sampleDocument.getBlobUrl());
        when(documentRepository.save(any(Document.class)))
                .thenReturn(sampleDocument);
        when(documentVersionRepository.save(any(DocumentVersion.class)))
                .thenReturn(sampleVersion);

        DocumentResponse response = documentService.uploadDocument(
                "CUST-1001",
                "APP-2001",
                DocumentType.IDENTITY_PROOF,
                "Passport.pdf",
                file
        );

        assertNotNull(response);
        assertEquals(1L, response.getDocumentId());
        assertEquals("CUST-1001", response.getCustomerId());
        assertEquals("APP-2001", response.getApplicationId());
        assertEquals(DocumentType.IDENTITY_PROOF, response.getDocumentType());
        assertEquals(DocumentStatus.UPLOADED, response.getStatus());

        verify(eventPublisher, times(1)).publishDocumentUploadedEvent(
                eq(1L), eq("APP-2001"), eq("CUST-1001"), eq("IDENTITY_PROOF"), anyString()
        );
    }

    @Test
    void testUploadDocument_EmptyFile_ThrowsInvalidDocumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(InvalidDocumentException.class, () ->
                documentService.uploadDocument("CUST-1", "APP-1", DocumentType.IDENTITY_PROOF, "test", emptyFile)
        );
    }

    @Test
    void testUploadDocument_InvalidFileType_ThrowsInvalidDocumentException() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/x-msdownload",
                "test data".getBytes()
        );

        when(documentTypeMasterRepository.findByTypeCodeIgnoreCase(anyString()))
                .thenReturn(Optional.of(sampleTypeMaster));

        assertThrows(InvalidDocumentException.class, () ->
                documentService.uploadDocument("CUST-1", "APP-1", DocumentType.IDENTITY_PROOF, "test", exeFile)
        );
    }

    @Test
    void testUploadDocument_FileExceedsMaxSize_ThrowsInvalidDocumentException() {
        DocumentTypeMaster smallLimitMaster = new DocumentTypeMaster(1L, "IDENTITY_PROOF", "Identity Proof", "desc", true, 1, "pdf", LocalDateTime.now());
        when(documentTypeMasterRepository.findByTypeCodeIgnoreCase("IDENTITY_PROOF"))
                .thenReturn(Optional.of(smallLimitMaster));

        byte[] largeData = new byte[2 * 1024 * 1024]; // 2 MB > 1 MB limit
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                largeData
        );

        assertThrows(InvalidDocumentException.class, () ->
                documentService.uploadDocument("CUST-1", "APP-1", DocumentType.IDENTITY_PROOF, "test", largeFile)
        );
    }

    @Test
    void testGetDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(documentVersionRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(sampleVersion));

        DocumentResponse response = documentService.getDocument(1L);

        assertNotNull(response);
        assertEquals(1L, response.getDocumentId());
        assertEquals("Passport.pdf", response.getDocumentName());
        assertEquals(1, response.getLatestVersion());
    }

    @Test
    void testGetDocument_NotFound_ThrowsDocumentNotFoundException() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> documentService.getDocument(99L));
    }

    @Test
    void testGetCustomerDocuments() {
        when(documentRepository.findByCustomerId("CUST-1001")).thenReturn(List.of(sampleDocument));
        when(documentVersionRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(sampleVersion));

        List<DocumentResponse> result = documentService.getCustomerDocuments("CUST-1001");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CUST-1001", result.get(0).getCustomerId());
    }

    @Test
    void testGetApplicationDocuments() {
        when(documentRepository.findByApplicationId("APP-2001")).thenReturn(List.of(sampleDocument));
        when(documentVersionRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(sampleVersion));

        List<DocumentResponse> result = documentService.getApplicationDocuments("APP-2001");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("APP-2001", result.get(0).getApplicationId());
    }

    @Test
    void testUploadNewVersion_Success() throws IOException {
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "passport_v2.pdf",
                "application/pdf",
                "Updated PDF Content".getBytes()
        );

        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(documentTypeMasterRepository.findByTypeCodeIgnoreCase("IDENTITY_PROOF"))
                .thenReturn(Optional.of(sampleTypeMaster));
        when(documentVersionRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(sampleVersion));
        when(blobStorageService.uploadBlob(eq("APP-2001"), eq("IDENTITY_PROOF"), eq(newFile)))
                .thenReturn("https://new-blob-url.com");

        DocumentVersion version2 = new DocumentVersion(
                11L, sampleDocument, 2, "passport_v2.pdf", "APP-2001/IDENTITY_PROOF/passport.pdf",
                "https://new-blob-url.com", "application/pdf", 1024L, "Updated PDF Content".getBytes(), LocalDateTime.now()
        );
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenReturn(version2);
        when(documentRepository.save(any(Document.class))).thenReturn(sampleDocument);

        DocumentVersionResponse response = documentService.uploadNewVersion(1L, newFile);

        assertNotNull(response);
        assertEquals(2, response.getVersionNumber());
        assertEquals("passport_v2.pdf", response.getFileName());

        verify(eventPublisher, times(1)).publishDocumentUploadedEvent(
                eq(1L), eq("APP-2001"), eq("CUST-1001"), eq("IDENTITY_PROOF"), anyString()
        );
    }

    @Test
    void testUpdateStatus_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentVersionRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(sampleVersion));

        DocumentStatusUpdateRequest request = new DocumentStatusUpdateRequest(DocumentStatus.VERIFIED, "Document verified successfully");
        DocumentResponse response = documentService.updateStatus(1L, request);

        assertNotNull(response);
        assertEquals(DocumentStatus.VERIFIED, response.getStatus());
    }

    @Test
    void testDeleteDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        doNothing().when(documentRepository).delete(sampleDocument);

        assertDoesNotThrow(() -> documentService.deleteDocument(1L));
        verify(documentRepository, times(1)).delete(sampleDocument);
    }

    @Test
    void testDownloadDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(documentVersionRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.of(sampleVersion));

        ResponseEntity<Resource> response = documentService.downloadDocument(1L);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testGetSecureDownloadUrl_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(blobStorageService.generateSasUrl(anyString())).thenReturn("https://blob-storage.com/doc.pdf?sasToken=123");

        String sasUrl = documentService.getSecureDownloadUrl(1L);

        assertNotNull(sasUrl);
        assertTrue(sasUrl.contains("sasToken"));
    }

    @Test
    void testGetAllDocumentTypes() {
        when(documentTypeMasterRepository.findAll()).thenReturn(List.of(sampleTypeMaster));

        List<DocumentTypeMaster> result = documentService.getAllDocumentTypes();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("IDENTITY_PROOF", result.get(0).getTypeCode());
    }
}
