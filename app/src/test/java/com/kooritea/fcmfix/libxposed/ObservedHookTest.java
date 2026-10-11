package com.kooritea.fcmfix.libxposed;
import com.kooritea.fcmfix.util.HookStatus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ObservedHookTest {
    private static XC_MethodHook.MethodHookParam param() {
        XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam();
        p.args = new Object[0]; return p;
    }
    @Test public void shortCircuitIsOneActionAndOriginalDoesNotRun() throws Throwable {
        HookStatus status = new HookStatus(); status.recordActive("a");
        XC_MethodHook callback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult(false); }
        };
        Object result = HookInvocation.invoke(param(), new ObservedHook(callback, status, "a", () -> 100),
                args -> { throw new AssertionError("original must not run"); }, e -> status.recordRuntimeFailure("a", e));
        assertEquals(false, result);
        assertTrue(status.runtime(100).get(0).contains("回调 1 次，保护/适配动作 1 次"));
        assertEquals(0, status.runtimeErrors());
    }
    @Test public void originalFailureIsPreservedWithoutBlamingHook() throws Throwable {
        HookStatus status = new HookStatus(); status.recordActive("a");
        IllegalStateException originalFailure = new IllegalStateException("framework rejected");
        try {
            HookInvocation.invoke(param(), new ObservedHook(new XC_MethodHook() {}, status, "a", () -> 100),
                    args -> { throw originalFailure; }, e -> status.recordRuntimeFailure("a", e));
            fail("expected original failure");
        } catch (IllegalStateException e) { assertSame(originalFailure, e); }
        assertEquals(0, status.runtimeErrors());
        assertTrue(status.runtime(100).get(0).contains("保护分支待验证"));
    }
    @Test public void callbackFailureIsReportedAndOriginalStillRuns() throws Throwable {
        HookStatus status = new HookStatus(); status.recordActive("a");
        XC_MethodHook callback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { throw new IllegalArgumentException("bad hook"); }
        };
        assertEquals(42, HookInvocation.invoke(param(), new ObservedHook(callback, status, "a", () -> 100),
                args -> 42, e -> status.recordRuntimeFailure("a", e)));
        assertEquals(1, status.runtimeErrors());
        assertTrue(status.runtime(100).get(0).contains("bad hook"));
    }
}
