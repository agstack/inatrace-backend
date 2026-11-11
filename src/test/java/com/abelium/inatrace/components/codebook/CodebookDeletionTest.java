package com.abelium.inatrace.components.codebook;

import com.abelium.inatrace.components.codebook.facility_type.FacilityTypeService;
import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeService;
import com.abelium.inatrace.components.codebook.processing_evidence_type.ProcessingEvidenceTypeService;
import com.abelium.inatrace.components.codebook.processingevidencefield.ProcessingEvidenceFieldService;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductService;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.product.ProductTypeService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceField;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.FarmPlantInformation;
import com.abelium.inatrace.db.entities.common.Plot;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomerProductType;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilitySemiProduct;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionOutputSemiProduct;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionPEF;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionPET;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrderPEFieldValue;
import com.abelium.inatrace.db.entities.stockorder.StockOrderPETypeValue;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChainFacilityType;
import com.abelium.inatrace.db.entities.value_chain.ValueChainMeasureUnitType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /** The references each delete checks, by the table of the codebook they point at. */
    private static final Map<String, List<String>> IN_USE_REFERENCES = Map.of(
            "FacilityType", FacilityTypeService.IN_USE_REFERENCES,
            "MeasureUnitType", MeasureUnitTypeService.IN_USE_REFERENCES,
            "ProcessingEvidenceType", ProcessingEvidenceTypeService.IN_USE_REFERENCES,
            "ProcessingEvidenceField", ProcessingEvidenceFieldService.IN_USE_REFERENCES,
            "SemiProduct", SemiProductService.IN_USE_REFERENCES,
            "ProductType", ProductTypeService.IN_USE_REFERENCES);

    static Stream<String> references() {
        return IN_USE_REFERENCES.values().stream().flatMap(List::stream).sorted();
    }

    /**
     * One reference at a time, so that each check of the delete is exercised on its own: with all of
     * them together the first matching one would answer and hide a missing or wrong later one.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("references")
    void deleteRefusesAnEntryReferencedOnlyBy(String reference) throws Exception {
        Entry entry = referencedOnlyBy(reference);
        em.flush();
        em.clear();

        MvcResult result = mockMvc.perform(entry.request().cookie(session)).andReturn();

        assertEquals(400, result.getResponse().getStatus(), reference + ": " + result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("that is in use"), reference);
        em.clear();
        assertNotNull(em.find(entry.type(), entry.id()), reference + ": the " + entry.name() + " should be kept");
    }

    /**
     * The references each delete checks are exactly the foreign keys to its table: a foreign key added
     * later fails here until the delete checks it (and then the case above needs a fixture for it).
     */
    @Test
    void everyForeignKeyToACodebookIsCovered() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT table_name, column_name, referenced_table_name
                        FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE()
                          AND referenced_table_name IN ('FacilityType', 'MeasureUnitType', 'ProcessingEvidenceType',
                                                        'ProcessingEvidenceField', 'SemiProduct', 'ProductType')
                          AND table_name NOT LIKE '%Translation'""")
                .getResultList();
        Set<String> inSchema = rows.stream()
                .map(row -> row[0] + "." + row[1] + " -> " + row[2])
                .collect(Collectors.toCollection(TreeSet::new));
        // An entity attribute "Entity.attribute" is the column "Entity.attribute_id".
        Set<String> checked = IN_USE_REFERENCES.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(reference -> reference + "_id -> " + e.getKey()))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(inSchema, checked, "the references the deletes check should be the foreign keys to the codebooks");
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
        return List.of(entry(semiProduct), entry(facilityType), entry(unit), entry(evidenceType), entry(evidenceField), entry(productType));
    }

    /** Creates a codebook entry and exactly one reference to it, the given one. */
    private Entry referencedOnlyBy(String reference) {
        String code = reference.substring(0, reference.indexOf('.')).toUpperCase();
        switch (reference) {
            case "Facility.facilityType" -> {
                FacilityType facilityType = facilityType(code);
                Facility facility = new Facility();
                facility.setFacilityType(facilityType);
                em.persist(facility);
                return entry(facilityType);
            }
            case "ValueChainFacilityType.facilityType" -> {
                FacilityType facilityType = facilityType(code);
                em.persist(new ValueChainFacilityType(valueChain(null), facilityType));
                return entry(facilityType);
            }
            case "MeasureUnitType.underlyingMeasurementUnitType" -> {
                MeasureUnitType unit = measureUnitType(code);
                MeasureUnitType derived = measureUnitType(code + "_DERIVED");
                derived.setUnderlyingMeasurementUnitType(unit);
                return entry(unit);
            }
            case "SemiProduct.measurementUnitType" -> {
                MeasureUnitType unit = measureUnitType(code);
                semiProduct(code, unit);
                return entry(unit);
            }
            case "FinalProduct.measurementUnitType" -> {
                MeasureUnitType unit = measureUnitType(code);
                FinalProduct finalProduct = new FinalProduct();
                finalProduct.setName("Deletion " + code);
                finalProduct.setDescription("Deletion " + code);
                finalProduct.setMeasurementUnitType(unit);
                em.persist(finalProduct);
                return entry(unit);
            }
            case "ValueChainMeasureUnitType.measureUnitType" -> {
                MeasureUnitType unit = measureUnitType(code);
                em.persist(new ValueChainMeasureUnitType(valueChain(null), unit));
                return entry(unit);
            }
            case "StockOrder.measurementUnitType" -> {
                MeasureUnitType unit = measureUnitType(code);
                stockOrder().setMeasurementUnitType(unit);
                return entry(unit);
            }
            case "Transaction.inputMeasureUnitType" -> {
                MeasureUnitType unit = measureUnitType(code);
                Transaction transaction = new Transaction();
                transaction.setInputMeasureUnitType(unit);
                em.persist(transaction);
                return entry(unit);
            }
            case "ValueChainProcEvidenceType.processingEvidenceType" -> {
                ProcessingEvidenceType evidenceType = evidenceType(code);
                em.persist(new ValueChainProcEvidenceType(valueChain(null), evidenceType));
                return entry(evidenceType);
            }
            case "ProcessingActionPET.processingEvidenceType" -> {
                ProcessingEvidenceType evidenceType = evidenceType(code);
                ProcessingActionPET link = new ProcessingActionPET();
                link.setProcessingAction(processingAction());
                link.setProcessingEvidenceType(evidenceType);
                em.persist(link);
                return entry(evidenceType);
            }
            case "StockOrderPETypeValue.processingEvidenceType" -> {
                ProcessingEvidenceType evidenceType = evidenceType(code);
                StockOrderPETypeValue value = new StockOrderPETypeValue();
                value.setStockOrder(stockOrder());
                value.setProcessingEvidenceType(evidenceType);
                em.persist(value);
                return entry(evidenceType);
            }
            case "ValueChainProcessingEvidenceField.processingEvidenceField" -> {
                ProcessingEvidenceField evidenceField = evidenceField(code);
                em.persist(new ValueChainProcessingEvidenceField(valueChain(null), evidenceField));
                return entry(evidenceField);
            }
            case "ProcessingActionPEF.processingEvidenceField" -> {
                ProcessingEvidenceField evidenceField = evidenceField(code);
                ProcessingActionPEF link = new ProcessingActionPEF();
                link.setProcessingAction(processingAction());
                link.setProcessingEvidenceField(evidenceField);
                em.persist(link);
                return entry(evidenceField);
            }
            case "StockOrderPEFieldValue.processingEvidenceField" -> {
                ProcessingEvidenceField evidenceField = evidenceField(code);
                StockOrderPEFieldValue value = new StockOrderPEFieldValue();
                value.setStockOrder(stockOrder());
                value.setProcessingEvidenceField(evidenceField);
                em.persist(value);
                return entry(evidenceField);
            }
            case "ValueChainSemiProduct.semiProduct" -> {
                SemiProduct semiProduct = semiProduct(code, null);
                em.persist(new ValueChainSemiProduct(valueChain(null), semiProduct));
                return entry(semiProduct);
            }
            case "FacilitySemiProduct.semiProduct" -> {
                SemiProduct semiProduct = semiProduct(code, null);
                Facility facility = new Facility();
                em.persist(facility);
                em.persist(new FacilitySemiProduct(facility, semiProduct));
                return entry(semiProduct);
            }
            case "ProcessingAction.inputSemiProduct" -> {
                SemiProduct semiProduct = semiProduct(code, null);
                processingAction().setInputSemiProduct(semiProduct);
                return entry(semiProduct);
            }
            case "ProcessingActionOutputSemiProduct.outputSemiProduct" -> {
                SemiProduct semiProduct = semiProduct(code, null);
                ProcessingActionOutputSemiProduct output = new ProcessingActionOutputSemiProduct();
                output.setProcessingAction(processingAction());
                output.setOutputSemiProduct(semiProduct);
                em.persist(output);
                return entry(semiProduct);
            }
            case "StockOrder.semiProduct" -> {
                SemiProduct semiProduct = semiProduct(code, null);
                stockOrder().setSemiProduct(semiProduct);
                return entry(semiProduct);
            }
            case "Transaction.semiProduct" -> {
                SemiProduct semiProduct = semiProduct(code, null);
                Transaction transaction = new Transaction();
                transaction.setSemiProduct(semiProduct);
                em.persist(transaction);
                return entry(semiProduct);
            }
            case "ValueChain.productType" -> {
                ProductType productType = productType(code);
                valueChain(productType);
                return entry(productType);
            }
            case "FarmPlantInformation.productType" -> {
                ProductType productType = productType(code);
                FarmPlantInformation plantInformation = new FarmPlantInformation();
                plantInformation.setProductType(productType);
                em.persist(plantInformation);
                return entry(productType);
            }
            case "UserCustomerProductType.productType" -> {
                ProductType productType = productType(code);
                UserCustomerProductType link = new UserCustomerProductType();
                link.setProductType(productType);
                em.persist(link);
                return entry(productType);
            }
            case "Plot.crop" -> {
                ProductType productType = productType(code);
                Plot plot = new Plot();
                plot.setCrop(productType);
                em.persist(plot);
                return entry(productType);
            }
            default -> throw new IllegalArgumentException("No fixture for " + reference);
        }
    }

    private Entry entry(Object codebookEntry) {
        if (codebookEntry instanceof SemiProduct e) {
            return new Entry("semi-product", SemiProduct.class, e.getId(), delete("/api/chain/semi-product/" + e.getId()));
        }
        if (codebookEntry instanceof FacilityType e) {
            return new Entry("facility type", FacilityType.class, e.getId(), delete("/api/chain/facility-type/" + e.getId()));
        }
        if (codebookEntry instanceof MeasureUnitType e) {
            return new Entry("measure unit type", MeasureUnitType.class, e.getId(), delete("/api/chain/measure-unit-type/" + e.getId()));
        }
        if (codebookEntry instanceof ProcessingEvidenceType e) {
            return new Entry("processing evidence type", ProcessingEvidenceType.class, e.getId(),
                    delete("/api/chain/processing-evidence-type/" + e.getId()));
        }
        if (codebookEntry instanceof ProcessingEvidenceField e) {
            return new Entry("processing evidence field", ProcessingEvidenceField.class, e.getId(),
                    delete("/api/chain/processing-evidence-field/" + e.getId()));
        }
        if (codebookEntry instanceof ProductType e) {
            // The product type route binds the id as a query parameter (see docs/api-auth-coverage/38-codebooks.md).
            return new Entry("product type", ProductType.class, e.getId(),
                    delete("/api/product-type/" + e.getId()).param("id", e.getId().toString()));
        }
        throw new IllegalArgumentException(String.valueOf(codebookEntry));
    }

    /** A value chain of the given product type, or of a product type of its own (a value chain needs one). */
    private ValueChain valueChain(ProductType productType) {
        ValueChain valueChain = new ValueChain();
        valueChain.setName("Codebook deletion");
        valueChain.setDescription("Codebook deletion");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setCreatedBy(admin);
        valueChain.setProductType(productType != null ? productType : productType("CHAIN"));
        em.persist(valueChain);
        return valueChain;
    }

    private StockOrder stockOrder() {
        StockOrder stockOrder = new StockOrder();
        stockOrder.setCreatedBy(admin);
        em.persist(stockOrder);
        return stockOrder;
    }

    private ProcessingAction processingAction() {
        ProcessingAction processingAction = new ProcessingAction();
        em.persist(processingAction);
        return processingAction;
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
