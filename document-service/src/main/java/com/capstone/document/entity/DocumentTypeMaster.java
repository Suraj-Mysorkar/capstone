package com.capstone.document.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTypeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "type_code", nullable = false, unique = true, length = 50)
    private String typeCode;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory;

    @Column(name = "max_size_mb", nullable = false)
    private Integer maxSizeMb = 10;

    @Column(name = "allowed_extensions", length = 100)
    private String allowedExtensions = "pdf,jpg,jpeg,png";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
