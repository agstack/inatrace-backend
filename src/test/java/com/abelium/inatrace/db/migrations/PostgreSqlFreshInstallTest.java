package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.INATraceBackendApplication;
import com.abelium.inatrace.support.TestVendor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL-only fresh install: Hibernate creates the schema, Flyway applies the
 * common and PostgreSQL migrations, and a second boot validates the result.
 */
class PostgreSqlFreshInstallTest {

    private static final String LOCATIONS =
            "classpath:com/abelium/inatrace/db/migrations,classpath:/db/migrations/postgresql,classpath:/db/migrations/common";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(300)
    void freshInstallAppliesMigrationsAndValidatesOnRestart() throws Exception {
        Assumptions.assumeFalse(TestVendor.isMySql(), "PostgreSQL-only fresh-install test");

        Path importDirectory = Path.of("import").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(importDirectory.resolve("countries.csv")),
                "The real countries.csv is required for bootstrap");

        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
                .withInitScript("db/postgresql/init.sql")) {
            postgres.start();

            try (ConfigurableApplicationContext context = start(postgres, importDirectory, "update")) {
                DataSource dataSource = context.getBean(DataSource.class);
                assertEquals(97L, scalar(dataSource, "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_type='BASE TABLE'"));
                assertEquals(0L, scalar(dataSource, "SELECT COUNT(*) FROM schema_version WHERE success = false"));
                assertEquals(1L, scalar(dataSource, "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name='User'"));
                assertEquals(249L, scalar(dataSource, "SELECT COUNT(*) FROM Country"));
                assertEquals(1L, scalar(dataSource, "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname='public' AND indexname='idx_document_uploaded_by'"));
                assertEquals(1L, scalar(dataSource, "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname='public' AND indexname='idx_auth_token_expiration'"));
            }

            try (ConfigurableApplicationContext context = start(postgres, importDirectory, "validate")) {
                DataSource dataSource = context.getBean(DataSource.class);
                assertEquals(0L, scalar(dataSource, "SELECT COUNT(*) FROM schema_version WHERE success = false"));
            }
        }
    }

    private ConfigurableApplicationContext start(PostgreSQLContainer postgres, Path importDirectory, String ddlMode) {
        return new SpringApplicationBuilder(INATraceBackendApplication.class).run(
                "--spring.config.location=classpath:/application-test.properties",
                "--spring.profiles.active=test",
                "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.datasource.hikari.minimum-idle=0",
                "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.jpa.hibernate.ddl-auto=" + ddlMode,
                "--spring.jpa.properties.hibernate.dialect=com.abelium.inatrace.configuration.CustomPostgreSQLDialect",
                "--spring.jpa.properties.hibernate.auto_quote_keyword=true",
                "--spring.flyway.locations=" + LOCATIONS,
                "--spring.flyway.fail-on-missing-locations=true",
                "--spring.flyway.out-of-order=true",
                "--INATrace.import.path=" + importDirectory + java.io.File.separator,
                "--INATrace.fileStorage.root=" + temporaryDirectory.resolve("storage"),
                "--INATrace.documents.root=" + temporaryDirectory.resolve("documents"));
    }

    private static long scalar(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
