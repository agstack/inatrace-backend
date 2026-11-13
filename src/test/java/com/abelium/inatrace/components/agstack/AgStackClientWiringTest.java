package com.abelium.inatrace.components.agstack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgStackClientWiringTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(Clients.class)
            .withPropertyValues("INATrace.agstack.email=synthetic@example.test",
                    "INATrace.agstack.password=synthetic-password",
                    "INATrace.agstack.loginBaseURL=http://127.0.0.1:1",
                    "INATrace.agstack.baseURL=http://127.0.0.1:2");

    @Test
    void defaultSpringWiringUsesSystemClockAndThirtySecondTimeout() {
        runner.run(context -> {
            AgStackClientTokenManager tokens = context.getBean(AgStackClientTokenManager.class);
            AgStackClientService client = context.getBean(AgStackClientService.class);
            assertSame(tokens, ReflectionTestUtils.getField(client, "tokenService"));
            assertEquals(Clock.systemUTC(), ReflectionTestUtils.getField(tokens, "clock"));
            assertEquals(30000L, ReflectionTestUtils.getField(tokens, "requestTimeoutMs"));
            assertEquals(30000L, ReflectionTestUtils.getField(client, "requestTimeoutMs"));
        });
    }

    @Test
    void configuredTimeoutIsInjectedIntoBothClients() {
        runner.withPropertyValues("INATrace.agstack.requestTimeoutMs=725")
                .run(context -> {
                    assertEquals(725L, ReflectionTestUtils.getField(
                            context.getBean(AgStackClientTokenManager.class), "requestTimeoutMs"));
                    assertEquals(725L, ReflectionTestUtils.getField(
                            context.getBean(AgStackClientService.class), "requestTimeoutMs"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AgStackClientTokenManager.class, AgStackClientService.class})
    static class Clients {}
}
