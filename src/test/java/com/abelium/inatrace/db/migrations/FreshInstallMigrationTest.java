package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.INATraceBackendApplication;
import com.abelium.inatrace.components.flyway.JpaMigration;
import com.abelium.inatrace.components.flyway.JpaMigrationResolver;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the supported Hibernate-then-Flyway bootstrap on a dedicated empty MySQL. */
class FreshInstallMigrationTest {

    private static final Logger log = LoggerFactory.getLogger(FreshInstallMigrationTest.class);
    private static final String JAVA_PACKAGE = "com.abelium.inatrace.db.migrations.";
    private static final String LOCATIONS = "classpath:com/abelium/inatrace/db/migrations,classpath:/db/migrations";

    @TempDir
    Path temporaryDirectory;

    @Test
    void inventoryDetectsUnreviewedAndMissingMigrationResources() throws Exception {
        List<Migration> withoutCustom = new ArrayList<>(migrationInventory());
        assertTrue(withoutCustom.remove(new Migration("CUSTOM", JAVA_PACKAGE + "V2020_03_27_15_00__Prefill_Countries")));
        assertThrows(AssertionError.class, () -> assertInventoryMatchesResources(withoutCustom));

        List<Migration> withMissingSql = new ArrayList<>(migrationInventory());
        withMissingSql.add(new Migration("SQL", "V2099_01_01_00_00__Missing.sql"));
        assertThrows(AssertionError.class, () -> assertInventoryMatchesResources(withMissingSql));
    }

