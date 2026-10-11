package com.kooritea.fcmfix.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReportExportResultTest {
    private static final String HEAD = "FCMFix report 2026-10-11\n";
    private static final String TAIL = "last section\nFCMFix report complete\n";
    @Test public void requiresCleanExitAndFooter() {
        assertEquals(ReportExportResult.Status.COMPLETE, ReportExportResult.assess(false, 0, true, HEAD, TAIL));
        assertEquals(ReportExportResult.Status.PARTIAL, ReportExportResult.assess(false, 0, true, HEAD, "last section"));
        assertEquals(ReportExportResult.Status.PARTIAL, ReportExportResult.assess(false, 1, true, HEAD, TAIL));
    }
    @Test public void timeoutOrUnfinishedReaderCannotSucceedEvenWithFooter() {
        assertEquals(ReportExportResult.Status.PARTIAL, ReportExportResult.assess(true, 0, true, HEAD, TAIL));
        assertEquals(ReportExportResult.Status.PARTIAL, ReportExportResult.assess(false, 0, false, HEAD, TAIL));
    }
    @Test public void rejectsInvalidHeaderOrNonFinalMarker() {
        assertEquals(ReportExportResult.Status.FAILED, ReportExportResult.assess(false, 0, true, "su: denied", TAIL));
        assertEquals(ReportExportResult.Status.PARTIAL, ReportExportResult.assess(false, 0, true, HEAD, TAIL + "crashed"));
    }
}
