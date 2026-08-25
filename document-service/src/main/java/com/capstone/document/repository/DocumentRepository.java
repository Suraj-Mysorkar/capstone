package com.capstone.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.document.entity.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    List<Document> findByCustomerId(Long customerId);

    List<Document> findByApplicationId(Long applicationId);

    List<Document> findByCustomerIdAndApplicationId(Long customerId, Long applicationId);
}
