package com.capstone.document.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.document.dto.DocumentResponse;
import com.capstone.document.dto.DocumentStatusUpdateRequest;
import com.capstone.document.dto.DocumentVersionResponse;
import com.capstone.document.enums.DocumentType;
import com.capstone.document.service.DocumentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam Long customerId,
            @RequestParam(required = false)
            Long applicationId,
            @RequestParam DocumentType documentType,
            @RequestParam MultipartFile file) {

        DocumentResponse response =
                documentService.uploadDocument(
                        customerId,
                        applicationId,
                        documentType,
                        file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable Long documentId) {

        return ResponseEntity.ok(documentService.getDocument(documentId));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long documentId) {

        return documentService.downloadDocument(documentId);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<DocumentResponse>> getCustomerDocuments(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(documentService.getCustomerDocuments(customerId));
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<List<DocumentResponse>> getApplicationDocuments(
            @PathVariable Long applicationId) {

        return ResponseEntity.ok(documentService.getApplicationDocuments(applicationId));
    }

    @GetMapping("/{documentId}/versions")
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(
            @PathVariable Long documentId) {

        return ResponseEntity.ok(documentService.getDocumentVersions(documentId));
    }

    @PostMapping(value = "/{documentId}/versions",consumes = "multipart/form-data")
    public ResponseEntity<DocumentVersionResponse> uploadNewVersion(
            @PathVariable Long documentId, 
            @RequestParam MultipartFile file) {

        DocumentVersionResponse response =
                documentService.uploadNewVersion(
                        documentId,
                        file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{documentId}/status")
    public ResponseEntity<DocumentResponse> updateStatus(
            @PathVariable Long documentId,
            @Valid
            @RequestBody DocumentStatusUpdateRequest request) {

        return ResponseEntity.ok(
                documentService.updateStatus(
                        documentId,
                        request));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId) {

        documentService.deleteDocument(documentId);

        return ResponseEntity.noContent()
                .build();
    }
}
