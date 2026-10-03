// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/** Stable setting prefixes for independently configured shade headers and the lock screen. */
public final class CarrierPanels {
    public static final String NOTIFICATION = "carrier_notification";
    public static final String CONTROL = "carrier_control";
    public static final String LOCKSCREEN = "carrier_lockscreen";
    public static final String CLASSIC = "carrier_classic";
    public static final String[] GROUPS = {NOTIFICATION, CONTROL, LOCKSCREEN};
    public static final String[] ALL_GROUPS = {NOTIFICATION, CONTROL, LOCKSCREEN, CLASSIC};

    public static boolean isPanel(String group) {
        return NOTIFICATION.equals(group) || CONTROL.equals(group) || LOCKSCREEN.equals(group) || CLASSIC.equals(group);
    }

    public static String key(String group, String suffix) {
        if (!isPanel(group)) throw new IllegalArgumentException("Unknown carrier panel: " + group);
        if (suffix == null || suffix.length() == 0) throw new IllegalArgumentException("Missing setting suffix");
        return group + (suffix.startsWith("_") ? suffix : "_" + suffix);
    }

    /** Shared shade settings apply only to those two shade pages, never to the lock screen. */
    public static String legacyKey(String key) {
        if (key == null) return null;
        for (String group : GROUPS) {
            if (LOCKSCREEN.equals(group)) continue;
            String prefix = group + "_";
            if (key.startsWith(prefix)) {
                String suffix = key.substring(prefix.length());
                return "enabled".equals(suffix) ? null : "carrier_" + suffix;
            }
        }
        return null;
    }

    private CarrierPanels() {}
}
