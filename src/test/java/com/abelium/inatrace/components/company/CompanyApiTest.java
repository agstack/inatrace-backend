package com.abelium.inatrace.components.company;

import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Company endpoint group, exercised over real HTTP.
 *
 * <p>Companies are the tenant boundary of the whole application: everything else -- products,
 * facilities, farmers, orders -- hangs off a company, and who may see or change a company's own
 * profile is decided entirely by {@link com.abelium.inatrace.security.utils.PermissionsUtil}
 * rather than by {@code @PreAuthorize}. These tests exercise that enrollment/role logic the same
 * way a browser would: real HTTP requests, real cookies, and companies/users seeded straight into
 * the database so the request thread sees them committed.
 *
 * <p>Every path below starts with {@code /api} for the same reason documented on
 * {@code UserAuthApiTest}: {@code PrefixedApiRequestHandler} prefixes every {@code com.abelium}
 * controller mapping with {@code api} at registration time, so {@code CompanyController}'s
 * {@code /company/**} is actually served at {@code /api/company/**}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CompanyApiTest {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private TestRestTemplate rest;

	@PersistenceContext
	private EntityManager em;

	@Autowired
	private PlatformTransactionManager txManager;

	/** See {@code UserAuthApiTest.useAClientThatDoesNotRetryOn401} for why this is needed. */
	@BeforeEach
	void useAClientThatDoesNotRetryOn401() {
		rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
	}

	// ---------------------------------------------------------------------------- create

	@Test
	@DisplayName("create refuses a caller with no session")
	void createRequiresAuthentication() {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/create",
				Map.of("name", "Nobody's Company"), null);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
				"an anonymous caller should be turned away by the entry point, not by a controller");
	}

	@Test
	@DisplayName("create refuses an ordinary user")
	void createRefusesPlainUser() {
		User user = seedUser(UserRole.USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/create",
				Map.of("name", "A New Company"), accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
				"only a system or regional admin may create a company");
	}

	@Test
	@DisplayName("a system admin can create a company, becoming its company admin")
	void systemAdminCanCreateCompany() throws Exception {
		User admin = seedUser(UserRole.SYSTEM_ADMIN);
		String name = "Acme " + UUID.randomUUID();

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/create",
				Map.of("name", name), accessCookieFor(admin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		Long companyId = createdCompanyId(response);
		assertEquals(CompanyStatus.REGISTERED, statusOf(companyId),
				"a freshly created company should start out REGISTERED, not already ACTIVE");
		assertEquals(CompanyUserRole.COMPANY_ADMIN, roleOf(companyId, admin.getId()),
				"the user who creates a company should be enrolled as its company admin");
	}

	@Test
	@DisplayName("a regional admin can also create a company")
	void regionalAdminCanCreateCompany() {
		User admin = seedUser(UserRole.REGIONAL_ADMIN);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/create",
				Map.of("name", "Regional Co " + UUID.randomUUID()), accessCookieFor(admin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	// ------------------------------------------------------------------------------ list

	@Test
	@DisplayName("list returns only the companies the caller is enrolled in")
	void listReturnsOnlyOwnCompanies() {
		User user = seedUser(UserRole.USER);
		Company own = seedCompany(CompanyStatus.ACTIVE, "Mine " + UUID.randomUUID());
		enroll(own, user, CompanyUserRole.COMPANY_USER);
		Company someoneElses = seedCompany(CompanyStatus.ACTIVE, "Not Mine " + UUID.randomUUID());

		ResponseEntity<String> response = get("/api/company/list", accessCookieFor(user));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("\"id\":" + own.getId()),
				"the caller's own company should be in the list");
		assertFalse(response.getBody().contains("\"id\":" + someoneElses.getId()),
				"a company the caller has no relation to should not be in the list");
	}

	// ------------------------------------------------------------------------ admin/list

	@Test
	@DisplayName("admin/list refuses an ordinary user")
	void adminListRefusesPlainUser() {
		User user = seedUser(UserRole.USER);

		ResponseEntity<String> response = get("/api/company/admin/list", accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	@DisplayName("admin/list refuses a regional admin -- only a system admin may see every company")
	void adminListRefusesRegionalAdmin() {
		User regionalAdmin = seedUser(UserRole.REGIONAL_ADMIN);

		ResponseEntity<String> response = get("/api/company/admin/list", accessCookieFor(regionalAdmin));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	@DisplayName("admin/list lets a system admin see a company they are not enrolled in")
	void adminListShowsAllCompaniesToSystemAdmin() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Unrelated " + UUID.randomUUID());

		ResponseEntity<String> response = get("/api/company/admin/list", accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("\"id\":" + company.getId()),
				"a system admin should see every company, enrolled or not");
	}

	// -------------------------------------------------------------------- profile/{id}

	@Test
	@DisplayName("profile/{id} refuses a user with no relation to the company")
	void getCompanyRefusesUnrelatedUser() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Some Company " + UUID.randomUUID());

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId(), accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	@DisplayName("profile/{id} answers with an unknown-id error for a company that does not exist")
	void getCompanyRejectsUnknownId() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);

		ResponseEntity<String> response = get("/api/company/profile/999999999", accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	@DisplayName("profile/{id} is visible to an enrolled user")
	void getCompanyAllowsEnrolledUser() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "My Company " + UUID.randomUUID());
		enroll(company, user, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId(), accessCookieFor(user));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains(company.getName()));
	}

	@Test
	@DisplayName("profile/{id} is visible to a system admin who is not enrolled")
	void getCompanyAllowsUnrelatedSystemAdmin() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.REGISTERED, "System Admin View " + UUID.randomUUID());

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId(), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	@DisplayName("profile/{id} only offers management actions to a system admin")
	void getCompanyOffersManagementActionsOnlyToSystemAdmin() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		User plainUser = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.REGISTERED, "Actions Co " + UUID.randomUUID());
		enroll(company, plainUser, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> adminView = get("/api/company/profile/" + company.getId(), accessCookieFor(systemAdmin));
		ResponseEntity<String> userView = get("/api/company/profile/" + company.getId(), accessCookieFor(plainUser));

		// Quoted, so the assertion cannot be satisfied by DEACTIVATE_COMPANY, which contains it.
		assertTrue(adminView.getBody().contains("\"ACTIVATE_COMPANY\""),
				"a REGISTERED company should offer the system admin an activate action");
		assertFalse(userView.getBody().contains("ADD_USER_TO_COMPANY"),
				"an enrolled non-admin should not be offered the system admin's management actions");
	}

	// -------------------------------------------------------------- profile/{id}/name

	@Test
	@DisplayName("profile/{id}/name refuses a user with no relation to the company")
	void getCompanyNameRefusesUnrelatedUser() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Name Co " + UUID.randomUUID());

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId() + "/name", accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	@DisplayName("profile/{id}/name is visible to an enrolled user")
	void getCompanyNameAllowsEnrolledUser() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Name Co " + UUID.randomUUID());
		enroll(company, user, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId() + "/name", accessCookieFor(user));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains(company.getName()));
	}

	// ------------------------------------------------------------- profile/{id}/users

	@Test
	@DisplayName("profile/{id}/users refuses a user with no relation to the company")
	void getCompanyUsersRefusesUnrelatedUser() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Users Co " + UUID.randomUUID());

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId() + "/users", accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	@DisplayName("profile/{id}/users lists the company's enrolled users")
	void getCompanyUsersListsEnrolledUsers() {
		User admin = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Users Co " + UUID.randomUUID());
		enroll(company, admin, CompanyUserRole.COMPANY_ADMIN);

		ResponseEntity<String> response = get("/api/company/profile/" + company.getId() + "/users", accessCookieFor(admin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains(admin.getEmail()));
	}

	// -------------------------------------------------------------------------- update

	@Test
	@DisplayName("update lets the company admin change the company's own profile")
	void updateAllowsCompanyAdmin() {
		User admin = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Old Name " + UUID.randomUUID());
		enroll(company, admin, CompanyUserRole.COMPANY_ADMIN);
		String newName = "New Name " + UUID.randomUUID();

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/company/profile",
				Map.of("id", company.getId(), "name", newName), accessCookieFor(admin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(newName, nameOf(company.getId()));
	}

	@Test
	@DisplayName("update refuses an enrolled user who is not the company admin")
	void updateRefusesPlainCompanyUser() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Protected Name " + UUID.randomUUID());
		enroll(company, user, CompanyUserRole.COMPANY_USER);
		String originalName = company.getName();

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/company/profile",
				Map.of("id", company.getId(), "name", "Hijacked"), accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals(originalName, nameOf(company.getId()), "a refused update should not change the name");
	}

	@Test
	@DisplayName("update answers an unrelated user with an invalid-id error, not a permission error")
	void updateRefusesUnrelatedUser() {
		// Unlike the read endpoints, update resolves the company through
		// CompanyQueries.fetchCompany(authUser, id), which for a non-system-admin looks the company
		// up *through* the caller's own memberships. A company the caller has no membership in is
		// therefore indistinguishable from one that does not exist, and both answer INVALID_REQUEST
		// (400) rather than UNAUTHORIZED (403). Refused either way, and it leaks nothing about
		// which companies exist.
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Someone Else's Co " + UUID.randomUUID());
		String originalName = company.getName();

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/company/profile",
				Map.of("id", company.getId(), "name", "Hijacked"), accessCookieFor(user));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(originalName, nameOf(company.getId()), "a refused update should not change the name");
	}

	@Test
	@DisplayName("update refuses even a system admin who is not enrolled in the company")
	void updateRefusesUnenrolledSystemAdmin() {
		// PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin resolves the caller's
		// CompanyUser row before it looks at UserRole, and that lookup throws when there is no row.
		// So the "or system admin" half of its name only reaches system admins who are enrolled --
		// worth pinning, because the name reads as though enrollment were optional for them.
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Admin Not Enrolled " + UUID.randomUUID());
		String originalName = company.getName();

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/company/profile",
				Map.of("id", company.getId(), "name", "Edited By Admin"), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals(originalName, nameOf(company.getId()));
	}

	@Test
	@DisplayName("update lets an enrolled system admin edit even without the company admin role")
	void updateAllowsEnrolledSystemAdminWithoutCompanyAdminRole() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Admin Editable " + UUID.randomUUID());
		enroll(company, systemAdmin, CompanyUserRole.COMPANY_USER);
		String newName = "Edited By Admin " + UUID.randomUUID();

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/company/profile",
				Map.of("id", company.getId(), "name", newName), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode(),
				"a system admin's role should stand in for the COMPANY_ADMIN role this member lacks");
		assertEquals(newName, nameOf(company.getId()));
	}

	// --------------------------------------------------------------- execute/{action}

	@Test
	@DisplayName("execute refuses a caller with no session")
	void executeRequiresAuthentication() {
		Company company = seedCompany(CompanyStatus.REGISTERED, "Anon Target " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), null);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	@DisplayName("a system admin can activate a REGISTERED company")
	void systemAdminCanActivateCompany() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.REGISTERED, "To Activate " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(CompanyStatus.ACTIVE, statusOf(company.getId()));
	}

	@Test
	@DisplayName("activating an already-active company is rejected rather than silently accepted")
	void activatingAnAlreadyActiveCompanyFails() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Already Active " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(CompanyStatus.ACTIVE, statusOf(company.getId()));
	}

	@Test
	@DisplayName("a system admin can deactivate an active company")
	void systemAdminCanDeactivateCompany() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "To Deactivate " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/DEACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(CompanyStatus.DEACTIVATED, statusOf(company.getId()));
	}

	@Test
	@DisplayName("a regional admin enrolled in the company can add a user to it")
	void enrolledRegionalAdminCanAddUser() {
		User regionalAdmin = seedUser(UserRole.REGIONAL_ADMIN);
		User newMember = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Regional Managed " + UUID.randomUUID());
		enroll(company, regionalAdmin, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ADD_USER_TO_COMPANY",
				Map.of("companyId", company.getId(), "userId", newMember.getId()), accessCookieFor(regionalAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(CompanyUserRole.COMPANY_USER, roleOf(company.getId(), newMember.getId()));
	}

	@Test
	@DisplayName("a regional admin cannot deactivate a company, even one they are enrolled in")
	void enrolledRegionalAdminCannotDeactivateCompany() {
		User regionalAdmin = seedUser(UserRole.REGIONAL_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Regional Protected " + UUID.randomUUID());
		enroll(company, regionalAdmin, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/DEACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), accessCookieFor(regionalAdmin));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals(CompanyStatus.ACTIVE, statusOf(company.getId()));
	}

	@Test
	@DisplayName("a regional admin cannot merge a company they are enrolled in into another")
	void enrolledRegionalAdminCannotMergeCompany() {
		User regionalAdmin = seedUser(UserRole.REGIONAL_ADMIN);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Regional Merge Source " + UUID.randomUUID());
		Company other = seedCompany(CompanyStatus.ACTIVE, "Regional Merge Target " + UUID.randomUUID());
		enroll(company, regionalAdmin, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/MERGE_TO_COMPANY",
				Map.of("companyId", company.getId(), "otherCompanyId", other.getId()), accessCookieFor(regionalAdmin));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	@DisplayName("a regional admin who is not enrolled in the company cannot act on it")
	void unrelatedRegionalAdminCannotExecuteAction() {
		User regionalAdmin = seedUser(UserRole.REGIONAL_ADMIN);
		Company company = seedCompany(CompanyStatus.REGISTERED, "Not My Region " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), accessCookieFor(regionalAdmin));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	/*
	 * The next three tests pin CompanyService.executeAction's non-admin branch. It used to read
	 * just `isCompanyAdmin(authUser, c.getId());` -- the boolean return value was discarded, so a
	 * plain USER (enrolled as a non-admin, or with no relation to the company at all) fell through
	 * to the switch and could execute any action, including ACTIVATE_COMPANY, ADD_USER_TO_COMPANY
	 * and REMOVE_USER_FROM_COMPANY, on a company they did not administer. Fixed to throw when
	 * isCompanyAdmin returns false, matching every other permission check in this method.
	 */

	@Test
	@DisplayName("a plain user with no relation to the company cannot execute actions on it")
	void unrelatedPlainUserCannotExecuteAction() {
		User user = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.REGISTERED, "Not Yours " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ACTIVATE_COMPANY",
				Map.of("companyId", company.getId()), accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals(CompanyStatus.REGISTERED, statusOf(company.getId()));
	}

	@Test
	@DisplayName("an enrolled user who is not the company admin cannot execute admin-only actions")
	void enrolledPlainCompanyUserCannotExecuteAdminAction() {
		User user = seedUser(UserRole.USER);
		User target = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Members Only " + UUID.randomUUID());
		enroll(company, user, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ADD_USER_TO_COMPANY",
				Map.of("companyId", company.getId(), "userId", target.getId()), accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertNull(roleOf(company.getId(), target.getId()));
	}

	@Test
	@DisplayName("the actual company admin (not a system or regional admin) can still execute actions")
	void enrolledCompanyAdminCanExecuteAction() {
		User companyAdmin = seedUser(UserRole.USER);
		User target = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Properly Administered " + UUID.randomUUID());
		enroll(company, companyAdmin, CompanyUserRole.COMPANY_ADMIN);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ADD_USER_TO_COMPANY",
				Map.of("companyId", company.getId(), "userId", target.getId()), accessCookieFor(companyAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode(),
				"the fix must not also lock out the company's own admin");
		assertEquals(CompanyUserRole.COMPANY_USER, roleOf(company.getId(), target.getId()));
	}

	@Test
	@DisplayName("adding a user who is already a member is rejected rather than duplicated")
	void addingAnExistingMemberFails() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		User member = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "No Duplicates " + UUID.randomUUID());
		enroll(company, member, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ADD_USER_TO_COMPANY",
				Map.of("companyId", company.getId(), "userId", member.getId()), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	@DisplayName("SET_USER_COMPANY_ROLE changes the role of an existing member")
	void setUserCompanyRoleChangesRole() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		User member = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Promotable " + UUID.randomUUID());
		enroll(company, member, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/SET_USER_COMPANY_ROLE",
				Map.of("companyId", company.getId(), "userId", member.getId(), "companyUserRole", "COMPANY_ADMIN"),
				accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(CompanyUserRole.COMPANY_ADMIN, roleOf(company.getId(), member.getId()));
	}

	@Test
	@DisplayName("SET_USER_COMPANY_ROLE refuses a user who is not a member of the company")
	void setUserCompanyRoleRefusesNonMember() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		User outsider = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "No Such Member " + UUID.randomUUID());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/SET_USER_COMPANY_ROLE",
				Map.of("companyId", company.getId(), "userId", outsider.getId(), "companyUserRole", "COMPANY_ADMIN"),
				accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	@DisplayName("REMOVE_USER_FROM_COMPANY drops the membership")
	void removeUserFromCompanyDropsMembership() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);
		User member = seedUser(UserRole.USER);
		Company company = seedCompany(CompanyStatus.ACTIVE, "Losing A Member " + UUID.randomUUID());
		enroll(company, member, CompanyUserRole.COMPANY_USER);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/REMOVE_USER_FROM_COMPANY",
				Map.of("companyId", company.getId(), "userId", member.getId()), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNull(roleOf(company.getId(), member.getId()));
	}

	@Test
	@DisplayName("execute refuses a company id that does not exist")
	void executeRejectsUnknownCompanyId() {
		User systemAdmin = seedUser(UserRole.SYSTEM_ADMIN);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/company/execute/ACTIVATE_COMPANY",
				Map.of("companyId", 999999999L), accessCookieFor(systemAdmin));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	// ---------------------------------------------------------------------------- helpers

	private TransactionTemplate tx() {
		return new TransactionTemplate(txManager);
	}

	/** Creates a committed, ACTIVE user with a usable password. Emails are unique per call. */
	private User seedUser(UserRole role) {
		String email = "u-" + UUID.randomUUID() + "@test.invalid";
		return tx().execute(s -> {
			User user = new User();
			user.setEmail(email);
			user.setName("Test");
			user.setSurname("User");
			user.setLanguage(Language.EN);
			user.setStatus(UserStatus.ACTIVE);
			user.setRole(role);
			user.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
			em.persist(user);
			em.flush();
			return user;
		});
	}

	private Company seedCompany(CompanyStatus status, String name) {
		return tx().execute(s -> {
			Company company = new Company();
			company.setName(name);
			company.setStatus(status);
			em.persist(company);
			em.flush();
			return company;
		});
	}

	private void enroll(Company company, User user, CompanyUserRole role) {
		tx().executeWithoutResult(s -> {
			CompanyUser companyUser = new CompanyUser();
			companyUser.setCompany(em.find(Company.class, company.getId()));
			companyUser.setUser(em.find(User.class, user.getId()));
			companyUser.setRole(role);
			em.persist(companyUser);
			em.flush();
		});
	}

	private CompanyStatus statusOf(Long companyId) {
		return tx().execute(s -> em.find(Company.class, companyId).getStatus());
	}

	private String nameOf(Long companyId) {
		return tx().execute(s -> em.find(Company.class, companyId).getName());
	}

	/** Null when the user is not (or no longer) enrolled in the company. */
	private CompanyUserRole roleOf(Long companyId, Long userId) {
		return tx().execute(s -> em.createQuery(
						"select cu.role from CompanyUser cu where cu.company.id = :companyId and cu.user.id = :userId",
						CompanyUserRole.class)
				.setParameter("companyId", companyId)
				.setParameter("userId", userId)
				.getResultList()
				.stream()
				.findFirst()
				.orElse(null));
	}

	/** Reads the id ApiBaseEntity hands back from a successful create call. */
	private long createdCompanyId(ResponseEntity<String> response) throws Exception {
		long id = new ObjectMapper().readTree(response.getBody()).path("data").path("id").asLong();
		assertTrue(id > 0, "create should answer with the new company's id: " + response.getBody());
		return id;
	}

	private String accessCookieFor(User user) {
		ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/user/login",
				Map.of("username", user.getEmail(), "password", PASSWORD), null);
		return setCookies(login).stream()
				.filter(c -> c.startsWith("inatrace-accessToken="))
				.map(c -> c.split(";", 2)[0])
				.findFirst()
				.orElseThrow(() -> new AssertionError("login did not return an access cookie"));
	}

	private List<String> setCookies(ResponseEntity<String> response) {
		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		return cookies == null ? List.of() : cookies;
	}

	private ResponseEntity<String> get(String path, String cookie) {
		return exchange(HttpMethod.GET, path, null, cookie);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String cookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}
}
