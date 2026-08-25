package com.capstone.document.service;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.document.dto.DocumentResponse;
import com.capstone.document.dto.DocumentStatusUpdateRequest;
import com.capstone.document.dto.DocumentVersionResponse;
import com.capstone.document.enums.DocumentType;

import org.springframework.core.io.Resource;

public interface DocumentService {
    
    DocumentResponse uploadDocument(
            Long customerId,
            Long applicationId,
            DocumentType documentType,
            MultipartFile file);

    DocumentResponse getDocument(Long documentId);

    ResponseEntity<Resource> downloadDocument(Long documentId);

    List<DocumentResponse> getCustomerDocuments(Long customerId);

    List<DocumentResponse> getApplicationDocuments(Long applicationId);

    List<DocumentVersionResponse> getDocumentVersions(Long documentId);

    DocumentVersionResponse uploadNewVersion(Long documentId, MultipartFile file);

    DocumentResponse updateStatus(Long documentId, DocumentStatusUpdateRequest request);

    void deleteDocument(Long documentId);
}
