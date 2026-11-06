package com.abelium.inatrace.components.currencies;

import com.abelium.inatrace.components.codebook.currencies.CurrencyTypeService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/** Checks production property resolution without starting MySQL or a provider. */
class CurrencyProviderWiringTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
            .withUserConfiguration(Services.class)
            .withPropertyValues("INAtrace.exchangerate.apiKey=synthetic-key");

    @Test
    void defaultPropertiesAreInjectedIntoBothServices() {
        runner.run(context -> {
            CurrencyService currency = context.getBean(CurrencyService.class);
            CurrencyTypeService types = context.getBean(CurrencyTypeService.class);
            assertSame(types, ReflectionTestUtils.getField(currency, "currencyTypeService"));
            assertEquals("http://api.exchangeratesapi.io/v1", ReflectionTestUtils.getField(currency, "baseURL"));
            assertEquals("http://api.exchangeratesapi.io/v1", ReflectionTestUtils.getField(types, "baseURL"));
            assertEquals(30000L, ReflectionTestUtils.getField(currency, "requestTimeoutMs"));
            assertEquals(30000L, ReflectionTestUtils.getField(types, "requestTimeoutMs"));
        });
    }

    @Test
    void configuredPropertiesAreInjectedIntoBothServices() {
        runner.withPropertyValues("INAtrace.exchangerate.baseURL=http://127.0.0.1:9876/v1",
                        "INAtrace.exchangerate.requestTimeoutMs=725")
                .run(context -> {
                    CurrencyService currency = context.getBean(CurrencyService.class);
                    CurrencyTypeService types = context.getBean(CurrencyTypeService.class);
                    assertEquals("http://127.0.0.1:9876/v1", ReflectionTestUtils.getField(currency, "baseURL"));
                    assertEquals("http://127.0.0.1:9876/v1", ReflectionTestUtils.getField(types, "baseURL"));
                    assertEquals(725L, ReflectionTestUtils.getField(currency, "requestTimeoutMs"));
                    assertEquals(725L, ReflectionTestUtils.getField(types, "requestTimeoutMs"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({CurrencyService.class, CurrencyTypeService.class})
    static class Services {}
}
