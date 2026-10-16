package com.abelium.inatrace.components.value_chain;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceField;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChainFacilityType;
import com.abelium.inatrace.db.entities.value_chain.ValueChainMeasureUnitType;
import com.abelium.inatrace.db.entities.value_chain.ValueChainProcEvidenceType;
import com.abelium.inatrace.db.entities.value_chain.ValueChainProcessingEvidenceField;
import com.abelium.inatrace.db.entities.value_chain.ValueChainSemiProduct;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.db.enums.EvidenceType;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValueChainApiTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;

    private Long valueChainId;
    private Long productTypeId;
    private Long initialFacilityTypeId;
    private Long replacementFacilityTypeId;
    private Long initialMeasureUnitTypeId;
    private Long replacementMeasureUnitTypeId;
    private Long initialProcessingEvidenceTypeId;
    private Long replacementProcessingEvidenceTypeId;
    private Long initialProcessingEvidenceFieldId;
    private Long replacementProcessingEvidenceFieldId;
    private Long initialSemiProductId;
    private Long replacementSemiProductId;
    private Cookie systemAdminSession;

    @BeforeEach
    void seed() {
        User systemAdmin = new User();
        systemAdmin.setEmail("value-chain-admin-50@test");
        systemAdmin.setName("Value chain");
        systemAdmin.setSurname("Administrator");
        systemAdmin.setPassword("not-used");
        systemAdmin.setRole(UserRole.SYSTEM_ADMIN);
        systemAdmin.setStatus(UserStatus.ACTIVE);
        em.persist(systemAdmin);

        ProductType productType = new ProductType();
        productType.setCode("VALUE_CHAIN_OPTIONAL_50");
        productType.setName("Value chain optional collections");
        em.persist(productType);

        FacilityType initialFacilityType = new FacilityType("VALUE_CHAIN_FACILITY_A_50", "Initial facility type");
        FacilityType replacementFacilityType = new FacilityType("VALUE_CHAIN_FACILITY_B_50", "Replacement facility type");
        em.persist(initialFacilityType);
        em.persist(replacementFacilityType);

        MeasureUnitType initialMeasureUnitType = new MeasureUnitType("VALUE_CHAIN_MEASURE_A_50", "Initial measure", BigDecimal.ONE);
        MeasureUnitType replacementMeasureUnitType = new MeasureUnitType("VALUE_CHAIN_MEASURE_B_50", "Replacement measure", BigDecimal.TEN);
        em.persist(initialMeasureUnitType);
        em.persist(replacementMeasureUnitType);

        ProcessingEvidenceType initialEvidenceType = processingEvidenceType("VALUE_CHAIN_EVIDENCE_A_50", "Initial evidence");
        ProcessingEvidenceType replacementEvidenceType = processingEvidenceType("VALUE_CHAIN_EVIDENCE_B_50", "Replacement evidence");
        em.persist(initialEvidenceType);
        em.persist(replacementEvidenceType);

        ProcessingEvidenceField initialEvidenceField = processingEvidenceField("value-chain-field-a-50", "Initial evidence field");
        ProcessingEvidenceField replacementEvidenceField = processingEvidenceField("value-chain-field-b-50", "Replacement evidence field");
        em.persist(initialEvidenceField);
        em.persist(replacementEvidenceField);

        SemiProduct initialSemiProduct = semiProduct("Initial semi product");
        SemiProduct replacementSemiProduct = semiProduct("Replacement semi product");
        em.persist(initialSemiProduct);
        em.persist(replacementSemiProduct);

        ValueChain valueChain = new ValueChain();
        valueChain.setName("VALUE-CHAIN-OPTIONAL-50");
        valueChain.setDescription("Optional collection contract");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setCreatedBy(systemAdmin);
        valueChain.setProductType(productType);
        em.persist(valueChain);

        valueChain.getFacilityTypes().add(persist(new ValueChainFacilityType(valueChain, initialFacilityType)));
        valueChain.getMeasureUnitTypes().add(persist(new ValueChainMeasureUnitType(valueChain, initialMeasureUnitType)));
        valueChain.getProcEvidenceTypes().add(persist(new ValueChainProcEvidenceType(valueChain, initialEvidenceType)));
        valueChain.getProcessingEvidenceFields().add(persist(new ValueChainProcessingEvidenceField(valueChain, initialEvidenceField)));
        valueChain.getSemiProducts().add(persist(new ValueChainSemiProduct(valueChain, initialSemiProduct)));

        valueChainId = valueChain.getId();
        productTypeId = productType.getId();
        initialFacilityTypeId = initialFacilityType.getId();
        replacementFacilityTypeId = replacementFacilityType.getId();
        initialMeasureUnitTypeId = initialMeasureUnitType.getId();
        replacementMeasureUnitTypeId = replacementMeasureUnitType.getId();
        initialProcessingEvidenceTypeId = initialEvidenceType.getId();
        replacementProcessingEvidenceTypeId = replacementEvidenceType.getId();
        initialProcessingEvidenceFieldId = initialEvidenceField.getId();
        replacementProcessingEvidenceFieldId = replacementEvidenceField.getId();
        initialSemiProductId = initialSemiProduct.getId();
        replacementSemiProductId = replacementSemiProduct.getId();
        systemAdminSession = new Cookie(accessCookieName, tokenService.createAccessToken(systemAdmin));
        em.flush();
        em.clear();
    }

    @Test
    void optionalCollectionsPreserveReplaceAndClearAssociationsAndResponsesAlwaysReturnArrays() throws Exception {
        assertStatus(put("/api/chain/value-chain").cookie(systemAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valueChainBody(valueChainId, "VALUE-CHAIN-OMIT-50")), 200);
        em.flush();
        em.clear();
        assertAssociationIds(initialFacilityTypeId, initialMeasureUnitTypeId, initialProcessingEvidenceTypeId,
                initialProcessingEvidenceFieldId, initialSemiProductId);

        assertStatus(put("/api/chain/value-chain").cookie(systemAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valueChainBody(valueChainId, "VALUE-CHAIN-NULL-50", allCollections("null", "null", "null", "null", "null"))), 200);
        em.flush();
        em.clear();
        assertAssociationIds(initialFacilityTypeId, initialMeasureUnitTypeId, initialProcessingEvidenceTypeId,
                initialProcessingEvidenceFieldId, initialSemiProductId);

        assertStatus(put("/api/chain/value-chain").cookie(systemAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valueChainBody(valueChainId, "VALUE-CHAIN-REPLACE-50", allCollections(
                        idList(replacementFacilityTypeId), idList(replacementMeasureUnitTypeId),
                        idList(replacementProcessingEvidenceTypeId), idList(replacementProcessingEvidenceFieldId),
                        idList(replacementSemiProductId)))), 200);
        em.flush();
        em.clear();
        assertAssociationIds(replacementFacilityTypeId, replacementMeasureUnitTypeId, replacementProcessingEvidenceTypeId,
                replacementProcessingEvidenceFieldId, replacementSemiProductId);

        assertStatus(put("/api/chain/value-chain").cookie(systemAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valueChainBody(valueChainId, "VALUE-CHAIN-CLEAR-50", allCollections("[]", "[]", "[]", "[]", "[]"))), 200);
        em.flush();
        em.clear();
        ValueChain cleared = em.find(ValueChain.class, valueChainId);
        assertTrue(cleared.getFacilityTypes().isEmpty());
        assertTrue(cleared.getMeasureUnitTypes().isEmpty());
        assertTrue(cleared.getProcEvidenceTypes().isEmpty());
        assertTrue(cleared.getProcessingEvidenceFields().isEmpty());
        assertTrue(cleared.getSemiProducts().isEmpty());

        MvcResult afterClear = mockMvc.perform(get("/api/chain/value-chain/" + valueChainId).cookie(systemAdminSession))
                .andReturn();
        String afterClearBody = afterClear.getResponse().getContentAsString();
        assertEquals(200, afterClear.getResponse().getStatus(), afterClearBody);
        assertTrue(afterClearBody.contains("\"facilityTypes\":[]"), afterClearBody);
        assertTrue(afterClearBody.contains("\"measureUnitTypes\":[]"), afterClearBody);
        assertTrue(afterClearBody.contains("\"processingEvidenceTypes\":[]"), afterClearBody);
        assertTrue(afterClearBody.contains("\"processingEvidenceFields\":[]"), afterClearBody);
        assertTrue(afterClearBody.contains("\"semiProducts\":[]"), afterClearBody);

        long before = valueChainCount();
        assertStatus(put("/api/chain/value-chain").cookie(systemAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valueChainBody(null, "VALUE-CHAIN-CREATE-50")), 200);
        assertEquals(before + 1, valueChainCount());
    }

    private String valueChainBody(Long id, String name) {
        return valueChainBody(id, name, "");
    }

    private String valueChainBody(Long id, String name, String collections) {
        String idField = id == null ? "" : "\"id\":" + id + ",";
        return "{" + idField + "\"name\":\"" + name + "\",\"description\":\"Optional collection contract\","
                + "\"productType\":{\"id\":" + productTypeId + "}"
                + collections + "}";
    }

    private String allCollections(String facilityTypes, String measureUnitTypes, String processingEvidenceTypes,
                                  String processingEvidenceFields, String semiProducts) {
        return ",\"facilityTypes\":" + facilityTypes + ",\"measureUnitTypes\":" + measureUnitTypes
                + ",\"processingEvidenceTypes\":" + processingEvidenceTypes
                + ",\"processingEvidenceFields\":" + processingEvidenceFields + ",\"semiProducts\":" + semiProducts;
    }

    private String idList(Long id) {
        return "[{\"id\":" + id + "}]";
    }

    private void assertAssociationIds(Long facilityTypeId, Long measureUnitTypeId, Long processingEvidenceTypeId,
                                      Long processingEvidenceFieldId, Long semiProductId) {
        ValueChain valueChain = em.find(ValueChain.class, valueChainId);
        assertEquals(facilityTypeId, valueChain.getFacilityTypes().iterator().next().getFacilityType().getId());
        assertEquals(measureUnitTypeId, valueChain.getMeasureUnitTypes().iterator().next().getMeasureUnitType().getId());
        assertEquals(processingEvidenceTypeId, valueChain.getProcEvidenceTypes().iterator().next().getProcessingEvidenceType().getId());
        assertEquals(processingEvidenceFieldId, valueChain.getProcessingEvidenceFields().iterator().next().getProcessingEvidenceField().getId());
        assertEquals(semiProductId, valueChain.getSemiProducts().iterator().next().getSemiProduct().getId());
    }

    private ProcessingEvidenceType processingEvidenceType(String code, String label) {
        ProcessingEvidenceType evidenceType = new ProcessingEvidenceType();
        evidenceType.setCode(code);
        evidenceType.setLabel(label);
        evidenceType.setType(EvidenceType.FIELD);
        return evidenceType;
    }

    private ProcessingEvidenceField processingEvidenceField(String fieldName, String label) {
        ProcessingEvidenceField evidenceField = new ProcessingEvidenceField();
        evidenceField.setFieldName(fieldName);
        evidenceField.setLabel(label);
        evidenceField.setType(ProcessingEvidenceFieldType.STRING);
        return evidenceField;
    }

    private SemiProduct semiProduct(String name) {
        SemiProduct semiProduct = new SemiProduct();
        semiProduct.setName(name);
        semiProduct.setDescription(name);
        return semiProduct;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private void assertStatus(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                              int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(expectedStatus, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private long valueChainCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(valueChain) FROM ValueChain valueChain", Long.class).getSingleResult();
    }
}
