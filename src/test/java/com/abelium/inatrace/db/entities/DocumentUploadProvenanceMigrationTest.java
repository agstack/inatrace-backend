package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.db.migrations.V2026_10_13_12_00__Add_Document_Uploaded_By;
import com.abelium.inatrace.db.migrations.V2026_10_21_12_00__Deduplicate_Document_Uploader_Foreign_Keys;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that the provenance migration upgrades an existing Document table safely. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentUploadProvenanceMigrationTest extends AbstractMySqlIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void addsNullableUploaderReferenceIndexAndForeignKeyToLegacyDocumentTable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String foreignKey = foreignKeyName(connection);
            if (foreignKey != null) {
                statement.execute("ALTER TABLE `Document` DROP FOREIGN KEY `" + foreignKey + "`");
            }
            statement.execute("ALTER TABLE `Document` DROP COLUMN `uploadedBy_id`");
            assertEquals(0, columnCount(connection));
        }

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            V2026_10_13_12_00__Add_Document_Uploaded_By migration =
                    new V2026_10_13_12_00__Add_Document_Uploaded_By();
            migration.migrate(em, null);
            migration.migrate(em, null); // Re-running after an interrupted deployment must be safe.
            em.createNativeQuery("ALTER TABLE `Document` ADD CONSTRAINT `FK_DOCUMENT_UPLOADED_BY_HIBERNATE` "
                    + "FOREIGN KEY (`uploadedBy_id`) REFERENCES `User` (`id`)").executeUpdate();
            V2026_10_21_12_00__Deduplicate_Document_Uploader_Foreign_Keys cleanup =
                    new V2026_10_21_12_00__Deduplicate_Document_Uploader_Foreign_Keys();
            cleanup.migrate(em, null);
            cleanup.migrate(em, null); // A retry must leave the selected relation intact.
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(1, columnCount(connection));
            assertTrue(indexCount(connection) > 0, "the uploader lookup needs an index");
            assertEquals(List.of("FK_DOCUMENT_UPLOADED_BY"), foreignKeyNames(connection),
                    "exactly one canonical uploader relation must remain");
        }
    }

    private long columnCount(Connection connection) throws Exception {
        return count(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Document' AND COLUMN_NAME = 'uploadedBy_id'");
    }

    private long indexCount(Connection connection) throws Exception {
        return count(connection, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Document' AND COLUMN_NAME = 'uploadedBy_id'");
    }

    private String foreignKeyName(Connection connection) throws Exception {
        List<String> foreignKeys = foreignKeyNames(connection);
        return foreignKeys.isEmpty() ? null : foreignKeys.get(0);
    }

    private List<String> foreignKeyNames(Connection connection) throws Exception {
        java.util.ArrayList<String> foreignKeys = new java.util.ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Document' "
                        + "AND COLUMN_NAME = 'uploadedBy_id' AND REFERENCED_TABLE_NAME = 'User' "
                        + "AND REFERENCED_COLUMN_NAME = 'id' ORDER BY CONSTRAINT_NAME")) {
            while (result.next()) {
                foreignKeys.add(result.getString(1));
            }
        }
        return foreignKeys;
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
