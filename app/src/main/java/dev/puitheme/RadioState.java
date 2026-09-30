package dev.puitheme;

/** Radio decisions from the same Wi-Fi model used by SystemUI, with a default-route fallback. */
public final class RadioState {
    private RadioState() { }

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
        if (!enabled || wifiInUse || airplaneMode) return "";
        String result = label(nativeLabel, normalize);
        return result.isEmpty() ? label(activeDataFallback, normalize) : result;
    }

    private static String label(String value, boolean normalize) {
        String normalized = NetworkLabel.normalize(value);
        if (normalized.isEmpty()) return "";
        return normalize ? normalized : value.trim();
    }
}
