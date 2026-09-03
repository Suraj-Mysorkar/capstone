package com.bank.customerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Default: validates JWTs issued by Microsoft Entra ID (Security &amp; Identity
 * layer). Trusts tokens forwarded by Azure API Management after APIM's own
 * centralized JWT validation; this service validates independently
 * (defense-in-depth). Roles/scopes map to Entra ID app roles ("customer_admin")
 * and delegated scopes ("customers.read", "customers.write").
 * <p>
 * With the <b>local</b> Spring profile the API is opened up (no token required,
 * permissive CORS) so it can run against embedded H2 with no Entra tenant — see
 * {@code application-local.yml}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**", "/actuator/info", "/api/customers/ping",
            "/api/customers/auth/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/h2-console/**"
    };

    /** Authorities granted to every request under the "local" profile. */
    private static final String[] LOCAL_AUTHORITIES = {
            "SCOPE_customers.read", "SCOPE_customers.write", "ROLE_customer_admin"
    };

    /**
     * Browser origins allowed to call this API directly (the self-service portal
     * SWA). Comma-separated; "*" allows any. Set {@code APP_CORS_ALLOWED_ORIGINS}
     * to the Static Web App origin(s) in production. When APIM fronts this
     * service it can own CORS instead — a permissive value here is still safe
     * because no cookies / credentials are used.
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    // ── Default profile: Entra ID JWT resource server ────────────────────────
    @Bean
    @Profile("!local")
    SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless API behind APIM; no browser form submissions
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Entra ID JWT decoder.
     * <p>
     * For a concrete single-tenant issuer (…/{tenant-guid}/v2.0) this uses OIDC
     * discovery with strict issuer validation. For the multi-tenant endpoints
     * (…/common/v2.0, …/organizations/v2.0) OIDC discovery returns a templated
     * issuer that fails strict validation, so we build the decoder straight from
     * the JWKS endpoint and validate the issuer with a loose "must look like an
     * Entra tenant issuer" check instead. Set {@code JWT_ISSUER_URI} to a
     * concrete tenant issuer in production to get strict validation.
     */
    @Bean
    @Profile("!local")
    JwtDecoder jwtDecoder(@Value("${security.jwt.issuer-uri}") String issuerUri) {
        String normalized = issuerUri.replaceAll("/+$", "");
        boolean multiTenant = normalized.endsWith("/common/v2.0")
                || normalized.endsWith("/organizations/v2.0")
                || normalized.endsWith("/consumers/v2.0");

        if (!multiTenant) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }

        String base = normalized.substring(0, normalized.lastIndexOf("/v2.0"));
        org.springframework.security.oauth2.jwt.NimbusJwtDecoder decoder =
                org.springframework.security.oauth2.jwt.NimbusJwtDecoder
                        .withJwkSetUri(base + "/discovery/v2.0/keys")
                        .build();
        decoder.setJwtValidator(token -> {
            String iss = token.getIssuer() == null ? "" : token.getIssuer().toString();
            boolean ok = iss.startsWith("https://login.microsoftonline.com/")
                    || iss.startsWith("https://sts.windows.net/");
            return ok
                    ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_issuer",
                            "Issuer is not a Microsoft Entra ID issuer: " + iss, null));
        });
        return decoder;
    }

    // ── local profile: open API for a zero-infrastructure demo ───────────────
    @Bean
    @Profile("local")
    SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .headers(headers -> headers.frameOptions(frame -> frame.disable())) // H2 console
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // give unauthenticated callers the scopes/role so @PreAuthorize passes
            .anonymous(anon -> anon.authorities(LOCAL_AUTHORITIES));

        return http.build();
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

    /**
     * Maps Entra ID's `roles` claim (app roles) and `scp` claim (delegated
     * scopes) onto Spring Security authorities: ROLE_xxx and SCOPE_xxx.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthorityPrefix("SCOPE_");
        scopesConverter.setAuthoritiesClaimName("scp");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopesConverter.convert(jwt));

            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }
}
