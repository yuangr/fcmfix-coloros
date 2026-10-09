package com.kooritea.fcmfix.util;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
public class NightHookCompatibilityTest {
    static class BundleStub { }
    static class Good {
        public boolean requestSceneActionSync(BundleStub b) { return true; }
        public void requestSceneAction(BundleStub b) { }
        public void other(BundleStub b) { }
    }
    static class Bad {
        public boolean requestSceneAction(BundleStub b) { return true; }
        public void requestSceneActionSync(BundleStub b) { }
        public void requestSceneAction(String b) { }
    }
    @Test public void onlyVerifiedSignaturesMatch() {
        for (Method m : Good.class.getDeclaredMethods())
            assertEquals(!m.getName().equals("other"), NightHookCompatibility.isSceneRequest(m, BundleStub.class));
        for (Method m : Bad.class.getDeclaredMethods())
            assertFalse(NightHookCompatibility.isSceneRequest(m, BundleStub.class));
    }
    @Test public void onlyGmsDeepSleepForceStopIsBlocked() {
        assertTrue(block("com.google.android.gms", "DeepSleepLogicDisNetRestore", "SCENE_COMMON_REQUEST", "CommonExternalClean", 2));
        assertFalse(block("com.tencent.mm", "DeepSleepLogicDisNetRestore", "SCENE_COMMON_REQUEST", "CommonExternalClean", 2));
        assertFalse(block("com.google.android.gms", "user", "SCENE_COMMON_REQUEST", "CommonExternalClean", 2));
        assertFalse(block("com.google.android.gms", "DeepSleepLogicDisNetRestore", "SCENE_SPECIAL_REQUEST", "CommonExternalClean", 2));
        assertFalse(block("com.google.android.gms", "DeepSleepLogicDisNetRestore", "SCENE_COMMON_REQUEST", "other", 2));
        assertFalse(block("com.google.android.gms", "DeepSleepLogicDisNetRestore", "SCENE_COMMON_REQUEST", "CommonExternalClean", 1));
        assertFalse(block(null, null, null, null, -1));
    }
    private boolean block(String p, String r, String a, String s, int strategy) {
        return NightHookCompatibility.shouldBlockAsyncClean(p,r,a,s,strategy);
    }
    @Test public void unknownFirmwareDoesNotHideMismatch() {
        assertTrue(NightHookCompatibility.isReviewedLegacyFirmware("PLQ110_16.0.10.500(CN01)"));
        assertFalse(NightHookCompatibility.isReviewedLegacyFirmware("PLQ110_16.0.10.501(CN01)"));
        assertFalse(NightHookCompatibility.isReviewedLegacyFirmware(null));
    }
    @Test public void skippedIsNeverCountedAsActiveAndCanBeReplaced() {
        HookStatus s = new HookStatus();
        s.recordActive("x"); s.recordSkipped("x", "not present");
        assertTrue(s.active().isEmpty()); assertTrue(s.failed().isEmpty());
        assertEquals(java.util.List.of("x: not present"), s.skipped());
        s.recordFailed("x", "error"); assertTrue(s.skipped().isEmpty());
        s.recordSkipped("x", "absent"); s.recordActive("x");
        assertTrue(s.skipped().isEmpty()); assertEquals(java.util.List.of("x"), s.active());
    }
}
