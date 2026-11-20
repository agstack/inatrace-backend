package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.components.flyway.JpaMigration;
import com.abelium.inatrace.db.entities.common.Country;
import jakarta.persistence.EntityManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backfills representative country coordinates from the packaged seed so the frontend can centre a
 * map on the farmer's country. Coordinates are the centres of the country bounding boxes in
 * geo/country-bboxes.csv (Natural Earth derived data). Countries whose bbox is missing, invalid or
 * crosses the antimeridian keep null and use the frontend's neutral fallback.
 *
 * <p>Existing non-null coordinates are preserved; the migration only fills rows that are still
 * empty. It runs once through Flyway, so both existing databases and new installations receive the
 * data.</p>
 */
public class V2026_11_20_12_00__Update_Country_Coordinates implements JpaMigration {

    private static final Logger logger =
            LoggerFactory.getLogger(V2026_11_20_12_00__Update_Country_Coordinates.class);

    private static final String COUNTRIES_RESOURCE = "db/seed/countries.csv";

    @Override
    public void migrate(EntityManager em, Environment environment) throws Exception {

        Map<String, double[]> coordinates = readCoordinates();
        if (coordinates.isEmpty()) {
            logger.warn("No country coordinates found in {}", COUNTRIES_RESOURCE);
            return;
        }

        List<Country> countries = em.createQuery("SELECT c FROM Country c", Country.class)
                .getResultList();

        int updated = 0;
        int preserved = 0;
        int withoutData = 0;

        for (Country country : countries) {
            double[] coordinate = coordinates.get(country.getCode());
            if (coordinate == null) {
                withoutData++;
                continue;
            }
            if (country.getLatitude() != null && country.getLongitude() != null) {
                preserved++;
                continue;
            }

            country.setLatitude(coordinate[0]);
            country.setLongitude(coordinate[1]);
            updated++;
        }

        logger.info("Country coordinates: updated={}, preserved={}, withoutData={}",
                updated, preserved, withoutData);
    }

    private Map<String, double[]> readCoordinates() throws Exception {

        ClassPathResource resource = new ClassPathResource(COUNTRIES_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing packaged country seed: " + COUNTRIES_RESOURCE);
        }

        Map<String, double[]> coordinates = new HashMap<>();
        try (InputStream input = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .withDelimiter(',')
                     .withIgnoreSurroundingSpaces(true)
                     .withFirstRecordAsHeader()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                String code = record.get("Code").trim();
                String latitude = record.get("Latitude").trim();
                String longitude = record.get("Longitude").trim();
                if (latitude.isEmpty() || longitude.isEmpty()) {
                    continue;
                }

                try {
                    coordinates.put(code, new double[] {
                            Double.parseDouble(latitude),
                            Double.parseDouble(longitude)
                    });
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Invalid coordinates for country " + code, e);
                }
            }
        }

        return coordinates;
    }
}
