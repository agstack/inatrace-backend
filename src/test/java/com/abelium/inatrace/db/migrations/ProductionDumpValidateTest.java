package com.abelium.inatrace.db.migrations;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in validation against a local, authorized and sanitized SQL dump. */
@EnabledIfEnvironmentVariable(named = "INATRACE_PRODUCTION_DUMP", matches = ".+")
class ProductionDumpValidateTest {
    @TempDir Path temporaryDirectory;

    @Test
    void sanitizedDumpValidatesWithoutSchemaWrites() throws Exception {
        Path dump = Path.of(System.getenv("INATRACE_PRODUCTION_DUMP")).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(dump) && Files.isReadable(dump), "The SQL dump must be a readable local file");
        String image = System.getenv().getOrDefault("INATRACE_VALIDATION_MYSQL_IMAGE", "mysql:8.4.11");
        validateDump(dump, image);
    }

    void validateDump(Path dump, String image) throws Exception {
        validateDump(dump, image, false);
    }

    void validateDump(Path dump, String image, boolean checkMissingColumn) throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(image)
                .withUrlParam("allowPublicKeyRetrieval", "true").withUrlParam("useSSL", "false")
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))) {
            mysql.start();
            mysql.copyFileToContainer(MountableFile.forHostPath(dump), "/tmp/validation-input.sql");
            var restore = mysql.execInContainer("sh", "-c", "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" \"$MYSQL_DATABASE\" < /tmp/validation-input.sql");
            assertEquals(0, restore.getExitCode(), "Dump import failed: " + restore.getStderr());
            StrictSchemaValidation.createReadOnlyUser(mysql);
            byte[] before;
            try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                before = SchemaSnapshot.capture(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            }
            try (ConfigurableApplicationContext context = StrictSchemaValidation.start(mysql, temporaryDirectory)) {
                assertEquals("validate", context.getBean(EntityManagerFactory.class).getProperties().get("hibernate.hbm2ddl.auto"));
                assertFalse(context.containsBean("delayedFlywayInitializer"));
                assertFalse(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
                assertTrue(SchemaSnapshot.differences(before, SchemaSnapshot.capture(context.getBean(DataSource.class))).isEmpty(),
                        "Strict validation must not change the imported schema");
            }
            if (checkMissingColumn) {
                try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                     var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE Country DROP COLUMN name");
                }
                Exception failure = assertThrows(Exception.class, () -> {
                    try (ConfigurableApplicationContext ignored = StrictSchemaValidation.start(mysql, temporaryDirectory)) { }
                }, "Hibernate validation must reject a missing mapped column");
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                assertTrue(cause.getMessage().contains("missing column [name]"),
                        "Unexpected validation failure: " + cause);
            }
        }
    }
}
