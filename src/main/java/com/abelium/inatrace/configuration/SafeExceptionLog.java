package com.abelium.inatrace.configuration;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Render diagnostic locations without exception messages, SQL, paths or payloads. */
public final class SafeExceptionLog {
    private static final int MAX_THROWABLES = 8;
    private static final int MAX_FRAMES = 24;

    private SafeExceptionLog() { }

    public static void error(Logger logger, String event, Throwable exception) {
        error(logger, event, exception, ApiRequestObservability.requestId());
    }

    /** Use the originating request ID when a callback runs on a different thread. */
    public static void error(Logger logger, String event, Throwable exception, String requestId) {
        logger.error("{} request_id={} diagnostic={}", event, requestId, describe(exception));
    }

    public static void warn(Logger logger, String event, Throwable exception) {
        logger.warn("{} request_id={} diagnostic={}", event, ApiRequestObservability.requestId(), describe(exception));
    }

    static String describe(Throwable exception) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        append(result, exception, seen);
        return result.toString();
    }

    private static void append(StringBuilder output, Throwable exception, Set<Throwable> seen) {
        if (exception == null || seen.size() >= MAX_THROWABLES || !seen.add(exception)) return;
        if (output.length() > 0) output.append("; ");
        output.append("type=").append(exception.getClass().getName());
        StackTraceElement[] frames = exception.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, MAX_FRAMES); i++) {
            StackTraceElement frame = frames[i];
            output.append(" ").append(safeName(frame.getClassName())).append(".")
                    .append(safeName(frame.getMethodName())).append(":").append(frame.getLineNumber());
        }
        append(output, exception.getCause(), seen);
        for (Throwable suppressed : exception.getSuppressed()) append(output, suppressed, seen);
    }

    private static String safeName(String value) {
        if (value == null) return "unknown";
        String safe = value.replaceAll("[^A-Za-z0-9_.$<>]", "_");
        return safe.substring(0, Math.min(safe.length(), 160));
    }
}
