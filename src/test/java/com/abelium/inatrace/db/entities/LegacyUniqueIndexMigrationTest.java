package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.components.flyway.JpaMigrationStrategy;
import com.abelium.inatrace.db.entities.auth.AuthenticationToken;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.migrations.V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes;
import com.abelium.inatrace.db.migrations.V2026_10_17_12_00__Add_Authentication_Token_Expiration_Index;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LegacyUniqueIndexMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"));

    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Environment environment;

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

    @Test
    void addsRefreshExpirationIndexToLegacySchemaIdempotently() throws Exception {
        EntityManager em = entityManagerFactory.createEntityManager();
        Long sessionId;
        try {
            em.getTransaction().begin();
            User user = new User();
            user.setEmail("migration-session@test.invalid");
            em.persist(user);
            AuthenticationToken session = new AuthenticationToken(user);
            session.setToken("legacy-session-hash");
            session.setExpiration(Instant.now().plus(1, ChronoUnit.DAYS));
            em.persist(session);
            em.flush();
            sessionId = session.getId();
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `AuthenticationToken` DROP INDEX `IDX_AUTH_TOKEN_EXPIRATION`");
        }
        // Use a separate Flyway history, starting immediately before this migration, so no
        // unrelated historical migrations are replayed against the isolated test database.
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:com/abelium/inatrace/db/migrations")
                .table("auth_token_test_schema_version")
                .baselineOnMigrate(true).baselineVersion("2026.10.17.11.59")
                .load();
        JpaMigrationStrategy strategy = new JpaMigrationStrategy(entityManagerFactory, environment);
        strategy.migrate(flyway);
        strategy.migrate(flyway);

        em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            assertEquals("legacy-session-hash", em.find(AuthenticationToken.class, sessionId).getToken());
            V2026_10_17_12_00__Add_Authentication_Token_Expiration_Index migration =
                    new V2026_10_17_12_00__Add_Authentication_Token_Expiration_Index();
            migration.migrate(em, environment); // Interrupted/repeated execution must be safe.
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.STATISTICS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AuthenticationToken' "
                     + "AND INDEX_NAME = 'IDX_AUTH_TOKEN_EXPIRATION' AND COLUMN_NAME = 'expiration'")) {
            result.next();
            assertEquals(1, result.getLong(1));
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
