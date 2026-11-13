package com.abelium.inatrace.support;

/** Selects the database engine used by the integration suite. */
public final class TestVendor {

    private TestVendor() {
    }

    public static boolean isMySql() {
        String vendor = System.getenv().getOrDefault("INATRACE_TEST_DB", "mysql");
        return !(vendor.equalsIgnoreCase("postgresql") || vendor.equalsIgnoreCase("postgres"));
    }
}
