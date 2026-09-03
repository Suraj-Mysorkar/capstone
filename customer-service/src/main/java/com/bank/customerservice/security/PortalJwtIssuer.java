package com.bank.customerservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Issues a compact HS256 JWT for the customer self-service portal.
 *
 * This mirrors the token the employee login (APIM + user-validator) hands the
 * loan officer console: the front-end stores it in localStorage and sends it as
 * {@code Authorization: Bearer <token>}. It is not validated by loan-service /
 * document-service in the local setup; it exists so the portal's auth gate and
 * role claims work the same way as capstone-ui.
 */
@Component
public class PortalJwtIssuer {

    private final byte[] secret;
    private final long ttlSeconds;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public PortalJwtIssuer(
            @Value("${app.portal.jwt.secret:customer-service-portal-dev-secret-change-me}") String secret,
            @Value("${app.portal.jwt.ttl-seconds:86400}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String subject, String name, String role, Object userId, String email, String customerId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);
        claims.put("preferred_username", subject);
        claims.put("name", name);
        claims.put("roles", "ROLE_" + (role == null ? "customer" : role));
        claims.put("role", role == null ? "customer" : role);
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("customerId", customerId);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());
        claims.put("iss", "customer-service-portal");

        try {
            String header = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = B64.encodeToString(mapper.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            String signature = B64.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue portal JWT", e);
        }
    }

    private byte[] hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
