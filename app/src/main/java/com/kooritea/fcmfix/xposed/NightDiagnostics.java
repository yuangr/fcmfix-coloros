package com.kooritea.fcmfix.xposed;

import android.os.SystemClock;
import com.kooritea.fcmfix.libxposed.XposedBridge;
import com.kooritea.fcmfix.util.BoundedJournal;
import com.kooritea.fcmfix.util.DiagnosticSession;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit opt-in, no wakelock or wakeup alarm, disk IO on one bounded worker. */
final class NightDiagnostics {
    private static final DiagnosticSession SESSION = new DiagnosticSession();
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> new Thread(runnable, "FCMFix-night-journal"));
    private static final AtomicLong DROPPED = new AtomicLong();
    private static volatile String sessionId = "";
    private static volatile String writeError;
    private static volatile boolean ready;
    private static BoundedJournal journal; // writer only
    static { WRITER.allowCoreThreadTimeOut(true); }
    private NightDiagnostics() { }
    static void configure(long until) {
        long elapsed = SystemClock.elapsedRealtime();
        if (!SESSION.configure(until, System.currentTimeMillis(), elapsed)) return;
        if (SESSION.remaining(elapsed) == 0) {
            XposedBridge.observeLogs(null);
            return;
        }
        sessionId = UUID.randomUUID().toString();
        writeError = null;
        ready = false;
        record("诊断会话开始；最多12小时；无唤醒采样；只记录模块事件，不采集消息正文");
        XposedBridge.observeLogs(NightDiagnostics::record);
    }
    private static void record(String message) {
        long elapsed = SystemClock.elapsedRealtime();
        if (SESSION.remaining(elapsed) == 0) { XposedBridge.observeLogs(null); return; }
        String line = "session=" + sessionId + " wall=" + System.currentTimeMillis()
                + " elapsed=" + elapsed + " " + message;
        try {
            WRITER.execute(() -> {
                try {
                    if (journal == null) journal = new BoundedJournal(new File(
                            android.os.Environment.getDataDirectory(), "system/fcmfix-coloros"), 256 * 1024);
                    journal.append(line);
                    ready = true;
                    writeError = null;
                } catch (Exception e) { writeError = e.getClass().getSimpleName() + ": " + e.getMessage(); }
            });
        } catch (RejectedExecutionException e) { DROPPED.incrementAndGet(); }
    }
    static boolean hasError() { return writeError != null || DROPPED.get() > 0; }
    static String status() {
        long remaining = SESSION.remaining(SystemClock.elapsedRealtime());
        return (remaining > 0 ? "记录中，剩余约 " + (remaining + 59_999) / 60_000 + " 分钟"
                : "未开启或已到期；可在主界面菜单开始12小时诊断")
                + (remaining == 0 ? "" : ready ? "；私有日志已写入" : "；等待写入")
                + "；最多保留两个256KB文件；休眠期间可能有采样空档"
                + (writeError == null ? "" : "；写入失败：" + writeError)
                + (DROPPED.get() == 0 ? "" : "；队列已丢弃 " + DROPPED.get() + " 条事件");
    }
}
