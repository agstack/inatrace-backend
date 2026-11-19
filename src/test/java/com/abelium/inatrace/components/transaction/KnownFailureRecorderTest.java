package com.abelium.inatrace.components.transaction;

import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownFailureRecorderTest {

    @Test
    void acceptsMysqlDeadlockChain() {
        SQLException deadlock = new SQLException(
                "Deadlock found when trying to get lock", "40001", 1213);
        KnownFailureRecorder recorder = new KnownFailureRecorder();
        recorder.record(new LockAcquisitionException("could not execute statement", deadlock));

        assertTrue(recorder.containsOnlyOptimisticOrDeadlockFailures());
    }

    @Test
    void rejectsUnrelatedFailures() {
        KnownFailureRecorder recorder = new KnownFailureRecorder();
        recorder.record(new IllegalStateException("boom"));

        assertFalse(recorder.containsOnlyOptimisticOrDeadlockFailures());
    }
}
