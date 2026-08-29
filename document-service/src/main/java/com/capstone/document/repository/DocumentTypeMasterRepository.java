package com.capstone.document.repository;

import com.capstone.document.entity.DocumentTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentTypeMasterRepository extends JpaRepository<DocumentTypeMaster, Long> {
    Optional<DocumentTypeMaster> findByTypeCodeIgnoreCase(String typeCode);
    boolean existsByTypeCodeIgnoreCase(String typeCode);
}
