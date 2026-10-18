package com.abelium.inatrace.components.common;

import com.abelium.inatrace.configuration.ApiRequestObservability;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.UserRole;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import java.sql.Date;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Lazy
@Service
public class TokenService extends BaseService {
	 protected final Logger logger = LoggerFactory.getLogger(TokenService.class);

	private static final String TOKEN_USE_CLAIM = "token_use";
	private static final String ACCESS_TOKEN_USE = "access";
	private static final String REFRESH_TOKEN_USE = "refresh";

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessTokenCookieName;

    @Value("${INATrace.auth.refreshTokenCookieName}")
    private String refreshTokenCookieName;
    
    @Value("${INATrace.auth.accessTokenExpirationSec}")
    private Integer accessTokenExpirationSec;
    
    @Value("${INATrace.auth.refreshTokenExpirationSec}")
    private Integer refreshTokenExpirationSec;
    
    @Value("${INATrace.auth.jwtSigningKey}")
    private String jwtSigningKey;

    
    public int getAccessTokenExpirationSec() {
    	return accessTokenExpirationSec;
    }

    public int getRefreshTokenExpirationSec() {
    	return refreshTokenExpirationSec;
    }
    
	public CustomUserDetails validateToken(String token) {
		// Legacy access and refresh JWTs are indistinguishable: accepting untyped JWTs
		// here would also accept a refresh token after its stored session is revoked.
		JWTVerifier verifier = JWT.require(Algorithm.HMAC256(jwtSigningKey))
			.withClaim(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE)
			.build();
		try {
			DecodedJWT dct = verifier.verify(token);
			CustomUserDetails cud = new CustomUserDetails(dct.getClaim("dbi").asLong(),
					dct.getSubject(), 
					dct.getClaim("nme").asString(), 
					dct.getClaim("snm").asString(), 
					UserRole.valueOf(dct.getClaim("rle").asString()));
			return cud;
		} catch (Exception e) {
			logger.debug("JWT validation failed request_id={}", ApiRequestObservability.requestId());
		}
		return null;
	}

    public String createAccessToken(User user) {
		return createToken(user, accessTokenExpirationSec, ACCESS_TOKEN_USE, null);
    }
    	    	
    public String createRefreshToken(User user) {
        return createToken(user, refreshTokenExpirationSec, REFRESH_TOKEN_USE, UUID.randomUUID().toString());
    }

    private String createToken(User user, int expirationSec, String purpose, String tokenId) {
    	Instant now = Instant.now();
    	Instant exp = now.plusSeconds(expirationSec);
    	
		JWTCreator.Builder builder = JWT.create().withSubject(user.getEmail()) // username
		   .withExpiresAt(Date.from(exp))
		   .withIssuedAt(Date.from(now))
		   .withNotBefore(Date.from(now))
		   .withClaim("nme", user.getName())
		   .withClaim("snm", user.getSurname())
		   .withClaim("rle", user.getRole().toString())
		   .withClaim("dbi", user.getId())
		   .withClaim(TOKEN_USE_CLAIM, purpose);
		if (tokenId != null) {
			builder.withJWTId(tokenId);
		}
		return builder.sign(Algorithm.HMAC256(jwtSigningKey));
    }
    
    public String createAccessCookie(String accessToken) {
    	return createCookie(accessTokenCookieName, accessToken, accessTokenExpirationSec);
    }
    
    public String createRefreshCookie(String refreshToken) {
    	return createCookie(refreshTokenCookieName, refreshToken, refreshTokenExpirationSec);
    }
    
    public String createRemoveAccessCookie() {
    	return createCookie(accessTokenCookieName, null, 0);
    }
    
    public String createRemoveRefreshCookie() {
    	return createCookie(refreshTokenCookieName, null, 0);
    }    
    
    public String createCookie(String name, String token, int expirationSec) {
		HttpCookie cookie = ResponseCookie.from(name, token)
	    	.maxAge(expirationSec)
	    	.httpOnly(true)
	    	.path("/")
	    	.build();
		return cookie.toString();
    }
    
    public Optional<Cookie> getAccessCookie(Cookie[] cookies) {
	return cookies == null ? Optional.empty()
			: Arrays.stream(cookies).filter(cookie -> accessTokenCookieName.equals(cookie.getName())).findFirst();
    }

    public Optional<Cookie> getRefreshCookie(Cookie[] cookies) {
	return cookies == null ? Optional.empty()
			: Arrays.stream(cookies).filter(cookie -> refreshTokenCookieName.equals(cookie.getName())).findFirst();
    }
    
    public String getRefreshToken(Cookie[] cookies) {
    	if (cookies != null) {
			Optional<Cookie> optCookie = getRefreshCookie(cookies);
			if (optCookie.isPresent()) {
				return optCookie.get().getValue();
			}
    	}
    	return null;
    }
    

}
