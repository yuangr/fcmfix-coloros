package com.kooritea.fcmfix.util;

public final class ReportExportResult {
    public static final String COMPLETE_MARKER = "FCMFix report complete";
    public enum Status { COMPLETE, PARTIAL, FAILED }
    private ReportExportResult() { }
    public static Status assess(boolean timedOut, int exitCode, boolean readerFinished,
                                String head, String tail) {
        if (head == null || !head.startsWith("FCMFix report ")) return Status.FAILED;
        if (timedOut || exitCode != 0 || !readerFinished || tail == null
                || !tail.trim().endsWith("\n" + COMPLETE_MARKER)) return Status.PARTIAL;
        return Status.COMPLETE;
    }
}
