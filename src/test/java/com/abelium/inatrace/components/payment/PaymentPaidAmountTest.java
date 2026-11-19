package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The paid amount kept in the stock order must follow the balance. It used to be recalculated only
 * when the stock order itself was saved, so recording or deleting a payment left it at zero.
 */
class PaymentPaidAmountTest {

	@Test
	@DisplayName("recalculates paid from cost minus balance")
	void recalculatesPaidFromCostAndBalance() {

		StockOrder stockOrder = new StockOrder();
		stockOrder.setCost(new BigDecimal("1000.00"));
		stockOrder.setBalance(new BigDecimal("250.00"));

		ReflectionTestUtils.invokeMethod(new PaymentService(null, null, null, null), "syncPaidAmount", stockOrder);

		assertEquals(new BigDecimal("750.00"), stockOrder.getPaid());
	}

	@Test
	@DisplayName("keeps paid untouched when the order has no cost or balance")
	void keepsPaidWhenAmountsAreMissing() {

		StockOrder stockOrder = new StockOrder();
		stockOrder.setPaid(new BigDecimal("10.00"));

		ReflectionTestUtils.invokeMethod(new PaymentService(null, null, null, null), "syncPaidAmount", stockOrder);

		assertEquals(new BigDecimal("10.00"), stockOrder.getPaid());
		assertNull(stockOrder.getBalance());
	}
}
