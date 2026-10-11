package com.kooritea.fcmfix.util;

/** Wall time selects a bounded session; only elapsed time controls it after activation. */
public final class DiagnosticSession {
    public static final String CONFIG_UNTIL = "nightDiagnosticsUntil";
    public static final long MAX_DURATION_MS = 12 * 60 * 60 * 1000L;
    private long configuredUntil = Long.MIN_VALUE;
    private long deadline;
    public synchronized boolean configure(long until, long wallNow, long elapsedNow) {
        if (until == configuredUntil) return false;
        configuredUntil = until;
        long remaining = until <= wallNow ? 0 : Math.min(MAX_DURATION_MS, until - wallNow);
        deadline = elapsedNow + remaining;
        return true;
    }
    public synchronized long remaining(long elapsedNow) { return Math.max(0, deadline - elapsedNow); }
}
