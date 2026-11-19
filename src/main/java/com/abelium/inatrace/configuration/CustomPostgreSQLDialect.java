package com.abelium.inatrace.configuration;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.PostgreSQLDialect;

/**
 * PostgreSQL dialect that maps the JPQL {@code GROUP_CONCAT} function used by
 * {@code GroupStockOrderService} to PostgreSQL's {@code string_agg}. The
 * argument is cast to text because {@code string_agg} does not accept bigint.
 */
public class CustomPostgreSQLDialect extends PostgreSQLDialect {

    @Override
    public void initializeFunctionRegistry(FunctionContributions functionContributions) {

        super.initializeFunctionRegistry(functionContributions);

        var stringType = functionContributions.getTypeConfiguration().getBasicTypeForJavaType(String.class);
        var integerType = functionContributions.getTypeConfiguration().getBasicTypeForJavaType(Integer.class);

        functionContributions.getFunctionRegistry()
                .registerPattern("GROUP_CONCAT", "string_agg(?1::text, ',')", stringType);

        functionContributions.getFunctionRegistry()
                .registerPattern("SEARCH_NORMALIZE", "lower(unaccent(?1))", stringType);

        // MySQL date-part functions used by the dashboard aggregations.
        functionContributions.getFunctionRegistry()
                .registerPattern("YEAR", "extract(year from ?1)", integerType);
        functionContributions.getFunctionRegistry()
                .registerPattern("MONTH", "extract(month from ?1)", integerType);
        functionContributions.getFunctionRegistry()
                .registerPattern("WEEK", "extract(week from ?1)", integerType);
    }
}
