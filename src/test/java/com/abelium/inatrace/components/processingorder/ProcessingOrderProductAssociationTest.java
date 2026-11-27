package com.abelium.inatrace.components.processingorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProcessingOrderProductAssociationTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long userId;
    private Long actionId;
    private Long emptyEnrolledActionId;
    private Long outputSemiProductId;
    private Long acmeFacilityId;
    private Long acmeSourceId;
    private Long emptyEnrolledCompanyId;
    private Long emptyEnrolledSourceId;
    private Long emptyOtherCompanyId;
    private Long emptyOtherSourceId;
    private Long otherProductCompanyId;
    private Long otherProductSourceId;
    private Cookie ownerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme product association");
        Company emptyEnrolled = company("Empty enrolled company");
        Company emptyOther = company("Empty other company");
        Company otherProduct = company("Other product company");

        User owner = user("product-association-owner@acme.test");
        enroll(owner, acme);
        enroll(owner, emptyEnrolled);

        ProductType productType = new ProductType();
        productType.setCode("PRODUCT_ASSOCIATION_TEST");
        productType.setName("Product association test type");
        em.persist(productType);

        ValueChain valueChain = new ValueChain();
        valueChain.setName("Product association test value chain");
        valueChain.setDescription("Value chain for product association tests");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setProductType(productType);
        valueChain.setCreatedBy(owner);
        em.persist(valueChain);

        createProduct("Acme product", acme, valueChain);
        createProduct("Other product", otherProduct, valueChain);

        MeasureUnitType kg = new MeasureUnitType("PRODUCT_ASSOCIATION_KG", "Product association kilogram", BigDecimal.ONE);
        em.persist(kg);

        SemiProduct inputSemiProduct = semiProduct("Product association input", kg);
        SemiProduct outputSemiProduct = semiProduct("Product association output", kg);
        outputSemiProductId = outputSemiProduct.getId();

        FacilityType facilityType = new FacilityType("PRODUCT_ASSOCIATION_FACILITY", "Product association facility");
        em.persist(facilityType);

        Facility acmeFacility = facility("Acme product association facility", acme, facilityType);
        Facility emptyEnrolledFacility = facility("Empty enrolled facility", emptyEnrolled, facilityType);
        Facility emptyOtherFacility = facility("Empty other facility", emptyOther, facilityType);
        Facility otherProductFacility = facility("Other product facility", otherProduct, facilityType);
        acmeFacilityId = acmeFacility.getId();

        ProcessingAction action = new ProcessingAction();
        action.setCompany(acme);
        action.setType(ProcessingActionType.PROCESSING);
        action.setInputSemiProduct(inputSemiProduct);
        action.setPrefix("PRODUCT-ASSOC");
        em.persist(action);
        actionId = action.getId();

        ProcessingAction emptyEnrolledAction = new ProcessingAction();
        emptyEnrolledAction.setCompany(emptyEnrolled);
        emptyEnrolledAction.setType(ProcessingActionType.PROCESSING);
        emptyEnrolledAction.setInputSemiProduct(inputSemiProduct);
        emptyEnrolledAction.setPrefix("EMPTY-ENROLLED");
        em.persist(emptyEnrolledAction);
        emptyEnrolledActionId = emptyEnrolledAction.getId();

        acmeSourceId = sourceOrder("ACME-PRODUCT-SOURCE", acme, acmeFacility, inputSemiProduct, kg, owner);
        emptyEnrolledSourceId = sourceOrder("EMPTY-ENROLLED-SOURCE", emptyEnrolled, emptyEnrolledFacility,
                inputSemiProduct, kg, owner);
        emptyOtherSourceId = sourceOrder("EMPTY-OTHER-SOURCE", emptyOther, emptyOtherFacility,
                inputSemiProduct, kg, owner);
        otherProductSourceId = sourceOrder("OTHER-PRODUCT-SOURCE", otherProduct, otherProductFacility,
                inputSemiProduct, kg, owner);

        userId = owner.getId();
        emptyEnrolledCompanyId = emptyEnrolled.getId();
        emptyOtherCompanyId = emptyOther.getId();
        otherProductCompanyId = otherProduct.getId();
        ownerSession = cookie(owner);

        em.flush();
        em.clear();
    }

    @Test
    void companyWithoutProductsAndEnrolledUserGetsActionableError() throws Exception {
        long processingOrdersBefore = processingOrderCount();
        long stockOrdersBefore = stockOrderCount();
        long transactionsBefore = transactionCount();

        MvcResult result = putProcessingOrder(processingOrderBody(
                emptyEnrolledCompanyId, emptyEnrolledSourceId, minimalTarget()));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("PRODUCT_ASSOCIATION_REQUIRED", body.path("status").asText());
        assertEquals(String.valueOf(emptyEnrolledCompanyId), body.path("errorDetails").asText());
        assertTrue(body.path("errorMessage").asText().contains("product association"));

        assertEquals(processingOrdersBefore, processingOrderCount());
        assertEquals(stockOrdersBefore, stockOrderCount());
        assertEquals(transactionsBefore, transactionCount());
    }

    @Test
    void companyWithoutProductsAndUnenrolledUserKeepsGenericForbidden() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                emptyOtherCompanyId, emptyOtherSourceId, minimalTarget()));

        assertEquals(403, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains("UNAUTHORIZED"), response);
        assertFalse(response.contains("product association"), response);
        assertFalse(response.contains("Empty other company"), response);
        assertEquals(0, processingOrderCount());
    }

    @Test
    void companyWithProductsButUnlinkedUserKeepsGenericForbidden() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                otherProductCompanyId, otherProductSourceId, minimalTarget()));

        assertEquals(403, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains("UNAUTHORIZED"), response);
        assertFalse(response.contains("product association"), response);
        assertFalse(response.contains("Other product company"), response);
        assertEquals(0, processingOrderCount());
    }

    @Test
    void sameCompanyWithoutProductsGetsActionableError() throws Exception {
        long processingOrdersBefore = processingOrderCount();

        MvcResult result = putProcessingOrder(processingOrderBody(
                emptyEnrolledActionId, emptyEnrolledCompanyId, emptyEnrolledSourceId, minimalTarget()));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("PRODUCT_ASSOCIATION_REQUIRED", body.path("status").asText());
        assertEquals(String.valueOf(emptyEnrolledCompanyId), body.path("errorDetails").asText());
        assertEquals(processingOrdersBefore, processingOrderCount());
    }

    @Test
    void inaccessibleLotResponseDoesNotRevealOwner() throws Exception {
        MvcResult correctCompany = putProcessingOrder(processingOrderBody(
                emptyOtherCompanyId, emptyOtherSourceId, minimalTarget()));
        MvcResult wrongCompany = putProcessingOrder(processingOrderBody(
                companyIdForAcme(), emptyOtherSourceId, minimalTarget()));

        assertEquals(403, correctCompany.getResponse().getStatus(), correctCompany.getResponse().getContentAsString());
        assertEquals(403, wrongCompany.getResponse().getStatus(), wrongCompany.getResponse().getContentAsString());
        assertEquals(correctCompany.getResponse().getContentAsString(), wrongCompany.getResponse().getContentAsString());
        assertFalse(correctCompany.getResponse().getContentAsString().contains("does not own"));
        assertEquals(0, processingOrderCount());
    }

    @Test
    void twoInputsWhereFirstCompanyHasNoProductsFailWithoutWrites() throws Exception {
        long processingOrdersBefore = processingOrderCount();
        long stockOrdersBefore = stockOrderCount();
        long transactionsBefore = transactionCount();

        MvcResult result = putProcessingOrder(processingOrderBodyWithTwoInputs(
                emptyEnrolledCompanyId, emptyEnrolledSourceId,
                companyIdForAcme(), acmeSourceId, minimalTarget()));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("PRODUCT_ASSOCIATION_REQUIRED", body.path("status").asText());
        assertEquals(String.valueOf(emptyEnrolledCompanyId), body.path("errorDetails").asText());

        assertEquals(processingOrdersBefore, processingOrderCount());
        assertEquals(stockOrdersBefore, stockOrderCount());
        assertEquals(transactionsBefore, transactionCount());
    }

    @Test
    void companyWithProductsAndLinkedUserCanCreateProcessingOrder() throws Exception {
        String target = """
                {"identifier":"PRODUCT-ASSOC-OUTPUT","orderType":"PROCESSING_ORDER",
                 "facility":{"id":%d},"semiProduct":{"id":%d},
                 "totalQuantity":5,"fulfilledQuantity":5,"availableQuantity":5}
                """.formatted(acmeFacilityId, outputSemiProductId);

        MvcResult result = putProcessingOrder(processingOrderBody(
                companyIdForAcme(), acmeSourceId, target));

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertNotNull(stockOrderByIdentifier("PRODUCT-ASSOC-OUTPUT"));
        assertNotNull(em.find(ProcessingOrder.class,
                objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong()));
    }

    private Long companyIdForAcme() {
        return em.createQuery("SELECT c.id FROM Company c WHERE c.name = :name", Long.class)
                .setParameter("name", "Acme product association")
                .getSingleResult();
    }

    private MvcResult putProcessingOrder(String body) throws Exception {
        return mockMvc.perform(put("/api/chain/processing-order")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String processingOrderBody(Long inputCompanyId, Long sourceStockOrderId, String targetStockOrders) {
        return processingOrderBody(actionId, inputCompanyId, sourceStockOrderId, targetStockOrders);
    }

    private String processingOrderBody(Long actionId, Long inputCompanyId, Long sourceStockOrderId,
                                       String targetStockOrders) {
        return """
                {"processingAction":{"id":%d},"initiatorUserId":%d,"processingDate":"2026-11-27",
                 "inputTransactions":[{"company":{"id":%d},
                   "sourceStockOrder":{"id":%d,"orderType":"PURCHASE_ORDER","totalQuantity":10,
                     "fulfilledQuantity":10,"availableQuantity":10},
                   "status":"PENDING","inputQuantity":5,"outputQuantity":5}],
                 "targetStockOrders":[%s]}
                """.formatted(actionId, userId, inputCompanyId, sourceStockOrderId, targetStockOrders);
    }

    private String processingOrderBodyWithTwoInputs(Long firstCompanyId, Long firstSourceId,
                                                     Long secondCompanyId, Long secondSourceId,
                                                     String targetStockOrders) {
        return """
                {"processingAction":{"id":%d},"initiatorUserId":%d,"processingDate":"2026-11-27",
                 "inputTransactions":[
                   {"company":{"id":%d},
                    "sourceStockOrder":{"id":%d,"orderType":"PURCHASE_ORDER","totalQuantity":10,
                      "fulfilledQuantity":10,"availableQuantity":10},
                    "status":"PENDING","inputQuantity":5,"outputQuantity":5},
                   {"company":{"id":%d},
                    "sourceStockOrder":{"id":%d,"orderType":"PURCHASE_ORDER","totalQuantity":10,
                      "fulfilledQuantity":10,"availableQuantity":10},
                    "status":"PENDING","inputQuantity":5,"outputQuantity":5}],
                 "targetStockOrders":[%s]}
                """.formatted(actionId, userId, firstCompanyId, firstSourceId,
                secondCompanyId, secondSourceId, targetStockOrders);
    }

    private String minimalTarget() {
        return "{\"id\":1}";
    }

    private long processingOrderCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(po) FROM ProcessingOrder po", Long.class).getSingleResult();
    }

    private long stockOrderCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(so) FROM StockOrder so", Long.class).getSingleResult();
    }

    private long transactionCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(t) FROM Transaction t", Long.class).getSingleResult();
    }

    private StockOrder stockOrderByIdentifier(String identifier) {
        em.flush();
        List<StockOrder> results = em.createQuery(
                        "SELECT so FROM StockOrder so WHERE so.identifier = :identifier", StockOrder.class)
                .setParameter("identifier", identifier)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private void createProduct(String name, Company company, ValueChain valueChain) {
        Product product = new Product();
        product.setName(name);
        product.setCompany(company);
        product.setValueChain(valueChain);
        product.setProcess(null);
        product.setResponsibility(null);
        product.setSustainability(null);
        product.setJourney(null);
        product.setSettings(null);
        product.setBusinessToCustomerSettings(null);
        em.persist(product);

        ProductCompany association = new ProductCompany();
        association.setProduct(product);
        association.setCompany(company);
        association.setType(ProductCompanyType.OWNER);
        em.persist(association);
        product.getAssociatedCompanies().add(association);
    }

    private Facility facility(String name, Company company, FacilityType facilityType) {
        Facility facility = new Facility();
        facility.setName(name);
        facility.setCompany(company);
        facility.setFacilityType(facilityType);
        facility.setIsCollectionFacility(true);
        facility.setIsDeactivated(false);
        facility.setIsPublic(true);
        em.persist(facility);
        return facility;
    }

    private Long sourceOrder(String identifier, Company company, Facility facility,
                             SemiProduct semiProduct, MeasureUnitType measureUnitType, User createdBy) {
        StockOrder order = new StockOrder();
        order.setCompany(company);
        order.setFacility(facility);
        order.setSemiProduct(semiProduct);
        order.setMeasurementUnitType(measureUnitType);
        order.setCreatedBy(createdBy);
        order.setIdentifier(identifier);
        order.setOrderType(OrderType.PURCHASE_ORDER);
        order.setPurchaseOrder(true);
        order.setAvailable(true);
        order.setTotalQuantity(new BigDecimal("10.00"));
        order.setTotalGrossQuantity(new BigDecimal("10.00"));
        order.setFulfilledQuantity(new BigDecimal("10.00"));
        order.setAvailableQuantity(new BigDecimal("10.00"));
        order.setProductionDate(LocalDate.of(2026, 11, 1));
        em.persist(order);
        return order.getId();
    }

    private SemiProduct semiProduct(String name, MeasureUnitType measureUnitType) {
        SemiProduct semiProduct = new SemiProduct();
        semiProduct.setName(name);
        semiProduct.setMeasurementUnitType(measureUnitType);
        semiProduct.setBuyable(true);
        em.persist(semiProduct);
        return semiProduct;
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
