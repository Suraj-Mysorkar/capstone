package com.bank.digital.lending.model.dto;

import java.time.LocalDateTime;

public class NotificationDTO {
    private String id;
    private String recipientUsername;
    private String title;
    private String message;
    private String eventType; // NEW_CASE_ASSIGNED, DOCUMENT_UPLOADED, NEW_LOAN_APPLICATION, DECISION_PROCESSED
    private String customerId;
    private String customerName;
    private String applicationId;
    private LocalDateTime timestamp;
    private boolean isRead;

    public NotificationDTO() {
        this.id = "NOTIF-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }

    public NotificationDTO(String recipientUsername, String title, String message, String eventType,
                           String customerId, String customerName, String applicationId) {
        this();
        this.recipientUsername = recipientUsername;
        this.title = title;
        this.message = message;
        this.eventType = eventType;
        this.customerId = customerId;
        this.customerName = customerName;
        this.applicationId = applicationId;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
