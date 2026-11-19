package com.abelium.inatrace.tools.queries;

import jakarta.persistence.EntityManager;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Query;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Builder for simple queries (with one alias).
 * See examples in {@code SimpleQueryTests}.
 * @param <T> the alias type
 */
public class SimpleQueryBuilder<T> implements Selection<T>
{
    @FunctionalInterface
    public interface TwoCombiner<S1, S2, R> {
        R apply(S1 v1, S2 v2);
    }

    @FunctionalInterface
    public interface ThreeCombiner<S1, S2, S3, R> {
        R apply(S1 v1, S2 v2, S3 v3);
    }
    
    private static class Ordering<T> {
        private PropertySelector<T, ?> selector;
        private boolean ascending;
        
        public Ordering(PropertySelector<T, ?> selector, boolean ascending) {
            this.selector = selector;
            this.ascending = ascending;
        }
    }
    
    private EntityManager em;
    private Class<T> entityClass;
    private List<AliasCondition<T>> conditions = new ArrayList<>();
    private List<Ordering<T>> ordering = new ArrayList<>();

    public SimpleQueryBuilder(EntityManager em, Class<T> entityClass) {
        this.em = em;
        this.entityClass = entityClass;
    }

    /**
     * Filter by a Torpedo condition. Methods {@code where} and {@code whereCondition} may be called multiple times and are combined with {@code and}.
     * @param condition a function that maps alias to a Torpedo conditon
     * @return this for chaining
     */
    public SimpleQueryBuilder<T> whereCondition(AliasCondition<T> condition) {
        conditions.add(condition);
        return this;
    }
    
    /**
     * Filter by a simple Torpedo-like expression (see {@link WhereBuilder}). Methods {@code where} and {@code whereCondition} may be called multiple times and are combined with {@code and}.
     * @param property property get method to be used in condition
     * @return this for chaining
     */
    public <S> WhereBuilder<T, S> where(PropertySelector<T, S> property) {
        return new WhereBuilder<>(this, property);
    }
    
    /**
     * Order ascending by property. Methods {@code orderAsc} and {@code orderDesc} may be called multiple times, first invocation is the primary key, then secondary etc.
     * @param property property get method
     * @return this for chaining
     */
    public SimpleQueryBuilder<T> orderAsc(PropertySelector<T, ?> property) {
        ordering.add(new Ordering<>(property, true));
        return this;
    }

    /**
     * Order descending by property. Methods {@code orderAsc} and {@code orderDesc} may be called multiple times, first invocation is the primary key, then secondary etc.
     * @param property property get method
     * @return this for chaining
     */
    public SimpleQueryBuilder<T> orderDesc(PropertySelector<T, ?> property) {
        ordering.add(new Ordering<>(property, false));
        return this;
    }
    
    /**
     * Select whole object from {@code from} clause.
     * You tipically don't need to call {@code select()} with zero arguments, because shortcuts to {@code list()}, {@code first()}, {@code limit()} etc. are provided that implicitly call it.
     * @return a {@link SimpleSelection} object for the alias type
     */
    public Selection<T> select() {
        T alias = buildTorpedoAliasQuery();
        Query<T> query = Torpedo.select(alias);
        return new SimpleSelection<>(em, query);
    }
    
    /**
     * Select a single property or function value.
     * @param property property get method or lambda that returns a torpedo function call
     * @return a {@link SimpleSelection} object for the property type
     */
    public <S> Selection<S> select(PropertySelector<T, S> property) {
        T alias = buildTorpedoAliasQuery();
        Query<S> query = Torpedo.select(property.evaluate(alias));
        return new SimpleSelection<>(em, query);
    }

    /**
     * Select multiple propertes or function values.
     * @param properties property get methods or lambdas that return torpedo function calls
     * @return a {@link SimpleSelection} object for {@code Object[]}
     */
    @SafeVarargs
    public final Selection<Object[]> select(PropertySelector<T, ?>... properties) {
        T alias = buildTorpedoAliasQuery();
        Object[] torpedoSelectors = new Object[properties.length];
        for (int i = 0; i < properties.length; i++) {
            torpedoSelectors[i] = properties[i].evaluate(alias);
        }
        Query<Object[]> query = Torpedo.select(torpedoSelectors);
        return new SimpleSelection<>(em, query);
    }
    
