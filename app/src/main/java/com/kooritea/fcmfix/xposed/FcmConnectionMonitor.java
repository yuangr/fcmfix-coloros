package com.kooritea.fcmfix.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;

import com.kooritea.fcmfix.util.InetDiag;
import com.kooritea.fcmfix.util.LogRing;
import com.kooritea.fcmfix.util.SelfCheck;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Watches GMS's FCM socket (port 5228-5230) from system_server through sock_diag, and keeps a
 * short timeline of what usually explains a drop: screen, deep Doze, network changes, pushes.
 * Sampling uses an uptime Handler, so it never wakes the device; a reconnect made while the CPU
 * slept still shows up as a changed connection at the next sample.
 */
final class FcmConnectionMonitor {
    private static final long SAMPLE_INTERVAL_MS = 60_000;
    private static final Runnable SOON_SAMPLE = FcmConnectionMonitor::sample;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final LogRing EVENTS = new LogRing(120);

    private static Handler handler;
    private static int gmsUid = -1;
    private static int sequence;

    private static int state = SelfCheck.UNKNOWN;
    private static String connectionKey;
    private static String remote;
    private static long since;
    private static LocalDateTime lastSeen;
    private static long lastSeenMillis;
    private static int drops;
    private static long startedAt;
    private static long lastSample = -1;
    private static long maxSampleGap;

    private FcmConnectionMonitor() {
    }

