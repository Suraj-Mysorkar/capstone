package com.capstone.document.entity;

import java.time.LocalDateTime;

import com.capstone.document.enums.DocumentStatus;
import com.capstone.document.enums.DocumentType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "application_id", length = 100)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_type_code", length = 50)
    private String documentTypeCode;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "blob_path", length = 500)
    private String blobPath;

    @Column(name = "blob_url", length = 1000)
    private String blobUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
