package com.kooritea.fcmfix.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticSessionTest {
    @Test public void expiresByElapsedTimeAndReloadCannotExtend() {
        DiagnosticSession session = new DiagnosticSession();
        assertTrue(session.configure(5000, 1000, 100));
        assertEquals(3000, session.remaining(1100));
        assertFalse(session.configure(5000, 0, 1100));
        assertEquals(0, session.remaining(4100));
    }
    @Test public void capsDurationAndSupportsExplicitStop() {
        DiagnosticSession session = new DiagnosticSession();
        session.configure(Long.MAX_VALUE, 0, 10);
        assertEquals(DiagnosticSession.MAX_DURATION_MS, session.remaining(10));
        session.configure(0, 100, 20);
        assertEquals(0, session.remaining(20));
    }
    @Test public void rebootUsesOriginalWallDeadline() {
        DiagnosticSession session = new DiagnosticSession();
        session.configure(5000, 4500, 0);
        assertEquals(500, session.remaining(0));
        session.configure(5001, 6000, 10);
        assertEquals(0, session.remaining(10));
    }
}
