package com.abelium.inatrace.security;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The dashboard endpoints only answer for a company the caller is enrolled in.
 *
 * <p>Two unrelated tenants: Alice belongs to "Acme Coffee Cooperative", which has a purchase
 * delivery on its books; Bob belongs to "Rival Trading Ltd" and has nothing to do with Acme. Bob
 * authenticates normally -- a real JWT from {@link TokenService}, presented in the access cookie,
 * through the real {@code TokenAuthenticationFilter} -- and asks for Acme's data by putting Acme's
 * id in the request. He is refused with 403, which is what {@code ApiStatus.UNAUTHORIZED} maps to
 * and what the other company-scoped reads answer.
 *
 * <p>{@link #guardedEndpointRejectsForeignCompany()} and {@link #ownCompanyDashboardIsReadable()}
 * are controls, so a failure of the two cases above cannot be dismissed as a broken fixture: the
 * first proves the harness authenticates Bob correctly and that an endpoint which does check
 * enrolment rejects him; the second proves the seeded delivery is real and readable by its owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class MultiTenantIsolationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    /** Quantity of the seeded delivery; distinctive so it can be spotted in a response body. */
    private static final BigDecimal DELIVERY_QUANTITY = new BigDecimal("1234.50");
    private static final LocalDate DELIVERY_DATE = LocalDate.of(2025, 6, 17);

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long acmeId;
    private Cookie bobSession;
    private Cookie aliceSession;

    @BeforeEach
    void seedTwoTenants() {
        Company acme = company("Acme Coffee Cooperative");
        Company rival = company("Rival Trading Ltd");

        User alice = user("alice@acme.test", "Alice", "Acme");
        User bob = user("bob@rival.test", "Bob", "Rival");

        enroll(alice, acme);
        enroll(bob, rival);

        // One purchase delivery belonging to Acme — the data Bob must not be able to reach.
        StockOrder delivery = new StockOrder();
        delivery.setCompany(acme);
        delivery.setOrderType(OrderType.PURCHASE_ORDER);
        delivery.setProductionDate(DELIVERY_DATE);
        delivery.setTotalQuantity(DELIVERY_QUANTITY);
        delivery.setCreatedBy(alice);
        delivery.setUpdatedBy(alice);
        em.persist(delivery);

        em.flush();

        acmeId = acme.getId();
        bobSession = sessionCookie(bob);
        aliceSession = sessionCookie(alice);
    }

    // ---------------------------------------------------------------- the breach

    @Test
    @DisplayName("Bob cannot read the aggregated dashboard of a company he does not belong to")
    void dashboardOfForeignCompanyIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/deliveries-aggregated-data")
                        .queryParam("companyId", String.valueOf(acmeId))
                        .queryParam("aggregationType", "DAY")
                        .cookie(bobSession))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertEquals(403, result.getResponse().getStatus(),
                "Bob is not enrolled in Acme, so the dashboard must refuse him. Got HTTP "
                        + result.getResponse().getStatus() + " and this body: " + body);
        assertFalse(body.contains(DELIVERY_QUANTITY.toPlainString()),
                "Acme's delivery quantity was disclosed to a user of another tenant: " + body);
    }

    @Test
    @DisplayName("Bob cannot export the deliveries CSV of a company he does not belong to")
    void deliveriesCsvExportOfForeignCompanyIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/deliveries-aggregated-data/export")
                        .queryParam("companyId", String.valueOf(acmeId))
                        .queryParam("aggregationType", "DAY")
                        .queryParam("exportType", "CSV")
                        .cookie(bobSession))
                .andReturn();

        String file = new String(result.getResponse().getContentAsByteArray()).replace('\n', '|');
        assertEquals(403, result.getResponse().getStatus(),
                "Bob is not enrolled in Acme, so the export must refuse him. Got HTTP "
                        + result.getResponse().getStatus() + " and this body: " + file);
        assertFalse(file.contains(DELIVERY_QUANTITY.toPlainString()),
                "A CSV of another tenant's deliveries was downloaded: " + file);
    }

    // ---------------------------------------------------------------- controls

    @Test
    @DisplayName("Control: a guarded endpoint does reject Bob, so the harness is sound")
    void guardedEndpointRejectsForeignCompany() throws Exception {
        // Same tenant, same session, an endpoint that checks enrollment in the service layer
        // (StockOrderService.getStockOrderListForCompany -> PermissionsUtil.checkUserIfCompanyEnrolled).
        MvcResult result = mockMvc.perform(get("/api/chain/stock-order/list/company/" + acmeId + "/quote-orders")
                        .cookie(bobSession))
                .andReturn();

        assertEquals(403, result.getResponse().getStatus(),
                "Expected the guarded stock-order list to reject a foreign tenant; got "
                        + result.getResponse().getStatus());
    }

    @Test
    @DisplayName("Control: Alice can read her own company's dashboard, so the fixture is real")
    void ownCompanyDashboardIsReadable() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/deliveries-aggregated-data")
                        .queryParam("companyId", String.valueOf(acmeId))
                        .queryParam("aggregationType", "DAY")
                        .cookie(aliceSession))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains(DELIVERY_QUANTITY.toPlainString()),
                "The seeded delivery should be visible to its owner: " + result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------- fixture helpers

    private Company company(String name) {
        Company c = new Company();
        c.setName(name);
        c.setStatus(CompanyStatus.ACTIVE);
        em.persist(c);
        return c;
    }

    private User user(String email, String name, String surname) {
        User u = new User();
        u.setEmail(email);
        u.setName(name);
        u.setSurname(surname);
        u.setPassword("not-used-in-this-test");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        em.persist(u);
        return u;
    }

    private void enroll(User user, Company company) {
        CompanyUser cu = new CompanyUser();
        cu.setUser(user);
        cu.setCompany(company);
        cu.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(cu);
        company.getUsers().add(cu);
    }

    /** A real access cookie, issued the way the login endpoint issues it. */
    private Cookie sessionCookie(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }
}
