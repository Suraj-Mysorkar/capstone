package com.bank.digital.lending.model.dto;

import com.bank.digital.lending.model.enums.DocType;
import java.util.List;

/**
 * Payload sent by document-service to loan-service when a document is uploaded.
 * Includes the document IDs (for legacy linking) plus the full metadata needed
 * to insert a proper row in LOAN_DOCUMENTS.
 */
public record DocumentUploadedRequest(
        List<String> documentIds,
        String customerId,
        String documentType,
        String documentName,
        String blobUrl,
        String blobPath,
        String contentType,
        Long fileSizeBytes
) {
    /**
     * Convenience constructor for backward compatibility —
     * when only documentIds + customerId are provided (old callers).
     */
    public DocumentUploadedRequest(List<String> documentIds, String customerId) {
        this(documentIds, customerId, null, null, null, null, null, null);
    }

    /**
     * Resolve the DocType enum safely, falling back to OTHER if unknown.
     */
    public DocType resolvedDocType() {
        if (documentType == null || documentType.isBlank()) return DocType.OTHER;
        try {
            return DocType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DocType.OTHER;
        }
    }
}
