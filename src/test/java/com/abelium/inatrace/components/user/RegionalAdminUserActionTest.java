package com.abelium.inatrace.components.user;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.user.types.UserAction;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A regional admin may onboard accounts (confirm the e-mail, activate) but must not change roles.
 *
 * <p>{@code POST /api/user/admin/execute/{action}} is open to SYSTEM_ADMIN and REGIONAL_ADMIN, and the
 * service only refused DEACTIVATE_USER to the regional admin. Every account without a company is in
 * its reach, so a regional admin could register a second account, confirm and activate it, and then
 * promote it with SET_USER_SYSTEM_ADMIN — or demote a system admin it shares a company with. The
 * frontend only offers the role actions to system admins.
 *
 * <p>The rest of the action matrix is here too: what a system admin may do to an account in each role
 * and status, and which accounts a regional admin reaches. The regional admin is also enrolled in a
 * REGISTERED company, which counts, and in a DEACTIVATED one, which does not. Refusals re-read the
 * target, so a change made behind an error status is caught.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegionalAdminUserActionTest extends AbstractMySqlIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private EntityManager em;
	@Autowired private TokenService tokenService;

	@Value("${INATrace.auth.accessTokenCookieName}")
	private String accessCookieName;

	private record State(UserRole role, UserStatus status) {
	}

	private User regionalAdmin;
	private Company company;
	private Company registeredCompany;
	private Company deactivatedCompany;
	private Company foreignCompany;

	@BeforeEach
	void seed() {
		regionalAdmin = user(UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
		company = company("Regional admin company", CompanyStatus.ACTIVE);
		registeredCompany = company("Regional admin registered company", CompanyStatus.REGISTERED);
		deactivatedCompany = company("Regional admin deactivated company", CompanyStatus.DEACTIVATED);
		foreignCompany = company("Foreign company", CompanyStatus.ACTIVE);
		enroll(regionalAdmin);
		enroll(regionalAdmin, registeredCompany, CompanyUserRole.COMPANY_USER);
		enroll(regionalAdmin, deactivatedCompany, CompanyUserRole.COMPANY_USER);
	}

	@Test
	@DisplayName("a regional admin cannot promote an account to system admin")
	void cannotPromoteToSystemAdmin() throws Exception {
		// An account with no company: the case of a second account registered by the regional admin.
		User target = user(UserRole.USER, UserStatus.ACTIVE);

		assertRefused("SET_USER_SYSTEM_ADMIN", target);
		assertEquals(UserRole.USER, roleOf(target));
	}

	@Test
	@DisplayName("a regional admin cannot grant or remove the admin roles")
	void cannotChangeAdminRoles() throws Exception {
		User user = user(UserRole.USER, UserStatus.ACTIVE);
		User otherRegional = user(UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
		enroll(otherRegional);
		enroll(systemAdmin);

		assertRefused("SET_USER_REGIONAL_ADMIN", user);
		assertRefused("UNSET_USER_REGIONAL_ADMIN", otherRegional);
		assertRefused("UNSET_USER_SYSTEM_ADMIN", systemAdmin);
		assertEquals(UserRole.USER, roleOf(user));
		assertEquals(UserRole.REGIONAL_ADMIN, roleOf(otherRegional));
		assertEquals(UserRole.SYSTEM_ADMIN, roleOf(systemAdmin));
	}

	@Test
	@DisplayName("a regional admin can still confirm and activate a new account")
	void canStillOnboard() throws Exception {
		User unconfirmed = user(UserRole.USER, UserStatus.UNCONFIRMED);

		assertAccepted("CONFIRM_USER_EMAIL", unconfirmed);
		assertEquals(UserStatus.CONFIRMED_EMAIL, statusOf(unconfirmed));
		assertAccepted("ACTIVATE_USER", unconfirmed);
		assertEquals(UserStatus.ACTIVE, statusOf(unconfirmed));
	}

	@Test
	@DisplayName("a system admin can still change roles")
	void systemAdminCanChangeRoles() throws Exception {
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
		User target = user(UserRole.USER, UserStatus.ACTIVE);

		MvcResult result = execute(systemAdmin, "SET_USER_SYSTEM_ADMIN", target);
		assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		assertEquals(UserRole.SYSTEM_ADMIN, roleOf(target));
	}

	@Test
	@DisplayName("a company user or company admin cannot execute any action on a colleague")
	void companyMembersCannotExecuteAnyAction() throws Exception {
		User companyUser = user(UserRole.USER, UserStatus.ACTIVE, company);
		User companyAdmin = user(UserRole.USER, UserStatus.ACTIVE);
		enroll(companyAdmin, company, CompanyUserRole.COMPANY_ADMIN);

		for (User caller : List.of(companyUser, companyAdmin)) {
			for (UserAction action : UserAction.values()) {
				assertForbidden(caller, action, user(UserRole.USER, UserStatus.CONFIRMED_EMAIL, company));
			}
		}
	}

	@Test
	@DisplayName("a system admin moves an account through every status and admin role")
	void systemAdminExecutesEveryAction() throws Exception {
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);

		assertApplied(systemAdmin, UserAction.CONFIRM_USER_EMAIL, UserRole.USER, UserStatus.UNCONFIRMED,
				UserRole.USER, UserStatus.CONFIRMED_EMAIL);
		assertApplied(systemAdmin, UserAction.ACTIVATE_USER, UserRole.USER, UserStatus.CONFIRMED_EMAIL,
				UserRole.USER, UserStatus.ACTIVE);
		assertApplied(systemAdmin, UserAction.ACTIVATE_USER, UserRole.USER, UserStatus.DEACTIVATED,
				UserRole.USER, UserStatus.ACTIVE);
		assertApplied(systemAdmin, UserAction.DEACTIVATE_USER, UserRole.USER, UserStatus.ACTIVE,
				UserRole.USER, UserStatus.DEACTIVATED);
		assertApplied(systemAdmin, UserAction.DEACTIVATE_USER, UserRole.USER, UserStatus.CONFIRMED_EMAIL,
				UserRole.USER, UserStatus.DEACTIVATED);
		assertApplied(systemAdmin, UserAction.DEACTIVATE_USER, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE,
				UserRole.REGIONAL_ADMIN, UserStatus.DEACTIVATED);
		assertApplied(systemAdmin, UserAction.SET_USER_REGIONAL_ADMIN, UserRole.USER, UserStatus.ACTIVE,
				UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
		assertApplied(systemAdmin, UserAction.UNSET_USER_REGIONAL_ADMIN, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE,
				UserRole.USER, UserStatus.ACTIVE);
		assertApplied(systemAdmin, UserAction.UNSET_USER_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE,
				UserRole.USER, UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("an action whose status or role precondition fails is refused and changes nothing")
	void actionsCheckTheirPreconditions() throws Exception {
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);

		assertInvalid(systemAdmin, UserAction.DEACTIVATE_USER, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.DEACTIVATE_USER, UserRole.USER, UserStatus.UNCONFIRMED);
		assertInvalid(systemAdmin, UserAction.DEACTIVATE_USER, UserRole.USER, UserStatus.DEACTIVATED);
		assertInvalid(systemAdmin, UserAction.ACTIVATE_USER, UserRole.USER, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.ACTIVATE_USER, UserRole.USER, UserStatus.UNCONFIRMED);
		assertInvalid(systemAdmin, UserAction.CONFIRM_USER_EMAIL, UserRole.USER, UserStatus.CONFIRMED_EMAIL);
		assertInvalid(systemAdmin, UserAction.CONFIRM_USER_EMAIL, UserRole.USER, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.SET_USER_SYSTEM_ADMIN, UserRole.USER, UserStatus.CONFIRMED_EMAIL);
		assertInvalid(systemAdmin, UserAction.SET_USER_SYSTEM_ADMIN, UserRole.USER, UserStatus.DEACTIVATED);
		assertInvalid(systemAdmin, UserAction.SET_USER_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.SET_USER_REGIONAL_ADMIN, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.SET_USER_REGIONAL_ADMIN, UserRole.USER, UserStatus.UNCONFIRMED);
		assertInvalid(systemAdmin, UserAction.UNSET_USER_SYSTEM_ADMIN, UserRole.USER, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.UNSET_USER_REGIONAL_ADMIN, UserRole.USER, UserStatus.ACTIVE);
		assertInvalid(systemAdmin, UserAction.VIEW_USER_PROFILE, UserRole.USER, UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("no admin can execute any action on itself")
	void adminsCannotActOnThemselves() throws Exception {
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);

		for (User admin : List.of(systemAdmin, regionalAdmin)) {
			State before = stateOf(admin);
			for (UserAction action : UserAction.values()) {
				MvcResult result = execute(admin, action.name(), admin);
				String body = result.getResponse().getContentAsString();

				assertEquals(400, result.getResponse().getStatus(), before.role() + " " + action + ": " + body);
				assertTrue(body.contains("\"status\":\"INVALID_REQUEST\""), body);
				assertEquals(before, stateOf(admin), action.name());
			}
		}
	}

	@Test
	@DisplayName("a regional admin onboards users of its registered company and users it shares with another company")
	void regionalAdminOnboardsUsersOfRegisteredAndSharedCompanies() throws Exception {
		for (User target : List.of(
				user(UserRole.USER, UserStatus.UNCONFIRMED, registeredCompany),
				user(UserRole.USER, UserStatus.UNCONFIRMED, company, foreignCompany))) {
			assertAccepted("CONFIRM_USER_EMAIL", target);
			assertEquals(new State(UserRole.USER, UserStatus.CONFIRMED_EMAIL), stateOf(target));
			assertAccepted("ACTIVATE_USER", target);
			assertEquals(new State(UserRole.USER, UserStatus.ACTIVE), stateOf(target));
		}
	}

	@Test
	@DisplayName("a regional admin cannot act through a deactivated company it belongs to, nor on a foreign company")
	void regionalAdminCannotActThroughDeactivatedOrForeignCompanies() throws Exception {
		for (Company outOfScope : List.of(deactivatedCompany, foreignCompany)) {
			assertForbidden(regionalAdmin, UserAction.CONFIRM_USER_EMAIL,
					user(UserRole.USER, UserStatus.UNCONFIRMED, outOfScope));
			assertForbidden(regionalAdmin, UserAction.ACTIVATE_USER,
					user(UserRole.USER, UserStatus.CONFIRMED_EMAIL, outOfScope));
			assertForbidden(regionalAdmin, UserAction.ACTIVATE_USER,
					user(UserRole.USER, UserStatus.DEACTIVATED, outOfScope));

			for (UserAction action : UserAction.values()) {
				assertForbidden(regionalAdmin, action, user(UserRole.USER, UserStatus.ACTIVE, outOfScope));
			}
		}
	}

	@Test
	@DisplayName("a regional admin cannot deactivate or promote a user of its own companies")
	void regionalAdminCannotDeactivateOrPromoteInItsCompanies() throws Exception {
		for (Company own : List.of(company, registeredCompany)) {
			assertForbidden(regionalAdmin, UserAction.DEACTIVATE_USER, user(UserRole.USER, UserStatus.ACTIVE, own));
			assertForbidden(regionalAdmin, UserAction.DEACTIVATE_USER, user(UserRole.USER, UserStatus.CONFIRMED_EMAIL, own));
			assertForbidden(regionalAdmin, UserAction.SET_USER_SYSTEM_ADMIN, user(UserRole.USER, UserStatus.ACTIVE, own));
			assertForbidden(regionalAdmin, UserAction.SET_USER_SYSTEM_ADMIN, user(UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE, own));
			assertForbidden(regionalAdmin, UserAction.SET_USER_REGIONAL_ADMIN, user(UserRole.USER, UserStatus.ACTIVE, own));
		}
	}

	@Test
	@DisplayName("the current service lets a regional admin reactivate a deactivated user of its company (#168)")
	void regionalAdminReactivatesADeactivatedUser() throws Exception {
		User deactivated = user(UserRole.USER, UserStatus.DEACTIVATED, company);

		assertAccepted("ACTIVATE_USER", deactivated);
		assertEquals(new State(UserRole.USER, UserStatus.ACTIVE), stateOf(deactivated));
	}

	@Test
	@DisplayName("deactivating a user ends the session it already had")
	void deactivationEndsTheTargetsSession() throws Exception {
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
		User target = user(UserRole.USER, UserStatus.ACTIVE, company);

		assertEquals(200, perform(get("/api/user/profile").cookie(session(target))).getResponse().getStatus());
		assertEquals(200, execute(systemAdmin, "DEACTIVATE_USER", target).getResponse().getStatus());
		assertEquals(401, perform(get("/api/user/profile").cookie(session(target))).getResponse().getStatus());
	}

	@Test
	@DisplayName("an unknown action, an unusable body or an unknown user is a client error")
	void malformedRequestsAreClientErrors() throws Exception {
		User systemAdmin = user(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
		User target = user(UserRole.USER, UserStatus.CONFIRMED_EMAIL, company);
		State before = stateOf(target);

		for (User caller : List.of(systemAdmin, regionalAdmin)) {
			for (MockHttpServletRequestBuilder request : List.of(
					action("NOT_AN_ACTION").content("{\"id\":" + target.getId() + "}"),
					action("ACTIVATE_USER"),
					action("ACTIVATE_USER").content("{"),
					action("ACTIVATE_USER").content("{}"),
					action("ACTIVATE_USER").content("{\"id\":" + Long.MAX_VALUE + "}"))) {
				MvcResult result = perform(request.cookie(session(caller)));
				assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
			}
			assertEquals(before, stateOf(target));
		}
	}

	// ---------------------------------------------------------------------- helpers

	private void assertApplied(User actor, UserAction action, UserRole role, UserStatus status,
							   UserRole expectedRole, UserStatus expectedStatus) throws Exception {
		User target = user(role, status, foreignCompany);

		MvcResult result = execute(actor, action.name(), target);

		assertEquals(200, result.getResponse().getStatus(),
				action + " on " + role + "/" + status + ": " + result.getResponse().getContentAsString());
		assertEquals(new State(expectedRole, expectedStatus), stateOf(target), action + " on " + role + "/" + status);
	}

	private void assertInvalid(User actor, UserAction action, UserRole role, UserStatus status) throws Exception {
		User target = user(role, status, foreignCompany);

		MvcResult result = execute(actor, action.name(), target);
		String body = result.getResponse().getContentAsString();

		assertEquals(400, result.getResponse().getStatus(), action + " on " + role + "/" + status + ": " + body);
		assertTrue(body.contains("\"status\":\"INVALID_REQUEST\""), body);
		assertEquals(new State(role, status), stateOf(target), action + " on " + role + "/" + status);
	}

	private void assertForbidden(User actor, UserAction action, User target) throws Exception {
		State before = stateOf(target);

		MvcResult result = execute(actor, action.name(), target);
		String body = result.getResponse().getContentAsString();

		assertEquals(403, result.getResponse().getStatus(), action + ": " + body);
		assertTrue(body.contains("\"status\":\"UNAUTHORIZED\""), body);
		assertEquals(before, stateOf(target), action.name());
	}

	private void assertRefused(String action, User target) throws Exception {
		MvcResult result = execute(regionalAdmin, action, target);
		String body = result.getResponse().getContentAsString();
		assertTrue(body.contains("\"status\":\"UNAUTHORIZED\""), action + " should be refused: " + body);
	}

	private void assertAccepted(String action, User target) throws Exception {
		MvcResult result = execute(regionalAdmin, action, target);
		assertEquals(200, result.getResponse().getStatus(), action + ": " + result.getResponse().getContentAsString());
	}

	private MvcResult execute(User actor, String action, User target) throws Exception {
		return perform(action(action).cookie(session(actor)).content("{\"id\":" + target.getId() + "}"));
	}

	private MockHttpServletRequestBuilder action(String action) {
		return post("/api/user/admin/execute/" + action).contentType(MediaType.APPLICATION_JSON);
	}

	private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
		// The service must load the users from the database, as it does in production: entities built
		// here have no company collection initialised.
		em.flush();
		em.clear();
		return mockMvc.perform(request).andReturn();
	}

	private Cookie session(User user) {
		return new Cookie(accessCookieName, tokenService.createAccessToken(user));
	}

	private State stateOf(User user) {
		return new State(roleOf(user), statusOf(user));
	}

	private UserRole roleOf(User user) {
		em.flush();
		em.clear();
		return em.find(User.class, user.getId()).getRole();
	}

	private UserStatus statusOf(User user) {
		em.flush();
		em.clear();
		return em.find(User.class, user.getId()).getStatus();
	}

	private Company company(String name, CompanyStatus status) {
		Company company = new Company();
		company.setName(name);
		company.setStatus(status);
		em.persist(company);
		return company;
	}

	private User user(UserRole role, UserStatus status, Company... companies) {
		User user = new User();
		user.setEmail("regional-" + UUID.randomUUID() + "@test.invalid");
		user.setName("Test");
		user.setSurname("User");
		user.setPassword("not-used-in-this-test");
		user.setRole(role);
		user.setStatus(status);
		em.persist(user);
		for (Company company : companies) {
			enroll(user, company, CompanyUserRole.COMPANY_USER);
		}
		return user;
	}

	private void enroll(User user) {
		enroll(user, company, CompanyUserRole.COMPANY_USER);
	}

	private void enroll(User user, Company company, CompanyUserRole role) {
		CompanyUser enrollment = new CompanyUser();
		enrollment.setUser(user);
		enrollment.setCompany(company);
		enrollment.setRole(role);
		em.persist(enrollment);
		company.getUsers().add(enrollment);
	}
}
