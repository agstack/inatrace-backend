package com.abelium.inatrace.components.company;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoCoordinateValidatorTest {

    @Test
    @DisplayName("accepts the inclusive latitude and longitude boundaries")
    void acceptsBoundaries() {
        assertDoesNotThrow(() -> GeoCoordinateValidator.validate(-90, -180));
        assertDoesNotThrow(() -> GeoCoordinateValidator.validate(90, 180));
        assertDoesNotThrow(() -> GeoCoordinateValidator.validate(0, 0));
    }

    @Test
    @DisplayName("rejects out-of-range latitudes")
    void rejectsLatitudeOutOfRange() {
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validate(-90.0001, 0)),
                "Latitude out of range");
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validate(90.0001, 0)),
                "Latitude out of range");
    }

    @Test
    @DisplayName("rejects out-of-range longitudes")
    void rejectsLongitudeOutOfRange() {
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validate(0, -180.0001)),
                "Longitude out of range");
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validate(0, 180.0001)),
                "Longitude out of range");
    }

    @Test
    @DisplayName("rejects NaN and infinite coordinates")
    void rejectsNonFiniteCoordinates() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertMessageContains(
                    assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validate(value, 0)),
                    "finite");
            assertMessageContains(
                    assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validate(0, value)),
                    "finite");
        }
    }

    @Test
    @DisplayName("validates every point in a list")
    void validatesPointLists() {
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{-1, 30});
        points.add(new double[]{-2, 31});
        assertEquals(points, GeoCoordinateValidator.validateAll(points));

        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validateAll(
                        List.of(new double[]{-1, 30}, new double[]{100, 31}))),
                "Latitude out of range");
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validateAll(null)),
                "No coordinates found");
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validateAll(
                        List.of(new double[]{-1}))),
                "latitude and longitude");
        assertMessageContains(
                assertThrows(IllegalArgumentException.class, () -> GeoCoordinateValidator.validateAll(
                        java.util.Collections.singletonList(null))),
                "latitude and longitude");
    }

    private void assertMessageContains(IllegalArgumentException exception, String expected) {
        assertTrue(exception.getMessage().contains(expected), exception.getMessage());
    }
}
