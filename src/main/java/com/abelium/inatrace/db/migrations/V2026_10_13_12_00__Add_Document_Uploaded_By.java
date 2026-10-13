package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.components.flyway.JpaMigration;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;

/**
 * Records the authenticated source of documents uploaded through the common API.
 * Existing documents deliberately remain without an owner so consumers can require
 * an explicit, newly uploaded source rather than guessing ownership retroactively.
 */
public class V2026_10_13_12_00__Add_Document_Uploaded_By implements JpaMigration {

    private static final String TABLE = "Document";
    private static final String COLUMN = "uploadedBy_id";
    private static final String INDEX = "IDX_DOCUMENT_UPLOADED_BY";
    private static final String FOREIGN_KEY = "FK_DOCUMENT_UPLOADED_BY";

    @Override
    public void migrate(EntityManager em, Environment environment) {
        if (!columnExists(em)) {
            em.createNativeQuery("ALTER TABLE `Document` ADD COLUMN `uploadedBy_id` BIGINT NULL").executeUpdate();
        }
        if (!indexExists(em)) {
            em.createNativeQuery("ALTER TABLE `Document` ADD INDEX `IDX_DOCUMENT_UPLOADED_BY` (`uploadedBy_id`)").executeUpdate();
        }
        if (!foreignKeyExists(em)) {
            em.createNativeQuery("ALTER TABLE `Document` ADD CONSTRAINT `FK_DOCUMENT_UPLOADED_BY` "
                    + "FOREIGN KEY (`uploadedBy_id`) REFERENCES `User` (`id`)").executeUpdate();
        }
    }

    private boolean columnExists(EntityManager em) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName AND COLUMN_NAME = :columnName")
                .setParameter("tableName", TABLE)
                .setParameter("columnName", COLUMN)
                .getSingleResult()).longValue() > 0;
    }

    private boolean indexExists(EntityManager em) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName AND INDEX_NAME = :indexName")
                .setParameter("tableName", TABLE)
                .setParameter("indexName", INDEX)
                .getSingleResult()).longValue() > 0;
    }

    private boolean foreignKeyExists(EntityManager em) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName "
                        + "AND CONSTRAINT_NAME = :foreignKey AND CONSTRAINT_TYPE = 'FOREIGN KEY'")
                .setParameter("tableName", TABLE)
                .setParameter("foreignKey", FOREIGN_KEY)
                .getSingleResult()).longValue() > 0;
    }
}
