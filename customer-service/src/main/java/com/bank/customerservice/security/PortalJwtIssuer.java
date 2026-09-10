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

    private final String issuer;
    private final String audience;

    public PortalJwtIssuer(
            @Value("${app.portal.jwt.secret:YourSuperLongAndSecureSecretKeyThatIsAtLeast256BitsLong!!}") String secret,
            @Value("${app.portal.jwt.ttl-seconds:86400}") long ttlSeconds,
            @Value("${app.portal.jwt.issuer:https://azure-api.net}") String issuer,
            @Value("${app.portal.jwt.audience:7f273f15-d6cd-40d7-8aa8-f39d1fb9406f}") String audience) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(String subject, String name, String role, Object userId, String email, String customerId) {
        Instant now = Instant.now();
        String roleStr = (role == null ? "CUSTOMER" : role.toUpperCase().replace("ROLE_", ""));
        String roleClaim = "ROLE_" + roleStr;

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());
        claims.put("iat", now.getEpochSecond());
        claims.put("sub", subject);
        claims.put("preferred_username", subject);
        claims.put("userId", userId != null ? String.valueOf(userId) : "0");
        claims.put("name", name != null ? name : "anonymous");
        claims.put("roles", roleClaim);
        claims.put("role", roleStr.toLowerCase());
        claims.put("email", email != null ? email : "");
        claims.put("customerId", customerId != null ? customerId : "");

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
