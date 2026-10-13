package com.kooritea.fcmfix.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class Plq110DeepSleepPolicyTest {
    private static final String ACE6 = "PLQ110_16.0.10.500(CN01)";

    @Test public void reviewedAce6UsesNativeRestrictionsWhenGmsAndServiceExist() {
        assertTrue(Plq110DeepSleepPolicy.shouldSelectWhitelist(ACE6, 36, 10112, true));
    }

    @Test public void oneplus15AndUnreviewedOtaKeepOriginalRouting() {
        assertFalse(Plq110DeepSleepPolicy.shouldSelectWhitelist("PLK110_16.0.10.500(CN01)", 36, 10112, true));
        assertFalse(Plq110DeepSleepPolicy.shouldSelectWhitelist("PLQ110_16.0.10.501(CN01)", 36, 10112, true));
        assertFalse(Plq110DeepSleepPolicy.shouldSelectWhitelist(null, 36, 10112, true));
        assertFalse(Plq110DeepSleepPolicy.shouldSelectWhitelist(ACE6, 37, 10112, true));
    }

    @Test public void missingServiceOrUnsupportedGmsUidDoesNotChangeRouting() {
        assertFalse(Plq110DeepSleepPolicy.shouldSelectWhitelist(ACE6, 36, 10112, false));
        for (int uid : new int[]{-1, 0, 1000, 9999, 110112}) {
            assertFalse(Plq110DeepSleepPolicy.shouldSelectWhitelist(ACE6, 36, uid, true));
        }
    }
}
