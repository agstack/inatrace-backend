package com.abelium.inatrace.components.stockorder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Expected physical conversion, assuming estimated yield is a mass fraction. */
class YieldConversionRegressionTest {
    private final QuantityPropertiesTest fixture = new QuantityPropertiesTest();

    @Test
    void twoSixtyKilogramBagsAtHalfYieldProduceSixtyKilograms() throws Exception {
        assertFalse(fixture.yieldFlag(new BigDecimal("2.00"), new BigDecimal("60.00"),
                new BigDecimal("60"), BigDecimal.ONE));
    }

    @Test
    void oneHundredTwentyKilogramsAtHalfYieldProduceOneSixtyKilogramBag() throws Exception {
        assertFalse(fixture.yieldFlag(new BigDecimal("120.00"), BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("60")));
    }

    @Test
    void twoSixtyKilogramBagsAtHalfYieldProduceOneBagOfTheSameUnit() throws Exception {
        assertFalse(fixture.yieldFlag(new BigDecimal("2.00"), BigDecimal.ONE,
                new BigDecimal("60"), new BigDecimal("60")));
    }
}
