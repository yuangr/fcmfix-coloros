package com.kooritea.fcmfix.util;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class RemoteConfigSubscriptionTest {
    private static final class Cache {
        final Map<String, Object> values = new HashMap<>();
        SharedPreferences.OnSharedPreferenceChangeListener callback;
        int registrations, removals;
        boolean rejectRegistration;
        final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "registerOnSharedPreferenceChangeListener":
                            if (rejectRegistration) throw new IllegalStateException("unavailable");
                            registrations++; callback = (SharedPreferences.OnSharedPreferenceChangeListener) args[0];
                            return null;
                        case "unregisterOnSharedPreferenceChangeListener": removals++; callback = null; return null;
                        case "getAll": return new HashMap<>(values);
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        void update(long until) {
            values.put(DiagnosticSession.CONFIG_UNTIL, until);
            if (callback != null) callback.onSharedPreferenceChanged(preferences, DiagnosticSession.CONFIG_UNTIL);
        }
    }

    @Test public void lateCacheUpdateReloadsDeadlineAfterEarlyBroadcastRead() {
        Cache cache = new Cache();
        AtomicLong loaded = new AtomicLong();
        RemoteConfigSubscription subscription = new RemoteConfigSubscription(() -> loaded.set(
                new ConfigSnapshot(cache.preferences.getAll()).nightDiagnosticsUntil));
        subscription.bind(cache.preferences);
        loaded.set(new ConfigSnapshot(cache.preferences.getAll()).nightDiagnosticsUntil);
        assertEquals(0, loaded.get()); // broadcast ran before the framework cache changed
        cache.update(5000L);
        assertEquals(5000L, loaded.get());
        cache.update(0L);
        assertEquals(0L, loaded.get()); // stopping also reaches the running framework
    }
    @Test public void repeatedReloadDoesNotMultiplySubscriptions() {
        Cache cache = new Cache(); AtomicInteger reloads = new AtomicInteger();
        RemoteConfigSubscription subscription = new RemoteConfigSubscription(reloads::incrementAndGet);
        subscription.bind(cache.preferences); subscription.bind(cache.preferences);
        cache.update(2000L);
        assertEquals(1, cache.registrations); assertEquals(1, reloads.get());
    }
    @Test public void replacementIgnoresAlreadyQueuedOldCallback() {
        Cache old = new Cache(), replacement = new Cache(); AtomicInteger reloads = new AtomicInteger();
        RemoteConfigSubscription subscription = new RemoteConfigSubscription(reloads::incrementAndGet);
        subscription.bind(old.preferences);
        SharedPreferences.OnSharedPreferenceChangeListener queued = old.callback;
        subscription.bind(replacement.preferences);
        queued.onSharedPreferenceChanged(old.preferences, DiagnosticSession.CONFIG_UNTIL);
        assertEquals(0, reloads.get()); assertEquals(1, old.removals);
        replacement.update(1000L); assertEquals(1, reloads.get());
    }
    @Test public void registrationFailureLeavesPreviousSubscriptionUsable() {
        Cache old = new Cache(), replacement = new Cache(); AtomicInteger reloads = new AtomicInteger();
        RemoteConfigSubscription subscription = new RemoteConfigSubscription(reloads::incrementAndGet);
        subscription.bind(old.preferences); replacement.rejectRegistration = true;
        assertThrows(IllegalStateException.class, () -> subscription.bind(replacement.preferences));
        old.update(1000L); assertEquals(1, reloads.get()); assertEquals(0, old.removals);
    }
}
