package com.abelium.inatrace.components.processingorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceField;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionPEF;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionPET;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrderPEFieldValue;
import com.abelium.inatrace.db.entities.stockorder.StockOrderPETypeValue;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.db.enums.EvidenceType;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.ProcessingEvidenceFieldType;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProcessingOrderEvidenceValidationTest extends AbstractMySqlIntegrationTest {

    private static final String QC_REPORT = "QC_REPORT";
    private static final String GRADE = "GRADE";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long companyId;
    private Long userId;
    private Long facilityId;
    private Long sourceOrderId;
    private Long outputSemiProductId;
    private Long actionId;
    private Long evidenceTypeId;
    private Long evidenceFieldId;
    private Long documentId;
    private Cookie ownerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Evidence validation");
        User owner = user("evidence-owner@acme.test");
        enroll(owner, acme);

        ProductType productType = new ProductType();
        productType.setCode("EVIDENCE_TEST");
        productType.setName("Evidence test product type");
        em.persist(productType);

        ValueChain valueChain = new ValueChain();
        valueChain.setName("Evidence test value chain");
        valueChain.setDescription("Value chain for processing evidence validation");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setProductType(productType);
        valueChain.setCreatedBy(owner);
        em.persist(valueChain);

        Product product = new Product();
        product.setName("Evidence test product");
        product.setCompany(acme);
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
        association.setCompany(acme);
        association.setType(ProductCompanyType.OWNER);
        em.persist(association);
        product.getAssociatedCompanies().add(association);

        MeasureUnitType kg = new MeasureUnitType("EVIDENCE_KG", "Evidence kilogram", BigDecimal.ONE);
        em.persist(kg);

        SemiProduct inputSemiProduct = semiProduct("Evidence input", kg);
        SemiProduct outputSemiProduct = semiProduct("Evidence output", kg);
        outputSemiProductId = outputSemiProduct.getId();

        FacilityType facilityType = new FacilityType("EVIDENCE_FACILITY", "Evidence facility");
        em.persist(facilityType);

        Facility facility = new Facility();
        facility.setName("Evidence facility");
        facility.setCompany(acme);
        facility.setFacilityType(facilityType);
        facility.setIsCollectionFacility(false);
        facility.setIsDeactivated(false);
        facility.setIsPublic(true);
        em.persist(facility);
        facilityId = facility.getId();

        ProcessingAction action = new ProcessingAction();
        action.setCompany(acme);
        action.setType(ProcessingActionType.PROCESSING);
        action.setInputSemiProduct(inputSemiProduct);
        action.setPrefix("EVIDENCE");
        em.persist(action);
        actionId = action.getId();

        ProcessingEvidenceType evidenceType = new ProcessingEvidenceType();
        evidenceType.setCode(QC_REPORT);
        evidenceType.setLabel("QC report");
        evidenceType.setType(EvidenceType.DOCUMENT);
        em.persist(evidenceType);
        evidenceTypeId = evidenceType.getId();

        ProcessingActionPET requiredDocument = new ProcessingActionPET(action, evidenceType, true, false, null);
        em.persist(requiredDocument);
        action.getRequiredDocumentTypes().add(requiredDocument);

        ProcessingEvidenceType optionalType = new ProcessingEvidenceType();
        optionalType.setCode("ORGANIC_CERT");
        optionalType.setLabel("Organic certificate");
        optionalType.setType(EvidenceType.DOCUMENT);
        em.persist(optionalType);
        ProcessingActionPET optionalDocument = new ProcessingActionPET(action, optionalType, false, true, null);
        em.persist(optionalDocument);
        action.getRequiredDocumentTypes().add(optionalDocument);

        ProcessingEvidenceField evidenceField = new ProcessingEvidenceField();
        evidenceField.setFieldName(GRADE);
        evidenceField.setLabel("Grade");
        evidenceField.setType(ProcessingEvidenceFieldType.STRING);
        em.persist(evidenceField);
        evidenceFieldId = evidenceField.getId();

        ProcessingActionPEF requiredField = new ProcessingActionPEF(action, evidenceField, true, false);
        em.persist(requiredField);
        action.getProcessingEvidenceFields().add(requiredField);

        ProcessingEvidenceField optionalNumberField = new ProcessingEvidenceField();
        optionalNumberField.setFieldName("BROKEN_SHARE");
        optionalNumberField.setLabel("Broken share");
        optionalNumberField.setType(ProcessingEvidenceFieldType.NUMBER);
        em.persist(optionalNumberField);
        ProcessingActionPEF optionalField = new ProcessingActionPEF(action, optionalNumberField, false, true);
        em.persist(optionalField);
        action.getProcessingEvidenceFields().add(optionalField);

        Document document = new Document();
        document.setStorageKey("evidence/qc-report.pdf");
        document.setName("qc-report.pdf");
        document.setContentType("application/pdf");
        document.setSize(128L);
        em.persist(document);
        documentId = document.getId();

        StockOrder sourceOrder = new StockOrder();
        sourceOrder.setCompany(acme);
        sourceOrder.setFacility(facility);
        sourceOrder.setSemiProduct(inputSemiProduct);
        sourceOrder.setMeasurementUnitType(kg);
        sourceOrder.setCreatedBy(owner);
        sourceOrder.setIdentifier("EVIDENCE-SOURCE");
        sourceOrder.setOrderType(OrderType.PURCHASE_ORDER);
        sourceOrder.setPurchaseOrder(true);
        sourceOrder.setAvailable(true);
        sourceOrder.setTotalQuantity(new BigDecimal("100.00"));
        sourceOrder.setTotalGrossQuantity(new BigDecimal("100.00"));
        sourceOrder.setFulfilledQuantity(new BigDecimal("100.00"));
        sourceOrder.setAvailableQuantity(new BigDecimal("100.00"));
        sourceOrder.setProductionDate(LocalDate.of(2026, 11, 1));
        em.persist(sourceOrder);
        sourceOrderId = sourceOrder.getId();

        companyId = acme.getId();
        userId = owner.getId();
        ownerSession = cookie(owner);

        em.flush();
        em.clear();
    }

    @Test
    void missingDocumentAndFieldIsRejectedBeforeMutation() throws Exception {
        long processingOrdersBefore = processingOrderCount();
        long stockOrdersBefore = stockOrderCount();
        long transactionsBefore = transactionCount();
        BigDecimal sourceAvailableBefore = sourceAvailable();

        MvcResult result = putProcessingOrder(processingOrderBody(output("OUT-MISSING", "", "")));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains(QC_REPORT), response);
        assertTrue(response.contains(GRADE), response);
        assertTrue(response.contains("OUT-MISSING"), response);

        assertEquals(processingOrdersBefore, processingOrderCount());
        assertEquals(stockOrdersBefore, stockOrderCount());
        assertEquals(transactionsBefore, transactionCount());
        assertEquals(0, sourceAvailableBefore.compareTo(sourceAvailable()));
        assertNull(stockOrderByIdentifier("OUT-MISSING"));
    }

    @Test
    void missingFieldIsRejected() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-NO-FIELD", "", documentValue())));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains(GRADE), response);
        assertFalse(response.contains("document '" + QC_REPORT + "'"), response);
        assertNull(stockOrderByIdentifier("OUT-NO-FIELD"));
    }

    @Test
    void missingDocumentIsRejected() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-NO-DOCUMENT", stringField("A"), "")));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains(QC_REPORT), response);
        assertFalse(response.contains("field '" + GRADE + "'"), response);
        assertNull(stockOrderByIdentifier("OUT-NO-DOCUMENT"));
    }

    @Test
    void blankGradeIsRejected() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-BLANK", stringField("   "), documentValue())));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains(GRADE));
        assertNull(stockOrderByIdentifier("OUT-BLANK"));
    }

    @Test
    void completeEvidenceIsAccepted() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-VALID", stringField("A"), documentValue())));

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        Long processingOrderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        StockOrder output = stockOrderByIdentifier("OUT-VALID");
        assertNotNull(output);
        assertEquals(1, output.getDocumentRequirements().size());
        StockOrderPETypeValue document = output.getDocumentRequirements().iterator().next();
        assertEquals(QC_REPORT, document.getProcessingEvidenceType().getCode());
        assertEquals(documentId, document.getDocument().getId());
        assertEquals(1, output.getProcessingEFValues().size());
        StockOrderPEFieldValue field = output.getProcessingEFValues().iterator().next();
        assertEquals(GRADE, field.getProcessingEvidenceField().getFieldName());
        assertEquals("A", field.getStringValue());
        assertNotNull(em.find(ProcessingOrder.class, processingOrderId));
    }

    @Test
    void documentIsRequiredOnEveryOutput() throws Exception {
        String first = output("OUT-DOC-ONE", stringField("A"), documentValue());
        String second = output("OUT-DOC-TWO", stringField("B"), "");

        MvcResult result = putProcessingOrder(processingOrderBody(first + "," + second));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains(QC_REPORT), response);
        assertTrue(response.contains("OUT-DOC-TWO"), response);
        assertNull(stockOrderByIdentifier("OUT-DOC-ONE"));
        assertNull(stockOrderByIdentifier("OUT-DOC-TWO"));
    }

    @Test
    void fieldIsRequiredOnEveryOutput() throws Exception {
        String first = output("OUT-FIELD-ONE", stringField("A"), documentValue());
        String second = output("OUT-FIELD-TWO", "", documentValue());

        MvcResult result = putProcessingOrder(processingOrderBody(first + "," + second));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains(GRADE), response);
        assertTrue(response.contains("OUT-FIELD-TWO"), response);
        assertNull(stockOrderByIdentifier("OUT-FIELD-ONE"));
        assertNull(stockOrderByIdentifier("OUT-FIELD-TWO"));
    }

    @Test
    void completeEvidenceIsAcceptedForEveryOutput() throws Exception {
        String first = output("OUT-ALL-ONE", stringField("A"), documentValue());
        String second = output("OUT-ALL-TWO", stringField("B"), documentValue());

        MvcResult result = putProcessingOrder(processingOrderBody(first + "," + second));

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        StockOrder firstOutput = stockOrderByIdentifier("OUT-ALL-ONE");
        StockOrder secondOutput = stockOrderByIdentifier("OUT-ALL-TWO");
        assertNotNull(firstOutput);
        assertNotNull(secondOutput);
        assertEquals(1, firstOutput.getDocumentRequirements().size());
        assertEquals(1, secondOutput.getDocumentRequirements().size());
        assertEquals("A", firstOutput.getProcessingEFValues().iterator().next().getStringValue());
        assertEquals("B", secondOutput.getProcessingEFValues().iterator().next().getStringValue());
    }

    @Test
    void editingExistingOrderWithoutEvidenceIsRejectedAndKeepsStoredEvidence() throws Exception {
        MvcResult created = putProcessingOrder(processingOrderBody(
                output("OUT-EDIT", stringField("A"), documentValue())));
        assertEquals(200, created.getResponse().getStatus(), created.getResponse().getContentAsString());

        Long processingOrderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        StockOrder storedOutput = stockOrderByIdentifier("OUT-EDIT");
        assertNotNull(storedOutput);
        Long outputId = storedOutput.getId();
        Long transactionId = em.createQuery(
                        "SELECT t.id FROM Transaction t WHERE t.targetProcessingOrder.id = :processingOrderId",
                        Long.class)
                .setParameter("processingOrderId", processingOrderId)
                .getSingleResult();

        MvcResult updated = putProcessingOrder(processingOrderBody(processingOrderId, transactionId,
                output(outputId, "OUT-EDIT", "", "")));

        assertEquals(400, updated.getResponse().getStatus(), updated.getResponse().getContentAsString());
        assertTrue(updated.getResponse().getContentAsString().contains(GRADE));

        StockOrder unchanged = stockOrderByIdentifier("OUT-EDIT");
        assertNotNull(unchanged);
        assertEquals(1, unchanged.getDocumentRequirements().size());
        assertEquals(1, unchanged.getProcessingEFValues().size());
        assertEquals("A", unchanged.getProcessingEFValues().iterator().next().getStringValue());
    }

    @Test
    void nonexistentDocumentIsRejected() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-INVALID-DOC", stringField("A"), documentValue(999999L))));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertNull(stockOrderByIdentifier("OUT-INVALID-DOC"));
    }

    @Test
    void fieldWithWrongTypeIsRejected() throws Exception {
        String numericGrade = "{\"evidenceFieldId\":%d,\"numericValue\":10}".formatted(evidenceFieldId);

        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-WRONG-TYPE", numericGrade, documentValue())));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains(GRADE));
        assertNull(stockOrderByIdentifier("OUT-WRONG-TYPE"));
    }

    @Test
    void nullFieldEntryIsRejectedEvenWithValidEvidence() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-NULL-FIELD", stringField("A") + ",null", documentValue())));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("must not be null"));
        assertNull(stockOrderByIdentifier("OUT-NULL-FIELD"));
    }

    @Test
    void nullTypeEntryIsRejectedEvenWithValidEvidence() throws Exception {
        MvcResult result = putProcessingOrder(processingOrderBody(
                output("OUT-NULL-TYPE", stringField("A"), documentValue().strip() + ",null")));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("must not be null"));
        assertNull(stockOrderByIdentifier("OUT-NULL-TYPE"));
    }

    @Test
    void nullOtherEvidenceEntryIsRejected() throws Exception {
        String target = output("OUT-NULL-OTHER", stringField("A"), documentValue())
                .replace("\"otherEvidenceDocuments\":[]", "\"otherEvidenceDocuments\":[null]");

        MvcResult result = putProcessingOrder(processingOrderBody(target));

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("must not be null"));
        assertNull(stockOrderByIdentifier("OUT-NULL-OTHER"));
    }

    private MvcResult putProcessingOrder(String body) throws Exception {
        return mockMvc.perform(put("/api/chain/processing-order")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String processingOrderBody(String targetStockOrders) {
        return processingOrderBody(null, null, targetStockOrders);
    }

    private String processingOrderBody(Long processingOrderId, Long transactionId, String targetStockOrders) {
        String idField = processingOrderId == null ? "" : "\"id\":" + processingOrderId + ",";
        String transactionIdField = transactionId == null ? "" : "\"id\":" + transactionId + ",";

        return """
                {%s"processingAction":{"id":%d},"initiatorUserId":%d,"processingDate":"2026-11-27",
                 "inputTransactions":[{%s"company":{"id":%d},
                   "sourceStockOrder":{"id":%d,"orderType":"PURCHASE_ORDER","totalQuantity":100,
                     "fulfilledQuantity":100,"availableQuantity":100},
                   "status":"PENDING","inputQuantity":10,"outputQuantity":10}],
                 "targetStockOrders":[%s]}
                """.formatted(idField, actionId, userId, transactionIdField, companyId, sourceOrderId, targetStockOrders);
    }

    private String output(String identifier, String evidenceFields, String evidenceTypes) {
        return output(null, identifier, evidenceFields, evidenceTypes);
    }

    private String output(Long id, String identifier, String evidenceFields, String evidenceTypes) {
        String idField = id == null ? "" : "\"id\":" + id + ",";

        return """
                {%s"identifier":"%s","orderType":"PROCESSING_ORDER","company":{"id":%d},
                 "facility":{"id":%d},"semiProduct":{"id":%d},
                 "totalQuantity":8,"fulfilledQuantity":8,"availableQuantity":8,
                 "requiredEvidenceFieldValues":[%s],
                 "requiredEvidenceTypeValues":[%s],
                 "otherEvidenceDocuments":[]}
                """.formatted(idField, identifier, companyId, facilityId, outputSemiProductId,
                evidenceFields, evidenceTypes);
    }

    private String stringField(String value) {
        return "{\"evidenceFieldId\":%d,\"stringValue\":\"%s\"}".formatted(evidenceFieldId, value);
    }

    private String documentValue() {
        return documentValue(documentId);
    }

    private String documentValue(Long id) {
        return """
                {"evidenceTypeId":%d,"date":"2026-11-27","document":{"id":%d,"storageKey":"evidence/qc-report.pdf","name":"qc-report.pdf"}}
                """.formatted(evidenceTypeId, id);
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

    private BigDecimal sourceAvailable() {
        em.flush();
        return em.find(StockOrder.class, sourceOrderId).getAvailableQuantity();
    }

    private StockOrder stockOrderByIdentifier(String identifier) {
        em.flush();
        List<StockOrder> results = em.createQuery(
                        "SELECT so FROM StockOrder so WHERE so.identifier = :identifier", StockOrder.class)
                .setParameter("identifier", identifier)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
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
