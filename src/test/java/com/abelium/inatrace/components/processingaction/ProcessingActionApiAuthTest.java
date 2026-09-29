package com.abelium.inatrace.components.processingaction;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionTranslation;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProcessingActionType;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProcessingActionApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "ACME-PROCESSING-ACTION-ONLY";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long acmeId;
    private Long rivalId;
    private Long actionId;
    private Long valueChainId;
    private Long semiProductId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme processing action");
        Company rival = company("Rival processing action");
        User owner = user("action-owner@acme.test");
        User stranger = user("action-stranger@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        ProductType productType = new ProductType();
        productType.setCode("ACTION_TEST");
        productType.setName("Action test product type");
        em.persist(productType);
        ValueChain chain = new ValueChain();
        chain.setName("Action test value chain");
        chain.setDescription("Action test value chain");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(productType);
        chain.setCreatedBy(owner);
        em.persist(chain);
        valueChainId = chain.getId();

        SemiProduct semiProduct = new SemiProduct();
        semiProduct.setName("Action test semi-product");
        em.persist(semiProduct);
        semiProductId = semiProduct.getId();

        ProcessingAction action = new ProcessingAction();
        action.setCompany(acme);
        action.setType(ProcessingActionType.SHIPMENT);
        action.setInputSemiProduct(semiProduct);
        action.setFinalProductAction(false);
        action.setPrefix("ACME-ACTION-PREFIX");
        em.persist(action);
        actionId = action.getId();

        ProcessingActionTranslation translation = new ProcessingActionTranslation(Language.EN);
        translation.setName(MARKER);
        translation.setDescription("Acme only processing action");
        translation.setProcessingAction(action);
        em.persist(translation);
        action.getProcessingActionTranslations().add(translation);

        acmeId = acme.getId();
        rivalId = rival.getId();
        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void processingActionReadsAreTenantScoped() throws Exception {
        for (String path : List.of(
                "/api/chain/processing-action/" + actionId,
                "/api/chain/processing-action/" + actionId + "/detail",
                "/api/chain/processing-action/list/company/" + acmeId)) {
            MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
            String ownerBody = allowed.getResponse().getContentAsString();
            assertEquals(200, allowed.getResponse().getStatus(), path + ": " + ownerBody);
            assertTrue(ownerBody.contains(MARKER), path + ": " + ownerBody);

            MvcResult refused = mockMvc.perform(get(path).cookie(strangerSession)).andReturn();
            String strangerBody = refused.getResponse().getContentAsString();
            assertEquals(403, refused.getResponse().getStatus(), path + ": " + strangerBody);
            assertFalse(strangerBody.contains(MARKER), path + ": " + strangerBody);

            MvcResult anonymous = mockMvc.perform(get(path)).andReturn();
            assertEquals(401, anonymous.getResponse().getStatus(), path);
            assertFalse(anonymous.getResponse().getContentAsString().contains(MARKER));
        }
    }

    @Test
    void updateCannotTransferForeignActionToRequestersCompany() throws Exception {
        String path = "/api/chain/processing-action";
        String takeover = actionBody(actionId, rivalId, "RIVAL-TAKEOVER-ATTEMPT");
        MvcResult refused = mockMvc.perform(put(path).cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(takeover)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(MARKER));
        em.clear();
        assertEquals(acmeId, em.find(ProcessingAction.class, actionId).getCompany().getId());
        assertEquals("ACME-ACTION-PREFIX", em.find(ProcessingAction.class, actionId).getPrefix());

        MvcResult anonymous = mockMvc.perform(put(path)
                .contentType(MediaType.APPLICATION_JSON).content(takeover)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());

        String allowedBody = actionBody(actionId, acmeId, "ACME-UPDATED-ACTION-ONLY");
        MvcResult allowed = mockMvc.perform(put(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(allowedBody)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertEquals("ACME-UPDATED-ACTION-ONLY", em.find(ProcessingAction.class, actionId).getPrefix());
    }

    @Test
    void foreignUserCannotCreateActionForAcme() throws Exception {
        long before = actionCount();
        String body = actionBody(null, acmeId, "ACME-NEW-ACTION-ONLY");
        MvcResult refused = mockMvc.perform(put("/api/chain/processing-action").cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertEquals(before, actionCount());

        MvcResult allowed = mockMvc.perform(put("/api/chain/processing-action").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(before + 1, actionCount());
    }

    @Test
    void foreignDeleteLeavesActionAndTranslationsUntouched() throws Exception {
        String path = "/api/chain/processing-action/" + actionId;
        MvcResult refused = mockMvc.perform(delete(path).cookie(strangerSession)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(MARKER));
        em.clear();
        ProcessingAction intact = em.find(ProcessingAction.class, actionId);
        assertNotNull(intact);
        assertEquals(MARKER, intact.getProcessingActionTranslations().iterator().next().getName());

        MvcResult anonymous = mockMvc.perform(delete(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());

        MvcResult allowed = mockMvc.perform(delete(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertNull(em.find(ProcessingAction.class, actionId));
    }

    private String actionBody(Long id, Long companyId, String prefix) {
        String idField = id == null ? "" : "\"id\":" + id + ",";
        return """
                {%s"company":{"id":%d},"type":"SHIPMENT","finalProductAction":false,
                 "inputSemiProduct":{"id":%d},"valueChains":[{"id":%d}],"prefix":"%s",
                 "translations":[{"language":"EN","name":"%s","description":"Test action"}],
                 "requiredEvidenceFields":[],"requiredDocumentTypes":[],"supportedFacilities":[]}
                """.formatted(idField, companyId, semiProductId, valueChainId, prefix, prefix);
    }

    private long actionCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(a) FROM ProcessingAction a", Long.class).getSingleResult();
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setSurname("Test");
        user.setPassword("not-used-in-this-test");
        user.setRole(UserRole.USER);
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

    private Cookie cookie(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }
}
