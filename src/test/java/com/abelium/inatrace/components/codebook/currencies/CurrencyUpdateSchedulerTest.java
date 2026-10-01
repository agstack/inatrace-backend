package com.abelium.inatrace.components.codebook.currencies;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyUpdateSchedulerTest {

    @Test
    void delegatesAnAutomaticRefreshToTheCurrencyService() {
        RecordingCurrencyTypeService currencyTypeService = new RecordingCurrencyTypeService();
        CurrencyUpdateScheduler scheduler = new CurrencyUpdateScheduler(currencyTypeService);

        scheduler.updateCurrencies();

        assertTrue(currencyTypeService.wasUpdated);
    }

    private static class RecordingCurrencyTypeService extends CurrencyTypeService {
        private boolean wasUpdated;

        @Override
        public void updateCurrencies() {
            wasUpdated = true;
        }
    }
}
