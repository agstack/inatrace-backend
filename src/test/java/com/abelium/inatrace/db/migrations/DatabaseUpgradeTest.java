package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.INATraceBackendApplication;
import com.abelium.inatrace.components.flyway.JpaMigrationResolver;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.auth.AuthenticationToken;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import com.abelium.inatrace.support.TestVendor;

/** Restores a pinned source fixture, then exercises the real current upgrade bootstrap. */
class DatabaseUpgradeTest {

    private static final String SOURCE_REVISION = "fb4fecf87c97c0ae65994850446a73bceb99d201";
    private static final String FIXTURE = "db/upgrade/fb4fecf/source-fixture.sql";
    private static final String LOCATIONS = "classpath:com/abelium/inatrace/db/migrations,classpath:/db/migrations/mysql,classpath:/db/migrations/common";
    private static final String IMPORT_DECISION_VERSION = "2026.11.06.12.00";
    private static final String IMPORT_DECISION_SCRIPT = "V2026_11_06_12_00__Create_Farmer_Import_Decision.sql";
    // Review migrations and schema deltas when the target changes; see docs/stability/database-upgrade.md.
    private static final Map<String, String> TARGET_MIGRATIONS = Map.of(
            "2026.09.23.12.00", "com.abelium.inatrace.db.migrations.V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes",
            "2026.10.13.12.00", "com.abelium.inatrace.db.migrations.V2026_10_13_12_00__Add_Document_Uploaded_By",
            "2026.10.17.12.00", "com.abelium.inatrace.db.migrations.V2026_10_17_12_00__Add_Authentication_Token_Expiration_Index",
            "2026.10.21.12.00", "com.abelium.inatrace.db.migrations.V2026_10_21_12_00__Deduplicate_Document_Uploader_Foreign_Keys");
    private static final List<JoinColumn> LEGACY_ONE_TO_ONE_COLUMNS = List.of(
            new JoinColumn("UserCustomer", "userCustomerLocation_id"),
            new JoinColumn("Company", "logo_id"), new JoinColumn("Facility", "facilityLocation_id"),
            new JoinColumn("StockOrderActivityProof", "activityProof_id"),
            new JoinColumn("BulkPaymentActivityProof", "activityProof_id"),
            new JoinColumn("Payment", "receiptDocument_id"),
            new JoinColumn("BusinessToCustomerSettings", "productFont_id"),
            new JoinColumn("BusinessToCustomerSettings", "textFont_id"),
            new JoinColumn("BusinessToCustomerSettings", "landingPageImage_id"),
            new JoinColumn("BusinessToCustomerSettings", "landingPageBackgroundImage_id"),
            new JoinColumn("BusinessToCustomerSettings", "headerBackgroundImage_id"),
            new JoinColumn("Product", "process_id"), new JoinColumn("Product", "responsibility_id"),
            new JoinColumn("Product", "sustainability_id"), new JoinColumn("Product", "journey_id"),
            new JoinColumn("Product", "settings_id"), new JoinColumn("Product", "businessToCustomerSettings_id"),
            new JoinColumn("ProductLabelContent", "process_id"),
            new JoinColumn("ProductLabelContent", "responsibility_id"),
            new JoinColumn("ProductLabelContent", "sustainability_id"),
            new JoinColumn("ProductLabelContent", "journey_id"),
            new JoinColumn("ProductLabelContent", "settings_id"),
            new JoinColumn("ProductLabelContent", "businessToCustomerSettings_id"),
            new JoinColumn("Certification", "certificate_id"),
            new JoinColumn("StockOrder", "productionLocation_id"),
            new JoinColumn("StockOrder", "consumerCompanyCustomer_id"),
            new JoinColumn("Transaction", "sourceStockOrder_id"),
            new JoinColumn("Transaction", "inputMeasureUnitType_id"));

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void requireMySqlForMigrationTests() {
        Assumptions.assumeTrue(TestVendor.isMySql(), "This migration test is MySQL-only");
    }

