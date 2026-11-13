package com.abelium.inatrace.components.product;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.product.ProductLabel;
import com.abelium.inatrace.db.entities.product.ProductLabelContent;
import com.abelium.inatrace.db.entities.product.ProductLabelFeedback;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.ProductLabelFeedbackType;
import com.abelium.inatrace.types.ProductLabelStatus;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The label UID is printed in every QR code, so the public feedback list must not carry what
 * consumers gave only to the company: e-mail, questionnaire answers and consents. The companies
 * of the product read them through an authenticated route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductLabelFeedbackPrivacyTest extends AbstractMySqlIntegrationTest {

    private static final String TEXT = "FEEDBACK-PRIVACY-TEXT";
    private static final String EMAIL = "consumer.privacy@example.com";
    private static final String AGE = "AGE-PRIVACY-34";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;

    private String labelUid;
    private Long labelId;
    private Cookie ownerSession;
    private Cookie associateSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company owner = company("Feedback privacy owner");
        Company associate = company("Feedback privacy associate");
        Company stranger = company("Feedback privacy stranger");
        User ownerUser = user("feedback-privacy-owner@test", owner);
        User associateUser = user("feedback-privacy-associate@test", associate);
        User strangerUser = user("feedback-privacy-stranger@test", stranger);

        ProductType type = new ProductType();
        type.setCode("FEEDBACK_PRIVACY");
        type.setName("Feedback privacy type");
        em.persist(type);
        ValueChain chain = new ValueChain();
        chain.setName("Feedback privacy chain");
        chain.setDescription("Feedback privacy chain");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(type);
        chain.setCreatedBy(ownerUser);
        em.persist(chain);

        Product product = new Product();
        product.setName("Feedback privacy product");
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

        ProductLabelContent content = ProductLabelContent.fromProduct(product);
        em.persist(content.getProcess());
        em.persist(content.getResponsibility());
        em.persist(content.getSustainability());
        em.persist(content.getJourney());
        em.persist(content.getSettings());
        em.persist(content.getBusinessToCustomerSettings());
        em.persist(content);
        ProductLabel label = new ProductLabel();
        label.setProduct(product);
        label.setContent(content);
        label.setTitle("Feedback privacy label");
        label.setLanguage(Language.EN);
        label.setStatus(ProductLabelStatus.PUBLISHED);
        em.persist(label);

        ProductLabelFeedback feedback = new ProductLabelFeedback();
        feedback.setLabel(label);
        feedback.setType(ProductLabelFeedbackType.PRAISE);
        feedback.setFeedback(TEXT);
        feedback.setEmail(EMAIL);
        feedback.setGdprConsent(true);
        feedback.setQuestionnaireAnswers(Map.of("age", AGE, "gender", "FEMALE"));
        em.persist(feedback);

        labelUid = label.getUuid();
        labelId = label.getId();
        ownerSession = session(ownerUser);
        associateSession = session(associateUser);
        strangerSession = session(strangerUser);
        em.flush();
        em.clear();
    }

    @Test
    void publicFeedbackListKeepsTheTextButNotTheConsumerDetails() throws Exception {
        String body = body(get("/api/public/product/label/feedback/list/" + labelUid), 200);
        assertTrue(body.contains(TEXT), body);
        assertFalse(body.contains(EMAIL), body);
        assertFalse(body.contains(AGE), body);
        assertFalse(body.contains("FEMALE"), body);
        assertFalse(body.contains("gdprConsent"), body);
        // Only type, text and product name: no internal feedback or label IDs either.
        assertFalse(body.contains("\"id\""), body);
        assertFalse(body.contains("\"labelId\""), body);
    }

    @Test
    void companiesOfTheProductReadTheConsumerDetails() throws Exception {
        for (Cookie session : new Cookie[] { ownerSession, associateSession }) {
            String body = body(get("/api/product/label/" + labelId + "/feedback/list").cookie(session), 200);
            assertTrue(body.contains(TEXT), body);
            assertTrue(body.contains(EMAIL), body);
            assertTrue(body.contains(AGE), body);
        }
    }

    @Test
    void authenticatedFeedbackListRejectsStrangersAndAnonymousUsers() throws Exception {
        MvcResult stranger = mockMvc.perform(get("/api/product/label/" + labelId + "/feedback/list").cookie(strangerSession)).andReturn();
        assertTrue(stranger.getResponse().getStatus() >= 400, stranger.getResponse().getContentAsString());
        assertFalse(stranger.getResponse().getContentAsString().contains(EMAIL));

        MvcResult anonymous = mockMvc.perform(get("/api/product/label/" + labelId + "/feedback/list")).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus(), anonymous.getResponse().getContentAsString());
        assertFalse(anonymous.getResponse().getContentAsString().contains(EMAIL));
    }

    private String body(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        String body = result.getResponse().getContentAsString();
        assertEquals(expected, result.getResponse().getStatus(), body);
        return body;
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private User user(String email, Company company) {
        User user = new User();
        user.setEmail(email);
        user.setName("Feedback");
        user.setSurname("Privacy");
        user.setPassword("not-used");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(company);
        enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(enrollment);
        company.getUsers().add(enrollment);
        return user;
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
