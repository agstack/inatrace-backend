package com.abelium.inatrace.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abelium.inatrace.components.common.StorageService;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.analytics.IpGeoLocationEngine;
import com.abelium.inatrace.components.exceptionhandling.SpringExceptionResponseBuilder;
import com.abelium.inatrace.components.mail.MailEngine;
import com.abelium.inatrace.components.user.UserService;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.api.errors.ApiImageException;
import com.abelium.inatrace.api.errors.HtmlPageException;
import com.abelium.inatrace.security.configuration.TokenAuthenticationFilter;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.maxmind.geoip2.DatabaseReader;
import jakarta.mail.MessagingException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiRequestObservabilityTest {
    private static final String ID = "B54A8B1D-9201-4C30-96A5-7B5CFCBC9644";

    @AfterEach
    void cleanup() { MDC.clear(); }

    @Test
    void requestIdsMustBeSingleCanonicalUuids() {
        assertEquals(ID.toLowerCase(), ApiRequestObservability.chooseId(List.of(ID)));
        assertEquals("123e4567-e89b-12d3-a456-426614174000",
                ApiRequestObservability.chooseId(List.of("123e4567-e89b-12d3-a456-426614174000")));
        for (List<String> invalid : List.of(List.<String>of(), List.of("1-1-1-1-1"),
                List.of(ID, ID), List.of(ID + "," + ID), List.of("private-value"), List.of("a".repeat(4096)))) {
            String generated = ApiRequestObservability.chooseId(invalid);
            assertEquals(4, UUID.fromString(generated).version());
            assertNotEquals(ID.toLowerCase(), generated);
        }
    }

    @Test
    void duplicateRequestHeadersAreReplacedBeforeTheResponseIsCommitted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addHeader(ApiRequestObservability.HEADER, ID);
        request.addHeader(ApiRequestObservability.HEADER, "private-invalid-header");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ApiRequestObservability.RequestFilter().doFilter(request, response, (req, res) -> {
            String id = ((jakarta.servlet.http.HttpServletResponse) res).getHeader(ApiRequestObservability.HEADER);
            assertEquals(4, UUID.fromString(id).version());
            assertNotEquals(ID.toLowerCase(), id);
            ((jakarta.servlet.http.HttpServletResponse) res).flushBuffer();
        });
        assertTrue(response.isCommitted());
        assertNotNull(response.getHeader(ApiRequestObservability.HEADER));
        new ApiRequestObservability.CompletionListener().requestDestroyed(
                new ServletRequestEvent(new MockServletContext(), request));
    }

    @Test
    void requestHasHeaderMdcAndOneFinalSafeCompletionEvent() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestObservability.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/secret-path");
            request.addHeader(ApiRequestObservability.HEADER, ID);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MDC.put("prior", "previous");
            new ApiRequestObservability.RequestFilter().doFilter(request, response, (req, res) -> {
                assertEquals(ID.toLowerCase(), MDC.get(ApiRequestObservability.MDC_KEY));
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/public/{id}");
                ((jakarta.servlet.http.HttpServletResponse) res).reset();
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(403);
            });
            assertNull(MDC.get(ApiRequestObservability.MDC_KEY));
            assertEquals("previous", MDC.get("prior"));
            assertEquals(ID.toLowerCase(), response.getHeader(ApiRequestObservability.HEADER));
            ServletRequestEvent event = new ServletRequestEvent(new MockServletContext(), request);
            ApiRequestObservability.CompletionListener listener = new ApiRequestObservability.CompletionListener();
            listener.requestDestroyed(event);
            listener.requestDestroyed(event);
            assertEquals(1, appender.list.size());
            String log = appender.list.get(0).getFormattedMessage();
            assertTrue(log.contains("request_id=" + ID.toLowerCase()));
            assertTrue(log.contains("route=/api/public/{id}"));
            assertTrue(log.contains("status=403"));
            assertFalse(log.contains("secret-path"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void nestedErrorDispatchCannotReplaceOriginalRoute() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestObservability.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/private-path");
            MockHttpServletResponse response = new MockHttpServletResponse();
            ApiRequestObservability.RequestFilter filter = new ApiRequestObservability.RequestFilter();
            filter.doFilter(request, response, (req, res) -> {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/public/{id}");
                new ApiRequestObservability.RouteInterceptor().preHandle(
                        (HttpServletRequest) req, (HttpServletResponse) res, new Object());
                request.setDispatcherType(DispatcherType.ERROR);
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/error");
                filter.doFilter(req, res, (nestedRequest, nestedResponse) ->
                        ((HttpServletResponse) nestedResponse).setStatus(500));
                request.setDispatcherType(DispatcherType.REQUEST);
            });
            new ApiRequestObservability.CompletionListener().requestDestroyed(
                    new ServletRequestEvent(new MockServletContext(), request));
            assertEquals(1, appender.list.size());
            String event = appender.list.get(0).getFormattedMessage();
            assertTrue(event.contains("route=/api/public/{id}"), event);
            assertTrue(event.contains("status=500"), event);
            assertFalse(event.contains("private-path"), event);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void asyncTimeoutDoesNotCompleteUntilAsyncIsFinished() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestObservability.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/test");
            request.setAsyncSupported(true);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new ApiRequestObservability.RequestFilter().doFilter(request, response, (req, res) -> {
                AsyncContext async = req.startAsync();
                MockAsyncContext context = (MockAsyncContext) async;
                try {
                    context.getListeners().get(0).onError(new AsyncEvent(context, new RuntimeException("private-error")));
                    context.getListeners().get(0).onTimeout(new AsyncEvent(context));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                assertTrue(appender.list.isEmpty());
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(503);
            });
            assertTrue(appender.list.isEmpty());
            request.getAsyncContext().complete();
            assertEquals(1, appender.list.size());
            assertTrue(appender.list.get(0).getFormattedMessage().contains("status=503"));
            new ApiRequestObservability.CompletionListener().requestDestroyed(
                    new ServletRequestEvent(new MockServletContext(), request));
            assertEquals(1, appender.list.size());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void exceptionDiagnosticsExcludeMessagesOfCausesAndSuppressedExceptions() {
        IllegalArgumentException outer = new IllegalArgumentException("password-secret");
        outer.initCause(new IllegalStateException("sql-secret"));
        outer.addSuppressed(new RuntimeException("jwt-secret"));
        String diagnostic = SafeExceptionLog.describe(outer);
        assertTrue(diagnostic.contains("IllegalArgumentException"));
        assertTrue(diagnostic.contains("IllegalStateException"));
        assertTrue(diagnostic.contains("RuntimeException"));
        assertTrue(diagnostic.contains("ApiRequestObservabilityTest"));
        assertFalse(diagnostic.contains("password-secret"));
        assertFalse(diagnostic.contains("sql-secret"));
        assertFalse(diagnostic.contains("jwt-secret"));
    }

    @Test
    void storageFailuresDoNotLogPathsUrlsOrExceptionMessages() {
        Logger logger = (Logger) LoggerFactory.getLogger(StorageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String id = UUID.randomUUID().toString();
            MDC.put(ApiRequestObservability.MDC_KEY, id);
            StorageService storage = new StorageService();
            assertNull(storage.downloadFileFromUrl("bad-url-secret?token=url-secret"));
            assertNull(storage.downloadLocalFile("/tmp/nonexistent-path-secret-for-inatrace-test"));
            assertEquals(2, appender.list.size());
            for (ILoggingEvent event : appender.list) {
                String message = event.getFormattedMessage();
                assertTrue(message.contains("request_id=" + id), message);
                assertFalse(message.contains("url-secret"), message);
                assertFalse(message.contains("path-secret"), message);
                assertNull(event.getThrowableProxy(), "raw exception attached to log event");
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unexpectedErrorsProduceCorrelatedSanitizedDiagnosticsAnd500() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultControllerExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String id = UUID.randomUUID().toString();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/private-path-marker");
            request.addHeader(ApiRequestObservability.HEADER, id);
            request.addHeader("Authorization", "jwt-secret-marker");
            request.setQueryString("password=secret-query-marker");
            request.setContent("{\"coordinates\":\"secret-body-marker\"}".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();
            DefaultControllerExceptionHandler handler = new DefaultControllerExceptionHandler();
            ReflectionTestUtils.setField(handler, "exceptionResponseBuilder", new SpringExceptionResponseBuilder());
            new ApiRequestObservability.RequestFilter().doFilter(request, response, (req, res) -> {
                RuntimeException error = new RuntimeException("secret-exception-marker",
                        new IllegalArgumentException("secret-cause-marker"));
                int status = handler.handleException(error, (jakarta.servlet.http.HttpServletRequest) req)
                        .getStatusCode().value();
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(status);
            });
            assertEquals(500, response.getStatus());
            assertEquals(id, response.getHeader(ApiRequestObservability.HEADER));
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            String message = event.getFormattedMessage();
            assertTrue(message.contains("request_id=" + id), message);
            assertTrue(message.contains("IllegalArgumentException"), message);
            assertFalse(message.contains("marker"), message);
            assertNull(event.getThrowableProxy());
            new ApiRequestObservability.CompletionListener().requestDestroyed(
                    new ServletRequestEvent(new MockServletContext(), request));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void invalidJwtIsNotLoggedEvenAtDebugLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(TokenService.class);
        ch.qos.logback.classic.Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        try {
            TokenService service = new TokenService();
            ReflectionTestUtils.setField(service, "jwtSigningKey", "test-only-signing-key");
            String id = UUID.randomUUID().toString();
            MDC.put(ApiRequestObservability.MDC_KEY, id);
            assertNull(service.validateToken("secret-jwt-marker"));
            assertEquals(1, appender.list.size());
            assertTrue(appender.list.get(0).getFormattedMessage().contains(id));
            assertFalse(appender.list.get(0).getFormattedMessage().contains("secret-jwt-marker"));
            assertNull(appender.list.get(0).getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
        }
    }

    @Test
    void authenticationBackendFailureHasSafeCorrelatedDiagnostic() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TokenAuthenticationFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TokenService tokens = mock(TokenService.class);
            UserService users = mock(UserService.class);
            CustomUserDetails principal = mock(CustomUserDetails.class);
            Cookie cookie = new Cookie("access", "private-token-marker");
            when(tokens.getAccessCookie(any())).thenReturn(Optional.of(cookie));
            when(tokens.validateToken(cookie.getValue())).thenReturn(principal);
            when(principal.getUserId()).thenReturn(42L);
            when(users.findActiveUserById(42L)).thenThrow(new IllegalStateException("private-sql-marker"));
            TokenAuthenticationFilter filter = new TokenAuthenticationFilter();
            ReflectionTestUtils.setField(filter, "tokenEngine", tokens);
            ReflectionTestUtils.setField(filter, "userEngine", users);
            String id = UUID.randomUUID().toString();
            MDC.put(ApiRequestObservability.MDC_KEY, id);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/profile");
            request.setCookies(cookie);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(204));
            assertEquals(204, response.getStatus(), "the filter must preserve its existing continuation behavior");
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("request_id=" + id));
            assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
            assertFalse(event.getFormattedMessage().contains("private-sql-marker"));
            assertFalse(event.getFormattedMessage().contains("private-token-marker"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void controlled4xxAnd5xxAreClassifiedWithoutLeakingMessages() {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultControllerExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DefaultControllerExceptionHandler handler = new DefaultControllerExceptionHandler();
            ReflectionTestUtils.setField(handler, "exceptionResponseBuilder", new SpringExceptionResponseBuilder());
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private-path-marker");
            String id = UUID.randomUUID().toString();
            MDC.put(ApiRequestObservability.MDC_KEY, id);
            handler.handleApiException(new ApiException(ApiStatus.UNAUTHORIZED, "private-message-marker"), request);
            handler.handleApiException(new ApiException(ApiStatus.ERROR, "private-message-marker"), request);
            handler.handleApiImageException(new ApiImageException(HttpStatus.INTERNAL_SERVER_ERROR), request);
            handler.handleHtmlPageException(new HtmlPageException(HttpStatus.INTERNAL_SERVER_ERROR, "private-message-marker"), request);
            assertEquals(4, appender.list.size());
            assertEquals(Level.WARN, appender.list.get(0).getLevel());
            assertTrue(appender.list.get(0).getFormattedMessage().contains("api_status=UNAUTHORIZED"));
            for (int i = 1; i < appender.list.size(); i++) assertEquals(Level.ERROR, appender.list.get(i).getLevel());
            assertTrue(appender.list.get(1).getFormattedMessage().contains("api_status=ERROR"));
            for (ILoggingEvent event : appender.list) {
                assertTrue(event.getFormattedMessage().contains("request_id=" + id));
                assertFalse(event.getFormattedMessage().contains("private-message-marker"));
                assertFalse(event.getFormattedMessage().contains("private-path-marker"));
                assertNull(event.getThrowableProxy());
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void clientAbortDoesNotLogTheRawUriOrExceptionMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultControllerExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String id = UUID.randomUUID().toString();
            MDC.put(ApiRequestObservability.MDC_KEY, id);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private-path-marker");
            request.setQueryString("token=private-query-marker");
            new DefaultControllerExceptionHandler().handleClientAbortException(
                    new ClientAbortException(new java.io.IOException("private-exception-marker")), request);
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains(id));
            assertFalse(event.getFormattedMessage().contains("private-"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void mailLogsExcludeRecipientsAndExceptionMessages() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MailEngine.class);
        ch.qos.logback.classic.Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSenderImpl> provider = mock(ObjectProvider.class);
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new JavaMailSenderImpl().createMimeMessage());
        MailEngine mail = new MailEngine(provider);
        ReflectionTestUtils.setField(mail, "fromAddress", "sender@example.test");
        ReflectionTestUtils.setField(mail, "sendingEnabled", true);
        try {
            mail.sendMailWithoutRedirection(mail.createSimplePlainTextMail(null,
                    "private-recipient@example.test", "subject", "private-body"));
            String requestId = UUID.randomUUID().toString();
            MDC.put(ApiRequestObservability.MDC_KEY, requestId);
            assertThrows(CompletionException.class, () -> mail.sendMailAsync(() -> {
                assertEquals(requestId, MDC.get(ApiRequestObservability.MDC_KEY));
                throw new MessagingException("private-recipient@example.test secret-message");
            }).join());
            assertEquals(requestId, MDC.get(ApiRequestObservability.MDC_KEY));
            assertTrue(appender.list.get(1).getFormattedMessage().contains("request_id=" + requestId));
            MDC.remove(ApiRequestObservability.MDC_KEY);
            assertThrows(CompletionException.class, () -> mail.sendMailAsync(() -> {
                assertNull(MDC.get(ApiRequestObservability.MDC_KEY));
                throw new MessagingException("secret-message");
            }).join());
            assertTrue(appender.list.get(2).getFormattedMessage().contains("request_id=none"));
            assertEquals(3, appender.list.size());
            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains("private-recipient"));
                assertFalse(event.getFormattedMessage().contains("private-body"));
                assertFalse(event.getFormattedMessage().contains("secret-message"));
                assertNull(event.getThrowableProxy());
            }
        } finally {
            mail.shutdownAndWait();
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
        }
    }

    @Test
    void failedGeolocationDoesNotLogClientIpEvenAtDebug() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(IpGeoLocationEngine.class);
        ch.qos.logback.classic.Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        try {
            DatabaseReader reader = mock(DatabaseReader.class);
            when(reader.city(any())).thenThrow(new java.io.IOException("192.0.2.44 secret-marker"));
            IpGeoLocationEngine geo = new IpGeoLocationEngine();
            ReflectionTestUtils.setField(geo, "reader", reader);
            assertNull(geo.getGeoLocation("192.0.2.44"));
            assertEquals(1, appender.list.size());
            assertFalse(appender.list.get(0).getFormattedMessage().contains("192.0.2.44"));
            assertNull(appender.list.get(0).getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
        }
    }
}
