package com.abelium.inatrace.db.entities.auth;

import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.types.Status;
import com.abelium.inatrace.types.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthenticationToken#isValid()} decides whether a refresh token still buys a new access
 * token, so anything it overlooks keeps a session alive that should have ended.
 *
 * <p>The three conditions were written without brackets — {@code a && b && c || d}, where {@code d}
 * is "the user is CONFIRMED_EMAIL". Java binds {@code &&} tighter than {@code ||}, so for a user in
 * that state the expression was true on the user's status alone: the row's expiry and its disabled
 * flag were never read. Such a session could not be revoked and did not run out. The clause is now
 * gone altogether: only an ACTIVE account holds a session, which is the rule login and password
 * reset apply. These tests are written against the entity directly because that is where the
 * mistake is; they need no context and run in milliseconds.
 */
class AuthenticationTokenValidityTest {

	@Test
	@DisplayName("an expired token is invalid, even for a CONFIRMED_EMAIL user")
	void expiredTokenIsInvalidForConfirmedEmailUser() {
		AuthenticationToken token = token(UserStatus.CONFIRMED_EMAIL, Status.ACTIVE,
				Instant.now().minus(1, ChronoUnit.HOURS));

		assertFalse(token.isValid(), "expiry must be honoured whatever the user's status");
	}

	@Test
	@DisplayName("a disabled token is invalid, even for a CONFIRMED_EMAIL user")
	void disabledTokenIsInvalidForConfirmedEmailUser() {
		AuthenticationToken token = token(UserStatus.CONFIRMED_EMAIL, Status.DISABLED,
				Instant.now().plus(1, ChronoUnit.HOURS));

		assertFalse(token.isValid(), "logging out disables the row; that must end the session");
	}

	@Test
	@DisplayName("a live token is still invalid for a CONFIRMED_EMAIL user")
	void liveTokenIsInvalidForConfirmedEmailUser() {
		// The account has not been approved, so it may not hold a session at all -- the same rule
		// login and password reset apply. A row for such a user could only exist from before the
		// fix, and it must not keep working.
		AuthenticationToken token = token(UserStatus.CONFIRMED_EMAIL, Status.ACTIVE,
				Instant.now().plus(1, ChronoUnit.HOURS));

		assertFalse(token.isValid(), "only an ACTIVE account holds a session");
	}

	@Test
	@DisplayName("a disabled token is invalid for an active user")
	void disabledTokenIsInvalidForActiveUser() {
		AuthenticationToken token = token(UserStatus.ACTIVE, Status.DISABLED,
				Instant.now().plus(1, ChronoUnit.HOURS));

		assertFalse(token.isValid());
	}

	@Test
	@DisplayName("a live token for an active user is valid")
	void liveTokenForActiveUserIsValid() {
		// The control: the conditions are still satisfiable, so a failure above is the bracketing
		// and not an over-strict rewrite.
		AuthenticationToken token = token(UserStatus.ACTIVE, Status.ACTIVE,
				Instant.now().plus(1, ChronoUnit.HOURS));

		assertTrue(token.isValid());
	}

	private static AuthenticationToken token(UserStatus userStatus, Status tokenStatus, Instant expiry) {
		User user = new User();
		user.setStatus(userStatus);

		AuthenticationToken token = new AuthenticationToken(user);
		token.setStatus(tokenStatus);
		token.setExpiration(expiry);
		return token;
	}
}
