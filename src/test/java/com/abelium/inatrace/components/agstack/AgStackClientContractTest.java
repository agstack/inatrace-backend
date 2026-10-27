package com.abelium.inatrace.components.agstack;

import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class AgStackClientContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger boundaries = new AtomicInteger();
    private final AtomicReference<String> loginBody = new AtomicReference<>();
    private final AtomicReference<String> boundaryBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> registryHeader = new AtomicReference<>();
    private final MutableClock clock = new MutableClock();
    private volatile int loginStatus = 200;
    private volatile String loginResponse = "{\"access_token\":\"synthetic-token\"}";
    private volatile int boundaryStatus = 200;
    private volatile String boundaryResponse = "{\"Geo Id\":\"geo-123\"}";
    private volatile boolean closeBoundarySocket;
    private HttpServer server;
    private ExecutorService serverExecutor;
    private volatile CountDownLatch loginArrivals;
    private volatile CountDownLatch releaseLogin;
    private volatile CountDownLatch boundaryArrived;
    private volatile CountDownLatch releaseBoundary;
    private AgStackClientTokenManager tokens;
    private AgStackClientService client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/login", exchange -> {
            logins.incrementAndGet();
            loginBody.set(readBody(exchange));
            if (loginArrivals != null) {
                loginArrivals.countDown();
                try {
                    if (!releaseLogin.await(5, TimeUnit.SECONDS)) throw new IOException("login gate timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            respond(exchange, loginStatus, loginResponse);
        });
        server.createContext("/register-field-boundary", exchange -> {
            boundaries.incrementAndGet();
            boundaryBody.set(readBody(exchange));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            registryHeader.set(exchange.getRequestHeaders().getFirst("X-FROM-ASSET-REGISTRY"));
            if (closeBoundarySocket) {
                exchange.close();
                return;
            }
            if (boundaryArrived != null) {
                boundaryArrived.countDown();
                try {
                    if (!releaseBoundary.await(5, TimeUnit.SECONDS)) throw new IOException("boundary gate timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            respond(exchange, boundaryStatus, boundaryResponse);
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        tokens = new AgStackClientTokenManager(clock);
        ReflectionTestUtils.setField(tokens, "baseURL", url);
        ReflectionTestUtils.setField(tokens, "email", "synthetic@example.test");
        ReflectionTestUtils.setField(tokens, "password", "synthetic-password");
        client = new AgStackClientService(tokens);
        ReflectionTestUtils.setField(client, "baseURL", url);
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test
    void loginIsCachedUntilMoreThanThreeWholeHoursHaveElapsed() throws Exception {
        assertEquals("synthetic-token", tokens.retrieveToken());
        assertEquals("synthetic-token", tokens.retrieveToken());
        JsonNode request = json.readTree(loginBody.get());
        assertEquals("synthetic@example.test", request.path("email").asText());
        assertEquals("synthetic-password", request.path("password").asText());
        assertEquals(1, logins.get());

        clock.instant = clock.instant.plusSeconds(3 * 3600);
        tokens.retrieveToken();
        assertEquals(1, logins.get());
        clock.instant = clock.instant.plusMillis(999);
        tokens.retrieveToken();
        assertEquals(1, logins.get());
        clock.instant = clock.instant.plusMillis(1);
        tokens.retrieveToken();
        assertEquals(2, logins.get());
    }

    @Test
    void failedOrMissingTokenDoesNotPreventNextLogin() {
        loginResponse = "{}";
        assertNull(tokens.retrieveToken());
        loginResponse = "{\"access_token\":\"recovered-token\"}";
        assertEquals("recovered-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @Test
    void blankTokenIsNotCached() {
        loginResponse = "{\"access_token\":\"   \"}";
        assertNull(tokens.retrieveToken());
        loginResponse = "{\"access_token\":\"recovered-token\"}";
        assertEquals("recovered-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @Test
    void malformedLoginResponseDoesNotPreventRecovery() {
        loginResponse = "not-json";
        assertThrows(RuntimeException.class, tokens::retrieveToken);
        loginResponse = "{\"access_token\":\"recovered-token\"}";
        assertEquals("recovered-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @Test
    void emptyLoginBodyFailsButNextLoginRecovers() {
        loginResponse = "";
        assertThrows(RuntimeException.class, tokens::retrieveToken);
        loginResponse = "{\"access_token\":\"recovered-token\"}";
        assertEquals("recovered-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @Test
    void rejectedLoginDoesNotPopulateTheCache() {
        loginStatus = 401;
        loginResponse = "{\"message\":\"synthetic-login-error\"}";
        assertThrows(RuntimeException.class, tokens::retrieveToken);
        loginStatus = 200;
        loginResponse = "{\"access_token\":\"recovered-token\"}";
        assertEquals("recovered-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void providerLoginErrorsDoNotPoisonTheCache(int status) {
        loginStatus = status;
        loginResponse = "{\"message\":\"synthetic-login-error\"}";
        assertThrows(RuntimeException.class, tokens::retrieveToken);
        loginStatus = 200;
        loginResponse = "{\"access_token\":\"recovered-token\"}";
        assertEquals("recovered-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @Test
    void concurrentFirstCallsCanPerformIndependentLogins() throws Exception {
        loginArrivals = new CountDownLatch(2);
        releaseLogin = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(tokens::retrieveToken);
            var second = callers.submit(tokens::retrieveToken);
            assertTrue(loginArrivals.await(5, TimeUnit.SECONDS), "both logins should overlap");
            releaseLogin.countDown();
            assertEquals("synthetic-token", first.get(5, TimeUnit.SECONDS));
            assertEquals("synthetic-token", second.get(5, TimeUnit.SECONDS));
            assertEquals(2, logins.get());
        } finally {
            releaseLogin.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void realLoginAndBoundaryUseExpectedContract() throws Exception {
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
        JsonNode request = json.readTree(boundaryBody.get());
        assertEquals("8, 13", request.path("s2_index").asText());
        assertEquals("POLYGON ((30.0 -1.0, 31.0 -1.0, 31.0 -2.0, 30.0 -1.0))", request.path("wkt").asText());
        assertEquals("Bearer synthetic-token", authorization.get());
        assertEquals("True", registryHeader.get());
        assertEquals(1, logins.get());
        assertEquals(1, boundaries.get());
    }

    @Test
    void matchedGeoIdsInBadRequestRemainAvailable() throws Exception {
        boundaryStatus = 400;
        boundaryResponse = "{\"matched geo ids\":[\"existing-geo\"],\"message\":\"matched\"}";
        assertEquals(List.of("existing-geo"), client.registerFieldBoundaryResponse(coordinates()).getMatchedGeoIDs());
    }

    @Test
    void badRequestWithoutMatchesIsReturnedWithoutGeoId() throws Exception {
        boundaryStatus = 400;
        boundaryResponse = "{\"message\":\"synthetic-rejection\"}";
        var response = client.registerFieldBoundaryResponse(coordinates());
        assertNull(response.getGeoID());
        assertNull(response.getMatchedGeoIDs());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void emptyProviderErrorBodyIsAnExplicitFailureAndCanRecover(int status) throws Exception {
        boundaryStatus = status;
        boundaryResponse = "";
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.registerFieldBoundaryResponse(coordinates()));
        ApiException cause = assertInstanceOf(ApiException.class, error.getCause());
        assertEquals(com.abelium.inatrace.api.ApiStatus.ERROR, cause.getApiStatus());
        assertEquals("AgStack boundary request failed with HTTP " + status, cause.getMessage());
        boundaryStatus = 200;
        boundaryResponse = "{\"Geo Id\":\"geo-123\"}";
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
        assertEquals(status == 401 ? 2 : 1, logins.get());
    }

    @Test
    void rejectedBoundaryInvalidatesOnlyItsTokenWithoutReplayingThePost() throws Exception {
        boundaryStatus = 401;
        boundaryResponse = "{\"error\":\"synthetic-rejection\"}";
        assertThrows(RuntimeException.class, () -> client.registerFieldBoundaryResponse(coordinates()));
        assertEquals(1, boundaries.get(), "the failed boundary POST must not be replayed");
        boundaryStatus = 200;
        loginResponse = "{\"access_token\":\"fresh-token\"}";
        boundaryResponse = "{\"Geo Id\":\"geo-123\"}";
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
        assertEquals("Bearer fresh-token", authorization.get());
        assertEquals(2, logins.get());
    }

    @Test
    void lateRejectionDoesNotInvalidateANewerToken() {
        assertEquals("synthetic-token", tokens.retrieveToken());
        clock.instant = clock.instant.plusSeconds(3 * 3600 + 1);
        loginResponse = "{\"access_token\":\"fresh-token\"}";
        assertEquals("fresh-token", tokens.retrieveToken());
        tokens.invalidateToken("synthetic-token");
        assertEquals("fresh-token", tokens.retrieveToken());
        assertEquals(2, logins.get());
    }

    @Test
    void concurrentExpiredCallsAreObservedIndependently() throws Exception {
        assertEquals("synthetic-token", tokens.retrieveToken());
        clock.instant = clock.instant.plusSeconds(3 * 3600 + 1);
        loginArrivals = new CountDownLatch(2);
        releaseLogin = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(tokens::retrieveToken);
            var second = callers.submit(tokens::retrieveToken);
            assertTrue(loginArrivals.await(5, TimeUnit.SECONDS));
            releaseLogin.countDown();
            assertEquals("synthetic-token", first.get(5, TimeUnit.SECONDS));
            assertEquals("synthetic-token", second.get(5, TimeUnit.SECONDS));
            assertEquals(3, logins.get());
        } finally {
            releaseLogin.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void rateLimitedBoundaryCanBeRetriedByTheCaller() throws Exception {
        boundaryStatus = 429;
        boundaryResponse = "{\"error\":\"rate-limited\"}";
        assertThrows(RuntimeException.class, () -> client.registerFieldBoundaryResponse(coordinates()));
        boundaryStatus = 200;
        boundaryResponse = "{\"Geo Id\":\"geo-123\"}";
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
        assertEquals(2, boundaries.get());
    }

    @Test
    void emptyBoundaryResponseCanBeFollowedBySuccess() throws Exception {
        boundaryResponse = "";
        assertNull(client.registerFieldBoundaryResponse(coordinates()));
        boundaryResponse = "{\"Geo Id\":\"geo-123\"}";
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
    }

    @Test
    void malformedBoundaryResponseCanBeFollowedBySuccess() throws Exception {
        boundaryResponse = "not-json";
        assertThrows(RuntimeException.class, () -> client.registerFieldBoundaryResponse(coordinates()));
        boundaryResponse = "{\"Geo Id\":\"geo-123\"}";
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
    }

    @Test
    void providerFailureCanBeFollowedBySuccessfulBoundary() throws Exception {
        boundaryStatus = 500;
        boundaryResponse = "{\"error\":\"synthetic-provider-error\"}";
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.registerFieldBoundaryResponse(coordinates()));
        assertInstanceOf(ApiException.class, error.getCause());
        boundaryStatus = 200;
        boundaryResponse = "{\"Geo Id\":\"recovered-geo\"}";
        assertEquals("recovered-geo", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
        assertEquals(1, logins.get());
        assertEquals(2, boundaries.get());
    }

    @Test
    void delayedBoundaryTimesOutAndNextRequestCanRecover() throws Exception {
        ReflectionTestUtils.setField(client, "requestTimeoutMs", 300L);
        boundaryArrived = new CountDownLatch(1);
        releaseBoundary = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            var result = caller.submit(() -> assertThrows(RuntimeException.class,
                    () -> client.registerFieldBoundaryResponse(coordinates())));
            assertTrue(boundaryArrived.await(5, TimeUnit.SECONDS));
            RuntimeException error = result.get(5, TimeUnit.SECONDS);
            assertInstanceOf(java.util.concurrent.TimeoutException.class, error.getCause());
        } finally {
            boundaryArrived = null;
            releaseBoundary.countDown();
            caller.shutdownNow();
        }
        ReflectionTestUtils.setField(client, "requestTimeoutMs", 3000L);
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
    }

    @Test
    void delayedLoginTimesOutAndNextLoginRecovers() throws Exception {
        ReflectionTestUtils.setField(tokens, "requestTimeoutMs", 300L);
        loginArrivals = new CountDownLatch(1);
        releaseLogin = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            var result = caller.submit(() -> assertThrows(RuntimeException.class, tokens::retrieveToken));
            assertTrue(loginArrivals.await(5, TimeUnit.SECONDS));
            assertInstanceOf(java.util.concurrent.TimeoutException.class, result.get(5, TimeUnit.SECONDS).getCause());
        } finally {
            loginArrivals = null;
            releaseLogin.countDown();
            caller.shutdownNow();
        }
        ReflectionTestUtils.setField(tokens, "requestTimeoutMs", 3000L);
        assertEquals("synthetic-token", tokens.retrieveToken());
    }

    @Test
    void connectionRefusalCanBeFollowedBySuccessfulLogin() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        ReflectionTestUtils.setField(tokens, "baseURL", "http://127.0.0.1:" + port);
        assertThrows(RuntimeException.class, tokens::retrieveToken);
        ReflectionTestUtils.setField(tokens, "baseURL", "http://127.0.0.1:" + server.getAddress().getPort());
        assertEquals("synthetic-token", tokens.retrieveToken());
    }

    @Test
    void closedBoundaryConnectionCanBeFollowedBySuccess() throws Exception {
        closeBoundarySocket = true;
        assertThrows(RuntimeException.class, () -> client.registerFieldBoundaryResponse(coordinates()));
        assertEquals(1, boundaries.get(), "a closed response must not replay the boundary POST");
        closeBoundarySocket = false;
        assertEquals("geo-123", client.registerFieldBoundaryResponse(coordinates()).getGeoID());
    }

    private static List<PlotCoordinate> coordinates() {
        return List.of(point(-1, 30), point(-1, 31), point(-2, 31), point(-1, 30));
    }

    private static PlotCoordinate point(double latitude, double longitude) {
        PlotCoordinate coordinate = new PlotCoordinate();
        coordinate.setLatitude(latitude);
        coordinate.setLongitude(longitude);
        return coordinate;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
