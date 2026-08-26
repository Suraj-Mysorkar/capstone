package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.DocumentUploadResponse;
import com.bank.digital.lending.model.entity.LoanDocument;
import com.bank.digital.lending.model.enums.DocType;
import com.bank.digital.lending.repository.LoanDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentStorageProxyService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageProxyService.class);

    @Value("${azure.enabled:false}")
    private boolean azureEnabled;

    @Value("${azure.storage.blob.container-name:loan-documents}")
    private String containerName;

    private final LoanDocumentRepository documentRepository;
    private final Path localUploadDir = Paths.get("./data/documents");

    public DocumentStorageProxyService(LoanDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        initLocalStorage();
    }

    private void initLocalStorage() {
        try {
            if (!Files.exists(localUploadDir)) {
                Files.createDirectories(localUploadDir);
            }
        } catch (IOException e) {
            log.warn("Could not create local upload directory: {}", e.getMessage());
        }
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(String customerId, String applicationId,
                                                 DocType docType, MultipartFile file) throws IOException {
        String documentId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        long size = file.getSize();

        String blobStoragePath = String.format("https://bankstorage.blob.core.windows.net/%s/%s_%s_%s",
                containerName, customerId, documentId, originalFilename);

        // Mock Logger for Azure Blob Storage
        log.info("================================================================================");
        log.info("[MOCK AZURE BLOB STORAGE] Initiating direct file ingestion to Azure Blob Storage");
        log.info("[MOCK AZURE BLOB STORAGE] Target Container: '{}' | File: '{}' | Size: {} bytes",
                containerName, originalFilename, size);
        log.info("[MOCK AZURE BLOB STORAGE] Blob URI: {}", blobStoragePath);
        log.info("[MOCK AZURE BLOB STORAGE] SAS Token generated with 15-minute write lease.");

        // Save local backup file in dev mode
        try {
            Path targetPath = localUploadDir.resolve(documentId + "_" + originalFilename);
            file.transferTo(targetPath);
            log.info("[MOCK AZURE BLOB STORAGE] Local storage mirror persisted at: {}", targetPath.toAbsolutePath());
        } catch (Exception e) {
            log.debug("Local disk write skipped: {}", e.getMessage());
        }
        log.info("================================================================================");

        LoanDocument entity = new LoanDocument(
                documentId,
                applicationId,
                customerId,
                docType,
                originalFilename,
                contentType,
                blobStoragePath,
                size
        );
        LoanDocument saved = documentRepository.save(entity);

        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentUploadResponse> getDocument(String documentId) {
        return documentRepository.findById(documentId).map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public List<DocumentUploadResponse> getDocumentsByApplicationId(String applicationId) {
        return documentRepository.findByApplicationId(applicationId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional
    public void linkDocumentsToApplication(List<String> documentIds, String applicationId) {
        if (documentIds == null || documentIds.isEmpty()) return;
        for (String docId : documentIds) {
            documentRepository.findById(docId).ifPresent(doc -> {
                doc.setApplicationId(applicationId);
                documentRepository.save(doc);
            });
        }
    }

    private DocumentUploadResponse mapToDTO(LoanDocument entity) {
        return new DocumentUploadResponse(
                entity.getDocumentId(),
                entity.getApplicationId(),
                entity.getCustomerId(),
                entity.getDocType(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getBlobStoragePath(),
                entity.getFileSizeBytes(),
                entity.getUploadedAt()
        );
    }
}
