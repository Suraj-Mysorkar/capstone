package com.capstone.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.document.entity.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    List<Document> findByCustomerId(String customerId);

    List<Document> findByApplicationId(String applicationId);

    List<Document> findByCustomerIdAndApplicationId(String customerId, String applicationId);
}
