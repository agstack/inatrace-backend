package com.abelium.inatrace.db.migrations;

import com.abelium.inatrace.components.flyway.JpaMigration;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;

/** Supports bounded cleanup of expired refresh sessions on existing databases. */
public class V2026_10_17_12_00__Add_Authentication_Token_Expiration_Index implements JpaMigration {

    @Override
    public void migrate(EntityManager em, Environment environment) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AuthenticationToken' "
                        + "AND INDEX_NAME = 'IDX_AUTH_TOKEN_EXPIRATION'")
                .getSingleResult();
        if (count.longValue() == 0) {
            em.createNativeQuery("CREATE INDEX `IDX_AUTH_TOKEN_EXPIRATION` "
                    + "ON `AuthenticationToken` (`expiration`)").executeUpdate();
        }
    }
}
