package com.bank.customerservice.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Role-based authorization driven by the identity headers Azure API Management
 * injects after it validates the caller's token / subscription key — the same
 * pattern used by {@code document-service} and {@code report-service}.
 *
 * <ul>
 *   <li>{@code X-User-Id}   &rarr; the authenticated principal</li>
 *   <li>{@code X-User-Role} &rarr; a single granted authority
 *       ({@code ROLE_CUSTOMER}, {@code ROLE_EMPLOYEE} or {@code ROLE_MANAGER})</li>
 * </ul>
 *
 * Method-level {@code @PreAuthorize} on the controllers decides who may call
 * what. Public endpoints (portal register / login, health, ping, Swagger) need
 * no headers; {@code POST /api/customers/loan-manager-assignments} is a
 * server-to-server call from loan-service and is also left open.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Reachable without APIM identity headers. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**", "/actuator/info",
            "/api/customers/ping",
            "/api/customers/auth/**",                       // portal register / login — no token yet
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/h2-console/**"
    };

    /**
     * Browser origins allowed to call this API directly (the self-service portal
     * Static Web App). Comma-separated; {@code *} allows any. Set
     * {@code APP_CORS_ALLOWED_ORIGINS} to the SWA origin(s) in production. Safe
     * to leave permissive here because no cookies / credentials are used.
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Captures the identity headers injected by Azure APIM.
        RequestHeaderAuthenticationFilter apimHeaderFilter = new RequestHeaderAuthenticationFilter();
        apimHeaderFilter.setPrincipalRequestHeader("X-User-Id");
        apimHeaderFilter.setCredentialsRequestHeader("X-User-Role");
        apimHeaderFilter.setAuthenticationManager(authenticationManager());
        // Public endpoints are reached without the headers — don't 500 when they're absent.
        apimHeaderFilter.setExceptionIfHeaderMissing(false);

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 console
            .addFilter(apimHeaderFilter)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    // loan-service -> customer-service, server-to-server (no user context)
                    .requestMatchers(HttpMethod.POST, "/api/customers/loan-manager-assignments").permitAll()
                    .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public ProviderManager authenticationManager() {
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();

        // Maps the value of "X-User-Role" straight into a Spring Security authority.
        provider.setPreAuthenticatedUserDetailsService(token -> {
            String username = String.valueOf(token.getPrincipal());
            Object credentials = token.getCredentials();
            String role = credentials == null ? "" : credentials.toString().trim();
            if (role.isEmpty()) {
                throw new BadCredentialsException("Missing X-User-Role header");
            }
            List<SimpleGrantedAuthority> authorities =
                    Collections.singletonList(new SimpleGrantedAuthority(role));
            return new User(username, "", true, true, true, true, authorities);
        });

        return new ProviderManager(provider);
    }

    private CorsConfigurationSource corsSource() {
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
        return source;
    }
}
