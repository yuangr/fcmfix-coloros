package com.kooritea.fcmfix.util;

import android.content.SharedPreferences;

/** Bind on the single config worker; keep the callback alive for the hooked process. */
public final class RemoteConfigSubscription {
    private volatile SharedPreferences current;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    public RemoteConfigSubscription(Runnable reload) {
        listener = (preferences, key) -> {
            if (preferences == current) reload.run();
        };
    }

    public void bind(SharedPreferences preferences) {
        if (preferences == current) return;
        // Register before reading getAll: an update racing the read requests another worker pass.
        preferences.registerOnSharedPreferenceChangeListener(listener);
        SharedPreferences previous = current;
        current = preferences;
        if (previous != null) previous.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
