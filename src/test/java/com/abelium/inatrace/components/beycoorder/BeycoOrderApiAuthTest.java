package com.abelium.inatrace.components.beycoorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class BeycoOrderApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String ACME_LOT = "ACME-BEYCO-LOT-ONLY";
    private static final String RIVAL_LOT = "RIVAL-BEYCO-LOT-ONLY";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private BeycoOrderService beycoOrderService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long acmeId;
    private Long acmeStockId;
    private Long rivalStockId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme Beyco");
        Company rival = company("Rival Beyco");
        User owner = user("beyco-owner@acme.test");
        User stranger = user("beyco-stranger@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        Country country = new Country();
        country.setCode("XB");
        country.setName("Beyco Test Country");
        em.persist(country);

        acmeStockId = stock(acme, owner, country, ACME_LOT).getId();
        rivalStockId = stock(rival, stranger, country, RIVAL_LOT).getId();
        acmeId = acme.getId();
        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void ownerCanReadFieldsForOwnStockOrder() throws Exception {
        MvcResult allowed = mockMvc.perform(fields(acmeStockId).cookie(ownerSession)).andReturn();
        String body = allowed.getResponse().getContentAsString();
        assertEquals(200, allowed.getResponse().getStatus(), body);
        assertTrue(body.contains(ACME_LOT), body);
        assertFalse(body.contains(RIVAL_LOT), body);
    }

    @Test
    void strangerCannotReadBeycoFieldsOfOtherCompany() throws Exception {
        MvcResult refused = mockMvc.perform(fields(acmeStockId).cookie(strangerSession)).andReturn();
        String body = refused.getResponse().getContentAsString();
        assertEquals(403, refused.getResponse().getStatus(), body);
        assertFalse(body.contains(ACME_LOT), body);
    }

    @Test
    void evenOwnerCannotMixForeignStockOrderIntoBeycoFields() throws Exception {
        MvcResult refused = mockMvc.perform(get("/api/chain/beyco-order/company/" + acmeId + "/fields")
                .queryParam("id", String.valueOf(acmeStockId), String.valueOf(rivalStockId))
                .cookie(ownerSession)).andReturn();
        String body = refused.getResponse().getContentAsString();
        assertEquals(403, refused.getResponse().getStatus(), body);
        assertFalse(body.contains(ACME_LOT), body);
        assertFalse(body.contains(RIVAL_LOT), body);
    }

    @Test
    void everyBeycoRouteRefusesOtherTenantAndAnonymousCallerBeforeExternalCall() throws Exception {
        for (RequestBuilder request : requests()) {
            MvcResult anonymous = mockMvc.perform(request).andReturn();
            assertEquals(401, anonymous.getResponse().getStatus(), anonymous.getResponse().getContentAsString());
            assertFalse(anonymous.getResponse().getContentAsString().contains(ACME_LOT));
        }

        for (RequestBuilder request : requestsWithStrangerSession()) {
            MvcResult refused = mockMvc.perform(request).andReturn();
            String body = refused.getResponse().getContentAsString();
            assertEquals(403, refused.getResponse().getStatus(), body);
            assertFalse(body.contains(ACME_LOT), body);
        }
    }

    @Test
    void ownerCanUseBeycoTransportWhileStrangerCannotReachIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> tokenBodies = new ArrayList<>();
        AtomicReference<String> offerBody = new AtomicReference<>();
        AtomicReference<String> offerAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth", exchange -> {
            calls.incrementAndGet();
            tokenBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"accessToken\":\"LOCAL-BEYCO-TOKEN\",\"tokenType\":\"Bearer\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.createContext("/api/Offers", exchange -> {
            calls.incrementAndGet();
            offerBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            offerAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"id\":\"LOCAL-BEYCO-OFFER\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });

        Object oldAuthUrl = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        Object oldBeycoUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", baseUrl + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", baseUrl);

            List<RequestBuilder> successRequests = new ArrayList<>(ownerExternalRequests());
            successRequests.set(2, post("/api/chain/beyco-order/company/" + acmeId + "/order")
                    .header("X-Beyco-Token", "test-token").cookie(ownerSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"test\",\"offerCoffees\":[{\"coffee\":{}}]}"));
            for (RequestBuilder request : successRequests) {
                MvcResult allowed = mockMvc.perform(request).andReturn();
                assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
                assertTrue(allowed.getResponse().getContentAsString().contains("LOCAL-BEYCO-"));
            }
            assertEquals(3, calls.get());
            ObjectMapper mapper = new ObjectMapper();
            var authorization = mapper.readTree(tokenBodies.get(0));
            var refresh = mapper.readTree(tokenBodies.get(1));
            assertEquals("test-code", authorization.path("code").asText());
            assertEquals("test-refresh", refresh.path("refreshToken").asText());
            assertEquals("AuthorizationCode", authorization.path("grantType").asText());
            assertEquals("RefreshToken", refresh.path("grantType").asText());
            assertEquals("none", authorization.path("clientId").asText());
            assertEquals("none", authorization.path("clientSecret").asText());
            assertEquals("Bearer test-token", offerAuthorization.get());
            var offer = mapper.readTree(offerBody.get());
            assertEquals("Public", offer.path("privacy").asText());
            assertTrue(offer.path("offerCoffees").get(0).path("isFixedPrice").asBoolean());
            assertTrue(offer.path("offerCoffees").get(0).path("coffee").path("isBulk").asBoolean());
            assertEquals("Kg", offer.path("offerCoffees").get(0).path("coffee").path("unit").asText());

            List<RequestBuilder> strangerRequests = requestsWithStrangerSession();
            for (RequestBuilder request : List.of(strangerRequests.get(0), strangerRequests.get(1), strangerRequests.get(3))) {
                MvcResult refused = mockMvc.perform(request).andReturn();
                assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
            }
            List<RequestBuilder> anonymousRequests = requests();
            for (RequestBuilder request : List.of(anonymousRequests.get(0), anonymousRequests.get(1), anonymousRequests.get(3))) {
                assertEquals(401, mockMvc.perform(request).andReturn().getResponse().getStatus());
            }
            assertEquals(3, calls.get(), "Unauthorized callers must not make outbound Beyco requests");
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuthUrl);
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldBeycoUrl);
            server.stop(0);
        }
    }

    @Test
    void providerFailuresAreSafeAndDoNotInvalidateTheLocalSession(CapturedOutput captured) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger status = new AtomicInteger(401);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean empty = new AtomicBoolean();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] body = (empty.get() ? "" : "PROVIDER-SECRET-MARKER").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status.get(), body.length == 0 ? -1 : body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        Object oldAuth = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        Object oldUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        Object oldSecret = ReflectionTestUtils.getField(beycoOrderService, "clientSecret");
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", base + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", base);
            ReflectionTestUtils.setField(beycoOrderService, "clientSecret", "SYNTHETIC-CLIENT-SECRET");
            for (int providerStatus : List.of(400, 401, 403, 429, 500, 503)) {
                status.set(providerStatus);
                for (RequestBuilder request : ownerExternalRequests()) {
                    MvcResult result = mockMvc.perform(request).andReturn();
                    assertEquals(503, result.getResponse().getStatus());
                    assertTrue(result.getResponse().getContentAsString().contains("UPSTREAM_HTTP_ERROR"));
                    assertFalse(result.getResponse().getContentAsString().contains("PROVIDER-SECRET-MARKER"));
                }
            }
            status.set(401);
            empty.set(true);
            for (RequestBuilder request : ownerExternalRequests()) {
                assertEquals(503, mockMvc.perform(request).andReturn().getResponse().getStatus());
            }
            assertEquals(21, calls.get());
            assertEquals(200, mockMvc.perform(get("/api/user/profile").cookie(ownerSession))
                    .andReturn().getResponse().getStatus());
            em.clear();
            assertEquals(0, new BigDecimal("100").compareTo(em.find(StockOrder.class, acmeStockId).getTotalQuantity()));
            assertFalse(captured.getAll().contains("PROVIDER-SECRET-MARKER"));
            assertFalse(captured.getAll().contains("test-token"));
            assertFalse(captured.getAll().contains("test-refresh"));
            assertFalse(captured.getAll().contains("SYNTHETIC-CLIENT-SECRET"));
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuth);
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldUrl);
            ReflectionTestUtils.setField(beycoOrderService, "clientSecret", oldSecret);
            server.stop(0);
        }
    }

    @Test
    void emptyAndMalformedProviderResponsesFailSafelyAndRecover() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>("");
        AtomicReference<String> mediaType = new AtomicReference<>("application/json");
        server.createContext("/", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", mediaType.get());
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        Object oldAuth = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        Object oldUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", base + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", base);
            for (String value : List.of("", "{invalid-json")) {
                body.set(value);
                for (RequestBuilder request : ownerExternalRequests()) {
                    assertEquals(503, mockMvc.perform(request).andReturn().getResponse().getStatus());
                }
            }
            mediaType.set("application/octet-stream");
            body.set("provider response with unexpected media type");
            for (RequestBuilder request : ownerExternalRequests()) {
                assertEquals(503, mockMvc.perform(request).andReturn().getResponse().getStatus());
            }
            mediaType.set("application/json");
            body.set("{\"accessToken\":\"RECOVERED\",\"tokenType\":\"Bearer\"}");
            assertTrue(mockMvc.perform(ownerExternalRequests().get(0)).andReturn().getResponse().getContentAsString().contains("RECOVERED"));
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuth);
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldUrl);
            server.stop(0);
        }
    }

    @Test
    void tokenResponsesRequireAnAccessTokenAndRejectInvalidOptionalFields() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> response = new AtomicReference<>("{}");
        server.createContext("/oauth", exchange -> {
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        Object oldAuth = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        server.start();
        try {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth");
            for (String invalid : List.of("{}", "{\"accessToken\":\"  \"}",
                    "{\"accessToken\":\"synthetic\",\"refreshToken\":\" \"}",
                    "{\"accessToken\":\"synthetic\",\"expiresIn\":0}",
                    "{\"accessToken\":\"synthetic\",\"expiresIn\":-1}")) {
                response.set(invalid);
                for (RequestBuilder request : ownerExternalRequests().subList(0, 2)) {
                    MvcResult result = mockMvc.perform(request).andReturn();
                    assertEquals(503, result.getResponse().getStatus());
                    assertFalse(result.getResponse().getContentAsString().contains("synthetic"));
                }
            }
            for (String valid : List.of("{\"accessToken\":\"synthetic\"}",
                    "{\"accessToken\":\"synthetic\",\"refreshToken\":\"refresh\",\"expiresIn\":3600}")) {
                response.set(valid);
                for (RequestBuilder request : ownerExternalRequests().subList(0, 2)) {
                    assertEquals(200, mockMvc.perform(request).andReturn().getResponse().getStatus());
                }
            }
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuth);
            server.stop(0);
        }
    }

    @Test
    void acceptedOfferWithLostResponseIsNotReplayed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/Offers", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.close();
        });
        Object oldUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        server.start();
        try {
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals(503, mockMvc.perform(ownerExternalRequests().get(2)).andReturn().getResponse().getStatus());
            assertEquals(1, calls.get(), "The result may be unknown, but the offer must not be replayed");
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldUrl);
            server.stop(0);
        }
    }

    @Test
    void refusedConnectionFailsSafelyAndAllOperationsRecover() throws Exception {
        ServerSocket unused = new ServerSocket(0);
        int unusedPort = unused.getLocalPort();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        unused.close();
        server.createContext("/", exchange -> {
            byte[] bytes = "{\"accessToken\":\"RECOVERED\",\"id\":\"RECOVERED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        Object oldAuth = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        Object oldUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        server.start();
        try {
            String unavailable = "http://127.0.0.1:" + unusedPort;
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", unavailable + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", unavailable);
            for (RequestBuilder request : ownerExternalRequests()) {
                assertEquals(503, mockMvc.perform(request).andReturn().getResponse().getStatus());
            }
            String healthy = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", healthy + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", healthy);
            for (RequestBuilder request : ownerExternalRequests()) {
                assertEquals(200, mockMvc.perform(request).andReturn().getResponse().getStatus());
            }
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuth);
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldUrl);
            server.stop(0);
        }
    }

    @Test
    void readTimeoutIsBoundedAndLaterRequestRecovers() throws Exception {
        for (int operation : List.of(0, 1, 2)) {
            assertReadTimeoutAndRecovery(operation);
        }
    }

    private void assertReadTimeoutAndRecovery(int operation) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (calls.incrementAndGet() == 1) {
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                exchange.close();
                return;
            }
            byte[] bytes = "{\"accessToken\":\"RECOVERED\",\"id\":\"RECOVERED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        Object oldAuth = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        Object oldUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        Object oldTimeout = ReflectionTestUtils.getField(beycoOrderService, "readTimeoutMs");
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", base + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", base);
            ReflectionTestUtils.setField(beycoOrderService, "readTimeoutMs", 150);
            long start = System.nanoTime();
            assertEquals(503, mockMvc.perform(ownerExternalRequests().get(operation)).andReturn().getResponse().getStatus());
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 5);
            assertEquals(1, calls.get(), "The timed-out request must not be replayed");
            release.countDown();
            assertEquals(200, mockMvc.perform(ownerExternalRequests().get(operation)).andReturn().getResponse().getStatus());
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuth);
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldUrl);
            ReflectionTestUtils.setField(beycoOrderService, "readTimeoutMs", oldTimeout);
            server.stop(0);
        }
    }

    private List<RequestBuilder> ownerExternalRequests() {
        return List.of(
                get("/api/chain/beyco-order/company/" + acmeId + "/token")
                        .queryParam("authCode", "test-code").cookie(ownerSession),
                get("/api/chain/beyco-order/company/" + acmeId + "/token/refresh")
                        .header("X-Beyco-Refresh-Token", "test-refresh").cookie(ownerSession),
                post("/api/chain/beyco-order/company/" + acmeId + "/order")
                        .header("X-Beyco-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"offerCoffees\":[]}")
                        .cookie(ownerSession));
    }

    private List<RequestBuilder> requests() {
        return List.of(
                get("/api/chain/beyco-order/company/" + acmeId + "/token").queryParam("authCode", "test-code"),
                get("/api/chain/beyco-order/company/" + acmeId + "/token/refresh")
                        .header("X-Beyco-Refresh-Token", "test-refresh"),
                fields(acmeStockId),
                post("/api/chain/beyco-order/company/" + acmeId + "/order")
                        .header("X-Beyco-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"offerCoffees\":[]}"));
    }

    private List<RequestBuilder> requestsWithStrangerSession() {
        return List.of(
                get("/api/chain/beyco-order/company/" + acmeId + "/token")
                        .queryParam("authCode", "test-code").cookie(strangerSession),
                get("/api/chain/beyco-order/company/" + acmeId + "/token/refresh")
                        .header("X-Beyco-Refresh-Token", "test-refresh").cookie(strangerSession),
                fields(acmeStockId).cookie(strangerSession),
                post("/api/chain/beyco-order/company/" + acmeId + "/order")
                        .header("X-Beyco-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"offerCoffees\":[]}")
                        .cookie(strangerSession));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder fields(Long stockId) {
        return get("/api/chain/beyco-order/company/" + acmeId + "/fields")
                .queryParam("id", String.valueOf(stockId));
    }

    private StockOrder stock(Company company, User owner, Country country, String lot) {
        FacilityLocation location = new FacilityLocation();
        Address address = new Address();
        address.setAddress("Beyco test street");
        address.setCountry(country);
        location.setAddress(address);
        em.persist(location);

        Facility facility = new Facility();
        facility.setName(lot + " facility");
        facility.setCompany(company);
        facility.setFacilityLocation(location);
        em.persist(facility);

        StockOrder stock = new StockOrder();
        stock.setCompany(company);
        stock.setFacility(facility);
        stock.setOrderType(OrderType.PURCHASE_ORDER);
        stock.setInternalLotNumber(lot);
        stock.setProductionDate(LocalDate.of(2025, 8, 18));
        stock.setTotalQuantity(new BigDecimal("100"));
        stock.setCreatedBy(owner);
        stock.setUpdatedBy(owner);
        em.persist(stock);
        return stock;
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setAllowBeycoIntegration(true);
        em.persist(company);
        return company;
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setSurname("Test");
        user.setPassword("not-used-in-this-test");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    private void enroll(User user, Company company) {
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(company);
        enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(enrollment);
        company.getUsers().add(enrollment);
    }

    private Cookie cookie(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }
}
