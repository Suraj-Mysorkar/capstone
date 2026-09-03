package com.bank.customerservice.repository;

import com.bank.customerservice.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByLoginIdIgnoreCase(String loginId);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByLoginIdIgnoreCase(String loginId);
    boolean existsByEmailIgnoreCase(String email);
}
