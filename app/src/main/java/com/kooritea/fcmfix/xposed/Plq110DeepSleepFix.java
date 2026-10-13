package com.kooritea.fcmfix.xposed;

import android.os.Build;

import com.kooritea.fcmfix.libxposed.XC_MethodHook;
import com.kooritea.fcmfix.libxposed.XposedBridge;
import com.kooritea.fcmfix.libxposed.XposedHelpers;
import com.kooritea.fcmfix.util.NightHookCompatibility;
import com.kooritea.fcmfix.util.Plq110DeepSleepPolicy;

import java.lang.reflect.Method;
import java.util.List;

/** Select Ace 6's native UID whitelist path instead of switching its radios off. */
final class Plq110DeepSleepFix extends XposedModule {
    private static final String GROUP = "PLQ110 deep-sleep whitelist routing";
    private static final String CONTROLLER = "com.oplus.deepsleep.ControllerCenter";
    private static final String MANAGER = "android.nwpower.OAppNetControlManager";
    // Never change routing midway through OEM restriction/restore because VPN reconnects.
    private volatile boolean routingSelected;

    Plq110DeepSleepFix(ClassLoader loader) {
        super(loader);
        OplusHooks.runHook(GROUP, this::install);
    }

    private void install() throws OplusHooks.NotApplicable {
        if (!NightHookCompatibility.isReviewedLegacyFirmware(Build.DISPLAY) || Build.VERSION.SDK_INT != 36) {
            throw new OplusHooks.NotApplicable("Ace 6 PLQ110_16.0.10.500(CN01) only; original routing retained");
        }
        Class<?> controller = XposedHelpers.findClass(CONTROLLER, classLoader);
        Class<?> manager = XposedHelpers.findClass(MANAGER, classLoader);
        Method selector = XposedHelpers.findMethodExact(controller, "isUseNetworkDisableWhiteList");
        Method nativeResult = XposedHelpers.findMethodExact(controller, "networkDisableWhiteListInner",
                List.class, int.class, int.class, boolean.class);
        Method api = XposedHelpers.findMethodExact(manager, "networkDisableWhiteList", List.class, int.class);
        if (selector.getReturnType() != boolean.class || nativeResult.getReturnType() != int.class
                || api.getReturnType() != int.class) throw new NoSuchMethodError("PLQ110 whitelist signatures");
        Class<?> features = XposedHelpers.findClass("com.oplus.content.OplusFeatureConfigManager", classLoader);
        Object featureManager = XposedHelpers.callStaticMethod(features, "getInstance");
        if (!Boolean.TRUE.equals(XposedHelpers.callMethod(featureManager, "hasFeature",
                "oplus.software.radio.nwpower_network_disable_white_list"))) {
            throw new IllegalStateException("Native nighttime whitelist feature disabled");
        }
        // The short selector may have been inlined in the actual radio/state methods.
        Method[] callers = new Method[]{
                XposedHelpers.findMethodExact(controller, "setMobileDataEnable", boolean.class, String.class),
                XposedHelpers.findMethodExact(controller, "setWifiEnable", boolean.class, String.class),
                XposedHelpers.findMethodExact(controller, "disableNetWork", boolean.class),
                XposedHelpers.findMethodExact(controller, "restoreNetWork", boolean.class, boolean.class),
                XposedHelpers.findMethodExact(controller, "handleFreezeStrictWhileSettingsNotOpen", boolean.class),
                XposedHelpers.findMethodExact(XposedHelpers.findClass(CONTROLLER + "$WorkHandler", classLoader),
                        "handleMessage", android.os.Message.class)
        };
        String previous = XposedBridge.swapHookGroup(null);
        try {
            for (Method caller : callers) {
                if (!XposedBridge.deoptimize(caller)) {
                    throw new IllegalStateException("Cannot deoptimize " + OplusHooks.describeMethod(caller));
                }
            }
        } finally {
            // Preparing callers is not an observed nighttime protection action.
            XposedBridge.swapHookGroup(previous);
        }
        // A registered selector is not evidence that netd/kernel accepted the whitelist.
        XposedBridge.hookMethod(nativeResult, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                if (!(param.getResult() instanceof Integer) || (Integer) param.getResult() != 0) {
                    OplusHooks.STATUS.recordRuntimeFailure(GROUP,
                            new IllegalStateException("Native whitelist returned " + param.getResult()
                                    + "; OEM radio-off fallback may execute"));
                    printLog("PLQ110 deep-sleep whitelist failed: result=" + param.getResult(), true);
                } else {
                    printLog("PLQ110 native deep-sleep whitelist accepted: enable=" + param.args[1], true);
                }
            }
        });
        // Install the selector last: observation failure must not leave routing partially enabled.
        XposedBridge.hookMethod(selector, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.hasThrowable() || !Boolean.FALSE.equals(param.getResult()) || context == null) return;
                if (routingSelected) {
                    param.setResult(true);
                    return;
                }
                Object instance = XposedHelpers.callStaticMethod(manager, "getInstance");
                boolean ready = instance != null && XposedHelpers.getObjectField(instance, "mService") != null;
                int[] protectedUids = FcmTransportWhitelist.protectedUids(context);
                if (!Plq110DeepSleepPolicy.shouldSelectWhitelist(Build.DISPLAY, Build.VERSION.SDK_INT,
                        protectedUids[0], ready)) return;
                routingSelected = true;
                param.setResult(true);
                printLog("PLQ110 deep-sleep routing: native UID whitelist selected, GMS/VPN UIDs="
                        + java.util.Arrays.toString(protectedUids), true);
            }
        });
        printLog("PLQ110 native deep-sleep routing hook registered; radio and restore methods retained");
    }
}
