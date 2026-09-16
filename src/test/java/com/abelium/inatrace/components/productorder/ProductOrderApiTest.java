package com.abelium.inatrace.components.productorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.productorder.ProductOrder;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * {@code GET /chain/product-order/{id}} answers only for the company that owns the order.
 *
 * <p>This endpoint used to take no authenticated principal at all: it read the id out of the URL,
 * loaded the row, and mapped it. A product order names the customer it was placed for, the facility
 * it was placed at, and every stock order line in it, so an unguarded read by id handed any logged
 * in user the order book of every tenant in the installation, one id at a time.
 *
 * <p>Two unrelated tenants are seeded: Alice belongs to "Acme Coffee Cooperative", which has a
 * product order on its books; Bob belongs to "Rival Trading Ltd" and has nothing to do with Acme.
 * Both authenticate normally -- a real JWT from {@link TokenService} in the access cookie, through
 * the real {@code TokenAuthenticationFilter} -- so Bob's refusal is the application's authorization
 * decision and not a failure to log in.
 *
 * <p>{@link #ORDER_ID} is a deliberately distinctive string. Asserting a status code alone would
 * not catch a handler that answered 403 in the header and still wrote the row into the body, so the
 * refused response is searched for it as well.
 *
 * @see com.abelium.inatrace.security.MultiTenantIsolationTest for the same treatment of the
 *      dashboard endpoints
 * @see com.abelium.inatrace.components.company.FarmerAndPlotApiTest for the farmer register
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class ProductOrderApiTest {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	/** The marker that must never reach anyone outside Acme. */
	private static final String ORDER_ID = "ACME-PO-0007-acmeonly";

	private static final LocalDate DELIVERY_DEADLINE = LocalDate.of(2025, 11, 4);

	@Autowired private MockMvc mockMvc;
	@Autowired private EntityManager em;
	@Autowired private TokenService tokenService;

	@Value("${INATrace.auth.accessTokenCookieName}")
	private String accessCookieName;

	private Long acmeOrderId;

	private Cookie aliceSession;
	private Cookie bobSession;

	@BeforeEach
	void seedTwoTenants() {

		Company acme = company("Acme Coffee Cooperative");
		Company rival = company("Rival Trading Ltd");

		User alice = user("alice@acme.test", "Alice", "Acme");
		User bob = user("bob@rival.test", "Bob", "Rival");

		enroll(alice, acme);
		enroll(bob, rival);

		// One product order placed at an Acme facility -- the row Bob must not be able to reach.
		ProductOrder order = new ProductOrder();
		order.setOrderId(ORDER_ID);
		order.setDeliveryDeadline(DELIVERY_DEADLINE);
		order.setFacility(facility("Acme Washing Station", acme));
		em.persist(order);

		acmeOrderId = order.getId();

		aliceSession = sessionCookie(alice);
		bobSession = sessionCookie(bob);

		// Hand the requests a cold persistence context, so the endpoint loads the order and its
		// facility's company from the database the way it does in production rather than from the
		// session the seed above left behind.
		em.flush();
		em.clear();
	}

	// ---------------------------------------------------------------- the breach

	@Test
	@DisplayName("Bob cannot read a product order belonging to a company he does not belong to")
	void productOrderOfForeignCompanyIsRejected() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/chain/product-order/" + acmeOrderId)
						.cookie(bobSession))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertEquals(403, result.getResponse().getStatus(),
				"Bob is not enrolled in Acme, so the product order must refuse him. Got HTTP "
						+ result.getResponse().getStatus() + " and this body: " + body);
		assertFalse(body.contains(ORDER_ID),
				"Acme's product order was disclosed to a user of another tenant: " + body);
	}

	@Test
	@DisplayName("An anonymous caller cannot read a product order")
	void productOrderRequiresASession() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/chain/product-order/" + acmeOrderId)).andReturn();

		String body = result.getResponse().getContentAsString();
		assertEquals(401, result.getResponse().getStatus(),
				"Without a session the endpoint must answer 401. Got HTTP "
						+ result.getResponse().getStatus() + " and this body: " + body);
		assertFalse(body.contains(ORDER_ID),
				"Acme's product order was disclosed to an anonymous caller: " + body);
	}

	// ---------------------------------------------------------------- control

	@Test
	@DisplayName("Control: Alice can read her own company's product order, so the fixture is real")
	void ownCompanyProductOrderIsReadable() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/chain/product-order/" + acmeOrderId)
						.cookie(aliceSession))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertEquals(200, result.getResponse().getStatus(),
				"Acme's own product order should be readable by Acme. Got HTTP "
						+ result.getResponse().getStatus() + " and this body: " + body);
		assertTrue(body.contains(ORDER_ID),
				"The seeded product order should be visible to its owner: " + body);
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

	/**
	 * A facility with a location: {@code FacilityMapper.toApiFacilityBase} reads the location
	 * without a null check, so the success path needs one even though the column allows null.
	 */
	private Facility facility(String name, Company company) {
		Facility f = new Facility();
		f.setName(name);
		f.setCompany(company);
		f.setFacilityLocation(new FacilityLocation());
		em.persist(f);
		return f;
	}

	/** A real access cookie, issued the way the login endpoint issues it. */
	private Cookie sessionCookie(User user) {
		return new Cookie(accessCookieName, tokenService.createAccessToken(user));
	}
}
