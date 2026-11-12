// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/** Radio decisions from the same Wi-Fi model used by SystemUI, with a default-route fallback. */
public final class RadioState {
    private RadioState() { }

    // Historical policy keys remain readable for existing backups/UI only.
    // Visibility follows the current radio/data state, not these retired knobs.
    public static final String ALWAYS_SHOWN = "label_always_shown";
    public static final String WIFI_MERGED = "label_wifi_merged";
    public static final String BOTH_VISIBLE = "network_both_visible";

    /**
     * nativeWifiVisible is WifiIcon.Visible, not a View's visibility or animation alpha.
     * null means the native model is unavailable. defaultWifi describes only the current
     * default network (including its VPN underlying transport), never all known networks.
     */
    public static boolean wifiInUse(boolean wifiEnabled, Boolean nativeWifiVisible, boolean defaultWifi) {
        if (!wifiEnabled) return false;
        return nativeWifiVisible != null ? nativeWifiVisible : defaultWifi;
    }

    /**
     * The active-data fallback must belong to the current data subscription; callers must
     * clear that source on SIM/radio changes rather than reusing the last displayed label.
     */
    public static String cellularLabel(boolean enabled, boolean wifiInUse, boolean airplaneMode,
                                       String nativeLabel, String activeDataFallback, boolean normalize) {
        return cellularLabel(enabled,wifiInUse,airplaneMode,nativeLabel,activeDataFallback,normalize,null,null);
    }

    public static String cellularLabel(boolean enabled, boolean wifiInUse, boolean airplaneMode,
                                       String nativeLabel, String activeDataFallback, boolean normalize,
                                       Boolean wifiEnabled,Boolean mobileDataEnabled) {
        if (!cellularLabelAllowed(enabled, wifiInUse, airplaneMode, mobileDataEnabled)) return "";
        String result = label(nativeLabel, normalize);
        return result.isEmpty() ? label(activeDataFallback, normalize) : result;
    }

    /** Wi-Fi being enabled is not evidence of active cellular data. Native RAT
     * models may retain voice-registration text after the data switch is OFF. */
    static boolean cellularLabelAllowed(boolean enabled, boolean wifiInUse, boolean airplaneMode, Boolean mobileDataEnabled) {
        return enabled && !wifiInUse && !airplaneMode && !Boolean.FALSE.equals(mobileDataEnabled);
    }

    private static String label(String value, boolean normalize) {
        String normalized = NetworkLabel.normalize(value);
        if (normalized.isEmpty()) return "";
        return normalize ? normalized : value.trim();
    }
}
