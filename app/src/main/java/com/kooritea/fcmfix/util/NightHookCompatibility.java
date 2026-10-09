package com.kooritea.fcmfix.util;

import java.lang.reflect.Method;

/** Compatibility decisions backed by extracted PLQ110 firmware, not guessed aliases. */
public final class NightHookCompatibility {
    private NightHookCompatibility() { }

    public static boolean isReviewedLegacyFirmware(String display) {
        return "PLQ110_16.0.10.500(CN01)".equals(display);
    }

    public static boolean isSceneRequest(Method method, Class<?> bundleType) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length != 1 || types[0] != bundleType) return false;
        return ("requestSceneActionSync".equals(method.getName())
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class))
                || ("requestSceneAction".equals(method.getName()) && method.getReturnType() == void.class);
    }

    public static boolean shouldBlockAsyncClean(String pkg, String reason, String action,
                                                String subAction, int strategy) {
        return "com.google.android.gms".equals(pkg)
                && "DeepSleepLogicDisNetRestore".equals(reason)
                && "SCENE_COMMON_REQUEST".equals(action)
                && "CommonExternalClean".equals(subAction) && strategy == 2;
    }
}
