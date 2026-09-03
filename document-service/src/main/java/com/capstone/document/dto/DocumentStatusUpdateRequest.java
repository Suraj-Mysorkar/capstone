package com.capstone.document.dto;

import com.capstone.document.enums.DocumentStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatusUpdateRequest {
    
    @NotNull(message = "Status is required")
    private DocumentStatus status;

    private String remarks;

    private String verifiedBy;

    private String customerEmail;

    public DocumentStatusUpdateRequest(DocumentStatus status, String remarks) {
        this.status = status;
        this.remarks = remarks;
    }
}
