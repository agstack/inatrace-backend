package com.abelium.inatrace.components.transaction;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.configuration.DefaultControllerExceptionHandler;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.payment.PaymentStatus;
import com.abelium.inatrace.db.entities.payment.PaymentType;
import com.abelium.inatrace.db.entities.payment.RecipientType;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.UserCustomerType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Exercises the public transaction-transition endpoints with independent HTTP
 * requests. The entry gates exist only in this test: they align requests before
 * the real service starts its transaction and never change production behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionTransitionConcurrencyTest extends AbstractMySqlIntegrationTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");
    private static final BigDecimal SEVEN = new BigDecimal("7.00");
    private static final BigDecimal THREE = new BigDecimal("3.00");
    private static final AtomicInteger COUNTRY_SEQUENCE = new AtomicInteger();

    @Autowired private TestRestTemplate rest;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private TokenService tokenService;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @PersistenceContext private EntityManager em;

    @SpyBean private DefaultControllerExceptionHandler exceptionHandler;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private volatile TransitionGate gate = TransitionGate.disabled();
    private final KnownFailureRecorder failureRecorder = new KnownFailureRecorder();
    private final TransactionEffectRecorder effectRecorder = new TransactionEffectRecorder();
    private final List<Fixture> fixtures = new CopyOnWriteArrayList<>();

    @BeforeEach
    void configureHttpClient() throws Exception {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        doAnswer(invocation -> {
            failureRecorder.record((Throwable) invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(exceptionHandler).handleException(any(Exception.class), any(HttpServletRequest.class));
        failureRecorder.clear();
        effectRecorder.clear();
    }

    @BeforeAll
    void observeLoadedTransactions() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(new PostLoadEventListener() {
            @Override
            public void onPostLoad(PostLoadEvent event) {
                if (event.getEntity() instanceof Transaction transaction) {
                    gate.afterTransactionLoad(transaction, TransactionSynchronizationManager.isActualTransactionActive());
                }
            }
        });
        registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(new PostUpdateEventListener() {
            @Override
            public void onPostUpdate(PostUpdateEvent event) {
                if (event.getEntity() instanceof Transaction transaction
                        && transaction.getStatus() == TransactionStatus.CANCELED) {
                    effectRecorder.recordCancellation(transaction.getId());
                }
            }

            @Override
            public boolean requiresPostCommitHandling(EntityPersister persister) {
                return false;
            }
        });
    }

    @AfterEach
    void removeFixtures() {
        gate.release();
        gate = TransitionGate.disabled();
        tx().executeWithoutResult(status -> fixtures.forEach(this::removeFixture));
        fixtures.clear();
    }

    @Test
    @DisplayName("competing approvals credit a pending transaction at most once")
    void concurrentApprovalsCreditTheTargetOnce() throws Exception {
        Fixture fixture = seedFixture();
        gate = TransitionGate.forTransaction(fixture.transactionId(), 2);

        List<RequestResult> responses = concurrent(List.of(
                request("approve-first", () -> approve(fixture)),
                request("approve-second", () -> approve(fixture))));

        assertGateObservedSamePendingVersion();
        assertExactlyOneSuccess(responses);
        assertOnlyDocumentedTransitionResponses(responses);
        State state = state(fixture);
        assertEquals(TransactionStatus.EXECUTED, state.status());
        assertDecimal(SEVEN, state.sourceAvailable());
        assertDecimal(THREE, state.targetAvailable());
        assertGraphPresent(fixture, state);
        assertResponsesMatchCommittedState(fixture, responses, state);
    }

    @Test
    @DisplayName("competing rejections restore a pending reservation at most once")
    void concurrentRejectionsRestoreTheSourceOnce() throws Exception {
        Fixture fixture = seedFixture();
        gate = TransitionGate.forTransaction(fixture.transactionId(), 2);

        List<RequestResult> responses = concurrent(List.of(
                request("reject-first", () -> reject(fixture, "first concurrent rejection")),
                request("reject-second", () -> reject(fixture, "second concurrent rejection"))));

        assertGateObservedSamePendingVersion();
        assertExactlyOneSuccess(responses);
        assertOnlyDocumentedTransitionResponses(responses);
        State state = state(fixture);
        assertEquals(TransactionStatus.CANCELED, state.status());
        assertDecimal(TEN, state.sourceAvailable());
        assertDecimal(BigDecimal.ZERO, state.targetAvailable());
        assertTrue(state.rejectComment().contains("concurrent rejection"));
        assertGraphPresent(fixture, state);
        assertResponsesMatchCommittedState(fixture, responses, state);
    }

    @Test
    @DisplayName("approval and rejection leave one permitted economic transition")
    void concurrentApprovalAndRejectionMatchOneSequentialOutcome() throws Exception {
        Fixture fixture = seedFixture();
        gate = TransitionGate.forTransaction(fixture.transactionId(), 2);

        List<RequestResult> responses = concurrent(List.of(
                request("approve", () -> approve(fixture)),
                request("reject", () -> reject(fixture, "competing rejection"))));

        assertGateObservedSamePendingVersion();
        assertExactlyOneSuccess(responses);
        assertOnlyDocumentedTransitionResponses(responses);
        State state = state(fixture);
        boolean approved = state.status() == TransactionStatus.EXECUTED
                && state.sourceAvailable().compareTo(SEVEN) == 0
                && state.targetAvailable().compareTo(THREE) == 0;
        boolean rejected = state.status() == TransactionStatus.CANCELED
                && state.sourceAvailable().compareTo(TEN) == 0
                && state.targetAvailable().compareTo(BigDecimal.ZERO) == 0;
        assertTrue(approved || rejected, () -> "unexpected final state: " + state);
        assertGraphPresent(fixture, state);
        assertResponsesMatchCommittedState(fixture, responses, state);
    }

    @Test
    @DisplayName("rejection racing processing-order deletion restores the reservation once")
    void concurrentRejectionAndDeletionDoNotDoubleRestore() throws Exception {
        Fixture fixture = seedFixture();
        gate = TransitionGate.forTransaction(fixture.transactionId(), 2);

        List<RequestResult> responses = concurrent(List.of(
                request("reject", () -> reject(fixture, "reject before delete")),
                request("delete", () -> delete(fixture))));

        assertGateObservedSamePendingVersion();
        assertAtLeastOneSuccess(responses);
        assertOnlyDocumentedTransitionResponses(responses);
        State state = state(fixture);
        assertDecimal(TEN, state.sourceAvailable());
        if (state.processingOrderPresent()) {
            assertEquals(TransactionStatus.CANCELED, state.status());
            assertDecimal(BigDecimal.ZERO, state.targetAvailable());
            assertGraphPresent(fixture, state);
        } else {
            assertFalse(state.transactionPresent());
            assertFalse(state.targetPresent());
        }
        assertResponsesMatchCommittedState(fixture, responses, state);
    }

    @Test
    @DisplayName("competing processing-order deletions remove one graph and restore once")
    void concurrentDeletesRemoveTheGraphWithoutDoubleRestoration() throws Exception {
        Fixture fixture = seedFixture();
        gate = TransitionGate.forTransaction(fixture.transactionId(), 2);

        List<RequestResult> responses = concurrent(List.of(
                request("delete-first", () -> delete(fixture)),
                request("delete-second", () -> delete(fixture))));

        assertGateObservedSamePendingVersion();
        assertExactlyOneSuccess(responses);
        assertOnlyDocumentedTransitionResponses(responses);
        State state = state(fixture);
        assertDecimal(TEN, state.sourceAvailable());
        assertFalse(state.processingOrderPresent());
        assertFalse(state.transactionPresent());
        assertFalse(state.targetPresent());
        assertResponsesMatchCommittedState(fixture, responses, state);
    }

    @Test
    @DisplayName("sequential controls establish approval, rejection and deletion accounting")
    void sequentialControlsEstablishThePermittedOutcomes() {
        Fixture approved = seedFixture();
        assertSuccess(approve(approved));
        State approvedState = state(approved);
        assertEquals(TransactionStatus.EXECUTED, approvedState.status());
        assertDecimal(SEVEN, approvedState.sourceAvailable());
        assertDecimal(THREE, approvedState.targetAvailable());

        Fixture rejected = seedFixture();
        assertSuccess(reject(rejected, "sequential rejection"));
        State rejectedState = state(rejected);
        assertEquals(TransactionStatus.CANCELED, rejectedState.status());
        assertDecimal(TEN, rejectedState.sourceAvailable());
        assertDecimal(BigDecimal.ZERO, rejectedState.targetAvailable());
        assertBadRequest(reject(rejected, "second rejection"), "Only PENDING transactions can be rejected.");

        assertSuccess(delete(rejected));
        State deletedState = state(rejected);
        assertDecimal(TEN, deletedState.sourceAvailable());
        assertFalse(deletedState.processingOrderPresent());
        assertFalse(deletedState.transactionPresent());
        assertFalse(deletedState.targetPresent());

        Fixture deletedPending = seedFixture();
        assertSuccess(delete(deletedPending));
        State deletedPendingState = state(deletedPending);
        assertDecimal(TEN, deletedPendingState.sourceAvailable());
        assertFalse(deletedPendingState.processingOrderPresent());
        assertFalse(deletedPendingState.transactionPresent());
        assertFalse(deletedPendingState.targetPresent());

        assertBadRequest(approve(approved), "Only PENDING transactions can be approved.");
    }

    @Test
    @DisplayName("rejection restores the source-side reservation when input and output units differ")
    void rejectionUsesTheSourceSideQuantityForDistinctUnits() {
        Fixture fixture = seedFixture(THREE, new BigDecimal("2.00"));

        assertSuccess(reject(fixture, "distinct unit rejection"));

        State state = state(fixture);
        assertEquals(TransactionStatus.CANCELED, state.status());
        assertDecimal(TEN, state.sourceAvailable());
        assertDecimal(BigDecimal.ZERO, state.targetAvailable());
    }

    @Test
    @DisplayName("a delete can remove an already canceled transaction without a second restoration")
    void concurrentDeleteOfAlreadyCanceledTransactionDoesNotRestoreTwice() throws Exception {
        Fixture fixture = seedFixture();
        assertSuccess(reject(fixture, "prepare canceled transaction"));
        gate = TransitionGate.forTransaction(fixture.transactionId(), 2);

        List<RequestResult> responses = concurrent(List.of(
                request("reject-canceled", () -> reject(fixture, "already canceled")),
                request("delete", () -> delete(fixture))));

        assertGateObservedSameVersion(TransactionStatus.CANCELED);
        assertExactlyOneSuccess(responses);
        assertOnlyDocumentedTransitionResponses(responses);
        State state = state(fixture);
        assertDecimal(TEN, state.sourceAvailable());
        assertFalse(state.processingOrderPresent());
        assertFalse(state.transactionPresent());
        assertFalse(state.targetPresent());
        assertResponsesMatchCommittedState(fixture, responses, state);
    }

    @Test
    @DisplayName("a processing order with a consumed output cannot be deleted")
    void processingOrderWithConsumedOutputCannotBeDeleted() {
        Fixture fixture = seedFixture();
        consumeOutput(fixture);

        ResponseEntity<String> response = delete(fixture);

        assertBadRequest(response, "output has been used by another transaction");
        State state = state(fixture);
        assertDecimal(SEVEN, state.sourceAvailable());
        assertDecimal(BigDecimal.ZERO, state.targetAvailable());
        assertGraphPresent(fixture, state);
        assertEquals(1L, state.outputConsumerCount());
    }

    @Test
    @DisplayName("a processing order with a payment on its output cannot be deleted")
    void processingOrderWithPaymentCannotBeDeleted() {
        Fixture fixture = seedFixture();
        addPaymentToOutput(fixture);

        ResponseEntity<String> response = delete(fixture);

        assertBadRequest(response, "output has payments");
        State state = state(fixture);
        assertDecimal(SEVEN, state.sourceAvailable());
        assertDecimal(BigDecimal.ZERO, state.targetAvailable());
        assertGraphPresent(fixture, state);
        assertEquals(1L, state.outputPaymentCount());
    }

    private List<RequestResult> concurrent(List<Request> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RequestResult>> futures = new ArrayList<>();
            for (Request request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to start transition request");
                    }
                    return new RequestResult(request.label(), request.execute().execute());
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS), "all request workers should be ready");
            start.countDown();
            assertTrue(gate.awaitArrivals(), "the selected transition requests should overlap at the service boundary");
            gate.release();
            List<RequestResult> results = new ArrayList<>();
            for (Future<RequestResult> future : futures) {
                results.add(future.get(45, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            gate.release();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "transition workers should finish");
        }
    }

    private ResponseEntity<String> approve(Fixture fixture) {
        return exchange("/api/chain/transaction/" + fixture.transactionId() + "/approve", HttpMethod.PUT, null, fixture);
    }

    private ResponseEntity<String> reject(Fixture fixture, String comment) {
        String body = "{\"id\":" + fixture.transactionId() + ",\"rejectComment\":\"" + comment + "\"}";
        return exchange("/api/chain/transaction/" + fixture.transactionId() + "/reject", HttpMethod.PUT, body, fixture);
    }

    private ResponseEntity<String> delete(Fixture fixture) {
        return exchange("/api/chain/processing-order/" + fixture.processingOrderId(), HttpMethod.DELETE, null, fixture);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String body, Fixture fixture) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, fixture.accessCookie());
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private Fixture seedFixture() {
        return seedFixture(THREE, THREE);
    }

    private Fixture seedFixture(BigDecimal inputQuantity, BigDecimal outputQuantity) {
        Fixture fixture = tx().execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Company company = new Company();
            company.setName("Transition concurrency " + suffix);
            company.setStatus(CompanyStatus.ACTIVE);
            em.persist(company);

            User user = new User();
            user.setEmail("transition-" + suffix + "@test.invalid");
            user.setName("Transition");
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

            FacilityType facilityType = new FacilityType("TRANSITION_" + suffix, "Transition facility");
            em.persist(facilityType);
            Country country = new Country();
            country.setCode(nextCountryCode());
            country.setName("Transition test country");
            em.persist(country);
            FacilityLocation location = new FacilityLocation();
            Address address = new Address();
            address.setAddress("Transition test address");
            address.setCity("Test city");
            address.setCountry(country);
            location.setAddress(address);
            em.persist(location);
            Facility facility = new Facility();
            facility.setName("Transition facility " + suffix);
            facility.setCompany(company);
            facility.setFacilityType(facilityType);
            facility.setFacilityLocation(location);
            facility.setIsCollectionFacility(true);
            facility.setIsDeactivated(false);
            em.persist(facility);

            MeasureUnitType measure = new MeasureUnitType("TRANSITION_KG_" + suffix,
                    "Transition kilogram", BigDecimal.ONE);
            em.persist(measure);
            SemiProduct semiProduct = new SemiProduct();
            semiProduct.setName("Transition semi product " + suffix);
            semiProduct.setBuyable(true);
            semiProduct.setMeasurementUnitType(measure);
            em.persist(semiProduct);
            UserCustomer farmer = new UserCustomer();
            farmer.setName("Transition farmer");
            farmer.setSurname(suffix);
            farmer.setType(UserCustomerType.FARMER);
            farmer.setCompany(company);
            em.persist(farmer);

            ProcessingAction action = new ProcessingAction();
            action.setCompany(company);
            action.setType(ProcessingActionType.SHIPMENT);
            action.setInputSemiProduct(semiProduct);
            action.setPrefix("TRANSITION");
            em.persist(action);
            ProcessingOrder processingOrder = new ProcessingOrder();
            processingOrder.setProcessingAction(action);
            processingOrder.setInitiatorUserId(user.getId());
            processingOrder.setProcessingDate(LocalDate.of(2026, 10, 20));
            em.persist(processingOrder);

            StockOrder source = stockOrder(company, user, facility, semiProduct, measure, farmer,
                    "transition-source-" + suffix, OrderType.PURCHASE_ORDER, TEN, TEN.subtract(outputQuantity));
            em.persist(source);
            StockOrder target = stockOrder(company, user, facility, semiProduct, measure, farmer,
                    "transition-target-" + suffix, OrderType.GENERAL_ORDER, inputQuantity, BigDecimal.ZERO);
            target.setProcessingOrder(processingOrder);
            target.setQuoteFacility(facility);
            em.persist(target);
            processingOrder.getTargetStockOrders().add(target);

            Transaction transaction = new Transaction();
            transaction.setCompany(company);
            transaction.setInitiationUserId(user.getId());
            transaction.setSourceStockOrder(source);
            transaction.setSourceFacility(facility);
            transaction.setTargetProcessingOrder(processingOrder);
            transaction.setSemiProduct(semiProduct);
            transaction.setInputMeasureUnitType(measure);
            transaction.setInputQuantity(inputQuantity);
            transaction.setOutputQuantity(outputQuantity);
            transaction.setStatus(TransactionStatus.PENDING);
            transaction.setIsProcessing(false);
            em.persist(transaction);
            processingOrder.getInputTransactions().add(transaction);
            em.flush();
            return new Fixture(company.getId(), user.getId(), facility.getId(), location.getId(), facilityType.getId(), country.getId(),
                    semiProduct.getId(), measure.getId(), farmer.getId(), action.getId(), source.getId(), target.getId(),
                    processingOrder.getId(), transaction.getId(), accessCookieName + "=" + tokenService.createAccessToken(user));
        });
        fixtures.add(fixture);
        return fixture;
    }

    private StockOrder stockOrder(Company company, User user, Facility facility, SemiProduct semiProduct,
                                  MeasureUnitType measure, UserCustomer farmer, String identifier, OrderType orderType,
                                  BigDecimal total, BigDecimal available) {
        StockOrder order = new StockOrder();
        order.setCompany(company);
        order.setCreatedBy(user);
        order.setUpdatedBy(user);
        order.setFacility(facility);
        order.setSemiProduct(semiProduct);
        order.setMeasurementUnitType(measure);
        order.setProducerUserCustomer(farmer);
        order.setIdentifier(identifier);
        order.setOrderType(orderType);
        order.setPreferredWayOfPayment(PreferredWayOfPayment.BANK_TRANSFER);
        order.setPriceDeterminedLater(true);
        order.setPurchaseOrder(orderType == OrderType.PURCHASE_ORDER);
        order.setAvailable(true);
        order.setTotalQuantity(total);
        order.setTotalGrossQuantity(total);
        order.setFulfilledQuantity(total);
        order.setAvailableQuantity(available);
        order.setPricePerUnit(BigDecimal.ONE);
        order.setCost(total);
        order.setProductionDate(LocalDate.of(2026, 10, 20));
        return order;
    }

    private String nextCountryCode() {
        for (int attempts = 0; attempts < 26 * 26; attempts++) {
            int value = Math.floorMod(COUNTRY_SEQUENCE.getAndIncrement(), 26 * 26);
            String code = "" + (char) ('A' + value / 26) + (char) ('A' + value % 26);
            Long existing = em.createQuery("SELECT COUNT(country) FROM Country country WHERE country.code = :code", Long.class)
                    .setParameter("code", code).getSingleResult();
            if (existing == 0) return code;
        }
        throw new IllegalStateException("no two-character country code is available for transition test fixture");
    }

    private void consumeOutput(Fixture fixture) {
        tx().executeWithoutResult(status -> {
            Company company = em.getReference(Company.class, fixture.companyId());
            StockOrder output = em.getReference(StockOrder.class, fixture.targetId());
            Facility facility = em.getReference(Facility.class, fixture.facilityId());
            SemiProduct semiProduct = em.getReference(SemiProduct.class, fixture.semiProductId());
            MeasureUnitType measure = em.getReference(MeasureUnitType.class, fixture.measureId());
            Transaction consumer = new Transaction();
            consumer.setCompany(company);
            consumer.setInitiationUserId(fixture.userId());
            consumer.setSourceStockOrder(output);
            consumer.setSourceFacility(facility);
            consumer.setSemiProduct(semiProduct);
            consumer.setInputMeasureUnitType(measure);
            consumer.setInputQuantity(BigDecimal.ONE);
            consumer.setOutputQuantity(BigDecimal.ONE);
            consumer.setStatus(TransactionStatus.PENDING);
            consumer.setIsProcessing(false);
            em.persist(consumer);
        });
    }

    private void addPaymentToOutput(Fixture fixture) {
        tx().executeWithoutResult(status -> {
            Payment payment = new Payment();
            payment.setCreatedBy(em.getReference(User.class, fixture.userId()));
            payment.setUpdatedBy(em.getReference(User.class, fixture.userId()));
            payment.setStockOrder(em.getReference(StockOrder.class, fixture.targetId()));
            payment.setPayingCompany(em.getReference(Company.class, fixture.companyId()));
            payment.setRecipientUserCustomer(em.getReference(UserCustomer.class, fixture.farmerId()));
            payment.setRecipientType(RecipientType.USER_CUSTOMER);
            payment.setPaymentType(PaymentType.BANK_TRANSFER);
            payment.setPaymentStatus(PaymentStatus.CONFIRMED);
            payment.setCurrency("EUR");
            payment.setPurchased(THREE);
            payment.setAmount(THREE);
            payment.setTotalPaid(THREE);
            payment.setPreferredWayOfPayment(PreferredWayOfPayment.BANK_TRANSFER);
            em.persist(payment);
        });
    }

    private State state(Fixture fixture) {
        return tx().execute(status -> {
            StockOrder source = em.find(StockOrder.class, fixture.sourceId());
            StockOrder target = em.find(StockOrder.class, fixture.targetId());
            Transaction transaction = em.find(Transaction.class, fixture.transactionId());
            ProcessingOrder order = em.find(ProcessingOrder.class, fixture.processingOrderId());
            Long outputConsumers = em.createQuery("SELECT COUNT(t) FROM Transaction t WHERE t.sourceStockOrder.id = :targetId "
                    + "AND (t.targetProcessingOrder IS NULL OR t.targetProcessingOrder.id <> :processingOrderId)", Long.class)
                    .setParameter("targetId", fixture.targetId()).setParameter("processingOrderId", fixture.processingOrderId())
                    .getSingleResult();
            Long outputPayments = em.createQuery("SELECT COUNT(p) FROM Payment p WHERE p.stockOrder.id = :targetId", Long.class)
                    .setParameter("targetId", fixture.targetId()).getSingleResult();
            return new State(source.getAvailableQuantity(), target == null ? null : target.getAvailableQuantity(),
                    transaction == null ? null : transaction.getStatus(), transaction == null ? null : transaction.getRejectComment(),
                    order != null, transaction != null, target != null,
                    order == null ? null : order.getProcessingAction().getId(),
                    target == null || target.getProcessingOrder() == null ? null : target.getProcessingOrder().getId(),
                    target == null || target.getCompany() == null ? null : target.getCompany().getId(),
                    transaction == null || transaction.getTargetProcessingOrder() == null ? null
                            : transaction.getTargetProcessingOrder().getId(),
                    transaction == null || transaction.getSourceStockOrder() == null ? null
                            : transaction.getSourceStockOrder().getId(),
                    transaction == null || transaction.getCompany() == null ? null : transaction.getCompany().getId(),
                    transaction == null ? null : TransitionGate.readVersion(transaction), outputConsumers, outputPayments);
        });
    }

    private void removeFixture(Fixture fixture) {
        em.createQuery("DELETE FROM Payment p WHERE p.stockOrder.processingOrder.id = :processingOrderId")
                .setParameter("processingOrderId", fixture.processingOrderId()).executeUpdate();
        em.createQuery("DELETE FROM Transaction t WHERE t.targetProcessingOrder.id = :processingOrderId")
                .setParameter("processingOrderId", fixture.processingOrderId()).executeUpdate();
        em.createQuery("DELETE FROM Transaction t WHERE t.sourceStockOrder.processingOrder.id = :processingOrderId")
                .setParameter("processingOrderId", fixture.processingOrderId()).executeUpdate();
        em.createQuery("DELETE FROM StockOrder so WHERE so.processingOrder.id = :processingOrderId OR so.id = :sourceId")
                .setParameter("processingOrderId", fixture.processingOrderId()).setParameter("sourceId", fixture.sourceId()).executeUpdate();
        em.createQuery("DELETE FROM ProcessingOrder po WHERE po.id = :processingOrderId")
                .setParameter("processingOrderId", fixture.processingOrderId()).executeUpdate();
        em.createQuery("DELETE FROM ProcessingAction pa WHERE pa.id = :actionId")
                .setParameter("actionId", fixture.actionId()).executeUpdate();
        em.createQuery("DELETE FROM UserCustomer customer WHERE customer.id = :farmerId")
                .setParameter("farmerId", fixture.farmerId()).executeUpdate();
        em.createQuery("DELETE FROM SemiProduct semiProduct WHERE semiProduct.id = :semiProductId")
                .setParameter("semiProductId", fixture.semiProductId()).executeUpdate();
        em.createQuery("DELETE FROM MeasureUnitType measure WHERE measure.id = :measureId")
                .setParameter("measureId", fixture.measureId()).executeUpdate();
        em.createQuery("DELETE FROM Facility facility WHERE facility.id = :facilityId")
                .setParameter("facilityId", fixture.facilityId()).executeUpdate();
        em.createQuery("DELETE FROM FacilityLocation location WHERE location.id = :locationId")
                .setParameter("locationId", fixture.locationId()).executeUpdate();
        em.createQuery("DELETE FROM Country country WHERE country.id = :countryId")
                .setParameter("countryId", fixture.countryId()).executeUpdate();
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

    private Request request(String label, RequestCall call) {
        return new Request(label, call);
    }

    private void assertGateObservedSamePendingVersion() {
        assertGateObservedSameVersion(TransactionStatus.PENDING);
    }

    private void assertGateObservedSameVersion(TransactionStatus expectedStatus) {
        assertTrue(gate.observedDistinctReadsOnSameVersion(expectedStatus),
                () -> "both workers must load the same " + expectedStatus + " transaction version inside real transactions");
    }

    private void assertAtLeastOneSuccess(List<RequestResult> responses) {
        assertTrue(responses.stream().anyMatch(result -> result.response().getStatusCode().is2xxSuccessful()),
                () -> "at least one valid transition must commit: " + describe(responses));
    }

    private void assertExactlyOneSuccess(List<RequestResult> responses) {
        assertEquals(1, responses.stream().filter(result -> result.response().getStatusCode().is2xxSuccessful()).count(),
                () -> "only one mutually exclusive transition may commit: " + describe(responses));
    }

    private void assertOnlyDocumentedTransitionResponses(List<RequestResult> responses) {
        assertTrue(responses.stream().allMatch(this::isDocumentedTransitionResponse),
                () -> "unexpected transition result: " + describe(responses));
        long internalServerErrors = responses.stream()
                .filter(result -> result.response().getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR).count();
        assertTrue(failureRecorder.coversOnlyOptimisticOrDeadlockFailures(internalServerErrors),
                () -> "each 500 must have a matching optimistic/deadlock diagnostic: " + describe(responses));
    }

    private boolean isDocumentedTransitionResponse(RequestResult result) {
        ResponseEntity<String> response = result.response();
        if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode() == HttpStatus.CONFLICT) {
            return true;
        }
        if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return response.getBody() != null && (response.getBody().contains("VALIDATION_ERROR")
                    || response.getBody().contains("INVALID_REQUEST"));
        }
        return response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                && failureRecorder.containsOnlyOptimisticOrDeadlockFailures();
    }

    private void assertSuccess(ResponseEntity<String> response) {
        assertTrue(response.getStatusCode().is2xxSuccessful(), response::getBody);
    }

    private void assertGraphPresent(Fixture fixture, State state) {
        assertTrue(state.processingOrderPresent());
        assertTrue(state.transactionPresent());
        assertTrue(state.targetPresent());
        assertEquals(fixture.actionId(), state.processingActionId());
        assertEquals(fixture.processingOrderId(), state.targetProcessingOrderId());
        assertEquals(fixture.companyId(), state.targetCompanyId());
        assertEquals(fixture.processingOrderId(), state.transactionTargetProcessingOrderId());
        assertEquals(fixture.sourceId(), state.transactionSourceStockOrderId());
        assertEquals(fixture.companyId(), state.transactionCompanyId());
        assertNotNull(state.transactionVersion());
    }

    private void assertResponsesMatchCommittedState(Fixture fixture, List<RequestResult> responses, State state) {
        for (RequestResult result : responses) {
            if (!result.response().getStatusCode().is2xxSuccessful()) continue;
            if (result.label().startsWith("approve")) {
                assertEquals(TransactionStatus.EXECUTED, state.status(), () -> "no-op approval: " + result);
            } else if (result.label().startsWith("reject")) {
                assertTrue(effectRecorder.wasCanceled(fixture.transactionId()), () -> "no-op rejection: " + result);
                assertTrue(state.status() == TransactionStatus.CANCELED || !state.transactionPresent(),
                        () -> "rejected transaction must remain canceled or be removed by the successful delete: " + result);
            } else if (result.label().startsWith("delete")) {
                assertFalse(state.processingOrderPresent(), () -> "no-op delete: " + result);
            }
        }
    }

    private void assertBadRequest(ResponseEntity<String> response, String expectedMessage) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response::getBody);
        assertTrue(response.getBody().contains("VALIDATION_ERROR"), response::getBody);
        assertTrue(response.getBody().contains(expectedMessage), response::getBody);
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual));
    }

    private String describe(List<RequestResult> responses) {
        return responses.stream().map(result -> result.label() + "=" + result.response().getStatusCode()
                + " " + result.response().getBody()).toList().toString();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    private interface RequestCall {
        ResponseEntity<String> execute();
    }

    private record Request(String label, RequestCall execute) { }

    private record RequestResult(String label, ResponseEntity<String> response) { }

    private record Fixture(Long companyId, Long userId, Long facilityId, Long locationId, Long facilityTypeId, Long countryId,
                           Long semiProductId, Long measureId, Long farmerId, Long actionId, Long sourceId,
                           Long targetId, Long processingOrderId, Long transactionId, String accessCookie) { }

    private record State(BigDecimal sourceAvailable, BigDecimal targetAvailable, TransactionStatus status,
                         String rejectComment, boolean processingOrderPresent, boolean transactionPresent,
                         boolean targetPresent, Long processingActionId, Long targetProcessingOrderId,
                         Long targetCompanyId, Long transactionTargetProcessingOrderId,
                         Long transactionSourceStockOrderId, Long transactionCompanyId, Long transactionVersion,
                         Long outputConsumerCount, Long outputPaymentCount) { }

    private static final class TransitionGate {
        private final Long transactionId;
        private final CountDownLatch arrivals;
        private final CountDownLatch release = new CountDownLatch(1);
        private final java.util.Map<Long, LoadObservation> observations = new ConcurrentHashMap<>();

        private TransitionGate(Long transactionId, int expectedArrivals) {
            this.transactionId = transactionId;
            this.arrivals = new CountDownLatch(expectedArrivals);
        }

        static TransitionGate forTransaction(Long transactionId, int arrivals) {
            return new TransitionGate(transactionId, arrivals);
        }

        static TransitionGate disabled() {
            return new TransitionGate(null, 0);
        }

        void afterTransactionLoad(Transaction transaction, boolean transactionActive) {
            if (transactionId == null || !transactionId.equals(transaction.getId())) return;
            LoadObservation observation = new LoadObservation(Thread.currentThread().getId(), transaction.getStatus(),
                    readVersion(transaction), transactionActive);
            if (observations.putIfAbsent(observation.threadId(), observation) != null) return;
            arrivals.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out after loading transaction for concurrent transition");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted after loading transaction for concurrent transition", exception);
            }
        }

        boolean awaitArrivals() throws InterruptedException {
            return arrivals.await(20, TimeUnit.SECONDS);
        }

        boolean observedDistinctReadsOnSameVersion(TransactionStatus expectedStatus) {
            if (observations.size() < 2) return false;
            Long version = null;
            for (LoadObservation observation : observations.values()) {
                if (!observation.transactionActive() || observation.status() != expectedStatus) return false;
                if (version == null) version = observation.entityVersion();
                else if (!java.util.Objects.equals(version, observation.entityVersion())) return false;
            }
            return version != null;
        }

        void release() {
            release.countDown();
        }

        private static Long readVersion(Transaction transaction) {
            try {
                Field field = Transaction.class.getDeclaredField("entityVersion");
                field.setAccessible(true);
                return (Long) field.get(transaction);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("cannot inspect Transaction entity version in test", exception);
            }
        }

        private record LoadObservation(Long threadId, TransactionStatus status, Long entityVersion,
                                       boolean transactionActive) { }
    }

    private static final class KnownFailureRecorder {
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();

        void record(Throwable failure) {
            failures.add(failure);
        }

        void clear() {
            failures.clear();
        }

        boolean containsOnlyOptimisticOrDeadlockFailures() {
            return !failures.isEmpty() && failures.stream().allMatch(this::isOptimisticOrDeadlockFailure);
        }

        boolean coversOnlyOptimisticOrDeadlockFailures(long expectedFailures) {
            return expectedFailures == 0 || (failures.size() >= expectedFailures && containsOnlyOptimisticOrDeadlockFailures());
        }

        private boolean isOptimisticOrDeadlockFailure(Throwable failure) {
            for (Throwable candidate = failure; candidate != null && candidate != candidate.getCause();
                 candidate = candidate.getCause()) {
                String name = candidate.getClass().getName();
                if (name.contains("OptimisticLock") || name.contains("StaleObject")
                        || name.contains("Deadlock") || name.contains("CannotAcquireLock")) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TransactionEffectRecorder {
        private final java.util.Set<Long> canceledTransactionIds = ConcurrentHashMap.newKeySet();

        void recordCancellation(Long transactionId) {
            canceledTransactionIds.add(transactionId);
        }

        void clear() {
            canceledTransactionIds.clear();
        }

        boolean wasCanceled(Long transactionId) {
            return canceledTransactionIds.contains(transactionId);
        }
    }
}
