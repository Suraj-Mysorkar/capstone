package com.bank.customerservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row in the shared {@code Users} table — the same table the loan officer
 * console (capstone-ui) authenticates against via the user-validator function.
 * <p>
 * The customer self-service portal writes a row here at registration with
 * {@code user_role = 'customer'}. Note the shared table's {@code loginid} column
 * is narrow (NVARCHAR(20)); it holds a short generated handle. Customers sign in
 * with their <b>email</b>, matched against the wider {@code email} column
 * ({@link com.bank.customerservice.repository.AppUserRepository#findByEmailIgnoreCase}).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "User_ID")
    private Long userId;

    /** Short generated handle (<= 20 chars) — the narrow shared column. */
    @Column(name = "loginid", nullable = false, length = 20)
    private String loginId;

    @Column(name = "login_password", nullable = false, length = 255)
    private String loginPassword;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "user_role", length = 40)
    private String userRole;

    @Column(name = "customer_id")
    private java.util.UUID customerId;

    @Column(name = "manager_id")
    private java.util.UUID managerId;
}
