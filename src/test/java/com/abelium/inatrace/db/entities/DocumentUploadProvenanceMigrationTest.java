package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.db.migrations.V2026_10_13_12_00__Add_Document_Uploaded_By;
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
            assertTrue(foreignKeyName(connection) != null, "uploader provenance must reference a real user");
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
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Document' "
                        + "AND COLUMN_NAME = 'uploadedBy_id' AND REFERENCED_TABLE_NAME = 'User' LIMIT 1")) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
