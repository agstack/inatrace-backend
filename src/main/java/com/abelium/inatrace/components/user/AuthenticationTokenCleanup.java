package com.abelium.inatrace.components.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Removes long-expired refresh sessions in bounded batches, outside the login path. */
@Component
public class AuthenticationTokenCleanup {

    private static final int RETENTION_DAYS = 7;
    private static final int BATCH_SIZE = 1000;

    @PersistenceContext
    private EntityManager em;

    @Scheduled(initialDelay = 3_600_000, fixedDelay = 300_000)
    @Transactional
    public void removeExpiredSessions() {
        em.createNativeQuery("DELETE FROM `AuthenticationToken` WHERE `expiration` < :cutoff "
                        + "ORDER BY `expiration` LIMIT " + BATCH_SIZE)
                .setParameter("cutoff", Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS))
                .executeUpdate();
    }
}
