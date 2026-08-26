package com.bank.digital.lending.model.entity;

import com.bank.digital.lending.model.enums.DocType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "LOAN_DOCUMENTS")
public class LoanDocument {

    @Id
    @Column(name = "DOCUMENT_ID", length = 36)
    private String documentId;

    @Column(name = "APPLICATION_ID", length = 36)
    private String applicationId;

    @Column(name = "CUSTOMER_ID", nullable = false, length = 36)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "DOC_TYPE", nullable = false, length = 50)
    private DocType docType;

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 100)
    private String contentType;

    @Column(name = "BLOB_STORAGE_PATH", nullable = false, length = 500)
    private String blobStoragePath;

    @Column(name = "FILE_SIZE_BYTES", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "UPLOADED_AT", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public LoanDocument() {
    }

    public LoanDocument(String documentId, String applicationId, String customerId, DocType docType,
                        String fileName, String contentType, String blobStoragePath, Long fileSizeBytes) {
        this.documentId = documentId;
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.docType = docType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.blobStoragePath = blobStoragePath;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedAt = LocalDateTime.now();
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public DocType getDocType() {
        return docType;
    }

    public void setDocType(DocType docType) {
        this.docType = docType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getBlobStoragePath() {
        return blobStoragePath;
    }

    public void setBlobStoragePath(String blobStoragePath) {
        this.blobStoragePath = blobStoragePath;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
