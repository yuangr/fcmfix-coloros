package com.kooritea.fcmfix.libxposed;

import com.kooritea.fcmfix.util.HookStatus;
import java.util.function.LongSupplier;

/** Observes only callbacks and explicit result changes; does not infer message delivery. */
final class ObservedHook extends XC_MethodHook {
    private final XC_MethodHook callback;
    private final HookStatus status;
    private final String group;
    private final LongSupplier clock;
    ObservedHook(XC_MethodHook callback, HookStatus status, String group, LongSupplier clock) {
        this.callback = callback; this.status = status; this.group = group; this.clock = clock;
    }
    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        status.recordCallback(group);
        callback.beforeHookedMethod(param);
        if (param.isReturnEarly()) status.recordEffect(group, clock.getAsLong());
    }
    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        boolean alreadyReturned = param.isReturnEarly();
        callback.afterHookedMethod(param);
        if (!alreadyReturned && param.isReturnEarly()) status.recordEffect(group, clock.getAsLong());
    }
}
