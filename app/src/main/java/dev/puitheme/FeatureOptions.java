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
    private static final Map<String, String[]> EFFECT_KEYS;
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
        // The empty v3 record stays native. Existing code187 selections remain active after upgrade.
        add(defaults, groups, "status_icon_library", IconManagementSettings.ENABLED, true);
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
        add(defaults, groups, QsEntrances.GROUP, QsEntrances.MASTER, false);
        add(defaults, groups, QsEntrances.GROUP, QsEntrances.EDIT_HIDDEN, false);
        add(defaults, groups, QsEntrances.GROUP, QsEntrances.SETTINGS_HIDDEN, false);
        add(defaults, groups, QsEntrances.GROUP, QsEntrances.HIDDEN_CLICKABLE, false);
        add(defaults, groups, "data", "data_badge_hidden", true);
        add(defaults, groups, "data", SignalResources.ROAMING_HIDDEN, false);
        // Legacy transport state is derived from the new independent native-arrow switch.
        defaults.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN, true);
        // Deprecated transport key retained for complete old snapshots, without runtime gating.
        add(defaults, groups, "data", "data_single_enabled", true);
        add(defaults, groups, "data", DataBatterySpacing.ENABLED, false);
        add(defaults, groups, "wifi", "wifi_icon_enabled", true);
        add(defaults, groups, "wifi", NetworkDisplaySettings.WIFI_HIDDEN, false);
        add(defaults, groups, "wifi", "wifi_badge_hidden", true);
        add(defaults, groups, "wifi", "wifi_activity_hidden", true);
        add(defaults, groups, "label", StatusBarSettings.LABEL_HIDDEN, false);
        add(defaults, groups, "label", RadioState.ALWAYS_SHOWN, false);
        add(defaults, groups, "label", RadioState.WIFI_MERGED, true);
        add(defaults, groups, "network", RadioState.BOTH_VISIBLE, false);
        add(defaults, groups, "network", NetworkSlotGeometry.POSITION, false);
        add(defaults, groups, "label", "label_normalize_enabled", true);
        add(defaults, groups, "label", NetworkLabelFormats.ENABLED, false);
        // This existing switch controls formatting, not the whole clock group.
        add(defaults, groups, "clock", "clock_enabled", false);
        add(defaults, groups, "shade_clock", "shade_clock_enabled", false);
        add(defaults, groups, "classic_clock", "classic_clock_enabled", false);
        // Digit cells only act on a format that shows seconds; two rows and margins are opt-in.
        for (String group : ClockLayoutSettings.DIGIT_GROUPS) {
            add(defaults, groups, group, ClockLayoutSettings.digitKey(group), true);
            add(defaults, groups, group, ClockLayoutSettings.colonKey(group), true);
        }
        add(defaults, groups, "clock", ClockLayoutSettings.DUAL_ENABLED, false);
        add(defaults, groups, "clock", ClockLayoutSettings.MARGIN_ENABLED, false);
        add(defaults, groups, "clock", ClockLayoutSettings.DUAL_COLOR_ENABLED, false);
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
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.MASTER, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.FOLLOW_NOTIFICATION, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.DESKTOP_HIGHLIGHTS, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.DESKTOP_BACKGROUND, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.FOLLOW_FOREGROUND, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.ENABLED, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.LOCKSCREEN_ENABLED, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.MASK_ENABLED, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudStyle.FOLLOW_MEDIA, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudStyle.NOTIFICATION_FOLLOW, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudStyle.FOLLOW_ONLY_PLAYING, true);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.LOCK_CUSTOM, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.LOCK_GRADIENT, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.AOD_CUSTOM, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudAccent.AOD_GRADIENT, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.MASTER, false);
        add(defaults, groups, "privacy_dot", PrivacyIndicatorSettings.HIDE_DOT, false);
        add(defaults, groups, "privacy_dot", PrivacyIndicatorSettings.CAPSULE, false);
        add(defaults, groups, "privacy_dot", PrivacyIndicatorSettings.CAMERA, true);
        add(defaults, groups, "privacy_dot", PrivacyIndicatorSettings.MICROPHONE, true);
        add(defaults, groups, "privacy_dot", PrivacyIndicatorSettings.LOCATION, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.FILL_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.FOLLOW_MEDIA, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.BORDER_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.RADIUS_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.GRADIENT_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.BLUR_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.MARQUEE_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.MARQUEE_REVERSE, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.SPECTRUM_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.SPECTRUM_FOLLOW, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.COVER_RADIUS_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.COVER_SIZE_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", CapsuleEffects.BOUNCE_ENABLED, false);
        add(defaults, groups, "fluid_cloud_capsule", FluidCloudCapsule.GLOW_ENABLED, false);
        add(defaults, groups, "fluid_cloud_accent", FluidCloudStyle.LOCK_CAPSULE, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.SCOPE_FLOAT, true);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.SCOPE_SHADE, false);
        add(defaults, groups, "fluid_cloud_style", FluidCloudStyle.SCOPE_LOCK, true);
        DEFAULTS = Collections.unmodifiableMap(defaults);
        GROUP_BY_KEY = Collections.unmodifiableMap(groups);
        Map<String, String[]> effectKeys = new LinkedHashMap<>();
        java.util.Set<String> effectGroups = new java.util.LinkedHashSet<>(groups.values());
        Collections.addAll(effectGroups, CarrierPanels.ALL_GROUPS);
        for (String group : effectGroups)
            effectKeys.put(group, new String[]{group + "_position_enabled", group + "_size_enabled",
                    group + "_color_enabled", group + "_text_style_enabled"});
        EFFECT_KEYS = Collections.unmodifiableMap(effectKeys);
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
        // These keys are also read in every native text draw. Keep the fixed names allocation-free.
        if ("clock".equals(group)) return "clock_controls_enabled";
        if ("shade_clock".equals(group)) return "shade_clock_controls_enabled";
        if ("classic_clock".equals(group)) return "classic_clock_controls_enabled";
        if ("speed".equals(group)) return "speed_enabled";
        if ("data".equals(group)) return "data_enabled";
        if ("wifi".equals(group)) return "wifi_enabled";
        if ("label".equals(group)) return "label_enabled";
        if ("carrier".equals(group)) return "carrier_enabled";
        if ("font".equals(group)) return "font_enabled";
        if ("battery".equals(group)) return "battery_enabled";
        if ("tiles".equals(group)) return "tiles_enabled";
        if ("status_icon_library".equals(group)) return IconManagementSettings.ENABLED;
        return group + "_enabled";
    }

    public boolean enabled(String group) {
        // The visible page master gates its own children, never the independent accent page.
        if ("fluid_cloud_style".equals(group)) return !safeMode &&
                isEnabled(FluidCloudStyle.MASTER) &&
                (isEnabled(FluidCloudStyle.FOLLOW_NOTIFICATION)
                        || isEnabled(FluidCloudStyle.DESKTOP_BACKGROUND));
        return !safeMode && isEnabled(masterKey(group));
    }
    /** Raw switch state; use effective() when a switch belongs to a group. */
    public boolean isEnabled(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public boolean effective(String group, String key) {
        // Saved post-1.8.2 network options stay in snapshots, but never change the public renderer.
        if (RadioState.BOTH_VISIBLE.equals(key) || NetworkSlotGeometry.POSITION.equals(key)
                || RadioState.WIFI_MERGED.equals(key) || RadioState.ALWAYS_SHOWN.equals(key)
                || SignalResources.ROAMING_HIDDEN.equals(key)) return false;
        // Custom contents and unify exclude each other; the editor turns the partner off, and a restored backup with both on
        // still shows the custom text only.
        if (NetworkLabelFormats.UNIFY.equals(key) && isEnabled(NetworkLabelFormats.ENABLED)) return false;
        // The whole media-accent page, following included, is governed by its master.
        // Following the media colour also needs the style page's custom base colour (code100).
        if(FluidCloudStyle.FOLLOW_MEDIA.equals(key)||FluidCloudStyle.NOTIFICATION_FOLLOW.equals(key)||FluidCloudStyle.FOLLOW_ONLY_PLAYING.equals(key))
            return !safeMode&&isEnabled(key)&&isEnabled(FluidCloudAccent.ENABLED)&&isEnabled(FluidCloudStyle.MASTER)&&isEnabled(FluidCloudStyle.DESKTOP_BACKGROUND);
        if(FluidCloudAccent.MASK_ENABLED.equals(key)
                ||FluidCloudAccent.LOCK_CUSTOM.equals(key)||FluidCloudAccent.LOCK_GRADIENT.equals(key)
                ||FluidCloudAccent.AOD_CUSTOM.equals(key)||FluidCloudAccent.AOD_GRADIENT.equals(key))return !safeMode&&isEnabled(key)&&isEnabled(FluidCloudAccent.ENABLED);
        if(FluidCloudStyle.FOLLOW_FOREGROUND.equals(key)||FluidCloudStyle.SCOPE_SHADE.equals(key))return false;
        if (StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(key)) return !safeMode && isEnabled(key);
        // Highlight removal is the C17 feature; keeping the music card's highlight only needs this switch.
        if (FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT.equals(key))
            return !safeMode&&isEnabled(key);
        return enabled(group) && isEnabled(key);
    }
    public boolean safeMode() { return safeMode; }
    /** The two-row status clock is its own mode and needs only its switch. The default single-row clock wins a
     * conflict: a saved state with both on (older versions allowed it) shows the single row. */
    public boolean dualClock() {
        return !safeMode && isEnabled(ClockLayoutSettings.DUAL_ENABLED)
                && !isEnabled(masterKey(ClockLayoutSettings.STATUS));
    }
    private boolean groupEffect(String group, int index) {
        String[] keys = EFFECT_KEYS.get(group);
        return keys != null && effective(group, keys[index]);
    }
    public boolean position(String group) { return groupEffect(group, 0); }
    public boolean size(String group) { return groupEffect(group, 1); }
    public boolean color(String group) { return groupEffect(group, 2); }
    public boolean textStyle(String group) { return groupEffect(group, 3); }
    /** Hiding owns only the label; disabling its group restores the native label behavior. */
    public boolean hideNetworkLabel() { return effective("label", StatusBarSettings.LABEL_HIDDEN); }
    /** Display and custom styling have separate switches. */
    public boolean hideWifiIcon() { return !safeMode && isEnabled(NetworkDisplaySettings.WIFI_HIDDEN); }
    public boolean hideNativeNetworkBadge() { return effective("data", "data_badge_hidden"); }
    public boolean singleSignal(String layout) {
        return enabled("data") && "single".equals(layout);
    }
    public Map<String, Boolean> values() { return values; }
}
