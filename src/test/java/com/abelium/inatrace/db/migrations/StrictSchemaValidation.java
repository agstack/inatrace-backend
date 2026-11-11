package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.INATraceBackendApplication;
import com.abelium.inatrace.components.analytics.AnalyticsEngine;
import com.abelium.inatrace.components.codebook.currencies.CurrencyUpdateScheduler;
import com.abelium.inatrace.components.flyway.JpaMigration;
import com.abelium.inatrace.components.user.AuthenticationTokenCleanup;
import com.abelium.inatrace.configuration.MigrationsConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Path;

/** Test-only boot that excludes the custom migration initializer and scheduled writers. */
final class StrictSchemaValidation {
    static final String USER = "schema_reader";
    static final String PASSWORD = "isolated-validation-only";

    private StrictSchemaValidation() { }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.abelium.inatrace.db.entities")
    @ComponentScan(basePackages = "com.abelium.inatrace", excludeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE, classes = {INATraceBackendApplication.class, JpaMigration.class,
            MigrationsConfiguration.class, AnalyticsEngine.class, CurrencyUpdateScheduler.class,
            AuthenticationTokenCleanup.class}))
    static class ValidationApplication {
        // ProductService needs this bean; excluding its component prevents @EnableScheduling.
        @Bean AnalyticsEngine analyticsEngine() { return new AnalyticsEngine(); }
    }

    static void createReadOnlyUser(MySQLContainer<?> mysql) throws Exception {
        String database = mysql.getDatabaseName();
        if (!database.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Unexpected database name");
        String sql = "CREATE USER '" + USER + "'@'%' IDENTIFIED BY '" + PASSWORD + "'; "
                + "GRANT SELECT, SHOW VIEW ON `" + database + "`.* TO '" + USER + "'@'%';";
        var result = mysql.execInContainer("mysql", "-uroot", "-p" + mysql.getPassword(), "-e", sql);
        if (result.getExitCode() != 0) throw new IllegalStateException("Could not create validation user: " + result.getStderr());
    }

    static ConfigurableApplicationContext start(MySQLContainer<?> mysql, Path temporaryDirectory) {
        return new SpringApplicationBuilder(ValidationApplication.class).run(
                "--spring.config.location=classpath:/application-test.properties", "--spring.profiles.active=test",
                "--server.port=0", "--spring.datasource.url=" + mysql.getJdbcUrl(),
                "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD,
                "--spring.datasource.hikari.minimum-idle=0", "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.properties.hibernate.hbm2ddl.auto=validate",
                "--spring.jpa.properties.jakarta.persistence.schema-generation.database.action=validate",
                "--spring.flyway.enabled=false", "--INATrace.fileStorage.root=" + temporaryDirectory.resolve("storage"),
                "--INATrace.documents.root=" + temporaryDirectory.resolve("documents"));
    }
}
