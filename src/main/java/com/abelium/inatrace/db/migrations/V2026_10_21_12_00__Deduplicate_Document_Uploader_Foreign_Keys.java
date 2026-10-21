package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.components.flyway.JpaMigration;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Hibernate can create the uploader relation during schema update before the
 * earlier named migration runs. That migration then adds its named relation,
 * leaving two equivalent foreign keys on existing databases. Keep the named
 * constraint where present and remove only additional single-column relations.
 */
public class V2026_10_21_12_00__Deduplicate_Document_Uploader_Foreign_Keys implements JpaMigration {

    private static final String TABLE = "Document";
    private static final String COLUMN = "uploadedBy_id";
    private static final String REFERENCED_TABLE = "User";
    private static final String REFERENCED_COLUMN = "id";
    private static final String PREFERRED_FOREIGN_KEY = "FK_DOCUMENT_UPLOADED_BY";

    @Override
    public void migrate(EntityManager em, Environment environment) {
        List<String> foreignKeys = uploaderForeignKeys(em);
        if (foreignKeys.size() < 2) {
            return;
        }

        String retained = foreignKeys.contains(PREFERRED_FOREIGN_KEY)
                ? PREFERRED_FOREIGN_KEY : foreignKeys.get(0);
        for (String foreignKey : foreignKeys) {
            if (!foreignKey.equals(retained)) {
                em.createNativeQuery("ALTER TABLE `Document` DROP FOREIGN KEY " + quoted(foreignKey)).executeUpdate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> uploaderForeignKeys(EntityManager em) {
        return em.createNativeQuery("SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName "
                        + "AND COLUMN_NAME = :columnName AND REFERENCED_TABLE_NAME = :referencedTable "
                        + "AND REFERENCED_COLUMN_NAME = :referencedColumn "
                        + "GROUP BY CONSTRAINT_NAME HAVING COUNT(*) = 1 ORDER BY CONSTRAINT_NAME")
                .setParameter("tableName", TABLE)
                .setParameter("columnName", COLUMN)
                .setParameter("referencedTable", REFERENCED_TABLE)
                .setParameter("referencedColumn", REFERENCED_COLUMN)
                .getResultList();
    }

    private String quoted(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
