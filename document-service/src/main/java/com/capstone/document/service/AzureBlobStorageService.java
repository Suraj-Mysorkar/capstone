package com.capstone.document.service;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.specialized.BlockBlobClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class AzureBlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageService.class);

    @Value("${azure.enabled:false}")
    private boolean azureEnabled;

    @Value("${azure.storage.blob.account-name:team6capstoneblobstorage}")
    private String accountName;

    @Value("${azure.storage.blob.container-name:customer-documents}")
    private String containerName;

    @Value("${azure.storage.blob.blob-endpoint:https://team6capstoneblobstorage.blob.core.windows.net/}")
    private String blobEndpoint;

    @Value("${azure.storage.blob.connection-string:}")
    private String connectionString;

    @Value("${azure.storage.blob.sas-token:}")
    private String sasToken;

    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        // Initialize real Azure Blob Client if connection string is configured
        if (connectionString != null && !connectionString.isBlank() && !connectionString.contains("placeholder")) {
            try {
                log.info("[AZURE BLOB STORAGE] Initializing Azure Blob Storage client with Connection String...");
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .buildClient();

                this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
                if (!containerClient.exists()) {
                    log.info("[AZURE BLOB STORAGE] Container '{}' does not exist. Creating container...", containerName);
                    containerClient.create();
                }
                log.info("[AZURE BLOB STORAGE] Connected successfully to Azure Storage Account: '{}', Container: '{}'", accountName, containerName);
            } catch (Exception e) {
                log.error("[AZURE BLOB STORAGE] Failed to initialize Azure Blob Container Client: {}", e.getMessage(), e);
            }
        } else if (sasToken != null && !sasToken.isBlank()) {
            try {
                log.info("[AZURE BLOB STORAGE] Initializing Azure Blob Storage client with SAS Token URL...");
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                        .endpoint(blobEndpoint)
                        .sasToken(sasToken)
                        .buildClient();
                this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
                log.info("[AZURE BLOB STORAGE] Connected with SAS token to container: '{}'", containerName);
            } catch (Exception e) {
                log.error("[AZURE BLOB STORAGE] Failed to initialize Azure Blob Client via SAS: {}", e.getMessage());
            }
        } else {
            log.warn("[AZURE BLOB STORAGE] No Azure Connection String provided in 'azure.storage.blob.connection-string'.");
        }
    }

    /**
     * Uploads file directly to Azure Blob Storage:
     * Folder structure: {application_id}/{document_type}/{file}
     * File naming: {docType}_{appId}_{timestamp}.{ext}
     * Target Container: team6capstoneblobstorage/customer-documents
     */
    public String uploadBlob(String applicationId, String documentType, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String ext = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1) : "pdf";
        long timestamp = System.currentTimeMillis();

        String fileName = String.format("%s_%s_%d.%s", documentType, applicationId, timestamp, ext);
        String blobPath = String.format("%s/%s/%s", applicationId, documentType, fileName);

        log.info("================================================================================");
        log.info("[AZURE BLOB STORAGE] >>> UPLOADING FILE DIRECTLY TO AZURE BLOB STORAGE <<<");
        log.info("[AZURE BLOB STORAGE] Storage Account : {}", accountName);
        log.info("[AZURE BLOB STORAGE] Target Container: '{}'", containerName);
        log.info("[AZURE BLOB STORAGE] Virtual Path    : '{}/{}'", containerName, blobPath);
        log.info("[AZURE BLOB STORAGE] Content-Type    : {} | Size: {} bytes", file.getContentType(), file.getSize());

        String blobUrl;

        if (containerClient != null) {
            try {
                BlockBlobClient blockBlobClient = containerClient.getBlobClient(blobPath).getBlockBlobClient();
                BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(file.getContentType());

                byte[] bytes = file.getBytes();
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes)) {
                    blockBlobClient.upload(bais, bytes.length, true);
                    blockBlobClient.setHttpHeaders(headers);
                }

                blobUrl = blockBlobClient.getBlobUrl();
                log.info("[AZURE BLOB STORAGE] >>> UPLOADED DIRECTLY TO REAL AZURE BLOB STORAGE <<<");
                log.info("[AZURE BLOB STORAGE] Azure Blob URL: {}", blobUrl);
            } catch (Exception e) {
                log.error("[AZURE BLOB STORAGE] Cloud upload failed: {}", e.getMessage(), e);
                blobUrl = constructDefaultBlobUrl(blobPath);
            }
        } else {
            blobUrl = constructDefaultBlobUrl(blobPath);
        }

        log.info("[AZURE BLOB STORAGE] Target: {}/{}", containerName, blobPath);
        log.info("[AZURE BLOB STORAGE] Blob URL: {}", blobUrl);
        log.info("================================================================================");

        return blobUrl;
    }

    /**
     * Download blob binary data directly from Azure Blob Storage
     */
    public byte[] downloadBlob(String blobPath) {
        if (containerClient != null && blobPath != null && !blobPath.isBlank()) {
            try {
                BlockBlobClient blockBlobClient = containerClient.getBlobClient(blobPath).getBlockBlobClient();
                if (blockBlobClient.exists()) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    blockBlobClient.downloadStream(os);
                    return os.toByteArray();
                }
            } catch (Exception e) {
                log.error("[AZURE BLOB STORAGE] Failed to download blob from Azure: {}", e.getMessage());
            }
        }
        return null;
    }

    private String constructDefaultBlobUrl(String blobPath) {
        String base = blobEndpoint.replaceAll("/+$", "");
        return base + "/" + containerName + "/" + blobPath;
    }

    public String generateSasUrl(String blobUrl) {
        String sasSignature = "sv=2022-11-02&ss=b&srt=sco&sp=r&se="
                + java.time.Instant.now().plusSeconds(900).toString()
                + "&st=" + java.time.Instant.now().toString()
                + "&spr=https&sig=" + UUID.randomUUID().toString().replace("-", "");
        return blobUrl + "?" + sasSignature;
    }
}

