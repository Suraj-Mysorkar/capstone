package com.bank.digital.lending.repository;

import com.bank.digital.lending.model.entity.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanDocumentRepository extends JpaRepository<LoanDocument, String> {
    List<LoanDocument> findByApplicationId(String applicationId);
    List<LoanDocument> findByCustomerId(String customerId);
}
