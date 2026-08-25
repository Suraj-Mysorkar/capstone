package com.capstone.document.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionResponse {
    
    private Long versionId;

    private Long documentId;

    private Integer versionNumber;

    private String fileName;

    private String contentType;

    private Long fileSize;

    private LocalDateTime createdAt;
}
