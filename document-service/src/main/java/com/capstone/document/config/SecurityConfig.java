package com.capstone.document.config;

import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
@EnableWebSecurity
@EnableMethodSecurity // Allows you to use @PreAuthorize annotations on your controllers
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Create the filter that captures the headers injected by Azure APIM
        RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();
        filter.setCredentialsRequestHeader("X-User-Role"); // Used to pull the security role
        filter.setPrincipalRequestHeader("X-User-Id"); // Used as the main username/principal
        filter.setAuthenticationManager(authenticationManager());

        filter.setExceptionIfHeaderMissing(false);

        // Tell the filter to bypass Swagger UI, API Docs, and document types catalog
        filter.setRequiresAuthenticationRequestMatcher(new NegatedRequestMatcher(
            new OrRequestMatcher(
                new AntPathRequestMatcher("/v3/api-docs/**"),
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/swagger-ui.html"),
                new AntPathRequestMatcher("/api/v1/documents/types"),
                new AntPathRequestMatcher("/types")
            )
        ));

        http
            .csrf(csrf -> csrf.disable())
            .addFilter(filter) 
            .authorizeHttpRequests(auth -> auth
                // Allow public access to Swagger and document types catalog
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api/v1/documents/types",
                    "/types"
                ).permitAll() 
                // Everything else requires the APIM headers
                .anyRequest().authenticated() 
            );

        return http.build();
    }

    @Bean
    public ProviderManager authenticationManager() {
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        
        // Maps the text inside the "X-User-Role" header straight into Spring Security Granted Authorities
        provider.setPreAuthenticatedUserDetailsService(token -> {
            String username = (String) token.getPrincipal();
            String role = (String) token.getCredentials(); // Will contain "ROLE_CUSTOMER" or "ROLE_MANAGER"
            
            if (role == null || role.isBlank()) {
                role = "ROLE_ANONYMOUS";
            } else if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }

            List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
            
            return new org.springframework.security.core.userdetails.User(
                username != null ? username : "anonymous", "", true, true, true, true, authorities
            );
        });

        return new ProviderManager(Collections.singletonList(provider));
    }
}
