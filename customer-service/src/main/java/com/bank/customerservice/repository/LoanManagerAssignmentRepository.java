package com.bank.customerservice.repository;

import com.bank.customerservice.entity.LoanManagerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanManagerAssignmentRepository extends JpaRepository<LoanManagerAssignment, UUID> {

    Optional<LoanManagerAssignment> findFirstByApplicationIdIgnoreCase(String applicationId);

    List<LoanManagerAssignment> findByCustomerIdOrderByAssignedAtDesc(UUID customerId);

    long countByManagerLogin(String managerLogin);
}
