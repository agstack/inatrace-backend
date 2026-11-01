package com.abelium.inatrace.components.product;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.product.KnowledgeBlog;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.KnowledgeBlogType;
import com.abelium.inatrace.types.ProductCompanyType;
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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * A product's knowledge blogs can be deleted one by one, and deleting the product deletes them too.
 * Before, a product with a single blog could not be deleted (foreign key from KnowledgeBlog) and no
 * route deleted blogs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KnowledgeBlogDeletionTest extends AbstractMySqlIntegrationTest {

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

    private void perform(String url) throws Exception {
        MvcResult result = mockMvc.perform(delete(url).cookie(ownerSession)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        em.flush();
        em.clear();
    }
}
