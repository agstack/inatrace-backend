package com.abelium.inatrace.tools;

import com.abelium.inatrace.tools.queries.AliasCondition;
import com.abelium.inatrace.tools.queries.SimpleQueryBuilder;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Queries
{
    /**
     * Whether any of the given entity attributes references the row with this id, for example
     * {@code "Facility.facilityType"}. Removing a row that is still referenced only fails on the
     * foreign key at commit, as a generic error, so delete operations check this first and answer
     * a client error. The attributes are constants in the calling code, never user input.
     */
    public static boolean isReferenced(EntityManager em, Long id, List<String> entityAttributes) {
        for (String entityAttribute : entityAttributes) {
            int dot = entityAttribute.indexOf('.');
            Long references = em.createQuery("SELECT COUNT(e) FROM " + entityAttribute.substring(0, dot)
                            + " e WHERE e." + entityAttribute.substring(dot + 1) + ".id = :id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult();
            if (references > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes an entry that {@link #isReferenced} found unused and flushes at once, so that a
     * reference created meanwhile by a concurrent request fails here, on the foreign key, and is
     * answered with the same client error instead of failing at commit as a generic one. The caller's
     * transaction must roll back on {@link ApiException}.
     */
    public static void removeUnreferenced(EntityManager em, Object entity, String inUseMessage) throws ApiException {
        em.remove(entity);
        try {
            em.flush();
        } catch (PersistenceException e) {
            if (isIntegrityViolation(e)) {
                throw new ApiException(ApiStatus.VALIDATION_ERROR, inUseMessage);
            }
            throw e;
        }
    }

    private static boolean isIntegrityViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException
                    || cause instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    // for BaseEntity (to automatically convert int -> long if needed)
    public static <T> T get(EntityManager em, Class<T> cls, long id) {
        return em.find(cls, id);
    }
    
    public static <T> T get(EntityManager em, Class<T> cls, Object id) {
        if (id == null) return null;
        return em.find(cls, id);
    }
    
    public static <T> List<T> getAll(EntityManager em, Class<T> cls) {
        T alias = Torpedo.from(cls);
        return Torpedo.select(alias).list(em);
    }
    
    public static <T, P> T getUniqueBy(EntityManager em, Class<T> cls, Function<T, P> property, P value) {
        if (value == null) return null;
        T alias = Torpedo.from(cls);
        Torpedo.where(property.apply(alias)).eq(value);
        Optional<T> result = Torpedo.select(alias).get(em);
        return result.orElse(null);
    }

    public static <T, P> T getFirstBy(EntityManager em, Class<T> cls, Function<T, P> property, P value) {
        if (value == null) return null;
        T alias = Torpedo.from(cls);
        Torpedo.where(property.apply(alias)).eq(value);
        List<T> results = Torpedo.select(alias).setMaxResults(1).list(em);
        return results.isEmpty() ? null : results.get(0);
    }

    public static <T, P> List<T> getAllBy(EntityManager em, Class<T> cls, Function<T, P> property, P value) {
        if (value == null) return Collections.emptyList();
        T alias = Torpedo.from(cls);
        Torpedo.where(property.apply(alias)).eq(value);
        return Torpedo.select(alias).list(em);
    }

    public static <T, P> List<T> getAllWherePropIn(EntityManager em, Class<T> cls, Function<T, P> property, Collection<P> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        T alias = Torpedo.from(cls);
        Torpedo.where(property.apply(alias)).in(values);
        return Torpedo.select(alias).list(em);
    }
    
    public static <T, P> int getCountBy(EntityManager em, Class<T> cls, Function<T, P> property, P value) {
        if (value == null) return 0;
        T alias = Torpedo.from(cls);
        Torpedo.where(property.apply(alias)).eq(value);
        Optional<Long> result = Torpedo.select(Torpedo.count(alias)).get(em);
        return result.isPresent() ? result.get().intValue() : 0;
    }
    
    // generic versions
    
    public static OnGoingLogicalCondition condition() {
        return Torpedo.condition();
    }
    
    public static <T> T getUniqueWhere(EntityManager em, Class<T> cls, AliasCondition<T> condition) {
        T alias = Torpedo.from(cls);
        Torpedo.where(condition.evaluate(alias));
        Optional<T> result = Torpedo.select(alias).get(em);
        return result.orElse(null);
    }

    public static <T> T getFirstWhere(EntityManager em, Class<T> cls, AliasCondition<T> condition) {
        T alias = Torpedo.from(cls);
        Torpedo.where(condition.evaluate(alias));
        List<T> results = Torpedo.select(alias).setMaxResults(1).list(em);
        return results.isEmpty() ? null : results.get(0);
    }
    
    public static <T> List<T> getAllWhere(EntityManager em, Class<T> cls, AliasCondition<T> condition) {
        T alias = Torpedo.from(cls);
        Torpedo.where(condition.evaluate(alias));
        return Torpedo.select(alias).list(em);
    }
    
    public static <T> int getCountWhere(EntityManager em, Class<T> cls, AliasCondition<T> condition) {
        T alias = Torpedo.from(cls);
        Torpedo.where(condition.evaluate(alias));
        Optional<Long> result = Torpedo.select(Torpedo.count(alias)).get(em);
        return result.isPresent() ? result.get().intValue() : 0;
    }
    
    // builder
    
    /**
     * Return a {@link SimpleQueryBuilder} for given class.
     * See examples in {@code SimpleQueryTests}.
     * @param em the entity manager to use
     * @param entityClass the entityClass (no collection joins are supported) 
     * @return a {@link SimpleQueryBuilder}
     */
    public static <T> SimpleQueryBuilder<T> from(EntityManager em, Class<T> entityClass) {
        return new SimpleQueryBuilder<>(em, entityClass);
    }
}
