package com.bank.customerservice.config;

import com.bank.customerservice.entity.AppUser;
import com.bank.customerservice.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ensures a small pool of loan managers exists in the shared {@code Users} table
 * ({@code user_role = 'manager'}) so a customer's loan application can be
 * assigned to a real manager. Rows are inserted only when the {@code loginid} is
 * missing — any pre-existing users are left untouched.
 * <p>
 * Runs in every profile (the Flyway migrations that create the {@code Users}
 * table are not enabled in the {@code local} profile), and is idempotent.
 * Seeded managers can also sign in to the loan officer console with the password
 * below.
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class LoanManagerSeeder implements ApplicationRunner {

    /** { loginid (<=20), display name, email }. */
    private static final String[][] MANAGERS = {
            {"mgr1", "Manager One",   "manager1@bank.com"},
            {"mgr2", "Manager Two",   "manager2@bank.com"},
            {"mgr3", "Manager Three", "manager3@bank.com"},
            {"mgr4", "Manager Four",  "manager4@bank.com"},
            {"mgr5", "Manager Five",  "manager5@bank.com"},
    };
    private static final String DEFAULT_PASSWORD = "Password@123";
    private static final String MANAGER_ROLE = "manager";

    private final AppUserRepository appUserRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = 0;
            for (String[] m : MANAGERS) {
                if (appUserRepository.existsByLoginIdIgnoreCase(m[0])) {
                    continue;
                }
                appUserRepository.save(AppUser.builder()
                        .loginId(m[0])
                        .loginPassword(DEFAULT_PASSWORD)
                        .name(m[1])
                        .email(m[2])
                        .userRole(MANAGER_ROLE)
                        .build());
                created++;
            }
            long total = appUserRepository.findByUserRoleIgnoreCase(MANAGER_ROLE).stream()
                    .map(AppUser::getLoginId).distinct().count();
            log.info("Loan manager pool ready: {} new manager row(s) inserted, {} manager(s) total.", created, total);
        } catch (Exception ex) {
            // Never block startup on seeding; assignment will surface a clear error if the pool is empty.
            log.warn("Loan manager seeding note: {}", ex.getMessage());
        }
    }

    /** Exposed for documentation / tests. */
    public static List<String[]> managers() {
        return List.of(MANAGERS);
    }
}
