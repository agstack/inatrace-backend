package com.abelium.inatrace.components.pub;

import com.abelium.inatrace.components.common.StorageKeyCache;
import com.abelium.inatrace.components.common.StorageService;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.GlobalSettings;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyCertification;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.facility.FacilityTranslation;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionTranslation;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.*;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.*;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String LABEL_MEDIA = "LABEL-MEDIA-37";
    private static final String BLOG_MEDIA = "BLOG-MEDIA-37";
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private StorageService storageService;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;
    private String publishedUid, draftUid, privateDocumentKey, privateImageKey;
    private Long blogId, publishedLabelId;
    private Cookie ownerSession;

    @BeforeEach void seed() throws Exception {
        Company company = new Company(); company.setName("Public 37 company"); company.setStatus(CompanyStatus.ACTIVE); em.persist(company);
        User user = new User(); user.setEmail("public37@test"); user.setName("Public"); user.setSurname("Test"); user.setPassword("x"); user.setRole(UserRole.USER); user.setStatus(UserStatus.ACTIVE); em.persist(user);
        ProductType type = new ProductType(); type.setCode("PUBLIC37"); type.setName("Public 37"); em.persist(type);
        ValueChain chain = new ValueChain(); chain.setName("Public 37"); chain.setDescription("Public 37"); chain.setValueChainStatus(ValueChainStatus.ENABLED); chain.setProductType(type); chain.setCreatedBy(user); em.persist(chain);
        Product product = new Product(); product.setName(PUBLISHED); product.setDescription(PUBLISHED); product.setCompany(company); product.setValueChain(chain);
        em.persist(product.getProcess()); em.persist(product.getResponsibility()); em.persist(product.getSustainability()); em.persist(product.getJourney()); em.persist(product.getSettings()); em.persist(product.getBusinessToCustomerSettings()); em.persist(product);
        ProductCompany owner = new ProductCompany(); owner.setProduct(product); owner.setCompany(company); owner.setType(ProductCompanyType.OWNER); em.persist(owner);
        CompanyUser membership = new CompanyUser(); membership.setCompany(company); membership.setUser(user); membership.setRole(CompanyUserRole.COMPANY_ADMIN); em.persist(membership); company.getUsers().add(membership);
        ProductLabel published = label(product, PUBLISHED, ProductLabelStatus.PUBLISHED);
        ProductLabel draft = label(product, DRAFT, ProductLabelStatus.UNPUBLISHED);
        Document labelMedia = storageService.uploadDocument(LABEL_MEDIA.getBytes(), "label37.txt", MediaType.TEXT_PLAIN_VALUE, (long) LABEL_MEDIA.length(), DocumentType.GENERAL);
        batch(published, "PUBLIC37").setPhoto(labelMedia); batch(draft, "DRAFT37");
        Document labelImage = storageService.uploadImageVariants(imageBytes(), com.abelium.inatrace.tools.ImageTools.STANDARD_IMAGE_SIZES, MediaType.IMAGE_PNG_VALUE, "label37.png");
        batch(published, "IMAGE37").setPhoto(labelImage);
        ProductLabelFeedback publishedFeedback = new ProductLabelFeedback(); publishedFeedback.setLabel(published); publishedFeedback.setType(ProductLabelFeedbackType.PRAISE); publishedFeedback.setFeedback(PUBLISHED + "-FEEDBACK"); em.persist(publishedFeedback);
        ProductLabelFeedback draftFeedback = new ProductLabelFeedback(); draftFeedback.setLabel(draft); draftFeedback.setType(ProductLabelFeedbackType.PRAISE); draftFeedback.setFeedback("DRAFT-FEEDBACK-37"); em.persist(draftFeedback);
        Document blogMedia = storageService.uploadDocument(BLOG_MEDIA.getBytes(), "blog37.txt", MediaType.TEXT_PLAIN_VALUE, (long) BLOG_MEDIA.length(), DocumentType.GENERAL);
        KnowledgeBlog blog = new KnowledgeBlog(); blog.setProduct(product); blog.setType(KnowledgeBlogType.PROVENANCE); blog.setTitle(DRAFT); blog.setSummary(DRAFT); blog.setContent(DRAFT); blog.getDocuments().add(blogMedia); em.persist(blog); blogId = blog.getId();
        StockOrder stock = new StockOrder(); stock.setOrderId(PUBLISHED); stock.setQrCodeTag("QR-PUBLIC-37"); stock.setCreatedBy(user); stock.setUpdatedBy(user); em.persist(stock);
        GlobalSettings publicSetting = new GlobalSettings(); publicSetting.setName("public37"); publicSetting.setValue(PUBLISHED); publicSetting.setIsPublic(true); em.persist(publicSetting);
        GlobalSettings privateSetting = new GlobalSettings(); privateSetting.setName("private37"); privateSetting.setValue(DRAFT); privateSetting.setIsPublic(false); em.persist(privateSetting);
        Document document = storageService.uploadDocument(PUBLISHED.getBytes(), "public37.txt", MediaType.TEXT_PLAIN_VALUE, (long) PUBLISHED.length(), DocumentType.GENERAL);
        privateDocumentKey = StorageKeyCache.put(document.getStorageKey(), user.getId());
        Document image = storageService.uploadImageVariants(imageBytes(), com.abelium.inatrace.tools.ImageTools.STANDARD_IMAGE_SIZES, MediaType.IMAGE_PNG_VALUE, "public37.png");
        privateImageKey = StorageKeyCache.put(image.getStorageKey(), user.getId());
        publishedUid = published.getUuid(); draftUid = draft.getUuid(); publishedLabelId = published.getId(); em.flush(); em.clear();
        ownerSession = new Cookie(accessCookieName, tokenService.createAccessToken(user));
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

    @Test void publishedLabelDerivedRoutesRemainAvailableAndDraftRoutesAreWithdrawn() throws Exception {
        String publishedProductionDate = productionDate(get("/api/public/product/label_batch/" + publishedUid + "/PUBLIC37"));
        assertContains(get("/api/public/product/label/" + publishedUid + "/verify_batch_authenticity").param("number", "PUBLIC37").param("productionDate", publishedProductionDate), 200, "\"data\":true");
        assertContains(get("/api/public/product/label/" + publishedUid + "/verify_batch_origin").param("number", "PUBLIC37"), 200, "PUBLIC-ORIGIN-37");
        assertContains(get("/api/public/product/label/feedback/list/" + publishedUid), 200, PUBLISHED + "-FEEDBACK");

        assertDenied(get("/api/public/product/label_batch/" + draftUid + "/DRAFT37"), "DRAFT37");
        assertDenied(get("/api/public/product/label/" + draftUid + "/verify_batch_authenticity").param("number", "DRAFT37").param("productionDate", "2026-01-01"), DRAFT);
        assertDenied(get("/api/public/product/label/" + draftUid + "/verify_batch_origin").param("number", "DRAFT37"), "DRAFT-ORIGIN-37");
        assertDenied(get("/api/public/product/label/feedback/list/" + draftUid), "DRAFT-FEEDBACK-37");

        // Knowledge blogs belong to a product, so the blog remains public while that product has
        // its separate published label.
        assertContains(get("/api/public/product/knowledgeBlog/" + blogId), 200, DRAFT);
    }

    @Test void unpublishingTheLastPublishedLabelWithdrawsPublicMediaAndKnowledgeBlogs() throws Exception {
        String labelMediaKey = storageKey(get("/api/public/product/label_batch/" + publishedUid + "/PUBLIC37"));
        String blogMediaKey = storageKey(get("/api/public/product/knowledgeBlog/" + blogId));
        String labelImageKey = storageKey(get("/api/public/product/label_batch/" + publishedUid + "/IMAGE37"));
        assertTextBytes(get("/api/public/document/" + labelMediaKey), LABEL_MEDIA);
        assertTextBytes(get("/api/public/document/" + blogMediaKey), BLOG_MEDIA);
        assertBytes(get("/api/public/image/" + labelImageKey));
        assertBytes(get("/api/public/image/" + labelImageKey + "/SMALL"));
        assertRejected(get("/api/common/document/" + labelMediaKey).cookie(ownerSession));

        assertStatus(post("/api/product/label/execute/UNPUBLISH_LABEL")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + publishedLabelId + "}")
                .cookie(ownerSession), 200);

        assertDenied(get("/api/public/product/label/" + publishedUid), PUBLISHED);
        assertDenied(get("/api/public/product/label_batch/" + publishedUid + "/PUBLIC37"), "PUBLIC37");
        assertDenied(get("/api/public/product/label/" + publishedUid + "/verify_batch_authenticity").param("number", "PUBLIC37").param("productionDate", "2026-01-01"), PUBLISHED);
        assertDenied(get("/api/public/product/label/" + publishedUid + "/verify_batch_origin").param("number", "PUBLIC37"), "PUBLIC-ORIGIN-37");
        assertDenied(get("/api/public/product/label/feedback/list/" + publishedUid), PUBLISHED + "-FEEDBACK");
        assertDenied(get("/api/public/product/knowledgeBlog/" + blogId), DRAFT);
        assertRejected(get("/api/public/document/" + labelMediaKey));
        assertRejected(get("/api/public/document/" + blogMediaKey));
        assertRejected(get("/api/public/image/" + labelImageKey));
        assertRejected(get("/api/public/image/" + labelImageKey + "/SMALL"));
    }

    @Test void qrFilesLoggingAndPublicSettingsWorkWithoutASession() throws Exception {
        assertContains(get("/api/public/stock-order/QR-PUBLIC-37"), 200, "QR-PUBLIC-37");
        assertRefused(get("/api/public/stock-order/unknown-public-37"), PUBLISHED);
        assertRefused(get("/api/public/document/" + privateDocumentKey), PUBLISHED);
        assertRejected(get("/api/public/image/" + privateImageKey));
        assertTextBytes(get("/api/common/document/" + privateDocumentKey).cookie(ownerSession), PUBLISHED);
        assertStatus(get("/api/common/image/" + privateImageKey).cookie(ownerSession), 200);
        assertThrows(IllegalStateException.class, () -> StorageKeyCache.put("unscoped-public-37", null));
        assertRefused(get("/api/public/document/unknown-public-37"), PUBLISHED);
        assertContains(get("/api/public/globalSettings/public37"), 200, PUBLISHED);
        assertRefused(get("/api/public/globalSettings/private37"), DRAFT);
        assertStatus(post("/api/public/logRequest").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"VERIFY_BATCH\",\"token\":\"none\",\"logKey\":\"PUBLIC37\"}"), 200);
        assertStatus(post("/api/public/logRequest").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"VERIFY_BATCH\",\"token\":\"invalid\"}"), 403);
    }

    @Test void publicQrCertificateLinkIsRevokedWhenItsCertificationIsRemoved() throws Exception {
        QrCertificateFixture fixture = qrCertificateFixture();
        String certificateKey = storageKey(get("/api/public/stock-order/" + fixture.qrTag + "?withHistory=true"), "QR-CERTIFICATE-68");

        assertTextBytes(get("/api/public/document/" + certificateKey), "QR-CERTIFICATE-68");
        assertRejected(get("/api/common/document/" + certificateKey).cookie(ownerSession));

        em.remove(em.getReference(CompanyCertification.class, fixture.certificationId));
        em.flush();

        assertRejected(get("/api/public/document/" + certificateKey));
    }

    @Test void publicQrCertificateLinkIsRevokedWhenItsDocumentIsReplaced() throws Exception {
        QrCertificateFixture fixture = qrCertificateFixture();
        String certificateKey = storageKey(get("/api/public/stock-order/" + fixture.qrTag + "?withHistory=true"), "QR-CERTIFICATE-68");
        CompanyCertification certification = em.find(CompanyCertification.class, fixture.certificationId);
        certification.setCertificate(storageService.uploadDocument("QR-CERTIFICATE-68-REPLACEMENT".getBytes(),
                "qr-certificate-68-replacement.txt", MediaType.TEXT_PLAIN_VALUE, 29L, DocumentType.GENERAL));
        em.flush();

        assertRejected(get("/api/public/document/" + certificateKey));
    }

    private ProductLabel label(Product product, String marker, ProductLabelStatus status) {
        ProductLabelContent content = ProductLabelContent.fromProduct(product);
        em.persist(content.getProcess()); em.persist(content.getResponsibility()); em.persist(content.getSustainability()); em.persist(content.getJourney()); em.persist(content.getSettings()); em.persist(content.getBusinessToCustomerSettings()); em.persist(content);
        ProductLabel label = new ProductLabel(); label.setProduct(product); label.setContent(content); label.setTitle(marker); label.setLanguage(Language.EN); label.setStatus(status); em.persist(label); return label;
    }
    private ProductLabelBatch batch(ProductLabel label, String number) {
        ProductLabelBatch batch = new ProductLabelBatch(); batch.setLabel(label); batch.setNumber(number); batch.setProductionDate(LocalDate.of(2026,1,1)); batch.setExpiryDate(LocalDate.of(2027,1,1)); batch.setCheckAuthenticity(true); batch.setTraceOrigin(true); em.persist(batch);
        BatchLocation location = new BatchLocation(); location.setBatch(batch); location.setPinName(number.equals("DRAFT37") ? "DRAFT-ORIGIN-37" : "PUBLIC-ORIGIN-37"); location.setLatitude(-23.5505); location.setLongitude(-46.6333); em.persist(location);
        return batch;
    }
    private QrCertificateFixture qrCertificateFixture() throws Exception {
        String qrTag = "QR-CERTIFICATE-68";
        Company company = new Company(); company.setName("QR certificate company"); company.setStatus(CompanyStatus.ACTIVE); em.persist(company);
        FacilityLocation location = new FacilityLocation(); location.setLatitude(-23.55); location.setLongitude(-46.63); em.persist(location);
        Facility facility = new Facility(); facility.setCompany(company); facility.setFacilityLocation(location); facility.setIsCollectionFacility(false); facility.setIsPublic(true); em.persist(facility);
        FacilityTranslation facilityTranslation = new FacilityTranslation(); facilityTranslation.setFacility(facility); facilityTranslation.setLanguage(Language.EN); facilityTranslation.setName("QR certificate facility"); em.persist(facilityTranslation); facility.getFacilityTranslations().add(facilityTranslation);
        User creator = em.createQuery("select u from User u where u.email = :email", User.class).setParameter("email", "public37@test").getSingleResult();
        ProcessingAction action = new ProcessingAction(); action.setCompany(company); action.setType(ProcessingActionType.PROCESSING); action.setPublicTimelineLabel("QR certificate step"); em.persist(action);
        ProcessingActionTranslation actionTranslation = new ProcessingActionTranslation(); actionTranslation.setProcessingAction(action); actionTranslation.setLanguage(Language.EN); actionTranslation.setName("QR certificate step"); em.persist(actionTranslation); action.getProcessingActionTranslations().add(actionTranslation);
        ProcessingOrder processingOrder = new ProcessingOrder(); processingOrder.setProcessingAction(action); em.persist(processingOrder);
        StockOrder output = new StockOrder(); output.setCreatedBy(creator); output.setUpdatedBy(creator); output.setFacility(facility); output.setProcessingOrder(processingOrder); output.setQrCodeTag(qrTag); output.setOrderId(qrTag); output.setBalance(BigDecimal.ONE); em.persist(output); processingOrder.getTargetStockOrders().add(output);
        StockOrder input = new StockOrder(); input.setCreatedBy(creator); input.setUpdatedBy(creator); input.setFacility(facility); input.setOrderId("QR-CERTIFICATE-68-INPUT"); input.setPurchaseOrder(true); input.setBalance(BigDecimal.ONE); em.persist(input);
        Transaction transaction = new Transaction(); transaction.setSourceStockOrder(input); transaction.setTargetProcessingOrder(processingOrder); transaction.setStatus(TransactionStatus.EXECUTED); em.persist(transaction); processingOrder.getInputTransactions().add(transaction);
        Document certificateDocument = storageService.uploadDocument("QR-CERTIFICATE-68".getBytes(), "qr-certificate-68.txt", MediaType.TEXT_PLAIN_VALUE, 17L, DocumentType.GENERAL);
        CompanyCertification certification = new CompanyCertification(); certification.setCompany(company); certification.setCertificate(certificateDocument); certification.setType("CERTIFICATE-68"); certification.setDescription("QR-CERTIFICATE-68"); em.persist(certification);
        em.flush(); em.clear();
        return new QrCertificateFixture(qrTag, certification.getId());
    }
    private byte[] imageBytes() throws Exception { BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB); image.setRGB(0, 0, 0x008037); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray(); }
    private void assertStatus(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(status,result.getResponse().getStatus(),result.getResponse().getContentAsString()); }
    private void assertContains(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(status,result.getResponse().getStatus(),result.getResponse().getContentAsString()); assertTrue(result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); }
    private void assertBytes(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString()); assertTrue(result.getResponse().getContentAsByteArray().length>0); }
    private void assertTextBytes(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString()); assertEquals(marker, new String(result.getResponse().getContentAsByteArray())); }
    private void assertRefused(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertTrue(result.getResponse().getStatus()>=400 || !result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); assertFalse(result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); }
    private void assertDenied(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertTrue(result.getResponse().getStatus()>=400,result.getResponse().getContentAsString()); assertFalse(result.getResponse().getContentAsString().contains(marker),result.getResponse().getContentAsString()); }
    private void assertRejected(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); assertTrue(result.getResponse().getStatus()>=400,result.getResponse().getContentAsString()); }
    private String storageKey(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception { return storageKey(request, null); }
    private String storageKey(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String marker) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); String body=result.getResponse().getContentAsString(); assertEquals(200,result.getResponse().getStatus(),body); if (marker != null) assertTrue(body.contains(marker),body); Matcher matcher=Pattern.compile("\\\"storageKey\\\":\\\"([^\\\"]+)\\\"").matcher(body); assertTrue(matcher.find(),body); return matcher.group(1); }
    private String productionDate(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception { MvcResult result=mockMvc.perform(request).andReturn(); String body=result.getResponse().getContentAsString(); assertEquals(200,result.getResponse().getStatus(),body); assertTrue(body.contains("PUBLIC37"),body); Matcher matcher=Pattern.compile("\\\"productionDate\\\":\\\"([^\\\"]+)\\\"").matcher(body); assertTrue(matcher.find(),body); return matcher.group(1); }
    private long count(Class<?> type) { return em.createQuery("select count(x) from " + type.getSimpleName() + " x",Long.class).getSingleResult(); }
    private record QrCertificateFixture(String qrTag, Long certificationId) { }
}
