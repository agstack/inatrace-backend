package com.abelium.inatrace.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.Locale;

/** Observes synchronous test requests without retaining SQL or parameter values. */
public final class DeliveryExportSqlObserver implements StatementInspector {
    private static final ThreadLocal<Sample> CURRENT = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
        Sample sample = CURRENT.get();
        if (sample != null) {
            sample.statements++;
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.startsWith("select ") && normalized.matches(".*\\bfrom stockorder\\s+.*")) {
                if (normalized.startsWith("select count(")) {
                    sample.stockCountSelects++;
                } else {
                    sample.stockSelects++;
                    if (normalized.matches(".*\\blimit\\s+.*")) sample.limitedStockSelects++;
                }
            }
        }
        return sql;
    }

    public static Sample start() {
        if (CURRENT.get() != null) throw new IllegalStateException("SQL sample already active");
        Sample sample = new Sample();
        CURRENT.set(sample);
        return sample;
    }

    public static final class Sample implements AutoCloseable {
        private int statements;
        private int stockSelects;
        private int limitedStockSelects;
        private int stockCountSelects;
        private boolean closed;

        public int statements() { return statements; }
        public int stockSelects() { return stockSelects; }
        public int limitedStockSelects() { return limitedStockSelects; }
        public int stockCountSelects() { return stockCountSelects; }

        @Override
        public void close() {
            if (closed || CURRENT.get() != this) throw new IllegalStateException("Invalid SQL sample owner");
            closed = true;
            CURRENT.remove();
        }
    }
}
