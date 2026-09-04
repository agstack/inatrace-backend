package com.abelium.inatrace.db.entities.auth;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.tools.EncryptionTools;
import com.abelium.inatrace.types.Status;
import com.abelium.inatrace.types.UserStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(indexes = { @Index(columnList = "token" ) })
public class AuthenticationToken extends BaseEntity {
    
    @Version
    private long entityVersion;
    
    @Column(length = Lengths.AUTH_TOKEN, nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiration;
    
    @ManyToOne
    @NotNull
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = Lengths.ENUM)
    private Status status = Status.ACTIVE;
    
    protected AuthenticationToken() {
    	super();
    }
    
    public AuthenticationToken(User user) {
		super();
		this.user = user;
	}

	public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
    
    public Instant getExpiration() {
		return expiration;
	}

	public void setExpiration(Instant expiration) {
		this.expiration = expiration;
	}

	public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isValid() {
        // Only an ACTIVE account holds a session, the same rule login and password reset apply
        // before issuing cookies. This used to read `a && b && c || d` with d being "the user is
        // CONFIRMED_EMAIL"; && binds tighter than ||, so for such a user the row's expiry and
        // disabled flag were never consulted. No path issues a token to a CONFIRMED_EMAIL user
        // any more, so the clause is gone rather than bracketed.
        return getExpiration().isAfter(Instant.now())
                && getStatus() == Status.ACTIVE
                && getUser().getStatus() == UserStatus.ACTIVE;
    }

    private Instant calculateExpiryDate(long expiryTimeInSeconds) {
        return Instant.now().plus(expiryTimeInSeconds, ChronoUnit.SECONDS);
    }

    public void updateToken(String token, long expirationSeconds) throws ApiException {
        setExpiration(calculateExpiryDate(expirationSeconds));
        setToken(encrypt(token));
        setStatus(Status.ACTIVE);
    }
    
    public static String encrypt(String token) throws ApiException {
        try {
            return EncryptionTools.sha3_384(token);
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ApiStatus.ERROR, "Encryption error");
        }
    }
    
}
