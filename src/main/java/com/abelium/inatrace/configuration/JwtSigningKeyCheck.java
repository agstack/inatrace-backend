package com.abelium.inatrace.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Checks the JWT signing key when the application starts. TokenService is lazy, so without this
 * check a missing key only shows up at the first login, and a short or example key is never
 * reported at all. Tokens are signed with HMAC-SHA256, which needs a key of at least 256 bits.
 */
@Configuration
public class JwtSigningKeyCheck {

    static final int MIN_KEY_BYTES = 32;

    @Value("${INATrace.auth.jwtSigningKey:}")
    private String jwtSigningKey;

    @PostConstruct
    void verify() {
        validate(jwtSigningKey);
    }

    static void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "INATrace.auth.jwtSigningKey is not set. Set it to a random secret of at least "
                            + MIN_KEY_BYTES + " bytes, for example the output of `openssl rand -base64 48`.");
        }
        if (key.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "INATrace.auth.jwtSigningKey is too short for HMAC-SHA256: use a random secret of at least "
                            + MIN_KEY_BYTES + " bytes, for example the output of `openssl rand -base64 48`.");
        }
    }
}
