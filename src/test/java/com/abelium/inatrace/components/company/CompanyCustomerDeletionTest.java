package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyCustomer;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.productorder.ProductOrder;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Deleting a company customer answers a clear error when the customer does not exist or already has
 * orders. Before, an unknown ID failed with a NullPointerException and a customer with orders failed on
 * the foreign key at commit, both as 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompanyCustomerDeletionTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long customerId;
    private Cookie session;

    @BeforeEach
    void seed() {
        Company company = new Company();
        company.setName("Customer deletion company");
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        User user = new User();
        user.setEmail("customer-deletion@company.test");
        user.setName("Customer");
        user.setSurname("Deletion");
        user.setPassword("not-used-in-this-test");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(company);
        enrollment.setRole(CompanyUserRole.COMPANY_USER);
        em.persist(enrollment);
        company.getUsers().add(enrollment);

        CompanyCustomer customer = new CompanyCustomer();
        customer.setCompany(company);
        customer.setName("Roaster customer");
        em.persist(customer);
        customerId = customer.getId();
        session = new Cookie(accessCookieName, tokenService.createAccessToken(user));
        em.flush();
        em.clear();
    }

    @Test
    void deleteRefusesUnknownCustomer() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/company/companyCustomers/999999999").cookie(session)).andReturn();
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("Invalid Company customer ID"));
    }

    @Test
    void deleteRefusesCustomerWithOrders() throws Exception {
        CompanyCustomer customer = em.find(CompanyCustomer.class, customerId);
        Facility facility = new Facility();
        facility.setCompany(customer.getCompany());
        em.persist(facility);
        ProductOrder order = new ProductOrder();
        order.setOrderId("CUSTOMER-DELETION-1");
        order.setFacility(facility);
        order.setDeliveryDeadline(LocalDate.of(2026, 12, 1));
        order.setCustomer(customer);
        em.persist(order);
        em.flush();
        em.clear();

        MvcResult result = mockMvc.perform(delete("/api/company/companyCustomers/" + customerId).cookie(session)).andReturn();
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains("Cannot delete a company customer that has orders"));
        em.clear();
        assertNotNull(em.find(CompanyCustomer.class, customerId));
    }

    @Test
    void deleteRemovesCustomerWithoutOrders() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/company/companyCustomers/" + customerId).cookie(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertNull(em.find(CompanyCustomer.class, customerId));
    }
}
