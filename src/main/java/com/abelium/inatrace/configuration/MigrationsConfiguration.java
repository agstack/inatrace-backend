package com.abelium.inatrace.configuration;

import com.abelium.inatrace.components.flyway.DelayedFlywayMigrationInitializer;
import com.abelium.inatrace.components.flyway.JpaMigrationStrategy;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class MigrationsConfiguration {
    
    // Override default Flyway initializer to do nothing
    @Bean
    FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(flyway, f -> { /* empty */ });
    }
    
    // Create a second Flyway initializer to run after EntityManagerFactory was initialized
    @Bean
    DelayedFlywayMigrationInitializer delayedFlywayInitializer(Flyway flyway, EntityManagerFactory entityManagerFactory, Environment environment) {
        return new DelayedFlywayMigrationInitializer(flyway, new JpaMigrationStrategy(entityManagerFactory, environment));
    }
}
