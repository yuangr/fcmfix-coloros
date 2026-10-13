package com.kooritea.fcmfix.util;

/** The radio/UID routing decision reviewed against Ace 6's extracted Battery.apk. */
public final class Plq110DeepSleepPolicy {
    private Plq110DeepSleepPolicy() { }

    public static boolean shouldSelectWhitelist(String display, int sdk, int gmsUid,
                                                 boolean nativeServiceReady) {
        return NightHookCompatibility.isReviewedLegacyFirmware(display) && sdk == 36
                && gmsUid >= 10000 && gmsUid < 100000 && nativeServiceReady;
    }
}
