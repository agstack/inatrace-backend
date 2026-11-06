package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class FarmerImportDecisionMigrationTest extends AbstractMySqlIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migrationCreatesAnIdempotentTableAndEnforcesUniqueDecisions() throws Exception {
        String sql = new String(new ClassPathResource("db/migrations/V2026_11_06_12_00__Create_Farmer_Import_Decision.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("FarmerImportDecision", "FarmerImportDecisionMigrationTest")
                .replace("REFERENCES Company (id)", "REFERENCES FarmerImportCompanyMigrationTest (id)")
                .replace("REFERENCES Document (id)", "REFERENCES FarmerImportDocumentMigrationTest (id)")
                .replace("FK_farmer_import_company", "FK_farmer_import_company_test")
                .replace("FK_farmer_import_document", "FK_farmer_import_document_test");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TABLE FarmerImportCompanyMigrationTest (id BIGINT PRIMARY KEY)");
                statement.execute("CREATE TABLE FarmerImportDocumentMigrationTest (id BIGINT PRIMARY KEY)");
                statement.execute("INSERT INTO FarmerImportCompanyMigrationTest VALUES (1)");
                statement.execute("INSERT INTO FarmerImportDocumentMigrationTest VALUES (2), (3)");
                statement.execute(sql);
                statement.execute(sql);
                statement.executeUpdate("INSERT INTO FarmerImportDecisionMigrationTest (companyId, documentId, selectionKey) VALUES (1, 2, 'key')");
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO FarmerImportDecisionMigrationTest (companyId, documentId, selectionKey) VALUES (1, 2, 'key')"));
                statement.executeUpdate("INSERT INTO FarmerImportDecisionMigrationTest (companyId, documentId, selectionKey) VALUES (1, 3, 'key')");
                statement.executeUpdate("DELETE FROM FarmerImportDocumentMigrationTest WHERE id = 2");
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM FarmerImportDecisionMigrationTest")) {
                    rows.next();
                    assertEquals(1, rows.getInt(1), "removing a document removes only its own decisions");
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS FarmerImportDecisionMigrationTest");
                statement.execute("DROP TABLE IF EXISTS FarmerImportDocumentMigrationTest");
                statement.execute("DROP TABLE IF EXISTS FarmerImportCompanyMigrationTest");
            }
        }
    }
}
