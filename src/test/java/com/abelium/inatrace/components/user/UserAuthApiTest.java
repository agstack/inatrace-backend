package com.abelium.inatrace.components.user;

import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.auth.AuthenticationToken;
import com.abelium.inatrace.db.entities.auth.ConfirmationToken;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.types.ConfirmationTokenType;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Authentication &amp; users endpoint group, exercised over real HTTP.
 *
 * <p>These are the endpoints every other one depends on: if login does not hand back a usable
 * cookie, nothing else in the API can be reached. They are also the endpoints where a mistake
 * is a security incident rather than a bug, so the tests below assert the refusals as carefully
 * as the successes -- who is turned away, with which status, and what the response does not
 * reveal about accounts that exist.
 *
 * <p><b>Why every path below starts with {@code /api}.</b> The controllers declare
 * {@code /user/**}, but {@link com.abelium.inatrace.configuration.PrefixedApiRequestHandler},
 * installed through {@code WebConfiguration}, prefixes every mapping in the {@code com.abelium}
 * packages with {@code api} as the handler methods are registered. So the served path is
 * {@code /api/user/login} even though no controller says so and no context path is configured,
 * which is what the security rules, the README and the generated clients all match. Requesting
 * the undecorated {@code /user/login} answers 404, so the tests use the real path.
 *
 * <p>No mail server is involved. {@code INATrace.mail.sendingEnabled} is false in the test
 * profile, so {@code MailEngine} builds a message and returns before it opens an SMTP
 * connection. The reset and confirmation flows are therefore tested the way they actually
 * work: the token is a database row, and the mail is only how it reaches the user. Every test
 * that needs a token reads it from the row it seeded, never from an inbox.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserAuthApiTest extends AbstractMySqlIntegrationTest {

	/** Long enough to satisfy PasswordTools.isPasswordComplex, which checks length 8..50. */
	private static final String PASSWORD = "correct-horse-battery";
	private static final String OTHER_PASSWORD = "a-different-password";

	@Autowired
	private TestRestTemplate rest;

	@PersistenceContext
	private EntityManager em;

	@Autowired
	private PlatformTransactionManager txManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private AuthenticationTokenCleanup tokenCleanup;

	@Value("${INATrace.auth.jwtSigningKey}")
	private String jwtSigningKey;

	/**
	 * These endpoints answer 401 a great deal, and the JDK's {@code HttpURLConnection} -- which
	 * {@code TestRestTemplate} reaches for by default -- responds to a 401 by trying to repeat the
	 * request with credentials. It cannot repeat a request whose body it has already streamed, so
	 * it throws {@code ResourceAccessException} instead of returning the response, and the
	 * assertion never sees the status it was checking. The newer {@code java.net.http} client does
	 * not attempt that retry, so every refusal arrives as an ordinary response.
	 */
	@BeforeEach
	void useAClientThatDoesNotRetryOn401() {
		rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
	}

	// ---------------------------------------------------------------- login

	@Test
	@DisplayName("bursts of 50 and 100 logins work with and without an existing refresh session")
	void concurrentLoginsHaveIndependentRefreshSessions() throws Exception {
		for (boolean existingSession : List.of(true, false)) {
			User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
			try {
				String previousCookies = existingSession ? bothCookiesFor(user) : null;
				List<ResponseEntity<String>> responses = concurrentLogins(user.getEmail(), existingSession ? 50 : 100);
				HashSet<String> refreshCookies = new HashSet<>();
				for (ResponseEntity<String> response : responses) {
					assertEquals(HttpStatus.OK, response.getStatusCode(), "concurrent login must succeed");
					String access = cookieNamed(response, "inatrace-accessToken");
					String refresh = cookieNamed(response, "inatrace-refreshToken");
					assertTrue(refreshCookies.add(refresh), "each login must receive a distinct refresh token");
					assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/user/refresh_authentication",
							null, access + "; " + refresh).getStatusCode(), "each session must refresh");
				}
				if (previousCookies != null) {
					assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/user/refresh_authentication",
							null, previousCookies).getStatusCode(), "existing session must remain valid");
				}
			} finally {
				cleanupAuthUser(user.getId());
			}
		}
	}

	@Test
	@DisplayName("login refuses an email that belongs to nobody")
	void loginRejectsUnknownEmail() {
		ResponseEntity<String> response = login("nobody-" + UUID.randomUUID() + "@test.invalid", PASSWORD);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
				"an unknown account should be an authentication failure, not a server error");
		assertNoAuthCookies(response);
	}

	@Test
	@DisplayName("login refuses the right account with the wrong password")
	void loginRejectsWrongPassword() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);

		ResponseEntity<String> response = login(user.getEmail(), OTHER_PASSWORD);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertNoAuthCookies(response);
	}

	@Test
	@DisplayName("login tells an unknown account and a wrong password apart from the outside only by luck")
	void loginDoesNotRevealWhetherTheAccountExists() {
		// Both paths raise AUTH_ERROR with the same message, so a caller probing for valid
		// addresses learns nothing from a failed password attempt. This is worth pinning: it is
		// the kind of property a later refactor silently loses.
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);

		ResponseEntity<String> unknown = login("nobody-" + UUID.randomUUID() + "@test.invalid", PASSWORD);
		ResponseEntity<String> wrongPassword = login(user.getEmail(), OTHER_PASSWORD);

		assertEquals(unknown.getStatusCode(), wrongPassword.getStatusCode(),
				"an unknown address and a wrong password should fail identically");
	}

	@Test
	@DisplayName("login refuses an account that has not been activated yet")
	void loginRejectsUnconfirmedAndConfirmedEmail() {
		// Registration alone is not enough to get in: an administrator has to activate the
		// account. Both pre-active states are refused, and with a different status from a bad
		// password, so the client can tell "wrong credentials" from "not allowed in yet".
		User unconfirmed = seedUser(UserStatus.UNCONFIRMED, UserRole.USER);
		User confirmedEmail = seedUser(UserStatus.CONFIRMED_EMAIL, UserRole.USER);

		assertEquals(HttpStatus.FORBIDDEN, login(unconfirmed.getEmail(), PASSWORD).getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, login(confirmedEmail.getEmail(), PASSWORD).getStatusCode());
	}

	@Test
	@DisplayName("login refuses a deactivated account")
	void loginRejectsDeactivated() {
		User user = seedUser(UserStatus.DEACTIVATED, UserRole.USER);

		ResponseEntity<String> response = login(user.getEmail(), PASSWORD);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertNoAuthCookies(response);
	}

	@Test
	@DisplayName("login hands back an access cookie and a refresh cookie, both HttpOnly")
	void loginIssuesBothCookies() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);

		ResponseEntity<String> response = login(user.getEmail(), PASSWORD);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		List<String> cookies = setCookies(response);
		assertEquals(2, cookies.size(), "the mobile login expects exactly the two auth cookies");
		assertTrue(cookies.get(0).startsWith("inatrace-accessToken="), "access cookie must remain first");
		assertTrue(cookies.get(1).startsWith("inatrace-refreshToken="), "refresh cookie must remain second");
		assertTrue(cookies.get(0).contains("Max-Age=" + tokenService.getAccessTokenExpirationSec()));
		assertTrue(cookies.get(1).contains("Max-Age=" + tokenService.getRefreshTokenExpirationSec()));
		assertTrue(cookies.stream().allMatch(c -> c.contains("Path=/")));
		String access = cookieNamed(response, "inatrace-accessToken").split("=", 2)[1];
		String refresh = cookieNamed(response, "inatrace-refreshToken").split("=", 2)[1];
		assertNotNull(JWT.decode(access).getExpiresAt(), "mobile reads the JWT expiry");
		assertEquals("access", JWT.decode(access).getClaim("token_use").asString());
		assertEquals("refresh", JWT.decode(refresh).getClaim("token_use").asString());
		assertTrue(response.getBody() != null && response.getBody().contains("\"status\":\"OK\""));
		assertTrue(cookies.stream().anyMatch(c -> c.startsWith("inatrace-accessToken=")),
				"login should set the access cookie");
		assertTrue(cookies.stream().anyMatch(c -> c.startsWith("inatrace-refreshToken=")),
				"login should set the refresh cookie");
		// The token must not be readable from JavaScript; that is the whole reason it travels
		// as a cookie rather than in the response body.
		assertTrue(cookies.stream()
						.filter(c -> c.startsWith("inatrace-"))
						.allMatch(c -> c.toLowerCase().contains("httponly")),
				"both auth cookies should be HttpOnly");
	}

	// ------------------------------------------------- using and dropping the session

	@Test
	@DisplayName("a guarded endpoint refuses a caller with no cookie")
	void profileRequiresAuthentication() {
		ResponseEntity<String> response = rest.getForEntity("/api/user/profile", String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
				"an anonymous caller should be turned away by the entry point, not by a controller");
	}

	@Test
	@DisplayName("the cookie from login opens the caller's own profile")
	void accessCookieOpensProfile() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = accessCookieFor(user);

		ResponseEntity<String> response = get("/api/user/profile", cookie);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains(user.getEmail()),
				"the profile should be the profile of the user who logged in");
	}

	@Test
	@DisplayName("a refresh JWT cannot authenticate protected reads or writes, before or after logout")
	void refreshJwtCannotActAsAccessJwt() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		try {
			ResponseEntity<String> login = login(user.getEmail(), PASSWORD);
			String access = cookieNamed(login, "inatrace-accessToken");
			String refresh = cookieNamed(login, "inatrace-refreshToken");
			String disguisedRefresh = asAccessCookie(refresh);
			assertEquals(HttpStatus.OK, get("/api/user/profile", access).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", disguisedRefresh).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, exchange(HttpMethod.PUT, "/api/user/profile",
					Map.of("name", "ShouldNotPersist"), disguisedRefresh).getStatusCode());
			assertEquals("Test", nameOf(user.getId()));
			assertEquals(HttpStatus.UNAUTHORIZED, refresh("inatrace-refreshToken=" + access.split("=", 2)[1]).getStatusCode(),
					"an access token must not identify a refresh session");
			assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/user/logout", null,
					access + "; " + refresh).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, refresh(refresh).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", disguisedRefresh).getStatusCode());
			assertEquals(HttpStatus.OK, get("/api/user/profile", access).getStatusCode(),
					"logout does not revoke an already-issued access JWT");
		} finally {
			cleanupAuthUser(user.getId());
		}
	}

	@Test
	@DisplayName("access authentication rejects untyped, unknown-purpose, expired and forged JWTs")
	void accessAuthenticationRejectsInvalidOrLegacyJwt() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		try {
			Map<String, String> invalidTokens = Map.of(
					"untyped legacy", signedJwt(user, null, 300, jwtSigningKey),
					"unknown purpose", signedJwt(user, "other", 300, jwtSigningKey),
					"expired", signedJwt(user, "access", -1, jwtSigningKey),
					"invalid signature", signedJwt(user, "access", 300, "wrong-test-signing-key"));
			for (Map.Entry<String, String> token : invalidTokens.entrySet()) {
				assertEquals(HttpStatus.UNAUTHORIZED,
						get("/api/user/profile", "inatrace-accessToken=" + token.getValue()).getStatusCode(),
						token.getKey() + " JWT must be rejected");
			}
		} finally {
			cleanupAuthUser(user.getId());
		}
	}

	@Test
	@DisplayName("an access token stops authenticating immediately after its user is deactivated")
	void accessCookieIsRejectedAfterUserIsDeactivated() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = accessCookieFor(user);

		assertEquals(HttpStatus.OK, get("/api/user/profile", cookie).getStatusCode(),
				"the token must work while its user remains active");
		setStatus(user.getId(), UserStatus.DEACTIVATED);

		ResponseEntity<String> response = get("/api/user/profile", cookie);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
				"the next request must not wait for the access-token expiry");
		assertFalse(response.getBody() != null && response.getBody().contains(user.getEmail()),
				"a refused request must not expose the deactivated user's profile");
	}

	@Test
	@DisplayName("an access token is refused for every non-active persisted user status")
	void accessCookieIsRejectedForEveryNonActiveStatus() {
		for (UserStatus status : List.of(UserStatus.UNCONFIRMED, UserStatus.CONFIRMED_EMAIL, UserStatus.DEACTIVATED)) {
			User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
			String cookie = accessCookieFor(user);
			setStatus(user.getId(), status);

			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", cookie).getStatusCode(),
					"status " + status + " must not authenticate a previously issued access token");
		}
	}

	@Test
	@DisplayName("a deactivated access cookie does not block a public endpoint")
	void deactivatedAccessCookieDoesNotBlockPublicEndpoint() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = accessCookieFor(user);
		setStatus(user.getId(), UserStatus.DEACTIVATED);

		ResponseEntity<String> response = get("/api/public/globalSettings/missing-" + UUID.randomUUID(), cookie);

		assertEquals(HttpStatus.OK, response.getStatusCode(),
				"a rejected principal must leave B2C routes anonymous rather than deny them");
	}

	@Test
	@DisplayName("a deactivated user cannot use an already-issued access token to change its profile")
	void deactivatedUserAccessTokenCannotWrite() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = accessCookieFor(user);
		setStatus(user.getId(), UserStatus.DEACTIVATED);

		ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/user/profile",
				Map.of("name", "ShouldNotPersist"), cookie);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("Test", nameOf(user.getId()),
				"a rejected access token must not reach the profile update service");
	}

	@Test
	@DisplayName("an access token uses the user's current role instead of its role claim")
	void accessCookieDoesNotKeepAnOldAdministratorRole() {
		User admin = seedUser(UserStatus.ACTIVE, UserRole.SYSTEM_ADMIN);
		String cookie = accessCookieFor(admin);

		assertEquals(HttpStatus.OK, get("/api/user/admin/list", cookie).getStatusCode());
		setRole(admin.getId(), UserRole.USER);

		assertEquals(HttpStatus.FORBIDDEN, get("/api/user/admin/list", cookie).getStatusCode(),
				"authorization must use the persisted role, not the JWT snapshot");
	}

	@Test
	@DisplayName("logout expires both cookies")
	void logoutClearsCookies() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = bothCookiesFor(user);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/logout", null, cookie);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		// Removal is an empty cookie with Max-Age=0; the browser drops it on receipt.
		assertTrue(setCookies(response).stream().anyMatch(c -> c.contains("Max-Age=0")),
				"logout should send expiring cookies back");
	}

	@Test
	@DisplayName("refresh_authentication issues a new access cookie from the refresh cookie")
	void refreshIssuesNewAccessCookie() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = bothCookiesFor(user);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/refresh_authentication", null, cookie);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(setCookies(response).stream().anyMatch(c -> c.startsWith("inatrace-accessToken=")),
				"a refresh should mint a fresh access cookie");
		String freshAccess = cookieNamed(response, "inatrace-accessToken");
		assertEquals("access", JWT.decode(freshAccess.split("=", 2)[1]).getClaim("token_use").asString());
		assertEquals(HttpStatus.OK, get("/api/user/profile", freshAccess).getStatusCode());
	}

	@Test
	@DisplayName("a later login and logout leave another refresh session valid")
	void refreshSessionsAreIndependent() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		try {
			String first = bothCookiesFor(user);
			String second = bothCookiesFor(user);
			assertEquals(HttpStatus.OK, refresh(first).getStatusCode());
			assertEquals(HttpStatus.OK, refresh(second).getStatusCode());
			assertEquals(HttpStatus.OK, refresh(first).getStatusCode(), "refresh stays reusable");
			assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/user/logout", null, first).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, refresh(first).getStatusCode());
			assertEquals(HttpStatus.OK, refresh(second).getStatusCode());
		} finally {
			cleanupAuthUser(user.getId());
		}
	}

	@Test
	@DisplayName("a legacy refresh JWT remains usable after a new login")
	void legacyRefreshTokenRemainsValid() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		try {
			String legacy = seedLegacyRefresh(user.getId());
			ResponseEntity<String> login = login(user.getEmail(), PASSWORD);
			assertEquals(HttpStatus.OK, login.getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", asAccessCookie(legacy)).getStatusCode());
			ResponseEntity<String> refreshed = refresh(legacy);
			assertEquals(HttpStatus.OK, refreshed.getStatusCode());
			String freshAccess = cookieNamed(refreshed, "inatrace-accessToken");
			assertEquals(HttpStatus.OK, get("/api/user/profile", freshAccess).getStatusCode());
			assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/user/logout", null,
					freshAccess + "; " + legacy).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, refresh(legacy).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", asAccessCookie(legacy)).getStatusCode());
		} finally {
			cleanupAuthUser(user.getId());
		}
	}

	@Test
	@DisplayName("cleanup removes only refresh sessions long past expiration")
	void cleanupKeepsUnexpiredAndRecentlyExpiredSessions() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		try {
			Long old = seedRefreshWithExpiration(user.getId(), Instant.now().minus(8, ChronoUnit.DAYS));
			Long recent = seedRefreshWithExpiration(user.getId(), Instant.now().minus(1, ChronoUnit.DAYS));
			Long active = seedRefreshWithExpiration(user.getId(), Instant.now().plus(1, ChronoUnit.DAYS));
			long unrelated = tx().execute(s -> em.createQuery(
					"select count(t) from AuthenticationToken t where t.user.id <> :uid and t.expiration < :cutoff",
					Long.class).setParameter("uid", user.getId())
					.setParameter("cutoff", Instant.now().minus(7, ChronoUnit.DAYS)).getSingleResult());
			assertEquals(0, unrelated, "do not run global cleanup when another test owns eligible rows");
			tokenCleanup.removeExpiredSessions();
			tx().executeWithoutResult(s -> {
				assertTrue(em.find(AuthenticationToken.class, old) == null, "old expiry should be removed");
				assertNotNull(em.find(AuthenticationToken.class, recent), "recent expiry should be retained");
				assertNotNull(em.find(AuthenticationToken.class, active), "active session should be retained");
			});
		} finally {
			cleanupAuthUser(user.getId());
		}
	}

	@Test
	@DisplayName("refresh_authentication refuses a user deactivated after login")
	void refreshRejectsADeactivatedUser() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String cookie = bothCookiesFor(user);
		setStatus(user.getId(), UserStatus.DEACTIVATED);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/refresh_authentication", null, cookie);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertNoAuthCookies(response);
	}

	@Test
	@DisplayName("refresh_authentication refuses a caller with no refresh cookie")
	void refreshWithoutCookieIsRejected() {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/refresh_authentication", null, null);

		assertFalse(response.getStatusCode().is2xxSuccessful(),
				"refreshing without a refresh token should not succeed");
	}

	// ---------------------------------------------------------------- registration

	@Test
	@DisplayName("register creates an account that cannot log in yet")
	void registerCreatesAnInactiveAccount() {
		String email = "new-" + UUID.randomUUID() + "@test.invalid";

		ResponseEntity<String> registered = exchange(HttpMethod.POST, "/api/user/register",
				Map.of("email", email, "password", PASSWORD, "name", "New", "surname", "User"), null);

		assertEquals(HttpStatus.OK, registered.getStatusCode());
		assertEquals(UserStatus.UNCONFIRMED, statusOf(email),
				"a fresh registration should be UNCONFIRMED until the email is confirmed");
		assertEquals(HttpStatus.FORBIDDEN, login(email, PASSWORD).getStatusCode(),
				"registering should not be enough to get in");
	}

	@Test
	@DisplayName("register refuses a body with fields missing")
	void registerRejectsIncompleteBody() {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/register",
				Map.of("email", "incomplete-" + UUID.randomUUID() + "@test.invalid"), null);

		assertTrue(response.getStatusCode().is4xxClientError(),
				"a body without the required fields should be rejected, not persisted");
	}

	// ------------------------------------------------------------- password reset

	@Test
	@DisplayName("request_reset_password says nothing about an address it does not know")
	void resetRequestForUnknownEmailRevealsNothing() {
		// The service returns quietly when the address is unknown, so the endpoint cannot be
		// used to enumerate accounts. No mail is involved either way.
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/request_reset_password",
				Map.of("email", "nobody-" + UUID.randomUUID() + "@test.invalid"), null);

		assertEquals(HttpStatus.OK, response.getStatusCode(),
				"an unknown address should answer exactly like a known one");
	}

	@Test
	@DisplayName("request_reset_password stores a reset token for an active account")
	void resetRequestStoresAToken() {
		// This is the assertion that replaces reading an inbox: the endpoint's real effect is a
		// PASSWORD_RESET row, and the mail is only its delivery mechanism.
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		assertEquals(0, countTokens(user.getId(), ConfirmationTokenType.PASSWORD_RESET));

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/request_reset_password",
				Map.of("email", user.getEmail()), null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(1, countTokens(user.getId(), ConfirmationTokenType.PASSWORD_RESET),
				"a reset request should leave exactly one reset token behind");
	}

	@Test
	@DisplayName("reset_password refuses a token that was never issued")
	void resetWithUnknownTokenIsRejected() {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/reset_password",
				Map.of("token", UUID.randomUUID().toString(), "password", OTHER_PASSWORD), null);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	@Test
	@DisplayName("reset_password changes the password and the new one logs in")
	void resetWithValidTokenChangesThePassword() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String token = seedToken(user.getId(), ConfirmationTokenType.PASSWORD_RESET);

		ResponseEntity<String> reset = exchange(HttpMethod.POST, "/api/user/reset_password",
				Map.of("token", token, "password", OTHER_PASSWORD), null);

		assertEquals(HttpStatus.OK, reset.getStatusCode());
		assertEquals(HttpStatus.OK, login(user.getEmail(), OTHER_PASSWORD).getStatusCode(),
				"the new password should work");
		assertEquals(HttpStatus.UNAUTHORIZED, login(user.getEmail(), PASSWORD).getStatusCode(),
				"the old password should not");
	}

	@Test
	@DisplayName("password reset revokes prior refresh sessions but issues a usable new one")
	void resetRevokesPreviousRefreshSessions() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		try {
			String first = bothCookiesFor(user);
			String second = bothCookiesFor(user);
			String legacy = seedLegacyRefresh(user.getId());
			assertEquals(HttpStatus.OK, refresh(legacy).getStatusCode());
			String token = seedToken(user.getId(), ConfirmationTokenType.PASSWORD_RESET);
			ResponseEntity<String> reset = exchange(HttpMethod.POST, "/api/user/reset_password",
					Map.of("token", token, "password", OTHER_PASSWORD), null);
			assertEquals(HttpStatus.OK, reset.getStatusCode());
			assertTrue(setCookies(reset).get(0).startsWith("inatrace-accessToken="));
			assertEquals(HttpStatus.UNAUTHORIZED, refresh(first).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, refresh(second).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, refresh(legacy).getStatusCode());
			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", asAccessCookie(legacy)).getStatusCode());
			String firstRefresh = first.substring(first.indexOf("inatrace-refreshToken="));
			assertEquals(HttpStatus.UNAUTHORIZED, get("/api/user/profile", asAccessCookie(firstRefresh)).getStatusCode());
			assertEquals(HttpStatus.OK, get("/api/user/profile", first.split("; ", 2)[0]).getStatusCode(),
					"reset does not revoke an already-issued access JWT");
			String replacement = cookieNamed(reset, "inatrace-refreshToken");
			assertEquals(HttpStatus.OK, refresh(replacement).getStatusCode());
		} finally {
			cleanupAuthUser(user.getId());
		}
	}

	@Test
	@DisplayName("an old-password login racing a reset cannot leave a valid refresh session")
	void oldPasswordLoginRacingResetCannotSurvive() throws Exception {
		for (int attempt = 0; attempt < 4; attempt++) {
			User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
			ExecutorService executor = Executors.newFixedThreadPool(2);
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			try {
				String resetToken = seedToken(user.getId(), ConfirmationTokenType.PASSWORD_RESET);
				Future<ResponseEntity<String>> login = executor.submit(() -> {
					ready.countDown();
					if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
					return login(user.getEmail(), PASSWORD);
				});
				Future<ResponseEntity<String>> reset = executor.submit(() -> {
					ready.countDown();
					if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
					return exchange(HttpMethod.POST, "/api/user/reset_password",
							Map.of("token", resetToken, "password", OTHER_PASSWORD), null);
				});
				assertTrue(ready.await(20, TimeUnit.SECONDS));
				start.countDown();
				ResponseEntity<String> loginResponse = login.get(45, TimeUnit.SECONDS);
				ResponseEntity<String> resetResponse = reset.get(45, TimeUnit.SECONDS);
				assertEquals(HttpStatus.OK, resetResponse.getStatusCode());
				assertTrue(loginResponse.getStatusCode() == HttpStatus.OK
						|| loginResponse.getStatusCode() == HttpStatus.UNAUTHORIZED,
						"old login must either finish before reset or be refused");
				if (loginResponse.getStatusCode() == HttpStatus.OK) {
					assertEquals(HttpStatus.UNAUTHORIZED,
							refresh(cookieNamed(loginResponse, "inatrace-refreshToken")).getStatusCode(),
							"a session created before reset must be revoked");
				}
				assertEquals(HttpStatus.OK,
						refresh(cookieNamed(resetResponse, "inatrace-refreshToken")).getStatusCode());
				assertEquals(HttpStatus.UNAUTHORIZED, login(user.getEmail(), PASSWORD).getStatusCode());
				assertEquals(HttpStatus.OK, login(user.getEmail(), OTHER_PASSWORD).getStatusCode());
			} finally {
				start.countDown();
				executor.shutdownNow();
				assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
				cleanupAuthUser(user.getId());
			}
		}
	}

	@Test
	@DisplayName("a reset token is spent once")
	void resetTokenCannotBeReused() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String token = seedToken(user.getId(), ConfirmationTokenType.PASSWORD_RESET);

		assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/user/reset_password",
				Map.of("token", token, "password", OTHER_PASSWORD), null).getStatusCode());

		ResponseEntity<String> second = exchange(HttpMethod.POST, "/api/user/reset_password",
				Map.of("token", token, "password", "yet-another-password"), null);

		assertEquals(HttpStatus.UNAUTHORIZED, second.getStatusCode(),
				"a spent token should not work a second time");
	}

	@Test
	@DisplayName("reset_password refuses a password below the length rule")
	void resetRejectsWeakPassword() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);
		String token = seedToken(user.getId(), ConfirmationTokenType.PASSWORD_RESET);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/reset_password",
				Map.of("token", token, "password", "short"), null);

		assertTrue(response.getStatusCode().is4xxClientError(),
				"PasswordTools requires at least 8 characters");
	}

	// ------------------------------------------------------------ email confirmation

	@Test
	@DisplayName("confirm_email moves an unconfirmed account to CONFIRMED_EMAIL")
	void confirmEmailAdvancesStatus() {
		User user = seedUser(UserStatus.UNCONFIRMED, UserRole.USER);
		String token = seedToken(user.getId(), ConfirmationTokenType.CONFIRM_EMAIL);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/confirm_email",
				Map.of("token", token), null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(UserStatus.CONFIRMED_EMAIL, statusOf(user.getEmail()));
		// Still not enough to log in -- an administrator has to activate the account.
		assertEquals(HttpStatus.FORBIDDEN, login(user.getEmail(), PASSWORD).getStatusCode());
	}

	@Test
	@DisplayName("confirm_email refuses a token that was never issued")
	void confirmEmailRejectsUnknownToken() {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/user/confirm_email",
				Map.of("token", UUID.randomUUID().toString()), null);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
	}

	// ---------------------------------------------------------------- authorisation

	@Test
	@DisplayName("an ordinary user cannot read the administrator's user list")
	void plainUserCannotListAllUsers() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);

		ResponseEntity<String> response = get("/api/user/admin/list", accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
				"@PreAuthorize should refuse a USER, and refuse it as forbidden rather than unauthorised");
	}

	@Test
	@DisplayName("an authorization refusal is a JSON API error when JSON is accepted")
	void plainUserReceivesJsonErrorFromAdministratorList() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);

		ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/user/admin/list", null, accessCookieFor(user),
				MediaType.APPLICATION_JSON);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertApiError(response, "UNAUTHORIZED", "Unauthorized");
	}

	@Test
	@DisplayName("a system administrator can read the administrator's user list")
	void systemAdminCanListAllUsers() {
		User admin = seedUser(UserStatus.ACTIVE, UserRole.SYSTEM_ADMIN);

		ResponseEntity<String> response = get("/api/user/admin/list", accessCookieFor(admin));

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	@DisplayName("an ordinary user cannot reach the regional administrator's list either")
	void plainUserCannotListRegionalUsers() {
		User user = seedUser(UserStatus.ACTIVE, UserRole.USER);

		ResponseEntity<String> response = get("/api/user/regional-admin/list", accessCookieFor(user));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	// ---------------------------------------------------------------------- helpers

	private TransactionTemplate tx() {
		return new TransactionTemplate(txManager);
	}

	private List<ResponseEntity<String>> concurrentLogins(String email, int count) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(count);
		CountDownLatch ready = new CountDownLatch(count);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(20, TimeUnit.SECONDS)) {
						throw new IllegalStateException("timed out waiting for concurrent logins");
					}
					return login(email, PASSWORD);
				}));
			}
			assertTrue(ready.await(20, TimeUnit.SECONDS), "login workers should be ready");
			start.countDown();
			List<ResponseEntity<String>> responses = new ArrayList<>();
			for (Future<ResponseEntity<String>> future : futures) {
				responses.add(future.get(45, TimeUnit.SECONDS));
			}
			return responses;
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "login workers should finish");
		}
	}

	private void cleanupAuthUser(Long userId) {
		tx().executeWithoutResult(s -> {
			em.createQuery("delete from AuthenticationToken t where t.user.id = :uid")
					.setParameter("uid", userId).executeUpdate();
			em.createQuery("delete from ConfirmationToken t where t.user.id = :uid")
					.setParameter("uid", userId).executeUpdate();
			User user = em.find(User.class, userId);
			if (user != null) {
				em.remove(user);
			}
		});
	}

	private Long seedRefreshWithExpiration(Long userId, Instant expiration) {
		return tx().execute(s -> {
			AuthenticationToken session = new AuthenticationToken(em.find(User.class, userId));
			session.setToken(UUID.randomUUID().toString());
			session.setExpiration(expiration);
			em.persist(session);
			em.flush();
			return session.getId();
		});
	}

	private String seedLegacyRefresh(Long userId) {
		return tx().execute(s -> {
			User managed = em.find(User.class, userId);
			String raw = signedJwt(managed, null, tokenService.getRefreshTokenExpirationSec(), jwtSigningKey);
			AuthenticationToken session = new AuthenticationToken(managed);
			try {
				session.updateToken(raw, tokenService.getRefreshTokenExpirationSec());
			} catch (Exception e) {
				throw new IllegalStateException("could not create legacy token", e);
			}
			em.persist(session);
			return "inatrace-refreshToken=" + raw;
		});
	}

	/** Creates a committed user, so the request thread can see it. Emails are unique per call. */
	private User seedUser(UserStatus status, UserRole role) {
		String email = "u-" + UUID.randomUUID() + "@test.invalid";
		return tx().execute(s -> {
			User user = new User();
			user.setEmail(email);
			user.setName("Test");
			user.setSurname("User");
			user.setLanguage(Language.EN);
			user.setStatus(status);
			user.setRole(role);
			user.setPassword(passwordEncoder.encode(PASSWORD));
			em.persist(user);
			em.flush();
			return user;
		});
	}

	/**
	 * Issues a confirmation token the same way the service does and returns the plaintext half,
	 * which is what would otherwise have been mailed out.
	 */
	private String seedToken(Long userId, ConfirmationTokenType type) {
		return tx().execute(s -> {
			User user = em.find(User.class, userId);
			Pair<ConfirmationToken, String> pair = ConfirmationToken.create(user, type);
			em.persist(pair.getLeft());
			em.flush();
			return pair.getRight();
		});
	}

	private long countTokens(Long userId, ConfirmationTokenType type) {
		return tx().execute(s -> em.createQuery(
						"select count(t) from ConfirmationToken t where t.user.id = :uid and t.type = :type",
						Long.class)
				.setParameter("uid", userId)
				.setParameter("type", type)
				.getSingleResult());
	}

	private UserStatus statusOf(String email) {
		return tx().execute(s -> em.createQuery(
						"select u.status from User u where u.email = :email", UserStatus.class)
				.setParameter("email", email)
				.getSingleResult());
	}

	private void setStatus(Long userId, UserStatus status) {
		tx().executeWithoutResult(s -> {
			User user = em.find(User.class, userId);
			user.setStatus(status);
			em.flush();
		});
	}

	private void setRole(Long userId, UserRole role) {
		tx().executeWithoutResult(s -> {
			User user = em.find(User.class, userId);
			user.setRole(role);
			em.flush();
		});
	}

	private String nameOf(Long userId) {
		return tx().execute(s -> em.find(User.class, userId).getName());
	}

	private ResponseEntity<String> login(String email, String password) {
		return exchange(HttpMethod.POST, "/api/user/login",
				Map.of("username", email, "password", password), null);
	}

	private ResponseEntity<String> refresh(String cookie) {
		return exchange(HttpMethod.POST, "/api/user/refresh_authentication", null, cookie);
	}

	private String asAccessCookie(String refreshCookie) {
		return "inatrace-accessToken=" + refreshCookie.split("=", 2)[1];
	}

	/** Issue a JWT directly to characterize old and invalid tokens independently of the service. */
	private String signedJwt(User user, String purpose, int expirationSeconds, String key) {
		Instant now = Instant.now();
		JWTCreator.Builder builder = JWT.create().withSubject(user.getEmail())
				.withIssuedAt(now.minusSeconds(120))
				.withNotBefore(now.minusSeconds(120))
				.withExpiresAt(now.plusSeconds(expirationSeconds))
				.withClaim("nme", user.getName())
				.withClaim("snm", user.getSurname())
				.withClaim("rle", user.getRole().toString())
				.withClaim("dbi", user.getId());
		if (purpose != null) builder.withClaim("token_use", purpose);
		return builder.sign(Algorithm.HMAC256(key));
	}

	/** Logs the user in and returns the access cookie as a Cookie header value. */
	private String accessCookieFor(User user) {
		return cookieNamed(login(user.getEmail(), PASSWORD), "inatrace-accessToken");
	}

	/** Logs the user in and returns both cookies, for the endpoints that read the refresh one. */
	private String bothCookiesFor(User user) {
		ResponseEntity<String> response = login(user.getEmail(), PASSWORD);
		return String.join("; ",
				cookieNamed(response, "inatrace-accessToken"),
				cookieNamed(response, "inatrace-refreshToken"));
	}

	private String cookieNamed(ResponseEntity<String> response, String name) {
		return setCookies(response).stream()
				.filter(c -> c.startsWith(name + "="))
				.map(c -> c.split(";", 2)[0])
				.findFirst()
				.orElseThrow(() -> new AssertionError("no " + name + " cookie on the response"));
	}

	private List<String> setCookies(ResponseEntity<String> response) {
		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		return cookies == null ? List.of() : cookies;
	}

	private void assertNoAuthCookies(ResponseEntity<String> response) {
		assertTrue(setCookies(response).stream().noneMatch(c -> c.startsWith("inatrace-accessToken=")
						&& !c.contains("Max-Age=0")),
				"a failed login must not hand out an access cookie");
	}

	private void assertApiError(ResponseEntity<String> response, String status, String errorMessage) {
		assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"status\":\"" + status + "\""), response.getBody());
		assertTrue(response.getBody().contains("\"errorMessage\":\"" + errorMessage + "\""), response.getBody());
	}

	private ResponseEntity<String> get(String path, String cookie) {
		return exchange(HttpMethod.GET, path, null, cookie);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String cookie) {
		return exchange(method, path, body, cookie, null);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String cookie, MediaType accept) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (accept != null) {
			headers.setAccept(List.of(accept));
		}
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}
}
