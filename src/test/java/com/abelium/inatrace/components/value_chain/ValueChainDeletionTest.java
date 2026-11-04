package com.abelium.inatrace.components.value_chain;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.value_chain.CompanyValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChainFacilityType;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Deleting a value chain answers a clear error when companies, facilities, processing actions or
 * products use it. Before, the foreign key made the delete fail at commit as a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValueChainDeletionTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long valueChainId;
    private Cookie session;

    @BeforeEach
    void seed() {
        User admin = new User();
        admin.setEmail("value-chain-deletion@admin.test");
        admin.setName("Value chain");
        admin.setSurname("Deletion");
        admin.setPassword("not-used-in-this-test");
        admin.setRole(UserRole.SYSTEM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        em.persist(admin);

        ProductType productType = new ProductType();
        productType.setCode("VALUE_CHAIN_DELETION");
        productType.setName("Value chain deletion product");
        productType.setDescription("Value chain deletion product");
        em.persist(productType);

        ValueChain valueChain = new ValueChain();
        valueChain.setName("Value chain deletion");
        valueChain.setDescription("Value chain deletion");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setProductType(productType);
        valueChain.setCreatedBy(admin);
        em.persist(valueChain);

        // Its own configuration goes with it.
        FacilityType facilityType = new FacilityType("VALUE_CHAIN_DELETION", "Value chain deletion facility");
        em.persist(facilityType);
        em.persist(new ValueChainFacilityType(valueChain, facilityType));

        valueChainId = valueChain.getId();
        session = new Cookie(accessCookieName, tokenService.createAccessToken(admin));
        em.flush();
        em.clear();
    }

    @Test
    void deleteRefusesValueChainUsedByACompany() throws Exception {
        Company company = new Company();
        company.setName("Value chain deletion company");
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        CompanyValueChain enrollment = new CompanyValueChain();
        enrollment.setCompany(company);
        enrollment.setValueChain(em.find(ValueChain.class, valueChainId));
        em.persist(enrollment);
        em.flush();
        em.clear();

        MvcResult result = mockMvc.perform(delete("/api/chain/value-chain/" + valueChainId).cookie(session)).andReturn();
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("Cannot delete a value chain that is used"));
        em.clear();
        assertNotNull(em.find(ValueChain.class, valueChainId));
    }

    @Test
    void deleteRemovesUnusedValueChainWithItsConfiguration() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/chain/value-chain/" + valueChainId).cookie(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertNull(em.find(ValueChain.class, valueChainId));
        assertEquals(0L, em.createQuery("SELECT COUNT(v) FROM ValueChainFacilityType v WHERE v.valueChain.id = :id", Long.class)
                .setParameter("id", valueChainId).getSingleResult());
    }
}
