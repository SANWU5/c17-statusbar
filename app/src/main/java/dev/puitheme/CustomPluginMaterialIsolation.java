// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.os.Bundle;
import org.json.JSONObject;

/** Runtime-only ownership of the glass plugin's materials; saved host preferences stay intact.
 * The plugin's 主模块功能 「保留」 switches hand overlapping host features back. A missing or off switch
 * means paused, the behaviour before those switches existed. */
final class CustomPluginMaterialIsolation {
    static final String PLUGIN = "dev.c17.liquid-glass";
    static final int MIN_VERSION = 72;
    static final String LOCK_OWNED = "_c17_plugin_lock_material_owned";
    private static final String CONTROL_OWNED = "_c17_plugin_control_material_owned";
    private static final int CONTROL = 1, NOTIFICATION = 2, CLEAR = 4, FLOAT = 8,
            LOCK = 16, BACKDROP = 32, CLOCK = 64;
    static final String QS_APPEARANCE = "host_keep_qs_appearance", QS_CORNERS = "host_keep_qs_corners",
            QS_MEDIA = "host_keep_qs_media", CC_HIGHLIGHT = "host_keep_cc_highlight",
            NC_HIGHLIGHT = "host_keep_nc_highlight", CLEAR_STYLE = "host_keep_clear", UNIFIED = "host_keep_unified",
            HUN_HIGHLIGHT = "host_keep_hun_highlight", FLUID_STYLE = "host_keep_fluid_style",
            FLUID_ACCENT = "host_keep_fluid_accent", LOCK_COLORS = "host_keep_lock_colors",
            FOLLOW_MEDIA = "host_keep_follow_media", LOCK_STATUS_BLUR = "host_keep_lock_status_blur",
            LOCK_FONT = "host_keep_lock_font", SHADE_WALLPAPER = "host_keep_shade_wallpaper";
    /** Fixed order: the change signature gives each switch one bit. */
    static final String[] SWITCHES = {QS_APPEARANCE, QS_CORNERS, QS_MEDIA, CC_HIGHLIGHT, NC_HIGHLIGHT, CLEAR_STYLE,
            UNIFIED, HUN_HIGHLIGHT, FLUID_STYLE, FLUID_ACCENT, LOCK_COLORS, FOLLOW_MEDIA, LOCK_STATUS_BLUR, LOCK_FONT,
            SHADE_WALLPAPER};
    private CustomPluginMaterialIsolation() { }

