package com.kooritea.fcmfix.util;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ConfigSnapshotTest {
    @org.junit.Test public void diagnosticDeadlineDefaultsOffAndRequiresLong() {
        assertEquals(0, new ConfigSnapshot(java.util.Map.of()).nightDiagnosticsUntil);
        assertEquals(5000, new ConfigSnapshot(java.util.Map.of(DiagnosticSession.CONFIG_UNTIL, 5000L)).nightDiagnosticsUntil);
        try {
            new ConfigSnapshot(java.util.Map.of(DiagnosticSession.CONFIG_UNTIL, "forever"));
            fail("invalid deadline must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void missingOptionsUseSafeDefaults() {
        ConfigSnapshot snapshot = new ConfigSnapshot(Collections.emptyMap());
        assertTrue(snapshot.allowList.isEmpty());
        assertFalse(snapshot.options.get("disableAutoCleanNotification"));
        assertFalse(snapshot.options.get("includeIceBoxDisableApp"));
        assertFalse(snapshot.autoAllowFcm());
        assertTrue(snapshot.excludeList.isEmpty());
    }
    @Test public void autoAllowCoversFcmAppsExceptExcluded() {
        Map<String, Object> values = new HashMap<>();
        values.put("allowList", new HashSet<>(Collections.singleton("manual")));
        values.put("excludeList", new HashSet<>(Collections.singleton("unticked")));
        Set<String> fcm = new HashSet<>(Arrays.asList("chat", "unticked"));
        assertFalse(new ConfigSnapshot(values).allows("chat", fcm));
        values.put("autoAllowFcm", true);
        ConfigSnapshot snapshot = new ConfigSnapshot(values);
        assertTrue(snapshot.allows("manual", fcm));
        assertTrue(snapshot.allows("chat", fcm));
        assertFalse(snapshot.allows("unticked", fcm));
        assertFalse(snapshot.allows("no-fcm", fcm));
        assertFalse(snapshot.allows(null, fcm));
    }
    @Test public void snapshotDoesNotShareMutableState() {
        Set<String> packages = new HashSet<>(Collections.singleton("target"));
        Map<String, Object> values = new HashMap<>();
        values.put("allowList", packages); values.put("includeIceBoxDisableApp", true);
        ConfigSnapshot snapshot = new ConfigSnapshot(values);
        packages.clear(); values.put("includeIceBoxDisableApp", false);
        assertTrue(snapshot.allowList.contains("target"));
        assertTrue(snapshot.options.get("includeIceBoxDisableApp"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.allowList.add("evil"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.options.put("includeIceBoxDisableApp", true));
    }
    @Test public void corruptConfigIsNotPublished() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSnapshot(Collections.singletonMap("allowList", "not a set")));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSnapshot(Collections.singletonMap("includeIceBoxDisableApp", "true")));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSnapshot(Collections.singletonMap("excludeList", "not a set")));
    }
}
