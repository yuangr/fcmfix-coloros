package com.kooritea.fcmfix.util;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HookStatusTest {
    @Test public void groupsAreReportedInInstallOrder() {
        HookStatus status = new HookStatus();
        status.recordActive("b");
        status.recordFailed("a", "NoSuchMethodError: x");
        status.recordActive("c");
        assertEquals(List.of("b", "c"), status.active());
        assertEquals(List.of("a: NoSuchMethodError: x"), status.failed());
    }

    @Test public void laterResultReplacesEarlierOne() {
        HookStatus status = new HookStatus();
        status.recordFailed("a", "first");
        status.recordActive("a");
        assertEquals(List.of("a"), status.active());
        assertTrue(status.failed().isEmpty());
    }

    @Test public void callbacksAndActionsDoNotCertifyFinalDelivery() {
        HookStatus status = new HookStatus();
        status.recordActive("a");
        status.recordCallback("a");
        assertTrue(status.runtime(100).get(0).contains("保护分支待验证"));
        status.recordEffect("a", 100);
        assertTrue(status.runtime(5100).get(0).contains("5 秒前"));
        assertEquals(0, status.runtimeErrors());
    }
    @Test public void runtimeFailureSurvivesLaterSuccessfulCallsAndUnavailableClearsOnAction() {
        HookStatus status = new HookStatus();
        status.recordActive("a");
        status.recordRuntimeFailure("a", new IllegalArgumentException("bad signature"));
        status.recordEffect("a", 100);
        assertEquals(1, status.runtimeErrors());
        assertTrue(status.runtime(100).get(0).contains("bad signature"));
        status.recordActive("b");
        status.recordUnavailable("b", "instance missing");
        assertEquals(2, status.runtimeErrors());
        status.recordEffect("b", 100);
        assertEquals(1, status.runtimeErrors());
    }
    @Test public void unsolicitedNamesCannotGrowRuntimeState() {
        HookStatus status = new HookStatus();
        for (int i = 0; i < 1000; i++) status.recordRuntimeFailure("unknown" + i, null);
        assertTrue(status.runtime(0).isEmpty());
        assertEquals(0, status.runtimeErrors());
    }

    @Test public void missingReasonIsStillAFailure() {
        HookStatus status = new HookStatus();
        status.recordFailed("a", null);
        assertEquals(List.of("a: unknown"), status.failed());
    }
}
