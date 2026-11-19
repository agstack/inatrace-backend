package com.abelium.inatrace.configuration;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.StandardBasicTypes;

public class CustomMySQLDialect extends MySQLDialect {

    @Override
    public void initializeFunctionRegistry(FunctionContributions functionContributions) {

        super.initializeFunctionRegistry(functionContributions);

        functionContributions.getFunctionRegistry()
                .register("GROUP_CONCAT", new StandardSQLFunction("group_concat", StandardBasicTypes.STRING));

        var stringType = functionContributions.getTypeConfiguration().getBasicTypeForJavaType(String.class);
        functionContributions.getFunctionRegistry()
                .registerPattern("SEARCH_NORMALIZE", "lower(?1)", stringType);
    }

}
