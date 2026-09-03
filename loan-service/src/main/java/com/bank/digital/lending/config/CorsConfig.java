package com.bank.digital.lending.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Global CORS for the loan service.
 * <p>
 * The per-controller {@code @CrossOrigin} annotations are unreliable once any
 * other CORS configuration exists in the deployed environment. This filter is
 * the single source of truth: it runs before Spring MVC and before security, so
 * browser pre-flight (OPTIONS) from the customer portal / officer console
 * always succeeds.
 * <p>
 * Origins come from {@code APP_CORS_ALLOWED_ORIGINS} (comma-separated). The
 * default {@code *} allows any origin — safe here because no cookies /
 * credentials are used. In production set it to the Static Web App origin(s).
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        cfg.setAllowedOriginPatterns(origins.isEmpty() ? List.of("*") : origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Location"));
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
