package com.kooritea.fcmfix.util;
import org.junit.Test;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;
public class ReportRedactorTest {
    @Test public void keepsTargetAndCorePackagesButMasksOthersAndIpv4() {
        ReportRedactor r = new ReportRedactor(List.of("org.telegram.messenger", "com.tencent.mm", "com.google.android.gms"),
                Set.of("com.tencent.mm", "com.google.android.gms"));
        String text = r.redact("com.tencent.mm com.google.android.gms org.telegram.messenger remote=198.18.0.4:5228");
        assertTrue(text.contains("com.tencent.mm com.google.android.gms"));
        assertFalse(text.contains("org.telegram.messenger"));
        assertFalse(text.contains("198.18.0.4"));
        assertTrue(text.contains(":5228"));
    }
    @Test public void preservesClockTimesAndMasksCompressedIpv6() {
        ReportRedactor r = new ReportRedactor(List.of(), Set.of());
        String text = r.redact("00:32:48 remote=[2001:db8::1]:5228 ::1");
        assertTrue(text.startsWith("00:32:48"));
        assertFalse(text.contains("2001:db8::1"));
        assertFalse(text.endsWith("::1"));
    }
    @Test public void matchesCompletePackageNameNotSubstringAndPrefersLongest() {
        ReportRedactor r = new ReportRedactor(List.of("com.example.a", "com.example.a.extra"), Set.of());
        assertEquals("com.example.abc", r.redact("com.example.abc"));
        assertFalse(r.redact("com.example.a.extra").contains(".extra"));
    }
    @Test public void shortInstalledPackageCannotCorruptKeptCoreAndTargetNames() {
        ReportRedactor r = new ReportRedactor(List.of("android", "com.google.android.gms",
                "io.github.artifical0.fcmfix.coloros.local", "com.example.android.client"),
                Set.of("com.google.android.gms", "io.github.artifical0.fcmfix.coloros.local",
                        "com.example.android.client"));
        String kept = "pkg=com.google.android.gms action=com.google.android.gms.gcm.ACTION_TASK_READY "
                + "io.github.artifical0.fcmfix.coloros.local/.MainActivity com.example.android.client:remote";
        assertEquals(kept, r.redact(kept));
        assertFalse(r.redact("pkg=android").contains("android"));
    }
    @Test public void protectsKeptChildAndRedactsNonTargetChildOfKeptParent() {
        ReportRedactor r = new ReportRedactor(List.of("com.example", "com.example.child",
                "com.target", "com.target.extra"), Set.of("com.example.child", "com.target"));
        String text = r.redact("com.example.child/.Activity com.target com.target.extra/.Activity");
        assertTrue(text.startsWith("com.example.child/.Activity com.target "));
        assertFalse(text.contains("com.target.extra"));
    }
    @Test public void keptNamesAreProtectedEvenWhenMissingFromInstalledSnapshot() {
        ReportRedactor r = new ReportRedactor(List.of("android"), Set.of("com.google.android.gms"));
        assertEquals("com.google.android.gms", r.redact("com.google.android.gms"));
    }
}
