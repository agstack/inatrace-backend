package com.abelium.inatrace.components.user;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.user.types.UserAction;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * <p>The regional admin is also a member of a second active company (C), of a registered company (S)
 * and of a deactivated one (D). The list reads its active companies only; the profile endpoints also
 * accept the registered one; the deactivated one opens nothing.
 *
 * <p><b>Transactions.</b> MockMvc runs the request on the test's thread, inside the test's own
 * transaction, which is rolled back afterwards. The service therefore reads the fixture before it
 * is committed; a separately committed request is not exercised here.
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

    private static final Account COMPANY_A_COMPANY_ADMIN =
            new Account("ACA78", "ACOADM78", "aca78@inatrace.test");
    private static final Account SHARED_AC_USER =
            new Account("SAC78", "SHAREDAC78", "sac78@inatrace.test");
    private static final Account COMPANY_S_USER =
            new Account("SU78", "SUSER78", "su78@inatrace.test");
    private static final Account COMPANY_D_USER =
            new Account("DU78", "DUSER78", "du78@inatrace.test");
    private static final Account UNASSIGNED_CONFIRMED =
            new Account("UCF78", "UNASSIGNEDCF78", "ucf78@inatrace.test");
    private static final Account UNASSIGNED_DEACTIVATED =
            new Account("UDE78", "UNASSIGNEDDE78", "ude78@inatrace.test");
    private static final Account UNASSIGNED_SYSTEM_ADMIN =
            new Account("USA78", "UNASSIGNEDSA78", "usa78@inatrace.test");
    private static final Account UNASSIGNED_REGIONAL_ADMIN =
            new Account("URG78", "UNASSIGNEDRG78", "urg78@inatrace.test");

    private static final List<Account> ALL_ACCOUNTS = List.of(
            REGIONAL_ADMIN_A, COMPANY_A_USER, COMPANY_A_UNCONFIRMED, COMPANY_A_CONFIRMED,
            COMPANY_A_DEACTIVATED, COMPANY_A_ORDINARY, COMPANY_A_SYSTEM_ADMIN, COMPANY_B_USER,
            COMPANY_B_UNCONFIRMED, COMPANY_R_USER, UNASSIGNED_ACTIVE, UNASSIGNED_UNCONFIRMED,
            SHARED_AB_USER, COMPANY_A_COMPANY_ADMIN, SHARED_AC_USER, COMPANY_S_USER, COMPANY_D_USER,
            UNASSIGNED_CONFIRMED, UNASSIGNED_DEACTIVATED, UNASSIGNED_SYSTEM_ADMIN, UNASSIGNED_REGIONAL_ADMIN);

    /** Every account without a company that is not DEACTIVATED: visible to any regional admin. */
    private static final List<Account> VISIBLE_UNASSIGNED = List.of(
            UNASSIGNED_ACTIVE, UNASSIGNED_UNCONFIRMED, UNASSIGNED_CONFIRMED,
            UNASSIGNED_SYSTEM_ADMIN, UNASSIGNED_REGIONAL_ADMIN);

    /** Every fixture account the list shows to the regional admin of A, C, S and D. */
    private static final List<Account> VISIBLE_TO_REGIONAL_ADMIN = List.of(
            REGIONAL_ADMIN_A, COMPANY_A_USER, COMPANY_A_UNCONFIRMED, COMPANY_A_CONFIRMED, COMPANY_A_ORDINARY,
            COMPANY_A_SYSTEM_ADMIN, COMPANY_A_COMPANY_ADMIN, SHARED_AB_USER, SHARED_AC_USER,
            UNASSIGNED_ACTIVE, UNASSIGNED_UNCONFIRMED, UNASSIGNED_CONFIRMED,
            UNASSIGNED_SYSTEM_ADMIN, UNASSIGNED_REGIONAL_ADMIN);

    /** Matches every fixture account and none that another class left in the shared database. */
    private static final String FIXTURE = "query=78@inatrace.test";

    /** What the list must not show to the regional admin of A, C, S and D. */
    private static final List<Account> HIDDEN_FROM_REGIONAL_ADMIN = List.of(
            COMPANY_A_DEACTIVATED, COMPANY_B_USER, COMPANY_B_UNCONFIRMED, COMPANY_R_USER, COMPANY_S_USER,
            COMPANY_D_USER, UNASSIGNED_DEACTIVATED);

    private static final Set<UserAction> VIEW_AND_UPDATE =
            EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE);

    private record Snapshot(String name, String surname, String email, Language language,
                            UserRole role, UserStatus status) {
    }

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
    private Cookie companyACompanyAdminSession;
    private Cookie unassignedSystemAdminSession;
    private Cookie unassignedRegionalAdminSession;

    private Long companyAId;
    private Long companyBId;
    private Long companySId;
    private Long regionalAdminId;
    private Long companyAUserId;
    private Long companyBUserId;
    private Long companyRUserId;
    private Long companyBUnconfirmedId;
    private Long unassignedActiveId;
    private Long unassignedUnconfirmedId;
    private Long sharedAbUserId;
    private Long sharedAcUserId;
    private Long companyAConfirmedId;
    private Long companyADeactivatedId;
    private Long companyASystemAdminId;
    private Long companyACompanyAdminId;
    private Long companySUserId;
    private Long companyDUserId;
    private Long unassignedConfirmedId;
    private Long unassignedDeactivatedId;
    private Long unassignedSystemAdminId;

    @BeforeEach
    void seed() {
        Company companyA = company("Company A", CompanyStatus.ACTIVE);
        Company companyB = company("Company B", CompanyStatus.ACTIVE);
        Company companyR = company("Company Registered", CompanyStatus.REGISTERED);
        Company companyC = company("Company C", CompanyStatus.ACTIVE);
        Company companyS = company("Company Own Registered", CompanyStatus.REGISTERED);
        Company companyD = company("Company Own Deactivated", CompanyStatus.DEACTIVATED);

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
        User companyACompanyAdmin = user(COMPANY_A_COMPANY_ADMIN, UserRole.USER, UserStatus.ACTIVE);
        User sharedAcUser = user(SHARED_AC_USER, UserRole.USER, UserStatus.ACTIVE);
        User companySUser = user(COMPANY_S_USER, UserRole.USER, UserStatus.ACTIVE);
        User companyDUser = user(COMPANY_D_USER, UserRole.USER, UserStatus.ACTIVE);
        User unassignedConfirmed = user(UNASSIGNED_CONFIRMED, UserRole.USER, UserStatus.CONFIRMED_EMAIL);
        User unassignedDeactivated = user(UNASSIGNED_DEACTIVATED, UserRole.USER, UserStatus.DEACTIVATED);
        User unassignedSystemAdmin = user(UNASSIGNED_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
        User unassignedRegionalAdmin = user(UNASSIGNED_REGIONAL_ADMIN, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);

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
        enroll(sharedAbUser, companyB, CompanyUserRole.COMPANY_ADMIN);
        enroll(regionalAdmin, companyC, CompanyUserRole.COMPANY_USER);
        enroll(regionalAdmin, companyS, CompanyUserRole.COMPANY_USER);
        enroll(regionalAdmin, companyD, CompanyUserRole.COMPANY_USER);
        enroll(companyACompanyAdmin, companyA, CompanyUserRole.COMPANY_ADMIN);
        enroll(sharedAcUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(sharedAcUser, companyC, CompanyUserRole.COMPANY_USER);
        enroll(companySUser, companyS, CompanyUserRole.COMPANY_USER);
        enroll(companyDUser, companyD, CompanyUserRole.COMPANY_USER);

        regionalAdminSession = session(regionalAdmin);
        companyAUserSession = session(companyAUser);
        companyAOrdinarySession = session(companyAOrdinary);
        companyASystemAdminSession = session(companyASystemAdmin);
        companyACompanyAdminSession = session(companyACompanyAdmin);
        unassignedSystemAdminSession = session(unassignedSystemAdmin);
        unassignedRegionalAdminSession = session(unassignedRegionalAdmin);

        companyAId = companyA.getId();
        companyBId = companyB.getId();
        companySId = companyS.getId();
        regionalAdminId = regionalAdmin.getId();
        companyAUserId = companyAUser.getId();
        companyBUserId = companyBUser.getId();
        companyRUserId = companyRUser.getId();
        companyBUnconfirmedId = companyBUnconfirmed.getId();
        unassignedActiveId = unassignedActive.getId();
        unassignedUnconfirmedId = unassignedUnconfirmed.getId();
        sharedAbUserId = sharedAbUser.getId();
        sharedAcUserId = sharedAcUser.getId();
        companyAConfirmedId = companyAConfirmed.getId();
        companyADeactivatedId = companyADeactivated.getId();
        companyASystemAdminId = companyASystemAdmin.getId();
        companyACompanyAdminId = companyACompanyAdmin.getId();
        companySUserId = companySUser.getId();
        companyDUserId = companyDUser.getId();
        unassignedConfirmedId = unassignedConfirmed.getId();
        unassignedDeactivatedId = unassignedDeactivated.getId();
        unassignedSystemAdminId = unassignedSystemAdmin.getId();

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

    @Test
    @DisplayName("the regional-admin list refuses company admins and system admins")
    void regionalAdminListRefusesCompanyAndSystemAdmins() throws Exception {
        for (Cookie caller : List.of(companyACompanyAdminSession, companyASystemAdminSession,
                unassignedSystemAdminSession)) {
            MvcResult result = perform(get("/api/user/regional-admin/list?limit=1000").cookie(caller));

            assertEquals(403, result.getResponse().getStatus(), body(result));
            assertTrue(body(result).contains("\"status\":\"UNAUTHORIZED\""), body(result));
            assertDoesNotLeak(result, ALL_ACCOUNTS);
        }
    }

    @Test
    @DisplayName("the regional-admin list ignores its registered and deactivated companies and deactivated accounts")
    void regionalAdminListIgnoresItsNonActiveCompanies() throws Exception {
        MvcResult result = perform(get("/api/user/regional-admin/list?limit=1000").cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertDoesNotLeak(result, HIDDEN_FROM_REGIONAL_ADMIN);
        assertContainsAccounts(result, List.of(SHARED_AB_USER, SHARED_AC_USER));
        assertContainsAccounts(result, VISIBLE_UNASSIGNED);
    }

    @Test
    @DisplayName("a user of two of the regional admin's companies is listed once")
    void regionalAdminListIsDeduplicated() throws Exception {
        MvcResult result = perform(get("/api/user/regional-admin/list?limit=1000").cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        List<Long> ids = new ArrayList<>();
        dataOf(result).path("items").forEach(item -> ids.add(item.path("id").asLong()));
        assertEquals(1, ids.stream().filter(sharedAcUserId::equals).count(), body(result));
        assertEquals(new HashSet<>(ids).size(), ids.size(), "no row may be duplicated: " + ids);
        assertEquals(ids.size(), dataOf(result).path("count").asInt(), body(result));
    }

    @Test
    @DisplayName("a regional admin without a company lists only the unassigned accounts")
    void regionalAdminWithoutACompanySeesOnlyUnassignedAccounts() throws Exception {
        MvcResult result = perform(get("/api/user/regional-admin/list?limit=1000")
                .cookie(unassignedRegionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertContainsAccounts(result, VISIBLE_UNASSIGNED);
        List<Account> others = new ArrayList<>(ALL_ACCOUNTS);
        others.removeAll(VISIBLE_UNASSIGNED);
        assertDoesNotLeak(result, others);
    }

    @Test
    @DisplayName("sorting, filtering and counting the regional-admin list stay in its scope")
    void regionalAdminListSortsAndCountsStayInScope() throws Exception {
        for (String query : List.of(
                "?sortBy=email&sort=ASC&limit=1000",
                "?sortBy=surname&sort=DESC&limit=1000",
                "?sortBy=an-unknown-column&sort=ASC&limit=1000",
                "?role=REGIONAL_ADMIN&limit=1000",
                "?query=" + COMPANY_S_USER.email(),
                "?email=" + COMPANY_D_USER.email(),
                "?requestType=FETCH&limit=1000")) {
            MvcResult result = perform(get("/api/user/regional-admin/list" + query).cookie(regionalAdminSession));
            assertEquals(200, result.getResponse().getStatus(), query + ": " + body(result));
            assertDoesNotLeak(result, HIDDEN_FROM_REGIONAL_ADMIN, query);
        }

        MvcResult deactivated = perform(get("/api/user/regional-admin/list?status=DEACTIVATED&limit=1000")
                .cookie(regionalAdminSession));
        assertEquals(0, dataOf(deactivated).path("count").asInt(), body(deactivated));

        MvcResult listed = perform(get("/api/user/regional-admin/list?limit=1000").cookie(regionalAdminSession));
        MvcResult count = perform(get("/api/user/regional-admin/list?requestType=COUNT").cookie(regionalAdminSession));
        assertEquals(200, count.getResponse().getStatus(), body(count));
        assertEquals(dataOf(listed).path("items").size(), dataOf(count).path("count").asInt(), body(count));
    }

    @Test
    @DisplayName("the regional-admin list shows exactly its scope, and its filters exactly the matching accounts")
    void regionalAdminListFiltersReturnExactlyTheMatchingAccounts() throws Exception {
        assertFilter(FIXTURE, VISIBLE_TO_REGIONAL_ADMIN);
        assertFilter(FIXTURE + "&role=SYSTEM_ADMIN", List.of(COMPANY_A_SYSTEM_ADMIN, UNASSIGNED_SYSTEM_ADMIN));
        assertFilter(FIXTURE + "&role=REGIONAL_ADMIN", List.of(REGIONAL_ADMIN_A, UNASSIGNED_REGIONAL_ADMIN));
        assertFilter(FIXTURE + "&status=UNCONFIRMED", List.of(COMPANY_A_UNCONFIRMED, UNASSIGNED_UNCONFIRMED));
        assertFilter(FIXTURE + "&status=CONFIRMED_EMAIL", List.of(COMPANY_A_CONFIRMED, UNASSIGNED_CONFIRMED));
        assertFilter("status=DEACTIVATED", List.of());

        assertFilter("query=" + COMPANY_A_USER.name(), List.of(COMPANY_A_USER));
        assertFilter("query=" + COMPANY_A_USER.surname(), List.of(COMPANY_A_USER));
        assertFilter("email=" + COMPANY_A_USER.email(), List.of(COMPANY_A_USER));
        assertFilter("surname=" + COMPANY_A_USER.surname(), List.of(COMPANY_A_USER));

        assertFilter("query=" + COMPANY_B_USER.email(), List.of());
        assertFilter("email=" + COMPANY_B_USER.email(), List.of());
        assertFilter("surname=" + COMPANY_B_USER.surname(), List.of());
    }

    @Test
    @DisplayName("the admin profile endpoints refuse a company admin, even for its own company")
    void adminProfileEndpointsRefuseCompanyAdmins() throws Exception {
        Snapshot before = snapshot(companyAUserId);

        MvcResult read = perform(get("/api/user/admin/profile/" + companyAUserId).cookie(companyACompanyAdminSession));
        assertEquals(403, read.getResponse().getStatus(), body(read));
        assertDoesNotLeak(read, ALL_ACCOUNTS);

        MvcResult update = putJson("/api/user/admin/profile", update(companyAUserId, "Hijacked"),
                companyACompanyAdminSession);
        assertEquals(403, update.getResponse().getStatus(), body(update));
        assertEquals(before, snapshot(companyAUserId));
    }

    @Test
    @DisplayName("a system admin reads any profile with every active or registered company of the user")
    void systemAdminReadsAnyProfile() throws Exception {
        for (Map.Entry<Long, Account> target : Map.of(
                companyDUserId, COMPANY_D_USER,
                unassignedActiveId, UNASSIGNED_ACTIVE,
                unassignedSystemAdminId, UNASSIGNED_SYSTEM_ADMIN).entrySet()) {
            MvcResult result = perform(get("/api/user/admin/profile/" + target.getKey())
                    .cookie(unassignedSystemAdminSession));
            assertEquals(200, result.getResponse().getStatus(), body(result));
            assertContainsAccounts(result, List.of(target.getValue()));
        }

        MvcResult shared = perform(get("/api/user/admin/profile/" + sharedAbUserId)
                .cookie(unassignedSystemAdminSession));
        assertEquals(Set.of(companyAId, companyBId), new HashSet<>(companyIdsOf(shared)), body(shared));
        assertEquals(List.of(companyBId), idsOf(shared, "companyIdsAdmin"), body(shared));
    }

    @Test
    @DisplayName("a regional admin reads users of its registered company and a system admin of a shared one")
    void regionalAdminReadsUsersOfItsRegisteredCompany() throws Exception {
        MvcResult registered = perform(get("/api/user/admin/profile/" + companySUserId).cookie(regionalAdminSession));
        assertEquals(200, registered.getResponse().getStatus(), body(registered));
        assertContainsAccounts(registered, List.of(COMPANY_S_USER));
        assertEquals(List.of(companySId), companyIdsOf(registered));

        for (Map.Entry<Long, Account> admin : Map.of(
                companyASystemAdminId, COMPANY_A_SYSTEM_ADMIN,
                unassignedSystemAdminId, UNASSIGNED_SYSTEM_ADMIN).entrySet()) {
            MvcResult result = perform(get("/api/user/admin/profile/" + admin.getKey()).cookie(regionalAdminSession));
            assertEquals(200, result.getResponse().getStatus(), body(result));
            assertContainsAccounts(result, List.of(admin.getValue()));
        }
    }

    @Test
    @DisplayName("a deactivated company the regional admin belongs to opens neither the profile nor its update")
    void regionalAdminHasNoAccessThroughADeactivatedCompany() throws Exception {
        Snapshot before = snapshot(companyDUserId);

        MvcResult read = perform(get("/api/user/admin/profile/" + companyDUserId).cookie(regionalAdminSession));
        assertEquals(403, read.getResponse().getStatus(), body(read));
        assertDoesNotLeak(read, ALL_ACCOUNTS);

        MvcResult update = putJson("/api/user/admin/profile", update(companyDUserId, "Hijacked"), regionalAdminSession);
        assertEquals(403, update.getResponse().getStatus(), body(update));
        assertEquals(before, snapshot(companyDUserId));
    }

    @Test
    @DisplayName("a regional admin does not see that a shared user administers a foreign company")
    void regionalAdminViewHidesForeignCompanyAdminRole() throws Exception {
        MvcResult result = perform(get("/api/user/admin/profile/" + sharedAbUserId).cookie(regionalAdminSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(List.of(), idsOf(result, "companyIdsAdmin"), body(result));
    }

    @Test
    @DisplayName("a system admin is offered the status and role actions that fit the user")
    void systemAdminActionsFollowTheUsersStatusAndRole() throws Exception {
        assertActions(unassignedSystemAdminSession, companyAUserId,
                EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE, UserAction.DEACTIVATE_USER,
                        UserAction.SET_USER_SYSTEM_ADMIN, UserAction.SET_USER_REGIONAL_ADMIN));
        assertActions(unassignedSystemAdminSession, companyASystemAdminId,
                EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE, UserAction.DEACTIVATE_USER,
                        UserAction.UNSET_USER_SYSTEM_ADMIN));
        assertActions(unassignedSystemAdminSession, regionalAdminId,
                EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE, UserAction.DEACTIVATE_USER,
                        UserAction.UNSET_USER_REGIONAL_ADMIN));
        assertActions(unassignedSystemAdminSession, unassignedConfirmedId,
                EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE, UserAction.DEACTIVATE_USER,
                        UserAction.ACTIVATE_USER));
        assertActions(unassignedSystemAdminSession, unassignedDeactivatedId,
                EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE, UserAction.ACTIVATE_USER));
        assertActions(unassignedSystemAdminSession, unassignedUnconfirmedId, VIEW_AND_UPDATE);
        assertActions(unassignedSystemAdminSession, unassignedSystemAdminId, VIEW_AND_UPDATE);
    }

    @Test
    @DisplayName("a regional admin is never offered deactivation or a role change")
    void regionalAdminActionsAreLimitedToOnboarding() throws Exception {
        Set<UserAction> withActivate = EnumSet.of(UserAction.VIEW_USER_PROFILE, UserAction.UPDATE_USER_PROFILE,
                UserAction.ACTIVATE_USER);

        assertActions(regionalAdminSession, companyAUserId, VIEW_AND_UPDATE);
        assertActions(regionalAdminSession, companyASystemAdminId, VIEW_AND_UPDATE);
        assertActions(regionalAdminSession, companyAConfirmedId, withActivate);
        assertActions(regionalAdminSession, companyADeactivatedId, withActivate);
        assertActions(regionalAdminSession, regionalAdminId, VIEW_AND_UPDATE);
    }

    @Test
    @DisplayName("reading an unknown or malformed user id is a client error")
    void readingAnUnknownOrMalformedIdIsAClientError() throws Exception {
        for (Cookie caller : List.of(unassignedSystemAdminSession, regionalAdminSession)) {
            for (String id : List.of(String.valueOf(Long.MAX_VALUE), "not-a-number")) {
                MvcResult result = perform(get("/api/user/admin/profile/" + id).cookie(caller));
                assertEquals(400, result.getResponse().getStatus(), id + ": " + body(result));
            }
        }
    }

    @Test
    @DisplayName("a system admin edits any user, changing only name, surname and language")
    void systemAdminEditsAnyUser() throws Exception {
        for (Long target : List.of(companyDUserId, unassignedActiveId, companyASystemAdminId)) {
            assertEdited(unassignedSystemAdminSession, target, "EditedBySysAdmin");
        }
    }

    @Test
    @DisplayName("a regional admin edits users of its registered company, and itself")
    void regionalAdminEditsUsersOfItsRegisteredCompanyAndItself() throws Exception {
        for (Long target : List.of(companySUserId, regionalAdminId)) {
            assertEdited(regionalAdminSession, target, "EditedByRegAdmin");
        }
    }

    @Test
    @DisplayName("the current service lets a regional admin edit a system admin of a shared company")
    void regionalAdminEditsASystemAdminOfASharedCompany() throws Exception {
        assertEdited(regionalAdminSession, companyASystemAdminId, "EditedSysAdmin");
    }

    @Test
    @DisplayName("an email, role or status in the admin profile body is ignored")
    void adminProfileUpdateIgnoresEmailRoleAndStatus() throws Exception {
        assertExtrasIgnored(unassignedSystemAdminSession, companyAUserId);
        assertExtrasIgnored(regionalAdminSession, companyAUserId);
        assertExtrasIgnored(regionalAdminSession, regionalAdminId);
    }

    @Test
    @DisplayName("updating a missing or unknown user id is a client error")
    void updatingAMissingOrUnknownIdIsAClientError() throws Exception {
        Map<String, Object> withoutId = update(null, "NoTarget");
        withoutId.remove("id");

        for (Map<String, Object> request : List.of(withoutId, update(Long.MAX_VALUE, "NoTarget"))) {
            MvcResult systemAdmin = putJson("/api/user/admin/profile", request, unassignedSystemAdminSession);
            assertEquals(400, systemAdmin.getResponse().getStatus(), body(systemAdmin));

            MvcResult regionalAdmin = putJson("/api/user/admin/profile", request, regionalAdminSession);
            assertEquals(403, regionalAdmin.getResponse().getStatus(),
                    "a user with no company in common is refused before it is looked up: " + body(regionalAdmin));
        }
    }

    @Test
    @DisplayName("a name or surname over the length limit is refused on both profile updates")
    void overLongNamesAreRejected() throws Exception {
        Snapshot before = snapshot(companyAUserId);

        for (Map<String, Object> request : List.of(
                Map.<String, Object>of("id", companyAUserId, "name", "N".repeat(Lengths.NAME + 1),
                        "surname", "OK", "language", "DE"),
                Map.<String, Object>of("id", companyAUserId, "name", "Ok",
                        "surname", "S".repeat(Lengths.SURNAME + 1), "language", "DE"))) {
            MvcResult admin = putJson("/api/user/admin/profile", request, unassignedSystemAdminSession);
            assertEquals(400, admin.getResponse().getStatus(), body(admin));
            MvcResult own = putJson("/api/user/profile", request, companyAUserSession);
            assertEquals(400, own.getResponse().getStatus(), body(own));
            assertEquals(before, snapshot(companyAUserId));
        }
    }

    @Test
    @DisplayName("an unknown language or an unreadable body is refused and nothing is written")
    void malformedProfileBodiesAreRejected() throws Exception {
        Snapshot before = snapshot(companyAUserId);

        for (String content : List.of(
                "{\"name\":\"Renamed\",\"surname\":\"RENAMED\",\"language\":\"XX\"}", "{\"name\":", "")) {
            MvcResult result = perform(put("/api/user/profile").cookie(companyAUserSession)
                    .contentType(MediaType.APPLICATION_JSON).content(content));
            assertEquals(400, result.getResponse().getStatus(), content + ": " + body(result));
            assertEquals(before, snapshot(companyAUserId));
        }
    }

    @Test
    @DisplayName("the own-profile update ignores an id, email, role or status in the body")
    void ownProfileUpdateIgnoresFieldsTheCallerDoesNotOwn() throws Exception {
        Map<Long, Cookie> callers = Map.of(
                companyAUserId, companyAUserSession,
                companyACompanyAdminId, companyACompanyAdminSession,
                regionalAdminId, regionalAdminSession,
                unassignedSystemAdminId, unassignedSystemAdminSession);

        for (Map.Entry<Long, Cookie> caller : callers.entrySet()) {
            Snapshot before = snapshot(caller.getKey());
            Snapshot otherBefore = snapshot(companyAConfirmedId);
            Map<String, Object> request = withExtras(update(companyAConfirmedId, "Renamed"), before);

            MvcResult result = putJson("/api/user/profile", request, caller.getValue());

            assertEquals(200, result.getResponse().getStatus(), body(result));
            assertEquals(otherBefore, snapshot(companyAConfirmedId), "the id in the body must not choose the target");
            assertEquals(edited(before, "Renamed"), snapshot(caller.getKey()));
        }
    }

    @Test
    @DisplayName("no fixture name, surname or email is a substring of another")
    void accountValuesAreMutuallyDistinguishable() {
        List<String> values = ALL_ACCOUNTS.stream().flatMap(account -> account.values().stream()).toList();
        for (String value : values) {
            assertEquals(1, values.stream().filter(other -> other.contains(value)).count(),
                    "\"" + value + "\" is contained in another value, so an assertion about either proves nothing");
        }
    }

    private void assertEdited(Cookie caller, Long target, String name) throws Exception {
        Snapshot before = snapshot(target);

        MvcResult result = putJson("/api/user/admin/profile", update(target, name), caller);

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(edited(before, name), snapshot(target), "only name, surname and language may change");
    }

    private void assertExtrasIgnored(Cookie caller, Long target) throws Exception {
        Snapshot before = snapshot(target);

        MvcResult result = putJson("/api/user/admin/profile", withExtras(update(target, "WithExtras"), before), caller);

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(edited(before, "WithExtras"), snapshot(target));
    }

    private void assertActions(Cookie caller, Long target, Set<UserAction> expected) throws Exception {
        MvcResult result = perform(get("/api/user/admin/profile/" + target).cookie(caller));
        assertEquals(200, result.getResponse().getStatus(), body(result));

        Set<UserAction> actions = EnumSet.noneOf(UserAction.class);
        dataOf(result).path("actions").forEach(action -> actions.add(UserAction.valueOf(action.asText())));
        assertEquals(expected, actions, "actions offered for user " + target + ": " + body(result));
    }

    private Map<String, Object> update(Long id, String name) {
        Map<String, Object> request = new HashMap<>();
        request.put("id", id);
        request.put("name", name);
        request.put("surname", name.toUpperCase());
        request.put("language", Language.DE.name());
        return request;
    }

    private Map<String, Object> withExtras(Map<String, Object> request, Snapshot current) {
        request.put("email", "taken-over@inatrace.test");
        request.put("role", current.role() == UserRole.SYSTEM_ADMIN
                ? UserRole.USER.name() : UserRole.SYSTEM_ADMIN.name());
        request.put("status", UserStatus.DEACTIVATED.name());
        return request;
    }

    private Snapshot edited(Snapshot before, String name) {
        return new Snapshot(name, name.toUpperCase(), before.email(), Language.DE, before.role(), before.status());
    }

    private Snapshot snapshot(Long id) {
        User user = reload(id);
        return new Snapshot(user.getName(), user.getSurname(), user.getEmail(), user.getLanguage(),
                user.getRole(), user.getStatus());
    }

    private List<Long> companyIdsOf(MvcResult result) throws Exception {
        return idsOf(result, "companyIds");
    }

    private List<Long> idsOf(MvcResult result, String field) throws Exception {
        List<Long> ids = new ArrayList<>();
        dataOf(result).path(field).forEach(node -> ids.add(node.asLong()));
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

    private void assertFilter(String filter, List<Account> expected) throws Exception {
        assertListsExactly(perform(get("/api/user/regional-admin/list?limit=1000&" + filter)
                .cookie(regionalAdminSession)), expected, filter);
    }

    private void assertListsExactly(MvcResult result, List<Account> accounts, String context) throws Exception {
        assertEquals(200, result.getResponse().getStatus(), context + ": " + body(result));
        List<String> emails = new ArrayList<>();
        dataOf(result).path("items").forEach(item -> emails.add(item.path("email").asText()));
        assertEquals(accounts.stream().map(Account::email).sorted().toList(), emails.stream().sorted().toList(),
                context + ": " + body(result));
        assertEquals(accounts.size(), dataOf(result).path("count").asInt(), context + ": " + body(result));
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
