package com.kooritea.fcmfix.util;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LogRingTest {
    @Test public void hugeLinesCannotExceedBinderExportCharacterBudget() {
        LogRing ring = new LogRing(400); ring.add("old"); ring.add("x".repeat(100000) + "latest");
        java.util.List<String> report = ring.snapshot(64);
        assertTrue(report.stream().mapToInt(s -> s.length() + 1).sum() <= 64);
        assertTrue(report.get(report.size() - 1).endsWith("latest"));
        assertTrue(report.get(0).contains("截断"));
        assertEquals(2, ring.snapshot().size());
    }
    @Test public void boundedExportPreservesShortLinesAndOrder() {
        LogRing ring = new LogRing(10); ring.add("first"); ring.add("last");
        assertEquals(Arrays.asList("first", "last"), ring.snapshot(1000));
        assertThrows(IllegalArgumentException.class, () -> ring.snapshot(1));
    }

    @Test public void keepsNewestLinesInOrder() {
        LogRing ring = new LogRing(3);
        ring.add("a");
        ring.add("b");
        assertEquals(Arrays.asList("a", "b"), ring.snapshot());
        ring.add("c");
        ring.add("d");
        ring.add("e");
        assertEquals(Arrays.asList("…（更早的 2 条已丢弃）", "c", "d", "e"), ring.snapshot());
    }
    @Test public void snapshotIsACopy() {
        LogRing ring = new LogRing(2);
        ring.add("a");
        ring.snapshot().clear();
        assertEquals(1, ring.snapshot().size());
    }
}
