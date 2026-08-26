package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.DocType;
import java.time.LocalDateTime;

public record DocumentUploadResponse(
    String documentId,
    String applicationId,
    String customerId,
    DocType docType,
    String fileName,
    String contentType,
    String blobStoragePath,
    Long fileSizeBytes,
    LocalDateTime uploadedAt
) {}
