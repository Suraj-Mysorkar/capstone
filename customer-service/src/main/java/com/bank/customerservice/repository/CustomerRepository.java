package com.bank.customerservice.repository;

import com.bank.customerservice.entity.Customer;
import com.bank.customerservice.entity.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmailIgnoreCase(String email);

    Optional<Customer> findByIdentityProviderSubject(String identityProviderSubject);

    Page<Customer> findByOnboardingStatus(OnboardingStatus status, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);
}
