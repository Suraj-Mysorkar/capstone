package com.capstone.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentNotificationDTO {

    private String documentId;
    private String applicationId;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private String email;
    private String documentName;
    private String documentType;
    private String blobUrl;
    private String status;
    private String remarks;
    private String verifiedBy;

    public DocumentNotificationDTO() {}

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName != null ? customerName : "Valued Customer";
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail != null ? customerEmail : email;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getEmail() {
        return email != null ? email : customerEmail;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDocumentName() {
        return documentName != null ? documentName : (documentType != null ? documentType : "Document");
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getBlobUrl() {
        return blobUrl;
    }

    public void setBlobUrl(String blobUrl) {
        this.blobUrl = blobUrl;
    }

    public String getStatus() {
        return status != null ? status : "UPLOADED";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getVerifiedBy() {
        return verifiedBy != null ? verifiedBy : "Operations Manager";
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }
}
