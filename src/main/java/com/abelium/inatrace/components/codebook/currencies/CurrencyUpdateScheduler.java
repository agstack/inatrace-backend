package com.abelium.inatrace.components.codebook.currencies;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Starts the external currency refresh only when it is enabled for the environment. */
@Component
@ConditionalOnProperty(
        name = "INATrace.currency.autoUpdate.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CurrencyUpdateScheduler {

    private final CurrencyTypeService currencyTypeService;

    public CurrencyUpdateScheduler(CurrencyTypeService currencyTypeService) {
        this.currencyTypeService = currencyTypeService;
    }

    @Scheduled(cron = "0 1 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    public void updateCurrencies() {
        currencyTypeService.updateCurrencies();
    }
}
