package com.abelium.inatrace.components.user;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HTTP coverage for the user endpoints that issue #78 found either unreached or covered only on
 * their refusal path: the regional-admin list, the admin profile read/update and the profile
 * update. The company-scoped {@code GET /api/user/list} is already covered by
 * {@link UserListApiTest}; this class does not duplicate it.
 *
 * <p>The fixture uses two active companies and one registered company. The regional admin belongs
 * to company A; company B, the registered company and the unassigned accounts exercise the scope
 * boundaries. The current service treats an account without a company as readable by any regional
 * admin, but refuses an admin profile update for it; both behaviors are pinned explicitly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegionalAdminUserScopeTest extends AbstractMySqlIntegrationTest {

    private record Account(String name, String surname, String email) {
        List<String> values() {
            return List.of(name, surname, email);
        }
    }

    private static final Account REGIONAL_ADMIN_A =
            new Account("RA78", "REGADMIN78", "ra78@inatrace.test");
    private static final Account COMPANY_A_USER =
            new Account("AU78", "AUSER78", "au78@inatrace.test");
    private static final Account COMPANY_A_UNCONFIRMED =
            new Account("AUC78", "AUNCONF78", "auc78@inatrace.test");
    private static final Account COMPANY_A_CONFIRMED =
            new Account("ACF78", "ACONF78", "acf78@inatrace.test");
    private static final Account COMPANY_A_DEACTIVATED =
            new Account("ADE78", "ADEACT78", "ade78@inatrace.test");
    private static final Account COMPANY_A_ORDINARY =
            new Account("AOR78", "AORD78", "aor78@inatrace.test");
    private static final Account COMPANY_A_SYSTEM_ADMIN =
            new Account("ASA78", "ASYS78", "asa78@inatrace.test");
    private static final Account COMPANY_B_USER =
            new Account("BU78", "BUSER78", "bu78@inatrace.test");
    private static final Account COMPANY_B_UNCONFIRMED =
            new Account("BUC78", "BUNCONF78", "buc78@inatrace.test");
    private static final Account COMPANY_R_USER =
            new Account("RU78", "RUSER78", "ru78@inatrace.test");
    private static final Account UNASSIGNED_ACTIVE =
            new Account("UA78", "UNASSIGNED78", "ua78@inatrace.test");
    private static final Account UNASSIGNED_UNCONFIRMED =
            new Account("UUC78", "UNASSIGNEDCONF78", "uuc78@inatrace.test");
    private static final Account SHARED_AB_USER =
            new Account("SAB78", "SHARED78", "sab78@inatrace.test");

    private static final List<Account> ALL_ACCOUNTS = List.of(
            REGIONAL_ADMIN_A, COMPANY_A_USER, COMPANY_A_UNCONFIRMED, COMPANY_A_CONFIRMED,
            COMPANY_A_DEACTIVATED, COMPANY_A_ORDINARY, COMPANY_A_SYSTEM_ADMIN, COMPANY_B_USER,
            COMPANY_B_UNCONFIRMED, COMPANY_R_USER, UNASSIGNED_ACTIVE, UNASSIGNED_UNCONFIRMED,
            SHARED_AB_USER);

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Cookie regionalAdminSession;
    private Cookie companyAUserSession;
    private Cookie companyAOrdinarySession;
    private Cookie companyASystemAdminSession;

    private Long companyAId;
    private Long regionalAdminId;
    private Long companyAUserId;
    private Long companyBUserId;
    private Long companyRUserId;
    private Long companyBUnconfirmedId;
    private Long unassignedActiveId;
    private Long unassignedUnconfirmedId;
    private Long sharedAbUserId;

    @BeforeEach
    void seed() {
        Company companyA = company("Company A", CompanyStatus.ACTIVE);
        Company companyB = company("Company B", CompanyStatus.ACTIVE);
        Company companyR = company("Company Registered", CompanyStatus.REGISTERED);

        User regionalAdmin = user(REGIONAL_ADMIN_A, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
        User companyAUser = user(COMPANY_A_USER, UserRole.USER, UserStatus.ACTIVE);
        User companyAUnconfirmed = user(COMPANY_A_UNCONFIRMED, UserRole.USER, UserStatus.UNCONFIRMED);
        User companyAConfirmed = user(COMPANY_A_CONFIRMED, UserRole.USER, UserStatus.CONFIRMED_EMAIL);
        User companyADeactivated = user(COMPANY_A_DEACTIVATED, UserRole.USER, UserStatus.DEACTIVATED);
        User companyAOrdinary = user(COMPANY_A_ORDINARY, UserRole.USER, UserStatus.ACTIVE);
        User companyASystemAdmin = user(COMPANY_A_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
        User companyBUser = user(COMPANY_B_USER, UserRole.USER, UserStatus.ACTIVE);
        User companyBUnconfirmed = user(COMPANY_B_UNCONFIRMED, UserRole.USER, UserStatus.UNCONFIRMED);
        User companyRUser = user(COMPANY_R_USER, UserRole.USER, UserStatus.ACTIVE);
        User unassignedActive = user(UNASSIGNED_ACTIVE, UserRole.USER, UserStatus.ACTIVE);
        User unassignedUnconfirmed = user(UNASSIGNED_UNCONFIRMED, UserRole.USER, UserStatus.UNCONFIRMED);
        User sharedAbUser = user(SHARED_AB_USER, UserRole.USER, UserStatus.ACTIVE);

        enroll(regionalAdmin, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyAUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyAUnconfirmed, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyAConfirmed, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyADeactivated, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyAOrdinary, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyASystemAdmin, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyBUser, companyB, CompanyUserRole.COMPANY_USER);
        enroll(companyBUnconfirmed, companyB, CompanyUserRole.COMPANY_USER);
        enroll(companyRUser, companyR, CompanyUserRole.COMPANY_USER);
        enroll(sharedAbUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(sharedAbUser, companyB, CompanyUserRole.COMPANY_USER);

        regionalAdminSession = session(regionalAdmin);
        companyAUserSession = session(companyAUser);
        companyAOrdinarySession = session(companyAOrdinary);
        companyASystemAdminSession = session(companyASystemAdmin);

        companyAId = companyA.getId();
        regionalAdminId = regionalAdmin.getId();
        companyAUserId = companyAUser.getId();
        companyBUserId = companyBUser.getId();
        companyRUserId = companyRUser.getId();
        companyBUnconfirmedId = companyBUnconfirmed.getId();
        unassignedActiveId = unassignedActive.getId();
        unassignedUnconfirmedId = unassignedUnconfirmed.getId();
        sharedAbUserId = sharedAbUser.getId();

        flushAndDetach();
    }

    @Test
    @DisplayName("the regional-admin list contains own-company and unassigned accounts, not another company")
    void regionalAdminListIsScopedToOwnCompaniesAndUnassignedAccounts() throws Exception {
        MvcResult result = perform(get("/api/user/regional-admin/list?limit=1000").cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertContainsAccounts(result, List.of(
                REGIONAL_ADMIN_A, COMPANY_A_USER, COMPANY_A_UNCONFIRMED, COMPANY_A_CONFIRMED,
                COMPANY_A_ORDINARY, COMPANY_A_SYSTEM_ADMIN, UNASSIGNED_ACTIVE, UNASSIGNED_UNCONFIRMED));
        assertDoesNotLeak(result, List.of(
                COMPANY_A_DEACTIVATED, COMPANY_B_USER, COMPANY_B_UNCONFIRMED, COMPANY_R_USER));
        assertEquals(dataOf(result).path("items").size(), dataOf(result).path("count").asInt(),
                "the paginated count must match the returned rows: " + body(result));
    }

    @Test
    @DisplayName("the regional-admin list refuses anonymous and plain users")
    void regionalAdminListRefusesAnonymousAndPlainUsers() throws Exception {
        MvcResult anonymous = perform(get("/api/user/regional-admin/list?limit=1000"));
        assertEquals(401, anonymous.getResponse().getStatus(), body(anonymous));
        assertDoesNotLeak(anonymous, ALL_ACCOUNTS);

        MvcResult ordinary = perform(get("/api/user/regional-admin/list?limit=1000").cookie(companyAOrdinarySession));
        assertEquals(403, ordinary.getResponse().getStatus(), body(ordinary));
        assertDoesNotLeak(ordinary, ALL_ACCOUNTS);
    }

    @Test
    @DisplayName("the regional-admin list filters cannot widen its company scope")
    void regionalAdminListFiltersCannotWidenScope() throws Exception {
        List<String> queries = List.of(
                "?limit=1",
                "?limit=1000&offset=1",
                "?query=" + COMPANY_B_USER.email(),
                "?email=" + COMPANY_B_USER.email(),
                "?surname=" + COMPANY_B_USER.surname(),
                "?status=DEACTIVATED",
                "?role=SYSTEM_ADMIN",
                "?status=ACTIVE&role=USER&limit=1000");

        for (String query : queries) {
            MvcResult result = perform(get("/api/user/regional-admin/list" + query).cookie(regionalAdminSession));
            assertEquals(200, result.getResponse().getStatus(), query + ": " + body(result));
            assertDoesNotLeak(result, List.of(
                    COMPANY_A_DEACTIVATED, COMPANY_B_USER, COMPANY_B_UNCONFIRMED, COMPANY_R_USER), query);
        }
    }

    @Test
    @DisplayName("a regional admin reads a profile in its company and only sees shared company ids")
    void regionalAdminCanReadProfileWithinOwnCompany() throws Exception {
        MvcResult result = perform(get("/api/user/admin/profile/" + companyAUserId).cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(COMPANY_A_USER.email(), dataOf(result).path("email").asText(), body(result));
        assertEquals(List.of(companyAId), companyIdsOf(result));
    }

    @Test
    @DisplayName("a regional admin reading a shared user does not see the other company id")
    void regionalAdminReadingSharedUserFiltersCompanyIds() throws Exception {
        MvcResult result = perform(get("/api/user/admin/profile/" + sharedAbUserId).cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(SHARED_AB_USER.email(), dataOf(result).path("email").asText(), body(result));
        assertEquals(List.of(companyAId), companyIdsOf(result),
                "company B must be filtered from the shared user's company ids: " + body(result));
    }

    @Test
    @DisplayName("a regional admin cannot read a profile from another company, including a registered one")
    void regionalAdminCannotReadProfileOutsideOwnCompanies() throws Exception {
        for (Long targetId : List.of(companyBUserId, companyRUserId)) {
            MvcResult result = perform(get("/api/user/admin/profile/" + targetId).cookie(regionalAdminSession));
            assertEquals(403, result.getResponse().getStatus(), body(result));
            assertDoesNotLeak(result, List.of(COMPANY_B_USER, COMPANY_R_USER));
        }
    }

    @Test
    @DisplayName("a regional admin can read an unassigned profile for onboarding")
    void regionalAdminCanReadUnassignedProfile() throws Exception {
        MvcResult result = perform(get("/api/user/admin/profile/" + unassignedActiveId).cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(UNASSIGNED_ACTIVE.email(), dataOf(result).path("email").asText(), body(result));
    }

    @Test
    @DisplayName("a regional admin updates a profile in its company")
    void regionalAdminCanUpdateProfileWithinOwnCompany() throws Exception {
        MvcResult result = putJson("/api/user/admin/profile",
                Map.of("id", companyAUserId, "name", "Updated A", "surname", "UPDATED_A", "language", "DE"),
                regionalAdminSession);

        assertEquals(200, result.getResponse().getStatus(), body(result));
        User reloaded = reload(companyAUserId);
        assertEquals("Updated A", reloaded.getName());
        assertEquals("UPDATED_A", reloaded.getSurname());
        assertEquals(Language.DE, reloaded.getLanguage());
    }

    @Test
    @DisplayName("a regional admin cannot update a profile from another company")
    void regionalAdminCannotUpdateProfileOutsideOwnCompanies() throws Exception {
        MvcResult result = putJson("/api/user/admin/profile",
                Map.of("id", companyBUserId, "name", "Should Not Apply", "surname", "NO", "language", "DE"),
                regionalAdminSession);

        assertEquals(403, result.getResponse().getStatus(), body(result));
        User reloaded = reload(companyBUserId);
        assertEquals(COMPANY_B_USER.name(), reloaded.getName());
        assertEquals(COMPANY_B_USER.surname(), reloaded.getSurname());
        assertEquals(Language.EN, reloaded.getLanguage());
    }

    @Test
    @DisplayName("the current service refuses a regional admin update of an unassigned profile")
    void regionalAdminCannotUpdateUnassignedProfile() throws Exception {
        MvcResult result = putJson("/api/user/admin/profile",
                Map.of("id", unassignedActiveId, "name", "Should Not Apply", "surname", "NO", "language", "ES"),
                regionalAdminSession);

        assertEquals(403, result.getResponse().getStatus(), body(result));
        User reloaded = reload(unassignedActiveId);
        assertEquals(UNASSIGNED_ACTIVE.name(), reloaded.getName());
        assertEquals(UNASSIGNED_ACTIVE.surname(), reloaded.getSurname());
        assertEquals(Language.EN, reloaded.getLanguage());
    }

    @Test
    @DisplayName("a regional admin cannot execute an action on a user from another company")
    void regionalAdminCannotExecuteOnAnotherCompany() throws Exception {
        for (String action : List.of("CONFIRM_USER_EMAIL", "ACTIVATE_USER")) {
            MvcResult result = execute(action, companyBUnconfirmedId, regionalAdminSession);
            assertEquals(403, result.getResponse().getStatus(), action + ": " + body(result));
            assertEquals(UserStatus.UNCONFIRMED, reload(companyBUnconfirmedId).getStatus(),
                    action + " must not change the target status: " + body(result));
        }
    }

    @Test
    @DisplayName("a regional admin cannot execute an action on itself")
    void regionalAdminCannotExecuteOnSelf() throws Exception {
        MvcResult result = execute("ACTIVATE_USER", regionalAdminId, regionalAdminSession);

        assertEquals(400, result.getResponse().getStatus(), body(result));
        assertTrue(body(result).contains("Cannot change status/role for this user"), body(result));
        assertEquals(UserStatus.ACTIVE, reload(regionalAdminId).getStatus());
    }

    @Test
    @DisplayName("anonymous and plain users cannot execute an admin action")
    void anonymousAndPlainUserCannotExecuteAdminAction() throws Exception {
        MvcResult anonymous = execute("ACTIVATE_USER", unassignedUnconfirmedId, null);
        assertEquals(401, anonymous.getResponse().getStatus(), body(anonymous));
        assertDoesNotLeak(anonymous, ALL_ACCOUNTS);

        MvcResult ordinary = execute("ACTIVATE_USER", unassignedUnconfirmedId, companyAOrdinarySession);
        assertEquals(403, ordinary.getResponse().getStatus(), body(ordinary));
        assertDoesNotLeak(ordinary, ALL_ACCOUNTS);
        assertEquals(UserStatus.UNCONFIRMED, reload(unassignedUnconfirmedId).getStatus());
    }

    @Test
    @DisplayName("a regional admin can confirm and activate an unassigned account")
    void regionalAdminCanOnboardUnassignedAccount() throws Exception {
        MvcResult confirm = execute("CONFIRM_USER_EMAIL", unassignedUnconfirmedId, regionalAdminSession);
        assertEquals(200, confirm.getResponse().getStatus(), body(confirm));
        assertEquals(UserStatus.CONFIRMED_EMAIL, reload(unassignedUnconfirmedId).getStatus());

        MvcResult activate = execute("ACTIVATE_USER", unassignedUnconfirmedId, regionalAdminSession);
        assertEquals(200, activate.getResponse().getStatus(), body(activate));
        assertEquals(UserStatus.ACTIVE, reload(unassignedUnconfirmedId).getStatus());
    }

    @Test
    @DisplayName("the admin profile endpoints refuse anonymous and plain users")
    void adminProfileEndpointsRefuseAnonymousAndPlainUsers() throws Exception {
        String path = "/api/user/admin/profile/" + companyAUserId;

        MvcResult anonymousRead = perform(get(path));
        assertEquals(401, anonymousRead.getResponse().getStatus(), body(anonymousRead));
        assertDoesNotLeak(anonymousRead, ALL_ACCOUNTS);

        MvcResult ordinaryRead = perform(get(path).cookie(companyAOrdinarySession));
        assertEquals(403, ordinaryRead.getResponse().getStatus(), body(ordinaryRead));
        assertDoesNotLeak(ordinaryRead, ALL_ACCOUNTS);

        MvcResult anonymousUpdate = putJson("/api/user/admin/profile",
                Map.of("id", companyAUserId, "name", "Anonymous", "surname", "NO", "language", "EN"), null);
        assertEquals(401, anonymousUpdate.getResponse().getStatus(), body(anonymousUpdate));

        MvcResult ordinaryUpdate = putJson("/api/user/admin/profile",
                Map.of("id", companyAUserId, "name", "Ordinary", "surname", "NO", "language", "EN"),
                companyAOrdinarySession);
        assertEquals(403, ordinaryUpdate.getResponse().getStatus(), body(ordinaryUpdate));

        User reloaded = reload(companyAUserId);
        assertEquals(COMPANY_A_USER.name(), reloaded.getName());
        assertEquals(COMPANY_A_USER.surname(), reloaded.getSurname());
        assertEquals(Language.EN, reloaded.getLanguage());
    }

    @Test
    @DisplayName("a system admin can read and update a profile from another company")
    void systemAdminCanReadAndUpdateAnotherCompanyProfile() throws Exception {
        MvcResult read = perform(get("/api/user/admin/profile/" + companyBUserId).cookie(companyASystemAdminSession));
        assertEquals(200, read.getResponse().getStatus(), body(read));
        assertEquals(COMPANY_B_USER.email(), dataOf(read).path("email").asText(), body(read));

        MvcResult update = putJson("/api/user/admin/profile",
                Map.of("id", companyBUserId, "name", "System Updated", "surname", "SYSTEM_UPDATED", "language", "DE"),
                companyASystemAdminSession);
        assertEquals(200, update.getResponse().getStatus(), body(update));
        assertEquals("System Updated", reload(companyBUserId).getName());
        assertEquals(Language.DE, reload(companyBUserId).getLanguage());
    }

    @Test
    @DisplayName("an active user updates its own profile and an anonymous caller is refused")
    void activeUserCanUpdateOwnProfile() throws Exception {
        MvcResult result = putJson("/api/user/profile",
                Map.of("name", "Updated Self", "surname", "UPDATED_SELF", "language", "ES"),
                companyAUserSession);

        assertEquals(200, result.getResponse().getStatus(), body(result));
        User reloaded = reload(companyAUserId);
        assertEquals("Updated Self", reloaded.getName());
        assertEquals("UPDATED_SELF", reloaded.getSurname());
        assertEquals(Language.ES, reloaded.getLanguage());

        MvcResult anonymous = putJson("/api/user/profile",
                Map.of("name", "Anonymous", "surname", "ANONYMOUS", "language", "EN"), null);
        assertEquals(401, anonymous.getResponse().getStatus(), body(anonymous));
        assertEquals("Updated Self", reload(companyAUserId).getName());
    }

    private List<Long> companyIdsOf(MvcResult result) throws Exception {
        List<Long> ids = new ArrayList<>();
        dataOf(result).path("companyIds").forEach(node -> ids.add(node.asLong()));
        return ids;
    }

    private MvcResult execute(String action, Long targetId, Cookie session) throws Exception {
        return postJson("/api/user/admin/execute/" + action, Map.of("id", targetId), session);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        flushAndDetach();
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult putJson(String path, Object body, Cookie session) throws Exception {
        flushAndDetach();
        MockHttpServletRequestBuilder request = put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body));
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult postJson(String path, Object body, Cookie session) throws Exception {
        flushAndDetach();
        MockHttpServletRequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body));
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private User reload(Long id) {
        em.flush();
        em.clear();
        return em.find(User.class, id);
    }

    private void flushAndDetach() {
        em.flush();
        em.clear();
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(body(result)).path("data");
    }

    private void assertContainsAccounts(MvcResult result, List<Account> accounts) throws Exception {
        String body = body(result);
        for (Account account : accounts) {
            for (String value : account.values()) {
                assertTrue(body.contains(value),
                        "the response should have contained " + value + ": " + body);
            }
        }
    }

    private void assertDoesNotLeak(MvcResult result, List<Account> accounts) throws Exception {
        assertDoesNotLeak(result, accounts, "");
    }

    private void assertDoesNotLeak(MvcResult result, List<Account> accounts, String context) throws Exception {
        String body = body(result);
        for (Account account : accounts) {
            for (String value : account.values()) {
                assertFalse(body.contains(value),
                        context + " the response disclosed " + value
                                + " to a caller who should not see it: " + body);
            }
        }
    }

    private Cookie session(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }

    private Company company(String name, CompanyStatus status) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(status);
        em.persist(company);
        return company;
    }

    private User user(Account account, UserRole role, UserStatus status) {
        User user = new User();
        user.setName(account.name());
        user.setSurname(account.surname());
        user.setEmail(account.email());
        user.setPassword("not-used-in-this-test");
        user.setLanguage(Language.EN);
        user.setRole(role);
        user.setStatus(status);
        em.persist(user);
        return user;
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
