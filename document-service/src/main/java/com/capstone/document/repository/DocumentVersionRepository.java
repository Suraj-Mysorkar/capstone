package com.capstone.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.document.entity.DocumentVersion;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    
    List<DocumentVersion> findByDocumentDocumentId(Long documentId);

    // Find the document version with the highest version number.
    Optional<DocumentVersion>
    findTopByDocumentDocumentIdOrderByVersionNumberDesc(Long documentId);
}
