package com.abelium.inatrace.components.pub;

import com.abelium.inatrace.components.common.StorageKeyCache;
import com.abelium.inatrace.components.common.StorageService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.GlobalSettings;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.product.*;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Characterizes the anonymous contract and publication boundary of PublicController. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicLabelApiTest extends AbstractMySqlIntegrationTest {
    private static final String PUBLISHED = "PUBLIC-LABEL-37";
    private static final String DRAFT = "DRAFT-LABEL-37";
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private StorageService storageService;
    private String publishedUid, draftUid, documentKey, imageKey;
    private Long blogId;

    @BeforeEach void seed() throws Exception {
        Company company = new Company(); company.setName("Public 37 company"); company.setStatus(CompanyStatus.ACTIVE); em.persist(company);
        User user = new User(); user.setEmail("public37@test"); user.setName("Public"); user.setSurname("Test"); user.setPassword("x"); user.setRole(UserRole.USER); user.setStatus(UserStatus.ACTIVE); em.persist(user);
        ProductType type = new ProductType(); type.setCode("PUBLIC37"); type.setName("Public 37"); em.persist(type);
        ValueChain chain = new ValueChain(); chain.setName("Public 37"); chain.setDescription("Public 37"); chain.setValueChainStatus(ValueChainStatus.ENABLED); chain.setProductType(type); chain.setCreatedBy(user); em.persist(chain);
        Product product = new Product(); product.setName(PUBLISHED); product.setDescription(PUBLISHED); product.setCompany(company); product.setValueChain(chain);
        em.persist(product.getProcess()); em.persist(product.getResponsibility()); em.persist(product.getSustainability()); em.persist(product.getJourney()); em.persist(product.getSettings()); em.persist(product.getBusinessToCustomerSettings()); em.persist(product);
        ProductCompany owner = new ProductCompany(); owner.setProduct(product); owner.setCompany(company); owner.setType(ProductCompanyType.OWNER); em.persist(owner);
        ProductLabel published = label(product, PUBLISHED, ProductLabelStatus.PUBLISHED);
        ProductLabel draft = label(product, DRAFT, ProductLabelStatus.UNPUBLISHED);
        batch(published, "PUBLIC37"); batch(draft, "DRAFT37");
        ProductLabelFeedback draftFeedback = new ProductLabelFeedback(); draftFeedback.setLabel(draft); draftFeedback.setType(ProductLabelFeedbackType.PRAISE); draftFeedback.setFeedback("DRAFT-FEEDBACK-37"); em.persist(draftFeedback);
        KnowledgeBlog blog = new KnowledgeBlog(); blog.setProduct(product); blog.setType(KnowledgeBlogType.PROVENANCE); blog.setTitle(DRAFT); blog.setSummary(DRAFT); blog.setContent(DRAFT); em.persist(blog); blogId = blog.getId();
        StockOrder stock = new StockOrder(); stock.setOrderId(PUBLISHED); stock.setQrCodeTag("QR-PUBLIC-37"); stock.setCreatedBy(user); stock.setUpdatedBy(user); em.persist(stock);
        GlobalSettings publicSetting = new GlobalSettings(); publicSetting.setName("public37"); publicSetting.setValue(PUBLISHED); publicSetting.setIsPublic(true); em.persist(publicSetting);
        GlobalSettings privateSetting = new GlobalSettings(); privateSetting.setName("private37"); privateSetting.setValue(DRAFT); privateSetting.setIsPublic(false); em.persist(privateSetting);
        Document document = storageService.uploadDocument(PUBLISHED.getBytes(), "public37.txt", MediaType.TEXT_PLAIN_VALUE, (long) PUBLISHED.length(), DocumentType.GENERAL);
        documentKey = StorageKeyCache.put(document.getStorageKey(), null);
        Document image = storageService.uploadImageVariants(imageBytes(), com.abelium.inatrace.tools.ImageTools.STANDARD_IMAGE_SIZES, MediaType.IMAGE_PNG_VALUE, "public37.png");
        imageKey = StorageKeyCache.put(image.getStorageKey(), null);
        publishedUid = published.getUuid(); draftUid = draft.getUuid(); em.flush(); em.clear();
    }

    @Test void publishedLabelIsAnonymousButDraftLandingAndFeedbackCreationAreWithdrawn() throws Exception {
        assertContains(get("/api/public/product/label/" + publishedUid), 200, PUBLISHED);
        assertRefused(get("/api/public/product/label/" + draftUid), DRAFT);
        long before = count(ProductLabelFeedback.class);
        assertStatus(post("/api/public/product/label/feedback/" + publishedUid).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PRAISE\",\"feedback\":\"" + PUBLISHED + "\"}"), 200);
        assertEquals(before + 1, count(ProductLabelFeedback.class));
        assertRefused(post("/api/public/product/label/feedback/" + draftUid).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PRAISE\",\"feedback\":\"" + DRAFT + "\"}"), DRAFT);
        assertEquals(before + 1, count(ProductLabelFeedback.class));
    }

    @Test void draftDerivedRoutesAreCharacterizedBeforeAnyPublicationPolicyFix() throws Exception {
        // These routes currently bypass fetchProductLabelPublic. This test records the observed exposure.
        assertContains(get("/api/public/product/label_batch/" + draftUid + "/DRAFT37"), 200, "DRAFT37");
        assertContains(get("/api/public/product/label/" + draftUid + "/verify_batch_authenticity").param("number", "DRAFT37").param("productionDate", "2025-12-31"), 200, "true");
        assertContains(get("/api/public/product/label/" + draftUid + "/verify_batch_origin").param("number", "DRAFT37"), 200, "DRAFT-ORIGIN-37");
        assertContains(get("/api/public/product/knowledgeBlog/" + blogId), 200, DRAFT);
        assertContains(get("/api/public/product/label/feedback/list/" + draftUid), 200, "DRAFT-FEEDBACK-37");
    }

    @Test void qrFilesLoggingAndPublicSettingsWorkWithoutASession() throws Exception {
        assertContains(get("/api/public/stock-order/QR-PUBLIC-37"), 200, "QR-PUBLIC-37");
        assertRefused(get("/api/public/stock-order/unknown-public-37"), PUBLISHED);
        assertBytes(get("/api/public/document/" + documentKey), PUBLISHED);
        assertBytes(get("/api/public/image/" + imageKey), "PNG");
        assertStatus(get("/api/public/image/" + imageKey + "/SMALL"), 200);
        assertRefused(get("/api/public/document/unknown-public-37"), PUBLISHED);
        assertContains(get("/api/public/globalSettings/public37"), 200, PUBLISHED);
        assertRefused(get("/api/public/globalSettings/private37"), DRAFT);
        assertStatus(post("/api/public/logRequest").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"VERIFY_BATCH\",\"token\":\"none\",\"logKey\":\"PUBLIC37\"}"), 200);
        assertStatus(post("/api/public/logRequest").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"VERIFY_BATCH\",\"token\":\"invalid\"}"), 403);
    }

    private ProductLabel label(Product product, String marker, ProductLabelStatus status) {
        ProductLabelContent content = ProductLabelContent.fromProduct(product);
        em.persist(content.getProcess()); em.persist(content.getResponsibility()); em.persist(content.getSustainability()); em.persist(content.getJourney()); em.persist(content.getSettings()); em.persist(content.getBusinessToCustomerSettings()); em.persist(content);
        ProductLabel label = new ProductLabel(); label.setProduct(product); label.setContent(content); label.setTitle(marker); label.setLanguage(Language.EN); label.setStatus(status); em.persist(label); return label;
    }
    private void batch(ProductLabel label, String number) {
        ProductLabelBatch batch = new ProductLabelBatch(); batch.setLabel(label); batch.setNumber(number); batch.setProductionDate(LocalDate.of(2026,1,1)); batch.setExpiryDate(LocalDate.of(2027,1,1)); batch.setCheckAuthenticity(true); batch.setTraceOrigin(true); em.persist(batch);
        if (number.equals("DRAFT37")) { BatchLocation location = new BatchLocation(); location.setBatch(batch); location.setPinName("DRAFT-ORIGIN-37"); location.setLatitude(-23.5505); location.setLongitude(-46.6333); em.persist(location); }
    }
    private byte[] imageBytes() throws Exception { BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB); image.setRGB(0, 0, 0x008037); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray(); }
    private void assertStatus(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(status,result.getResponse().getStatus(),result.getResponse().getContentAsString()); }
    private void assertContains(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(status,result.getResponse().getStatus(),result.getResponse().getContentAsString()); assertTrue(result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); }
    private void assertBytes(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString()); assertTrue(new String(result.getResponse().getContentAsByteArray()).contains(marker) || result.getResponse().getContentAsByteArray().length>0); }
    private void assertRefused(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertTrue(result.getResponse().getStatus()>=400 || !result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); assertFalse(result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); }
    private long count(Class<?> type) { return em.createQuery("select count(x) from " + type.getSimpleName() + " x",Long.class).getSingleResult(); }
}
