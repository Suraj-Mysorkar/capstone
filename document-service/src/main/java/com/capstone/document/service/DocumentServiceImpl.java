package com.capstone.document.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.capstone.document.dto.DocumentResponse;
import com.capstone.document.dto.DocumentStatusUpdateRequest;
import com.capstone.document.dto.DocumentVersionResponse;
import com.capstone.document.entity.Document;
import com.capstone.document.entity.DocumentVersion;
import com.capstone.document.enums.DocumentStatus;
import com.capstone.document.enums.DocumentType;
import com.capstone.document.exception.DocumentNotFoundException;
import com.capstone.document.exception.InvalidDocumentException;
import com.capstone.document.repository.DocumentRepository;
import com.capstone.document.repository.DocumentVersionRepository;

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {
    
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository) {

        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    @Override
    public DocumentResponse uploadDocument(
            Long customerId,
            Long applicationId,
            DocumentType documentType,
            MultipartFile file) {

        validateFile(file);

        Document document = new Document();

        document.setCustomerId(customerId);
        document.setApplicationId(applicationId);
        document.setDocumentType(documentType);
        document.setDocumentName(file.getOriginalFilename());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());

        Document savedDocument = documentRepository.save(document);

        DocumentVersion version =
                createVersion(
                        savedDocument,
                        file,
                        1);

        documentVersionRepository.save(version);

        return mapToResponse(savedDocument, version);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(
            Long documentId) {

        Document document = getDocumentById(documentId);

        DocumentVersion latestVersion =
                documentVersionRepository
                        .findTopByDocumentDocumentIdOrderByVersionNumberDesc(
                                documentId)
                        .orElse(null);

        return mapToResponse(document, latestVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadDocument(
            Long documentId) {

        Document document = getDocumentById(documentId);

        DocumentVersion latestVersion =
                documentVersionRepository
                        .findTopByDocumentDocumentIdOrderByVersionNumberDesc(
                                documentId)
                        .orElseThrow(() ->
                                new DocumentNotFoundException(
                                        "No version found for document: "
                                                + documentId));

        Resource resource = new ByteArrayResource(latestVersion.getFileData());

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(
                    latestVersion.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + latestVersion.getFileName()
                                + "\"")
                .body(resource);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getCustomerDocuments(
            Long customerId) {

        return documentRepository
                .findByCustomerId(customerId)
                .stream()
                .map(document -> {

                    DocumentVersion latestVersion =
                            documentVersionRepository
                                    .findTopByDocumentDocumentIdOrderByVersionNumberDesc(
                                            document.getDocumentId())
                                    .orElse(null);

                    return mapToResponse( document, latestVersion);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getApplicationDocuments(
            Long applicationId) {

        return documentRepository
                .findByApplicationId(applicationId)
                .stream()
                .map(document -> {

                    DocumentVersion latestVersion =
                            documentVersionRepository
                                    .findTopByDocumentDocumentIdOrderByVersionNumberDesc(
                                            document.getDocumentId())
                                    .orElse(null);

                    return mapToResponse(document, latestVersion);
                })
                .toList();
    }

     @Override
    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getDocumentVersions(
            Long documentId) {

        getDocumentById(documentId);

        return documentVersionRepository
                .findByDocumentDocumentId(documentId)
                .stream()
                .map(this::mapToVersionResponse)
                .toList();
    }

    @Override
    public DocumentVersionResponse uploadNewVersion(
            Long documentId,
            MultipartFile file) {

        validateFile(file);

        Document document = getDocumentById(documentId);

        DocumentVersion latestVersion =
                documentVersionRepository
                        .findTopByDocumentDocumentIdOrderByVersionNumberDesc(
                                documentId)
                        .orElse(null);

        int nextVersion =
                latestVersion == null
                        ? 1
                        : latestVersion.getVersionNumber() + 1;

        DocumentVersion newVersion =
                createVersion(
                        document,
                        file,
                        nextVersion);

        DocumentVersion savedVersion = documentVersionRepository.save(newVersion);

        document.setDocumentName(file.getOriginalFilename());

        document.setUpdatedAt(LocalDateTime.now());

        documentRepository.save(document);

        return mapToVersionResponse(savedVersion);
    }

    @Override
    public DocumentResponse updateStatus(
            Long documentId,
            DocumentStatusUpdateRequest request) {

        Document document = getDocumentById(documentId);

        document.setStatus(request.getStatus());
        document.setUpdatedAt(LocalDateTime.now());

        Document savedDocument = documentRepository.save(document);

        DocumentVersion latestVersion =
                documentVersionRepository
                        .findTopByDocumentDocumentIdOrderByVersionNumberDesc(
                                documentId)
                        .orElse(null);

        return mapToResponse(savedDocument, latestVersion);
    }

    @Override
    public void deleteDocument(Long documentId) {

        Document document = getDocumentById(documentId);

        documentRepository.delete(document);
    }

    private Document getDocumentById(Long documentId) {

        return documentRepository
                .findById(documentId)
                .orElseThrow(() ->
                        new DocumentNotFoundException(
                                "Document not found: "
                                        + documentId));
    }

    private DocumentVersion createVersion(
            Document document,
            MultipartFile file,
            int versionNumber) {

        DocumentVersion version = new DocumentVersion();

        version.setDocument(document);
        version.setVersionNumber(versionNumber);
        version.setFileName(file.getOriginalFilename());
        version.setContentType(file.getContentType());
        version.setFileSize(file.getSize());

        try {
            version.setFileData(file.getBytes());
        } catch (IOException e) {
            throw new InvalidDocumentException(
                    "Unable to read uploaded file");
        }

        version.setCreatedAt(LocalDateTime.now());

        return version;
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException(
                    "Document file cannot be empty");
        }

        long maxFileSize = 10 * 1024 * 1024;

        if (file.getSize() > maxFileSize) {
            throw new InvalidDocumentException(
                    "Maximum file size is 10 MB");
        }

        String contentType = file.getContentType();

        List<String> allowedTypes =
                List.of(
                        "application/pdf",
                        "image/jpeg",
                        "image/png");

        if (contentType == null || !allowedTypes.contains(contentType)) {

            throw new InvalidDocumentException(
                    "Only PDF, JPEG and PNG files are supported");
        }
    }

    private DocumentResponse mapToResponse(
            Document document,
            DocumentVersion version) {

        DocumentResponse response = new DocumentResponse();

        response.setDocumentId(document.getDocumentId());

        response.setCustomerId(document.getCustomerId());

        response.setApplicationId(document.getApplicationId());

        response.setDocumentType(document.getDocumentType());

        response.setDocumentName(document.getDocumentName());

        response.setStatus(document.getStatus());

        if (version != null) {
            response.setLatestVersion(
                    version.getVersionNumber());
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