    @Test
    @Timeout(300)
    void freshBootstrapRunsMigrationsValidatesAndRestartsWithoutRepeatingThem() throws Exception {
        long started = System.nanoTime();
        List<Migration> inventory = migrationInventory();
        assertInventoryMatchesResources(inventory);

        Path importDirectory = Path.of("import").toAbsolutePath().normalize();
        Path countries = importDirectory.resolve("countries.csv");
        assertTrue(Files.isRegularFile(countries), "The real countries.csv is required for bootstrap");
        Map<String, String> expectedCountries = countriesFromCsv(countries);
        assertFalse(expectedCountries.isEmpty());

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11")
                .withUrlParam("allowPublicKeyRetrieval", "true")
                .withUrlParam("useSSL", "false")
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))) {
            mysql.start();
            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE()"), "Bootstrap must start with an empty schema");
            }

            List<History> history;
            List<String> schema;
            Fixture fixture;
            try (ConfigurableApplicationContext context = start(mysql, importDirectory, "update")) {
                assertDdlMode(context, "update");
                DataSource dataSource = context.getBean(DataSource.class);
                assertHistory(dataSource, inventory);
                assertNoPendingMigrations(context);
                assertReferenceData(dataSource, expectedCountries);
                assertSelectedStructure(dataSource);
                fixture = createFixture(context.getBean(EntityManagerFactory.class));
                history = history(dataSource);
                schema = schemaSnapshot(dataSource);
            }

            try (ConfigurableApplicationContext context = start(mysql, importDirectory, "validate")) {
                assertDdlMode(context, "validate");
                DataSource dataSource = context.getBean(DataSource.class);
                assertEquals(history, history(dataSource), "Validation must not run additional migrations");
                assertEquals(schema, schemaSnapshot(dataSource), "Validation must not repair the schema");
                assertNoPendingMigrations(context);
                assertReferenceData(dataSource, expectedCountries);
                verifyAndUpdateFixture(context.getBean(EntityManagerFactory.class), fixture);
            }

            try (ConfigurableApplicationContext context = start(mysql, importDirectory, "update")) {
                assertDdlMode(context, "update");
                DataSource dataSource = context.getBean(DataSource.class);
                assertEquals(history, history(dataSource), "Restart must not repeat migrations");
                assertEquals(schema, schemaSnapshot(dataSource), "Restart must not modify the schema");
                assertNoPendingMigrations(context);
                assertReferenceData(dataSource, expectedCountries);
                assertSelectedStructure(dataSource);
                assertFixture(context.getBean(EntityManagerFactory.class), fixture);
            }
        } finally {
            log.info("Fresh-install migration test: one dedicated MySQL container, three sequential "
                    + "application contexts, duration={} ms", Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
    }

    private ConfigurableApplicationContext start(MySQLContainer<?> mysql, Path importDirectory, String ddlMode) {
        return new SpringApplicationBuilder(INATraceBackendApplication.class).run(
                "--spring.config.location=classpath:/application-test.properties",
                "--spring.profiles.active=test",
                "--server.port=0",
                "--spring.datasource.url=" + mysql.getJdbcUrl(),
                "--spring.datasource.username=" + mysql.getUsername(),
                "--spring.datasource.password=" + mysql.getPassword(),
                "--spring.datasource.hikari.minimum-idle=0",
                "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.jpa.hibernate.ddl-auto=" + ddlMode,
                "--spring.flyway.locations=" + LOCATIONS,
                "--spring.flyway.fail-on-missing-locations=true",
                "--spring.flyway.out-of-order=true",
                "--INATrace.import.path=" + importDirectory + java.io.File.separator,
                "--INATrace.fileStorage.root=" + temporaryDirectory.resolve("storage"),
                "--INATrace.documents.root=" + temporaryDirectory.resolve("documents"));
    }

    private void assertDdlMode(ConfigurableApplicationContext context, String expected) {
        assertEquals(expected, context.getBean(EntityManagerFactory.class).getProperties().get("hibernate.hbm2ddl.auto"),
                "The active Hibernate schema action must match this bootstrap phase");
    }

    private List<Migration> migrationInventory() throws Exception {
        List<Migration> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("db/fresh-install-migrations.txt").getInputStream(), StandardCharsets.UTF_8))) {
            for (String line : reader.lines().map(String::trim).toList()) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(" ", 2);
                assertEquals(2, parts.length, "Invalid migration inventory line: " + line);
                assertTrue(Set.of("SQL", "CUSTOM").contains(parts[0]), line);
                result.add(new Migration(parts[0], parts[1]));
            }
        }
        assertFalse(result.isEmpty());
        assertEquals(result.size(), Set.copyOf(result).size(), "Duplicate inventory entries");
        assertEquals(result.size(), result.stream().map(Migration::version).distinct().count(),
                "Migration versions must be unique");
        return result;
    }

    private void assertInventoryMatchesResources(List<Migration> expected) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Set<Migration> resources = new TreeSet<>((a, b) -> (a.type() + a.script()).compareTo(b.type() + b.script()));
        for (Resource resource : resolver.getResources("classpath*:com/abelium/inatrace/db/migrations/*.class")) {
            String filename = resource.getFilename();
            if (filename != null && !filename.contains("$")) {
                String className = JAVA_PACKAGE + filename.substring(0, filename.length() - 6);
                Class<?> type = Class.forName(className, false, getClass().getClassLoader());
                if (JpaMigration.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                    resources.add(new Migration("CUSTOM", className));
                }
            }
        }
        for (Resource resource : resolver.getResources("classpath*:db/migrations/*.sql")) {
            resources.add(new Migration("SQL", resource.getFilename()));
        }
        assertEquals(Set.copyOf(expected), resources, "Update the reviewed inventory when migrations change");
    }

    private void assertHistory(DataSource dataSource, List<Migration> expected) throws Exception {
        List<History> rows = history(dataSource);
        assertTrue(rows.stream().allMatch(History::success), "A failed migration is recorded");
        List<History> baselines = rows.stream().filter(row -> "BASELINE".equals(row.type())).toList();
        assertEquals(1, baselines.size(), "Hibernate creates tables before Flyway baselines the schema");
        assertEquals(MigrationVersion.fromVersion("1"), MigrationVersion.fromVersion(baselines.get(0).version()));
        List<History> applied = rows.stream().filter(row -> !"BASELINE".equals(row.type())).toList();
        assertEquals(expected.size(), applied.size(), "Unexpected, missing or duplicate history entries");
        assertEquals(Set.copyOf(expected), applied.stream().map(row -> new Migration(row.type(), row.script()))
                .collect(Collectors.toSet()), "Applied scripts/types differ from the reviewed inventory");
        for (History row : applied) {
            Migration migration = new Migration(row.type(), row.script());
            assertEquals(migration.version(), MigrationVersion.fromVersion(row.version()), row.script());
        }
    }

    private void assertNoPendingMigrations(ConfigurableApplicationContext context) {
        Flyway bean = context.getBean(Flyway.class);
        ClassicConfiguration configuration = new ClassicConfiguration(bean.getConfiguration());
        configuration.setResolvers(new JpaMigrationResolver(bean.getConfiguration(),
                context.getBean(EntityManagerFactory.class), context.getEnvironment()));
        Flyway effectiveFlyway = Flyway.configure().configuration(configuration).load();
        assertEquals(0, effectiveFlyway.info().pending().length, "Effective Flyway resolver has pending migrations");
    }

    private List<History> history(DataSource dataSource) throws Exception {
        List<History> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT version, type, script, success FROM schema_version ORDER BY installed_rank")) {
            while (result.next()) {
                rows.add(new History(result.getString(1), result.getString(2), result.getString(3), result.getBoolean(4)));
            }
        }
        return rows;
    }

    private Map<String, String> countriesFromCsv(Path csv) throws Exception {
        Map<String, String> countries = new LinkedHashMap<>();
        try (CSVParser parser = CSVFormat.DEFAULT.withDelimiter(',').withIgnoreSurroundingSpaces(true)
                .withFirstRecordAsHeader().parse(Files.newBufferedReader(csv, StandardCharsets.UTF_8))) {
            for (CSVRecord row : parser) {
                assertNull(countries.put(row.get("Code"), row.get("Name")), "Duplicate country code in CSV");
            }
        }
        return countries;
    }

    private void assertReferenceData(DataSource dataSource, Map<String, String> expectedCountries) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(expectedCountries, keyedValues(connection, "SELECT code, name FROM Country"));
            assertEquals(Map.of("WASHING_STATION", "Washing station", "DRYING_BED", "Drying bed",
                    "HULLING_STATION", "Hulling station", "STORAGE", "Storage"),
                    keyedValues(connection, "SELECT code, label FROM FacilityType"));
            assertEquals(Map.of("VOLUME_L", "liter", "WEIGHT_KG", "kg", "BAG_60", "Bag (60 kg)"),
                    keyedValues(connection, "SELECT code, label FROM MeasureUnitType"));
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT code, weight FROM MeasureUnitType")) {
                while (result.next()) {
                    BigDecimal weight = result.getBigDecimal(2);
                    switch (result.getString(1)) {
                        case "VOLUME_L" -> assertNull(weight);
                        case "WEIGHT_KG" -> assertEquals(0, BigDecimal.ONE.compareTo(weight));
                        case "BAG_60" -> assertEquals(0, new BigDecimal("60").compareTo(weight));
                        default -> fail("Unexpected measure unit");
                    }
                }
            }
        }
    }

    private Map<String, String> keyedValues(Connection connection, String sql) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                assertNull(values.put(result.getString(1), result.getString(2)), "Duplicate logical key");
            }
        }
        return values;
    }

    private void assertSelectedStructure(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Document' "
                    + "AND COLUMN_NAME='uploadedBy_id' AND IS_NULLABLE='YES'"));
            assertTrue(scalar(connection, "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Document' "
                    + "AND COLUMN_NAME='uploadedBy_id' AND REFERENCED_TABLE_NAME='User'") > 0);
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Document' "
                    + "AND INDEX_NAME='IDX_DOCUMENT_UPLOADED_BY' AND COLUMN_NAME='uploadedBy_id'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='AuthenticationToken' "
                    + "AND INDEX_NAME='IDX_AUTH_TOKEN_EXPIRATION' AND COLUMN_NAME='expiration'"));
            assertTrue(scalar(connection, "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Facility' "
                    + "AND COLUMN_NAME='facilityLocation_id' AND REFERENCED_TABLE_NAME='FacilityLocation'") > 0);
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM (SELECT INDEX_NAME "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                    + "AND TABLE_NAME='Facility' AND NON_UNIQUE=0 AND INDEX_NAME <> 'PRIMARY' "
                    + "GROUP BY INDEX_NAME HAVING COUNT(*)=1 AND MAX(COLUMN_NAME)='facilityLocation_id') indexes_found"));
        }
    }

    private Fixture createFixture(EntityManagerFactory factory) {
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            FacilityType facilityType = em.createQuery("SELECT f FROM FacilityType f WHERE f.code = :code", FacilityType.class)
                    .setParameter("code", "STORAGE").getSingleResult();
            Company company = new Company();
            company.setName("Migration test company");
            FacilityLocation location = new FacilityLocation();
            Facility first = new Facility();
            first.setName("Migration first facility");
            first.setCompany(company);
            first.setFacilityType(facilityType);
            first.setFacilityLocation(location);
            Facility second = new Facility();
            second.setName("Migration second facility");
            second.setCompany(company);
            second.setFacilityType(facilityType);
            second.setFacilityLocation(location);
            em.persist(company);
            em.persist(location);
            em.persist(first);
            em.persist(second);
            Fixture fixture = new Fixture(company.getId(), location.getId(), first.getId(), second.getId());
            em.getTransaction().commit();
            return fixture;
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private void verifyAndUpdateFixture(EntityManagerFactory factory, Fixture fixture) {
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            assertFixture(em, fixture, "Migration first facility");
            em.find(Facility.class, fixture.first()).setName("Migration validated facility");
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private void assertFixture(EntityManagerFactory factory, Fixture fixture) {
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            assertFixture(em, fixture, "Migration validated facility");
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private void assertFixture(EntityManager em, Fixture fixture, String firstName) {
        assertEquals("Migration test company", em.find(Company.class, fixture.company()).getName());
        Facility first = em.find(Facility.class, fixture.first());
        Facility second = em.find(Facility.class, fixture.second());
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(firstName, first.getName());
        assertEquals("Migration second facility", second.getName());
        for (Facility facility : List.of(first, second)) {
            assertEquals(fixture.company(), facility.getCompany().getId());
            assertEquals(fixture.location(), facility.getFacilityLocation().getId());
            assertEquals("STORAGE", facility.getFacilityType().getCode());
        }
    }

    private List<String> schemaSnapshot(DataSource dataSource) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            collectRows(connection, result, "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                    + "COALESCE(COLUMN_DEFAULT, '<NULL>'), EXTRA FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION");
            collectRows(connection, result, "SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                    + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX");
            collectRows(connection, result, "SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, "
                    + "REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL "
                    + "ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION");
        }
        return result;
    }

    private void collectRows(Connection connection, List<String> rows, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= columns; i++) values.add(result.getString(i));
                rows.add(values.toString());
            }
        }
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private record Migration(String type, String script) {
        MigrationVersion version() {
            String name = script.substring(script.lastIndexOf('.') + 1);
            if ("SQL".equals(type)) name = script.substring(0, script.length() - 4);
            return MigrationVersion.fromVersion(name.substring(1, name.indexOf("__")).replace('_', '.'));
        }
    }

    private record History(String version, String type, String script, boolean success) {}
    private record Fixture(Long company, Long location, Long first, Long second) {}
}
