package com.abelium.inatrace.components.product;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.process.Process;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.product.KnowledgeBlog;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.product.ProductLabel;
import com.abelium.inatrace.db.entities.product.ProductLabelBatch;
import com.abelium.inatrace.db.entities.product.ProductLabelContent;
import com.abelium.inatrace.db.entities.product.ProductLabelFeedback;
import com.abelium.inatrace.db.entities.product.ProductSettings;
import com.abelium.inatrace.db.entities.product.Responsibility;
import com.abelium.inatrace.db.entities.product.Sustainability;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.KnowledgeBlogType;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.ProductLabelFeedbackType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.List;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a product removes what belongs to it (labels with their batches, feedback, links and
 * content tree; knowledge blogs) and refuses business data of its own (final products, customers).
 * A product's knowledge blogs can also be deleted one by one. Before, a product with a blog, label
 * feedback or a final product failed on a foreign key, and deleting labels left their content tree
 * behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductDeletionTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;

    private Long productId;
    private Long blogId;
    private Long documentId;
    private Cookie ownerSession;

    @BeforeEach
    void seed() {
        Company owner = new Company();
        owner.setName("Knowledge blog owner");
        owner.setStatus(CompanyStatus.ACTIVE);
        em.persist(owner);
        User user = new User();
        user.setEmail("knowledge-blog-owner@test");
        user.setName("Knowledge");
        user.setSurname("Blog");
        user.setPassword("not-used");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(owner);
        enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(enrollment);
        owner.getUsers().add(enrollment);

        ProductType type = new ProductType();
        type.setCode("KNOWLEDGE_BLOG");
        type.setName("Knowledge blog type");
        em.persist(type);
        ValueChain chain = new ValueChain();
        chain.setName("Knowledge blog chain");
        chain.setDescription("Knowledge blog chain");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(type);
        chain.setCreatedBy(user);
        em.persist(chain);

        Product product = new Product();
        product.setName("Knowledge blog product");
        product.setCompany(owner);
        product.setValueChain(chain);
        product.getResponsibility().setLaborPolicies("Fair pay within 15 days");
        product.getProcess().setProduction("Washed");
        em.persist(product.getProcess());
        em.persist(product.getResponsibility());
        em.persist(product.getSustainability());
        em.persist(product.getJourney());
        em.persist(product.getSettings());
        em.persist(product.getBusinessToCustomerSettings());
        em.persist(product);
        ProductCompany association = new ProductCompany();
        association.setProduct(product);
        association.setCompany(owner);
        association.setType(ProductCompanyType.OWNER);
        em.persist(association);
        product.getAssociatedCompanies().add(association);

        Document document = new Document();
        document.setStorageKey("knowledge-blog-document");
        document.setName("tasting.pdf");
        document.setContentType("application/pdf");
        em.persist(document);
        KnowledgeBlog blog = new KnowledgeBlog();
        blog.setProduct(product);
        blog.setType(KnowledgeBlogType.QUALITY);
        blog.setTitle("Tasting notes");
        blog.getDocuments().add(document);
        em.persist(blog);

        productId = product.getId();
        blogId = blog.getId();
        documentId = document.getId();
        ownerSession = new Cookie(accessCookieName, tokenService.createAccessToken(user));
        em.flush();
        em.clear();
    }

    @Test
    void ownerDeletesAKnowledgeBlog() throws Exception {
        perform("/api/product/knowledgeBlog/" + blogId);

        assertNull(em.find(KnowledgeBlog.class, blogId));
        assertNotNull(em.find(Product.class, productId));
        // The document is kept, as when a blog update replaces its documents.
        assertNotNull(em.find(Document.class, documentId));
    }

    @Test
    void deletingAProductDeletesItsKnowledgeBlogs() throws Exception {
        perform("/api/product/" + productId);

        assertNull(em.find(Product.class, productId));
        assertEquals(0L, em.createQuery("SELECT count(kb) FROM KnowledgeBlog kb WHERE kb.id = :id", Long.class)
                .setParameter("id", blogId)
                .getSingleResult());
    }

    @Test
    void deletingAProductRemovesItsLabelsWithTheirFeedbackBatchesAndContent() throws Exception {
        Long first = labelWithFeedbackAndBatch("First label");
        Long second = labelWithFeedbackAndBatch("Second label");
        Object[] firstTree = contentTree(first);
        Object[] secondTree = contentTree(second);
        em.clear();

        perform("/api/product/" + productId);

        assertNull(em.find(Product.class, productId));
        assertNull(em.find(ProductLabel.class, first));
        assertNull(em.find(ProductLabel.class, second));
        assertContentTreeGone(firstTree);
        assertContentTreeGone(secondTree);
        assertEquals(0L, em.createQuery("SELECT COUNT(b) FROM ProductLabelBatch b WHERE b.label.id IN :ids", Long.class)
                .setParameter("ids", List.of(first, second)).getSingleResult());
    }

    @Test
    void deletingALabelWithFeedbackRemovesItsContent() throws Exception {
        Long label = labelWithFeedbackAndBatch("Only label");
        Object[] tree = contentTree(label);
        em.clear();

        perform("/api/product/label/" + label);

        assertNull(em.find(ProductLabel.class, label));
        assertContentTreeGone(tree);
        assertEquals(0L, feedbackCount());
        assertNotNull(em.find(Product.class, productId));
    }

    @Test
    void deletingAProductWithAFinalProductIsRefused() throws Exception {
        FinalProduct finalProduct = new FinalProduct();
        finalProduct.setName("Roasted 340 g");
        finalProduct.setDescription("Roasted coffee in 340 g bags");
        finalProduct.setProduct(em.find(Product.class, productId));
        em.persist(finalProduct);
        em.flush();
        em.clear();

        String body = perform("/api/product/" + productId, 400);

        assertTrue(body.contains("Cannot delete a product that has final products"), body);
        assertNotNull(em.find(Product.class, productId));
        assertNotNull(em.find(KnowledgeBlog.class, blogId));
    }

    private void perform(String url) throws Exception {
        perform(url, 200);
    }

    private String perform(String url, int status) throws Exception {
        MvcResult result = mockMvc.perform(delete(url).cookie(ownerSession)).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        em.flush();
        em.clear();
        return result.getResponse().getContentAsString();
    }

    /** A label created through the API, so it has the full content tree, with feedback and a batch. */
    private Long labelWithFeedbackAndBatch(String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/product/label/create").contentType(MediaType.APPLICATION_JSON).cookie(ownerSession)
                .content("{\"productId\":" + productId + ",\"language\":\"EN\",\"title\":\"" + title + "\"}")).andReturn();
        assertEquals(200, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        em.flush();
        em.clear();
        ProductLabel label = em.createQuery("SELECT pl FROM ProductLabel pl WHERE pl.product.id = :productId AND pl.title = :title", ProductLabel.class)
                .setParameter("productId", productId).setParameter("title", title).getSingleResult();
        ProductLabelFeedback feedback = new ProductLabelFeedback();
        feedback.setLabel(label);
        feedback.setType(ProductLabelFeedbackType.PRAISE);
        feedback.setFeedback("Great coffee");
        em.persist(feedback);
        ProductLabelBatch batch = new ProductLabelBatch();
        batch.setLabel(label);
        batch.setNumber("LOT" + title.length());
        em.persist(batch);
        em.flush();
        em.clear();
        return label.getId();
    }

    /** IDs of a label's content and of each part of its content tree. */
    private Object[] contentTree(Long labelId) {
        ProductLabelContent c = em.find(ProductLabel.class, labelId).getContent();
        return new Object[] { c.getId(), c.getProcess().getId(), c.getResponsibility().getId(), c.getSustainability().getId(), c.getSettings().getId() };
    }

    private void assertContentTreeGone(Object[] tree) {
        assertNull(em.find(ProductLabelContent.class, tree[0]));
        assertNull(em.find(Process.class, tree[1]));
        assertNull(em.find(Responsibility.class, tree[2]));
        assertNull(em.find(Sustainability.class, tree[3]));
        assertNull(em.find(ProductSettings.class, tree[4]));
    }

    private long feedbackCount() {
        return em.createQuery("SELECT COUNT(f) FROM ProductLabelFeedback f WHERE f.label.product.id = :productId", Long.class)
                .setParameter("productId", productId).getSingleResult();
    }
}
