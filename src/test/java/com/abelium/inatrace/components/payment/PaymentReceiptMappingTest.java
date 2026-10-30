package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderMapper;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.types.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Payment receipts are mapped only for an authenticated user.
 *
 * <p>Since public storage keys require an explicit public scope, {@code StorageKeyCache.put(key, null)}
 * throws. {@code StockOrderMapper.toApiStockOrder} mapped the payments of a stock order with a
 * {@code null} user, so reading any stock order with a paid receipt ({@code GET /api/chain/stock-order/{id}})
 * answered 500. Against the unfixed code the first and third tests below fail with
 * {@code IllegalStateException: A public storage key requires an explicit public scope}.
 */
class PaymentReceiptMappingTest {

	private static Payment paidWithReceipt() {
		Document receipt = new Document();
		receipt.setStorageKey("receipts/receipt-1.pdf");
		receipt.setName("receipt-1.pdf");
		receipt.setContentType("application/pdf");
		Payment payment = new Payment();
		payment.setPurchased(BigDecimal.valueOf(500));
		payment.setReceiptDocument(receipt);
		return payment;
	}

	@Test
	@DisplayName("Without a user the receipt is left out instead of failing")
	void receiptIsOmittedWithoutUser() {
		ApiPayment mapped = assertDoesNotThrow(() -> PaymentMapper.toApiPayment(paidWithReceipt(), null));
		assertNull(mapped.getReceiptDocument());
	}

	@Test
	@DisplayName("With a user the receipt gets a private temporary key")
	void receiptIsMappedForUser() {
		ApiPayment mapped = PaymentMapper.toApiPayment(paidWithReceipt(), 7L);
		assertNotNull(mapped.getReceiptDocument());
		assertNotNull(mapped.getReceiptDocument().getStorageKey());
		assertNotEquals("receipts/receipt-1.pdf", mapped.getReceiptDocument().getStorageKey());
	}

	@Test
	@DisplayName("A stock order with a paid receipt maps for the requesting user")
	void stockOrderWithReceiptMaps() {
		StockOrder order = new StockOrder();
		Payment payment = paidWithReceipt();
		payment.setStockOrder(order);
		order.getPayments().add(payment);

		ApiStockOrder mapped = assertDoesNotThrow(() -> StockOrderMapper.toApiStockOrder(order, 7L, Language.EN));
		assertEquals(1, mapped.getPayments().size());
		assertNotNull(mapped.getPayments().get(0).getReceiptDocument());
	}
}
