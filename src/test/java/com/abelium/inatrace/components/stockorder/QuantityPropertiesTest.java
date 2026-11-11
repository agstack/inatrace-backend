package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.types.ProcessingActionType;
import net.jqwik.api.ForAll;
import net.jqwik.api.Example;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class QuantityPropertiesTest {
    private final StockOrderService service = mock(StockOrderService.class, CALLS_REAL_METHODS);

    private static BigDecimal cents(int value) {
        return BigDecimal.valueOf(value, 2);
    }

    private static ApiStockOrder request(OrderType type, BigDecimal total, BigDecimal fulfilled, BigDecimal available) {
        ApiStockOrder request = new ApiStockOrder();
        request.setOrderType(type);
        request.setTotalQuantity(total);
        request.setFulfilledQuantity(fulfilled);
        request.setAvailableQuantity(available);
        return request;
    }

    @Property(tries = 100)
    void newPurchaseStartsWithEntireQuantityAvailable(@ForAll @IntRange(min = 0, max = 1000000) int amount) throws ApiException {
        BigDecimal quantity = cents(amount);
        StockOrder order = new StockOrder();
        order.setOrderType(OrderType.PURCHASE_ORDER);
        service.calculateQuantities(request(OrderType.PURCHASE_ORDER, quantity, quantity, quantity), order, null, null);
        assertEquals(0, quantity.compareTo(order.getTotalQuantity()));
        assertEquals(0, quantity.compareTo(order.getFulfilledQuantity()));
        assertEquals(0, quantity.compareTo(order.getAvailableQuantity()));
        assertEquals(amount > 0, order.getAvailable());
    }

    @Property(tries = 100)
    void equivalentDecimalScalesPreservePurchaseValues(@ForAll @IntRange(min = 0, max = 1000000) int amount)
            throws ApiException {
        BigDecimal quantity = cents(amount);
        BigDecimal extraScale = quantity.setScale(4);
        StockOrder order = new StockOrder();
        order.setOrderType(OrderType.PURCHASE_ORDER);
        service.calculateQuantities(request(OrderType.PURCHASE_ORDER, extraScale, quantity, quantity), order, null, null);
        assertEquals(0, quantity.compareTo(order.getTotalQuantity()));
        assertEquals(0, quantity.compareTo(order.getAvailableQuantity()));
    }

    @Property(tries = 100)
    void editingPurchaseRetainsPreviouslyUsedAmount(@ForAll @IntRange(min = 0, max = 10000) int used,
                                                    @ForAll @IntRange(min = 0, max = 10000) int remaining,
                                                    @ForAll @IntRange(min = 0, max = 10000) int newRemaining) throws ApiException {
        BigDecimal previous = cents(used + remaining);
        BigDecimal updated = cents(used + newRemaining);
        StockOrder order = new StockOrder();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.setOrderType(OrderType.PURCHASE_ORDER);
        order.setTotalQuantity(previous);
        order.setFulfilledQuantity(previous);
        order.setAvailableQuantity(cents(remaining));
        service.calculateQuantities(request(OrderType.PURCHASE_ORDER, updated, updated, updated), order, null, null);
        assertEquals(0, updated.compareTo(order.getFulfilledQuantity()));
        assertEquals(0, cents(newRemaining).compareTo(order.getAvailableQuantity()));
    }

    @Property(tries = 100)
    void editingBelowAlreadyUsedQuantityIsRejected(@ForAll @IntRange(min = 1, max = 10000) int used,
                                                   @ForAll @IntRange(min = 0, max = 10000) int remaining) {
        BigDecimal previous = cents(used + remaining);
        BigDecimal tooSmall = cents(used - 1);
        StockOrder order = new StockOrder();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.setOrderType(OrderType.PURCHASE_ORDER);
        order.setTotalQuantity(previous);
        order.setFulfilledQuantity(previous);
        order.setAvailableQuantity(cents(remaining));
        assertThrows(ApiException.class, () -> service.calculateQuantities(
                request(OrderType.PURCHASE_ORDER, tooSmall, tooSmall, tooSmall), order, null, null));
    }

    @Property(tries = 100)
    void newGeneralOrderRetainsValidQuantities(@ForAll @IntRange(min = 0, max = 10000) int available,
                                                @ForAll @IntRange(min = 0, max = 10000) int used,
                                                @ForAll @IntRange(min = 0, max = 10000) int outstanding) throws ApiException {
        BigDecimal fulfilled = cents(available + used);
        BigDecimal total = cents(available + used + outstanding);
        StockOrder order = new StockOrder();
        order.setOrderType(OrderType.GENERAL_ORDER);
        service.calculateQuantities(request(OrderType.GENERAL_ORDER, total, fulfilled, cents(available)), order, null, null);
        assertEquals(0, cents(available).compareTo(order.getAvailableQuantity()));
        assertEquals(0, fulfilled.compareTo(order.getFulfilledQuantity()));
        assertEquals(outstanding > 0, order.getIsOpenOrder());
    }

    @Property(tries = 100)
    void yieldFlagIncludesBoundsAndExcludesAdjacentCents(@ForAll @IntRange(min = 100, max = 100000) int inputCents)
            throws ApiException {
        BigDecimal input = cents(inputCents);
        // A 50% yield permits 40% to 60% of input when both units weigh one.
        BigDecimal low = input.multiply(new BigDecimal("0.4")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal high = input.multiply(new BigDecimal("0.6")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(false, yieldFlag(input, low));
        assertEquals(false, yieldFlag(input, high));
        assertEquals(true, yieldFlag(input, low.subtract(new BigDecimal("0.01"))));
        assertEquals(true, yieldFlag(input, high.add(new BigDecimal("0.01"))));
    }

    private boolean yieldFlag(BigDecimal input, BigDecimal output) throws ApiException {
        return yieldFlag(input, output, BigDecimal.ONE);
    }

    @Property(tries = 100)
    void yieldBoundariesRespectOutputUnitsWeighingTwoBaseUnits(@ForAll @IntRange(min = 1, max = 1000) int multiplier)
            throws ApiException {
        // 4n base units produce 2n two-base-unit packages; 50% yield is n packages.
        BigDecimal input = BigDecimal.valueOf(multiplier * 4L).setScale(2);
        BigDecimal low = BigDecimal.valueOf(multiplier * 80L, 2);
        BigDecimal high = BigDecimal.valueOf(multiplier * 120L, 2);
        assertFalse(yieldFlag(input, low, new BigDecimal("2")));
        assertFalse(yieldFlag(input, high, new BigDecimal("2")));
        assertTrue(yieldFlag(input, low.subtract(new BigDecimal("0.01")), new BigDecimal("2")));
        assertTrue(yieldFlag(input, high.add(new BigDecimal("0.01")), new BigDecimal("2")));
    }

    @Property(tries = 100)
    void yieldBoundsUseEquivalentMassForDifferentWeights(
            @ForAll @IntRange(min = 100, max = 100000) int inputCents,
            @ForAll @IntRange(min = 1, max = 60) int inputWeight,
            @ForAll @IntRange(min = 1, max = 60) int outputWeight) throws ApiException {
        // Compute the half-yield 80% and 120% bounds in integer cents using
        // rational arithmetic, independently of the production BigDecimal formula.
        long divisor = 10L * outputWeight;
        long inputMassCents = (long) inputCents * inputWeight;
        long lowCents = (inputMassCents * 4 + divisor / 2) / divisor;
        long highCents = (inputMassCents * 6 + divisor / 2) / divisor;
        BigDecimal input = cents(inputCents);
        BigDecimal inputUnit = BigDecimal.valueOf(inputWeight);
        BigDecimal outputUnit = BigDecimal.valueOf(outputWeight);
        BigDecimal low = BigDecimal.valueOf(lowCents, 2);
        BigDecimal high = BigDecimal.valueOf(highCents, 2);
        assertFalse(yieldFlag(input, low, inputUnit, outputUnit));
        assertFalse(yieldFlag(input, high, inputUnit, outputUnit));
        if (lowCents > 0) {
            assertTrue(yieldFlag(input, low.subtract(new BigDecimal("0.01")), inputUnit, outputUnit));
        }
        assertTrue(yieldFlag(input, high.add(new BigDecimal("0.01")), inputUnit, outputUnit));
    }

    @Property(tries = 100)
    void yieldBoundsRespectFractionalUnitWeights(
            @ForAll @IntRange(min = 100, max = 100000) int inputCents,
            @ForAll @IntRange(min = 0, max = 2) int inputIndex,
            @ForAll @IntRange(min = 0, max = 2) int outputIndex) throws ApiException {
        int[] weightHundredths = {50, 125, 250};
        int inputWeight = weightHundredths[inputIndex];
        int outputWeight = weightHundredths[outputIndex];
        long divisor = 10L * outputWeight;
        long weightedCents = (long) inputCents * inputWeight;
        long lowCents = (weightedCents * 4 + divisor / 2) / divisor;
        long highCents = (weightedCents * 6 + divisor / 2) / divisor;
        BigDecimal input = cents(inputCents);
        BigDecimal inputUnit = BigDecimal.valueOf(inputWeight, 2);
        BigDecimal outputUnit = BigDecimal.valueOf(outputWeight, 2);
        BigDecimal low = BigDecimal.valueOf(lowCents, 2);
        BigDecimal high = BigDecimal.valueOf(highCents, 2);
        assertFalse(yieldFlag(input, low, inputUnit, outputUnit));
        assertFalse(yieldFlag(input, high, inputUnit, outputUnit));
        if (lowCents > 0) {
            assertTrue(yieldFlag(input, low.subtract(new BigDecimal("0.01")), inputUnit, outputUnit));
        }
        assertTrue(yieldFlag(input, high.add(new BigDecimal("0.01")), inputUnit, outputUnit));
    }

    @Property(tries = 100)
    void yieldBoundsRespectGeneratedRatesAndTheInputMassCap(
            @ForAll @IntRange(min = 100, max = 100000) int inputCents,
            @ForAll @IntRange(min = 1, max = 60) int inputWeight,
            @ForAll @IntRange(min = 1, max = 60) int outputWeight,
            @ForAll @IntRange(min = 1, max = 10) int yieldTenths) throws ApiException {
        long inputMassCents = (long) inputCents * inputWeight;
        long divisor = 100L * outputWeight;
        long lowNumerator = inputMassCents * yieldTenths * 8L;
        long highNumerator = Math.min(inputMassCents * yieldTenths * 12L, inputMassCents * 100L);
        BigDecimal low = BigDecimal.valueOf((lowNumerator + divisor / 2) / divisor, 2);
        BigDecimal high = BigDecimal.valueOf((highNumerator + divisor / 2) / divisor, 2);
        BigDecimal input = cents(inputCents);
        BigDecimal inputUnit = BigDecimal.valueOf(inputWeight);
        BigDecimal outputUnit = BigDecimal.valueOf(outputWeight);
        BigDecimal yield = BigDecimal.valueOf(yieldTenths, 1);
        assertFalse(yieldFlag(input, low, inputUnit, outputUnit, yield));
        assertFalse(yieldFlag(input, high, inputUnit, outputUnit, yield));
        if (low.signum() > 0) {
            assertTrue(yieldFlag(input, low.subtract(new BigDecimal("0.01")), inputUnit, outputUnit, yield));
        }
        assertTrue(yieldFlag(input, high.add(new BigDecimal("0.01")), inputUnit, outputUnit, yield));
    }

    @Example
    void rejectsUnitsWithoutPositiveMassConversion() {
        for (BigDecimal invalid : new BigDecimal[]{null, BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
            ApiException inputError = assertThrows(ApiException.class,
                    () -> yieldFlag(BigDecimal.ONE, BigDecimal.ONE, invalid, BigDecimal.ONE));
            assertEquals(ApiStatus.VALIDATION_ERROR, inputError.getApiStatus());
            ApiException outputError = assertThrows(ApiException.class,
                    () -> yieldFlag(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, invalid));
            assertEquals(ApiStatus.VALIDATION_ERROR, outputError.getApiStatus());
        }
    }

    @Example
    void rejectsMissingMeasurementUnits() {
        MeasureUnitType valid = new MeasureUnitType("KG", "kg", BigDecimal.ONE);
        ApiException missingInput = assertThrows(ApiException.class, () -> yieldFlagWithUnits(
                BigDecimal.ONE, BigDecimal.ONE, null, valid, new BigDecimal("0.5")));
        ApiException missingOutput = assertThrows(ApiException.class, () -> yieldFlagWithUnits(
                BigDecimal.ONE, BigDecimal.ONE, valid, null, new BigDecimal("0.5")));
        assertEquals(ApiStatus.VALIDATION_ERROR, missingInput.getApiStatus());
        assertEquals(ApiStatus.VALIDATION_ERROR, missingOutput.getApiStatus());
    }

    @Example
    void repeatingIntermediateDivisionDoesNotMoveHalfCentBoundary() throws ApiException {
        // 1.01 * 50 * 0.5 * 1.2 / 12 = 2.525, which rounds to 2.53.
        assertFalse(yieldFlag(new BigDecimal("1.01"), new BigDecimal("2.53"),
                new BigDecimal("50"), new BigDecimal("12")));
        assertTrue(yieldFlag(new BigDecimal("1.01"), new BigDecimal("2.54"),
                new BigDecimal("50"), new BigDecimal("12")));
    }

    private boolean yieldFlag(BigDecimal input, BigDecimal output, BigDecimal outputWeight) throws ApiException {
        return yieldFlag(input, output, BigDecimal.ONE, outputWeight);
    }

    boolean yieldFlag(BigDecimal input, BigDecimal output, BigDecimal inputWeight, BigDecimal outputWeight)
            throws ApiException {
        return yieldFlag(input, output, inputWeight, outputWeight, new BigDecimal("0.5"));
    }

    private boolean yieldFlag(BigDecimal input, BigDecimal output, BigDecimal inputWeight, BigDecimal outputWeight,
                              BigDecimal yield) throws ApiException {
        MeasureUnitType inputUnit = new MeasureUnitType("INPUT", "input", inputWeight);
        MeasureUnitType outputUnit = new MeasureUnitType("OUTPUT", "output", outputWeight);
        return yieldFlagWithUnits(input, output, inputUnit, outputUnit, yield);
    }

    private boolean yieldFlagWithUnits(BigDecimal input, BigDecimal output, MeasureUnitType inputUnit,
                                       MeasureUnitType outputUnit, BigDecimal yield) throws ApiException {
        SemiProduct inputProduct = new SemiProduct();
        inputProduct.setMeasurementUnitType(inputUnit);
        SemiProduct outputProduct = new SemiProduct();
        outputProduct.setMeasurementUnitType(outputUnit);
        ProcessingAction action = new ProcessingAction();
        action.setType(ProcessingActionType.PROCESSING);
        action.setInputSemiProduct(inputProduct);
        action.setRepackedOutputFinalProducts(false);
        action.setEstimatedOutputQuantityPerUnit(yield);
        Transaction inputTransaction = new Transaction();
        inputTransaction.setStatus(TransactionStatus.PENDING);
        inputTransaction.setOutputQuantity(input);
        ProcessingOrder processing = new ProcessingOrder();
        processing.setProcessingAction(action);
        processing.setInputTransactions(Set.of(inputTransaction));
        StockOrder outputOrder = new StockOrder();
        outputOrder.setOrderType(OrderType.PROCESSING_ORDER);
        outputOrder.setSemiProduct(outputProduct);
        service.calculateQuantities(request(OrderType.PROCESSING_ORDER, output, output, output), outputOrder, processing, null);
        return outputOrder.getOutQuantityNotInRange();
    }
}
