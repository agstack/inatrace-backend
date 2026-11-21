package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.db.entities.payment.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The payment response must expose the persisted {@code totalPaid}. The field was stored and
 * returned by the entity, but the mapper copied only {@code amount}.
 */
class PaymentMapperTest {

    @Test
    @DisplayName("the mapper keeps amount and totalPaid as independent values")
    void mapsAmountAndTotalPaidIndependently() {
        Payment payment = payment(new BigDecimal("731.25"), new BigDecimal("700.00"));

        ApiPayment mapped = PaymentMapper.toApiPayment(payment, null);

        assertEquals(new BigDecimal("731.25"), mapped.getAmount());
        assertEquals(new BigDecimal("700.00"), mapped.getTotalPaid());
    }

    @Test
    @DisplayName("a payment without totalPaid keeps the field null")
    void mapsNullTotalPaidAsNull() {
        Payment payment = payment(new BigDecimal("731.25"), null);

        ApiPayment mapped = PaymentMapper.toApiPayment(payment, null);

        assertEquals(new BigDecimal("731.25"), mapped.getAmount());
        assertNull(mapped.getTotalPaid());
    }

    private Payment payment(BigDecimal amount, BigDecimal totalPaid) {
        Payment payment = new Payment();
        payment.setPurchased(BigDecimal.ONE);
        payment.setAmount(amount);
        payment.setTotalPaid(totalPaid);
        return payment;
    }
}