    static Bundle apply(Bundle saved, JSONObject glass) {
        if (saved == null) return null;
        Bundle result = new Bundle(saved);
        if (saved.getBoolean("module_safe_mode", false) || glass == null) return result;
        int areas = ownership(glass);
        if ((areas & CONTROL) != 0) {
            // Keep the host's opt-in 2x1 fill available alongside control-center glass.
            result.putBoolean(CONTROL_OWNED, true);
            // The host's tile colour paints only active fills; kept, the plugin's glass stays on inactive tiles.
            if (blocks(glass, QS_APPEARANCE)) off(result, "qs_appearance_enabled");
            if (blocks(glass, QS_CORNERS)) off(result, "qs_tile_corners_enabled");
            if (blocks(glass, QS_MEDIA)) off(result, "qs_media_cover_enabled");
            if (blocks(glass, CC_HIGHLIGHT)) off(result, "c17_highlight_removal_control_center_enabled");
        }
        // Ordinary notification material is also the clear button's and the lock screen's.
        if ((areas & (NOTIFICATION | CLEAR | LOCK)) != 0 && blocks(glass, NC_HIGHLIGHT))
            off(result, "c17_highlight_removal_notification_enabled");
        if ((areas & CLEAR) != 0 && blocks(glass, CLEAR_STYLE))
            off(result, "notification_clear_custom_enabled", "notification_clear_landscape_custom_enabled");
        if ((areas & FLOAT) != 0) {
            // Glass owns expanded floating cards, not the host's collapsed top capsule.
            if (blocks(glass, HUN_HIGHLIGHT)) off(result, "c17_highlight_removal_heads_up_enabled");
            if (blocks(glass, FLUID_STYLE)) off(result, "fluid_cloud_style_enabled", "fluid_cloud_follow_notification_enabled",
                    "fluid_cloud_desktop_background_removal_enabled");
            if (blocks(glass, FLUID_ACCENT)) off(result, "fluid_cloud_accent_enabled");
        }
        if ((areas & (NOTIFICATION | CLEAR | FLOAT | LOCK)) != 0 && blocks(glass, UNIFIED))
            off(result, "c17_notification_color_unified_enabled");
        if ((areas & LOCK) != 0) {
            boolean colors = blocks(glass, LOCK_COLORS), follow = blocks(glass, FOLLOW_MEDIA);
            // The host's older lock colour/gradient recipes have no shared master switch. The runtime ownership
            // flag also excludes their automatic native-colour path, so it is set only while both are paused.
            if (colors && follow) result.putBoolean(LOCK_OWNED, true);
            if (colors) off(result, "lock_aod_notification_white_background_enabled", "fluid_cloud_lock_custom_enabled",
                    "fluid_cloud_aod_custom_enabled", "fluid_cloud_lock_gradient_enabled", "fluid_cloud_aod_gradient_enabled",
                    "fluid_cloud_lock_capsule_enabled", "lock_aod_unify_enabled");
            if (follow) off(result, "notification_follow_media_enabled");
        }
        if ((areas & (LOCK | CLOCK)) != 0 && blocks(glass, LOCK_STATUS_BLUR)) off(result, "lockscreen_status_blur_enabled");
        if ((areas & BACKDROP) != 0 && blocks(glass, SHADE_WALLPAPER))
            off(result, "shade_wallpaper_enabled", "shade_wallpaper_portrait_enabled", "shade_wallpaper_landscape_enabled");
        if ((areas & CLOCK) != 0 && blocks(glass, LOCK_FONT)) {
            try {
                JSONObject fonts = new JSONObject(result.getString("font_scope_assignments", "{}"));
                fonts.put("lockscreen_text", "system");
                result.putString("font_scope_assignments", fonts.toString());
            } catch (org.json.JSONException invalid) {
                // A validated host snapshot normally contains a valid map. A broken legacy map
                // must not prevent the plugin's own clock from retaining the native font source.
                result.putString("font_scope_assignments", "{\"lockscreen_text\":\"system\"}");
            }
        }
        return result;
    }

    static boolean lockOwned(Bundle settings) { return settings != null && settings.getBoolean(LOCK_OWNED, false); }
    static boolean controlOwned(Bundle settings) { return settings != null && settings.getBoolean(CONTROL_OWNED, false); }
    /** The plugin's 「保留」 switch for this feature is off or missing: the host feature stays paused. */
    static boolean blocks(JSONObject glass, String key) { return glass == null || !glass.optBoolean(key, false); }

    /** Ownership plus one bit per kept host feature: any change to what {@link #apply} hands back. */
    static int signature(JSONObject glass) {
        int areas = ownership(glass);
        if (areas == 0) return 0;
        for (int i = 0; i < SWITCHES.length; i++) if (!blocks(glass, SWITCHES[i])) areas |= 1 << (8 + i);
        return areas;
    }

    /** Mirrors only the plugin's ownership gates, never its optical implementation or saved values. */
    static int ownership(JSONObject glass) {
        if (glass == null) return 0;
        int areas = 0;
        if (glass.optBoolean("system_glass", false)) {
            if (glass.optBoolean("cc_glass", false)) areas |= CONTROL;
            if (glass.optBoolean("nc_glass", false)) areas |= NOTIFICATION;
            if (glass.optBoolean("clear_glass", false)) areas |= CLEAR;
            if (glass.optBoolean("float_glass", false)) areas |= FLOAT;
            // Ordinary notification material is also used on the lock screen.
            if (glass.optBoolean("lock_glass", false) || (areas & NOTIFICATION) != 0) areas |= LOCK;
            if ((areas & (CONTROL | NOTIFICATION)) != 0 || glass.optBoolean("bg_tint", false)
                    || glass.optDouble("bg_mask", 0) != 0 || glass.optDouble("bg_milk", 0) != 0
                    || glass.optDouble("bg_saturation", 0) != 0 || glass.optDouble("bg_blur", 100) != 100) areas |= BACKDROP;
        }
        if (glass.optBoolean("clock_glass", false) && glass.optBoolean("clock_lock_glass", true)) areas |= CLOCK;
        return areas;
    }

    private static void off(Bundle result, String... keys) { for (String key : keys) result.putBoolean(key, false); }
}
