package com.abelium.inatrace.components.user;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HTTP coverage for {@code GET /api/user/list}, one of the {@code UserController} routes that issue
 * #78 found unreached. The route is open to any authenticated caller and is scoped entirely by
 * company membership: {@code UserService.userListQueryObject} takes the caller's companies from
 * {@code CompanyQueries.fetchCompanyIdsForUser(userId, List.of(CompanyStatus.ACTIVE))} and then
 * lists, distinctly, the users enrolled in those companies.
 *
 * <p><b>The fixture.</b> Four companies and two accounts without one, so that every shape the
 * scoping query can produce is reachable: a caller with one active company, a caller with two (for
 * the de-duplicated union), a caller whose memberships are part active and part not (the status
 * filter applies per company, not per caller), a caller whose only company is DEACTIVATED or
 * REGISTERED, and a caller with no company at all -- as a plain user and as a system admin, since
 * {@code UserRole} grants nothing on this route. Company A has a system admin, a regional admin and
 * a company admin who is a plain user, each enrolled once, so neither role axis can stand in for the
 * other.
 *
 * <p><b>Accounts.</b> Every assertion matches on the raw response body, so each account's name,
 * surname and email must be unique across the fixture. No value may be a substring of another, or
 * {@code assertDoesNotLeak} would pass by accident; {@link #accountValuesAreMutuallyDistinguishable}
 * guards that property.
 *
 * <p>The values are synthetic: each account has one short code, built from {@code Co} + company
 * letter(s) + role, or {@code NoCo} for an account without a company. The role names the
 * {@link UserRole} ({@code User}, {@code SysAdmin}, {@code RegAdmin}) or, for a plain user, the
 * {@link UserStatus} when it is not ACTIVE ({@code Unconf}, {@code Deact}); {@code CoAdmin} is the
 * one exception, a plain user enrolled as {@link CompanyUserRole#COMPANY_ADMIN}. Every other
 * enrollment is {@code COMPANY_USER}, except the second membership of each shared account. The name
 * spells the code in PascalCase, the surname in upper snake case and the email in kebab case on the
 * reserved {@code .test} domain, e.g. {@code CoBSysAdmin} / {@code CO_B_SYS_ADMIN} /
 * {@code co-b-sys-admin@inatrace.test}. The spellings differ in case and separator, so no value is
 * contained in another, and a leaked value names its account.
 *
 * <p>Every group keeps a success case as a control: a suite that only asserted refusals would stay
 * green if the whole route stopped returning rows. The refusal assertions check the response body
 * as well as the status code, because a response that refuses in the header and still streams the
 * rows is the leak worth catching.
 *
 * <p><b>Status codes.</b> {@code /api/user/list} is not in the {@code permitAll} list of
 * {@code SpringSecurityConfig}, so an unauthenticated request is answered 401 by the filter chain's
 * {@code RestAuthenticationEntryPoint} -- never an empty 200, which would make a broken session
 * indistinguishable from a caller with no colleagues.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserListApiTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private record Account(String name, String surname, String email) {
        List<String> values() {
            return List.of(name, surname, email);
        }
    }

    private static final Account COMPANY_A_USER =
            new Account("CoAUser", "CO_A_USER", "co-a-user@inatrace.test");
    private static final Account COMPANY_A_SYSTEM_ADMIN =
            new Account("CoASysAdmin", "CO_A_SYS_ADMIN", "co-a-sys-admin@inatrace.test");
    private static final Account COMPANY_A_COMPANY_ADMIN =
            new Account("CoACoAdmin", "CO_A_CO_ADMIN", "co-a-co-admin@inatrace.test");
    private static final Account COMPANY_A_REGIONAL_ADMIN =
            new Account("CoARegAdmin", "CO_A_REG_ADMIN", "co-a-reg-admin@inatrace.test");
    private static final Account COMPANY_A_UNCONFIRMED_USER =
            new Account("CoAUnconf", "CO_A_UNCONF", "co-a-unconf@inatrace.test");
    private static final Account COMPANY_A_DEACTIVATED_USER =
            new Account("CoADeact", "CO_A_DEACT", "co-a-deact@inatrace.test");

    private static final Account COMPANY_B_USER =
            new Account("CoBUser", "CO_B_USER", "co-b-user@inatrace.test");
    private static final Account COMPANY_B_SYSTEM_ADMIN =
            new Account("CoBSysAdmin", "CO_B_SYS_ADMIN", "co-b-sys-admin@inatrace.test");
    private static final Account COMPANY_B_REGIONAL_ADMIN =
            new Account("CoBRegAdmin", "CO_B_REG_ADMIN", "co-b-reg-admin@inatrace.test");
    private static final Account COMPANY_B_UNCONFIRMED_USER =
            new Account("CoBUnconf", "CO_B_UNCONF", "co-b-unconf@inatrace.test");

    private static final Account SHARED_AB_USER =
            new Account("CoABUser", "CO_AB_USER", "co-ab-user@inatrace.test");
    private static final Account SHARED_AD_USER =
            new Account("CoADUser", "CO_AD_USER", "co-ad-user@inatrace.test");

    private static final Account COMPANY_D_USER =
            new Account("CoDUser", "CO_D_USER", "co-d-user@inatrace.test");
    private static final Account COMPANY_R_USER =
            new Account("CoRUser", "CO_R_USER", "co-r-user@inatrace.test");

    private static final Account UNASSIGNED_USER =
            new Account("NoCoUser", "NO_CO_USER", "no-co-user@inatrace.test");
    private static final Account UNASSIGNED_SYSTEM_ADMIN =
            new Account("NoCoSysAdmin", "NO_CO_SYS_ADMIN", "no-co-sys-admin@inatrace.test");

    private static final List<Account> ALL_ACCOUNTS = List.of(
            COMPANY_A_USER, COMPANY_A_SYSTEM_ADMIN, COMPANY_A_COMPANY_ADMIN, COMPANY_A_REGIONAL_ADMIN,
            COMPANY_A_UNCONFIRMED_USER, COMPANY_A_DEACTIVATED_USER, COMPANY_B_USER, COMPANY_B_SYSTEM_ADMIN,
            COMPANY_B_REGIONAL_ADMIN, COMPANY_B_UNCONFIRMED_USER, SHARED_AB_USER, SHARED_AD_USER,
            COMPANY_D_USER, COMPANY_R_USER, UNASSIGNED_USER, UNASSIGNED_SYSTEM_ADMIN);

    private static final List<Account> COMPANY_A_MEMBERS = List.of(
            COMPANY_A_USER, COMPANY_A_SYSTEM_ADMIN, COMPANY_A_COMPANY_ADMIN, COMPANY_A_REGIONAL_ADMIN,
            COMPANY_A_UNCONFIRMED_USER, COMPANY_A_DEACTIVATED_USER, SHARED_AB_USER, SHARED_AD_USER);

    private static final List<Account> COMPANY_B_MEMBERS = List.of(
            COMPANY_B_USER, COMPANY_B_SYSTEM_ADMIN, COMPANY_B_REGIONAL_ADMIN, COMPANY_B_UNCONFIRMED_USER,
            SHARED_AB_USER);

    private static final List<Account> OUTSIDE_COMPANY_A = List.of(
            COMPANY_B_USER, COMPANY_B_SYSTEM_ADMIN, COMPANY_B_REGIONAL_ADMIN, COMPANY_B_UNCONFIRMED_USER,
            COMPANY_D_USER, COMPANY_R_USER, UNASSIGNED_USER, UNASSIGNED_SYSTEM_ADMIN);

    private static final List<Account> OUTSIDE_COMPANY_B = List.of(
            COMPANY_A_USER, COMPANY_A_SYSTEM_ADMIN, COMPANY_A_COMPANY_ADMIN, COMPANY_A_REGIONAL_ADMIN,
            COMPANY_A_UNCONFIRMED_USER, COMPANY_A_DEACTIVATED_USER, SHARED_AD_USER, COMPANY_D_USER,
            COMPANY_R_USER, UNASSIGNED_USER, UNASSIGNED_SYSTEM_ADMIN);

    private Cookie companyAUserSession;
    private Cookie companyASystemAdminSession;
    private Cookie companyACompanyAdminSession;
    private Cookie companyARegionalAdminSession;
    private Cookie companyBUserSession;
    private Cookie companyBRegionalAdminSession;
    private Cookie sharedAbUserSession;
    private Cookie sharedAdUserSession;
    private Cookie companyDUserSession;
    private Cookie companyRUserSession;
    private Cookie unassignedUserSession;
    private Cookie unassignedSystemAdminSession;

    private Long sharedAbUserId;
    private Long companyAUserId;

    @BeforeEach
    void seed() {
        Company companyA = company("Company A", CompanyStatus.ACTIVE);
        Company companyB = company("Company B", CompanyStatus.ACTIVE);
        Company companyD = company("Company Deactivated", CompanyStatus.DEACTIVATED);
        Company companyR = company("Company Registered", CompanyStatus.REGISTERED);

        User companyAUser = user(COMPANY_A_USER, UserRole.USER, UserStatus.ACTIVE);
        User companyASystemAdmin = user(COMPANY_A_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
        User companyACompanyAdmin = user(COMPANY_A_COMPANY_ADMIN, UserRole.USER, UserStatus.ACTIVE);
        User companyARegionalAdmin = user(COMPANY_A_REGIONAL_ADMIN, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
        User companyAUnconfirmedUser = user(COMPANY_A_UNCONFIRMED_USER, UserRole.USER, UserStatus.UNCONFIRMED);
        User companyADeactivatedUser = user(COMPANY_A_DEACTIVATED_USER, UserRole.USER, UserStatus.DEACTIVATED);

        User companyBUser = user(COMPANY_B_USER, UserRole.USER, UserStatus.ACTIVE);
        User companyBSystemAdmin = user(COMPANY_B_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
        User companyBRegionalAdmin = user(COMPANY_B_REGIONAL_ADMIN, UserRole.REGIONAL_ADMIN, UserStatus.ACTIVE);
        User companyBUnconfirmedUser = user(COMPANY_B_UNCONFIRMED_USER, UserRole.USER, UserStatus.UNCONFIRMED);

        User sharedAbUser = user(SHARED_AB_USER, UserRole.USER, UserStatus.ACTIVE);
        User sharedAdUser = user(SHARED_AD_USER, UserRole.USER, UserStatus.ACTIVE);

        User companyDUser = user(COMPANY_D_USER, UserRole.USER, UserStatus.ACTIVE);
        User companyRUser = user(COMPANY_R_USER, UserRole.USER, UserStatus.ACTIVE);

        User unassignedUser = user(UNASSIGNED_USER, UserRole.USER, UserStatus.ACTIVE);
        User unassignedSystemAdmin = user(UNASSIGNED_SYSTEM_ADMIN, UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);

        enroll(companyAUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyASystemAdmin, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyACompanyAdmin, companyA, CompanyUserRole.COMPANY_ADMIN);
        enroll(companyARegionalAdmin, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyAUnconfirmedUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(companyADeactivatedUser, companyA, CompanyUserRole.COMPANY_USER);

        enroll(companyBUser, companyB, CompanyUserRole.COMPANY_USER);
        enroll(companyBSystemAdmin, companyB, CompanyUserRole.COMPANY_USER);
        enroll(companyBRegionalAdmin, companyB, CompanyUserRole.COMPANY_USER);
        enroll(companyBUnconfirmedUser, companyB, CompanyUserRole.COMPANY_USER);

        enroll(sharedAbUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(sharedAbUser, companyB, CompanyUserRole.COMPANY_ADMIN);
        enroll(sharedAdUser, companyA, CompanyUserRole.COMPANY_USER);
        enroll(sharedAdUser, companyD, CompanyUserRole.COMPANY_ADMIN);

        enroll(companyDUser, companyD, CompanyUserRole.COMPANY_USER);
        enroll(companyRUser, companyR, CompanyUserRole.COMPANY_USER);

        companyAUserSession = session(companyAUser);
        companyASystemAdminSession = session(companyASystemAdmin);
        companyACompanyAdminSession = session(companyACompanyAdmin);
        companyARegionalAdminSession = session(companyARegionalAdmin);

        companyBUserSession = session(companyBUser);
        companyBRegionalAdminSession = session(companyBRegionalAdmin);

        sharedAbUserSession = session(sharedAbUser);
        sharedAdUserSession = session(sharedAdUser);

        companyDUserSession = session(companyDUser);
        companyRUserSession = session(companyRUser);
        unassignedUserSession = session(unassignedUser);
        unassignedSystemAdminSession = session(unassignedSystemAdmin);

        sharedAbUserId = sharedAbUser.getId();
        companyAUserId = companyAUser.getId();

        flushAndDetach();
    }

    @Test
    @DisplayName("the company user list refuses an anonymous request")
    void userListRejectsAnonymousRequests() throws Exception {
        MvcResult result = perform(get("/api/user/list"));

        assertEquals(401, result.getResponse().getStatus(), body(result));
        assertDoesNotLeak(result, ALL_ACCOUNTS);
    }

    @Test
    @DisplayName("the company user list shows colleagues of an active shared company and nobody else")
    void ownCompanyUserListIsScopedToSharedActiveCompanies() throws Exception {
        MvcResult companyAView = perform(get("/api/user/list?limit=1000").cookie(companyAUserSession));
        assertEquals(200, companyAView.getResponse().getStatus(), body(companyAView));
        assertContainsAccounts(companyAView, COMPANY_A_MEMBERS);
        assertDoesNotLeak(companyAView, OUTSIDE_COMPANY_A);

        MvcResult companyBView = perform(get("/api/user/list?limit=1000").cookie(companyBUserSession));
        assertEquals(200, companyBView.getResponse().getStatus(), body(companyBView));
        assertContainsAccounts(companyBView, COMPANY_B_MEMBERS);
        assertDoesNotLeak(companyBView, OUTSIDE_COMPANY_B);
    }

    @Test
    @DisplayName("a caller in two companies gets the de-duplicated union of both")
    void userListIsTheDeduplicatedUnionOfTheCallersCompanies() throws Exception {
        MvcResult result = perform(get("/api/user/list?limit=1000").cookie(sharedAbUserSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertContainsAccounts(result, COMPANY_A_MEMBERS);
        assertContainsAccounts(result, COMPANY_B_MEMBERS);
        assertDoesNotLeak(result, List.of(COMPANY_D_USER, COMPANY_R_USER, UNASSIGNED_USER, UNASSIGNED_SYSTEM_ADMIN));

        int union = COMPANY_A_MEMBERS.size() + COMPANY_B_MEMBERS.size() - 1;
        List<Long> ids = idsOf(result);
        assertEquals(union, ids.size(), body(result));
        assertEquals(union, dataOf(result).get("count").asInt(),
                "the paginated count must match the de-duplicated union: " + body(result));
        assertEquals(new LinkedHashSet<>(ids).size(), ids.size(),
                "the shared membership must not duplicate a row: " + ids);
        assertEquals(1, ids.stream().filter(sharedAbUserId::equals).count(),
                "the user enrolled in both companies must appear exactly once: " + ids);
    }

    @Test
    @DisplayName("an inactive membership does not cancel an active one")
    void anInactiveMembershipDoesNotRemoveTheActiveCompanysUsers() throws Exception {
        MvcResult result = perform(get("/api/user/list?limit=1000").cookie(sharedAdUserSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertContainsAccounts(result, COMPANY_A_MEMBERS);
        assertEquals(COMPANY_A_MEMBERS.size(), dataOf(result).get("count").asInt(), body(result));
        assertDoesNotLeak(result, List.of(COMPANY_D_USER),
                "the deactivated company contributes nothing, even for a caller that also has an active one");
    }

    @Test
    @DisplayName("membership of a non-active company does not open the company user list")
    void userListIgnoresMembershipOfANonActiveCompany() throws Exception {
        MvcResult deactivated = perform(get("/api/user/list").cookie(companyDUserSession));
        assertEquals(200, deactivated.getResponse().getStatus(), body(deactivated));
        assertEquals(0, dataOf(deactivated).get("count").asInt(),
                "the company is DEACTIVATED, so it grants no visibility: " + body(deactivated));
        assertDoesNotLeak(deactivated, ALL_ACCOUNTS);

        MvcResult registered = perform(get("/api/user/list").cookie(companyRUserSession));
        assertEquals(200, registered.getResponse().getStatus(), body(registered));
        assertEquals(0, dataOf(registered).get("count").asInt(),
                "the company is only REGISTERED, so it grants no visibility: " + body(registered));
        assertDoesNotLeak(registered, ALL_ACCOUNTS);
    }

    @Test
    @DisplayName("the company user list of a user without a company is empty, not the whole instance")
    void userListIsEmptyForAUserWithNoCompany() throws Exception {
        MvcResult result = perform(get("/api/user/list?limit=1000").cookie(unassignedUserSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertEquals(0, dataOf(result).get("count").asInt(), body(result));
        assertDoesNotLeak(result, ALL_ACCOUNTS);
    }

    @Test
    @DisplayName("neither the company role nor the user role changes the result set")
    void neitherCompanyRoleNorUserRoleChangesTheResultSet() throws Exception {
        Set<Long> plainMemberView = new HashSet<>(idsOf(
                perform(get("/api/user/list?limit=1000").cookie(companyAUserSession))));
        Set<Long> companyAdminView = new HashSet<>(idsOf(
                perform(get("/api/user/list?limit=1000").cookie(companyACompanyAdminSession))));
        Set<Long> systemAdminView = new HashSet<>(idsOf(
                perform(get("/api/user/list?limit=1000").cookie(companyASystemAdminSession))));
        Set<Long> regionalAdminView = new HashSet<>(idsOf(
                perform(get("/api/user/list?limit=1000").cookie(companyARegionalAdminSession))));

        assertEquals(COMPANY_A_MEMBERS.size(), plainMemberView.size(), plainMemberView.toString());
        assertEquals(plainMemberView, companyAdminView,
                "a COMPANY_ADMIN must see exactly what a COMPANY_USER of the same company sees");
        assertEquals(plainMemberView, systemAdminView,
                "a SYSTEM_ADMIN must see exactly what a COMPANY_USER of the same company sees");
        assertEquals(plainMemberView, regionalAdminView,
                "a REGIONAL_ADMIN must see exactly what a COMPANY_USER of the same company sees");
    }

    @Test
    @DisplayName("an administrator calling the company user list is scoped to its own companies too")
    void companyUserListIsCompanyScopedForAdministratorsAsWell() throws Exception {
        MvcResult systemAdminView = perform(get("/api/user/list?limit=1000").cookie(companyASystemAdminSession));
        assertEquals(200, systemAdminView.getResponse().getStatus(), body(systemAdminView));
        assertContainsAccounts(systemAdminView, COMPANY_A_MEMBERS);
        assertDoesNotLeak(systemAdminView, OUTSIDE_COMPANY_A);

        MvcResult regionalAdminView = perform(get("/api/user/list?limit=1000").cookie(companyBRegionalAdminSession));
        assertEquals(200, regionalAdminView.getResponse().getStatus(), body(regionalAdminView));
        assertContainsAccounts(regionalAdminView, COMPANY_B_MEMBERS);
        assertEquals(COMPANY_B_MEMBERS.size(), dataOf(regionalAdminView).get("count").asInt(),
                body(regionalAdminView));
        assertDoesNotLeak(regionalAdminView, OUTSIDE_COMPANY_B);
    }

    @Test
    @DisplayName("a system admin enrolled in no company gets an empty list here, not the whole instance")
    void systemAdminWithoutACompanyGetsAnEmptyList() throws Exception {
        MvcResult result = perform(get("/api/user/list?limit=1000").cookie(unassignedSystemAdminSession));

        assertEquals(200, result.getResponse().getStatus(),
                "UserRole grants no access on this route, but it must not be refused either: " + body(result));
        assertEquals(0, dataOf(result).get("count").asInt(),
                "a SYSTEM_ADMIN with no company has no scope on this route: " + body(result));
        assertDoesNotLeak(result, ALL_ACCOUNTS);
    }

    @Test
    @DisplayName("deactivated and unconfirmed colleagues are listed by default")
    void deactivatedAndUnconfirmedColleaguesAreListedByDefault() throws Exception {
        MvcResult result = perform(get("/api/user/list?limit=1000").cookie(companyAUserSession));

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertContainsAccounts(result, List.of(COMPANY_A_UNCONFIRMED_USER, COMPANY_A_DEACTIVATED_USER));
        assertEquals(UserStatus.UNCONFIRMED.name(), statusOf(result, COMPANY_A_UNCONFIRMED_USER), body(result));
        assertEquals(UserStatus.DEACTIVATED.name(), statusOf(result, COMPANY_A_DEACTIVATED_USER), body(result));
    }

    @Test
    @DisplayName("no list filter, page size or sort widens the company user list beyond the caller's companies")
    void userListFiltersCannotWidenTheScope() throws Exception {
        List<String> queries = List.of(
                "?limit=1000",
                "?limit=1",
                "?role=SYSTEM_ADMIN",
                "?role=REGIONAL_ADMIN",
                "?role=USER&limit=1000",
                "?status=ACTIVE&limit=1000&offset=0",
                "?status=UNCONFIRMED&limit=1000",
                "?status=DEACTIVATED&limit=1000",
                "?query=" + COMPANY_B_USER.name(),
                "?query=" + COMPANY_B_USER.email(),
                "?email=" + COMPANY_B_USER.email(),
                "?surname=" + COMPANY_B_USER.surname(),
                "?sortBy=email&sort=ASC",
                "?sortBy=email&sort=DESC",
                "?sortBy=surname&sort=ASC",
                "?sortBy=an-unknown-column&sort=ASC",
                "?requestType=FETCH&limit=1000");

        for (String query : queries) {
            MvcResult result = perform(get("/api/user/list" + query).cookie(companyAUserSession));
            assertEquals(200, result.getResponse().getStatus(), query + ": " + body(result));
            assertDoesNotLeak(result, OUTSIDE_COMPANY_A, query);
        }

        MvcResult count = perform(get("/api/user/list?requestType=COUNT").cookie(companyAUserSession));
        assertEquals(200, count.getResponse().getStatus(), body(count));
        assertEquals(COMPANY_A_MEMBERS.size(), dataOf(count).get("count").asInt(),
                "a COUNT request must count only the caller's company members: " + body(count));
    }

    @Test
    @DisplayName("an access token stops opening the company user list once its user is no longer active")
    void userListRejectsATokenWhoseUserIsNoLongerActive() throws Exception {
        MvcResult whileActive = perform(get("/api/user/list?limit=1000").cookie(companyAUserSession));
        assertEquals(200, whileActive.getResponse().getStatus(),
                "the token must work while its user is active: " + body(whileActive));
        assertContainsAccounts(whileActive, COMPANY_A_MEMBERS);

        for (UserStatus status : List.of(UserStatus.UNCONFIRMED, UserStatus.CONFIRMED_EMAIL, UserStatus.DEACTIVATED)) {
            em.createQuery("update User u set u.status = :status where u.id = :id")
                    .setParameter("status", status)
                    .setParameter("id", companyAUserId)
                    .executeUpdate();

            MvcResult result = perform(get("/api/user/list?limit=1000").cookie(companyAUserSession));

            assertEquals(401, result.getResponse().getStatus(), status + ": " + body(result));
            assertDoesNotLeak(result, ALL_ACCOUNTS, status.name());
        }
    }

    @Test
    @DisplayName("no fixture name, surname or email is a substring of another")
    void accountValuesAreMutuallyDistinguishable() {
        List<String> values = ALL_ACCOUNTS.stream().flatMap(account -> account.values().stream()).toList();
        for (String value : values) {
            for (String other : values) {
                if (value.equals(other)) {
                    continue;
                }
                assertFalse(other.contains(value),
                        "\"" + value + "\" is contained in \"" + other
                                + "\", so an assertion about either one proves nothing");
            }
        }
        assertEquals(values.size(), new HashSet<>(values).size(), "fixture values must be unique: " + values);
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

    private List<Long> idsOf(MvcResult result) throws Exception {
        List<Long> ids = new ArrayList<>();
        dataOf(result).path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private JsonNode itemOf(MvcResult result, Account account) throws Exception {
        for (JsonNode item : dataOf(result).path("items")) {
            if (account.email().equals(item.path("email").asText())) {
                return item;
            }
        }
        throw new AssertionError("no element for " + account.email() + ": " + body(result));
    }

    private String statusOf(MvcResult result, Account account) throws Exception {
        return itemOf(result, account).path("status").asText();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        flushAndDetach();
        return mockMvc.perform(request).andReturn();
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
