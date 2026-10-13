package com.kooritea.fcmfix.xposed;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import com.kooritea.fcmfix.libxposed.XposedHelpers;
import com.kooritea.fcmfix.util.NightNetworkWhitelist;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Preserve GMS and the user's VPN transport, without exempting VPN client apps. */
final class FcmTransportWhitelist {
    private FcmTransportWhitelist() { }

    static int[] protectedUids(Context context) throws PackageManager.NameNotFoundException {
        if (android.os.Build.VERSION.SDK_INT < 30) {
            throw new IllegalStateException("VPN management requires API 30+");
        }
        int user = Process.myUid() / 100000;
        if (user != 0) throw new IllegalStateException("PLQ110 routing supports the owner user only");
        Set<Integer> uids = new LinkedHashSet<>();
        uids.add(context.getPackageManager().getPackageUid("com.google.android.gms", 0));
        // Keep the configured always-on VPN even while it is reconnecting. No package is hardcoded.
        String alwaysOn = Settings.Secure.getString(context.getContentResolver(), "always_on_vpn_app");
        addVpnUid(context, uids, alwaysOn);
        Object vpn = context.getSystemService(Context.VPN_MANAGEMENT_SERVICE);
        if (vpn == null) throw new IllegalStateException("VPN state unavailable");
        Object config = XposedHelpers.callMethod(vpn, "getVpnConfig", user);
        if (config != null && !Boolean.TRUE.equals(XposedHelpers.getObjectField(config, "legacy"))) {
            Object pkg = XposedHelpers.getObjectField(config, "user");
            if (!(pkg instanceof String) || ((String) pkg).isEmpty()) {
                throw new IllegalStateException("VPN owner package unavailable");
            }
            addVpnUid(context, uids, (String) pkg);
        }
        int[] result = new int[uids.size()];
        int i = 0;
        for (int uid : uids) result[i++] = uid;
        return result;
    }

    private static void addVpnUid(Context context, Set<Integer> uids, String pkg)
            throws PackageManager.NameNotFoundException {
        if (pkg == null || pkg.isEmpty()) return;
        int uid = context.getPackageManager().getPackageUid(pkg, 0);
        if (uid < 10000 || uid >= 100000) {
            throw new IllegalStateException("Unsupported VPN owner UID " + uid);
        }
        uids.add(uid);
    }

    static List<Object> append(Context context, List<?> original, String group) {
        try {
            return NightNetworkWhitelist.withAppUids(original, protectedUids(context));
        } catch (Throwable error) {
            // Keep the existing GMS augmentation if a VPN package/state read fails.
            OplusHooks.STATUS.recordRuntimeFailure(group, error);
            XposedModule.printLog("PLQ110 VPN whitelist unavailable: " + error, true);
            return null;
        }
    }
}
