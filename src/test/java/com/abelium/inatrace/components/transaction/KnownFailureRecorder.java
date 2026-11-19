package com.abelium.inatrace.components.transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the exceptions handled by the API so the concurrency test can tell a
 * known optimistic-lock/deadlock failure from an unexpected 500.
 */
class KnownFailureRecorder {

    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    void record(Throwable failure) {
        failures.add(failure);
    }

    void clear() {
        failures.clear();
    }

    boolean containsOnlyOptimisticOrDeadlockFailures() {
        return !failures.isEmpty() && failures.stream().allMatch(this::isOptimisticOrDeadlockFailure);
    }

    boolean coversOnlyOptimisticOrDeadlockFailures(long expectedFailures) {
        return expectedFailures == 0
                || (failures.size() >= expectedFailures && containsOnlyOptimisticOrDeadlockFailures());
    }

    private boolean isOptimisticOrDeadlockFailure(Throwable failure) {
        for (Throwable candidate = failure; candidate != null && candidate != candidate.getCause();
             candidate = candidate.getCause()) {
            if (candidate instanceof SQLException sql && isSerializationFailure(sql)) {
                return true;
            }

            String name = candidate.getClass().getName();
            if (name.contains("OptimisticLock") || name.contains("StaleObject")
                    || name.contains("Deadlock") || name.contains("CannotAcquireLock")
                    || name.contains("LockAcquisition") || name.contains("PessimisticLock")) {
                return true;
            }

            String message = candidate.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("deadlock")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSerializationFailure(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("40001".equals(current.getSQLState()) || current.getErrorCode() == 1213) {
                return true;
            }
        }
        return false;
    }
}
