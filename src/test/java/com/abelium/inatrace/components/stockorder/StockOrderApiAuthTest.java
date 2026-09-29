package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.codebook.SemiProductTranslation;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Plot;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.company.CompanyCustomer;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.facility.FacilityTranslation;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.productorder.ProductOrder;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionTranslation;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.UserCustomerType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockOrderApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "ACME-DELIVERY-ONLY-35";
    private static final String GEO_MARKER = "44.654321";
    private static final String QUOTE_MARKER = "ACME-QUOTE-ONLY-35";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long companyId;
    private Long facilityId;
    private Long orderId;
    private Long semiProductId;
    private Long farmerId;
    private Long rivalFarmerId;
    private Long processingOrderId;
    private Long processingActionId;
    private Long rivalProcessingActionId;
    private Long ownerUserId;
    private Long transactionId;
    private Long deletableOrderId;
    private Long finalProductId;
    private Long customerId;
    private Long rivalCustomerId;
    private Long rivalFacilityId;
    private Long rivalProductOrderId;
    private Long rivalStockOrderId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme stock 35");
        Company rival = company("Rival stock 35");
        User owner = user("stock-owner-35@acme.test");
        User stranger = user("stock-stranger-35@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        ProductType type = new ProductType();
        type.setCode("STOCK_35");
        type.setName("Stock auth crop");
        em.persist(type);
        ValueChain chain = new ValueChain();
        chain.setName("Stock auth chain");
        chain.setDescription("Stock authorization test chain");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(type);
        chain.setCreatedBy(owner);
        em.persist(chain);
        Product product = new Product();
        product.setName("Acme stock product");
        product.setCompany(acme);
        product.setValueChain(chain);
        product.setProcess(null);
        product.setResponsibility(null);
        product.setSustainability(null);
        product.setJourney(null);
        product.setSettings(null);
        product.setBusinessToCustomerSettings(null);
        em.persist(product);
        ProductCompany association = new ProductCompany();
        association.setProduct(product);
        association.setCompany(acme);
        association.setType(ProductCompanyType.OWNER);
        em.persist(association);
        product.getAssociatedCompanies().add(association);

        CompanyCustomer customer = new CompanyCustomer();
        customer.setName("Acme customer 35");
        customer.setCompany(acme);
        customer.setProduct(product);
        em.persist(customer);
        customerId = customer.getId();
        CompanyCustomer rivalCustomer = new CompanyCustomer();
        rivalCustomer.setName("Rival customer forbidden 35");
        rivalCustomer.setCompany(rival);
        em.persist(rivalCustomer);
        rivalCustomerId = rivalCustomer.getId();

        Country country = new Country();
        country.setCode("XS");
        country.setName("Stock Test Country");
        em.persist(country);
        FacilityType facilityType = new FacilityType("STOCK_AUTH_35", "Stock auth facility");
        em.persist(facilityType);
        Address address = new Address();
        address.setCountry(country);
        address.setAddress("Stock test address");
        FacilityLocation location = new FacilityLocation();
        location.setAddress(address);
        em.persist(location);
        Facility facility = new Facility();
        facility.setName("Acme stock facility");
        facility.setCompany(acme);
        facility.setFacilityLocation(location);
        facility.setFacilityType(facilityType);
        facility.setIsCollectionFacility(true);
        facility.setIsDeactivated(false);
        em.persist(facility);
        FacilityTranslation facilityTranslation = new FacilityTranslation();
        facilityTranslation.setFacility(facility);
        facilityTranslation.setLanguage(Language.EN);
        facilityTranslation.setName("Acme stock facility");
        em.persist(facilityTranslation);
        facility.getFacilityTranslations().add(facilityTranslation);
        Facility rivalFacility = new Facility();
        rivalFacility.setName("Rival facility forbidden 35");
        rivalFacility.setCompany(rival);
        rivalFacility.setFacilityLocation(location);
        rivalFacility.setFacilityType(facilityType);
        em.persist(rivalFacility);
        rivalFacilityId = rivalFacility.getId();
        ProductOrder rivalProductOrder = new ProductOrder();
        rivalProductOrder.setOrderId("RIVAL-PRODUCT-ORDER-35");
        rivalProductOrder.setDeliveryDeadline(LocalDate.of(2025, 11, 4));
        rivalProductOrder.setFacility(rivalFacility);
        em.persist(rivalProductOrder);
        rivalProductOrderId = rivalProductOrder.getId();

        SemiProduct semi = new SemiProduct();
        semi.setName("Acme stock semi");
        semi.setBuyable(true);
        MeasureUnitType measure = new MeasureUnitType("STOCK_35_KG", "Stock kilogram", BigDecimal.ONE);
        em.persist(measure);
        semi.setMeasurementUnitType(measure);
        em.persist(semi);
        SemiProductTranslation semiTranslation = new SemiProductTranslation();
        semiTranslation.setSemiProduct(semi);
        semiTranslation.setLanguage(Language.EN);
        semiTranslation.setName("Acme stock semi");
        em.persist(semiTranslation);
        semi.getSemiProductTranslations().add(semiTranslation);

        FinalProduct finalProduct = new FinalProduct();
        finalProduct.setName("Acme final 35");
        finalProduct.setDescription("Final product for product-order authorization");
        finalProduct.setProduct(product);
        finalProduct.setMeasurementUnitType(measure);
        em.persist(finalProduct);
        finalProductId = finalProduct.getId();

        UserCustomer farmer = new UserCustomer();
        farmer.setName("Acme stock farmer");
        farmer.setSurname("Only");
        farmer.setType(UserCustomerType.FARMER);
        farmer.setCompany(acme);
        farmer.setProduct(product);
        em.persist(farmer);
        UserCustomer rivalFarmer = new UserCustomer();
        rivalFarmer.setName("Rival farmer forbidden 35");
        rivalFarmer.setSurname("Only");
        rivalFarmer.setType(UserCustomerType.FARMER);
        rivalFarmer.setCompany(rival);
        em.persist(rivalFarmer);
        Plot plot = new Plot();
        plot.setFarmer(farmer);
        plot.setPlotName("Acme plot 35");
        em.persist(plot);
        PlotCoordinate coordinate = new PlotCoordinate();
        coordinate.setPlot(plot);
        coordinate.setLatitude(12.345678);
        coordinate.setLongitude(44.654321);
        em.persist(coordinate);
        plot.getCoordinates().add(coordinate);

        StockOrder order = new StockOrder();
        order.setCompany(acme);
        order.setFacility(facility);
        order.setSemiProduct(semi);
        order.setMeasurementUnitType(measure);
        order.setProducerUserCustomer(farmer);
        order.setCreatedBy(owner);
        order.setUpdatedBy(owner);
        order.setIdentifier(MARKER);
        order.setOrderType(OrderType.PURCHASE_ORDER);
        order.setPreferredWayOfPayment(PreferredWayOfPayment.BANK_TRANSFER);
        order.setPurchaseOrder(true);
        order.setAvailable(true);
        order.setTotalQuantity(new BigDecimal("123.50"));
        order.setTotalGrossQuantity(new BigDecimal("123.50"));
        order.setFulfilledQuantity(new BigDecimal("123.50"));
        order.setAvailableQuantity(new BigDecimal("113.50"));
        order.setPricePerUnit(new BigDecimal("4.00"));
        order.setCost(new BigDecimal("494.00"));
        order.setProductionDate(LocalDate.of(2025, 8, 18));
        em.persist(order);

        StockOrder rivalOrder = new StockOrder();
        rivalOrder.setCompany(rival);
        rivalOrder.setFacility(rivalFacility);
        rivalOrder.setQuoteCompany(rival);
        rivalOrder.setQuoteFacility(rivalFacility);
        rivalOrder.setSemiProduct(semi);
        rivalOrder.setMeasurementUnitType(measure);
        rivalOrder.setCreatedBy(stranger);
        rivalOrder.setIdentifier("RIVAL-QUOTE-ONLY-35");
        rivalOrder.setOrderType(OrderType.GENERAL_ORDER);
        rivalOrder.setTotalQuantity(BigDecimal.TEN);
        rivalOrder.setFulfilledQuantity(BigDecimal.ZERO);
        rivalOrder.setAvailableQuantity(BigDecimal.ZERO);
        em.persist(rivalOrder);
        rivalStockOrderId = rivalOrder.getId();

        StockOrder quote = new StockOrder();
        quote.setCompany(acme);
        quote.setFacility(facility);
        quote.setQuoteCompany(acme);
        quote.setQuoteFacility(facility);
        quote.setSemiProduct(semi);
        quote.setMeasurementUnitType(measure);
        quote.setConsumerCompanyCustomer(customer);
        quote.setCreatedBy(owner);
        quote.setUpdatedBy(owner);
        quote.setIdentifier(QUOTE_MARKER);
        quote.setOrderType(OrderType.GENERAL_ORDER);
        quote.setTotalQuantity(new BigDecimal("25.00"));
        quote.setFulfilledQuantity(BigDecimal.ZERO);
        quote.setAvailableQuantity(BigDecimal.ZERO);
        quote.setIsOpenOrder(true);
        quote.setProductionDate(LocalDate.of(2025, 8, 19));
        em.persist(quote);

        ProcessingAction action = new ProcessingAction();
        action.setCompany(acme);
        action.setType(ProcessingActionType.SHIPMENT);
        action.setInputSemiProduct(semi);
        action.setPrefix("STOCK-35");
        em.persist(action);
        ProcessingActionTranslation actionTranslation = new ProcessingActionTranslation(Language.EN);
        actionTranslation.setProcessingAction(action);
        actionTranslation.setName("Acme shipment action 35");
        em.persist(actionTranslation);
        action.getProcessingActionTranslations().add(actionTranslation);
        ProcessingAction rivalAction = new ProcessingAction();
        rivalAction.setCompany(rival);
        rivalAction.setType(ProcessingActionType.SHIPMENT);
        rivalAction.setInputSemiProduct(semi);
        rivalAction.setPrefix("RIVAL-STOCK-35");
        em.persist(rivalAction);
        rivalProcessingActionId = rivalAction.getId();
        ProcessingOrder processing = new ProcessingOrder();
        processing.setProcessingAction(action);
        processing.setInitiatorUserId(owner.getId());
        processing.setProcessingDate(LocalDate.of(2025, 8, 19));
        em.persist(processing);
        quote.setProcessingOrder(processing);
        processing.getTargetStockOrders().add(quote);
        processingOrderId = processing.getId();
        processingActionId = action.getId();
        ownerUserId = owner.getId();

        Transaction transaction = new Transaction();
        transaction.setCompany(acme);
        transaction.setInitiationUserId(owner.getId());
        transaction.setSourceStockOrder(order);
        transaction.setTargetProcessingOrder(processing);
        transaction.setSourceFacility(facility);
        transaction.setSemiProduct(semi);
        transaction.setInputMeasureUnitType(measure);
        transaction.setInputQuantity(new BigDecimal("10.00"));
        transaction.setOutputQuantity(new BigDecimal("10.00"));
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setIsProcessing(false);
        em.persist(transaction);
        processing.getInputTransactions().add(transaction);
        transactionId = transaction.getId();

        StockOrder deletable = new StockOrder();
        deletable.setCompany(acme);
        deletable.setFacility(facility);
        deletable.setSemiProduct(semi);
        deletable.setMeasurementUnitType(measure);
        deletable.setCreatedBy(owner);
        deletable.setIdentifier("ACME-DELETABLE-35");
        deletable.setOrderType(OrderType.GENERAL_ORDER);
        deletable.setTotalQuantity(BigDecimal.TEN);
        deletable.setFulfilledQuantity(BigDecimal.ZERO);
        deletable.setAvailableQuantity(BigDecimal.ZERO);
        em.persist(deletable);
        deletableOrderId = deletable.getId();

        companyId = acme.getId();
        facilityId = facility.getId();
        orderId = order.getId();
        semiProductId = semi.getId();
        farmerId = farmer.getId();
        rivalFarmerId = rivalFarmer.getId();
        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void individualOrderAndHistoryAreScopedToAssociatedCompany() throws Exception {
        for (String path : List.of("/api/chain/stock-order/" + orderId,
                "/api/chain/stock-order/" + orderId + "/aggregated-history")) {
            assertAllowedAndRefused(path, MARKER);
        }
    }

    @Test
    void processingOrderReadFromStockOrderChecksTheStockOwnersProductConnection() throws Exception {
        String path = "/api/chain/stock-order/" + quoteOrderId() + "/processing-order";
        assertAllowedAndRefused(path, QUOTE_MARKER);
    }

    @Test
    void processingOrderReadAndDeleteAreTenantScoped() throws Exception {
        String path = "/api/chain/processing-order/" + processingOrderId;
        assertAllowedAndRefused(path, QUOTE_MARKER);

        MvcResult refused = mockMvc.perform(delete(path).cookie(strangerSession)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(QUOTE_MARKER));
        em.clear();
        assertNotNull(em.find(ProcessingOrder.class, processingOrderId));
        assertNotNull(em.find(StockOrder.class, quoteOrderId()));

        MvcResult anonymous = mockMvc.perform(delete(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        em.clear();
        assertNotNull(em.find(ProcessingOrder.class, processingOrderId));

        MvcResult allowed = mockMvc.perform(delete(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertNull(em.find(ProcessingOrder.class, processingOrderId));
    }

    @Test
    void inputTransactionListChecksStockOrderOwner() throws Exception {
        assertAllowedAndRefused(
                "/api/chain/transaction/list/input/stock-order/" + quoteOrderId(), MARKER);
    }

    @Test
    void approvingTransactionIsRestrictedToQuoteOwnerAndPreservesRefusedState() throws Exception {
        String path = "/api/chain/transaction/" + transactionId + "/approve";
        BigDecimal sourceAvailable = availableQuantity(orderId);
        BigDecimal quoteAvailable = availableQuantity(quoteOrderId());
        assertTransactionWriteDenied(path, null);
        assertEquals(TransactionStatus.PENDING, transactionStatus());
        assertEquals(sourceAvailable, availableQuantity(orderId));
        assertEquals(quoteAvailable, availableQuantity(quoteOrderId()));
        MvcResult allowed = mockMvc.perform(put(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(TransactionStatus.EXECUTED, transactionStatus());
        assertTrue(availableQuantity(quoteOrderId()).compareTo(quoteAvailable) > 0);
    }

    @Test
    void rejectingTransactionIsRestrictedToQuoteOwnerAndPreservesRefusedState() throws Exception {
        String path = "/api/chain/transaction/" + transactionId + "/reject";
        String body = "{\"id\":" + transactionId + ",\"rejectComment\":\"ACME-REJECTION-35\"}";
        BigDecimal sourceAvailable = availableQuantity(orderId);
        BigDecimal quoteAvailable = availableQuantity(quoteOrderId());
        assertTransactionWriteDenied(path, body);
        assertEquals(TransactionStatus.PENDING, transactionStatus());
        assertEquals(sourceAvailable, availableQuantity(orderId));
        assertEquals(quoteAvailable, availableQuantity(quoteOrderId()));
        MvcResult allowed = mockMvc.perform(put(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(TransactionStatus.CANCELED, transactionStatus());
        assertTrue(availableQuantity(orderId).compareTo(sourceAvailable) > 0);
    }

    @Test
    void productOrderCreationRejectsForeignTenantWithoutAnyOrderOrStockWrites() throws Exception {
        String path = "/api/chain/product-order";
        String body = productOrderBody(customerId, facilityId);
        long productsBefore = productOrderCount();
        long stockBefore = orderCount();
        long processingBefore = processingCount();
        assertRefusedWrite(post(path), post(path), body, "ACME-PRODUCT-ORDER-35");
        assertEquals(productsBefore, productOrderCount());
        assertEquals(stockBefore, orderCount());
        assertEquals(processingBefore, processingCount());
        MvcResult allowed = mockMvc.perform(post(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(productsBefore + 1, productOrderCount());
        assertEquals(stockBefore + 1, orderCount());
        assertEquals(processingBefore + 1, processingCount());
    }

    @Test
    void productOrderCannotAttachCustomerFromAnotherTenant() throws Exception {
        long before = productOrderCount();
        String body = productOrderBody(rivalCustomerId, facilityId);
        MvcResult refused = mockMvc.perform(post("/api/chain/product-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("Rival customer forbidden 35"));
        assertEquals(before, productOrderCount());
    }

    @Test
    void productOrderCannotCreateItsItemAtForeignFacility() throws Exception {
        long before = productOrderCount();
        long stockBefore = orderCount();
        String body = productOrderBody(customerId, rivalFacilityId);
        MvcResult refused = mockMvc.perform(post("/api/chain/product-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("Rival facility forbidden 35"));
        assertEquals(before, productOrderCount());
        assertEquals(stockBefore, orderCount());
    }

    private String productOrderBody(Long requestedCustomerId, Long itemFacilityId) {
        return """
                {"orderId":"ACME-PRODUCT-ORDER-35","deliveryDeadline":"2025-11-04",
                 "facility":{"id":%d},"customer":{"id":%d},
                 "items":[{"facility":{"id":%d},"quoteFacility":{"id":%d},
                   "finalProduct":{"id":%d},"totalQuantity":10,
                   "processingOrder":{"processingAction":{"id":%d},"initiatorUserId":%d}}]}
                """.formatted(facilityId, requestedCustomerId, itemFacilityId,
                facilityId, finalProductId, processingActionId, ownerUserId);
    }

    private long productOrderCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(po) FROM ProductOrder po", Long.class).getSingleResult();
    }

    private void assertTransactionWriteDenied(String path, String body) throws Exception {
        var stranger = put(path).cookie(strangerSession);
        var anonymous = put(path);
        if (body != null) {
            stranger.contentType(MediaType.APPLICATION_JSON).content(body);
            anonymous.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult refused = mockMvc.perform(stranger).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(MARKER));
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-REJECTION-35"));
        MvcResult noSession = mockMvc.perform(anonymous).andReturn();
        assertEquals(401, noSession.getResponse().getStatus());
        assertEquals(TransactionStatus.PENDING, transactionStatus());
    }

    private TransactionStatus transactionStatus() {
        em.flush();
        em.clear();
        return em.find(Transaction.class, transactionId).getStatus();
    }

    private BigDecimal availableQuantity(Long stockId) {
        em.flush();
        em.clear();
        return em.find(StockOrder.class, stockId).getAvailableQuantity();
    }

    @Test
    void processingOrderCreationChecksActionAndOutputOwnerBeforeWriting() throws Exception {
        String path = "/api/chain/processing-order";
        String body = processingOrderBody(processingActionId);
        long ordersBefore = orderCount();
        long processingBefore = processingCount();
        assertRefusedWrite(put(path), put(path), body, "ACME-NEW-QUOTE-35");
        assertEquals(ordersBefore, orderCount());
        assertEquals(processingBefore, processingCount());
        MvcResult allowed = mockMvc.perform(put(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(ordersBefore + 1, orderCount());
        assertEquals(processingBefore + 1, processingCount());
    }

    @Test
    void companyCannotCreateProcessingWithForeignAction() throws Exception {
        long ordersBefore = orderCount();
        long processingBefore = processingCount();
        String body = processingOrderBody(rivalProcessingActionId);
        MvcResult refused = mockMvc.perform(put("/api/chain/processing-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-NEW-QUOTE-35"));
        assertEquals(ordersBefore, orderCount());
        assertEquals(processingBefore, processingCount());
    }

    @Test
    void existingProcessingOrderCannotMoveToForeignAction() throws Exception {
        String body = processingOrderBody(rivalProcessingActionId)
                .replace("{\"processingAction\"", "{\"id\":" + processingOrderId + ",\"processingAction\"");
        long ordersBefore = orderCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/processing-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-NEW-QUOTE-35"));
        assertEquals(ordersBefore, orderCount());
        em.clear();
        assertEquals(processingActionId,
                em.find(ProcessingOrder.class, processingOrderId).getProcessingAction().getId());
    }

    @Test
    void processingOrderCannotTakeOverForeignOutputStockOrder() throws Exception {
        String body = processingOrderBody(processingActionId)
                .replace("{\"identifier\"", "{\"id\":" + rivalStockOrderId + ",\"identifier\"");
        long stockBefore = orderCount();
        long processingBefore = processingCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/processing-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("RIVAL-QUOTE-ONLY-35"));
        assertEquals(stockBefore, orderCount());
        assertEquals(processingBefore, processingCount());
        em.clear();
        StockOrder rivalOrder = em.find(StockOrder.class, rivalStockOrderId);
        assertEquals("RIVAL-QUOTE-ONLY-35", rivalOrder.getIdentifier());
        assertEquals(rivalCompanyId(), rivalOrder.getCompany().getId());
    }

    @Test
    void processingOrderCannotCreateOutputAtForeignFacility() throws Exception {
        String body = processingOrderBody(processingActionId)
                .replace("\"facility\":{\"id\":" + facilityId + "}",
                        "\"facility\":{\"id\":" + rivalFacilityId + "}");
        long stockBefore = orderCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/processing-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertEquals(stockBefore, orderCount());
    }

    @Test
    void processingOrderCannotUseForeignInputStockOrder() throws Exception {
        String input = """
                {"company":{"id":%d},"sourceStockOrder":{"id":%d},
                 "status":"PENDING","inputQuantity":5,"outputQuantity":5}
                """.formatted(rivalCompanyId(), rivalStockOrderId);
        String body = processingOrderBody(processingActionId)
                .replace("\"inputTransactions\":[]", "\"inputTransactions\":[" + input + "]");
        long processingBefore = processingCount();
        long stockBefore = orderCount();
        long transactionsBefore = transactionCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/processing-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("RIVAL-QUOTE-ONLY-35"));
        assertEquals(processingBefore, processingCount());
        assertEquals(stockBefore, orderCount());
        assertEquals(transactionsBefore, transactionCount());
    }

    @Test
    void ownerCanCreateProcessingWithItsOwnInputStockOrder() throws Exception {
        String input = """
                {"company":{"id":%d},"sourceStockOrder":{"id":%d,
                   "orderType":"PURCHASE_ORDER","totalQuantity":123.50,
                   "fulfilledQuantity":123.50,"availableQuantity":113.50},
                 "status":"PENDING","inputQuantity":5,"outputQuantity":5}
                """.formatted(companyId, orderId);
        String body = processingOrderBody(processingActionId)
                .replace("\"inputTransactions\":[]", "\"inputTransactions\":[" + input + "]");
        long transactionsBefore = transactionCount();
        long processingBefore = processingCount();
        BigDecimal sourceAvailable = availableQuantity(orderId);
        MvcResult allowed = mockMvc.perform(put("/api/chain/processing-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(transactionsBefore + 1, transactionCount());
        assertEquals(processingBefore + 1, processingCount());
        assertTrue(availableQuantity(orderId).compareTo(sourceAvailable) < 0);
    }

    private long transactionCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(t) FROM Transaction t", Long.class).getSingleResult();
    }

    private Long rivalCompanyId() {
        return em.createQuery("SELECT c.id FROM Company c WHERE c.name = :name", Long.class)
                .setParameter("name", "Rival stock 35").getSingleResult();
    }

    private String processingOrderBody(Long actionId) {
        return """
                {"processingAction":{"id":%d},"initiatorUserId":%d,
                 "processingDate":"2025-08-20","inputTransactions":[],
                 "targetStockOrders":[{"identifier":"ACME-NEW-QUOTE-35",
                   "orderType":"GENERAL_ORDER","company":{"id":%d},
                   "facility":{"id":%d},"quoteFacility":{"id":%d},
                   "semiProduct":{"id":%d},"totalQuantity":10,
                   "fulfilledQuantity":0,"availableQuantity":0}]}
                """.formatted(actionId, ownerUserId, companyId,
                facilityId, facilityId, semiProductId);
    }

    @Test
    void allStockListsCheckTheRequestedCompanyOrFacility() throws Exception {
        for (String path : List.of(
                "/api/chain/stock-order/list/company/" + companyId,
                "/api/chain/stock-order/list/facility/" + facilityId,
                "/api/chain/stock-order/list/facility/" + facilityId + "/available?semiProductId=" + semiProductId)) {
            assertAllowedAndRefused(path, MARKER);
        }
        assertAllowedAndRefused("/api/chain/stock-order/list/company/" + companyId + "/orders-for-customers", QUOTE_MARKER);
    }

    @Test
    void companyListsKeepNestedFarmerCustomerAndFacilityFiltersWithinPathCompany() throws Exception {
        assertAllowedAndRefused(
                "/api/chain/stock-order/list/company/" + companyId + "?farmerId=" + farmerId,
                MARKER);
        assertAllowedAndRefused(
                "/api/chain/stock-order/list/company/" + companyId
                        + "/orders-for-customers?companyCustomerId=" + customerId,
                QUOTE_MARKER);

        for (String path : List.of(
                "/api/chain/stock-order/list/company/" + companyId + "?farmerId=" + rivalFarmerId,
                "/api/chain/stock-order/list/company/" + companyId
                        + "/orders-for-customers?companyCustomerId=" + rivalCustomerId,
                "/api/chain/stock-order/list/company/" + companyId
                        + "/orders-for-customers?facilityId=" + rivalFacilityId)) {
            MvcResult owner = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
            assertEquals(200, owner.getResponse().getStatus(), path + ": " + owner.getResponse().getContentAsString());
            assertFalse(owner.getResponse().getContentAsString().contains(MARKER));
            assertFalse(owner.getResponse().getContentAsString().contains(QUOTE_MARKER));
            assertRefused(path, MARKER);
        }
    }

    @Test
    void quoteOrderListChecksEnrollmentBeforeReturningRows() throws Exception {
        String path = "/api/chain/stock-order/list/company/" + companyId + "/quote-orders";
        assertAllowedAndRefused(path, QUOTE_MARKER);
    }

    @Test
    void geoJsonExportAuthorizesBeforeStreamingCoordinates() throws Exception {
        String path = "/api/chain/stock-order/" + orderId + "/exportGeoData";
        MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertTrue(allowed.getResponse().getContentAsString().contains(GEO_MARKER));
        assertRefused(path, GEO_MARKER);
    }

    @Test
    void deliveryExportAuthorizesBeforeWritingWorkbook() throws Exception {
        String path = "/api/chain/stock-order/export/deliveries/company/" + companyId;
        MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE, allowed.getResponse().getContentType());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(allowed.getResponse().getContentAsByteArray()))) {
            assertEquals(MARKER, workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
        assertRefused(path, MARKER);
    }

    @Test
    void purchaseCreationAndUpdateRejectForeignTenantWithoutWrites() throws Exception {
        String path = "/api/chain/stock-order";
        long before = orderCount();
        String create = purchaseBody(null, "ACME-NEW-DELIVERY-35");
        assertRefusedWrite(put(path), put(path), create, "ACME-NEW-DELIVERY-35");
        assertEquals(before, orderCount());
        MvcResult allowedCreate = mockMvc.perform(put(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(create)).andReturn();
        assertEquals(200, allowedCreate.getResponse().getStatus(), allowedCreate.getResponse().getContentAsString());
        assertEquals(before + 1, orderCount());

        String update = purchaseBody(orderId, "ACME-UPDATED-DELIVERY-35");
        assertRefusedWrite(put(path), put(path), update, "ACME-UPDATED-DELIVERY-35");
        em.clear();
        assertEquals(MARKER, em.find(StockOrder.class, orderId).getIdentifier());
        MvcResult allowedUpdate = mockMvc.perform(put(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andReturn();
        assertEquals(200, allowedUpdate.getResponse().getStatus(), allowedUpdate.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertEquals("ACME-UPDATED-DELIVERY-35", em.find(StockOrder.class, orderId).getIdentifier());
    }

    @Test
    void ownerCannotAttachRivalFarmerToItsPurchase() throws Exception {
        String body = purchaseBody(null, "CROSS-TENANT-FARMER-35")
                .replace("\"producerUserCustomer\":{\"id\":" + farmerId + "}",
                        "\"producerUserCustomer\":{\"id\":" + rivalFarmerId + "}");
        long before = orderCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/stock-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("Rival farmer forbidden 35"));
        assertEquals(before, orderCount());
    }

    @Test
    void ownerCannotAttachRivalCustomerToItsStockOrder() throws Exception {
        String body = purchaseBody(null, "CROSS-TENANT-CUSTOMER-35")
                .replace("\"orderType\"", "\"consumerCompanyCustomer\":{\"id\":"
                        + rivalCustomerId + "},\"orderType\"");
        long before = orderCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/stock-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("Rival customer forbidden 35"));
        assertEquals(before, orderCount());
    }

    @Test
    void ownerCannotAttachRivalProductOrderToItsStockOrder() throws Exception {
        String body = purchaseBody(null, "CROSS-TENANT-PRODUCT-ORDER-35")
                .replace("\"orderType\"", "\"productOrder\":{\"id\":"
                        + rivalProductOrderId + "},\"orderType\"");
        long before = orderCount();
        MvcResult refused = mockMvc.perform(put("/api/chain/stock-order").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("RIVAL-PRODUCT-ORDER-35"));
        assertEquals(before, orderCount());
    }

    @Test
    void bulkPurchaseRejectsForeignTenantBeforeInsertingAnything() throws Exception {
        String path = "/api/chain/stock-order/bulk-purchase";
        String body = """
                {"facility":{"id":%d},"productionDate":"2025-08-20","currency":"USD",
                 "preferredWayOfPayment":"BANK_TRANSFER","farmers":[
                   {"identifier":"ACME-BULK-DELIVERY-35","producerUserCustomer":{"id":%d},
                    "semiProduct":{"id":%d},"totalQuantity":20,"totalGrossQuantity":20,
                    "fulfilledQuantity":0,"pricePerUnit":4}]}
                """.formatted(facilityId, farmerId, semiProductId);
        long before = orderCount();
        assertRefusedWrite(post(path), post(path), body, "ACME-BULK-DELIVERY-35");
        assertEquals(before, orderCount());
        MvcResult allowed = mockMvc.perform(post(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(before + 1, orderCount());
    }

    @Test
    void bulkPurchaseWithMixedTenantFarmersWritesNoOrders() throws Exception {
        String body = """
                {"facility":{"id":%d},"productionDate":"2025-08-20",
                 "farmers":[
                   {"identifier":"ACME-FIRST-BULK-35","producerUserCustomer":{"id":%d},
                    "semiProduct":{"id":%d},"totalQuantity":10,"totalGrossQuantity":10,
                    "fulfilledQuantity":0,"pricePerUnit":4},
                   {"identifier":"RIVAL-SECOND-BULK-35","producerUserCustomer":{"id":%d},
                    "semiProduct":{"id":%d},"totalQuantity":10,"totalGrossQuantity":10,
                    "fulfilledQuantity":0,"pricePerUnit":4}]}
                """.formatted(facilityId, farmerId, semiProductId, rivalFarmerId, semiProductId);
        long before = orderCount();
        MvcResult refused = mockMvc.perform(post("/api/chain/stock-order/bulk-purchase")
                .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-FIRST-BULK-35"));
        assertFalse(refused.getResponse().getContentAsString().contains("RIVAL-SECOND-BULK-35"));
        assertEquals(before, orderCount());
    }

    @Test
    void deleteRejectsForeignTenantAndLeavesOrderInPlace() throws Exception {
        String path = "/api/chain/stock-order/" + deletableOrderId;
        MvcResult refused = mockMvc.perform(delete(path).cookie(strangerSession)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-DELETABLE-35"));
        em.clear();
        assertEquals("ACME-DELETABLE-35", em.find(StockOrder.class, deletableOrderId).getIdentifier());
        MvcResult anonymous = mockMvc.perform(delete(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        em.clear();
        assertNotNull(em.find(StockOrder.class, deletableOrderId));
        long before = orderCount();
        MvcResult allowed = mockMvc.perform(delete(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(before - 1, orderCount());
        em.clear();
        assertNull(em.find(StockOrder.class, deletableOrderId));
    }

    private String purchaseBody(Long id, String identifier) {
        return """
                {"id":%s,"identifier":"%s","orderType":"PURCHASE_ORDER",
                 "facility":{"id":%d},"semiProduct":{"id":%d},
                 "producerUserCustomer":{"id":%d},"totalQuantity":%d,
                 "totalGrossQuantity":%d,"fulfilledQuantity":%d,
                 "pricePerUnit":4,"preferredWayOfPayment":"BANK_TRANSFER",
                 "productionDate":"2025-08-20","currency":"USD"}
                """.formatted(id == null ? "null" : id, identifier, facilityId, semiProductId, farmerId,
                id == null ? 20 : 200, id == null ? 20 : 200, id == null ? 0 : 200);
    }

    private void assertRefusedWrite(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder anonymousRequest,
                                    String body, String marker) throws Exception {
        MvcResult refused = mockMvc.perform(request.cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(marker));
        MvcResult anonymous = mockMvc.perform(anonymousRequest.contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        assertFalse(anonymous.getResponse().getContentAsString().contains(marker));
    }

    private long orderCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(so) FROM StockOrder so", Long.class).getSingleResult();
    }

    private long processingCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(po) FROM ProcessingOrder po", Long.class).getSingleResult();
    }

    private void assertAllowedAndRefused(String path, String marker) throws Exception {
        MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), path + ": " + allowed.getResponse().getContentAsString());
        assertTrue(allowed.getResponse().getContentAsString().contains(marker), path);
        assertRefused(path, marker);
    }

    private Long quoteOrderId() {
        return em.createQuery("SELECT so.id FROM StockOrder so WHERE so.identifier = :identifier", Long.class)
                .setParameter("identifier", QUOTE_MARKER).getSingleResult();
    }

    private void assertRefused(String path, String marker) throws Exception {
        MvcResult refused = mockMvc.perform(get(path).cookie(strangerSession)).andReturn();
        String body = refused.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(403, refused.getResponse().getStatus(), path + ": " + body);
        assertFalse(body.contains(marker), path);
        MvcResult anonymous = mockMvc.perform(get(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus(), path);
        assertFalse(anonymous.getResponse().getContentAsString().contains(marker), path);
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
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
