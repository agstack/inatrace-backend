package com.abelium.inatrace.components.product;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.product.types.ProductLabelAction;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.product.KnowledgeBlog;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.product.ProductLabel;
import com.abelium.inatrace.db.entities.product.ProductLabelBatch;
import com.abelium.inatrace.db.entities.product.ProductLabelContent;
import com.abelium.inatrace.db.entities.product.ProductLabelFeedback;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.KnowledgeBlogType;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.ProductLabelFeedbackType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Authorization inventory for every authenticated mapping in ProductController and
 * FinalProductController. The fixture deliberately distinguishes the product owner,
 * an associated company and a completely unrelated tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductAndLabelApiTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "OWNER-PRODUCT-36-PRIVATE";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;

    private Long ownerCompanyId;
    private Long valueChainId;
    private Long productId;
    private Long labelId;
    private Long batchId;
    private Long blogId;
    private Long feedbackId;
    private Long finalProductId;
    private Long measureUnitTypeId;
    private Cookie ownerSession;
    private Cookie associateSession;
    private Cookie strangerSession;
    private Cookie systemAdminSession;

    @BeforeEach
    void seed() {
        Company owner = company("Owner Products 36");
        Company associate = company("Associate Products 36");
        Company stranger = company("Stranger Products 36");
        User ownerUser = user("owner-products-36@test", UserRole.USER);
        User associateUser = user("associate-products-36@test", UserRole.USER);
        User strangerUser = user("stranger-products-36@test", UserRole.USER);
        User systemAdmin = user("admin-products-36@test", UserRole.SYSTEM_ADMIN);
        enroll(ownerUser, owner);
        enroll(associateUser, associate);
        enroll(strangerUser, stranger);

        ProductType type = new ProductType();
        type.setCode("PRODUCT_AUTH_36");
        type.setName("Product authorization type");
        em.persist(type);
        ValueChain chain = new ValueChain();
        chain.setName("Product authorization chain");
        chain.setDescription("Product authorization chain");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(type);
        chain.setCreatedBy(ownerUser);
        em.persist(chain);
        Product product = new Product();
        product.setName(MARKER);
        product.setDescription(MARKER);
        product.setCompany(owner);
        product.setValueChain(chain);
        em.persist(product.getProcess());
        em.persist(product.getResponsibility());
        em.persist(product.getSustainability());
        em.persist(product.getJourney());
        em.persist(product.getSettings());
        em.persist(product.getBusinessToCustomerSettings());
        em.persist(product);
        association(product, owner, ProductCompanyType.OWNER);
        association(product, associate, ProductCompanyType.BUYER);

        ProductLabel label = new ProductLabel();
        label.setProduct(product);
        label.setTitle(MARKER + " LABEL");
        label.setLanguage(Language.EN);
        ProductLabelContent content = ProductLabelContent.fromProduct(product);
        em.persist(content.getProcess());
        em.persist(content.getResponsibility());
        em.persist(content.getSustainability());
        em.persist(content.getJourney());
        em.persist(content.getSettings());
        em.persist(content.getBusinessToCustomerSettings());
        em.persist(content);
        label.setContent(content);
        em.persist(label);
        ProductLabelBatch batch = new ProductLabelBatch();
        batch.setLabel(label);
        batch.setNumber("BATCH36");
        batch.setProductionDate(LocalDate.of(2026, 1, 1));
        batch.setExpiryDate(LocalDate.of(2027, 1, 1));
        em.persist(batch);
        KnowledgeBlog blog = new KnowledgeBlog();
        blog.setProduct(product);
        blog.setType(KnowledgeBlogType.PROVENANCE);
        blog.setTitle(MARKER + " BLOG");
        blog.setSummary(MARKER);
        blog.setContent(MARKER);
        em.persist(blog);
        ProductLabelFeedback feedback = new ProductLabelFeedback();
        feedback.setLabel(label);
        feedback.setType(ProductLabelFeedbackType.PRAISE);
        feedback.setFeedback(MARKER);
        em.persist(feedback);
        MeasureUnitType unit = new MeasureUnitType("PRODUCT_AUTH_36_KG", "Product auth kilogram", BigDecimal.ONE);
        em.persist(unit);
        FinalProduct finalProduct = new FinalProduct();
        finalProduct.setProduct(product);
        finalProduct.setName(MARKER + " FINAL");
        finalProduct.setDescription(MARKER);
        finalProduct.setMeasurementUnitType(unit);
        em.persist(finalProduct);

        ownerCompanyId = owner.getId();
        valueChainId = chain.getId();
        productId = product.getId();
        labelId = label.getId();
        batchId = batch.getId();
        blogId = blog.getId();
        feedbackId = feedback.getId();
        finalProductId = finalProduct.getId();
        measureUnitTypeId = unit.getId();
        ownerSession = session(ownerUser);
        associateSession = session(associateUser);
        strangerSession = session(strangerUser);
        systemAdminSession = session(systemAdmin);
        em.flush();
        em.clear();
    }

    @Test
    void associatedCompanyCanReadAssociatedProductAndLabelRoutesWhileStrangerAndAnonymousCannot() throws Exception {
        List<String> associatedRoutes = List.of(
                "/api/product/" + productId,
                "/api/product/" + productId + "/labels",
                "/api/product/label/" + labelId,
                "/api/product/label/content/" + labelId,
                "/api/product/label/" + labelId + "/documents",
                "/api/product/label/analytics/" + em.find(ProductLabel.class, labelId).getUuid(),
                "/api/product/label_batch/" + batchId,
                "/api/product/knowledgeBlog/list/" + productId,
                "/api/product/knowledgeBlog/" + blogId,
                "/api/product/" + productId + "/finalProduct/" + finalProductId,
                "/api/product/" + productId + "/finalProduct/" + finalProductId + "/labels",
                "/api/product/" + productId + "/finalProduct/list"
        );
        for (String route : associatedRoutes) {
            assertStatus(get(route).cookie(associateSession), 200);
            assertRefused(get(route).cookie(strangerSession), 403);
            assertRefused(get(route), 401);
        }
    }

    @Test
    void productListsAndFinalProductCompanyListRemainBoundToTheirUserOrCompany() throws Exception {
        assertContains(get("/api/product/list").cookie(ownerSession), 200, MARKER);
        assertRefused(get("/api/product/list"), 401);
        assertRefused(get("/api/final-product/company/" + ownerCompanyId).cookie(strangerSession), 403);
        assertRefused(get("/api/final-product/company/" + ownerCompanyId), 401);
        assertContains(get("/api/final-product/company/" + ownerCompanyId).cookie(ownerSession), 200, MARKER);
        assertContains(get("/api/product/admin/list").cookie(systemAdminSession), 200, MARKER);
        assertRefused(get("/api/product/admin/list").cookie(ownerSession), 403);
        assertRefused(get("/api/product/admin/list"), 401);
    }

    @Test
    void ownerOnlyProductBatchAndFinalProductReadsRejectAssociateAndStranger() throws Exception {
        String batches = "/api/product/label/" + labelId + "/batches";
        assertContains(get(batches).cookie(ownerSession), 200, "BATCH36");
        assertRefused(get(batches).cookie(associateSession), 403);
        assertRefused(get(batches).cookie(strangerSession), 403);
        assertRefused(get(batches), 401);

        // Instructions are generated from files; authorization must happen before generation.
        String instructions = "/api/product/label/" + labelId + "/instructions";
        assertRefused(get(instructions).cookie(strangerSession), 403);
        assertRefused(get(instructions), 401);
    }

    @Test
    void ownerOnlyProductWritesDoNotMutateForeignProducts() throws Exception {
        long before = count(Product.class);
        assertRefused(post("/api/product/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":{\"id\":" + ownerCompanyId + "},\"name\":\"FOREIGN-CREATE-36\"}")
                .cookie(strangerSession), 403);
        assertEquals(before, count(Product.class));
        assertRefused(post("/api/product/create").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), 401);

        String update = "{\"id\":" + productId + ",\"name\":\"FOREIGN-UPDATE-36\"}";
        assertRefused(put("/api/product/").contentType(MediaType.APPLICATION_JSON).content(update)
                .cookie(strangerSession), 403);
        assertRefused(put("/api/product/").contentType(MediaType.APPLICATION_JSON).content(update), 401);
        assertProductMarker();

        assertRefused(delete("/api/product/" + productId).cookie(associateSession), 403);
        assertRefused(delete("/api/product/" + productId).cookie(strangerSession), 403);
        assertRefused(delete("/api/product/" + productId), 401);
        assertTrue(em.find(Product.class, productId) != null);
    }

    @Test
    void associatedLabelWritesRejectStrangerAndLeaveRowsUntouched() throws Exception {
        long labelsBefore = count(ProductLabel.class);
        assertRefused(post("/api/product/label/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"language\":\"EN\"}").cookie(strangerSession), 403);
        assertEquals(labelsBefore, count(ProductLabel.class));
        assertRefused(post("/api/product/label/create").contentType(MediaType.APPLICATION_JSON).content("{}"), 401);

        String labelUpdate = "{\"id\":" + labelId + ",\"title\":\"FOREIGN-LABEL-UPDATE-36\"}";
        assertRefused(put("/api/product/label").contentType(MediaType.APPLICATION_JSON).content(labelUpdate)
                .cookie(strangerSession), 403);
        assertRefused(put("/api/product/label").contentType(MediaType.APPLICATION_JSON).content(labelUpdate), 401);
        assertLabelMarker();

        assertRefused(put("/api/product/label/content").contentType(MediaType.APPLICATION_JSON)
                .content("{\"labelId\":" + labelId + ",\"name\":\"FOREIGN-CONTENT-36\"}").cookie(strangerSession), 403);
        assertRefused(put("/api/product/label/content").contentType(MediaType.APPLICATION_JSON).content("{}"), 401);

        assertRefused(put("/api/product/label/" + labelId + "/documents").contentType(MediaType.APPLICATION_JSON)
                .content("[]").cookie(strangerSession), 403);
        assertRefused(put("/api/product/label/" + labelId + "/documents").contentType(MediaType.APPLICATION_JSON)
                .content("[]"), 401);

        assertRefused(post("/api/product/label/execute/" + ProductLabelAction.PUBLISH_LABEL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + labelId + "}").cookie(strangerSession), 403);
        assertRefused(post("/api/product/label/execute/" + ProductLabelAction.PUBLISH_LABEL).contentType(MediaType.APPLICATION_JSON)
                .content("{}"), 401);

        assertRefused(delete("/api/product/label/" + labelId).cookie(strangerSession), 403);
        assertRefused(delete("/api/product/label/" + labelId), 401);
        assertTrue(em.find(ProductLabel.class, labelId) != null);
    }

    @Test
    void associatedBatchAndKnowledgeBlogWritesRejectStrangerWithoutPartialWrites() throws Exception {
        long batchesBefore = count(ProductLabelBatch.class);
        assertRefused(post("/api/product/label_batch/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"labelId\":" + labelId + ",\"number\":\"FOREIGNBATCH36\"}").cookie(strangerSession), 403);
        assertEquals(batchesBefore, count(ProductLabelBatch.class));
        assertRefused(post("/api/product/label_batch/create").contentType(MediaType.APPLICATION_JSON).content("{}"), 401);

        assertRefused(put("/api/product/label_batch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + batchId + ",\"number\":\"FOREIGNBATCH36\"}").cookie(strangerSession), 403);
        assertRefused(put("/api/product/label_batch").contentType(MediaType.APPLICATION_JSON).content("{}"), 401);
        assertEquals("BATCH36", em.find(ProductLabelBatch.class, batchId).getNumber());
        assertRefused(delete("/api/product/label_batch/" + batchId).cookie(strangerSession), 403);
        assertRefused(delete("/api/product/label_batch/" + batchId), 401);
        assertTrue(em.find(ProductLabelBatch.class, batchId) != null);

        assertRefused(put("/api/product/knowledgeBlog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + blogId + ",\"title\":\"FOREIGN-BLOG-36\"}").cookie(strangerSession), 403);
        assertRefused(put("/api/product/knowledgeBlog").contentType(MediaType.APPLICATION_JSON).content("{}"), 401);
        assertRefused(post("/api/product/knowledgeBlog/" + productId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"FOREIGN-BLOG-36\"}").cookie(strangerSession), 403);
        assertRefused(post("/api/product/knowledgeBlog/" + productId).contentType(MediaType.APPLICATION_JSON).content("{}"), 401);
        assertContains(get("/api/product/knowledgeBlog/" + blogId).cookie(ownerSession), 200, MARKER);
    }

    @Test
    void feedbackAndOwnerOnlyFinalProductWritesRejectForeignUsersWithoutDeletion() throws Exception {
        assertRefused(delete("/api/product/label/feedback/" + feedbackId).cookie(strangerSession), 403);
        assertRefused(delete("/api/product/label/feedback/" + feedbackId), 401);
        assertTrue(em.find(ProductLabelFeedback.class, feedbackId) != null);

        String finalProduct = "{\"id\":" + finalProductId + ",\"name\":\"FOREIGN-FINAL-36\",\"description\":\"x\","
                + "\"measurementUnitType\":{\"id\":" + measureUnitTypeId + "},\"labels\":[]}";
        assertRefused(put("/api/product/" + productId + "/finalProduct").contentType(MediaType.APPLICATION_JSON)
                .content(finalProduct).cookie(associateSession), 403);
        assertRefused(put("/api/product/" + productId + "/finalProduct").contentType(MediaType.APPLICATION_JSON)
                .content(finalProduct).cookie(strangerSession), 403);
        assertRefused(put("/api/product/" + productId + "/finalProduct").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), 401);
        assertContains(get("/api/product/" + productId + "/finalProduct/" + finalProductId).cookie(ownerSession), 200, MARKER);

        assertRefused(delete("/api/product/" + productId + "/finalProduct/" + finalProductId).cookie(associateSession), 403);
        assertRefused(delete("/api/product/" + productId + "/finalProduct/" + finalProductId).cookie(strangerSession), 403);
        assertRefused(delete("/api/product/" + productId + "/finalProduct/" + finalProductId), 401);
        assertTrue(em.find(FinalProduct.class, finalProductId) != null);
    }

    @Test
    void entitledUsersCanCompleteEveryProductAndLabelWriteRoute() throws Exception {
        long productsBefore = count(Product.class);
        assertStatus(post("/api/product/create").contentType(MediaType.APPLICATION_JSON).cookie(ownerSession)
                .content("{\"company\":{\"id\":" + ownerCompanyId + "},\"valueChain\":{\"id\":" + valueChainId
                        + "},\"name\":\"OWNER-CREATE-36\",\"dataSharingAgreements\":[]}"), 200);
        assertEquals(productsBefore + 1, count(Product.class));

        assertStatus(put("/api/product/").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"id\":" + productId + ",\"name\":\"ASSOCIATE-UPDATE-36\",\"dataSharingAgreements\":[]}"), 200);

        long labelsBefore = count(ProductLabel.class);
        assertStatus(post("/api/product/label/create").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"productId\":" + productId + ",\"language\":\"EN\",\"title\":\"ASSOCIATE-LABEL-36\"}"), 200);
        assertEquals(labelsBefore + 1, count(ProductLabel.class));
        assertStatus(put("/api/product/label").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"id\":" + labelId + ",\"title\":\"ASSOCIATE-LABEL-UPDATE-36\"}"), 200);
        assertStatus(put("/api/product/label/content").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"labelId\":" + labelId + ",\"name\":\"ASSOCIATE-CONTENT-36\"}"), 200);
        assertStatus(put("/api/product/label/" + labelId + "/documents").contentType(MediaType.APPLICATION_JSON)
                .cookie(associateSession).content("[]"), 200);
        assertStatus(post("/api/product/label/execute/" + ProductLabelAction.PUBLISH_LABEL).contentType(MediaType.APPLICATION_JSON)
                .cookie(associateSession).content("{\"id\":" + labelId + "}"), 200);

        long batchesBefore = count(ProductLabelBatch.class);
        assertStatus(post("/api/product/label_batch/create").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"labelId\":" + labelId + ",\"number\":\"ASSOCIATEBATCH36\"}"), 200);
        assertEquals(batchesBefore + 1, count(ProductLabelBatch.class));
        assertStatus(put("/api/product/label_batch").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"id\":" + batchId + ",\"number\":\"ASSOCIATEUPDATE36\"}"), 200);

        assertStatus(put("/api/product/knowledgeBlog").contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"id\":" + blogId + ",\"title\":\"ASSOCIATE-BLOG-36\"}"), 200);
        long blogsBefore = count(KnowledgeBlog.class);
        assertStatus(post("/api/product/knowledgeBlog/" + productId).contentType(MediaType.APPLICATION_JSON).cookie(associateSession)
                .content("{\"type\":\"QUALITY\",\"title\":\"ASSOCIATE-NEW-BLOG-36\"}"), 200);
        assertEquals(blogsBefore + 1, count(KnowledgeBlog.class));

        String finalProduct = "{\"id\":" + finalProductId + ",\"name\":\"OWNER-FINAL-UPDATE-36\",\"description\":\"owner\","
                + "\"measurementUnitType\":{\"id\":" + measureUnitTypeId + "},\"labels\":[]}";
        assertStatus(put("/api/product/" + productId + "/finalProduct").contentType(MediaType.APPLICATION_JSON)
                .cookie(ownerSession).content(finalProduct), 200);

        assertStatus(delete("/api/product/label/feedback/" + feedbackId).cookie(associateSession), 200);
        assertTrue(em.find(ProductLabelFeedback.class, feedbackId) == null);
        assertStatus(delete("/api/product/label_batch/" + batchId).cookie(associateSession), 200);
        assertTrue(em.find(ProductLabelBatch.class, batchId) == null);
        assertStatus(delete("/api/product/label/" + labelId).cookie(associateSession), 200);
        assertTrue(em.find(ProductLabel.class, labelId) == null);
        assertStatus(delete("/api/product/" + productId + "/finalProduct/" + finalProductId).cookie(ownerSession), 200);
        assertTrue(em.find(FinalProduct.class, finalProductId) == null);
    }

    private void assertStatus(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private void assertRefused(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertFalse(result.getResponse().getContentAsString().contains(MARKER),
                "A refused response leaked the owner marker: " + result.getResponse().getContentAsString());
    }

    private void assertContains(MockHttpServletRequestBuilder request, int expected, String marker) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains(marker), result.getResponse().getContentAsString());
    }

    private void assertProductMarker() {
        em.clear();
        assertEquals(MARKER, em.find(Product.class, productId).getName());
    }

    private void assertLabelMarker() {
        em.clear();
        assertEquals(MARKER + " LABEL", em.find(ProductLabel.class, labelId).getTitle());
    }

    private long count(Class<?> entity) {
        return em.createQuery("SELECT count(e) FROM " + entity.getSimpleName() + " e", Long.class).getSingleResult();
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
        user.setName("Product");
        user.setSurname("Authorization");
        user.setPassword("not-used");
        user.setRole(role);
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

    private void association(Product product, Company company, ProductCompanyType type) {
        ProductCompany association = new ProductCompany();
        association.setProduct(product);
        association.setCompany(company);
        association.setType(type);
        em.persist(association);
        product.getAssociatedCompanies().add(association);
    }

    private Cookie session(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }
}
