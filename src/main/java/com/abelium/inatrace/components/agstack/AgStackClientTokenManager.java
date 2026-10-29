package com.abelium.inatrace.components.agstack;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.agstack.api.ApiLoginErrorResponse;
import com.abelium.inatrace.components.agstack.api.ApiLoginRequest;
import com.abelium.inatrace.components.agstack.api.ApiLoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgStackClientTokenManager {
    private static final long TOKEN_REFRESH_SECONDS = 3 * 3600;

    @Value("${INATrace.agstack.email}")
    private String email;

    @Value("${INATrace.agstack.password}")
    private String password;

    @Value("${INATrace.agstack.loginBaseURL}")
    private String baseURL;

    @Value("${INATrace.agstack.requestTimeoutMs:30000}")
    private long requestTimeoutMs = 30000;

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();
    private final Clock clock;

    public AgStackClientTokenManager() {
        this(Clock.systemUTC());
    }

    AgStackClientTokenManager(Clock clock) {
        this.clock = clock;
    }

    public String retrieveToken() {
        Instant now = clock.instant();
        CachedToken cached = cachedToken.get();
        if (shouldRefreshToken(cached, now)) {
            return refreshToken(cached, now);
        }
        return cached.token();
    }

    void invalidateToken(String rejectedToken) {
        cachedToken.updateAndGet(current -> current != null && current.token().equals(rejectedToken)
                ? null : current);
    }

    private String refreshToken(CachedToken previous, Instant instant) {
        String token = this.login(email, password).getAccessToken();
        if (token != null && !token.isBlank()) {
            if (!cachedToken.compareAndSet(previous, new CachedToken(token, instant))) {
                CachedToken current = cachedToken.get();
                return current != null ? current.token() : token;
            }
        }
        return token == null || token.isBlank() ? null : token;
    }

    private boolean shouldRefreshToken(CachedToken cached, Instant instant) {
        if (cached == null) {
            return true;
        }
        return Duration.between(cached.lastUpdated(), instant).toSeconds() > TOKEN_REFRESH_SECONDS;
    }

    private record CachedToken(String token, Instant lastUpdated) {}

    private ApiLoginResponse login(String username, String password) {
        ApiLoginRequest apiLoginRequest = new ApiLoginRequest();
        apiLoginRequest.setEmail(username);
        apiLoginRequest.setPassword(password);

        WebClient webClient = WebClient.create(baseURL);
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder.path("/login").build())
                .body(Mono.just(apiLoginRequest), ApiLoginRequest.class)
                .header("User-Agent", "Postman") // Fix for API to return JSON
                .header("X-FROM-ASSET-REGISTRY", "True")
                .header("Content-Type", "application/json")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        HttpStatus.INTERNAL_SERVER_ERROR::equals,
                        clientResponse -> clientResponse
                                .bodyToMono(ApiLoginErrorResponse.class)
                                .flatMap(error -> Mono.error(new ApiException(ApiStatus.ERROR, error.getMessage()))))
                .onStatus(HttpStatus.BAD_REQUEST::equals,
                        clientResponse -> clientResponse
                                .bodyToMono(ApiLoginErrorResponse.class)
                                .flatMap(error -> Mono.error(new ApiException(ApiStatus.INVALID_REQUEST, error.getMessage()))))
                .bodyToMono(ApiLoginResponse.class)
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .block();
    }
}
