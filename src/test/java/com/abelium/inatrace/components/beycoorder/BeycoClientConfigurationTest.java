package com.abelium.inatrace.components.beycoorder;

import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BeycoClientConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
            .withBean(StockOrderService.class, () -> mock(StockOrderService.class))
            .withBean(CompanyQueries.class, () -> mock(CompanyQueries.class))
            .withBean(BeycoOrderService.class)
            .withPropertyValues("beyco.oauth2.clientId=test-id", "beyco.oauth2.clientSecret=test-secret",
                    "beyco.oauth2.url=http://localhost/oauth", "beyco.integration.url=http://localhost");

    @Test
    void springUsesTheDocumentedDefaultDeadlines() {
        context.run(result -> {
            BeycoOrderService service = result.getBean(BeycoOrderService.class);
            assertEquals(30000, ReflectionTestUtils.getField(service, "connectTimeoutMs"));
            assertEquals(30000, ReflectionTestUtils.getField(service, "readTimeoutMs"));
        });
    }

    @Test
    void springInjectsConfiguredDeadlines() {
        context.withPropertyValues("beyco.integration.connectTimeoutMs=1250", "beyco.integration.readTimeoutMs=2500")
                .run(result -> {
                    BeycoOrderService service = result.getBean(BeycoOrderService.class);
                    assertEquals(1250, ReflectionTestUtils.getField(service, "connectTimeoutMs"));
                    assertEquals(2500, ReflectionTestUtils.getField(service, "readTimeoutMs"));
                });
    }

    @Test
    void rejectsUnboundedOrNegativeDeadlines() {
        context.withPropertyValues("beyco.integration.readTimeoutMs=0").run(result ->
                assertThrows(Exception.class, () -> result.getBean(BeycoOrderService.class)));
        context.withPropertyValues("beyco.integration.connectTimeoutMs=-1").run(result ->
                assertThrows(Exception.class, () -> result.getBean(BeycoOrderService.class)));
    }
}
