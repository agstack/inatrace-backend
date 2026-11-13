package com.abelium.inatrace.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

/**
 * One database instance shared by compatible integration-test contexts in one
 * Maven JVM. The engine is selected by the {@code INATRACE_TEST_DB} environment
 * variable ({@code mysql} by default, {@code postgresql} to run the suite on
 * PostgreSQL).
 *
 * <p>The container is deliberately managed outside JUnit's {@code @Container}
 * lifecycle. A JUnit-managed static container is stopped after each test class,
 * which would create a new datasource and prevent Spring's context cache from
 * being reused by the next class.
 */
public abstract class AbstractDatabaseIntegrationTest {

    static final String VENDOR = System.getenv().getOrDefault("INATRACE_TEST_DB", "mysql");
    static final boolean POSTGRESQL = VENDOR.equalsIgnoreCase("postgresql") || VENDOR.equalsIgnoreCase("postgres");

    private static final MySQLContainer MYSQL = POSTGRESQL ? null : new MySQLContainer("mysql:8.4.11")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"));

    private static final PostgreSQLContainer POSTGRES = POSTGRESQL ? new PostgreSQLContainer("postgres:18")
            .withInitScript("db/postgresql/init.sql") : null;

    static {
        if (POSTGRESQL) {
            POSTGRES.start();
        } else {
            MYSQL.start();
        }
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (POSTGRESQL) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.jpa.properties.hibernate.dialect",
                    () -> "com.abelium.inatrace.configuration.CustomPostgreSQLDialect");
            registry.add("spring.jpa.properties.hibernate.auto_quote_keyword", () -> "true");
        } else {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
            registry.add("spring.jpa.properties.hibernate.dialect",
                    () -> "com.abelium.inatrace.configuration.CustomMySQLDialect");
            registry.add("spring.jpa.properties.hibernate.auto_quote_keyword", () -> "false");
        }
    }
}
