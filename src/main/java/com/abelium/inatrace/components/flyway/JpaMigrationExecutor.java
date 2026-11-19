package com.abelium.inatrace.components.flyway;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.executor.Context;
import org.flywaydb.core.api.executor.MigrationExecutor;
import org.flywaydb.core.internal.jdbc.Results;
import org.springframework.core.env.Environment;

import java.sql.SQLException;
import java.util.List;

/**
 * Adapter for executing migrations implementing JpaMigration.
 */
public class JpaMigrationExecutor implements MigrationExecutor {
    
    /**
     * The JpaMigration to execute.
     */
    private final JpaMigration migration;

    private final EntityManagerFactory entityManagerFactory;
    
    private final Environment environment;
    
    /**
     * Creates a new JpaMigrationExecutor.
     * @param entityManagerFactory Factory
     * @param migration The JpaMigration to execute.
     * @param environment The application environment
     */
    public JpaMigrationExecutor(JpaMigration migration, EntityManagerFactory entityManagerFactory, Environment environment) {
        this.migration = migration;
        this.entityManagerFactory = entityManagerFactory;
        this.environment = environment;
    }

    @Override
    public List<Results> execute(Context context) throws SQLException {
        EntityManager em = entityManagerFactory.createEntityManager();
        
        try {
            em.getTransaction().begin();
            migration.migrate(em, environment);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive())
                em.getTransaction().rollback();
            throw new FlywayException("Migration failed!", e);
        } finally {
            em.close();
        }
        return List.of();
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public boolean shouldExecute() {
        return true;
    }
}
