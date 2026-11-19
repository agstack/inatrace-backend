package com.abelium.inatrace.components.user;

import com.abelium.inatrace.components.common.TokenService;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A regional admin may onboard accounts (confirm the e-mail, activate) but must not change roles.
 *
 * <p>{@code POST /api/user/admin/execute/{action}} is open to SYSTEM_ADMIN and REGIONAL_ADMIN, and the
 * service only refused DEACTIVATE_USER to the regional admin. Every account without a company is in
 * its reach, so a regional admin could register a second account, confirm and activate it, and then
 * promote it with SET_USER_SYSTEM_ADMIN — or demote a system admin it shares a company with. The
 * frontend only offers the role actions to system admins.
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

	private User regionalAdmin;
	private Company company;

	@BeforeEach
	void seed() {
		regionalAdmin = user(UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
		company = new Company();
		company.setName("Regional admin company");
		company.setStatus(CompanyStatus.ACTIVE);
		em.persist(company);
		enroll(regionalAdmin);
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

	// ---------------------------------------------------------------------- helpers

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
		// The service must load the users from the database, as it does in production: entities built
		// here have no company collection initialised.
		em.flush();
		em.clear();
		return mockMvc.perform(post("/api/user/admin/execute/" + action)
						.cookie(new Cookie(accessCookieName, tokenService.createAccessToken(actor)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":" + target.getId() + "}"))
				.andReturn();
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

	private User user(UserRole role, UserStatus status) {
		User user = new User();
		user.setEmail("regional-" + UUID.randomUUID() + "@test.invalid");
		user.setName("Test");
		user.setSurname("User");
		user.setPassword("not-used-in-this-test");
		user.setRole(role);
		user.setStatus(status);
		em.persist(user);
		return user;
	}

	private void enroll(User user) {
		CompanyUser enrollment = new CompanyUser();
		enrollment.setUser(user);
		enrollment.setCompany(company);
		enrollment.setRole(CompanyUserRole.COMPANY_USER);
		em.persist(enrollment);
		company.getUsers().add(enrollment);
	}
}
