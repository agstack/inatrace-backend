package com.abelium.inatrace.components.flyway;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.core.env.Environment;

public class JpaMigrationStrategy implements FlywayMigrationStrategy {
    private EntityManagerFactory entityManagerFactory;
    
    private Environment environment;

    public JpaMigrationStrategy(EntityManagerFactory entityManagerFactory, Environment environment) {
        this.entityManagerFactory = entityManagerFactory;
        this.environment = environment;
    }

    @Override
    public void migrate(Flyway flyway) {
        JpaMigrationResolver jpaMigrationResolver = 
                new JpaMigrationResolver(flyway.getConfiguration(), entityManagerFactory, environment);
        
        ClassicConfiguration configuration = new ClassicConfiguration(flyway.getConfiguration());
        configuration.setResolvers(jpaMigrationResolver);
        Flyway.configure().configuration(configuration).load().migrate();
    }

}
