package com.bank.digital.lending.repository;

import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanSchemeRepository extends JpaRepository<LoanScheme, String> {
    List<LoanScheme> findByIsActiveTrue();
    Optional<LoanScheme> findByLoanTypeAndIsActiveTrue(LoanType loanType);
}
