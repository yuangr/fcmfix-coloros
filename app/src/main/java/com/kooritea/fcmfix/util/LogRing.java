package com.kooritea.fcmfix.util;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * The module's own recent log lines. Busy ColorOS builds keep only minutes of logcat, so boot
 * hook results and night-time events are gone by the time a report is exported.
 */
public final class LogRing {
    private final int capacity;
    private final ArrayDeque<String> lines;
    private long dropped;

    public LogRing(int capacity) {
        this.capacity = capacity;
        this.lines = new ArrayDeque<>(capacity);
    }

    public synchronized void add(String line) {
        if (lines.size() == capacity) {
            lines.removeFirst();
            dropped++;
        }
        lines.addLast(line);
    }

    /** Bound Binder report size even if individual hook errors contain very long strings. */
    public synchronized ArrayList<String> snapshot(int maxChars) {
        if (maxChars < 64) throw new IllegalArgumentException("export budget");
        ArrayList<String> all = snapshot(), result = new ArrayList<>();
        int remaining = maxChars - 48;
        boolean truncated = false;
        for (int i = all.size() - 1; i >= 0; i--) {
            String line = all.get(i);
            if (line.length() + 1 > remaining) {
                if (remaining > 1) result.add(0, line.substring(line.length() - remaining + 1));
                truncated = true;
                break;
            }
            result.add(0, line);
            remaining -= line.length() + 1;
        }
        if (truncated) result.add(0, "…（报告仅保留日志尾部，已截断）");
        return result;
    }

    /** Oldest first; starts with a marker line when earlier lines were dropped. */
    public synchronized ArrayList<String> snapshot() {
        ArrayList<String> result = new ArrayList<>(lines.size() + 1);
        if (dropped > 0) result.add("…（更早的 " + dropped + " 条已丢弃）");
        result.addAll(lines);
        return result;
    }
}
