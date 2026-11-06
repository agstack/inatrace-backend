package com.abelium.inatrace.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiRequestObservabilityIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;

    @Test
    void authenticatedRequestsRecordActorAndRouteWithoutCredentials() throws Exception {
        User user = new User();
        user.setEmail("request-id-sensitive@example.test");
        user.setName("Test");
        user.setSurname("User");
        user.setPassword("unused");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        em.flush();
        String token = tokenService.createAccessToken(user);
        String id = UUID.randomUUID().toString();

        Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestObservability.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MvcResult result = mockMvc.perform(get("/api/user/profile")
                    .header(ApiRequestObservability.HEADER, id)
                    .queryParam("password", "sensitive-query")
                    .cookie(new Cookie(accessCookieName, token))).andReturn();
            assertEquals(200, result.getResponse().getStatus());
            assertEquals(id, result.getResponse().getHeader(ApiRequestObservability.HEADER));
            finish(result);
            assertEquals(1, appender.list.size());
            String event = appender.list.get(0).getFormattedMessage();
            assertTrue(event.contains("route=/api/user/profile"), event);
            assertTrue(event.contains("user_id=" + user.getId()), event);
            assertTrue(event.contains("role=USER"), event);
            assertFalse(event.contains(token), event);
            assertFalse(event.contains(user.getEmail()), event);
            assertFalse(event.contains("sensitive-query"), event);

            MvcResult denied = mockMvc.perform(get("/api/user/admin/list")
                    .cookie(new Cookie(accessCookieName, token))).andReturn();
            assertEquals(403, denied.getResponse().getStatus());
            assertNotNull(denied.getResponse().getHeader(ApiRequestObservability.HEADER));
            finish(denied);
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.get(1).getFormattedMessage().contains("status=403"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void validationAndMethodErrorsRetainCorrelationHeaders() throws Exception {
        String id = UUID.randomUUID().toString();
        MvcResult badRequest = mockMvc.perform(post("/api/user/login")
                .header(ApiRequestObservability.HEADER, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"sensitive-body\"}")).andReturn();
        assertEquals(400, badRequest.getResponse().getStatus());
        assertEquals(id, badRequest.getResponse().getHeader(ApiRequestObservability.HEADER));
        finish(badRequest);

        MvcResult wrongMethod = mockMvc.perform(get("/api/user/login")).andReturn();
        assertEquals(405, wrongMethod.getResponse().getStatus());
        assertNotNull(wrongMethod.getResponse().getHeader(ApiRequestObservability.HEADER));
        finish(wrongMethod);
    }

    // MockMvc does not simulate the servlet container's request-destroyed callback.
    private void finish(MvcResult result) {
        new ApiRequestObservability.CompletionListener().requestDestroyed(
                new ServletRequestEvent(new MockServletContext(), result.getRequest()));
    }
}
