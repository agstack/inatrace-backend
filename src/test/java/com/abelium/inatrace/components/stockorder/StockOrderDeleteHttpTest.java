package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Deletes a stock order through the HTTP endpoint without a surrounding test transaction.
 *
 * <p>{@code StockOrderApiAuthTest} is {@code @Transactional} at class level, so the endpoint runs
 * inside the test's transaction and a service method that lost its own {@code @Transactional}
 * still passes there. This class is deliberately not transactional: the delete has to open and
 * commit its own transaction, or {@code em.remove} fails with {@code TransactionRequiredException}
 * and the endpoint answers 500. Fixtures are committed and removed explicitly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockOrderDeleteHttpTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Fixture fixture;

    @BeforeEach
    void seed() {
        fixture = tx().execute(status -> {
            Company company = new Company();
            company.setName("Delete HTTP company");
            company.setStatus(CompanyStatus.ACTIVE);
            em.persist(company);

            User owner = new User();
            owner.setEmail("stock-delete-http@acme.test");
            owner.setName("Delete");
            owner.setSurname("Owner");
            owner.setPassword("not-used-in-this-test");
            owner.setRole(UserRole.USER);
            owner.setStatus(UserStatus.ACTIVE);
            em.persist(owner);

            CompanyUser enrollment = new CompanyUser();
            enrollment.setUser(owner);
            enrollment.setCompany(company);
            enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
            em.persist(enrollment);

            Country country = new Country();
            country.setCode("XD");
            country.setName("Delete HTTP country");
            em.persist(country);
            FacilityType facilityType = new FacilityType("DELETE_HTTP", "Delete HTTP facility");
            em.persist(facilityType);
            Address address = new Address();
            address.setCountry(country);
            address.setAddress("Delete HTTP address");
            FacilityLocation location = new FacilityLocation();
            location.setAddress(address);
            em.persist(location);
            Facility facility = new Facility();
            facility.setName("Delete HTTP facility");
            facility.setCompany(company);
            facility.setFacilityLocation(location);
            facility.setFacilityType(facilityType);
            facility.setIsCollectionFacility(true);
            facility.setIsDeactivated(false);
            em.persist(facility);

            MeasureUnitType measure = new MeasureUnitType("DELETE_HTTP_KG", "Delete HTTP kilogram", BigDecimal.ONE);
            em.persist(measure);
            SemiProduct semi = new SemiProduct();
            semi.setName("Delete HTTP semi");
            semi.setBuyable(true);
            semi.setMeasurementUnitType(measure);
            em.persist(semi);

            StockOrder order = new StockOrder();
            order.setCompany(company);
            order.setFacility(facility);
            order.setSemiProduct(semi);
            order.setMeasurementUnitType(measure);
            order.setCreatedBy(owner);
            order.setIdentifier("DELETE-HTTP-ORDER");
            order.setOrderType(OrderType.GENERAL_ORDER);
            order.setTotalQuantity(BigDecimal.TEN);
            order.setFulfilledQuantity(BigDecimal.ZERO);
            order.setAvailableQuantity(BigDecimal.ZERO);
            em.persist(order);

            return new Fixture(company.getId(), owner.getId(), country.getId(), facilityType.getId(),
                    location.getId(), facility.getId(), measure.getId(), semi.getId(), order.getId(),
                    new Cookie(accessCookieName, tokenService.createAccessToken(owner)));
        });
    }

    @AfterEach
    void removeFixture() {
        tx().executeWithoutResult(status -> {
            remove("StockOrder", fixture.orderId());
            remove("SemiProduct", fixture.semiProductId());
            remove("MeasureUnitType", fixture.measureId());
            remove("Facility", fixture.facilityId());
            remove("FacilityLocation", fixture.locationId());
            remove("FacilityType", fixture.facilityTypeId());
            remove("Country", fixture.countryId());
            em.createQuery("DELETE FROM CompanyUser enrollment WHERE enrollment.company.id = :companyId")
                    .setParameter("companyId", fixture.companyId()).executeUpdate();
            remove("User", fixture.ownerId());
            remove("Company", fixture.companyId());
        });
    }

    @Test
    @DisplayName("DELETE /api/chain/stock-order/{id} commits in its own transaction")
    void ownerDeletesStockOrderWithoutTestTransaction() throws Exception {
        MvcResult allowed = mockMvc.perform(delete("/api/chain/stock-order/" + fixture.orderId())
                .cookie(fixture.ownerSession())).andReturn();

        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertNull(tx().execute(status -> em.find(StockOrder.class, fixture.orderId())),
                "the delete must be committed, not only flushed inside a test transaction");
    }

    private void remove(String entity, Long id) {
        em.createQuery("DELETE FROM " + entity + " e WHERE e.id = :id").setParameter("id", id).executeUpdate();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    private record Fixture(Long companyId, Long ownerId, Long countryId, Long facilityTypeId, Long locationId,
                           Long facilityId, Long measureId, Long semiProductId, Long orderId, Cookie ownerSession) {}
}
