package com.abelium.inatrace.db.migrations;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryCoordinatesSeedTest {

    private static final String COUNTRIES_RESOURCE = "db/seed/countries.csv";

    @Test
    void containsRepresentativeCoordinatesForSupportedCountries() throws Exception {
        Map<String, double[]> coordinates = readCoordinates();

        assertCoordinate(coordinates, "NG", 9.055, 8.635);
        assertCoordinate(coordinates, "BR", -14.265, -54.36);
        assertCoordinate(coordinates, "RW", -2.025, 29.92);
        assertTrue(coordinates.size() >= 150,
                "Expected at least 150 countries with coordinates, got " + coordinates.size());
    }

    @Test
    void leavesCountriesWithoutAUsableBboxWithoutCoordinates() throws Exception {
        Map<String, double[]> coordinates = readCoordinates();

        // These bboxes are missing or cross the antimeridian and cannot produce a meaningful centre.
        assertFalse(coordinates.containsKey("RU"));
        assertFalse(coordinates.containsKey("FJ"));
        assertFalse(coordinates.containsKey("AQ"));
    }

    @Test
    void onlyStoresValidPairedCoordinates() throws Exception {
        Map<String, double[]> coordinates = readCoordinates();

        assertFalse(coordinates.isEmpty());
        coordinates.forEach((code, coordinate) -> {
            assertTrue(coordinate[0] >= -90 && coordinate[0] <= 90, code + " latitude");
            assertTrue(coordinate[1] >= -180 && coordinate[1] <= 180, code + " longitude");
        });
    }

    private void assertCoordinate(Map<String, double[]> coordinates, String code, double latitude, double longitude) {
        double[] coordinate = coordinates.get(code);
        assertNotNull(coordinate, "Missing coordinates for " + code);
        assertEquals(latitude, coordinate[0], 0.01, code + " latitude");
        assertEquals(longitude, coordinate[1], 0.01, code + " longitude");
    }

    private Map<String, double[]> readCoordinates() throws Exception {
        ClassPathResource resource = new ClassPathResource(COUNTRIES_RESOURCE);
        assertTrue(resource.exists(), "Missing " + COUNTRIES_RESOURCE);

        Map<String, double[]> coordinates = new HashMap<>();
        try (InputStream input = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .withDelimiter(',')
                     .withIgnoreSurroundingSpaces(true)
                     .withFirstRecordAsHeader()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                String latitude = record.get("Latitude").trim();
                String longitude = record.get("Longitude").trim();
                if (latitude.isEmpty() && longitude.isEmpty()) {
                    continue;
                }
                assertFalse(latitude.isEmpty() || longitude.isEmpty(),
                        "Unpaired coordinates for " + record.get("Code"));
                coordinates.put(record.get("Code").trim(), new double[] {
                        Double.parseDouble(latitude),
                        Double.parseDouble(longitude)
                });
            }
        }
        return coordinates;
    }
}
