package com.bank.customerservice.repository;

import com.bank.customerservice.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findFirstByLoginIdIgnoreCase(String loginId);
    Optional<AppUser> findFirstByEmailIgnoreCase(String email);
    Optional<AppUser> findByLoginIdIgnoreCase(String loginId);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByLoginIdIgnoreCase(String loginId);
    boolean existsByEmailIgnoreCase(String email);

    /** The pool of loan managers used for auto-assignment ({@code user_role = 'manager'}). */
    List<AppUser> findByUserRoleIgnoreCase(String userRole);
}
