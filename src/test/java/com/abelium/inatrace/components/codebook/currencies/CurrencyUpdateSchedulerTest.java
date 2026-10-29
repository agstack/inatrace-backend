package com.abelium.inatrace.components.codebook.currencies;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CurrencyUpdateSchedulerTest {

    @Test
    void delegatesAnAutomaticRefreshToTheCurrencyService() {
        RecordingCurrencyTypeService currencyTypeService = new RecordingCurrencyTypeService();
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypeService);

        scheduler.updateCurrencies();

        assertTrue(currencyTypeService.wasUpdated);
    }

    @Test
    void enabledSchedulerReceivesReadyEvent() {
        CurrencyTypeService service = mock(CurrencyTypeService.class);
        new ApplicationContextRunner().withInitializer(context -> context.getBeanFactory().registerSingleton("currencyTypeService", service))
                .withUserConfiguration(CurrencyUpdateScheduler.class)
                .withPropertyValues("INATrace.currency.autoUpdate.enabled=true")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(CurrencyUpdateScheduler.class).size());
                    context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                            context.getSourceApplicationContext(), Duration.ZERO));
                    verify(service).updateCurrencies();
                });
    }

    @Test
    void disabledSchedulerDoesNotRegister() {
        new ApplicationContextRunner().withInitializer(context -> context.getBeanFactory().registerSingleton("currencyTypeService", mock(CurrencyTypeService.class)))
                .withUserConfiguration(CurrencyUpdateScheduler.class)
                .withPropertyValues("INATrace.currency.autoUpdate.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(CurrencyUpdateScheduler.class).isEmpty()));
    }

    @Test
    void schedulerIsEnabledWhenPropertyIsAbsent() {
        CurrencyTypeService service = mock(CurrencyTypeService.class);
        new ApplicationContextRunner().withInitializer(context -> context.getBeanFactory().registerSingleton("currencyTypeService", service))
                .withUserConfiguration(CurrencyUpdateScheduler.class)
                .run(context -> assertEquals(1, context.getBeansOfType(CurrencyUpdateScheduler.class).size()));
    }

    private static class RecordingCurrencyTypeService extends CurrencyTypeService {
        private boolean wasUpdated;

        @Override
        public void updateCurrencies() {
            wasUpdated = true;
        }
    }
}
