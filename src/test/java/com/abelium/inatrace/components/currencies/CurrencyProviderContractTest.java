package com.abelium.inatrace.components.currencies;

import com.abelium.inatrace.components.codebook.currencies.CurrencyTypeService;
import com.abelium.inatrace.components.codebook.currencies.CurrencyUpdateScheduler;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.CurrencyType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Exercises the real clients and committed database state against a synthetic provider. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Timeout(45)
class CurrencyProviderContractTest extends AbstractMySqlIntegrationTest {
    private static final Date DATE = Date.from(LocalDate.of(2024, 4, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    @Autowired private CurrencyService currencyService;
    @Autowired private CurrencyTypeService currencyTypes;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private TokenService tokenService;
    @Autowired private MockMvc mockMvc;

    private final List<Long> ownedIds = new ArrayList<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private String currencyA;
    private String currencyB;
    private String disabled;
    private String discovered;
    private volatile int status = 200;
    private volatile String historical;
    private volatile String latest;
    private volatile String symbols;
    private volatile CountDownLatch responseGate;
    private volatile String gatePath;
    private volatile String failurePath;
    private volatile int failureStatus = 200;
    private volatile String failureBody;
    private volatile boolean closeConnection;
    private HttpServer server;
    private ExecutorService executor;
    private Object originalCurrencyUrl;
    private Object originalTypeUrl;
    private Object originalCurrencyKey;
    private Object originalTypeKey;
    private Object originalCurrencyTimeout;
    private Object originalTypeTimeout;
    private Long ownedUserId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        currencyA = "A" + suffix;
        currencyB = "B" + suffix;
        disabled = "D" + suffix;
        discovered = "N" + suffix;
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            List<CurrencyType> existing = em.createQuery("select c from CurrencyType c where c.code = 'EUR'", CurrencyType.class).getResultList();
            if (existing.isEmpty()) createCurrency("EUR", true);
            createCurrency(currencyA, true);
            createCurrency(currencyB, true);
            createCurrency(disabled, false);
        });
        historical = rates("2", "4");
        latest = historical;
        symbols = "{\"success\":true,\"symbols\":{\"" + discovered + "\":\"Synthetic new\"}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1/", this::handle);
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        originalCurrencyUrl = ReflectionTestUtils.getField(currencyService, "baseURL");
        originalTypeUrl = ReflectionTestUtils.getField(currencyTypes, "baseURL");
        originalCurrencyKey = ReflectionTestUtils.getField(currencyService, "apiKey");
        originalTypeKey = ReflectionTestUtils.getField(currencyTypes, "apiKey");
        originalCurrencyTimeout = ReflectionTestUtils.getField(currencyService, "requestTimeoutMs");
        originalTypeTimeout = ReflectionTestUtils.getField(currencyTypes, "requestTimeoutMs");
        ReflectionTestUtils.setField(currencyService, "baseURL", url);
        ReflectionTestUtils.setField(currencyTypes, "baseURL", url);
        ReflectionTestUtils.setField(currencyService, "apiKey", "synthetic-key");
        ReflectionTestUtils.setField(currencyTypes, "apiKey", "synthetic-key");
        ReflectionTestUtils.setField(currencyService, "requestTimeoutMs", 1000L);
        ReflectionTestUtils.setField(currencyTypes, "requestTimeoutMs", 1000L);
    }

    @AfterEach
    void tearDown() {
        if (responseGate != null) responseGate.countDown();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        if (originalCurrencyUrl != null) ReflectionTestUtils.setField(currencyService, "baseURL", originalCurrencyUrl);
        if (originalTypeUrl != null) ReflectionTestUtils.setField(currencyTypes, "baseURL", originalTypeUrl);
        if (originalCurrencyKey != null) ReflectionTestUtils.setField(currencyService, "apiKey", originalCurrencyKey);
        if (originalTypeKey != null) ReflectionTestUtils.setField(currencyTypes, "apiKey", originalTypeKey);
        if (originalCurrencyTimeout != null) ReflectionTestUtils.setField(currencyService, "requestTimeoutMs", originalCurrencyTimeout);
        if (originalTypeTimeout != null) ReflectionTestUtils.setField(currencyTypes, "requestTimeoutMs", originalTypeTimeout);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            if (ownedUserId != null) {
                em.createQuery("delete from User u where u.id = :id").setParameter("id", ownedUserId).executeUpdate();
                em.createNativeQuery("delete from User_AUD where id = :id").setParameter("id", ownedUserId).executeUpdate();
            }
            em.createQuery("delete from CurrencyPair p where p.to.code in :codes").setParameter("codes", List.of(currencyA, currencyB, disabled)).executeUpdate();
            em.createQuery("delete from CurrencyType c where c.code = :code").setParameter("code", discovered).executeUpdate();
            if (!ownedIds.isEmpty()) em.createQuery("delete from CurrencyType c where c.id in :ids").setParameter("ids", ownedIds).executeUpdate();
        });
    }

    @Test
    void historicalMissPersistsRatesThenCacheAndSameCurrencyAvoidNetwork() {
        assertEquals(0, new BigDecimal("20.000000").compareTo(currencyService.convertAtDate(currencyA, currencyB, new BigDecimal("10"), DATE)));
        assertEquals(1, requests.size());
        assertTrue(requests.get(0).startsWith("/v1/2024-04-01?"));
        assertTrue(requests.get(0).contains("access_key=synthetic-key"));
        assertTrue(requests.get(0).contains("base=EUR"));
        assertEquals(2, countRates());
        assertEquals(0, new BigDecimal("20").compareTo(currencyService.convertAtDate(currencyA, currencyB, new BigDecimal("10"), DATE)));
        assertEquals(0, new BigDecimal("3.25").compareTo(currencyService.convertAtDate(currencyA, currencyA, new BigDecimal("3.25"), DATE)));
        assertEquals(1, requests.size());
    }

    @Test
    void divisionUsesSixPlaceHalfUpBeforeConversion() {
        historical = rates("3", "4");
        assertEquals(0, new BigDecimal("1.333332").compareTo(currencyService.convertAtDate(currencyA, currencyB, BigDecimal.ONE, DATE)));
        assertEquals(1, requests.size());
    }

    @Test
    void repeatedScheduledRefreshPreservesRowsAndDisabledCurrencies() {
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypes);
        scheduler.updateCurrencies();
        scheduler.updateCurrencies();
        assertEquals(4, requests.size());
        var symbolsRequest = UriComponentsBuilder.fromUriString(requests.get(0)).build();
        assertEquals("/v1/symbols", symbolsRequest.getPath());
        assertEquals(java.util.Map.of("access_key", List.of("synthetic-key")), symbolsRequest.getQueryParams());
        var latestRequest = UriComponentsBuilder.fromUriString(requests.get(1)).build();
        assertEquals("/v1/latest", latestRequest.getPath());
        assertEquals(java.util.Map.of("access_key", List.of("synthetic-key"), "base", List.of("EUR")), latestRequest.getQueryParams());
        assertEquals(2, countRates());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            CurrencyType created = em.createQuery("select c from CurrencyType c where c.code = :code", CurrencyType.class)
                    .setParameter("code", discovered).getSingleResult();
            assertEquals(Boolean.FALSE, created.getEnabled());
            assertEquals(0L, em.createQuery("select count(p) from CurrencyPair p where p.to.code = :code", Long.class)
                    .setParameter("code", disabled).getSingleResult());
        });
    }

    @Test
    void latestRatesAreUsedForConversionsWithoutFurtherNetworkCalls() {
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypes);
        scheduler.updateCurrencies();
        requests.clear();
        BigDecimal result = new TransactionTemplate(transactions).execute(tx ->
                currencyService.convert(currencyA, currencyB, new BigDecimal("10")));
        assertEquals(0, new BigDecimal("20").compareTo(result));
        assertTrue(requests.isEmpty());
    }

    @Test
    void defaultProviderConfigurationIsPresentInBothSpringBeans() {
        assertEquals("http://api.exchangeratesapi.io/v1", originalCurrencyUrl);
        assertEquals(originalCurrencyUrl, originalTypeUrl);
        assertEquals(30000L, originalCurrencyTimeout);
        assertEquals(originalCurrencyTimeout, originalTypeTimeout);
    }

    @Test
    void invalidLatestAndMissingSymbolsDoNotInsertIncompleteRates() {
        symbols = "{\"success\":true,\"symbols\":null}";
        latest = rates("0", "4");
        currencyTypes.updateCurrencies();
        assertEquals(0, countRates());
        long found = new TransactionTemplate(transactions).execute(tx -> em.createQuery("select count(c) from CurrencyType c where c.code = :code", Long.class)
                .setParameter("code", discovered).getSingleResult());
        assertEquals(0, found);
        symbols = "{\"success\":true,\"symbols\":{\"" + discovered + "\":\"Synthetic new\"}}";
        latest = rates("2", "4");
        currencyTypes.updateCurrencies();
        assertEquals(2, countRates());
    }

    @ParameterizedTest
    @CsvSource({"symbols,429", "symbols,500", "latest,429", "latest,500"})
    void schedulerRecoversAfterIndependentProviderStatusFailure(String endpoint, int httpStatus) {
        failurePath = "/v1/" + endpoint;
        failureStatus = httpStatus;
        failureBody = "{\"error\":\"synthetic-upstream-body\"}";
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypes);
        scheduler.updateCurrencies();
        assertEquals(endpoint.equals("symbols") ? 2 : 0, countRates());
        assertEquals(2, requests.size());
        failurePath = null;
        scheduler.updateCurrencies();
        assertEquals(2, countRates());
        assertEquals(4, requests.size());
        long found = new TransactionTemplate(transactions).execute(tx -> em.createQuery(
                        "select count(c) from CurrencyType c where c.code = :code", Long.class)
                .setParameter("code", discovered).getSingleResult());
        assertEquals(1, found);
    }

    @ParameterizedTest
    @CsvSource({"symbols,empty", "symbols,malformed", "latest,empty", "latest,malformed"})
    void schedulerRecoversAfterEmptyOrMalformedProviderResponse(String endpoint, String kind) {
        failurePath = "/v1/" + endpoint;
        failureStatus = 200;
        failureBody = kind.equals("empty") ? "" : "not-json";
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypes);
        scheduler.updateCurrencies();
        assertEquals(endpoint.equals("symbols") ? 2 : 0, countRates());
        failurePath = null;
        scheduler.updateCurrencies();
        assertEquals(2, countRates());
        assertEquals(4, requests.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"symbols", "latest"})
    void schedulerRecoversAfterEachRefreshRequestTimesOut(String endpoint) {
        ReflectionTestUtils.setField(currencyTypes, "requestTimeoutMs", 300L);
        gatePath = "/v1/" + endpoint;
        responseGate = new CountDownLatch(1);
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypes);
        Logger logger = (Logger) LoggerFactory.getLogger(CurrencyTypeService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            scheduler.updateCurrencies();
            assertEquals(endpoint.equals("symbols") ? 2 : 0, countRates());
            assertEquals(2, requests.size());
            assertEquals(1, logs.list.stream().filter(event ->
                    event.getFormattedMessage().contains("type=java.util.concurrent.TimeoutException")).count());
        } finally {
            logger.detachAppender(logs);
            logs.stop();
            responseGate.countDown();
            responseGate = null;
            gatePath = null;
        }
        ReflectionTestUtils.setField(currencyTypes, "requestTimeoutMs", 3000L);
        scheduler.updateCurrencies();
        assertEquals(2, countRates());
        assertEquals(4, requests.size());
    }

    @Test
    void providerErrorsAndInvalidRatesDoNotPersistAndCanRecover() {
        for (int failureStatus : List.of(401, 429, 500)) {
            status = failureStatus;
            assertThrows(RuntimeException.class, () -> currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE));
            assertEquals(0, countRates());
        }
        status = 200;
        for (String invalid : List.of("", "not-json", "{\"success\":false}", "{\"success\":true,\"base\":\"EUR\",\"date\":\"2024-04-01\",\"rates\":null}", "{\"success\":true,\"date\":null,\"rates\":{}}", rates("0", "4"), rates("-1", "4"),
                rates("2", "4").replace("2024-04-01", "2024-04-02"),
                rates("2", "4").replace("\"base\":\"EUR\"", "\"base\":\"USD\""))) {
            historical = invalid;
            assertThrows(RuntimeException.class, () -> currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE));
            assertEquals(0, countRates());
        }
        historical = rates("2", "4");
        assertEquals(0, new BigDecimal("2").compareTo(currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE)));
        assertEquals(2, countRates());
    }

    @Test
    void missingRequestedCurrencyDoesNotCommitPartialHistoricalRates() {
        historical = "{\"success\":true,\"base\":\"EUR\",\"date\":\"2024-04-01\",\"rates\":{\"" + currencyB + "\":4}}";
        assertThrows(RuntimeException.class, () -> currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE));
        assertEquals(0, countRates());
        historical = rates("2", "4");
        assertEquals(0, new BigDecimal("2").compareTo(currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE)));
    }

    @Test
    void providerErrorsDoNotExposeKeyOrUpstreamPayloadInReviewedLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(CurrencyService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            status = 401;
            historical = "{\"error\":\"synthetic-upstream-body\"}";
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE));
            assertFalse(String.valueOf(error.getMessage()).contains("synthetic-key"));
            assertFalse(String.valueOf(error.getMessage()).contains("synthetic-upstream-body"));
            assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains("synthetic-key")
                    || event.getFormattedMessage().contains("synthetic-upstream-body")));
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void refreshErrorsDoNotExposeKeyOrUpstreamPayloadInReviewedLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(CurrencyTypeService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            status = 401;
            symbols = "{\"error\":\"synthetic-upstream-body\"}";
            latest = symbols;
            currencyTypes.updateCurrencies();
            assertEquals(2, logs.list.size());
            assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains("synthetic-key")
                    || event.getFormattedMessage().contains("synthetic-upstream-body")));
            assertEquals(0, countRates());
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void providerUnauthorizedDoesNotInvalidateLocalSessionOrExposeProviderError() throws Exception {
        String token = new TransactionTemplate(transactions).execute(tx -> {
            User user = new User();
            user.setEmail("currency-" + UUID.randomUUID() + "@example.test");
            user.setName("Synthetic");
            user.setSurname("User");
            user.setPassword("unused");
            user.setRole(UserRole.USER);
            user.setStatus(UserStatus.ACTIVE);
            em.persist(user);
            em.flush();
            ownedUserId = user.getId();
            return tokenService.createAccessToken(user);
        });
        Cookie cookie = new Cookie("inatrace-accessToken", token);
        status = 401;
        historical = "{\"error\":\"synthetic-upstream-body\"}";
        var failed = mockMvc.perform(get("/api/chain/currency/convert/1/EUR/to/" + currencyA + "/on/2024-04-01")
                .cookie(cookie)).andReturn().getResponse();
        assertEquals(500, failed.getStatus());
        assertFalse(failed.getContentAsString().contains("synthetic-key"));
        assertFalse(failed.getContentAsString().contains("synthetic-upstream-body"));
        assertEquals(200, mockMvc.perform(get("/api/user/profile").cookie(cookie)).andReturn().getResponse().getStatus());
    }

    @Test
    void symbolsCanCommitWhenRatesFailThenLaterRefreshRecovers() {
        latest = "{\"success\":false}";
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypes);
        scheduler.updateCurrencies();
        assertEquals(0, countRates());
        long found = new TransactionTemplate(transactions).execute(tx -> em.createQuery("select count(c) from CurrencyType c where c.code = :code", Long.class)
                .setParameter("code", discovered).getSingleResult());
        assertEquals(1, found);
        latest = rates("2", "4");
        scheduler.updateCurrencies();
        assertEquals(2, countRates());
    }

    @Test
    void delayedHistoricalRequestTimesOutAndLaterCallRecovers() {
        ReflectionTestUtils.setField(currencyService, "requestTimeoutMs", 200L);
        responseGate = new CountDownLatch(1);
        try {
            assertThrows(RuntimeException.class, () -> currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE));
            assertEquals(0, countRates());
        } finally {
            responseGate.countDown();
            responseGate = null;
        }
        ReflectionTestUtils.setField(currencyService, "requestTimeoutMs", 3000L);
        assertEquals(0, new BigDecimal("2").compareTo(currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE)));
    }

    @Test
    void closedProviderSocketDoesNotPersistAndLaterRequestRecovers() {
        closeConnection = true;
        assertThrows(RuntimeException.class, () -> currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE));
        assertEquals(0, countRates());
        closeConnection = false;
        assertEquals(0, new BigDecimal("2").compareTo(currencyService.convertAtDate("EUR", currencyA, BigDecimal.ONE, DATE)));
        assertEquals(2, countRates());
    }

    private CurrencyType createCurrency(String code, boolean enabled) {
        CurrencyType currency = new CurrencyType();
        currency.setCode(code);
        currency.setLabel("Synthetic " + code);
        currency.setEnabled(enabled);
        em.persist(currency);
        em.flush();
        ownedIds.add(currency.getId());
        return currency;
    }

    private long countRates() {
        return new TransactionTemplate(transactions).execute(tx -> em.createQuery("select count(p) from CurrencyPair p where p.to.code in :codes", Long.class)
                .setParameter("codes", List.of(currencyA, currencyB, disabled)).getSingleResult());
    }

    private String rates(String a, String b) {
        return "{\"success\":true,\"base\":\"EUR\",\"date\":\"2024-04-01\",\"rates\":{\"" + currencyA + "\":" + a
                + ",\"" + currencyB + "\":" + b + ",\"" + disabled + "\":8}}";
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestURI().toString());
        if (closeConnection) {
            exchange.close();
            return;
        }
        CountDownLatch gate = responseGate;
        String path = exchange.getRequestURI().getPath();
        if (gate != null && (gatePath == null || gatePath.equals(path))) {
            try { gate.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        boolean failing = path.equals(failurePath);
        String body = failing ? failureBody : path.endsWith("/symbols") ? symbols : path.endsWith("/latest") ? latest : historical;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(failing ? failureStatus : status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
