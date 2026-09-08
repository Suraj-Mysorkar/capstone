package com.capstone.document.controller;

import java.util.List;

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
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @PostMapping(value = {"", "/upload"}, consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name,   // Reads the extracted claim passed by APIM
            @RequestParam("customerId") String customerId,
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

        DocumentResponse response = documentService.uploadDocument(
                customerId,
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
     */
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/types")
    public ResponseEntity<List<DocumentTypeMaster>> getDocumentTypes(
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
        
        return ResponseEntity.ok(documentService.getAllDocumentTypes());
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
        
        return ResponseEntity.ok(documentService.getDocument(documentId));
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {

        return documentService.downloadDocument(documentId);
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/{documentId}/sas-url")
    public ResponseEntity<String> getSecureDownloadUrl(@PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {

        return ResponseEntity.ok(documentService.getSecureDownloadUrl(documentId));
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<DocumentResponse>> getCustomerDocuments(@PathVariable String customerId,
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {

        return ResponseEntity.ok(documentService.getCustomerDocuments(customerId));
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/application/{applicationId}")
    public ResponseEntity<List<DocumentResponse>> getApplicationDocuments(@PathVariable String applicationId,
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
                
        return ResponseEntity.ok(documentService.getApplicationDocuments(applicationId));
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @GetMapping("/{documentId}/versions")
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(@PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId,     // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
                
        return ResponseEntity.ok(documentService.getDocumentVersions(documentId));
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @PostMapping(value = "/{documentId}/versions", consumes = "multipart/form-data")
    public ResponseEntity<DocumentVersionResponse> uploadNewVersion(
            @PathVariable Long documentId, 
            @RequestHeader("X-User-Id") Long userId, // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name,
            @RequestParam MultipartFile file) {

        DocumentVersionResponse response = documentService.uploadNewVersion(documentId, file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @PutMapping("/{documentId}/status")
    public ResponseEntity<DocumentResponse> updateStatus(
            @PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId, // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name,
            @Valid @RequestBody DocumentStatusUpdateRequest request) {

        return ResponseEntity.ok(documentService.updateStatus(documentId, request));
    }

    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_MANAGER', 'EMPLOYEE', 'MANAGER')")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId, // Reads the extracted claim passed by APIM
            @RequestHeader("X-User-Role") String name) {
                
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
