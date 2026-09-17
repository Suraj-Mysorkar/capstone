package com.bank.digital.lending.repository;

import com.bank.digital.lending.model.entity.LoanDocument;
import com.bank.digital.lending.model.enums.DocType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanDocumentRepository extends JpaRepository<LoanDocument, String> {
    List<LoanDocument> findByApplicationId(String applicationId);
    List<LoanDocument> findByCustomerId(String customerId);
    Optional<LoanDocument> findByApplicationIdAndDocType(String applicationId, DocType docType);
}
