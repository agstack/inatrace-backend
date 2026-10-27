package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionOutputSemiProduct;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.UserCustomerType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Exercises stock reservations through the HTTP processing-order endpoint with independent
 * server transactions. The test-only gate deliberately aligns requests after their source
 * order was read and before it is updated; production code contains no timing hook.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class StockConsumptionConcurrencyTest extends AbstractMySqlIntegrationTest {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal SEVENTY = new BigDecimal("70.00");
    private static final BigDecimal THIRTY = new BigDecimal("30.00");

    @Autowired private TestRestTemplate rest;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private TokenService tokenService;
    @PersistenceContext private EntityManager em;

    @SpyBean private StockOrderService stockOrderService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private volatile ReservationGate reservationGate = ReservationGate.disabled();
    private volatile WriteObservation writeObservation = WriteObservation.disabled();
    private final List<Fixture> fixtures = new CopyOnWriteArrayList<>();

    @BeforeEach
    void configureHttpClientAndTestOnlyGate() throws Exception {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        doAnswer(invocation -> {
            StockOrder candidate = invocation.getArgument(1);
            reservationGate.awaitIfReservedSource(candidate);
            writeObservation.record(candidate, invocation.getArgument(2), invocation.getArgument(3));
            return invocation.callRealMethod();
        }).when(stockOrderService).calculateQuantities(any(), any(), any(), any());
    }

    @AfterEach
    void removeScenarioFixtures() {
        reservationGate.release();
        reservationGate = ReservationGate.disabled();
        writeObservation = WriteObservation.disabled();
        tx().executeWithoutResult(status -> fixtures.forEach(this::removeFixture));
        fixtures.clear();
    }

    @Test
    @DisplayName("two overlapping reservations cannot overdraw one stock order or leave a partial loser")
    void overlappingReservationsKeepSourceQuantityAndRowsConsistent() throws Exception {
        Fixture fixture = seedFixture(ONE_HUNDRED);
        reservationGate = ReservationGate.forSource(fixture.primarySourceId(), 2);

        List<ProcessingRequestResult> results = concurrentProcessingRequests(
                fixture, List.of(fixture.primarySourceId(), fixture.primarySourceId()), SEVENTY, true);

        long successful = results.stream().filter(this::isSuccess).count();
        assertEquals(1, successful, () -> "one reservation must win: " + describe(results));
        assertTrue(results.stream().anyMatch(result -> result.response().getStatusCode().isError()),
                () -> "the competing reservation must be refused or rolled back: " + describe(results));
        assertConcurrentResponsesAreSuccessfulOrConflicts(results);
        assertProcessingResultsMatchPersistence(results);

        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        assertEquals(0, new BigDecimal("30.00").compareTo(state.availableQuantity()));
        assertEquals(0, SEVENTY.compareTo(state.committedInputQuantity()));
        assertEquals(1, state.processingOrders());
        assertEquals(1, state.inputTransactions());
        assertEquals(1, state.outputStockOrders());
        assertReservationInvariant(state, ONE_HUNDRED);
    }

    @Test
    @DisplayName("two sequential reservations establish the valid sufficient-stock control")
    void sequentialReservationsWithSufficientStockBothCommit() {
        Fixture fixture = seedFixture(new BigDecimal("200.00"));

        String firstIdentifier = "sequential-output-" + UUID.randomUUID();
        ResponseEntity<String> first = processingRequest(fixture, fixture.primarySourceId(), SEVENTY,
                "GENERAL_ORDER", firstIdentifier);
        String secondIdentifier = "sequential-output-" + UUID.randomUUID();
        ResponseEntity<String> second = processingRequest(fixture, fixture.primarySourceId(),
                sourceRequestState(fixture.primarySourceId()), SEVENTY,
                "GENERAL_ORDER", secondIdentifier);

        assertEquals(HttpStatus.OK, first.getStatusCode(), first::getBody);
        assertEquals(HttpStatus.OK, second.getStatusCode(), second::getBody);
        assertSuccessfulProcessingGraph(fixture.primarySourceId(), firstIdentifier, SEVENTY, SEVENTY);
        assertSuccessfulProcessingGraph(fixture.primarySourceId(), secondIdentifier, SEVENTY, SEVENTY);
        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        assertEquals(0, new BigDecimal("60.00").compareTo(state.availableQuantity()));
        assertEquals(0, new BigDecimal("140.00").compareTo(state.committedInputQuantity()));
        assertEquals(2, state.processingOrders());
        assertEquals(2, state.inputTransactions());
        assertEquals(2, state.outputStockOrders());
        assertReservationInvariant(state, new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("two overlapping reservations with sufficient stock reconcile committed and rolled-back graphs")
    void overlappingReservationsWithSufficientStockRemainConsistent() throws Exception {
        Fixture fixture = seedFixture(new BigDecimal("200.00"));
        reservationGate = ReservationGate.forSource(fixture.primarySourceId(), 2);

        List<ProcessingRequestResult> results = concurrentProcessingRequests(
                fixture, List.of(fixture.primarySourceId(), fixture.primarySourceId()), SEVENTY, true);

        long successful = results.stream().filter(this::isSuccess).count();
        assertTrue(successful >= 1, () -> describe(results));
        assertConcurrentResponsesAreSuccessfulOrConflicts(results);
        assertProcessingResultsMatchPersistence(results);
        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        assertEquals(successful, state.processingOrders());
        assertEquals(successful, state.inputTransactions());
        assertEquals(successful, state.outputStockOrders());
        assertEquals(0, SEVENTY.multiply(BigDecimal.valueOf(successful)).compareTo(state.committedInputQuantity()));
        assertEquals(0, state.committedInputQuantity().compareTo(state.outputQuantity()));
        assertReservationInvariant(state, new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("a bounded burst never commits more source quantity than is available")
    void boundedCompetingReservationsRemainReconciled() throws Exception {
        Fixture fixture = seedFixture(ONE_HUNDRED);
        reservationGate = ReservationGate.forSource(fixture.primarySourceId(), 4);

        List<ProcessingRequestResult> results = concurrentProcessingRequests(fixture,
                List.of(fixture.primarySourceId(), fixture.primarySourceId(),
                        fixture.primarySourceId(), fixture.primarySourceId()),
                new BigDecimal("30.00"), true);

        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        long successful = results.stream().filter(this::isSuccess).count();
        assertTrue(successful >= 1, () -> "at least one valid reservation should commit: " + describe(results));
        assertConcurrentResponsesAreSuccessfulOrConflicts(results);
        assertProcessingResultsMatchPersistence(results);
        assertEquals(successful, state.processingOrders(), "a failed request must not leave an order");
        assertEquals(successful, state.inputTransactions(), "a failed request must not leave a transaction");
        assertEquals(successful, state.outputStockOrders(), "a failed request must not leave output stock");
        assertEquals(0, state.committedInputQuantity().compareTo(state.outputQuantity()),
                "each committed input must have an output with the same quantity");
        assertTrue(state.availableQuantity().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(state.committedInputQuantity().compareTo(ONE_HUNDRED) <= 0);
        assertReservationInvariant(state, ONE_HUNDRED);
    }

    @Test
    @DisplayName("parallel reservations from independent stock orders both complete")
    void independentSourcesDoNotBlockOrCorruptEachOther() throws Exception {
        Fixture fixture = seedFixture(ONE_HUNDRED);

        List<ProcessingRequestResult> results = concurrentProcessingRequests(fixture,
                List.of(fixture.primarySourceId(), fixture.secondarySourceId()), SEVENTY, false);

        assertTrue(results.stream().allMatch(this::isSuccess), () -> describe(results));
        assertProcessingResultsMatchPersistence(results);
        PersistedState primary = persistedState(fixture, fixture.primarySourceId());
        PersistedState secondary = persistedState(fixture, fixture.secondarySourceId());
        assertEquals(0, new BigDecimal("30.00").compareTo(primary.availableQuantity()));
        assertEquals(0, new BigDecimal("30.00").compareTo(secondary.availableQuantity()));
        assertEquals(0, SEVENTY.compareTo(primary.committedInputQuantity()));
        assertEquals(0, SEVENTY.compareTo(secondary.committedInputQuantity()));
        assertEquals(1, primary.linkedProcessingOrders());
        assertEquals(1, secondary.linkedProcessingOrders());
        assertEquals(1, primary.linkedOutputStockOrders());
        assertEquals(1, secondary.linkedOutputStockOrders());
        assertEquals(2, primary.processingOrders());
        assertEquals(2, primary.outputStockOrders());
        assertEquals(0, new BigDecimal("140.00").compareTo(primary.outputQuantity()));
        assertReservationInvariant(primary, ONE_HUNDRED);
        assertReservationInvariant(secondary, ONE_HUNDRED);
    }

    @Test
    @DisplayName("editing a source order concurrently with consumption leaves one serial result")
    void concurrentSourceEditAndConsumptionLeaveNoMixedQuantityState() throws Exception {
        Fixture fixture = seedFixture(ONE_HUNDRED);
        reservationGate = ReservationGate.forSource(fixture.primarySourceId(), 2);

        EditAndConsumptionResult result = concurrentEditAndConsumption(fixture, THIRTY);

        assertTrue(List.of(result.consumption().response(), result.editResponse()).stream().filter(this::isSuccess).count() >= 1,
                () -> describe(List.of(result.consumption())) + " / " + result.editResponse());
        assertProcessingResultsMatchPersistence(List.of(result.consumption()));
        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        boolean consumptionWon = state.inputTransactions() == 1
                && state.totalQuantity().compareTo(ONE_HUNDRED) == 0
                && state.availableQuantity().compareTo(SEVENTY) == 0;
        boolean editWon = state.inputTransactions() == 0
                && state.totalQuantity().compareTo(new BigDecimal("120.00")) == 0
                && state.availableQuantity().compareTo(new BigDecimal("120.00")) == 0;
        boolean bothCommitted = state.inputTransactions() == 1
                && state.totalQuantity().compareTo(new BigDecimal("120.00")) == 0
                && state.availableQuantity().compareTo(new BigDecimal("90.00")) == 0;
        assertTrue(consumptionWon || editWon || bothCommitted, () -> "must match one serial outcome: " + state);
        assertEquals(state.inputTransactions(), state.processingOrders());
        assertEquals(state.inputTransactions(), state.outputStockOrders());
        assertReservationInvariant(state, state.totalQuantity());
    }

    @Test
    @DisplayName("source edit and consumption establish each permitted serial quantity outcome")
    void sequentialSourceEditAndConsumptionEstablishSerialControls() {
        Fixture editOnlyFixture = seedFixture(ONE_HUNDRED);
        assertEquals(HttpStatus.OK, sourceEditRequest(editOnlyFixture).getStatusCode());
        assertSourceState(editOnlyFixture, new BigDecimal("120.00"), new BigDecimal("120.00"), 0);

        Fixture consumeOnlyFixture = seedFixture(ONE_HUNDRED);
        String consumeOnlyIdentifier = "serial-consume-" + UUID.randomUUID();
        assertEquals(HttpStatus.OK, processingRequest(consumeOnlyFixture, consumeOnlyFixture.primarySourceId(), THIRTY,
                "GENERAL_ORDER", consumeOnlyIdentifier).getStatusCode());
        assertSuccessfulProcessingGraph(consumeOnlyFixture.primarySourceId(), consumeOnlyIdentifier, THIRTY, THIRTY);
        assertSourceState(consumeOnlyFixture, ONE_HUNDRED, SEVENTY, 1);

        Fixture editThenConsumeFixture = seedFixture(ONE_HUNDRED);
        assertEquals(HttpStatus.OK, sourceEditRequest(editThenConsumeFixture).getStatusCode());
        String editThenConsumeIdentifier = "serial-edit-consume-" + UUID.randomUUID();
        assertEquals(HttpStatus.OK, processingRequest(editThenConsumeFixture, editThenConsumeFixture.primarySourceId(),
                sourceRequestState(editThenConsumeFixture.primarySourceId()), THIRTY, "GENERAL_ORDER",
                editThenConsumeIdentifier).getStatusCode());
        assertSuccessfulProcessingGraph(editThenConsumeFixture.primarySourceId(), editThenConsumeIdentifier, THIRTY, THIRTY);
        assertSourceState(editThenConsumeFixture, new BigDecimal("120.00"), new BigDecimal("90.00"), 1);

        Fixture consumeThenEditFixture = seedFixture(ONE_HUNDRED);
        String consumeThenEditIdentifier = "serial-consume-edit-" + UUID.randomUUID();
        assertEquals(HttpStatus.OK, processingRequest(consumeThenEditFixture, consumeThenEditFixture.primarySourceId(), THIRTY,
                "GENERAL_ORDER", consumeThenEditIdentifier).getStatusCode());
        assertSuccessfulProcessingGraph(consumeThenEditFixture.primarySourceId(), consumeThenEditIdentifier, THIRTY, THIRTY);
        assertEquals(HttpStatus.OK, sourceEditRequest(consumeThenEditFixture).getStatusCode());
        assertSourceState(consumeThenEditFixture, new BigDecimal("120.00"), new BigDecimal("90.00"), 1);
    }

    @Test
    @DisplayName("a later validation failure rolls back the input reservation and all derived rows")
    void invalidTargetAfterInputReservationRollsBackEverything() {
        Fixture fixture = seedFixture(ONE_HUNDRED);
        writeObservation = WriteObservation.forSource(fixture.primarySourceId());

        String identifier = "rollback-target-" + UUID.randomUUID();
        ResponseEntity<String> response = processingRequest(fixture, fixture.primarySourceId(), SEVENTY,
                "PURCHASE_ORDER", identifier);

        assertTrue(response.getStatusCode().is4xxClientError(), response::getBody);
        assertTrue(response.getBody().contains("INVALID_REQUEST"), response::getBody);
        assertTrue(response.getBody().contains("Order must be of orderType GENERAL_ORDER"), response::getBody);
        assertNoProcessingOutput(identifier);
        assertTrue(writeObservation.sawIdentityInsertsBeforeLaterValidation(),
                "the identity-backed processing order and input transaction must be inserted before the order-type guard");
        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        assertEquals(0, ONE_HUNDRED.compareTo(state.availableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(state.committedInputQuantity()));
        assertEquals(0, state.processingOrders());
        assertEquals(0, state.inputTransactions());
        assertEquals(0, state.outputStockOrders());
    }

    @Test
    @DisplayName("processing yield uses the configured unit-aware range without changing the source debit")
    void configuredYieldKeepsSourceDebitAndMarksOutOfRangeOutput() {
        Fixture inRangeFixture = seedFixture(ONE_HUNDRED);
        String inRangeIdentifier = "yield-output-" + UUID.randomUUID();
        ResponseEntity<String> inRange = yieldProcessingRequest(inRangeFixture, inRangeIdentifier, new BigDecimal("50.00"));
        assertEquals(HttpStatus.OK, inRange.getStatusCode(), inRange::getBody);
        assertSuccessfulProcessingGraph(inRangeFixture.primarySourceId(), inRangeIdentifier, ONE_HUNDRED, new BigDecimal("50.00"));
        assertYieldState(inRangeFixture.primarySourceId(), new BigDecimal("50.00"), false);

        Fixture outOfRangeFixture = seedFixture(ONE_HUNDRED);
        String outOfRangeIdentifier = "yield-output-" + UUID.randomUUID();
        ResponseEntity<String> outOfRange = yieldProcessingRequest(outOfRangeFixture, outOfRangeIdentifier, new BigDecimal("39.00"));
        assertEquals(HttpStatus.OK, outOfRange.getStatusCode(), outOfRange::getBody);
        assertSuccessfulProcessingGraph(outOfRangeFixture.primarySourceId(), outOfRangeIdentifier, ONE_HUNDRED, new BigDecimal("39.00"));
        assertYieldState(outOfRangeFixture.primarySourceId(), new BigDecimal("39.00"), true);
    }

    @Test
    @DisplayName("processing yield normalizes the input against a distinct output unit weight")
    void configuredYieldNormalizesDifferentInputAndOutputUnits() {
        Fixture fixture = seedFixture(ONE_HUNDRED);
        String identifier = "yield-output-" + UUID.randomUUID();

        ResponseEntity<String> response = yieldProcessingRequest(fixture, identifier, fixture.outputSemiProductId(),
                new BigDecimal("25.00"));

        assertEquals(HttpStatus.OK, response.getStatusCode(), response::getBody);
        assertSuccessfulProcessingGraph(fixture.primarySourceId(), identifier, ONE_HUNDRED, new BigDecimal("25.00"));
        assertYieldState(fixture.primarySourceId(), new BigDecimal("25.00"), false);
    }

    private List<ProcessingRequestResult> concurrentProcessingRequests(Fixture fixture, List<Long> sourceIds,
                                                                        BigDecimal quantity, boolean awaitGate)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(sourceIds.size());
        CountDownLatch ready = new CountDownLatch(sourceIds.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ProcessingRequestResult>> futures = new ArrayList<>();
            for (int index = 0; index < sourceIds.size(); index++) {
                Long sourceId = sourceIds.get(index);
                String identifier = "concurrent-output-" + UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to start processing requests");
                    }
                    return new ProcessingRequestResult(sourceId, identifier, quantity, quantity,
                            processingRequest(fixture, sourceId, quantity, "GENERAL_ORDER", identifier));
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS), "all workers should be ready before the burst");
            start.countDown();
            if (awaitGate) {
                assertTrue(reservationGate.awaitArrivals(), "all requests should reach the reservation boundary");
                assertTrue(reservationGate.observedDistinctRequestsOnSameVersion(),
                        "each worker must read the same source version before competing writes are released");
                reservationGate.release();
            }
            List<ProcessingRequestResult> results = new ArrayList<>();
            for (Future<ProcessingRequestResult> future : futures) {
                results.add(future.get(45, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            reservationGate.release();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "request workers should finish");
            reservationGate = ReservationGate.disabled();
        }
    }

    private EditAndConsumptionResult concurrentEditAndConsumption(Fixture fixture, BigDecimal quantity) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            String consumeIdentifier = "concurrent-output-" + UUID.randomUUID();
            Future<ProcessingRequestResult> consume = executor.submit(() -> {
                ready.countDown();
                if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                return new ProcessingRequestResult(fixture.primarySourceId(), consumeIdentifier, quantity, quantity,
                        processingRequest(fixture, fixture.primarySourceId(), quantity, "GENERAL_ORDER", consumeIdentifier));
            });
            Future<ResponseEntity<String>> edit = executor.submit(() -> {
                ready.countDown();
                if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                return sourceEditRequest(fixture);
            });
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(reservationGate.awaitArrivals(), "both writes should read the same source state");
            assertTrue(reservationGate.observedDistinctRequestsOnSameVersion(),
                    "both requests must read the same source version before competing writes are released");
            reservationGate.release();
            return new EditAndConsumptionResult(consume.get(45, TimeUnit.SECONDS), edit.get(45, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            reservationGate.release();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            reservationGate = ReservationGate.disabled();
        }
    }

    private ResponseEntity<String> processingRequest(Fixture fixture, Long sourceId, BigDecimal quantity,
                                                      String targetOrderType, String identifier) {
        return processingRequest(fixture, sourceId, sourceRequestState(sourceId), quantity, targetOrderType, identifier);
    }

    private ResponseEntity<String> processingRequest(Fixture fixture, Long sourceId, SourceRequestState source,
                                                      BigDecimal quantity, String targetOrderType, String identifier) {
        String body = """
                {"processingAction":{"id":%d},"initiatorUserId":%d,"processingDate":"2026-10-19",
                 "inputTransactions":[{"company":{"id":%d},"sourceStockOrder":{"id":%d,
                   "orderType":"PURCHASE_ORDER","totalQuantity":%s,"fulfilledQuantity":%s,
                   "availableQuantity":%s},"status":"PENDING","inputQuantity":%s,"outputQuantity":%s}],
                 "targetStockOrders":[{"identifier":"%s","orderType":"%s","company":{"id":%d},
                   "facility":{"id":%d},"quoteFacility":{"id":%d},"semiProduct":{"id":%d},
                   "totalQuantity":%s,"fulfilledQuantity":0,"availableQuantity":0}]}
                """.formatted(fixture.processingActionId(), fixture.userId(), fixture.companyId(), sourceId,
                source.totalQuantity(), source.fulfilledQuantity(), source.availableQuantity(), quantity, quantity,
                identifier, targetOrderType, fixture.companyId(), fixture.facilityId(),
                fixture.facilityId(), fixture.semiProductId(), quantity);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, fixture.accessCookie());
        return rest.exchange("/api/chain/processing-order", HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> sourceEditRequest(Fixture fixture) {
        String body = """
                {"id":%d,"identifier":"concurrent-edited-source-%s","orderType":"PURCHASE_ORDER",
                 "facility":{"id":%d},"semiProduct":{"id":%d},"producerUserCustomer":{"id":%d},
                 "totalQuantity":120,"totalGrossQuantity":120,"fulfilledQuantity":120,"pricePerUnit":1,
                 "preferredWayOfPayment":"BANK_TRANSFER","productionDate":"2026-10-19","currency":"USD"}
                """.formatted(fixture.primarySourceId(), UUID.randomUUID(), fixture.facilityId(),
                fixture.semiProductId(), fixture.farmerId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, fixture.accessCookie());
        return rest.exchange("/api/chain/stock-order", HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> yieldProcessingRequest(Fixture fixture, String identifier, BigDecimal outputQuantity) {
        return yieldProcessingRequest(fixture, identifier, fixture.semiProductId(), outputQuantity);
    }

    private ResponseEntity<String> yieldProcessingRequest(Fixture fixture, String identifier, Long outputSemiProductId,
                                                           BigDecimal outputQuantity) {
        String body = """
                {"processingAction":{"id":%d},"initiatorUserId":%d,"processingDate":"2026-10-19",
                 "inputTransactions":[{"company":{"id":%d},"sourceStockOrder":{"id":%d,
                   "orderType":"PURCHASE_ORDER","totalQuantity":%s,"fulfilledQuantity":%s,
                   "availableQuantity":%s},"status":"PENDING","inputQuantity":100,"outputQuantity":100}],
                 "targetStockOrders":[{"identifier":"%s","orderType":"PROCESSING_ORDER",
                   "company":{"id":%d},"facility":{"id":%d},"semiProduct":{"id":%d},
                   "totalQuantity":%s,"fulfilledQuantity":%s,"availableQuantity":%s}]}
                """.formatted(fixture.yieldProcessingActionId(), fixture.userId(), fixture.companyId(),
                fixture.primarySourceId(), fixture.initialQuantity(), fixture.initialQuantity(), fixture.initialQuantity(),
                identifier, fixture.companyId(), fixture.facilityId(),
                outputSemiProductId, outputQuantity, outputQuantity, outputQuantity);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, fixture.accessCookie());
        return rest.exchange("/api/chain/processing-order", HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);
    }

    private Fixture seedFixture(BigDecimal available) {
        Fixture fixture = tx().execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Company company = new Company();
            company.setName("Concurrent stock " + suffix);
            company.setStatus(CompanyStatus.ACTIVE);
            em.persist(company);

            User user = new User();
            user.setEmail("stock-concurrency-" + suffix + "@test.invalid");
            user.setName("Concurrent");
            user.setSurname("Tester");
            user.setPassword("not-used");
            user.setRole(UserRole.USER);
            user.setStatus(UserStatus.ACTIVE);
            em.persist(user);
            CompanyUser enrollment = new CompanyUser();
            enrollment.setCompany(company);
            enrollment.setUser(user);
            enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
            em.persist(enrollment);
            company.getUsers().add(enrollment);

            ProductType productType = new ProductType();
            productType.setCode("CONCURRENT_" + suffix);
            productType.setName("Concurrency test crop");
            em.persist(productType);
            ValueChain valueChain = new ValueChain();
            valueChain.setName("Concurrency value chain " + suffix);
            valueChain.setDescription("Value chain used by stock concurrency tests");
            valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
            valueChain.setProductType(productType);
            valueChain.setCreatedBy(user);
            em.persist(valueChain);
            Product product = new Product();
            product.setName("Concurrency product " + suffix);
            product.setCompany(company);
            product.setValueChain(valueChain);
            product.setProcess(null);
            product.setResponsibility(null);
            product.setSustainability(null);
            product.setJourney(null);
            product.setSettings(null);
            product.setBusinessToCustomerSettings(null);
            em.persist(product);
            ProductCompany owner = new ProductCompany();
            owner.setProduct(product);
            owner.setCompany(company);
            owner.setType(ProductCompanyType.OWNER);
            em.persist(owner);
            product.getAssociatedCompanies().add(owner);

            FacilityType facilityType = new FacilityType("CONCURRENT_" + suffix, "Concurrency facility");
            em.persist(facilityType);
            FacilityLocation location = new FacilityLocation();
            em.persist(location);
            Facility facility = new Facility();
            facility.setName("Concurrency facility " + suffix);
            facility.setCompany(company);
            facility.setFacilityLocation(location);
            facility.setFacilityType(facilityType);
            facility.setIsCollectionFacility(true);
            facility.setIsDeactivated(false);
            em.persist(facility);

            MeasureUnitType measure = new MeasureUnitType("CONCURRENT_KG_" + suffix,
                    "Concurrency kilogram", BigDecimal.ONE);
            em.persist(measure);
            MeasureUnitType outputMeasure = new MeasureUnitType("CONCURRENT_OUTPUT_" + suffix,
                    "Concurrency output unit", new BigDecimal("2.00"));
            em.persist(outputMeasure);
            SemiProduct semiProduct = new SemiProduct();
            semiProduct.setName("Concurrency semi product " + suffix);
            semiProduct.setBuyable(true);
            semiProduct.setMeasurementUnitType(measure);
            em.persist(semiProduct);
            SemiProduct outputSemiProduct = new SemiProduct();
            outputSemiProduct.setName("Concurrency output semi product " + suffix);
            outputSemiProduct.setBuyable(true);
            outputSemiProduct.setMeasurementUnitType(outputMeasure);
            em.persist(outputSemiProduct);

            UserCustomer farmer = new UserCustomer();
            farmer.setName("Concurrency farmer");
            farmer.setSurname(suffix);
            farmer.setType(UserCustomerType.FARMER);
            farmer.setCompany(company);
            farmer.setProduct(product);
            em.persist(farmer);

            ProcessingAction action = new ProcessingAction();
            action.setCompany(company);
            action.setType(ProcessingActionType.SHIPMENT);
            action.setInputSemiProduct(semiProduct);
            action.setPrefix("CONCURRENT");
            em.persist(action);
            ProcessingAction yieldAction = new ProcessingAction();
            yieldAction.setCompany(company);
            yieldAction.setType(ProcessingActionType.PROCESSING);
            yieldAction.setInputSemiProduct(semiProduct);
            yieldAction.setEstimatedOutputQuantityPerUnit(new BigDecimal("0.50"));
            yieldAction.setRepackedOutputFinalProducts(false);
            yieldAction.setPrefix("YIELD");
            em.persist(yieldAction);
            ProcessingActionOutputSemiProduct yieldOutput = new ProcessingActionOutputSemiProduct();
            yieldOutput.setProcessingAction(yieldAction);
            yieldOutput.setOutputSemiProduct(semiProduct);
            yieldOutput.setRepackedOutput(false);
            em.persist(yieldOutput);
            yieldAction.getOutputSemiProducts().add(yieldOutput);
            ProcessingActionOutputSemiProduct distinctYieldOutput = new ProcessingActionOutputSemiProduct();
            distinctYieldOutput.setProcessingAction(yieldAction);
            distinctYieldOutput.setOutputSemiProduct(outputSemiProduct);
            distinctYieldOutput.setRepackedOutput(false);
            em.persist(distinctYieldOutput);
            yieldAction.getOutputSemiProducts().add(distinctYieldOutput);

            StockOrder primary = purchaseSource(company, facility, semiProduct, measure, farmer, user,
                    "concurrent-primary-" + suffix, available);
            StockOrder secondary = purchaseSource(company, facility, semiProduct, measure, farmer, user,
                    "concurrent-secondary-" + suffix, available);
            em.flush();
            return new Fixture(company.getId(), user.getId(), productType.getId(), valueChain.getId(), product.getId(),
                    facilityType.getId(), location.getId(), facility.getId(), farmer.getId(), semiProduct.getId(), measure.getId(),
                    outputSemiProduct.getId(), outputMeasure.getId(),
                    action.getId(), yieldAction.getId(), primary.getId(), secondary.getId(),
                    available, accessCookieName + "=" + tokenService.createAccessToken(user));
        });
        fixtures.add(fixture);
        return fixture;
    }

    private void removeFixture(Fixture fixture) {
        List<Long> actionIds = List.of(fixture.processingActionId(), fixture.yieldProcessingActionId());
        List<Long> sourceIds = List.of(fixture.primarySourceId(), fixture.secondarySourceId());

        em.createQuery("DELETE FROM StockOrder so WHERE so.processingOrder.processingAction.id IN :actionIds")
                .setParameter("actionIds", actionIds).executeUpdate();
        em.createQuery("DELETE FROM Transaction t WHERE t.sourceStockOrder.id IN :sourceIds "
                        + "OR t.targetProcessingOrder.processingAction.id IN :actionIds")
                .setParameter("sourceIds", sourceIds).setParameter("actionIds", actionIds).executeUpdate();
        em.createQuery("DELETE FROM ProcessingOrder po WHERE po.processingAction.id IN :actionIds")
                .setParameter("actionIds", actionIds).executeUpdate();
        em.createQuery("DELETE FROM StockOrder so WHERE so.id IN :sourceIds")
                .setParameter("sourceIds", sourceIds).executeUpdate();
        em.createQuery("DELETE FROM ProcessingActionOutputSemiProduct output "
                        + "WHERE output.processingAction.id IN :actionIds")
                .setParameter("actionIds", actionIds).executeUpdate();
        em.createQuery("DELETE FROM ProcessingAction action WHERE action.id IN :actionIds")
                .setParameter("actionIds", actionIds).executeUpdate();
        em.createQuery("DELETE FROM ProductCompany productCompany WHERE productCompany.product.id = :productId")
                .setParameter("productId", fixture.productId()).executeUpdate();
        em.createQuery("DELETE FROM UserCustomer customer WHERE customer.id = :farmerId")
                .setParameter("farmerId", fixture.farmerId()).executeUpdate();
        em.createQuery("DELETE FROM Facility facility WHERE facility.id = :facilityId")
                .setParameter("facilityId", fixture.facilityId()).executeUpdate();
        em.createQuery("DELETE FROM FacilityLocation location WHERE location.id = :locationId")
                .setParameter("locationId", fixture.locationId()).executeUpdate();
        em.createQuery("DELETE FROM Product product WHERE product.id = :productId")
                .setParameter("productId", fixture.productId()).executeUpdate();
        em.createQuery("DELETE FROM ValueChain valueChain WHERE valueChain.id = :valueChainId")
                .setParameter("valueChainId", fixture.valueChainId()).executeUpdate();
        em.createQuery("DELETE FROM ProductType productType WHERE productType.id = :productTypeId")
                .setParameter("productTypeId", fixture.productTypeId()).executeUpdate();
        em.createQuery("DELETE FROM SemiProduct semiProduct WHERE semiProduct.id IN :semiProductIds")
                .setParameter("semiProductIds", List.of(fixture.semiProductId(), fixture.outputSemiProductId()))
                .executeUpdate();
        em.createQuery("DELETE FROM MeasureUnitType measure WHERE measure.id IN :measureIds")
                .setParameter("measureIds", List.of(fixture.measureId(), fixture.outputMeasureId())).executeUpdate();
        em.createQuery("DELETE FROM FacilityType facilityType WHERE facilityType.id = :facilityTypeId")
                .setParameter("facilityTypeId", fixture.facilityTypeId()).executeUpdate();
        em.createQuery("DELETE FROM CompanyUser enrollment WHERE enrollment.company.id = :companyId")
                .setParameter("companyId", fixture.companyId()).executeUpdate();
        em.createQuery("DELETE FROM Company company WHERE company.id = :companyId")
                .setParameter("companyId", fixture.companyId()).executeUpdate();
        em.createQuery("DELETE FROM User user WHERE user.id = :userId")
                .setParameter("userId", fixture.userId()).executeUpdate();
        em.clear();
    }

    private StockOrder purchaseSource(Company company, Facility facility, SemiProduct semiProduct,
                                      MeasureUnitType measure, UserCustomer farmer, User user,
                                      String identifier, BigDecimal quantity) {
        StockOrder order = new StockOrder();
        order.setCompany(company);
        order.setFacility(facility);
        order.setSemiProduct(semiProduct);
        order.setMeasurementUnitType(measure);
        order.setProducerUserCustomer(farmer);
        order.setCreatedBy(user);
        order.setUpdatedBy(user);
        order.setIdentifier(identifier);
        order.setOrderType(OrderType.PURCHASE_ORDER);
        order.setPreferredWayOfPayment(PreferredWayOfPayment.BANK_TRANSFER);
        order.setPurchaseOrder(true);
        order.setAvailable(true);
        order.setTotalQuantity(quantity);
        order.setTotalGrossQuantity(quantity);
        order.setFulfilledQuantity(quantity);
        order.setAvailableQuantity(quantity);
        order.setPricePerUnit(BigDecimal.ONE);
        order.setCost(quantity);
        order.setProductionDate(LocalDate.of(2026, 10, 19));
        em.persist(order);
        return order;
    }

    private SourceRequestState sourceRequestState(Long sourceId) {
        return tx().execute(status -> {
            StockOrder source = em.find(StockOrder.class, sourceId);
            assertNotNull(source);
            return new SourceRequestState(source.getTotalQuantity(), source.getFulfilledQuantity(),
                    source.getAvailableQuantity());
        });
    }

    private PersistedState persistedState(Fixture fixture, Long sourceId) {
        return tx().execute(status -> {
            StockOrder source = em.find(StockOrder.class, sourceId);
            assertNotNull(source);
            BigDecimal consumed = em.createQuery("SELECT COALESCE(SUM(t.outputQuantity), 0) FROM Transaction t "
                            + "WHERE t.sourceStockOrder.id = :sourceId", BigDecimal.class)
                    .setParameter("sourceId", sourceId).getSingleResult();
            long transactions = em.createQuery("SELECT COUNT(t) FROM Transaction t WHERE t.sourceStockOrder.id = :sourceId",
                            Long.class).setParameter("sourceId", sourceId).getSingleResult();
            long linkedProcessing = em.createQuery("SELECT COUNT(DISTINCT t.targetProcessingOrder) FROM Transaction t "
                            + "WHERE t.sourceStockOrder.id = :sourceId", Long.class)
                    .setParameter("sourceId", sourceId).getSingleResult();
            long linkedOutputs = em.createQuery("SELECT COUNT(so) FROM StockOrder so WHERE so.processingOrder.id IN "
                            + "(SELECT t.targetProcessingOrder.id FROM Transaction t WHERE t.sourceStockOrder.id = :sourceId)",
                            Long.class).setParameter("sourceId", sourceId).getSingleResult();
            List<Long> actions = List.of(fixture.processingActionId(), fixture.yieldProcessingActionId());
            long processing = em.createQuery("SELECT COUNT(po) FROM ProcessingOrder po "
                            + "WHERE po.processingAction.id IN :actions", Long.class)
                    .setParameter("actions", actions).getSingleResult();
            long outputs = em.createQuery("SELECT COUNT(so) FROM StockOrder so "
                            + "WHERE so.processingOrder.processingAction.id IN :actions", Long.class)
                    .setParameter("actions", actions).getSingleResult();
            BigDecimal outputQuantity = em.createQuery("SELECT COALESCE(SUM(so.totalQuantity), 0) FROM StockOrder so "
                            + "WHERE so.processingOrder.processingAction.id IN :actions", BigDecimal.class)
                    .setParameter("actions", actions).getSingleResult();
            return new PersistedState(source.getTotalQuantity(), source.getAvailableQuantity(), consumed, processing,
                    transactions, outputs, outputQuantity, linkedProcessing, linkedOutputs);
        });
    }

    private void assertReservationInvariant(PersistedState state, BigDecimal originalQuantity) {
        assertEquals(0, originalQuantity.subtract(state.committedInputQuantity())
                .compareTo(state.availableQuantity()), "available quantity must equal original minus committed inputs");
    }

    private void assertSourceState(Fixture fixture, BigDecimal expectedTotal, BigDecimal expectedAvailable,
                                   long expectedProcessingOrders) {
        PersistedState state = persistedState(fixture, fixture.primarySourceId());
        assertEquals(0, expectedTotal.compareTo(state.totalQuantity()));
        assertEquals(0, expectedAvailable.compareTo(state.availableQuantity()));
        assertEquals(expectedProcessingOrders, state.processingOrders());
        assertEquals(expectedProcessingOrders, state.inputTransactions());
        assertEquals(expectedProcessingOrders, state.outputStockOrders());
        assertEquals(0, state.committedInputQuantity().compareTo(state.outputQuantity()));
        assertReservationInvariant(state, expectedTotal);
    }

    private void assertYieldState(Long sourceId, BigDecimal expectedOutput, boolean expectedOutOfRange) {
        Fixture fixture = fixtures.stream().filter(candidate -> candidate.primarySourceId().equals(sourceId))
                .findFirst().orElseThrow();
        PersistedState state = persistedState(fixture, sourceId);
        assertEquals(0, BigDecimal.ZERO.compareTo(state.availableQuantity()));
        assertEquals(0, ONE_HUNDRED.compareTo(state.committedInputQuantity()));
        StockOrder output = tx().execute(status -> em.createQuery("SELECT so FROM StockOrder so "
                        + "WHERE so.processingOrder.id IN (SELECT t.targetProcessingOrder.id FROM Transaction t "
                        + "WHERE t.sourceStockOrder.id = :sourceId)", StockOrder.class)
                .setParameter("sourceId", sourceId).getSingleResult());
        assertEquals(0, expectedOutput.compareTo(output.getTotalQuantity()));
        assertEquals(expectedOutOfRange, output.getOutQuantityNotInRange());
        assertReservationInvariant(state, ONE_HUNDRED);
    }

    private void assertProcessingResultsMatchPersistence(List<ProcessingRequestResult> results) {
        for (ProcessingRequestResult result : results) {
            if (isSuccess(result)) {
                assertSuccessfulProcessingGraph(result.sourceId(), result.identifier(), result.inputQuantity(),
                        result.outputQuantity());
            } else {
                assertNoProcessingOutput(result.identifier());
            }
        }
    }

    private void assertSuccessfulProcessingGraph(Long sourceId, String identifier, BigDecimal expectedInput,
                                                 BigDecimal expectedOutput) {
        tx().executeWithoutResult(status -> {
            List<StockOrder> outputs = em.createQuery("SELECT so FROM StockOrder so WHERE so.identifier = :identifier",
                            StockOrder.class)
                    .setParameter("identifier", identifier).getResultList();
            assertEquals(1, outputs.size(), "a successful response must persist exactly its own output identifier");
            StockOrder output = outputs.get(0);
            assertNotNull(output.getProcessingOrder(), "the output must belong to a processing order");
            assertEquals(0, expectedOutput.compareTo(output.getTotalQuantity()));
            List<Transaction> inputs = em.createQuery("SELECT t FROM Transaction t "
                            + "WHERE t.targetProcessingOrder.id = :processingOrderId", Transaction.class)
                    .setParameter("processingOrderId", output.getProcessingOrder().getId()).getResultList();
            assertEquals(1, inputs.size(), "the processing order must contain exactly its input transaction");
            Transaction input = inputs.get(0);
            assertEquals(sourceId, input.getSourceStockOrder().getId());
            assertEquals(0, expectedInput.compareTo(input.getOutputQuantity()));
        });
    }

    private void assertNoProcessingOutput(String identifier) {
        tx().executeWithoutResult(status -> {
            long outputs = em.createQuery("SELECT COUNT(so) FROM StockOrder so WHERE so.identifier = :identifier",
                            Long.class)
                    .setParameter("identifier", identifier).getSingleResult();
            assertEquals(0, outputs, "a failed response must not leave its output identifier behind");
        });
    }

    private boolean isSuccess(ResponseEntity<String> response) {
        return response.getStatusCode().is2xxSuccessful();
    }

    private boolean isSuccess(ProcessingRequestResult result) {
        return isSuccess(result.response());
    }

    private void assertConcurrentResponsesAreSuccessfulOrConflicts(List<ProcessingRequestResult> results) {
        assertTrue(results.stream().allMatch(result -> isSuccess(result)
                        || result.response().getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                        || result.response().getStatusCode() == HttpStatus.CONFLICT),
                () -> "concurrent requests must not be mistaken for validation or authorization failures: " + describe(results));
    }

    private String describe(List<ProcessingRequestResult> results) {
        return results.stream().map(result -> result.identifier() + "=" + result.response().getStatusCode().value()
                + " " + result.response().getBody()).toList().toString();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    private record Fixture(Long companyId, Long userId, Long productTypeId, Long valueChainId, Long productId,
                           Long facilityTypeId, Long locationId, Long facilityId, Long farmerId, Long semiProductId,
                           Long measureId, Long outputSemiProductId, Long outputMeasureId,
                           Long processingActionId, Long yieldProcessingActionId, Long primarySourceId,
                           Long secondarySourceId, BigDecimal initialQuantity, String accessCookie) {
    }

    private record SourceRequestState(BigDecimal totalQuantity, BigDecimal fulfilledQuantity, BigDecimal availableQuantity) {
    }

    private record ProcessingRequestResult(Long sourceId, String identifier, BigDecimal inputQuantity,
                                           BigDecimal outputQuantity, ResponseEntity<String> response) {
    }

    private record EditAndConsumptionResult(ProcessingRequestResult consumption, ResponseEntity<String> editResponse) {
    }

    private record PersistedState(BigDecimal totalQuantity, BigDecimal availableQuantity, BigDecimal committedInputQuantity,
                                  long processingOrders, long inputTransactions, long outputStockOrders,
                                  BigDecimal outputQuantity, long linkedProcessingOrders, long linkedOutputStockOrders) {
    }

    private static final class ReservationGate {
        private static final Field ENTITY_VERSION = entityVersionField();
        private final Long sourceId;
        private final int expectedArrivals;
        private final CountDownLatch arrivals;
        private final CountDownLatch release = new CountDownLatch(1);
        private final Set<Long> workerThreads = ConcurrentHashMap.newKeySet();
        private final Set<Long> sourceVersions = ConcurrentHashMap.newKeySet();

        private ReservationGate(Long sourceId, int expectedArrivals) {
            this.sourceId = sourceId;
            this.expectedArrivals = expectedArrivals;
            this.arrivals = new CountDownLatch(expectedArrivals);
        }

        static ReservationGate forSource(Long sourceId, int expectedArrivals) {
            return new ReservationGate(sourceId, expectedArrivals);
        }

        static ReservationGate disabled() {
            return new ReservationGate(null, 0);
        }

        void awaitIfReservedSource(StockOrder candidate) {
            if (sourceId == null || !sourceId.equals(candidate.getId())) {
                return;
            }
            workerThreads.add(Thread.currentThread().getId());
            sourceVersions.add(entityVersion(candidate));
            arrivals.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting at the reservation boundary");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted at the reservation boundary", exception);
            }
        }

        boolean awaitArrivals() throws InterruptedException {
            return arrivals.await(20, TimeUnit.SECONDS);
        }

        boolean observedDistinctRequestsOnSameVersion() {
            return workerThreads.size() == expectedArrivals
                    && sourceVersions.size() == 1;
        }

        void release() {
            release.countDown();
        }

        private static Field entityVersionField() {
            try {
                Field field = StockOrder.class.getDeclaredField("entityVersion");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException exception) {
                throw new IllegalStateException("StockOrder version field is required for the concurrency test", exception);
            }
        }

        private static Long entityVersion(StockOrder stockOrder) {
            try {
                return (Long) ENTITY_VERSION.get(stockOrder);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot observe StockOrder version in concurrency test", exception);
            }
        }
    }

    private static final class WriteObservation {
        private final Long sourceId;
        private volatile Long processingOrderId;
        private volatile Long inputTransactionId;

        private WriteObservation(Long sourceId) {
            this.sourceId = sourceId;
        }

        static WriteObservation forSource(Long sourceId) {
            return new WriteObservation(sourceId);
        }

        static WriteObservation disabled() {
            return new WriteObservation(null);
        }

        void record(StockOrder source, ProcessingOrder processingOrder, Long newInputTransactionId) {
            if (sourceId == null || !sourceId.equals(source.getId())) {
                return;
            }
            processingOrderId = processingOrder.getId();
            inputTransactionId = newInputTransactionId;
        }

        boolean sawIdentityInsertsBeforeLaterValidation() {
            return processingOrderId != null && inputTransactionId != null;
        }
    }
}
