package com.bank.digital.lending.repository;

import com.bank.digital.lending.model.entity.LoanAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanAuditLogRepository extends JpaRepository<LoanAuditLog, Long> {
    List<LoanAuditLog> findByApplicationIdOrderByTimestampAsc(String applicationId);
}
