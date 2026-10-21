package com.abelium.inatrace.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ApiRequestObservabilityHttpTest.EndpointsConfiguration.class)
class ApiRequestObservabilityHttpTest extends AbstractMySqlIntegrationTest {
    @Autowired private TestRestTemplate rest;

    @Test
    void securitySendErrorReturnsCorrelationIdAndOneFinal401Event() throws Exception {
        assertRequest("/api/user/profile", 401, "UNMATCHED");
    }

    @Test
    void publicNotFoundIsCorrelatedWithoutRawPathOrQuery() throws Exception {
        assertRequest("/api/public/private-path-marker?private-query-marker=secret", 404, "UNMATCHED");
    }

    @Test
    void unexpectedServerErrorHasOneCorrelatedCompletion() throws Exception {
        assertRequest("/api/public/_observability/server-error/private-path-marker", 500,
                "/api/public/_observability/server-error/{id}");
    }

    @Test
    void asyncSuccessHasOneCorrelatedCompletion() throws Exception {
        assertRequest("/api/public/_observability/async-success/private-path-marker", 200,
                "/api/public/_observability/async-success/{id}");
    }

    @Test
    void asyncErrorHasOneCorrelatedCompletion() throws Exception {
        assertRequest("/api/public/_observability/async-error/private-path-marker", 500,
                "/api/public/_observability/async-error/{id}");
    }

    @Test
    void asyncTimeoutHasOneCorrelatedCompletion() throws Exception {
        assertRequest("/api/public/_observability/async-timeout/private-path-marker", 503,
                "/api/public/_observability/async-timeout/{id}");
    }

    private void assertRequest(String path, int status, String expectedRoute) throws Exception {
        String id = UUID.randomUUID().toString();
        Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestObservability.class);
        Logger errorLogger = (Logger) LoggerFactory.getLogger(DefaultControllerExceptionHandler.class);
        CompletionAppender appender = new CompletionAppender(id);
        ListAppender<ILoggingEvent> errors = new ListAppender<>();
        appender.start();
        errors.start();
        logger.addAppender(appender);
        errorLogger.addAppender(errors);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(ApiRequestObservability.HEADER, id);
            ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertEquals(status, response.getStatusCode().value());
            assertEquals(id, response.getHeaders().getFirst(ApiRequestObservability.HEADER));
            assertTrue(appender.finished.await(5, TimeUnit.SECONDS), "missing completion event");
            assertEquals(1, appender.messages.size());
            String log = appender.messages.get(0);
            assertTrue(log.contains("status=" + status), log);
            assertTrue(log.contains("request_id=" + id), log);
            if (expectedRoute != null) assertTrue(log.contains("route=" + expectedRoute), log);
            assertFalse(log.contains("private-path-marker"), log);
            assertFalse(log.contains("private-query-marker"), log);
            if (status == 500) {
                assertEquals(1, errors.list.size());
                ILoggingEvent diagnostic = errors.list.get(0);
                assertTrue(diagnostic.getFormattedMessage().contains("request_id=" + id));
                assertTrue(diagnostic.getFormattedMessage().contains("IllegalStateException"));
                assertFalse(diagnostic.getFormattedMessage().contains("private-error-marker"));
                assertNull(diagnostic.getThrowableProxy());
                assertFalse(response.getBody().contains("private-error-marker"));
            }
        } finally {
            logger.detachAppender(appender);
            errorLogger.detachAppender(errors);
            appender.stop();
            errors.stop();
        }
    }

    private static final class CompletionAppender extends AppenderBase<ILoggingEvent> {
        private final String id;
        final CountDownLatch finished = new CountDownLatch(1);
        final List<String> messages = new CopyOnWriteArrayList<>();

        CompletionAppender(String id) { this.id = id; }

        @Override
        protected void append(ILoggingEvent event) {
            String message = event.getFormattedMessage();
            if (message.contains("request_id=" + id)) {
                messages.add(message);
                finished.countDown();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EndpointsConfiguration {
        @Bean TestEndpoints observabilityTestEndpoints() { return new TestEndpoints(); }
    }

    /** Test-only routes: no async endpoint is registered in production. */
    @Hidden
    @RestController
    @Profile("observability-test-component-scan-disabled")
    @RequestMapping("/public/_observability")
    static class TestEndpoints {
        @GetMapping("/server-error/{id}")
        String serverError() {
            throw new IllegalStateException("private-error-marker");
        }

        @GetMapping("/async-success/{id}")
        DeferredResult<String> asyncSuccess() {
            DeferredResult<String> result = new DeferredResult<>(5000L);
            CompletableFuture.runAsync(() -> result.setResult("ok"));
            return result;
        }

        @GetMapping("/async-error/{id}")
        DeferredResult<String> asyncError() {
            DeferredResult<String> result = new DeferredResult<>(5000L);
            CompletableFuture.runAsync(() -> result.setErrorResult(new IllegalStateException("private-error-marker")));
            return result;
        }

        @GetMapping("/async-timeout/{id}")
        DeferredResult<ResponseEntity<String>> asyncTimeout() {
            DeferredResult<ResponseEntity<String>> result = new DeferredResult<>(100L);
            result.onTimeout(() -> result.setResult(ResponseEntity.status(503).body("timed out")));
            return result;
        }
    }

}
