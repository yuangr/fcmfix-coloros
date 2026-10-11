package com.kooritea.fcmfix.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-process result of each hook group, in install order, for the in-app status query. */
public final class HookStatus {
    /** Ordered broadcast from the module app; each hooked process adds its result extras. */
    public static final String QUERY_ACTION_SUFFIX = ".query.status";
    public static final String ACTIVE_SUFFIX = ".active";
    public static final String FAILED_SUFFIX = ".failed";
    public static final String SKIPPED_SUFFIX = ".skipped";
    public static final String RUNTIME_SUFFIX = ".runtime";
    public static final String VERSION_SUFFIX = ".versionCode";
    public static final String RUNTIME_ERRORS_SUFFIX = ".runtimeErrors";
    /** system_server only: whether Battery declares IgnoreGmsUserSet (gates the firewall drop). */
    public static final String KEY_IGNORE_GMS_USER_SET = "ignoreGmsUserSet";
    /** Query extra: also return each process's recent module log lines (report export only). */
    public static final String EXTRA_LOGS = "logs";
    public static final String LOGS_SUFFIX = ".logs";

    private final Map<String, String> failures = new LinkedHashMap<>();
    private final Map<String, String> skipped = new LinkedHashMap<>();
    private final Map<String, RuntimeState> runtime = new LinkedHashMap<>();
    private static final class RuntimeState {
        long callbacks, effects, errors, lastEffect;
        String lastError, unavailable;
    }
    private RuntimeState state(String name) {
        if (name == null || !failures.containsKey(name)) return null;
        return runtime.computeIfAbsent(name, key -> new RuntimeState());
    }
    public synchronized void recordCallback(String name) {
        RuntimeState s = state(name);
        if (s != null) s.callbacks++;
    }
    public synchronized void recordEffect(String name, long now) {
        RuntimeState s = state(name);
        if (s != null) { s.effects++; s.lastEffect = now; s.unavailable = null; }
    }
    public synchronized void recordRuntimeFailure(String name, Throwable error) {
        RuntimeState s = state(name);
        if (s != null) {
            s.errors++;
            String detail = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
            s.lastError = detail.substring(0, Math.min(240, detail.length()));
        }
    }
    public synchronized void recordUnavailable(String name, String reason) {
        RuntimeState s = state(name);
        if (s != null) s.unavailable = reason;
    }
    public synchronized int runtimeErrors() {
        int count = 0;
        for (RuntimeState s : runtime.values()) if (s.errors > 0 || s.unavailable != null) count++;
        return count;
    }
    public synchronized ArrayList<String> runtime(long now) {
        ArrayList<String> result = new ArrayList<>();
        for (String name : failures.keySet()) {
            RuntimeState s = runtime.get(name);
            String detail = s == null ? "回调未观察到，保护分支待验证"
                    : "回调 " + s.callbacks + " 次，保护/适配动作 " + s.effects + " 次"
                    + (s.effects == 0 ? "（保护分支待验证）"
                    : "，最后观察于 " + Math.max(0, now - s.lastEffect) / 1000 + " 秒前")
                    + (s.errors == 0 ? "" : "，累计运行异常 " + s.errors + " 次：" + s.lastError)
                    + (s.unavailable == null ? "" : "，暂不可执行：" + s.unavailable);
            result.add(name + ": " + detail);
        }
        return result;
    }

    public synchronized void recordActive(String name) {
        skipped.remove(name);
        failures.put(name, null);
    }

    public synchronized void recordFailed(String name, String reason) {
        skipped.remove(name);
        failures.put(name, reason == null ? "unknown" : reason);
    }

    public synchronized ArrayList<String> active() {
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : failures.entrySet()) {
            if (entry.getValue() == null) result.add(entry.getKey());
        }
        return result;
    }

    public synchronized void recordSkipped(String name, String reason) {
        failures.remove(name);
        skipped.put(name, reason);
    }

    public synchronized ArrayList<String> skipped() {
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : skipped.entrySet()) {
            result.add(entry.getKey() + ": " + entry.getValue());
        }
        return result;
    }

    /** "name: reason" for every group that did not install. */
    public synchronized ArrayList<String> failed() {
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : failures.entrySet()) {
            if (entry.getValue() != null) result.add(entry.getKey() + ": " + entry.getValue());
        }
        return result;
    }
}
