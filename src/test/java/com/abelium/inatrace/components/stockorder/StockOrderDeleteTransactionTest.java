package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.security.service.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.transaction.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a stock order runs in a transaction.
 *
 * <p>{@code deleteStockOrder(Long, CustomUserDetails)} lost its {@code @Transactional} when
 * {@code checkPurchaseOrderUserCustomerCompany} was inserted between the annotation and the method:
 * the annotation ended up on the new private helper, where the Spring proxy ignores it.
 * {@code DELETE /api/chain/stock-order/{id}} then failed with {@code TransactionRequiredException}
 * at {@code em.remove}. {@code StockOrderApiAuthTest} is {@code @Transactional} at class level, so it
 * runs the endpoint inside the test's transaction and did not notice.
 */
class StockOrderDeleteTransactionTest {

	@Test
	@DisplayName("deleteStockOrder(id, user) is transactional")
	void deleteIsTransactional() throws NoSuchMethodException {
		Method delete = StockOrderService.class.getMethod("deleteStockOrder", Long.class, CustomUserDetails.class);
		assertTrue(delete.isAnnotationPresent(Transactional.class));
	}

	@Test
	@DisplayName("No private method of StockOrderService carries @Transactional (the proxy would ignore it)")
	void noPrivateTransactionalMethods() {
		List<String> ignored = Arrays.stream(StockOrderService.class.getDeclaredMethods())
				.filter(m -> Modifier.isPrivate(m.getModifiers()) && m.isAnnotationPresent(Transactional.class))
				.map(Method::getName)
				.toList();
		assertEquals(List.of(), ignored);
	}
}
