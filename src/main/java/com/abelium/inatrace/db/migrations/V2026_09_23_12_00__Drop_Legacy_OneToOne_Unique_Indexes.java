package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.components.flyway.JpaMigration;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A change from OneToOne to ManyToOne does not remove the old unique indexes from
 * databases created with the earlier mappings. Only the former join columns are
 * considered here; composite and primary-key indexes are never removed.
 */
public class V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes implements JpaMigration {

    private static final String[][] JOIN_COLUMNS = {
            {"UserCustomer", "userCustomerLocation_id"},
            {"Company", "logo_id"},
            {"Facility", "facilityLocation_id"},
            {"StockOrderActivityProof", "activityProof_id"},
            {"BulkPaymentActivityProof", "activityProof_id"},
            {"Payment", "receiptDocument_id"},
            {"BusinessToCustomerSettings", "productFont_id"},
            {"BusinessToCustomerSettings", "textFont_id"},
            {"BusinessToCustomerSettings", "landingPageImage_id"},
            {"BusinessToCustomerSettings", "landingPageBackgroundImage_id"},
            {"BusinessToCustomerSettings", "headerBackgroundImage_id"},
            {"Product", "process_id"},
            {"Product", "responsibility_id"},
            {"Product", "sustainability_id"},
            {"Product", "journey_id"},
            {"Product", "settings_id"},
            {"Product", "businessToCustomerSettings_id"},
            {"ProductLabelContent", "process_id"},
            {"ProductLabelContent", "responsibility_id"},
            {"ProductLabelContent", "sustainability_id"},
            {"ProductLabelContent", "journey_id"},
            {"ProductLabelContent", "settings_id"},
            {"ProductLabelContent", "businessToCustomerSettings_id"},
            {"Certification", "certificate_id"},
            {"StockOrder", "productionLocation_id"},
            {"StockOrder", "consumerCompanyCustomer_id"},
            {"Transaction", "sourceStockOrder_id"},
            {"Transaction", "inputMeasureUnitType_id"}
    };

    @Override
    public void migrate(EntityManager em, Environment environment) {
        for (String[] join : JOIN_COLUMNS) {
            dropLegacyUniqueIndexes(em, join[0], join[1]);
        }
    }

    private void dropLegacyUniqueIndexes(EntityManager em, String table, String column) {
        Number columnCount = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName " +
                                "AND COLUMN_NAME = :columnName")
                .setParameter("tableName", table)
                .setParameter("columnName", column)
                .getSingleResult();
        if (columnCount.longValue() == 0) {
            return; // A fresh or partially migrated schema may not contain this table yet.
        }

        @SuppressWarnings("unchecked")
        List<String> obsoleteIndexes = em.createNativeQuery(
                        "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName " +
                                "AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY' " +
                                "GROUP BY INDEX_NAME HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = :columnName")
                .setParameter("tableName", table)
                .setParameter("columnName", column)
                .getResultList();
        if (obsoleteIndexes.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> leadingIndexes = em.createNativeQuery(
                        "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName " +
                                "AND COLUMN_NAME = :columnName AND SEQ_IN_INDEX = 1")
                .setParameter("tableName", table)
                .setParameter("columnName", column)
                .getResultList();
        Set<String> supportingIndexes = new HashSet<>(leadingIndexes);
        supportingIndexes.removeAll(obsoleteIndexes);

        if (supportingIndexes.isEmpty()) {
            String replacement = "IDX_SHARED_" + column;
            em.createNativeQuery("ALTER TABLE " + quoted(table) + " ADD INDEX " + quoted(replacement)
                    + " (" + quoted(column) + ")").executeUpdate();
        }

        for (String index : obsoleteIndexes) {
            em.createNativeQuery("ALTER TABLE " + quoted(table) + " DROP INDEX " + quoted(index))
                    .executeUpdate();
        }
    }

    private String quoted(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
