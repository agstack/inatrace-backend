package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.components.processingorder.api.ApiProcessingOrder;
import com.abelium.inatrace.components.processingorder.mappers.ProcessingOrderMapper;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderMapper;
import com.abelium.inatrace.db.entities.common.ActivityProof;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrderActivityProof;
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
 * {@code IllegalStateException: A public storage key requires an explicit public scope}. The target
 * stock orders of a processing order had the same problem: their receipts and activity proofs were
 * mapped with a {@code null} user, so the mapper now takes the requesting user (fourth test).
 *
 * <p>Only the public QR traceability maps without a user; there the receipt is left out. The
 * logged-in history passes the user, so it keeps the receipt.
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

	@Test
	@DisplayName("The target stock orders of a processing order map their receipts and proofs for the user")
	void processingOrderTargetsMapForUser() {
		StockOrder target = new StockOrder();
		Payment payment = paidWithReceipt();
		payment.setStockOrder(target);
		target.getPayments().add(payment);
		Document proofDocument = new Document();
		proofDocument.setStorageKey("proofs/proof-1.pdf");
		proofDocument.setName("proof-1.pdf");
		ActivityProof proof = new ActivityProof();
		proof.setType("CERTIFICATE");
		proof.setDocument(proofDocument);
		StockOrderActivityProof link = new StockOrderActivityProof();
		link.setStockOrder(target);
		link.setActivityProof(proof);
		target.getActivityProofs().add(link);
		Company mill = new Company();
		mill.setName("Mill");
		ProcessingAction action = new ProcessingAction();
		action.setCompany(mill);
		ProcessingOrder processingOrder = new ProcessingOrder();
		processingOrder.setProcessingAction(action);
		processingOrder.getTargetStockOrders().add(target);

		ApiProcessingOrder mapped = assertDoesNotThrow(() -> ProcessingOrderMapper.toApiProcessingOrder(processingOrder, 7L, Language.EN));
		ApiStockOrder mappedTarget = mapped.getTargetStockOrders().get(0);
		assertNotNull(mappedTarget.getPayments().get(0).getReceiptDocument());
		assertNotNull(mappedTarget.getActivityProofs().get(0).getDocument());
	}

	@Test
	@DisplayName("The logged-in history keeps the receipt; the public one leaves it out")
	void historyKeepsReceiptOnlyForUser() {
		StockOrder order = new StockOrder();
		Payment payment = paidWithReceipt();
		payment.setStockOrder(order);
		order.getPayments().add(payment);

		ApiStockOrder forUser = StockOrderMapper.toApiStockOrderHistory(order, 7L, Language.EN);
		assertNotNull(forUser.getPayments().get(0).getReceiptDocument());

		ApiStockOrder forPublic = assertDoesNotThrow(() -> StockOrderMapper.toApiStockOrderHistory(order, null, Language.EN));
		assertEquals(1, forPublic.getPayments().size());
		assertNull(forPublic.getPayments().get(0).getReceiptDocument());
	}
}
