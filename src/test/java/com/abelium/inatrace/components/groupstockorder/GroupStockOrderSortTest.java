package com.abelium.inatrace.components.groupstockorder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sort key of the grouped stock order list is mapped to a fixed set of aliases before it is
 * added to the query.
 */
class GroupStockOrderSortTest {

    @ParameterizedTest
    @ValueSource(strings = {"date", "id", "noOfSacs", "deliveryTime", "updateTimestamp"})
    void knownSortKeysAreKept(String sortBy) {
        assertThat(GroupStockOrderService.orderByColumn(sortBy)).isEqualTo(sortBy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "identifier", "SO.id", "date DESC", "date, id", "(date)"})
    void anythingElseFallsBackToTheDefault(String sortBy) {
        assertThat(GroupStockOrderService.orderByColumn(sortBy)).isEqualTo("id");
    }

    @Test
    void nullFallsBackToTheDefault() {
        assertThat(GroupStockOrderService.orderByColumn(null)).isEqualTo("id");
    }
}
