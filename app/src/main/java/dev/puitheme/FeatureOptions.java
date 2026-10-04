// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable feature switches. A group switch gates every effect owned by that group. */
public final class FeatureOptions {
    /** Whole-list stacking has an independent opt-in for landscape. */
    public static final String STACK_LANDSCAPE_ENABLED = "notification_big_clock_stack_landscape_enabled";
    public static final String[] GROUPS = {"speed", "data", "wifi", "label", "clock", "shade_clock", "classic_clock", "carrier", "font", "battery", "tiles"};
    public static final Map<String, Boolean> DEFAULTS;
    /** Maps each switch to its owning UI group, in display order. */
    public static final Map<String, String> GROUP_BY_KEY;
    private final Map<String, Boolean> values;
    private final boolean safeMode;

    static {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        Map<String, String> groups = new LinkedHashMap<>();
        // New installations leave native behavior intact; explicit saved switches still win.
        for (String group : GROUPS) add(defaults, groups, group, masterKey(group), false);
        defaults.put(STACK_LANDSCAPE_ENABLED, false);
        defaults.put(NativeDataActivity.MASTER, false);
        defaults.put(NativeDataActivity.COLOR_ENABLED, false);
        for (String group : GROUPS) {
            if ("font".equals(group) || "tiles".equals(group)) continue;
            add(defaults, groups, group, group + "_position_enabled", true);
            add(defaults, groups, group, group + "_size_enabled", true);
            add(defaults, groups, group, group + "_color_enabled", true);
        }
        for (String group : new String[]{"speed", "label", "clock", "shade_clock", "classic_clock", "carrier"})
            add(defaults, groups, group, group + "_text_style_enabled", true);
        add(defaults, groups, "speed", "speed_lines_enabled", true);
        add(defaults, groups, "data", "data_icon_enabled", true);
        add(defaults, groups, "data", "data_badge_hidden", true);
        // Legacy transport state is derived from the new independent native-arrow switch.
        defaults.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN, true);
        // Deprecated transport key retained for complete old snapshots, without runtime gating.
        add(defaults, groups, "data", "data_single_enabled", true);
        add(defaults, groups, "data", DataBatterySpacing.ENABLED, false);
        add(defaults, groups, "wifi", "wifi_icon_enabled", true);
        add(defaults, groups, "wifi", "wifi_badge_hidden", true);
        add(defaults, groups, "wifi", "wifi_activity_hidden", true);
        add(defaults, groups, "label", StatusBarSettings.LABEL_HIDDEN, false);
        add(defaults, groups, "label", "label_normalize_enabled", true);
        // This existing switch controls formatting, not the whole clock group.
        add(defaults, groups, "clock", "clock_enabled", false);
        add(defaults, groups, "shade_clock", "shade_clock_enabled", false);
        add(defaults, groups, "classic_clock", "classic_clock_enabled", false);
        add(defaults, groups, "carrier", "carrier_replace_enabled", true);
        for (String panel : CarrierPanels.ALL_GROUPS)
            for (String suffix : new String[]{"enabled", "replace_enabled", "position_enabled",
                    "size_enabled", "color_enabled", "text_style_enabled"})
                add(defaults, groups, "carrier", CarrierPanels.key(panel, suffix),
                        !"enabled".equals(suffix));
        add(defaults, groups, "battery", "battery_style_enabled", true);
        add(defaults, groups, "battery", "battery_charge_inside", true);
        add(defaults, groups, "battery", BatteryControls.HIDE_CHARGE, false);
        for (String part : new String[]{"text", "bolt", "charge", "alert"})
            add(defaults, groups, "battery", "battery_" + part + "_color_enabled", true);
        add(defaults, groups, "tiles", "tiles_fade_enabled", true);
        add(defaults, groups, "tiles", "tiles_blur_enabled", false);
        add(defaults, groups, "tiles", "tiles_portrait_enabled", true);
        add(defaults, groups, "tiles", "tiles_landscape_enabled", true);
        DEFAULTS = Collections.unmodifiableMap(defaults);
        GROUP_BY_KEY = Collections.unmodifiableMap(groups);
    }

    private static void add(Map<String, Boolean> defaults, Map<String, String> groups,
                            String group, String key, boolean value) {
        defaults.put(key, value);
        groups.put(key, group);
    }

    private FeatureOptions(Map<String, ?> supplied) {
        safeMode = supplied != null && Boolean.TRUE.equals(supplied.get(StatusBarSettings.SAFE_MODE));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : DEFAULTS.entrySet()) {
            result.put(entry.getKey(), StatusBarSettings.bool(supplied, entry.getKey()));
        }
        values = Collections.unmodifiableMap(result);
    }

    public static FeatureOptions from(Map<String, ?> values) { return new FeatureOptions(values); }

    public static FeatureOptions from(Bundle settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (settings != null) for (String key : DEFAULTS.keySet()) {
            Object value = settings.get(key);
            if (value instanceof Boolean) result.put(key, value);
        }
        if (settings != null) result.put(StatusBarSettings.SAFE_MODE, settings.get(StatusBarSettings.SAFE_MODE));
        return new FeatureOptions(result);
    }

    public static String masterKey(String group) {
        return "clock".equals(group) || "shade_clock".equals(group) || "classic_clock".equals(group) ? group + "_controls_enabled" : group + "_enabled";
    }

    public boolean enabled(String group) {
        // Each page is an independent opt-in. Its switch must not require a hidden global gate.
        return !safeMode && isEnabled(masterKey(group));
    }
    /** Raw switch state; use effective() when a switch belongs to a group. */
    public boolean isEnabled(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public boolean effective(String group, String key) {
        if (StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(key)) return !safeMode && isEnabled(key);
        return enabled(group) && isEnabled(key);
    }
    public boolean safeMode() { return safeMode; }
    public boolean position(String group) { return effective(group, group + "_position_enabled"); }
    public boolean size(String group) { return effective(group, group + "_size_enabled"); }
    public boolean color(String group) { return effective(group, group + "_color_enabled"); }
    public boolean textStyle(String group) { return effective(group, group + "_text_style_enabled"); }
    /** Hiding owns only the label; disabling its group restores the native label behavior. */
    public boolean hideNetworkLabel() { return effective("label", StatusBarSettings.LABEL_HIDDEN); }
    public boolean hideNativeNetworkBadge() { return effective("data", "data_badge_hidden"); }
    public boolean singleSignal(String layout) {
        return enabled("data") && "single".equals(layout);
    }
    public Map<String, Boolean> values() { return values; }
}
