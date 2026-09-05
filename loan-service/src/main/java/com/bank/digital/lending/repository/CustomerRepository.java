package com.bank.digital.lending.repository;

import com.bank.digital.lending.model.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, java.util.UUID> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByMobileNumber(String mobileNumber);
}
