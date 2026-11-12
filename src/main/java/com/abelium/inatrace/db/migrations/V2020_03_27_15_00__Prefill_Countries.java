package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.components.flyway.JpaMigration;
import com.abelium.inatrace.db.entities.common.Country;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class V2020_03_27_15_00__Prefill_Countries implements JpaMigration 
{
    private static final String COUNTRIES_RESOURCE = "db/seed/countries.csv";

    public void migrate(EntityManager em, Environment environment) throws Exception {
        ClassPathResource resource = new ClassPathResource(COUNTRIES_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing packaged country seed: " + COUNTRIES_RESOURCE);
        }

        try (InputStream input = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.
                     withDelimiter(',').
                     withIgnoreSurroundingSpaces(true).
                     withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord rec : parser) {
                String code = rec.get("Code");
                String name = rec.get("Name");

                Country c = new Country();
                c.setCode(code);
                c.setName(name);
                em.persist(c);
            }
        }
    }
}
