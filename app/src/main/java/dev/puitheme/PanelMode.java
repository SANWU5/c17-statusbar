// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

/** The OS17 panel repository reads these exact System settings as string booleans. */
public final class PanelMode {
    public static final String METHOD = "panel_mode";
    public static final String CLASSIC = "classic", SEPARATE = "separate", UNKNOWN = "unknown";
    public static final String SETTING = "separate_noti_and_qs_enable";
    public static final String DEFAULT_SETTING = "separate_noti_and_qs_enable_default";
    public static final String CLOCK = "classic_clock";
    private static volatile String current = UNKNOWN;
    private static volatile String source = UNKNOWN;

    public interface Reader { String read(String key); }

    /** No guessed integer aliases: unreadable or unrecognised settings retain the native choice. */
    public static String parse(String value) {
        return "true".equals(value) ? SEPARATE : "false".equals(value) ? CLASSIC : UNKNOWN;
    }

    public static Bundle query(Context context) {
        return query(key -> context == null ? null : Settings.System.getString(context.getContentResolver(), key));
    }

    static Bundle query(Reader reader) {
        String value = null, selected = UNKNOWN, origin = UNKNOWN;
        try {
            value = reader.read(SETTING);
            if (value != null) { selected = parse(value); origin = "system"; }
            else {
                value = reader.read(DEFAULT_SETTING);
                if (value != null) { selected = parse(value); origin = "system_default"; }
            }
        } catch (RuntimeException unreadable) { value = null; }
        current = selected; source = UNKNOWN.equals(selected) ? UNKNOWN : origin;
        Bundle result = snapshot();
        if (value != null) result.putString("value", value);
        return result;
    }

    /** Only the concrete native repository/ex implementation reports, never an absent Lazy. */
    public static boolean report(boolean separate) {
        String mode = separate ? SEPARATE : CLASSIC;
        boolean changed = !mode.equals(current);
        current = mode; source = "systemui";
        return changed;
    }

    public static String current() { return current; }
    public static boolean classic() { return CLASSIC.equals(current); }
    public static boolean isClassic() { return classic(); }
    public static String freezeReason(String key) {
        String unavailable = ShadeWallpaperSettings.unavailableReason(key);
        if (!unavailable.isEmpty()) return unavailable;
        if (!classic() || key == null) return "";
        if (key.startsWith("notification_big_clock_") || key.equals(NotificationGroupStack.MASTER)
                || key.equals(StatusBarShadeIconSettings.MASTER))
            return "经典模式下暂停，切回分离模式后按已保存配置恢复";
        return "";
    }
    public static Bundle snapshot() {
        Bundle result = new Bundle();
        result.putString("mode", current); result.putString("source", source);
        return result;
    }

    /** Gate a delivered copy. Saved preferences and the successful startup snapshot are untouched. */
    public static Bundle runtimeSettings(Bundle saved) { return runtimeSettings(saved, current); }
    static Bundle runtimeSettings(Bundle saved, String mode) {
        Bundle result = saved == null ? new Bundle() : new Bundle(saved);
        if (!ShadeWallpaperSettings.available())
            for (String key : ShadeWallpaperSettings.BOOLEANS.keySet()) result.putBoolean(key, false);
        if (!CLASSIC.equals(mode)) return result;
        for (String key : new String[]{NotificationBigClockSettings.MASTER,
                NotificationBigClockSettings.LANDSCAPE_MASTER, NotificationBigClockSettings.STACK_ENABLED,
                NotificationBigClockSettings.INTERACTIVE_STACK, NotificationBigClockSettings.ENTRY_EFFECT_ENABLED,
                FeatureOptions.STACK_LANDSCAPE_ENABLED, NotificationGroupStack.MASTER,
                StatusBarShadeIconSettings.MASTER}) result.putBoolean(key, false);
        return result;
    }

    private PanelMode() { }
}
