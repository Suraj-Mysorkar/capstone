package com.capstone.document.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {
    
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentTypeMasterRepository documentTypeMasterRepository;
    private final AzureBlobStorageService blobStorageService;
    private final LoanIntegrationEventPublisher eventPublisher;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentTypeMasterRepository documentTypeMasterRepository,
            AzureBlobStorageService blobStorageService,
            LoanIntegrationEventPublisher eventPublisher) {

        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentTypeMasterRepository = documentTypeMasterRepository;
        this.blobStorageService = blobStorageService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public DocumentResponse uploadDocument(
            String customerId,
            String applicationId,
            DocumentType documentType,
            String documentName,
            MultipartFile file) {

        validateFile(file, documentType);

        String effectiveDocName = (documentName != null && !documentName.isBlank())
                ? documentName
                : (file.getOriginalFilename() != null ? file.getOriginalFilename() : documentType.name());

        // 1. Upload to Azure Blob Storage with required folder hierarchy:
        // {application_id}/{document_type}/{docType}_{appId}_{timestamp}.{ext}
        String blobUrl;
        try {
            blobUrl = blobStorageService.uploadBlob(
                    applicationId != null ? applicationId : "UNASSIGNED",
                    documentType.name(),
                    file
            );
        } catch (IOException e) {
            throw new InvalidDocumentException("Failed to upload file to storage: " + e.getMessage());
        }

        // 2. Persist Document Entity in Database
        Document document = new Document();
        document.setCustomerId(customerId != null ? customerId : "UNKNOWN_CUSTOMER");
        document.setApplicationId(applicationId);
        document.setDocumentType(documentType);
        document.setDocumentTypeCode(documentType.name());
        document.setDocumentName(effectiveDocName);
        document.setOriginalFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFileSizeBytes(file.getSize());
        document.setBlobPath(applicationId + "/" + documentType.name() + "/" + file.getOriginalFilename());
        document.setBlobUrl(blobUrl);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());

        Document savedDocument = documentRepository.save(document);

        // 3. Save Document Version record
        DocumentVersion version = createVersion(savedDocument, file, 1, blobUrl);
        documentVersionRepository.save(version);

        // 4. Publish DOCUMENT_UPLOADED Event & notify Loan Service
        eventPublisher.publishDocumentUploadedEvent(
                savedDocument.getDocumentId(),
                savedDocument.getApplicationId(),
                savedDocument.getCustomerId(),
                documentType.name(),
                blobUrl
        );

        return mapToResponse(savedDocument, version);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long documentId) {
        Document document = getDocumentById(documentId);
        DocumentVersion latestVersion = documentVersionRepository
                .findTopByDocumentDocumentIdOrderByVersionNumberDesc(documentId)
                .orElse(null);
        return mapToResponse(document, latestVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public String getSecureDownloadUrl(Long documentId) {
        Document document = getDocumentById(documentId);
        return blobStorageService.generateSasUrl(document.getBlobUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadDocument(Long documentId) {
        Document document = getDocumentById(documentId);

        DocumentVersion latestVersion = documentVersionRepository
                .findTopByDocumentDocumentIdOrderByVersionNumberDesc(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("No version found for document: " + documentId));

        byte[] data = null;
        if (latestVersion.getBlobPath() != null) {
            data = blobStorageService.downloadBlob(latestVersion.getBlobPath());
        }
        if (data == null || data.length == 0) {
            data = latestVersion.getFileData() != null ? latestVersion.getFileData() : new byte[0];
        }

        Resource resource = new ByteArrayResource(data);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(latestVersion.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + latestVersion.getFileName() + "\"")
                .body(resource);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getCustomerDocuments(String customerId) {
        return documentRepository.findByCustomerId(customerId)
                .stream()
                .map(doc -> {
                    DocumentVersion v = documentVersionRepository
                            .findTopByDocumentDocumentIdOrderByVersionNumberDesc(doc.getDocumentId())
                            .orElse(null);
                    return mapToResponse(doc, v);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getApplicationDocuments(String applicationId) {
        return documentRepository.findByApplicationId(applicationId)
                .stream()
                .map(doc -> {
                    DocumentVersion v = documentVersionRepository
                            .findTopByDocumentDocumentIdOrderByVersionNumberDesc(doc.getDocumentId())
                            .orElse(null);
                    return mapToResponse(doc, v);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getDocumentVersions(Long documentId) {
        getDocumentById(documentId);
        return documentVersionRepository.findByDocumentDocumentId(documentId)
                .stream()
                .map(this::mapToVersionResponse)
                .toList();
    }

    @Override
    public DocumentVersionResponse uploadNewVersion(Long documentId, MultipartFile file) {
        Document document = getDocumentById(documentId);
        validateFile(file, document.getDocumentType());

        DocumentVersion latestVersion = documentVersionRepository
                .findTopByDocumentDocumentIdOrderByVersionNumberDesc(documentId)
                .orElse(null);

        int nextVersion = (latestVersion == null) ? 1 : latestVersion.getVersionNumber() + 1;

        String blobUrl;
        try {
            blobUrl = blobStorageService.uploadBlob(
                    document.getApplicationId() != null ? document.getApplicationId() : "UNASSIGNED",
                    document.getDocumentType().name(),
                    file
            );
        } catch (IOException e) {
            throw new InvalidDocumentException("Failed to upload version: " + e.getMessage());
        }

        DocumentVersion newVersion = createVersion(document, file, nextVersion, blobUrl);
        DocumentVersion savedVersion = documentVersionRepository.save(newVersion);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setBlobUrl(blobUrl);
        document.setFileSizeBytes(file.getSize());
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        // Notify loan service of new version upload
        eventPublisher.publishDocumentUploadedEvent(
                document.getDocumentId(),
                document.getApplicationId(),
                document.getCustomerId(),
                document.getDocumentType().name(),
                blobUrl
        );

        return mapToVersionResponse(savedVersion);
    }

    @Override
    public DocumentResponse updateStatus(Long documentId, DocumentStatusUpdateRequest request) {
        Document document = getDocumentById(documentId);
        document.setStatus(request.getStatus());
        document.setUpdatedAt(LocalDateTime.now());
        Document savedDocument = documentRepository.save(document);

        DocumentVersion latestVersion = documentVersionRepository
                .findTopByDocumentDocumentIdOrderByVersionNumberDesc(documentId)
                .orElse(null);

        return mapToResponse(savedDocument, latestVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTypeMaster> getAllDocumentTypes() {
        return documentTypeMasterRepository.findAll();
    }

    @Override
    public void deleteDocument(Long documentId) {
        Document document = getDocumentById(documentId);
        documentRepository.delete(document);
    }

    private Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));
    }

    private DocumentVersion createVersion(Document document, MultipartFile file, int versionNumber, String blobUrl) {
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(versionNumber);
        version.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "doc.pdf");
        version.setContentType(file.getContentType() != null ? file.getContentType() : "application/pdf");
        version.setFileSize(file.getSize());
        version.setBlobPath(document.getBlobPath());
        version.setBlobUrl(blobUrl);
        try {
            version.setFileData(file.getBytes());
        } catch (IOException e) {
            version.setFileData(new byte[0]);
        }
        version.setCreatedAt(LocalDateTime.now());
        return version;
    }

    private void validateFile(MultipartFile file, DocumentType documentType) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Document file cannot be empty");
        }

        // Fetch max size from master table if present, default 10MB
        int maxSizeMb = 10;
        if (documentType != null) {
            maxSizeMb = documentTypeMasterRepository.findByTypeCodeIgnoreCase(documentType.name())
                    .map(DocumentTypeMaster::getMaxSizeMb)
                    .orElse(10);
        }
        long maxFileSizeBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidDocumentException("Maximum allowed file size for " + documentType + " is " + maxSizeMb + " MB");
        }

        String contentType = file.getContentType();
        List<String> allowedTypes = List.of(
                "application/pdf",
                "image/jpeg",
                "image/jpg",
                "image/png"
        );

        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new InvalidDocumentException("Invalid file format. Only PDF, JPEG, and PNG files are supported.");
        }
    }

    private DocumentResponse mapToResponse(Document document, DocumentVersion version) {
        DocumentResponse response = new DocumentResponse();
        response.setDocumentId(document.getDocumentId());
        response.setCustomerId(document.getCustomerId());
        response.setApplicationId(document.getApplicationId());
        response.setDocumentType(document.getDocumentType());
        response.setDocumentName(document.getDocumentName());
        response.setOriginalFileName(document.getOriginalFileName());
        response.setBlobPath(document.getBlobPath());
        response.setBlobUrl(document.getBlobUrl());
        response.setContentType(document.getContentType());
        response.setFileSizeBytes(document.getFileSizeBytes());
        response.setStatus(document.getStatus());
        if (version != null) {
            response.setLatestVersion(version.getVersionNumber());
        }
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        return response;
    }

    private DocumentVersionResponse mapToVersionResponse(DocumentVersion version) {
        DocumentVersionResponse response = new DocumentVersionResponse();
        response.setVersionId(version.getVersionId());
        response.setDocumentId(version.getDocument().getDocumentId());
        response.setVersionNumber(version.getVersionNumber());
        response.setFileName(version.getFileName());
        response.setContentType(version.getContentType());
        response.setFileSize(version.getFileSize());
        response.setCreatedAt(version.getCreatedAt());
        return response;
    }
}
