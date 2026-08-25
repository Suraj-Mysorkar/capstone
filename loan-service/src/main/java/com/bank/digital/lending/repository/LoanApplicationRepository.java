package com.bank.digital.lending.repository;

import com.bank.digital.lending.model.entity.LoanApplication;
import com.bank.digital.lending.model.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String> {
    List<LoanApplication> findByStatus(LoanStatus status);
    List<LoanApplication> findByCustomerId(String customerId);
    List<LoanApplication> findAllByOrderByCreatedAtDesc();
}
