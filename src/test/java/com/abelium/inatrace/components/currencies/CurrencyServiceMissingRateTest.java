package com.abelium.inatrace.components.currencies;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * When the exchange rate provider is unavailable the conversion has no rate to work with. It must
 * return null so that public payloads degrade instead of throwing NoResultException (which used to
 * turn the public QR endpoint into a 500).
 */
class CurrencyServiceMissingRateTest {

	/** Simulates a date for which no rate could be fetched for any currency. */
	private static final class MissingRateCurrencyService extends CurrencyService {

		@Override
		public BigDecimal convertToEurAtDateOrNull(String from, BigDecimal value, Date date) {
			return null;
		}

		@Override
		public BigDecimal convertFromEurAtDateOrNull(String to, BigDecimal value, Date date) {
			return null;
		}
	}

	private final CurrencyService service = new MissingRateCurrencyService();
	private final Date date = new Date();

	@Test
	@DisplayName("returns null when the source currency rate is missing")
	void returnsNullWhenSourceRateIsMissing() {
		assertNull(service.convertAtDateOrNull("BRL", "USD", new BigDecimal("1000.00"), date));
	}

	@Test
	@DisplayName("returns null when the target currency rate is missing")
	void returnsNullWhenTargetRateIsMissing() {
		assertNull(service.convertAtDateOrNull("EUR", "BRL", new BigDecimal("1000.00"), date));
	}

	@Test
	@DisplayName("keeps the identity conversion working without any rate")
	void keepsIdentityConversion() {
		BigDecimal value = new BigDecimal("1000.00");
		assertEquals(value, service.convertAtDateOrNull("BRL", "BRL", value, date));
	}
}