    /**
     * Select two properties and combine them with {@code combiner} function.
     * @param combiner functions that creates combined result (typically a constructor accepting two arguments).
     * @param prop1 property get method or lambda that return a torpedo function call
     * @param prop2 property get method or lambda that return a torpedo function call
     * @return the combined result selection
     */
    @SuppressWarnings("unchecked")
    public <S1, S2, R> Selection<R> select(TwoCombiner<S1, S2, R> combiner, PropertySelector<T, S1> prop1, PropertySelector<T, S2> prop2) {
        return select(prop1, prop2)
                .transform(row -> combiner.apply((S1) row[0], (S2) row[1]));
    }

    /**
     * Select two properties and combine them with {@code combiner} function.
     * @param combiner functions that creates combined result (typically a constructor accepting three arguments).
     * @param prop1 property get method or lambda that return a torpedo function call
     * @param prop2 property get method or lambda that return a torpedo function call
     * @param prop3 property get method or lambda that return a torpedo function call
     * @return the combined result selection
     */
    @SuppressWarnings("unchecked")
    public <S1, S2, S3, R> Selection<R> select(ThreeCombiner<S1, S2, S3, R> combiner, PropertySelector<T, S1> prop1, PropertySelector<T, S2> prop2, PropertySelector<T, S3> prop3) {
        return select(prop1, prop2, prop3)
                .transform(row -> combiner.apply((S1) row[0], (S2) row[1], (S3) row[2]));
    }
    
    /**
     * Select multiple properties and put them in fields/properties of an object, using {@link BindingSelection#bind(PropertySelector, BindingSelection.BindingSetter)} calls.
     * @param objectCreator function that creates a new object, typically {@code SomeClass::new}
     * @return a {@link BindingSelection} object for target type
     */
    public <S> BindingSelection<T, S> selectBinding(Supplier<S> objectCreator) {
        return new BindingSelection<>(this, objectCreator);
    }
    
    // shortcuts to selection methods
    
    public Selection<T> limit(Integer limit) {
        return select().limit(limit);
    }
    
    public Selection<T> offset(Integer offset) {
        return select().offset(offset);
    }

    public List<T> list() {
        return select().list();
    }
    
    public Optional<T> unique() {
        return select().unique();
    }
    
    public Optional<T> first() {
        return select().first();
    }

    public String queryString() {
        return select().queryString();
    }

    /**
     * The query row count. 
     */
    public int count() {
        T alias = buildTorpedoAliasQuery();
        Optional<Long> result = Torpedo.select(Torpedo.count(alias)).get(em);
        return result.isPresent() ? result.get().intValue() : 0;
    }
    
    /**
     * The query row count as long. 
     */
    public long longCount() {
        T alias = buildTorpedoAliasQuery();
        Optional<Long> result = Torpedo.select(Torpedo.count(alias)).get(em);
        return result.isPresent() ? result.get().longValue() : 0L;
    }

    // implementation
    
    private T buildTorpedoAliasQuery() {
        T alias = Torpedo.from(entityClass);
        if (!conditions.isEmpty()) {
            OnGoingLogicalCondition torpedoCond = Torpedo.condition();
            for (AliasCondition<T> cond : conditions) {
                torpedoCond = torpedoCond.and(cond.evaluate(alias));
            }
            Torpedo.where(torpedoCond);
        }
        if (!ordering.isEmpty()) {
            Object[] torpedoOrder = new Object[ordering.size()];
            for (int i = 0; i < ordering.size(); i++) {
                Ordering<T> order = ordering.get(i);
                if (order.ascending) {
                    torpedoOrder[i] = Torpedo.asc(order.selector.evaluate(alias));
                } else {
                    torpedoOrder[i] = Torpedo.desc(order.selector.evaluate(alias));
                }
            }
            com.abelium.inatrace.tools.QueryTools.orderByColumns(torpedoOrder);
        }
        return alias;
    }
}
