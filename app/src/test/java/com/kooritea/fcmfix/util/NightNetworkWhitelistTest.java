package com.kooritea.fcmfix.util;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class NightNetworkWhitelistTest {
    @Test public void appendsUidWithoutTouchingTheOriginal() {
        List<String> original = new ArrayList<>(List.of("1000", "10050:com.heytap.mcs"));
        assertEquals(List.of("1000", "10050:com.heytap.mcs", "10123"),
                NightNetworkWhitelist.withUid(original, 10123));
        assertEquals(2, original.size());
    }

    @Test public void listedUidIsLeftAlone() {
        assertNull(NightNetworkWhitelist.withUid(List.of("10123"), 10123));
        assertNull(NightNetworkWhitelist.withUid(List.of("10123:com.google.android.gms"), 10123));
    }

    @Test public void prefixOfAnotherUidDoesNotCount() {
        assertEquals(List.of("101234", "10123"), NightNetworkWhitelist.withUid(List.of("101234"), 10123));
    }

    @Test public void nullEntriesAreTolerated() {
        assertEquals(Arrays.asList(null, "10123"), NightNetworkWhitelist.withUid(Arrays.asList((String) null), 10123));
    }

    @Test public void protectsGmsAndVpnWithoutChangingOemPidEntriesOrOtherApps() {
        List<String> original = List.of("1000:23231", "10153", "10112");
        List<Object> result = NightNetworkWhitelist.withAppUids(original, 10112, 10397, 10397);
        assertEquals(List.of("1000:23231", "10153", "10112", "10397"), result);
        assertEquals(List.of("1000:23231", "10153", "10112"), original);
        assertFalse(result.contains("10379"));
        assertNull(NightNetworkWhitelist.withAppUids(result, 10112, 10397));
    }

    @Test public void invalidAndOtherUserTransportUidsAreNeverWhitelisted() {
        assertNull(NightNetworkWhitelist.withAppUids(List.of("1000:23231"), -1, 0, 1000, 110112));
        assertEquals(List.of("1000:23231", "10112"),
                NightNetworkWhitelist.withAppUids(List.of("1000:23231"), -1, 10112, 110397));
    }
}
