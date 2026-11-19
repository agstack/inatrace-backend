package com.abelium.inatrace.db.migrations;

import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.dialect.PostgreSQLDialect;

/**
 * Helpers for JPA migrations that contain vendor-specific DDL.
 */
final class JpaMigrationSupport {

    private JpaMigrationSupport() {
    }

    static boolean isPostgreSql(EntityManager em) {
        return em.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class)
                .getJdbcServices()
                .getDialect() instanceof PostgreSQLDialect;
    }
}
