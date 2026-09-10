package com.capstone.document.controller;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.document.dto.DocumentResponse;
import com.capstone.document.dto.DocumentStatusUpdateRequest;
import com.capstone.document.dto.DocumentVersionResponse;
import com.capstone.document.entity.DocumentTypeMaster;
import com.capstone.document.enums.DocumentType;
import com.capstone.document.service.DocumentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/documents")
@CrossOrigin(origins = "*")
public class DocumentController {
    
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload document (supports multipart/form-data for Postman and UI).
     * Accepts customerId, applicationId, documentType, documentName (optional), and file.
     */
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @PostMapping(value = {"", "/upload"}, consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "customerId", required = false) String customerId,
            @RequestHeader(value = "X-Customer-Id", required = false) String customerHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "applicationId", required = false) String applicationId,
            @RequestParam("documentType") String documentTypeStr,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam("file") MultipartFile file) {

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(documentTypeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            docType = DocumentType.OTHER;
        }

        String effectiveCustomerId = resolveCustomerId(customerHeader, customerId, authorization);
        if (effectiveCustomerId == null || effectiveCustomerId.isBlank()) {
            throw new IllegalArgumentException("Customer identity must come from the authenticated gateway token.");
        }

        DocumentResponse response = documentService.uploadDocument(
            effectiveCustomerId,
                applicationId,
                docType,
                documentName,
                file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Lists master document types with mandatory flags and size limits.
     * Publicly accessible for customer & employee document upload forms.
     */
    @GetMapping("/types")
    public ResponseEntity<List<DocumentTypeMaster>> getDocumentTypes(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        
        return ResponseEntity.ok(documentService.getAllDocumentTypes());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long documentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        
        return ResponseEntity.ok(documentService.getDocument(documentId));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        return documentService.downloadDocument(documentId);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/{documentId}/sas-url")
    public ResponseEntity<String> getSecureDownloadUrl(@PathVariable Long documentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        return ResponseEntity.ok(documentService.getSecureDownloadUrl(documentId));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/customer/me")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments(
            @RequestHeader(value = "X-Customer-Id", required = false) String customerHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(documentService.getCustomerDocuments(resolveCustomerId(customerHeader, null, authorization)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<DocumentResponse>> getCustomerDocuments(@PathVariable String customerId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        return ResponseEntity.ok(documentService.getCustomerDocuments(customerId));
    }

    private String resolveCustomerId(String customerHeader, String requestCustomerId, String authorization) {
        if (customerHeader != null && !customerHeader.isBlank()) return customerHeader;
        if (requestCustomerId != null && !requestCustomerId.isBlank()) return requestCustomerId;
        try {
            String[] parts = authorization == null ? new String[0] : authorization.split(" ");
            if (parts.length == 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1].split("\\.")[1]), StandardCharsets.UTF_8);
                String marker = "\"customerId\":\"";
                int start = payload.indexOf(marker);
                if (start >= 0) {
                    int valueStart = start + marker.length();
                    int valueEnd = payload.indexOf('"', valueStart);
                    if (valueEnd > valueStart) return payload.substring(valueStart, valueEnd);
                }
            }
        } catch (RuntimeException ignored) {
            // Production identity is supplied by APIM; this fallback is for local browser testing.
        }
        throw new IllegalArgumentException("Customer identity must come from the authenticated gateway token.");
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/application/{applicationId}")
    public ResponseEntity<List<DocumentResponse>> getApplicationDocuments(@PathVariable String applicationId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
                
        return ResponseEntity.ok(documentService.getApplicationDocuments(applicationId));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @GetMapping("/{documentId}/versions")
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(@PathVariable Long documentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
                
        return ResponseEntity.ok(documentService.getDocumentVersions(documentId));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @PostMapping(value = "/{documentId}/versions", consumes = "multipart/form-data")
    public ResponseEntity<DocumentVersionResponse> uploadNewVersion(
            @PathVariable Long documentId, 
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam MultipartFile file) {

        DocumentVersionResponse response = documentService.uploadNewVersion(documentId, file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @PutMapping("/{documentId}/status")
    public ResponseEntity<DocumentResponse> updateStatus(
            @PathVariable Long documentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody DocumentStatusUpdateRequest request) {

        return ResponseEntity.ok(documentService.updateStatus(documentId, request));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_MANAGER')")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
                
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
