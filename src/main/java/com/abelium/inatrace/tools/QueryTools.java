package com.abelium.inatrace.tools;

import com.abelium.inatrace.types.SortDirection;
import org.torpedoquery.jakarta.jpa.Torpedo;

import java.util.function.Supplier;

public class QueryTools {

	private static final ThreadLocal<Boolean> SUPPRESS_ORDER_BY = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/**
	 * Runs a count query without the ORDER BY that TorpedoQuery would otherwise
	 * copy from the list query. PostgreSQL rejects ORDER BY on aggregate queries
	 * unless the column is grouped or aggregated; MySQL tolerates it.
	 */
	public static <T> T withoutOrderBy(Supplier<T> supplier) {
		boolean previous = Boolean.TRUE.equals(SUPPRESS_ORDER_BY.get());
		SUPPRESS_ORDER_BY.set(Boolean.TRUE);
		try {
			return supplier.get();
		} finally {
			SUPPRESS_ORDER_BY.set(previous);
		}
	}

	public static void orderByColumns(Object... orderBy) {
		if (Boolean.TRUE.equals(SUPPRESS_ORDER_BY.get())) {
			return;
		}
		Torpedo.orderBy(orderBy);
	}

	public static <T> void orderBy(SortDirection dir, T column) {
		if (Boolean.TRUE.equals(SUPPRESS_ORDER_BY.get())) {
			return;
		}
		if (dir == SortDirection.ASC) Torpedo.orderBy(Torpedo.asc(column));
		else Torpedo.orderBy(Torpedo.desc(column));
	}
}
