package com.abelium.inatrace.security.configuration;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.user.UserService;
import com.abelium.inatrace.db.entities.common.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.abelium.inatrace.security.service.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class TokenAuthenticationFilter extends OncePerRequestFilter {
	
    protected final Logger logger = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

    @Autowired
    private TokenService tokenEngine;

    @Autowired
    private UserService userEngine;

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
    	String token = null;
    	
        try {
            token = getAccessTokenFromCookie(httpServletRequest);
            if (StringUtils.hasText(token)) {
                CustomUserDetails tokenDetails = tokenEngine.validateToken(token);
                User user = tokenDetails == null ? null : userEngine.findActiveUserById(tokenDetails.getUserId());
                if (user != null) {
                    CustomUserDetails userDetails = new CustomUserDetails(user);
	                PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(userDetails, null, userDetails.getAuthorities());
	                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));
	                SecurityContextHolder.getContext().setAuthentication(authentication);
            	}
            }
        } catch (Exception e) {
            logger.debug("Unable to authenticate access-token cookie", e);
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private String getAccessTokenFromCookie(HttpServletRequest request) {
    	Optional<Cookie> optCookie = tokenEngine.getAccessCookie(request.getCookies());
    	return optCookie.isPresent() ? optCookie.get().getValue() : null;
    }

}
