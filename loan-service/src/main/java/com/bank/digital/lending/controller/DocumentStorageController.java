package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.DocumentUploadResponse;
import com.bank.digital.lending.model.enums.DocType;
import com.bank.digital.lending.service.DocumentStorageProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/loans/documents")
@Tag(name = "Document Storage Proxy", description = "Endpoints for uploading KYC and supporting loan documents to Azure Blob Storage")
@CrossOrigin(origins = "*")
public class DocumentStorageController {

    private final DocumentStorageProxyService documentStorageProxy;

    public DocumentStorageController(DocumentStorageProxyService documentStorageProxy) {
        this.documentStorageProxy = documentStorageProxy;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload KYC or Income Document",
               description = "Directly ingests documents into Azure Blob Storage (or local storage mirror in mock mode)")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("customerId") String customerId,
            @RequestParam(value = "applicationId", required = false) String applicationId,
            @RequestParam("docType") DocType docType,
            @RequestPart("file") MultipartFile file) throws IOException {

        DocumentUploadResponse response = documentStorageProxy.uploadDocument(
                customerId, applicationId, docType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Document Metadata by ID",
               description = "Retrieves uploaded document metadata and secure Blob storage URI")
    public ResponseEntity<DocumentUploadResponse> getDocument(@PathVariable("id") String id) {
        return documentStorageProxy.getDocument(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
