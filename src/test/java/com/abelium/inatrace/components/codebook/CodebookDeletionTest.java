package com.abelium.inatrace.components.codebook;

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
import com.abelium.inatrace.db.entities.value_chain.ValueChainProcEvidenceType;
import com.abelium.inatrace.db.entities.value_chain.ValueChainProcessingEvidenceField;
import com.abelium.inatrace.db.entities.value_chain.ValueChainSemiProduct;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.db.enums.EvidenceType;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Deleting a codebook entry that is still in use answers a clear error. Before, semi-products,
 * product types, facility types, measure unit types and processing evidence types and fields were
 * removed without checking their references, so the foreign key made the delete fail at commit as a
 * 500. Unused entries are still deleted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CodebookDeletionTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private User admin;
    private Cookie session;

    private record Entry(String name, Class<?> type, Long id, MockHttpServletRequestBuilder request) { }

    @BeforeEach
    void seed() {
        admin = new User();
        admin.setEmail("codebook-deletion@admin.test");
        admin.setName("Codebook");
        admin.setSurname("Deletion");
        admin.setPassword("not-used-in-this-test");
        admin.setRole(UserRole.SYSTEM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        em.persist(admin);
        session = new Cookie(accessCookieName, tokenService.createAccessToken(admin));
    }

    @Test
    void deleteRefusesEntriesInUse() throws Exception {
        MeasureUnitType unit = measureUnitType("IN_USE");
        SemiProduct semiProduct = semiProduct("In use", unit);
        FacilityType facilityType = facilityType("IN_USE");
        ProcessingEvidenceType evidenceType = evidenceType("IN_USE");
        ProcessingEvidenceField evidenceField = evidenceField("IN_USE");
        ProductType productType = productType("IN_USE");

        // One value chain references them all; the measure unit is referenced by the semi-product.
        ValueChain valueChain = new ValueChain();
        valueChain.setName("Codebook deletion");
        valueChain.setDescription("Codebook deletion");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setCreatedBy(admin);
        valueChain.setProductType(productType);
        em.persist(valueChain);
        em.persist(new ValueChainSemiProduct(valueChain, semiProduct));
        em.persist(new ValueChainFacilityType(valueChain, facilityType));
        em.persist(new ValueChainProcEvidenceType(valueChain, evidenceType));
        em.persist(new ValueChainProcessingEvidenceField(valueChain, evidenceField));
        em.flush();
        em.clear();

        for (Entry entry : entries(semiProduct, facilityType, unit, evidenceType, evidenceField, productType)) {
            MvcResult result = mockMvc.perform(entry.request().cookie(session)).andReturn();
            assertEquals(400, result.getResponse().getStatus(), entry.name() + ": " + result.getResponse().getContentAsString());
            assertTrue(result.getResponse().getContentAsString().contains("that is in use"), entry.name());
            em.clear();
            assertNotNull(em.find(entry.type(), entry.id()), entry.name() + " should be kept");
        }
    }

    @Test
    void deleteRemovesUnusedEntries() throws Exception {
        List<Entry> entries = entries(semiProduct("Unused", null), facilityType("UNUSED"), measureUnitType("UNUSED"),
                evidenceType("UNUSED"), evidenceField("UNUSED"), productType("UNUSED"));
        em.flush();
        em.clear();

        for (Entry entry : entries) {
            MvcResult result = mockMvc.perform(entry.request().cookie(session)).andReturn();
            assertEquals(200, result.getResponse().getStatus(), entry.name() + ": " + result.getResponse().getContentAsString());
            em.flush();
            em.clear();
            assertNull(em.find(entry.type(), entry.id()), entry.name() + " should be deleted");
        }
    }

    // ---------------------------------------------------------------------- helpers

    private List<Entry> entries(SemiProduct semiProduct, FacilityType facilityType, MeasureUnitType unit,
                                ProcessingEvidenceType evidenceType, ProcessingEvidenceField evidenceField, ProductType productType) {
        return List.of(
                new Entry("semi-product", SemiProduct.class, semiProduct.getId(), delete("/api/chain/semi-product/" + semiProduct.getId())),
                new Entry("facility type", FacilityType.class, facilityType.getId(), delete("/api/chain/facility-type/" + facilityType.getId())),
                new Entry("measure unit type", MeasureUnitType.class, unit.getId(), delete("/api/chain/measure-unit-type/" + unit.getId())),
                new Entry("processing evidence type", ProcessingEvidenceType.class, evidenceType.getId(),
                        delete("/api/chain/processing-evidence-type/" + evidenceType.getId())),
                new Entry("processing evidence field", ProcessingEvidenceField.class, evidenceField.getId(),
                        delete("/api/chain/processing-evidence-field/" + evidenceField.getId())),
                // The product type route binds the id as a query parameter (see docs/api-auth-coverage/38-codebooks.md).
                new Entry("product type", ProductType.class, productType.getId(),
                        delete("/api/product-type/" + productType.getId()).param("id", productType.getId().toString())));
    }

    private MeasureUnitType measureUnitType(String code) {
        MeasureUnitType unit = new MeasureUnitType("DELETION_" + code, "Deletion " + code, BigDecimal.ONE);
        em.persist(unit);
        return unit;
    }

    private SemiProduct semiProduct(String name, MeasureUnitType unit) {
        SemiProduct semiProduct = new SemiProduct();
        semiProduct.setName("Deletion " + name);
        semiProduct.setDescription("Deletion " + name);
        semiProduct.setMeasurementUnitType(unit);
        semiProduct.setSKU(false);
        semiProduct.setBuyable(true);
        semiProduct.setSKUEndCustomer(false);
        em.persist(semiProduct);
        return semiProduct;
    }

    private FacilityType facilityType(String code) {
        FacilityType facilityType = new FacilityType("DELETION_" + code, "Deletion " + code);
        em.persist(facilityType);
        return facilityType;
    }

    private ProcessingEvidenceType evidenceType(String code) {
        ProcessingEvidenceType evidenceType = new ProcessingEvidenceType();
        evidenceType.setCode("DELETION_" + code);
        evidenceType.setLabel("Deletion " + code);
        evidenceType.setType(EvidenceType.DOCUMENT);
        evidenceType.setFairness(false);
        evidenceType.setProvenance(false);
        evidenceType.setQuality(false);
        em.persist(evidenceType);
        return evidenceType;
    }

    private ProcessingEvidenceField evidenceField(String name) {
        ProcessingEvidenceField evidenceField = new ProcessingEvidenceField();
        evidenceField.setFieldName("deletion_" + name.toLowerCase());
        evidenceField.setLabel("Deletion " + name);
        evidenceField.setType(ProcessingEvidenceFieldType.STRING);
        em.persist(evidenceField);
        return evidenceField;
    }

    private ProductType productType(String code) {
        ProductType productType = new ProductType();
        productType.setCode("DELETION_" + code);
        productType.setName("Deletion " + code);
        productType.setDescription("Deletion " + code);
        em.persist(productType);
        return productType;
    }
}
