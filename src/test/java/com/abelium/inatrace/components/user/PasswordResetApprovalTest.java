package com.abelium.inatrace.components.user;

import com.abelium.inatrace.db.entities.auth.ConfirmationToken;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.types.ConfirmationTokenType;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.lang3.tuple.Pair;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Password reset must not become a way around administrator approval.
 *
 * <p>Anyone may register. Confirming the email address moves the account to CONFIRMED_EMAIL, where
 * it waits for an administrator to activate it, and login refuses it until then — that gate is the
 * only admission control the system has. The reset flow ended by calling the same {@code loginUser}
 * that login calls, without the gate, so registering an address you control and then asking for a
 * password reset produced a working session for an account nobody had approved.
 *
 * <p>Paths carry the {@code /api} prefix that {@code PrefixedApiRequestHandler} adds to every
 * mapping in the {@code com.abelium} packages; no controller declares it and no context path is
 * configured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PasswordResetApprovalTest {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	private static final String PASSWORD = "correct-horse-battery";
	private static final String NEW_PASSWORD = "a-different-password";

	@Autowired
	private TestRestTemplate rest;

	@PersistenceContext
	private EntityManager em;

	@Autowired
	private PlatformTransactionManager txManager;

	/**
	 * These endpoints answer 401 and 403 often, and the JDK's HttpURLConnection reacts to a 401 by
	 * retrying the request with credentials — which it cannot do once the body is streamed, so it
	 * throws instead of returning the response the assertion is about.
	 */
	@BeforeEach
	void useAClientThatDoesNotRetryOn401() {
		rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
	}

	@Test
	@DisplayName("reset does not sign in an account that is still awaiting approval")
	void resetDoesNotStartASessionForAnUnapprovedAccount() {
		User user = seedUser(UserStatus.CONFIRMED_EMAIL);
		String token = seedResetToken(user.getId());

		ResponseEntity<String> reset = post("/api/user/reset_password",
				Map.of("token", token, "password", NEW_PASSWORD));

		assertTrue(setCookies(reset).stream().noneMatch(c -> c.startsWith("inatrace-accessToken=")),
				"an unapproved account must not be handed a session by the reset flow");
		assertEquals(HttpStatus.FORBIDDEN,
				post("/api/user/login", Map.of("username", user.getEmail(), "password", NEW_PASSWORD))
						.getStatusCode(),
				"and it still cannot log in until an administrator activates it");
	}

	@Test
	@DisplayName("reset still signs in an approved account")
	void resetStillStartsASessionForAnActiveAccount() {
		// The other half of the rule: the frontend routes to /home straight after a reset, so
		// closing the bypass must not cost an approved user their session.
		User user = seedUser(UserStatus.ACTIVE);
		String token = seedResetToken(user.getId());

		ResponseEntity<String> reset = post("/api/user/reset_password",
				Map.of("token", token, "password", NEW_PASSWORD));

		assertEquals(HttpStatus.OK, reset.getStatusCode());
		assertTrue(setCookies(reset).stream().anyMatch(c -> c.startsWith("inatrace-accessToken=")),
				"an approved user should still be signed in by a successful reset");
	}

	@Test
	@DisplayName("the reset still changes the password of an unapproved account")
	void resetStillChangesThePassword() {
		// The account is not entitled to a session; it is entitled to its password being set.
		// Asserted so the fix cannot quietly become "reset does nothing for these users".
		User user = seedUser(UserStatus.CONFIRMED_EMAIL);
		String stored = user.getPassword();
		String token = seedResetToken(user.getId());

		assertEquals(HttpStatus.OK, post("/api/user/reset_password",
				Map.of("token", token, "password", NEW_PASSWORD)).getStatusCode());
		assertNotEquals(stored, passwordOf(user.getId()), "the stored password should have changed");
	}

	// ---------------------------------------------------------------------- helpers

	private TransactionTemplate tx() {
		return new TransactionTemplate(txManager);
	}

	private User seedUser(UserStatus status) {
		String email = "u-" + UUID.randomUUID() + "@test.invalid";
		return tx().execute(s -> {
			User user = new User();
			user.setEmail(email);
			user.setName("Test");
			user.setSurname("User");
			user.setLanguage(Language.EN);
			user.setStatus(status);
			user.setRole(UserRole.USER);
			user.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
			em.persist(user);
			em.flush();
			return user;
		});
	}

	/** Issues a reset token the way the service does, returning the half that would be mailed. */
	private String seedResetToken(Long userId) {
		return tx().execute(s -> {
			User user = em.find(User.class, userId);
			Pair<ConfirmationToken, String> pair =
					ConfirmationToken.create(user, ConfirmationTokenType.PASSWORD_RESET);
			em.persist(pair.getLeft());
			em.flush();
			return pair.getRight();
		});
	}

	private String passwordOf(Long userId) {
		return tx().execute(s -> em.createQuery(
						"select u.password from User u where u.id = :id", String.class)
				.setParameter("id", userId)
				.getSingleResult());
	}

	private ResponseEntity<String> post(String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private List<String> setCookies(ResponseEntity<String> response) {
		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		return cookies == null ? List.of() : cookies;
	}
}