    @Test
    @Timeout(300)
    void upgradesPinnedHistoricalDatabaseWithoutLosingSyntheticData() throws Exception {
        Manifest manifest = readManifest();
        try (MySQLContainer mysql = new MySQLContainer(DockerImageName.parse(manifest.mysqlImage() + "@" + manifest.mysqlDigest())
                .asCompatibleSubstituteFor("mysql"))
                .withUrlParam("allowPublicKeyRetrieval", "true")
                .withUrlParam("useSSL", "false")
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))) {
            mysql.start();
            restoreSourceFixture(mysql);
            UpgradeSnapshot sourceSnapshot;
            String upgradedFixtureDataSha256;
            try (Connection source = connection(mysql)) {
                assertSourceState(source, manifest);
                assertHistorySignatureDetectsLastRowChange(source, manifest);
                assertSyntheticStateCanBeRecreated(source);
                assertSourceState(source, manifest);
                sourceSnapshot = snapshot(source);
            }

            try (ConfigurableApplicationContext update = start(mysql, "update")) {
                assertDdlMode(update, "update");
                assertExpectedSchemaDelta(sourceSnapshot.schema(), update.getBean(DataSource.class));
                assertFixtureDataPreserved(sourceSnapshot.fixtureDataSha256(), update.getBean(DataSource.class));
                assertPreservedData(update.getBean(EntityManagerFactory.class));
                assertTargetHistory(update.getBean(DataSource.class), manifest);
                assertTargetStructure(update.getBean(DataSource.class));
                assertLegacyIndexesWereMigrated(update.getBean(DataSource.class));
                assertCurrentJpaCanWriteSharedFacilityLocation(update.getBean(EntityManagerFactory.class));
                assertCurrentJpaCanWriteStockAndProcessing(update.getBean(EntityManagerFactory.class));
                assertNoPendingMigrations(update);
                assertFlywayValidationDetectsChangedHistoricalChecksum(update);
                upgradedFixtureDataSha256 = fixtureDataSignature(update.getBean(DataSource.class));
            }
            try (ConfigurableApplicationContext validate = start(mysql, "validate")) {
                assertDdlMode(validate, "validate");
                assertExpectedSchemaDelta(sourceSnapshot.schema(), validate.getBean(DataSource.class));
                assertPreservedData(validate.getBean(EntityManagerFactory.class));
                assertCurrentWritesPersisted(validate.getBean(EntityManagerFactory.class));
                assertFixtureDataPreserved(upgradedFixtureDataSha256, validate.getBean(DataSource.class));
                assertTargetHistory(validate.getBean(DataSource.class), manifest);
                assertTargetStructure(validate.getBean(DataSource.class));
                assertLegacyIndexesWereMigrated(validate.getBean(DataSource.class));
                assertNoPendingMigrations(validate);
            }
            try (ConfigurableApplicationContext restart = start(mysql, "update")) {
                assertDdlMode(restart, "update");
                assertExpectedSchemaDelta(sourceSnapshot.schema(), restart.getBean(DataSource.class));
                assertPreservedData(restart.getBean(EntityManagerFactory.class));
                assertCurrentWritesPersisted(restart.getBean(EntityManagerFactory.class));
                assertFixtureDataPreserved(upgradedFixtureDataSha256, restart.getBean(DataSource.class));
                assertTargetHistory(restart.getBean(DataSource.class), manifest);
                assertTargetStructure(restart.getBean(DataSource.class));
                assertLegacyIndexesWereMigrated(restart.getBean(DataSource.class));
                assertNoPendingMigrations(restart);
            }
        }
    }

    private ConfigurableApplicationContext start(MySQLContainer mysql, String ddlMode) {
        return new SpringApplicationBuilder(INATraceBackendApplication.class).run(
                "--spring.config.location=classpath:/application-test.properties", "--spring.profiles.active=test",
                "--server.port=0", "--spring.datasource.url=" + mysql.getJdbcUrl(),
                "--spring.datasource.username=" + mysql.getUsername(), "--spring.datasource.password=" + mysql.getPassword(),
                "--spring.datasource.hikari.minimum-idle=0", "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.jpa.hibernate.ddl-auto=" + ddlMode, "--spring.flyway.locations=" + LOCATIONS,
                "--spring.flyway.fail-on-missing-locations=true", "--spring.flyway.out-of-order=true",
                "--INATrace.fileStorage.root=" + temporaryDirectory.resolve("storage"),
                "--INATrace.documents.root=" + temporaryDirectory.resolve("documents"));
    }

    private void restoreSourceFixture(MySQLContainer mysql) throws Exception {
        try (Connection connection = connection(mysql); Statement statement = connection.createStatement()) {
            statement.execute("SET NAMES utf8mb4");
            statement.execute("SET SESSION time_zone = '+00:00'");
            statement.execute("SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO'");
            statement.execute("SET SESSION UNIQUE_CHECKS=0");
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try (ResultSet settings = statement.executeQuery("SELECT @@session.character_set_client, @@session.character_set_connection, @@session.time_zone, @@session.sql_mode, @@session.unique_checks, @@session.foreign_key_checks")) {
                assertTrue(settings.next());
                assertEquals("utf8mb4", settings.getString(1));
                assertEquals("utf8mb4", settings.getString(2));
                assertEquals("+00:00", settings.getString(3));
                assertTrue(settings.getString(4).contains("NO_AUTO_VALUE_ON_ZERO"));
                assertEquals(0, settings.getInt(5));
                assertEquals(0, settings.getInt(6));
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(FIXTURE), StandardCharsets.UTF_8));
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
            statement.execute("SET SESSION UNIQUE_CHECKS=1");
        }
    }

    private Connection connection(MySQLContainer mysql) throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private void assertSyntheticStateCanBeRecreated(Connection connection) throws Exception {
        UpgradeSnapshot expected = snapshot(connection);
        List<String[]> indexes = new java.util.ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS "
                     + "WHERE TABLE_SCHEMA=DATABASE() AND LEFT(INDEX_NAME, 10)='UK_LEGACY_'")) {
            while (result.next()) indexes.add(new String[]{result.getString(1), result.getString(2), result.getString(3)});
        }
        assertEquals(28, indexes.size(), "Fixture must contain the reviewed synthetic indexes");
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (int i = 0; i < indexes.size(); i++) {
                String[] index = indexes.get(i);
                statement.execute("ALTER TABLE `" + index[0] + "` ADD INDEX `IDX_TMP_UPGRADE_" + i + "` (`" + index[2] + "`)");
                statement.execute("ALTER TABLE `" + index[0] + "` DROP INDEX `" + index[1] + "`");
            }
            for (String deletion : List.of("AuthenticationToken:12001", "Transaction:10001", "StockOrder:9001,9002",
                    "ProcessingOrder:8001", "ProcessingAction:7001", "SemiProduct:6001", "Facility:5001",
                    "FacilityLocation:4001", "Document:11001", "CompanyUser:3001", "Company:2001", "User:1001")) {
                String[] parts = deletion.split(":");
                statement.executeUpdate("DELETE FROM `" + parts[0] + "` WHERE id IN (" + parts[1] + ")");
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/upgrade/fb4fecf/synthetic-state.sql"), StandardCharsets.UTF_8));
            for (int i = 0; i < indexes.size(); i++) {
                statement.execute("ALTER TABLE `" + indexes.get(i)[0] + "` DROP INDEX `IDX_TMP_UPGRADE_" + i + "`");
            }
        }
        assertEquals(expected, snapshot(connection), "Recreated synthetic graph and indexes must match the reviewed fixture");
    }

    private Manifest readManifest() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/db/upgrade/fb4fecf/manifest.properties")) {
            assertNotNull(input, "The reviewed upgrade manifest must be available");
            properties.load(input);
        }
        assertEquals(SOURCE_REVISION, properties.getProperty("source.revision"));
        assertTrue(IMPORT_DECISION_VERSION.equals(properties.getProperty("target.required.migration")),
                "Required migration must be included in TARGET_MIGRATIONS; see docs/stability/database-upgrade.md");
        assertEquals("sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d",
                properties.getProperty("mysql.image.digest"));
        assertEquals("org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl",
                properties.getProperty("source.hibernate.implicit_naming_strategy"));
        assertEquals("org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
                properties.getProperty("source.hibernate.physical_naming_strategy"));
        assertEquals("source-fixture.sql:schema_version INSERT rows", properties.getProperty("source.history.inventory"));
        assertEquals("rank-version-description-type-script-checksum-success-pipe-lf-utf8-v1",
                properties.getProperty("source.history.signature"));
        assertEquals("source-produced-schema-and-history-with-synthetic-legacy-state", properties.getProperty("fixture.kind"));
        assertNotNull(properties.getProperty("fixture.synthetic_graph"));
        assertNotNull(properties.getProperty("fixture.synthetic_legacy_indexes"));
        assertEquals(sha256(FIXTURE), properties.getProperty("fixture.sha256"));
        return new Manifest(properties.getProperty("mysql.image"), properties.getProperty("mysql.image.digest"),
                Integer.parseInt(properties.getProperty("source.table.count")),
                Integer.parseInt(properties.getProperty("source.history.count")),
                Integer.parseInt(properties.getProperty("source.sql.checksum.count")),
                properties.getProperty("source.history.sha256"));
    }

    private String sha256(String resource) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(input, "The reviewed source fixture must be available");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private void assertSourceState(Connection connection, Manifest manifest) throws Exception {
        assertEquals(manifest.tableCount(), scalar(connection, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
        assertEquals(manifest.historyCount(), scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE success"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE type='BASELINE' AND version='1'"));
        assertEquals(41, scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE type <> 'BASELINE'"));
        assertEquals(manifest.sqlChecksumCount(), scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE checksum IS NOT NULL"));
        assertEquals(manifest.historySha256(), sourceHistorySignature(connection));
        assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE version LIKE '2026.%'"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM Facility WHERE id=5001 AND facilityLocation_id=4001"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM StockOrder WHERE id=9001 AND availableQuantity=70.00 "
                + "AND fulfilledQuantity=30.00 AND totalQuantity=100.00"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM `Transaction` WHERE id=10001 AND sourceStockOrder_id=9001 "
                + "AND targetProcessingOrder_id=8001 AND inputQuantity=30.00 AND outputQuantity=25.00"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM Document WHERE id=11001 AND storageKey='upgrade-fixture-document'"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM AuthenticationToken WHERE id=12001 AND token='upgrade-fixture-token'"));
        assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='Document' AND COLUMN_NAME='uploadedBy_id'"));
        assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='AuthenticationToken' AND INDEX_NAME='IDX_AUTH_TOKEN_EXPIRATION'"));
        for (JoinColumn join : LEGACY_ONE_TO_ONE_COLUMNS) assertEquals(1, legacyUniqueIndexCount(connection, join), "Fixture must retain " + join);
    }

    private void assertTargetHistory(DataSource dataSource, Manifest manifest) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> actual = rows(connection, "SELECT CONCAT(version, '|', script, '|', type, '|', COALESCE(CAST(checksum AS CHAR), '<NULL>'), '|', success) FROM schema_version WHERE installed_rank > " + manifest.historyCount() + " AND version <> '" + IMPORT_DECISION_VERSION + "'");
            Set<String> expected = new LinkedHashSet<>();
            TARGET_MIGRATIONS.forEach((version, script) -> expected.add(version + "|" + script + "|CUSTOM|<NULL>|1"));
            assertEquals(expected, actual, "Unexpected or missing migrations: extra=" + difference(actual, expected)
                    + ", missing=" + difference(expected, actual) + "; see docs/stability/database-upgrade.md");
            assertEquals(manifest.historyCount() + TARGET_MIGRATIONS.size() + 1, scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE success"),
                    "Unexpected migration count; see docs/stability/database-upgrade.md");
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE version='" + IMPORT_DECISION_VERSION
                    + "' AND script='" + IMPORT_DECISION_SCRIPT + "' AND type='SQL' AND checksum IS NOT NULL AND success"));
            assertEquals(manifest.historySha256(), sourceHistorySignature(connection));
            for (Map.Entry<String, String> migration : TARGET_MIGRATIONS.entrySet()) {
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM schema_version WHERE version='" + migration.getKey()
                        + "' AND script='" + migration.getValue() + "' AND type='CUSTOM' AND checksum IS NULL AND success"));
            }
        }
    }

    private void assertTargetStructure(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                    + "AND TABLE_NAME='Document' AND COLUMN_NAME='uploadedBy_id'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM Document WHERE id=11001 AND uploadedBy_id IS NULL"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                    + "AND TABLE_NAME='Document' AND INDEX_NAME='IDX_DOCUMENT_UPLOADED_BY' AND COLUMN_NAME='uploadedBy_id'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() "
                    + "AND TABLE_NAME='Document' AND CONSTRAINT_NAME='FK_DOCUMENT_UPLOADED_BY' AND CONSTRAINT_TYPE='FOREIGN KEY'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(DISTINCT CONSTRAINT_NAME) FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Document' AND COLUMN_NAME='uploadedBy_id' "
                    + "AND REFERENCED_TABLE_NAME='User' AND REFERENCED_COLUMN_NAME='id'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                    + "AND TABLE_NAME='AuthenticationToken' AND INDEX_NAME='IDX_AUTH_TOKEN_EXPIRATION' AND COLUMN_NAME='expiration'"));
        }
    }

    private void assertLegacyIndexesWereMigrated(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (JoinColumn join : LEGACY_ONE_TO_ONE_COLUMNS) {
                assertEquals(0, legacyUniqueIndexCount(connection, join), "Target must remove " + join);
                assertTrue(leadingIndexCount(connection, join) >= 1, "Target must retain a supporting index for " + join);
            }
        }
    }

    private void assertPreservedData(EntityManagerFactory entityManagerFactory) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            User user = entityManager.find(User.class, 1001L);
            assertNotNull(user);
            assertEquals("upgrade-fixture-user@inatrace.test", user.getEmail());
            assertEquals("Upgrade", user.getName());
            assertEquals("Fixture", user.getSurname());
            Company company = entityManager.find(Company.class, 2001L);
            assertNotNull(company);
            assertEquals("upgrade-fixture-company", company.getName());
            CompanyUser membership = entityManager.find(CompanyUser.class, 3001L);
            assertNotNull(membership);
            assertEquals(1001L, membership.getUser().getId());
            assertEquals(2001L, membership.getCompany().getId());
            Facility facility = entityManager.find(Facility.class, 5001L);
            assertNotNull(facility);
            assertEquals("upgrade-fixture-facility", facility.getName());
            assertEquals(4001L, facility.getFacilityLocation().getId());
            StockOrder source = entityManager.find(StockOrder.class, 9001L);
            assertNotNull(source);
            assertDecimal("100.00", source.getTotalQuantity());
            assertDecimal("70.00", source.getAvailableQuantity());
            assertDecimal("30.00", source.getFulfilledQuantity());
            StockOrder output = entityManager.find(StockOrder.class, 9002L);
            assertNotNull(output);
            assertEquals("upgrade-fixture-output-stock", output.getIdentifier());
            assertDecimal("25.00", output.getTotalQuantity());
            assertDecimal("25.00", output.getAvailableQuantity());
            assertEquals(8001L, output.getProcessingOrder().getId());
            ProcessingOrder processingOrder = entityManager.find(ProcessingOrder.class, 8001L);
            assertNotNull(processingOrder);
            assertNotNull(processingOrder.getProcessingAction());
            com.abelium.inatrace.db.entities.stockorder.Transaction transaction = entityManager.find(
                    com.abelium.inatrace.db.entities.stockorder.Transaction.class, 10001L);
            assertNotNull(transaction);
            assertEquals(9001L, transaction.getSourceStockOrder().getId());
            assertEquals(8001L, transaction.getTargetProcessingOrder().getId());
            assertDecimal("30.00", transaction.getInputQuantity());
            assertDecimal("25.00", transaction.getOutputQuantity());
            assertEquals("EXECUTED", transaction.getStatus().name());
            Document document = entityManager.find(Document.class, 11001L);
            assertNotNull(document);
            assertEquals("upgrade-fixture-document", document.getStorageKey());
            assertEquals("upgrade-fixture-document.txt", document.getName());
            assertEquals("text/plain", document.getContentType());
            assertEquals(12L, document.getSize());
            assertEquals("GENERAL", document.getType().name());
            assertNull(document.getUploadedBy(), "Historical documents must not acquire an inferred uploader");
            AuthenticationToken token = entityManager.find(AuthenticationToken.class, 12001L);
            assertNotNull(token);
            assertEquals("upgrade-fixture-token", token.getToken());
            assertEquals(1001L, token.getUser().getId());
            assertEquals("ACTIVE", token.getStatus().name());
            FacilityType facilityType = entityManager.find(FacilityType.class, 4L);
            assertNotNull(facilityType);
            assertEquals("STORAGE", facilityType.getCode());
            MeasureUnitType measureUnitType = entityManager.find(MeasureUnitType.class, 2L);
            assertNotNull(measureUnitType);
            assertEquals("WEIGHT_KG", measureUnitType.getCode());
            assertDecimal("1.00", measureUnitType.getWeight());
        }
    }

    private void assertCurrentJpaCanWriteSharedFacilityLocation(EntityManagerFactory entityManagerFactory) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            Facility shared = new Facility("upgrade-fixture-shared-location", false, false, false,
                    entityManager.getReference(FacilityLocation.class, 4001L), entityManager.getReference(Company.class, 2001L),
                    entityManager.getReference(FacilityType.class, 4L), null, null, null);
            entityManager.persist(shared);
            entityManager.getTransaction().commit();
            assertNotNull(shared.getId());
            entityManager.clear();
            Facility reloaded = entityManager.find(Facility.class, shared.getId());
            assertNotNull(reloaded);
            assertEquals(4001L, reloaded.getFacilityLocation().getId());
        }
    }

    private void assertCurrentJpaCanWriteStockAndProcessing(EntityManagerFactory entityManagerFactory) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            StockOrder output = entityManager.find(StockOrder.class, 9002L);
            ProcessingOrder processingOrder = entityManager.find(ProcessingOrder.class, 8001L);
            ProcessingAction processingAction = entityManager.find(ProcessingAction.class, 7001L);
            assertNotNull(output);
            assertNotNull(processingOrder);
            assertNotNull(processingAction);
            output.setComments("upgrade-fixture-current-stock-write");
            processingAction.setPublicTimelineLabel("upgrade-fixture-current-processing-write");
            entityManager.getTransaction().commit();
            entityManager.clear();
            assertEquals("upgrade-fixture-current-stock-write", entityManager.find(StockOrder.class, 9002L).getComments());
            assertEquals("upgrade-fixture-current-processing-write",
                    entityManager.find(ProcessingAction.class, 7001L).getPublicTimelineLabel());
        }
    }

    private void assertCurrentWritesPersisted(EntityManagerFactory entityManagerFactory) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            assertEquals("upgrade-fixture-current-stock-write", entityManager.find(StockOrder.class, 9002L).getComments());
            assertEquals("upgrade-fixture-current-processing-write",
                    entityManager.find(ProcessingAction.class, 7001L).getPublicTimelineLabel());
        }
    }

    private UpgradeSnapshot snapshot(Connection connection) throws Exception {
        return new UpgradeSnapshot(schemaSnapshot(connection), fixtureDataSignature(connection));
    }

    private SchemaSnapshot schemaSnapshot(Connection connection) throws Exception {
        return new SchemaSnapshot(rows(connection, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"),
                rows(connection, "SELECT CONCAT(TABLE_NAME, '|', COLUMN_NAME, '|', COLUMN_TYPE, '|', IS_NULLABLE, '|', "
                        + "COALESCE(COLUMN_DEFAULT, '<NULL>'), '|', COALESCE(CHARACTER_SET_NAME, '<NONE>'), '|', "
                        + "COALESCE(COLLATION_NAME, '<NONE>')) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE()"),
                rows(connection, "SELECT CONCAT(TABLE_NAME, '|', INDEX_NAME, '|', NON_UNIQUE, '|', INDEX_TYPE, '|', "
                        + "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA=DATABASE() GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE, INDEX_TYPE"),
                rows(connection, "SELECT CONCAT(k.TABLE_NAME, '|', k.CONSTRAINT_NAME, '|', "
                        + "GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR ','), '|', "
                        + "MAX(k.REFERENCED_TABLE_NAME), '|', GROUP_CONCAT(k.REFERENCED_COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR ','), '|', "
                        + "MAX(rc.UPDATE_RULE), '|', MAX(rc.DELETE_RULE)) "
                        + "FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS rc "
                        + "ON rc.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME=k.CONSTRAINT_NAME "
                        + "AND rc.TABLE_NAME=k.TABLE_NAME WHERE k.TABLE_SCHEMA=DATABASE() "
                        + "AND k.REFERENCED_TABLE_NAME IS NOT NULL GROUP BY k.TABLE_NAME, k.CONSTRAINT_NAME"));
    }

    private void assertExpectedSchemaDelta(SchemaSnapshot source, DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            SchemaSnapshot target = schemaSnapshot(connection);
            assertEquals(Set.of("FarmerImportDecision"), difference(target.tables(), source.tables()), "Unexpected added tables");
            assertTrue(difference(source.tables(), target.tables()).isEmpty(), "Target must not remove source tables");
            Set<String> addedColumns = difference(target.columns(), source.columns());
            assertEquals(5, addedColumns.size(), "Unexpected added columns: " + addedColumns + "; see docs/stability/database-upgrade.md");
            for (String prefix : List.of("Document|uploadedBy_id|bigint|YES|<NULL>|", "FarmerImportDecision|id|bigint|NO|",
                    "FarmerImportDecision|companyId|bigint|NO|", "FarmerImportDecision|documentId|bigint|NO|",
                    "FarmerImportDecision|selectionKey|varchar(64)|NO|")) {
                assertTrue(addedColumns.stream().anyMatch(column -> column.startsWith(prefix)), "Missing expected column: " + prefix);
            }
            assertTrue(difference(source.columns(), target.columns()).isEmpty(), "Target must not remove source columns");
            Set<String> removedIndexes = difference(source.indexes(), target.indexes());
            assertEquals(legacyUniqueIndexes(source.indexes()), removedIndexes, "Unexpected removed indexes; see docs/stability/database-upgrade.md");
            assertEquals(expectedAddedIndexes(), difference(target.indexes(), source.indexes()), "Unexpected added indexes; see docs/stability/database-upgrade.md");
            assertTrue(difference(source.foreignKeys(), target.foreignKeys()).isEmpty(), "Target must not remove source foreign keys");
            assertExpectedDocumentUploaderForeignKeys(difference(target.foreignKeys(), source.foreignKeys()));
        }
    }

    private Set<String> legacyUniqueIndexes(Set<String> indexes) {
        Set<String> result = new LinkedHashSet<>();
        for (JoinColumn join : LEGACY_ONE_TO_ONE_COLUMNS) {
            String prefix = join.table() + "|";
            String suffix = "|0|BTREE|" + join.column();
            indexes.stream().filter(index -> index.startsWith(prefix) && index.endsWith(suffix)).forEach(result::add);
        }
        assertEquals(LEGACY_ONE_TO_ONE_COLUMNS.size(), result.size(), "Fixture must have every legacy unique index");
        return result;
    }

    private Set<String> expectedAddedIndexes() {
        Set<String> expected = new LinkedHashSet<>();
        expected.add("AuthenticationToken|IDX_AUTH_TOKEN_EXPIRATION|1|BTREE|expiration");
        expected.add("Document|IDX_DOCUMENT_UPLOADED_BY|1|BTREE|uploadedBy_id");
        expected.add("FarmerImportDecision|PRIMARY|0|BTREE|id");
        expected.add("FarmerImportDecision|UK_farmer_import_decision|0|BTREE|companyId,documentId,selectionKey");
        expected.add("FarmerImportDecision|FK_farmer_import_document|1|BTREE|documentId");
        for (JoinColumn join : LEGACY_ONE_TO_ONE_COLUMNS) {
            expected.add(join.table() + "|IDX_SHARED_" + join.column() + "|1|BTREE|" + join.column());
        }
        // Hibernate 7.4 names generated unique indexes IDX... instead of UK.... On an
        // existing database it adds the new index beside the legacy constraint, so the
        // upgrade path keeps both until a dedicated cleanup migration removes the old one.
        expected.add("CompanyTranslation|IDXhg3olp2ynrkn4htdvmn7va2qs|0|BTREE|company_id,language");
        expected.add("CompanyValueChain|IDXbbly17yyupinf6ryi9o4vjnta|0|BTREE|valueChain_id,company_id");
        expected.add("User|IDXe6gkqunxajvyxl5uctpl2vl2p|0|BTREE|email");
        expected.add("ValueChainFacilityType|IDXp4nh3wfjqp8grrrny232u2jkc|0|BTREE|valueChain_id,facilityType_id");
        expected.add("ValueChainMeasureUnitType|IDXbkw7lhamgim4k6ef78x7nh50q|0|BTREE|valueChain_id,measureUnitType_id");
        expected.add("ValueChainProcEvidenceType|IDX6odw4g6gvmlt1xvbjqyf0bh51|0|BTREE|valueChain_id,processingEvidenceType_id");
        expected.add("ValueChainSemiProduct|IDXlsuycp13s1d417cikemfdp0b7|0|BTREE|valueChain_id,semiProduct_id");
        return expected;
    }

    private void assertExpectedDocumentUploaderForeignKeys(Set<String> addedForeignKeys) {
        assertEquals(Set.of("Document|FK_DOCUMENT_UPLOADED_BY|uploadedBy_id|User|id|NO ACTION|NO ACTION",
                "FarmerImportDecision|FK_farmer_import_company|companyId|Company|id|NO ACTION|CASCADE",
                "FarmerImportDecision|FK_farmer_import_document|documentId|Document|id|NO ACTION|CASCADE"), addedForeignKeys,
                "Uploader and import decision foreign keys must match the reviewed schema");
    }

    private void assertFixtureDataPreserved(String sourceFixtureDataSha256, DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(sourceFixtureDataSha256, fixtureDataSignature(connection),
                    "Target bootstrap must preserve every synthetic fixture marker before current-model writes");
        }
    }

    private String fixtureDataSignature(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return fixtureDataSignature(connection);
        }
    }

    private String fixtureDataSignature(Connection connection) throws Exception {
        StringBuilder values = new StringBuilder();
        for (String query : List.of(
                "SELECT CONCAT_WS('|', 'User', id, email, name, surname, status, role, language) FROM `User` WHERE id=1001",
                "SELECT CONCAT_WS('|', 'Company', id, name, status) FROM Company WHERE id=2001",
                "SELECT CONCAT_WS('|', 'CompanyUser', id, user_id, company_id, role) FROM CompanyUser WHERE id=3001",
                "SELECT CONCAT_WS('|', 'FacilityLocation', id, pinName, latitude, longitude) FROM FacilityLocation WHERE id=4001",
                "SELECT CONCAT_WS('|', 'Facility', id, name, company_id, facilityLocation_id, facilityType_id) FROM Facility WHERE id=5001",
                "SELECT CONCAT_WS('|', 'StockOrder', id, identifier, totalQuantity, fulfilledQuantity, availableQuantity, orderType, company_id, facility_id, COALESCE(processingOrder_id, 'NULL'), COALESCE(comments, 'NULL')) FROM StockOrder WHERE id IN (9001, 9002) ORDER BY id",
                "SELECT CONCAT_WS('|', 'ProcessingOrder', id, initiatorUserId, processingDate, processingAction_id) FROM ProcessingOrder WHERE id=8001",
                "SELECT CONCAT_WS('|', 'ProcessingAction', id, COALESCE(publicTimelineLabel, 'NULL')) FROM ProcessingAction WHERE id=7001",
                "SELECT CONCAT_WS('|', 'Transaction', id, status, sourceStockOrder_id, targetProcessingOrder_id, inputQuantity, outputQuantity) FROM `Transaction` WHERE id=10001",
                "SELECT CONCAT_WS('|', 'Document', id, storageKey, type, name, contentType, size) FROM Document WHERE id=11001",
                "SELECT CONCAT_WS('|', 'AuthenticationToken', id, token, expiration, status, user_id) FROM AuthenticationToken WHERE id=12001",
                "SELECT CONCAT_WS('|', 'FacilityType', id, code, label) FROM FacilityType WHERE id=4",
                "SELECT CONCAT_WS('|', 'MeasureUnitType', id, code, label, weight) FROM MeasureUnitType WHERE id=2")) {
            for (String row : rows(connection, query)) values.append(row).append('\n');
        }
        return sha256(values.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Set<String> rows(Connection connection, String sql) throws Exception {
        Set<String> rows = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) rows.add(result.getString(1));
        }
        return rows;
    }

    private Set<String> difference(Set<String> first, Set<String> second) {
        Set<String> difference = new LinkedHashSet<>(first);
        difference.removeAll(second);
        return difference;
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02x", value));
        return result.toString();
    }

    private void assertNoPendingMigrations(ConfigurableApplicationContext context) {
        Flyway effectiveFlyway = effectiveFlyway(context);
        assertEquals(0, effectiveFlyway.info().pending().length, "Target bootstrap must leave no pending migrations");
        effectiveFlyway.validate();
    }

    private Flyway effectiveFlyway(ConfigurableApplicationContext context) {
        Flyway flyway = context.getBean(Flyway.class);
        ClassicConfiguration configuration = new ClassicConfiguration(flyway.getConfiguration());
        configuration.setResolvers(new JpaMigrationResolver(flyway.getConfiguration(),
                context.getBean(EntityManagerFactory.class), context.getEnvironment()));
        return Flyway.configure().configuration(configuration).load();
    }

    private void assertFlywayValidationDetectsChangedHistoricalChecksum(ConfigurableApplicationContext context) throws Exception {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertEquals(1, statement.executeUpdate("UPDATE schema_version SET checksum=checksum+1 WHERE version='2023.06.15.10.04'"));
            try {
                assertThrows(FlywayException.class, () -> effectiveFlyway(context).validate(),
                        "Flyway validation must detect a changed historical SQL checksum");
            } finally {
                assertEquals(1, statement.executeUpdate("UPDATE schema_version SET checksum=checksum-1 WHERE version='2023.06.15.10.04'"));
            }
        }
        effectiveFlyway(context).validate();
    }

    private void assertDdlMode(ConfigurableApplicationContext context, String expected) {
        assertEquals(expected, context.getBean(EntityManagerFactory.class).getProperties().get("hibernate.hbm2ddl.auto"));
    }

    private long legacyUniqueIndexCount(Connection connection, JoinColumn join) throws Exception {
        return scalar(connection, "SELECT COUNT(*) FROM (SELECT INDEX_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + join.table() + "' AND COLUMN_NAME='" + join.column()
                + "' AND NON_UNIQUE=0 AND INDEX_NAME <> 'PRIMARY' GROUP BY INDEX_NAME HAVING COUNT(*)=1) indexes");
    }

    private long leadingIndexCount(Connection connection, JoinColumn join) throws Exception {
        return scalar(connection, "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='" + join.table() + "' AND COLUMN_NAME='" + join.column() + "' AND SEQ_IN_INDEX=1");
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String sourceHistorySignature(Connection connection) throws Exception {
        String sql = "SELECT CONCAT_WS('|', installed_rank, COALESCE(version,''), description, "
                + "type, script, COALESCE(CAST(checksum AS CHAR),''), success) FROM schema_version "
                + "WHERE version IS NULL OR version NOT LIKE '2026.%' ORDER BY installed_rank";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            StringBuilder history = new StringBuilder();
            while (result.next()) history.append(result.getString(1)).append('\n');
            return sha256(history.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void assertHistorySignatureDetectsLastRowChange(Connection connection, Manifest manifest) throws Exception {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            assertEquals(1, statement.executeUpdate("UPDATE schema_version SET description=CONCAT(description, '-changed') WHERE installed_rank=" + manifest.historyCount()));
            assertNotEquals(manifest.historySha256(), sourceHistorySignature(connection),
                    "The signature must cover the final historical migration");
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private record JoinColumn(String table, String column) { }

    private record UpgradeSnapshot(SchemaSnapshot schema, String fixtureDataSha256) { }

    private record SchemaSnapshot(Set<String> tables, Set<String> columns, Set<String> indexes, Set<String> foreignKeys) { }

    private record Manifest(String mysqlImage, String mysqlDigest, int tableCount, int historyCount, int sqlChecksumCount,
                            String historySha256) { }
}
