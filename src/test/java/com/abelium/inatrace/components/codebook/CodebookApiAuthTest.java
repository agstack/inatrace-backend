package com.abelium.inatrace.components.codebook;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.CurrencyType;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceField;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceFieldTranslation;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceType;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceTypeTranslation;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.ProductTypeTranslation;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.codebook.SemiProductTranslation;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProcessingEvidenceFieldType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Regression coverage for agstack/inatrace-backend#38.
 *
 * <p>Codebooks are global reference data. Every authenticated user may read
 * them; mutations are restricted to the roles declared by each controller and
 * the services additionally reserve edits of existing records for system
 * administrators.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CodebookApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "CODEBOOK-AUTHZ-38";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Cookie systemAdminSession;
    private Cookie regionalAdminSession;
    private Cookie companyUserSession;
    private Long facilityTypeId;
    private Long measureUnitTypeId;
    private Long processingEvidenceTypeId;
    private Long processingEvidenceFieldId;
    private Long semiProductId;
    private Long valueChainId;
    private Long productTypeId;
    private Long enabledCurrencyId;
    private Long disabledCurrencyId;

    @BeforeEach
    void seed() {
        User systemAdmin = user("codebook-system-admin-38@test.local", UserRole.SYSTEM_ADMIN);
        User regionalAdmin = user("codebook-regional-admin-38@test.local", UserRole.REGIONAL_ADMIN);
        User companyUser = user("codebook-company-user-38@test.local", UserRole.USER);
        Company company = company("Codebook authorization company");
        enroll(companyUser, company);

        FacilityType facilityType = new FacilityType("FACILITY_38", MARKER + " facility");
        em.persist(facilityType);
        facilityTypeId = facilityType.getId();

        MeasureUnitType measureUnitType = new MeasureUnitType("MEASURE_38", MARKER + " measure", BigDecimal.ONE);
        em.persist(measureUnitType);
        measureUnitTypeId = measureUnitType.getId();

        ProductType productType = new ProductType();
        productType.setCode("PRODUCT_38");
        productType.setName(MARKER + " product");
        productType.setDescription(MARKER + " product description");
        em.persist(productType);
        productTranslation(productType, MARKER + " product", MARKER + " product description");
        productTypeId = productType.getId();

        ProcessingEvidenceType evidenceType = new ProcessingEvidenceType();
        evidenceType.setCode("EVIDENCE_38");
        evidenceType.setLabel(MARKER + " evidence");
        evidenceType.setType(com.abelium.inatrace.db.enums.EvidenceType.DOCUMENT);
        evidenceType.setFairness(false);
        evidenceType.setProvenance(false);
        evidenceType.setQuality(false);
        em.persist(evidenceType);
        evidenceTypeTranslation(evidenceType, MARKER + " evidence");
        processingEvidenceTypeId = evidenceType.getId();

        ProcessingEvidenceField evidenceField = new ProcessingEvidenceField();
        evidenceField.setFieldName("field_38");
        evidenceField.setLabel(MARKER + " field");
        evidenceField.setType(ProcessingEvidenceFieldType.STRING);
        em.persist(evidenceField);
        evidenceFieldTranslation(evidenceField, MARKER + " field");
        processingEvidenceFieldId = evidenceField.getId();

        SemiProduct semiProduct = new SemiProduct();
        semiProduct.setName(MARKER + " semi");
        semiProduct.setDescription(MARKER + " semi description");
        semiProduct.setMeasurementUnitType(measureUnitType);
        semiProduct.setSKU(false);
        semiProduct.setBuyable(true);
        semiProduct.setSKUEndCustomer(false);
        em.persist(semiProduct);
        semiProductTranslation(semiProduct, MARKER + " semi", MARKER + " semi description");
        semiProductId = semiProduct.getId();

        ValueChain valueChain = new ValueChain();
        valueChain.setName(MARKER + " value chain");
        valueChain.setDescription(MARKER + " value chain description");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setCreatedBy(systemAdmin);
        valueChain.setProductType(productType);
        em.persist(valueChain);
        valueChainId = valueChain.getId();

        CurrencyType enabledCurrency = currency("EUR_38", MARKER + " enabled currency", true);
        CurrencyType disabledCurrency = currency("USD_38", MARKER + " disabled currency", false);
        enabledCurrencyId = enabledCurrency.getId();
        disabledCurrencyId = disabledCurrency.getId();

        em.flush();
        systemAdminSession = cookie(systemAdmin);
        regionalAdminSession = cookie(regionalAdmin);
        companyUserSession = cookie(companyUser);
        em.clear();
    }

    @Test
    void everyCurrentCodebookReadIsAvailableToAuthenticatedUsersOnly() throws Exception {
        for (ReadEndpoint endpoint : readEndpoints()) {
            MvcResult allowed = mockMvc.perform(endpoint.request().get().cookie(companyUserSession)).andReturn();
            assertEquals(200, allowed.getResponse().getStatus(), endpoint.name() + ": " + allowed.getResponse().getContentAsString());

            MvcResult anonymous = mockMvc.perform(endpoint.request().get()).andReturn();
            assertEquals(401, anonymous.getResponse().getStatus(), endpoint.name());
            assertFalse(anonymous.getResponse().getContentAsString().contains(MARKER), endpoint.name());
        }
    }

    @Test
    void ordinaryCompanyUsersCannotModifyAnyCodebookAndResponsesDoNotLeakMarkers() throws Exception {
        for (MutationEndpoint endpoint : protectedMutations()) {
            MvcResult refused = mockMvc.perform(endpoint.request().get().cookie(companyUserSession)).andReturn();
            assertEquals(403, refused.getResponse().getStatus(), endpoint.name() + ": " + refused.getResponse().getContentAsString());
            assertFalse(refused.getResponse().getContentAsString().contains(MARKER), endpoint.name());

            MvcResult anonymous = mockMvc.perform(endpoint.request().get()).andReturn();
            assertEquals(401, anonymous.getResponse().getStatus(), endpoint.name());
            assertFalse(anonymous.getResponse().getContentAsString().contains(MARKER), endpoint.name());
        }

        assertSeededCodebooksUnchanged();
    }

    @Test
    void regionalAdminsCanCreateButCannotEditExistingRestrictedCodebooks() throws Exception {
        long facilityTypeCount = count(FacilityType.class);
        long measureUnitTypeCount = count(MeasureUnitType.class);
        long semiProductCount = count(SemiProduct.class);
        long processingEvidenceTypeCount = count(ProcessingEvidenceType.class);
        long processingEvidenceFieldCount = count(ProcessingEvidenceField.class);
        long valueChainCount = count(ValueChain.class);
        long productTypeCount = count(ProductType.class);
        assertSuccessful(regionalAdminSession, "regional facility type create", put("/api/chain/facility-type"),
                facilityTypeBody(null, "REGIONAL-CREATED-38"));
        assertSuccessful(regionalAdminSession, "regional measure unit type create", put("/api/chain/measure-unit-type"),
                measureUnitTypeBody(null, "REGIONAL-CREATED-38"));
        assertSuccessful(regionalAdminSession, "regional semi-product create", put("/api/chain/semi-product"),
                semiProductBody(null, "REGIONAL-CREATED-38"));
        assertSuccessful(regionalAdminSession, "regional processing evidence type create", put("/api/chain/processing-evidence-type"),
                processingEvidenceTypeBody(null, "REGIONAL-CREATED-38"));
        assertSuccessful(regionalAdminSession, "regional processing evidence field create", put("/api/chain/processing-evidence-field"),
                processingEvidenceFieldBody(null, "REGIONAL-CREATED-38"));
        assertSuccessful(regionalAdminSession, "regional value chain create", put("/api/chain/value-chain"),
                valueChainBody(null, "REGIONAL-CREATED-38"));
        assertSuccessful(regionalAdminSession, "regional product type create", post("/api/product-type"),
                productTypeBody(null, "REGIONAL-CREATED-38"));
        em.flush();
        assertEquals(facilityTypeCount + 1, count(FacilityType.class));
        assertEquals(measureUnitTypeCount + 1, count(MeasureUnitType.class));
        assertEquals(semiProductCount + 1, count(SemiProduct.class));
        assertEquals(processingEvidenceTypeCount + 1, count(ProcessingEvidenceType.class));
        assertEquals(processingEvidenceFieldCount + 1, count(ProcessingEvidenceField.class));
        assertEquals(valueChainCount + 1, count(ValueChain.class));
        assertEquals(productTypeCount + 1, count(ProductType.class));

        for (MutationEndpoint endpoint : regionalEditMutations()) {
            MvcResult refused = mockMvc.perform(endpoint.request().get().cookie(regionalAdminSession)).andReturn();
            assertEquals(403, refused.getResponse().getStatus(), endpoint.name() + ": " + refused.getResponse().getContentAsString());
            assertFalse(refused.getResponse().getContentAsString().contains("REGIONAL-EDIT-38"), endpoint.name());
        }

        assertSeededCodebooksUnchanged();
    }

    @Test
    void systemAdminsCanEditCodebooksAndPerformSystemOnlyStatusChanges() throws Exception {
        MvcResult facilityUpdate = mockMvc.perform(put("/api/chain/facility-type").cookie(systemAdminSession)
                .contentType(MediaType.APPLICATION_JSON).content(facilityTypeBody(facilityTypeId, "SYSTEM-EDIT-38"))).andReturn();
        assertEquals(200, facilityUpdate.getResponse().getStatus(), facilityUpdate.getResponse().getContentAsString());

        assertSuccessful(systemAdminSession, "measure unit type update", put("/api/chain/measure-unit-type"),
                measureUnitTypeBody(measureUnitTypeId, "SYSTEM-EDIT-38"));
        assertSuccessful(systemAdminSession, "semi-product update", put("/api/chain/semi-product"),
                semiProductBody(semiProductId, "SYSTEM-EDIT-38"));
        assertSuccessful(systemAdminSession, "processing evidence type update", put("/api/chain/processing-evidence-type"),
                processingEvidenceTypeBody(processingEvidenceTypeId, "SYSTEM-EDIT-38"));
        assertSuccessful(systemAdminSession, "processing evidence field update", put("/api/chain/processing-evidence-field"),
                processingEvidenceFieldBody(processingEvidenceFieldId, "SYSTEM-EDIT-38"));
        assertSuccessful(systemAdminSession, "value chain update", put("/api/chain/value-chain"),
                valueChainBody(valueChainId, "SYSTEM-EDIT-38"));
        assertSuccessful(systemAdminSession, "product type update", put("/api/product-type"),
                productTypeBody(productTypeId, "SYSTEM-EDIT-38"));

        MvcResult disable = mockMvc.perform(put("/api/chain/currency-type/" + enabledCurrencyId + "/disable")
                .cookie(systemAdminSession)).andReturn();
        assertEquals(200, disable.getResponse().getStatus(), disable.getResponse().getContentAsString());

        MvcResult enable = mockMvc.perform(post("/api/chain/value-chain/" + valueChainId + "/enable")
                .cookie(systemAdminSession)).andReturn();
        assertEquals(200, enable.getResponse().getStatus(), enable.getResponse().getContentAsString());

        MvcResult regionalEnable = mockMvc.perform(put("/api/chain/currency-type/" + disabledCurrencyId + "/enable")
                .cookie(regionalAdminSession)).andReturn();
        assertEquals(200, regionalEnable.getResponse().getStatus(), regionalEnable.getResponse().getContentAsString());

        em.flush();
        em.clear();
        assertEquals("SYSTEM-EDIT-38", em.find(FacilityType.class, facilityTypeId).getLabel());
        assertEquals("SYSTEM-EDIT-38", em.find(MeasureUnitType.class, measureUnitTypeId).getLabel());
        assertEquals("SYSTEM-EDIT-38", em.find(SemiProduct.class, semiProductId).getName());
        assertEquals("SYSTEM-EDIT-38", em.find(ProcessingEvidenceType.class, processingEvidenceTypeId).getLabel());
        assertEquals("SYSTEM-EDIT-38", em.find(ProcessingEvidenceField.class, processingEvidenceFieldId).getLabel());
        assertEquals("SYSTEM-EDIT-38", em.find(ValueChain.class, valueChainId).getName());
        assertEquals("SYSTEM-EDIT-38", em.find(ProductType.class, productTypeId).getName());
        assertFalse(em.find(CurrencyType.class, enabledCurrencyId).getEnabled());
        assertTrue(em.find(CurrencyType.class, disabledCurrencyId).getEnabled());
    }

    private List<ReadEndpoint> readEndpoints() {
        return List.of(
                read("enabled currencies", () -> get("/api/chain/currency-type/list/enabled")),
                read("disabled currencies", () -> get("/api/chain/currency-type/list/disabled")),
                read("currency conversion", () -> get("/api/chain/currency/convert/12/EUR/to/EUR")),
                read("dated currency conversion", () -> get("/api/chain/currency/convert/12/EUR/to/EUR/on/2026-01-01")),
                read("facility type list", () -> get("/api/chain/facility-type/list")),
                read("facility type", () -> get("/api/chain/facility-type/" + facilityTypeId)),
                read("measure unit type list", () -> get("/api/chain/measure-unit-type/list")),
                read("measure unit type", () -> get("/api/chain/measure-unit-type/" + measureUnitTypeId)),
                read("processing evidence field list", () -> get("/api/chain/processing-evidence-field/list")),
                read("processing evidence fields by value chains", () -> get("/api/chain/processing-evidence-field/list/by-value-chains").param("valueChainIds", valueChainId.toString())),
                read("processing evidence field", () -> get("/api/chain/processing-evidence-field/" + processingEvidenceFieldId)),
                read("processing evidence type list", () -> get("/api/chain/processing-evidence-type/list")),
                read("processing evidence types by value chains", () -> get("/api/chain/processing-evidence-type/list/by-value-chains").param("valueChainIds", valueChainId.toString())),
                read("processing evidence type", () -> get("/api/chain/processing-evidence-type/" + processingEvidenceTypeId)),
                read("semi-product list", () -> get("/api/chain/semi-product/list")),
                read("semi-products by value chains", () -> get("/api/chain/semi-product/list/by-value-chains").param("valueChainIds", valueChainId.toString())),
                read("semi-product", () -> get("/api/chain/semi-product/" + semiProductId)),
                read("semi-product details", () -> get("/api/chain/semi-product/" + semiProductId + "/detail")),
                read("value chain list", () -> get("/api/chain/value-chain/list")),
                read("value chain", () -> get("/api/chain/value-chain/" + valueChainId)),
                read("product type list", () -> get("/api/product-type")),
                read("product type", () -> get("/api/product-type/" + productTypeId).param("id", productTypeId.toString()))
        );
    }

    private List<MutationEndpoint> protectedMutations() {
        return List.of(
                mutation("semi-product upsert", () -> json(put("/api/chain/semi-product"), semiProductBody(semiProductId, "ORDINARY-EDIT-38"))),
                mutation("facility type upsert", () -> json(put("/api/chain/facility-type"), facilityTypeBody(facilityTypeId, "ORDINARY-EDIT-38"))),
                mutation("measure unit type upsert", () -> json(put("/api/chain/measure-unit-type"), measureUnitTypeBody(measureUnitTypeId, "ORDINARY-EDIT-38"))),
                mutation("processing evidence type upsert", () -> json(put("/api/chain/processing-evidence-type"), processingEvidenceTypeBody(processingEvidenceTypeId, "ORDINARY-EDIT-38"))),
                mutation("processing evidence field upsert", () -> json(put("/api/chain/processing-evidence-field"), processingEvidenceFieldBody(processingEvidenceFieldId, "ORDINARY-EDIT-38"))),
                mutation("value chain upsert", () -> json(put("/api/chain/value-chain"), valueChainBody(valueChainId, "ORDINARY-EDIT-38"))),
                mutation("enable currency", () -> put("/api/chain/currency-type/" + disabledCurrencyId + "/enable")),
                mutation("disable currency", () -> put("/api/chain/currency-type/" + enabledCurrencyId + "/disable")),
                mutation("create product type", () -> json(post("/api/product-type"), productTypeBody(null, "ORDINARY-CREATED-38"))),
                mutation("update product type", () -> json(put("/api/product-type"), productTypeBody(productTypeId, "ORDINARY-EDIT-38"))),
                mutation("enable value chain", () -> post("/api/chain/value-chain/" + valueChainId + "/enable")),
                mutation("disable value chain", () -> post("/api/chain/value-chain/" + valueChainId + "/disable")),
                mutation("delete semi-product", () -> delete("/api/chain/semi-product/" + semiProductId)),
                mutation("delete facility type", () -> delete("/api/chain/facility-type/" + facilityTypeId)),
                mutation("delete measure unit type", () -> delete("/api/chain/measure-unit-type/" + measureUnitTypeId)),
                mutation("delete processing evidence type", () -> delete("/api/chain/processing-evidence-type/" + processingEvidenceTypeId)),
                mutation("delete processing evidence field", () -> delete("/api/chain/processing-evidence-field/" + processingEvidenceFieldId)),
                mutation("delete value chain", () -> delete("/api/chain/value-chain/" + valueChainId)),
                mutation("delete product type", () -> delete("/api/product-type/" + productTypeId).param("id", productTypeId.toString()))
        );
    }

    private List<MutationEndpoint> regionalEditMutations() {
        return List.of(
                mutation("regional facility type edit", () -> json(put("/api/chain/facility-type"), facilityTypeBody(facilityTypeId, "REGIONAL-EDIT-38"))),
                mutation("regional measure unit type edit", () -> json(put("/api/chain/measure-unit-type"), measureUnitTypeBody(measureUnitTypeId, "REGIONAL-EDIT-38"))),
                mutation("regional semi-product edit", () -> json(put("/api/chain/semi-product"), semiProductBody(semiProductId, "REGIONAL-EDIT-38"))),
                mutation("regional processing evidence type edit", () -> json(put("/api/chain/processing-evidence-type"), processingEvidenceTypeBody(processingEvidenceTypeId, "REGIONAL-EDIT-38"))),
                mutation("regional processing evidence field edit", () -> json(put("/api/chain/processing-evidence-field"), processingEvidenceFieldBody(processingEvidenceFieldId, "REGIONAL-EDIT-38"))),
                mutation("regional value chain edit", () -> json(put("/api/chain/value-chain"), valueChainBody(valueChainId, "REGIONAL-EDIT-38"))),
                mutation("regional currency disable", () -> put("/api/chain/currency-type/" + enabledCurrencyId + "/disable")),
                mutation("regional value chain enable", () -> post("/api/chain/value-chain/" + valueChainId + "/enable")),
                mutation("regional value chain disable", () -> post("/api/chain/value-chain/" + valueChainId + "/disable")),
                mutation("regional product type update", () -> json(put("/api/product-type"), productTypeBody(productTypeId, "REGIONAL-EDIT-38"))),
                mutation("regional semi-product delete", () -> delete("/api/chain/semi-product/" + semiProductId)),
                mutation("regional facility type delete", () -> delete("/api/chain/facility-type/" + facilityTypeId)),
                mutation("regional measure unit type delete", () -> delete("/api/chain/measure-unit-type/" + measureUnitTypeId)),
                mutation("regional processing evidence type delete", () -> delete("/api/chain/processing-evidence-type/" + processingEvidenceTypeId)),
                mutation("regional processing evidence field delete", () -> delete("/api/chain/processing-evidence-field/" + processingEvidenceFieldId)),
                mutation("regional value chain delete", () -> delete("/api/chain/value-chain/" + valueChainId)),
                mutation("regional product type delete", () -> delete("/api/product-type/" + productTypeId).param("id", productTypeId.toString()))
        );
    }

    private String facilityTypeBody(Long id, String label) {
        return "{\"id\":%s,\"code\":\"FACILITY_38\",\"label\":\"%s\"}".formatted(id == null ? "null" : id, label);
    }

    private String measureUnitTypeBody(Long id, String label) {
        return "{\"id\":%s,\"code\":\"MEASURE_38\",\"label\":\"%s\",\"weight\":1}".formatted(id == null ? "null" : id, label);
    }

    private String semiProductBody(Long id, String name) {
        return """
                {"id":%s,"name":"%s","description":"%s description","measurementUnitType":{"id":%d},
                 "sku":false,"buyable":true,"skueNdCustomer":false,
                 "translations":[{"language":"EN","name":"%s","description":"%s description"}]}
                """.formatted(id == null ? "null" : id, name, name, measureUnitTypeId, name, name);
    }

    private String processingEvidenceTypeBody(Long id, String label) {
        return """
                {"id":%s,"code":"EVIDENCE_38","label":"%s","type":"DOCUMENT","fairness":false,
                 "provenance":false,"quality":false,"translations":[{"language":"EN","label":"%s"}]}
                """.formatted(id == null ? "null" : id, label, label);
    }

    private String processingEvidenceFieldBody(Long id, String label) {
        return """
                {"id":%s,"fieldName":"field_38","label":"%s","type":"STRING",
                 "translations":[{"language":"EN","label":"%s"}]}
                """.formatted(id == null ? "null" : id, label, label);
    }

    private String valueChainBody(Long id, String name) {
        return """
                {"id":%s,"name":"%s","description":"%s description","facilityTypes":[],"measureUnitTypes":[],
                 "processingEvidenceTypes":[],"processingEvidenceFields":[],"semiProducts":[],"productType":{"id":%d}}
                """.formatted(id == null ? "null" : id, name, name, productTypeId);
    }

    private String productTypeBody(Long id, String name) {
        String idField = id == null ? "" : "\"id\":" + id + ",";
        return """
                {%s"code":"PRODUCT_38","name":"%s","description":"%s description",
                 "translations":[{"language":"EN","name":"%s","description":"%s description"}]}
                """.formatted(idField, name, name, name, name);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
        return request.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private void assertSuccessful(Cookie session, String operation, MockHttpServletRequestBuilder request, String body) throws Exception {
        MvcResult result = mockMvc.perform(json(request, body).cookie(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), operation + ": " + result.getResponse().getContentAsString());
    }

    private long count(Class<?> entityClass) {
        return em.createQuery("SELECT COUNT(entity) FROM " + entityClass.getSimpleName() + " entity", Long.class)
                .getSingleResult();
    }

    private void assertSeededCodebooksUnchanged() {
        em.clear();
        assertEquals(MARKER + " facility", em.find(FacilityType.class, facilityTypeId).getLabel());
        assertEquals(MARKER + " measure", em.find(MeasureUnitType.class, measureUnitTypeId).getLabel());
        assertEquals(MARKER + " semi", em.find(SemiProduct.class, semiProductId).getName());
        assertEquals(MARKER + " evidence", em.find(ProcessingEvidenceType.class, processingEvidenceTypeId).getLabel());
        assertEquals(MARKER + " field", em.find(ProcessingEvidenceField.class, processingEvidenceFieldId).getLabel());
        assertEquals(MARKER + " value chain", em.find(ValueChain.class, valueChainId).getName());
        assertEquals(MARKER + " product", em.find(ProductType.class, productTypeId).getName());
        assertTrue(em.find(CurrencyType.class, enabledCurrencyId).getEnabled());
        assertFalse(em.find(CurrencyType.class, disabledCurrencyId).getEnabled());
    }

    private ReadEndpoint read(String name, Supplier<MockHttpServletRequestBuilder> request) {
        return new ReadEndpoint(name, request);
    }

    private MutationEndpoint mutation(String name, Supplier<MockHttpServletRequestBuilder> request) {
        return new MutationEndpoint(name, request);
    }

    private CurrencyType currency(String code, String label, boolean enabled) {
        CurrencyType currency = new CurrencyType();
        currency.setCode(code);
        currency.setLabel(label);
        currency.setEnabled(enabled);
        em.persist(currency);
        return currency;
    }

    private void semiProductTranslation(SemiProduct semiProduct, String name, String description) {
        SemiProductTranslation translation = new SemiProductTranslation();
        translation.setSemiProduct(semiProduct);
        translation.setLanguage(Language.EN);
        translation.setName(name);
        translation.setDescription(description);
        em.persist(translation);
        semiProduct.getSemiProductTranslations().add(translation);
    }

    private void evidenceTypeTranslation(ProcessingEvidenceType type, String label) {
        ProcessingEvidenceTypeTranslation translation = new ProcessingEvidenceTypeTranslation();
        translation.setProcessingEvidenceType(type);
        translation.setLanguage(Language.EN);
        translation.setLabel(label);
        em.persist(translation);
        type.getTranslations().add(translation);
    }

    private void evidenceFieldTranslation(ProcessingEvidenceField field, String label) {
        ProcessingEvidenceFieldTranslation translation = new ProcessingEvidenceFieldTranslation();
        translation.setProcessingEvidenceField(field);
        translation.setLanguage(Language.EN);
        translation.setLabel(label);
        em.persist(translation);
        field.getTranslations().add(translation);
    }

    private void productTranslation(ProductType productType, String name, String description) {
        ProductTypeTranslation translation = new ProductTypeTranslation();
        translation.setProductType(productType);
        translation.setLanguage(Language.EN);
        translation.setName(name);
        translation.setDescription(description);
        em.persist(translation);
        productType.getProductTypeTranslations().add(translation);
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private User user(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setSurname("Test");
        user.setPassword("not-used-in-this-test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    private void enroll(User user, Company company) {
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(company);
        enrollment.setRole(CompanyUserRole.COMPANY_USER);
        em.persist(enrollment);
        company.getUsers().add(enrollment);
    }

    private Cookie cookie(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }

    private record ReadEndpoint(String name, Supplier<MockHttpServletRequestBuilder> request) { }

    private record MutationEndpoint(String name, Supplier<MockHttpServletRequestBuilder> request) { }
}