    static synchronized void start(Context context) {
        if (handler != null || context == null) return;
        try {
            gmsUid = context.getPackageManager().getPackageUid("com.google.android.gms", 0);
        } catch (Throwable e) {
            XposedModule.printLog("FCM monitor: GMS not installed");
            return;
        }
        HandlerThread thread = new HandlerThread("FCMFix-monitor");
        thread.start();
        handler = new Handler(thread.getLooper());
        startedAt = android.os.SystemClock.elapsedRealtime();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) event("亮屏");
                else if (Intent.ACTION_SCREEN_OFF.equals(action)) event("熄屏");
                else if (PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                    PowerManager power = receiverContext.getSystemService(PowerManager.class);
                    event(power != null && power.isDeviceIdleMode() ? "进入深度 Doze" : "退出深度 Doze");
                }
                sampleSoon();
            }
        }, filter, null, handler);

        try {
            ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
            connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                private String last;

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    String name = networkName(capabilities) + " #" + network
                            + (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? " 已验证" : " 未验证")
                            + (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ? " 需认证" : "");
                    if (!name.equals(last)) {
                        last = name;
                        event("网络：" + name);
                        sampleSoon();
                    }
                }

                @Override
                public void onLost(Network network) {
                    last = null;
                    event("网络断开");
                    sampleSoon();
                }
            }, handler);
        } catch (Throwable e) {
            XposedModule.printLog("FCM monitor: network callback unavailable: " + e);
        }
        handler.post(FcmConnectionMonitor::sampleAndReschedule);
        XposedModule.printLog("FCM connection monitor started");
    }

    private static String networkName(NetworkCapabilities capabilities) {
        String transport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi‑Fi"
                : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "移动数据"
                : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "以太网" : "其他";
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN" : transport;
    }

    static void event(String text) {
        EVENTS.add(LocalDateTime.now().format(TIME) + " " + text);
        XposedModule.printLog("event: " + text);
    }

    /** Newest last. */
    static ArrayList<String> events() {
        return EVENTS.snapshot();
    }

    private static void sampleSoon() {
        Handler current = handler;
        if (current != null) {
            current.removeCallbacks(SOON_SAMPLE);
            current.postDelayed(SOON_SAMPLE, 3000);
        }
    }

    private static void sampleAndReschedule() {
        sample();
        handler.postDelayed(FcmConnectionMonitor::sampleAndReschedule, SAMPLE_INTERVAL_MS);
    }

    /** Takes a fresh sample; also called from the self-check thread. */
    static synchronized void sample() {
        if (gmsUid < 0) return;
        long sampleTime = android.os.SystemClock.elapsedRealtime();
        if (lastSample >= 0) {
            long gap = Math.max(0, sampleTime - lastSample);
            maxSampleGap = Math.max(maxSampleGap, gap);
            if (gap > 120_000) event("采样空档 " + SelfCheck.duration(gap) + "（期间连接状态未知）");
        }
        lastSample = sampleTime;
        List<InetDiag.Socket> sockets;
        try {
            sockets = dumpTcp();
        } catch (Throwable e) {
            XposedModule.logOnce("FCM monitor: sock_diag failed: " + e);
            if (state != SelfCheck.UNKNOWN) event("连接采样失败；状态未知");
            state = SelfCheck.UNKNOWN;
            return;
        }
        InetDiag.Socket established = null;
        InetDiag.Socket connecting = null;
        for (InetDiag.Socket socket : sockets) {
            if (socket.uid != gmsUid || socket.remotePort < 5228 || socket.remotePort > 5230) continue;
            // While GMS reconnects two sockets may coexist; keep following the current one.
            if (socket.state == InetDiag.TCP_ESTABLISHED
                    && (established == null || socket.key().equals(connectionKey))) established = socket;
            else if (socket.state == InetDiag.TCP_SYN_SENT && connecting == null) connecting = socket;
        }
        long now = sampleTime;
        String key = established == null ? null : established.key();
        if (connectionKey != null && !connectionKey.equals(key)) {
            drops++;
            // The drop happened somewhere between the last sample that saw it and now.
            event("GMS 候选连接中断或替换（" + lastSeen.format(TIME) + " 时仍看到TCP，首末观察间隔 "
                    + SelfCheck.duration(lastSeenMillis - since) + "）");
        }
        if (key != null && !key.equals(connectionKey)) {
            since = now;
            remote = established.remote.getHostAddress() + ":" + established.remotePort;
            event("GMS 候选 TCP 已建立 " + remote);
        }
        connectionKey = key;
        if (established != null) {
            state = SelfCheck.FCM_CONNECTED;
            lastSeen = LocalDateTime.now();
            lastSeenMillis = now;
        } else {
            state = connecting != null ? SelfCheck.FCM_CONNECTING : SelfCheck.FCM_NONE;
            remote = connecting == null ? null
                    : connecting.remote.getHostAddress() + ":" + connecting.remotePort;
        }
    }

    static synchronized int state() {
        return state;
    }

    static synchronized String remote() {
        return remote;
    }

    /** Monotonic first-seen time; 0 when not connected. */
    static synchronized long since() {
        return state == SelfCheck.FCM_CONNECTED ? since : 0;
    }

    static synchronized int drops() {
        return drops;
    }

    static synchronized long startedAt() {
        return startedAt;
    }

    static synchronized long maxSampleGap() { return maxSampleGap; }

    private static List<InetDiag.Socket> dumpTcp() throws Exception {
        List<InetDiag.Socket> sockets = new ArrayList<>();
        int states = (1 << InetDiag.TCP_ESTABLISHED) | (1 << InetDiag.TCP_SYN_SENT);
        for (int family : new int[]{OsConstants.AF_INET, OsConstants.AF_INET6}) {
            FileDescriptor fd = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM | OsConstants.SOCK_CLOEXEC,
                    InetDiag.NETLINK_INET_DIAG);
            try {
                Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(2000));
                byte[] request = InetDiag.request(family, states, ++sequence, ByteOrder.nativeOrder());
                // An unconnected netlink socket sends to the kernel (port id 0).
                Os.write(fd, request, 0, request.length);
                byte[] buffer = new byte[32 * 1024];
                boolean done = false;
                while (!done) {
                    int read = Os.read(fd, buffer, 0, buffer.length);
                    if (read <= 0) break;
                    done = InetDiag.parse(ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.nativeOrder()), sockets);
                }
            } finally {
                Os.close(fd);
            }
        }
        return sockets;
    }
}
