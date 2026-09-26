package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.migrations.V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LegacyUniqueIndexMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void removesLegacyUniqueIndexAndAllowsTwoFacilitiesToShareLocation() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Facility ADD UNIQUE INDEX UK_LEGACY_FACILITY_LOCATION (facilityLocation_id)");
            assertEquals(1, uniqueIndexCount(connection));
        }

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes migration =
                    new V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes();
            migration.migrate(em, null);
            migration.migrate(em, null); // Interrupted/repeated execution must be safe.
            em.getTransaction().commit();

            Company company = new Company();
            company.setName("Shared location cooperative");
            FacilityLocation location = new FacilityLocation();
            Facility first = new Facility();
            first.setName("First warehouse");
            first.setCompany(company);
            first.setFacilityLocation(location);
            Facility second = new Facility();
            second.setName("Second warehouse");
            second.setCompany(company);
            second.setFacilityLocation(location);

            em.getTransaction().begin();
            em.persist(company);
            em.persist(location);
            em.persist(first);
            em.persist(second);
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(0, uniqueIndexCount(connection));
            assertTrue(supportingIndexCount(connection) > 0);
        }
    }

    private long uniqueIndexCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Facility' " +
                             "AND COLUMN_NAME = 'facilityLocation_id' AND NON_UNIQUE = 0")) {
            result.next();
            return result.getLong(1);
        }
    }

    private long supportingIndexCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Facility' " +
                             "AND COLUMN_NAME = 'facilityLocation_id' AND SEQ_IN_INDEX = 1")) {
            result.next();
            return result.getLong(1);
        }
    }
}
