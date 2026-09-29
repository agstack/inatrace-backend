package com.abelium.inatrace.components.beycoorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BeycoOrderApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String ACME_LOT = "ACME-BEYCO-LOT-ONLY";
    private static final String RIVAL_LOT = "RIVAL-BEYCO-LOT-ONLY";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private BeycoOrderService beycoOrderService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long acmeId;
    private Long acmeStockId;
    private Long rivalStockId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme Beyco");
        Company rival = company("Rival Beyco");
        User owner = user("beyco-owner@acme.test");
        User stranger = user("beyco-stranger@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        Country country = new Country();
        country.setCode("XB");
        country.setName("Beyco Test Country");
        em.persist(country);

        acmeStockId = stock(acme, owner, country, ACME_LOT).getId();
        rivalStockId = stock(rival, stranger, country, RIVAL_LOT).getId();
        acmeId = acme.getId();
        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void ownerCanReadFieldsForOwnStockOrder() throws Exception {
        MvcResult allowed = mockMvc.perform(fields(acmeStockId).cookie(ownerSession)).andReturn();
        String body = allowed.getResponse().getContentAsString();
        assertEquals(200, allowed.getResponse().getStatus(), body);
        assertTrue(body.contains(ACME_LOT), body);
        assertFalse(body.contains(RIVAL_LOT), body);
    }

    @Test
    void strangerCannotReadBeycoFieldsOfOtherCompany() throws Exception {
        MvcResult refused = mockMvc.perform(fields(acmeStockId).cookie(strangerSession)).andReturn();
        String body = refused.getResponse().getContentAsString();
        assertEquals(403, refused.getResponse().getStatus(), body);
        assertFalse(body.contains(ACME_LOT), body);
    }

    @Test
    void evenOwnerCannotMixForeignStockOrderIntoBeycoFields() throws Exception {
        MvcResult refused = mockMvc.perform(get("/api/chain/beyco-order/company/" + acmeId + "/fields")
                .queryParam("id", String.valueOf(acmeStockId), String.valueOf(rivalStockId))
                .cookie(ownerSession)).andReturn();
        String body = refused.getResponse().getContentAsString();
        assertEquals(403, refused.getResponse().getStatus(), body);
        assertFalse(body.contains(ACME_LOT), body);
        assertFalse(body.contains(RIVAL_LOT), body);
    }

    @Test
    void everyBeycoRouteRefusesOtherTenantAndAnonymousCallerBeforeExternalCall() throws Exception {
        for (RequestBuilder request : requests()) {
            MvcResult anonymous = mockMvc.perform(request).andReturn();
            assertEquals(401, anonymous.getResponse().getStatus(), anonymous.getResponse().getContentAsString());
            assertFalse(anonymous.getResponse().getContentAsString().contains(ACME_LOT));
        }

        for (RequestBuilder request : requestsWithStrangerSession()) {
            MvcResult refused = mockMvc.perform(request).andReturn();
            String body = refused.getResponse().getContentAsString();
            assertEquals(403, refused.getResponse().getStatus(), body);
            assertFalse(body.contains(ACME_LOT), body);
        }
    }

    @Test
    void ownerCanUseBeycoTransportWhileStrangerCannotReachIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth", exchange -> {
            calls.incrementAndGet();
            byte[] response = "{\"accessToken\":\"LOCAL-BEYCO-TOKEN\",\"tokenType\":\"Bearer\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.createContext("/api/Offers", exchange -> {
            calls.incrementAndGet();
            byte[] response = "{\"id\":\"LOCAL-BEYCO-OFFER\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });

        Object oldAuthUrl = ReflectionTestUtils.getField(beycoOrderService, "authUrl");
        Object oldBeycoUrl = ReflectionTestUtils.getField(beycoOrderService, "beycoUrl");
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", baseUrl + "/oauth");
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", baseUrl);

            for (RequestBuilder request : ownerExternalRequests()) {
                MvcResult allowed = mockMvc.perform(request).andReturn();
                assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
                assertTrue(allowed.getResponse().getContentAsString().contains("LOCAL-BEYCO-"));
            }
            assertEquals(3, calls.get());

            List<RequestBuilder> strangerRequests = requestsWithStrangerSession();
            for (RequestBuilder request : List.of(strangerRequests.get(0), strangerRequests.get(1), strangerRequests.get(3))) {
                MvcResult refused = mockMvc.perform(request).andReturn();
                assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
            }
            assertEquals(3, calls.get(), "Foreign tenant must not make outbound Beyco requests");
        } finally {
            ReflectionTestUtils.setField(beycoOrderService, "authUrl", oldAuthUrl);
            ReflectionTestUtils.setField(beycoOrderService, "beycoUrl", oldBeycoUrl);
            server.stop(0);
        }
    }

    private List<RequestBuilder> ownerExternalRequests() {
        return List.of(
                get("/api/chain/beyco-order/company/" + acmeId + "/token")
                        .queryParam("authCode", "test-code").cookie(ownerSession),
                get("/api/chain/beyco-order/company/" + acmeId + "/token/refresh")
                        .header("X-Beyco-Refresh-Token", "test-refresh").cookie(ownerSession),
                post("/api/chain/beyco-order/company/" + acmeId + "/order")
                        .header("X-Beyco-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"offerCoffees\":[]}")
                        .cookie(ownerSession));
    }

    private List<RequestBuilder> requests() {
        return List.of(
                get("/api/chain/beyco-order/company/" + acmeId + "/token").queryParam("authCode", "test-code"),
                get("/api/chain/beyco-order/company/" + acmeId + "/token/refresh")
                        .header("X-Beyco-Refresh-Token", "test-refresh"),
                fields(acmeStockId),
                post("/api/chain/beyco-order/company/" + acmeId + "/order")
                        .header("X-Beyco-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"offerCoffees\":[]}"));
    }

    private List<RequestBuilder> requestsWithStrangerSession() {
        return List.of(
                get("/api/chain/beyco-order/company/" + acmeId + "/token")
                        .queryParam("authCode", "test-code").cookie(strangerSession),
                get("/api/chain/beyco-order/company/" + acmeId + "/token/refresh")
                        .header("X-Beyco-Refresh-Token", "test-refresh").cookie(strangerSession),
                fields(acmeStockId).cookie(strangerSession),
                post("/api/chain/beyco-order/company/" + acmeId + "/order")
                        .header("X-Beyco-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"offerCoffees\":[]}")
                        .cookie(strangerSession));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder fields(Long stockId) {
        return get("/api/chain/beyco-order/company/" + acmeId + "/fields")
                .queryParam("id", String.valueOf(stockId));
    }

    private StockOrder stock(Company company, User owner, Country country, String lot) {
        FacilityLocation location = new FacilityLocation();
        Address address = new Address();
        address.setAddress("Beyco test street");
        address.setCountry(country);
        location.setAddress(address);
        em.persist(location);

        Facility facility = new Facility();
        facility.setName(lot + " facility");
        facility.setCompany(company);
        facility.setFacilityLocation(location);
        em.persist(facility);

        StockOrder stock = new StockOrder();
        stock.setCompany(company);
        stock.setFacility(facility);
        stock.setOrderType(OrderType.PURCHASE_ORDER);
        stock.setInternalLotNumber(lot);
        stock.setProductionDate(LocalDate.of(2025, 8, 18));
        stock.setTotalQuantity(new BigDecimal("100"));
        stock.setCreatedBy(owner);
        stock.setUpdatedBy(owner);
        em.persist(stock);
        return stock;
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setAllowBeycoIntegration(true);
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
