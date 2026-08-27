package com.bank.digital.lending.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.domain.AuditorAware;
import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class AuditingConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
        // In a real application, retrieve the current user from security context.
        // For this demo, we use a static placeholder.
        return () -> Optional.of("system");
    }
}
