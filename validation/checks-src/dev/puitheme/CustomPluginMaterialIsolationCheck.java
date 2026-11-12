// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.os.Bundle;
import org.json.JSONObject;

/** Opposing host recipes must yield the same plugin-owned material, without modifying storage. */
public final class CustomPluginMaterialIsolationCheck {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++; if (!value) throw new AssertionError(reason);
    }
    private static JSONObject state(String... enabled) throws Exception {
        JSONObject result = new JSONObject();
        for (String key : enabled) result.put(key, true);
        return result;
    }
    private static Bundle host(boolean enabled) {
        Bundle result = new Bundle();
        for (String key : new String[]{"qs_appearance_enabled", "qs_media_cover_enabled", "qs_tile_corners_enabled",
                "qs_tile_2x1_full_fill_enabled", "c17_highlight_removal_enabled", "c17_highlight_removal_control_center_enabled",
                "c17_highlight_removal_notification_enabled", "c17_highlight_removal_heads_up_enabled",
                "c17_notification_color_unified_enabled", "fluid_cloud_style_enabled", "fluid_cloud_accent_enabled",
                "fluid_cloud_follow_notification_enabled", "fluid_cloud_desktop_background_removal_enabled",
                "fluid_cloud_lock_custom_enabled", "fluid_cloud_aod_custom_enabled", "fluid_cloud_lock_gradient_enabled",
                "notification_follow_media_enabled", "lock_aod_unify_enabled", "fluid_cloud_capsule_enabled", "fluid_cloud_lock_capsule_enabled",
                "lock_aod_notification_white_background_enabled", "lockscreen_status_blur_enabled",
                "notification_clear_custom_enabled", "notification_clear_landscape_custom_enabled",
                "shade_wallpaper_enabled", "shade_wallpaper_portrait_enabled", "shade_wallpaper_landscape_enabled"})
            result.putBoolean(key, enabled);
        result.putFloat("wifi_offset_x", 21.5f);
        result.putString("font_mode", "custom:kept");
        return result;
    }
    private static void unchanged(Bundle saved, Bundle actual, String why) {
        check(saved != actual && saved.keySet().equals(actual.keySet()), why + " copies all keys");
        for (String key : saved.keySet()) check(java.util.Objects.equals(saved.get(key), actual.get(key)), why + ": " + key);
    }
    /** 主模块功能: each 「保留」 switch turned on hands back exactly its own host keys; missing or off keeps the old pause. */
    private static void keptSwitches() throws Exception {
        String[][] table = {
                {CustomPluginMaterialIsolation.QS_APPEARANCE, "qs_appearance_enabled"},
                {CustomPluginMaterialIsolation.QS_CORNERS, "qs_tile_corners_enabled"},
                {CustomPluginMaterialIsolation.QS_MEDIA, "qs_media_cover_enabled"},
                {CustomPluginMaterialIsolation.CC_HIGHLIGHT, "c17_highlight_removal_control_center_enabled"},
                {CustomPluginMaterialIsolation.NC_HIGHLIGHT, "c17_highlight_removal_notification_enabled"},
                {CustomPluginMaterialIsolation.CLEAR_STYLE, "notification_clear_custom_enabled", "notification_clear_landscape_custom_enabled"},
                {CustomPluginMaterialIsolation.UNIFIED, "c17_notification_color_unified_enabled"},
                {CustomPluginMaterialIsolation.HUN_HIGHLIGHT, "c17_highlight_removal_heads_up_enabled"},
                {CustomPluginMaterialIsolation.FLUID_STYLE, "fluid_cloud_style_enabled", "fluid_cloud_follow_notification_enabled",
                        "fluid_cloud_desktop_background_removal_enabled"},
                {CustomPluginMaterialIsolation.FLUID_ACCENT, "fluid_cloud_accent_enabled"},
                {CustomPluginMaterialIsolation.LOCK_COLORS, "lock_aod_notification_white_background_enabled", "fluid_cloud_lock_custom_enabled",
                        "fluid_cloud_aod_custom_enabled", "fluid_cloud_lock_gradient_enabled", "fluid_cloud_lock_capsule_enabled", "lock_aod_unify_enabled"},
                {CustomPluginMaterialIsolation.FOLLOW_MEDIA, "notification_follow_media_enabled"},
                {CustomPluginMaterialIsolation.LOCK_STATUS_BLUR, "lockscreen_status_blur_enabled"},
                {CustomPluginMaterialIsolation.SHADE_WALLPAPER, "shade_wallpaper_enabled", "shade_wallpaper_portrait_enabled",
                        "shade_wallpaper_landscape_enabled"}};
        check(table.length + 1 == CustomPluginMaterialIsolation.SWITCHES.length, "every switch but the font one is in the table");
        String[] all = {"system_glass", "cc_glass", "nc_glass", "clear_glass", "float_glass", "lock_glass", "clock_glass"};
        Bundle saved = host(true);
        Bundle paused = CustomPluginMaterialIsolation.apply(saved, state(all));
        int base = CustomPluginMaterialIsolation.signature(state(all));
        check(base == CustomPluginMaterialIsolation.ownership(state(all)), "no kept switch: the signature is the ownership");
        java.util.Set<Integer> signatures = new java.util.HashSet<>();
        for (String[] row : table) {
            JSONObject glass = state(all).put(row[0], true);
            Bundle kept = CustomPluginMaterialIsolation.apply(saved, glass);
            java.util.Set<String> own = new java.util.HashSet<>(java.util.Arrays.asList(row).subList(1, row.length));
            for (String key : own) check(kept.getBoolean(key), row[0] + " kept hands back " + key);
            for (String key : saved.keySet()) if (!own.contains(key))
                check(java.util.Objects.equals(kept.get(key), paused.get(key)), row[0] + " leaves other keys as paused: " + key);
            unchangedExcept(paused, CustomPluginMaterialIsolation.apply(saved, state(all).put(row[0], false)), row[0] + " false equals missing");
            int signature = CustomPluginMaterialIsolation.signature(glass);
            check(signature != base && signatures.add(signature), row[0] + " has its own change signature");
        }
        check(paused.getBoolean(CustomPluginMaterialIsolation.LOCK_OWNED), "both lock recipes paused: lock ownership set");
        check(!CustomPluginMaterialIsolation.apply(saved, state(all).put(CustomPluginMaterialIsolation.LOCK_COLORS, true))
                .getBoolean(CustomPluginMaterialIsolation.LOCK_OWNED), "kept lock colours run the host lock path");
        check(!CustomPluginMaterialIsolation.apply(saved, state(all).put(CustomPluginMaterialIsolation.FOLLOW_MEDIA, true))
                .getBoolean(CustomPluginMaterialIsolation.LOCK_OWNED), "kept follow-media runs the host lock path");
        saved.putString("font_scope_assignments", "{\"lockscreen_text\":\"custom:kept\"}");
        JSONObject keptFont = state("clock_glass").put(CustomPluginMaterialIsolation.LOCK_FONT, true);
        check("{\"lockscreen_text\":\"custom:kept\"}".equals(CustomPluginMaterialIsolation.apply(saved, keptFont).getString("font_scope_assignments")),
                "kept lock font leaves the host font choice");
        check(!CustomPluginMaterialIsolation.apply(saved, keptFont).getBoolean("lockscreen_status_blur_enabled"),
                "clock glass still pauses the lock status blur by default");
        Bundle onlyControlKept = CustomPluginMaterialIsolation.apply(saved, state("system_glass", "cc_glass").put(CustomPluginMaterialIsolation.QS_APPEARANCE, true));
        check(onlyControlKept.getBoolean("qs_appearance_enabled") && CustomPluginMaterialIsolation.controlOwned(onlyControlKept),
                "kept tile colour with control glass: host colours active fills, plugin still owns the material");
        check(!CustomPluginMaterialIsolation.apply(host(false), state("system_glass", "cc_glass").put(CustomPluginMaterialIsolation.QS_APPEARANCE, true))
                .getBoolean("qs_appearance_enabled"), "keeping never enables a user-disabled tile colour");
        check(CustomPluginMaterialIsolation.signature(null) == 0 && CustomPluginMaterialIsolation.signature(state()) == 0,
                "no plugin: no signature");
        check(CustomPluginMaterialIsolation.signature(state(CustomPluginMaterialIsolation.QS_APPEARANCE).put("cc_glass", false)) == 0,
                "switches of an area that is off do not change the signature");
    }
    private static void unchangedExcept(Bundle expected, Bundle actual, String why) {
        check(expected.keySet().equals(actual.keySet()), why + " key set");
        for (String key : expected.keySet()) check(java.util.Objects.equals(expected.get(key), actual.get(key)), why + ": " + key);
    }
    public static void main(String[] args) throws Exception {
        Bundle saved = host(true);
        JSONObject glass = state("system_glass", "cc_glass", "nc_glass", "clear_glass", "float_glass", "lock_glass");
        Bundle isolated = CustomPluginMaterialIsolation.apply(saved, glass);
        check(!isolated.getBoolean("qs_appearance_enabled"), "host QS colours cannot recolour plugin glass");
        check(!isolated.getBoolean("qs_media_cover_enabled"), "host media cover recipe cannot replace plugin glass");
        check(isolated.getBoolean(QsTileFullFill.ENABLED), "user-enabled 2x1 fill survives control-center glass");
        check(!CustomPluginMaterialIsolation.apply(host(false), glass).getBoolean(QsTileFullFill.ENABLED),
                "glass never enables a user-disabled 2x1 fill");
        check(!isolated.getBoolean("qs_tile_corners_enabled"), "host corners cannot reshape plugin glass");
        check(!isolated.getBoolean("c17_highlight_removal_control_center_enabled"), "host control material yields");
        check(!isolated.getBoolean("c17_highlight_removal_notification_enabled"), "host notification material yields");
        check(!isolated.getBoolean("c17_highlight_removal_heads_up_enabled"), "host heads-up material yields");
        check(!isolated.getBoolean("c17_notification_color_unified_enabled"), "independent uniform notification colour yields");
        check(isolated.getBoolean("fluid_cloud_capsule_enabled"), "top fluid capsule remains controlled by the host while glass owns expanded floating cards");
        check(!isolated.getBoolean("fluid_cloud_lock_capsule_enabled"), "separate bottom lock capsule still yields to lock glass");
        check(!isolated.getBoolean("fluid_cloud_style_enabled"), "host fluid material yields");
        check(!isolated.getBoolean("fluid_cloud_accent_enabled"), "host fluid accent yields");
        check(!isolated.getBoolean("lock_aod_notification_white_background_enabled"), "host AOD white layer yields");
        check(!isolated.getBoolean("lockscreen_status_blur_enabled"), "host backdrop blur cannot alter plugin source");
        check(isolated.getBoolean(CustomPluginMaterialIsolation.LOCK_OWNED), "always-on legacy lock effects are explicitly bypassed");
        check(!isolated.getBoolean("notification_clear_custom_enabled") && !isolated.getBoolean("notification_clear_landscape_custom_enabled"), "both clear-button orientations yield");
        check(!isolated.getBoolean("shade_wallpaper_portrait_enabled") && !isolated.getBoolean("shade_wallpaper_landscape_enabled"), "host wallpaper cannot replace glass input");
        check(isolated.getFloat("wifi_offset_x") == 21.5f && "custom:kept".equals(isolated.getString("font_mode")), "independent layout and fonts survive");
        check(saved.getBoolean("qs_appearance_enabled") && !saved.containsKey(CustomPluginMaterialIsolation.LOCK_OWNED), "stored user snapshot stays intact");
        for (String key : saved.keySet()) if (!key.equals("c17_highlight_removal_enabled")
                && !key.equals("fluid_cloud_capsule_enabled") && !key.equals(QsTileFullFill.ENABLED))
            check(java.util.Objects.equals(isolated.get(key), CustomPluginMaterialIsolation.apply(host(false), glass).get(key)), "host switch cannot affect owned recipe: " + key);
        unchanged(saved, CustomPluginMaterialIsolation.apply(saved, null), "disabled/failed/missing glass");
        unchanged(saved, CustomPluginMaterialIsolation.apply(saved, state()), "all plugin effects off");
        unchanged(saved, CustomPluginMaterialIsolation.apply(saved, state("cc_glass")), "family master off");
        unchanged(saved, CustomPluginMaterialIsolation.apply(saved, state("side_text_custom")), "sidebar text does not take host materials");
        Bundle onlyControl = CustomPluginMaterialIsolation.apply(saved, state("system_glass", "cc_glass"));
        check(onlyControl.getBoolean("notification_clear_custom_enabled") && onlyControl.getBoolean("fluid_cloud_style_enabled"), "control-only glass leaves other scenes intact");
        Bundle onlyClear = CustomPluginMaterialIsolation.apply(saved, state("system_glass", "clear_glass"));
        check(!onlyClear.getBoolean("c17_highlight_removal_notification_enabled"), "clear-only glass bypasses host notification optics also applied to the clear button");
        check(!onlyClear.getBoolean("c17_notification_color_unified_enabled"), "clear-only glass bypasses host unified notification tint also applied to the clear button");
        check(onlyClear.getBoolean("qs_appearance_enabled") && onlyClear.getBoolean("fluid_cloud_style_enabled"), "clear-only glass leaves control and fluid recipes intact");
        Bundle onlyFloat = CustomPluginMaterialIsolation.apply(saved, state("system_glass", "float_glass"));
        check(onlyFloat.getBoolean("fluid_cloud_capsule_enabled"), "floating glass does not disable the host top capsule");
        check(onlyFloat.getBoolean("fluid_cloud_lock_capsule_enabled"), "floating glass does not claim the bottom lock capsule");
        check(!onlyFloat.getBoolean("fluid_cloud_style_enabled") && !onlyFloat.getBoolean("fluid_cloud_accent_enabled"), "expanded floating materials still yield to glass");
        Bundle capsuleOff = host(true);
        capsuleOff.putBoolean("fluid_cloud_capsule_enabled", false);
        check(!CustomPluginMaterialIsolation.apply(capsuleOff, glass).getBoolean("fluid_cloud_capsule_enabled"), "glass never enables a user-disabled top capsule");
        capsuleOff.remove("fluid_cloud_capsule_enabled");
        check(!CustomPluginMaterialIsolation.apply(capsuleOff, glass).containsKey("fluid_cloud_capsule_enabled"), "glass does not invent a missing top capsule preference");
        saved.putBoolean("fluid_cloud_capsule_fill_enabled", true);
        saved.putString("fluid_cloud_capsule_fill_color", "#A1234567");
        saved.putFloat("fluid_cloud_capsule_blur_radius", 37.5f);
        Bundle capsuleSettings = CustomPluginMaterialIsolation.apply(saved, glass);
        for (String key : saved.keySet()) if (key.startsWith("fluid_cloud_capsule_"))
            check(java.util.Objects.equals(saved.get(key), capsuleSettings.get(key)), "glass preserves current top capsule values: " + key);
        saved.putString("font_scope_assignments", "{\"statusbar_clock\":\"system\",\"lockscreen_text\":\"custom:kept\"}");
        Bundle clock = CustomPluginMaterialIsolation.apply(saved, state("clock_glass"));
        JSONObject fonts = new JSONObject(clock.getString("font_scope_assignments"));
        check("system".equals(fonts.getString("lockscreen_text")) && "system".equals(fonts.getString("statusbar_clock")), "plugin clock isolates only its native font source");
        check(clock.getBoolean("qs_appearance_enabled"), "clock glass does not claim QS materials");
        unchanged(saved, CustomPluginMaterialIsolation.apply(saved, state("clock_glass", "clock_lock_glass_off").put("clock_lock_glass", false)), "desktop-only clock leaves host lock scope intact");
        keptSwitches();
        saved.putBoolean("module_safe_mode", true);
        unchanged(saved, CustomPluginMaterialIsolation.apply(saved, glass), "safe mode does not claim materials");
        check(CustomPluginMaterialIsolation.apply(null, glass) == null, "absent host snapshot remains absent");
        System.out.println("CustomPluginMaterialIsolationCheck: " + checks + " checks passed");
    }
}
