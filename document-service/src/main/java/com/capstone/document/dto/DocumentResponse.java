package com.capstone.document.dto;

import java.time.LocalDateTime;

import com.capstone.document.enums.DocumentStatus;
import com.capstone.document.enums.DocumentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    
    private Long documentId;

    private String customerId;

    private String applicationId;

    private DocumentType documentType;

    private String documentName;

    private String originalFileName;

    private String blobPath;

    private String blobUrl;

    private String contentType;

    private Long fileSizeBytes;

    private DocumentStatus status;

    private Integer latestVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
