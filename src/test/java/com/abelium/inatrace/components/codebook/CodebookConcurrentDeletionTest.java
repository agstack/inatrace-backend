package com.abelium.inatrace.components.codebook;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.codebook.SemiProductTranslation;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilitySemiProduct;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * A reference created by a concurrent request between the in-use check of a codebook delete and the
 * delete itself still gets the in-use answer (400), not a generic error, and the entry is kept with its
 * translations (the delete rolls back).
 *
 * Not transactional on purpose: the two sides need transactions of their own. Another thread inserts
 * a reference and holds it uncommitted, so the delete's check does not see it and the delete then
 * waits on the row lock; the reference is committed only once the delete is shown to be waiting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodebookConcurrentDeletionTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private TransactionTemplate tx;
    private Long adminId;
    private Long semiProductId;
    private Long facilityId;
    private Cookie session;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            User admin = new User();
            admin.setEmail("codebook-concurrent-deletion@admin.test");
            admin.setName("Codebook");
            admin.setSurname("Concurrent");
            admin.setPassword("not-used-in-this-test");
            admin.setRole(UserRole.SYSTEM_ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            em.persist(admin);

            SemiProduct semiProduct = new SemiProduct();
            semiProduct.setName("Concurrent deletion");
            semiProduct.setDescription("Concurrent deletion");
            semiProduct.setSKU(false);
            semiProduct.setBuyable(true);
            semiProduct.setSKUEndCustomer(false);
            // A translation is removed in cascade before the semi-product itself: it has to come back too.
            SemiProductTranslation translation = new SemiProductTranslation();
            translation.setLanguage(Language.EN);
            translation.setName("Concurrent deletion");
            translation.setSemiProduct(semiProduct);
            semiProduct.getSemiProductTranslations().add(translation);
            em.persist(semiProduct);

            Facility facility = new Facility();
            em.persist(facility);

            adminId = admin.getId();
            semiProductId = semiProduct.getId();
            facilityId = facility.getId();
            session = new Cookie(accessCookieName, tokenService.createAccessToken(admin));
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM FacilitySemiProduct f WHERE f.semiProduct.id = :id").setParameter("id", semiProductId).executeUpdate();
            em.createQuery("DELETE FROM Facility f WHERE f.id = :id").setParameter("id", facilityId).executeUpdate();
            em.createQuery("DELETE FROM SemiProductTranslation t WHERE t.semiProduct.id = :id").setParameter("id", semiProductId).executeUpdate();
            em.createQuery("DELETE FROM SemiProduct s WHERE s.id = :id").setParameter("id", semiProductId).executeUpdate();
            em.createQuery("DELETE FROM User u WHERE u.id = :id").setParameter("id", adminId).executeUpdate();
        });
    }

    @Test
    void referenceCreatedDuringTheDeleteStillGetsTheInUseAnswer() throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // A concurrent request references the semi-product and has not committed yet.
            Future<?> reference = threads.submit(() -> tx.executeWithoutResult(status -> {
                em.persist(new FacilitySemiProduct(em.find(Facility.class, facilityId), em.find(SemiProduct.class, semiProductId)));
                em.flush();
                inserted.countDown();
                try {
                    assertTrue(release.await(60, TimeUnit.SECONDS), "the test never released the reference");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(inserted.await(30, TimeUnit.SECONDS), "the reference was not inserted");

            Future<MvcResult> deletion = threads.submit(() ->
                    mockMvc.perform(delete("/api/chain/semi-product/" + semiProductId).cookie(session)).andReturn());

            // The in-use check cannot see the uncommitted reference, so the delete goes ahead and waits
            // on the lock that reference holds (with the check alone it would answer at once).
            assertThrows(TimeoutException.class, () -> deletion.get(2, TimeUnit.SECONDS),
                    "the delete should be waiting for the uncommitted reference");

            release.countDown();
            reference.get(30, TimeUnit.SECONDS);
            MvcResult result = deletion.get(60, TimeUnit.SECONDS);

            assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            assertTrue(result.getResponse().getContentAsString().contains("Cannot delete a semi-product that is in use"),
                    result.getResponse().getContentAsString());
            tx.executeWithoutResult(status -> {
                assertNotNull(em.find(SemiProduct.class, semiProductId), "the semi-product should be kept");
                assertEquals(1L, em.createQuery("SELECT COUNT(t) FROM SemiProductTranslation t WHERE t.semiProduct.id = :id", Long.class)
                        .setParameter("id", semiProductId).getSingleResult(), "its translation should be kept too (the delete rolls back)");
            });
        } finally {
            release.countDown();
            threads.shutdownNow();
        }
    }
}
