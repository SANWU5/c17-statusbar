// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pull-down panel's own wallpaper. Each native panel mode and orientation has an independent, opt-in image: in the separate mode a
 * portrait and a landscape one for the notification centre and for the control centre, in the classic mode one per orientation. Every
 * scene has its brightness and its blur; the notification centre's scenes also have a "clock zone": the band behind the big clock where
 * the picture is blurred differently, so the clock stays readable and its glass shows what is really behind it.
 */
public final class ShadeWallpaperSettings {
    /** The first release's one switch. Not shown or read any more: each orientation has its own page switch (below). An old "on" carries over to both. */
    public static final String MASTER = "shade_wallpaper_enabled";
    /** The portrait and the landscape page's switches: every scene of that orientation waits for its page. */
    public static final String PORTRAIT_MASTER = "shade_wallpaper_portrait_enabled", LANDSCAPE_MASTER = "shade_wallpaper_landscape_enabled";
    /** The picture goes from sharp to blurred as the panel is pulled down, like the system's own blur. Off: blurred from the first pixel. Portrait; landscape has its own. */
    public static final String BLUR_WITH_PULL = "shade_wallpaper_blur_with_pull_enabled";
    public static final String LANDSCAPE_BLUR_WITH_PULL = "shade_wallpaper_landscape_blur_with_pull_enabled";
    /** Swiping between the two panels: off, the two pictures cross-fade behind the pages; on, they slide with the pages. Portrait; landscape has its own. */
    public static final String SWIPE_SLIDE = "shade_wallpaper_swipe_slide_enabled";
    public static final String LANDSCAPE_SWIPE_SLIDE = "shade_wallpaper_landscape_swipe_slide_enabled";
    /** The first release's "manage pictures" row key: no longer shown, still a known setting. */
    public static final String LEGACY_MANAGE = "shade_wallpaper_manage";
    public static final String CLASSIC_PORTRAIT = "classic_portrait";
    public static final String CLASSIC_LANDSCAPE = "classic_landscape";
    public static final String NOTIFICATION_PORTRAIT = "notification_portrait";
    public static final String NOTIFICATION_LANDSCAPE = "notification_landscape";
    public static final String CONTROL_PORTRAIT = "control_portrait";
    public static final String CONTROL_LANDSCAPE = "control_landscape";
    public static final List<String> SCENES = Collections.unmodifiableList(Arrays.asList(
            CLASSIC_PORTRAIT, CLASSIC_LANDSCAPE, NOTIFICATION_PORTRAIT,
            NOTIFICATION_LANDSCAPE, CONTROL_PORTRAIT, CONTROL_LANDSCAPE));
    /** The scenes where the big clock lives. */
    public static final List<String> ZONE_SCENES = Collections.unmodifiableList(Arrays.asList(NOTIFICATION_PORTRAIT, NOTIFICATION_LANDSCAPE));
    public static final float MAX_BLUR_DP = 64f, MAX_ZONE_HEIGHT_DP = 800f, MAX_ZONE_FEATHER_DP = 400f;
    public static final float DEFAULT_ZONE_BLUR_DP = 24f, DEFAULT_ZONE_HEIGHT_DP = 240f, DEFAULT_ZONE_FEATHER_DP = 72f;
    /** What the zone does to the picture under the clock besides blurring it: a signed lift (+ toward white, - toward black), saturation, a tint. */
    public static final float DEFAULT_ZONE_LIFT = 0f, DEFAULT_ZONE_SATURATION = 100f, DEFAULT_ZONE_TINT_STRENGTH = 0f;
    public static final int DEFAULT_ZONE_TINT = 0xffffffff;
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, String> STRINGS;
    public static final Map<String, Integer> COLORS;
    static {
        Map<String, Boolean> booleans = new LinkedHashMap<>();
        Map<String, Float> numbers = new LinkedHashMap<>();
        Map<String, String> strings = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        booleans.put(MASTER, false);
        booleans.put(PORTRAIT_MASTER, false);
        booleans.put(LANDSCAPE_MASTER, false);
        booleans.put(BLUR_WITH_PULL, true);
        booleans.put(LANDSCAPE_BLUR_WITH_PULL, true);
        booleans.put(SWIPE_SLIDE, false);
        booleans.put(LANDSCAPE_SWIPE_SLIDE, false);
        for (String scene : SCENES) {
            booleans.put(enabledKey(scene), false);
            numbers.put(brightnessKey(scene), 100f);
            numbers.put(blurKey(scene), 0f);
            strings.put(revisionKey(scene), "");
            if (hasZone(scene)) {
                booleans.put(zoneEnabledKey(scene), false);
                booleans.put(zoneFollowKey(scene), true);
                numbers.put(zoneBlurKey(scene), DEFAULT_ZONE_BLUR_DP);
                numbers.put(zoneHeightKey(scene), DEFAULT_ZONE_HEIGHT_DP);
                numbers.put(zoneFeatherKey(scene), DEFAULT_ZONE_FEATHER_DP);
                numbers.put(zoneLiftKey(scene), DEFAULT_ZONE_LIFT);
                numbers.put(zoneSaturationKey(scene), DEFAULT_ZONE_SATURATION);
                numbers.put(zoneTintStrengthKey(scene), DEFAULT_ZONE_TINT_STRENGTH);
                colors.put(zoneTintKey(scene), DEFAULT_ZONE_TINT);
            }
        }
        BOOLEANS = Collections.unmodifiableMap(booleans);
        NUMBERS = Collections.unmodifiableMap(numbers);
        STRINGS = Collections.unmodifiableMap(strings);
        COLORS = Collections.unmodifiableMap(colors);
    }
    /** The keys of the first release of this feature (schema 66): the master and each scene's switch, brightness and picture. */
    static Map<String, Object> firstReleaseDefaults() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(MASTER, false);
        for (String scene : SCENES) values.put(enabledKey(scene), false);
        for (String scene : SCENES) values.put(brightnessKey(scene), 100f);
        for (String scene : SCENES) values.put(revisionKey(scene), "");
        return values;
    }
    private ShadeWallpaperSettings() { }
    /**
     * Device code162: the author asked for the pull-down wallpaper again (shelved since code126). False shelves it: it cannot be switched
     * on, whatever is saved is not applied, and its hooks and window are not installed.
     */
    private static final boolean OPEN = true;
    private static Boolean checkOverride;
    /** Check seam: the desktop checks run the feature open or shelved, whatever {@link #OPEN} says. */
    static void openForCheck(boolean value) { checkOverride = value; }
    public static boolean available() { return checkOverride != null ? checkOverride : OPEN; }
    public static String unavailableReason() { return available() ? "" : "下拉壁纸未完成，暂时不能开启"; }
    public static String unavailableReason(String key) { return key != null && key.startsWith("shade_wallpaper_") ? unavailableReason() : ""; }
    public static boolean validScene(String scene) { return SCENES.contains(scene); }
    /** The card has its own asset/settings; it is never a pull-down scene or page master. */
    public static boolean assetScene(String scene) { return validScene(scene)||QsCardSettings.wallpaperScene(scene); }
    public static boolean assetAvailable(String scene) { return QsCardSettings.wallpaperScene(scene)||available(); }
    public static String assetRevisionKey(String scene) { return QsCardSettings.wallpaperScene(scene)?QsCardSettings.WALLPAPER_REVISION:revisionKey(scene); }
    public static String assetEnabledKey(String scene) { return QsCardSettings.wallpaperScene(scene)?QsCardSettings.WALLPAPER_ENABLED:enabledKey(scene); }
    public static String assetMasterKey(String scene) { return QsCardSettings.wallpaperScene(scene)?null:masterKey(scene); }
    public static String assetTitle(String scene) { return QsCardSettings.wallpaperScene(scene)?"音乐卡片":title(scene); }
    /** The scene whose picture this setting key names (the picture card is shown on that key), or null. */
    public static String sceneOfRevisionKey(String key) {
        if (key == null) return null;
        for (String scene : SCENES) if (revisionKey(scene).equals(key)) return scene;
        return null;
    }
    public static boolean landscape(String scene) { return validScene(scene) && scene.endsWith("_landscape"); }
    public static String masterKey(boolean landscape) { return landscape ? LANDSCAPE_MASTER : PORTRAIT_MASTER; }
    public static String masterKey(String scene) { return masterKey(landscape(scene)); }
    public static String blurWithPullKey(boolean landscape) { return landscape ? LANDSCAPE_BLUR_WITH_PULL : BLUR_WITH_PULL; }
    public static String swipeSlideKey(boolean landscape) { return landscape ? LANDSCAPE_SWIPE_SLIDE : SWIPE_SLIDE; }
    public static boolean hasZone(String scene) { return ZONE_SCENES.contains(scene); }
    public static String enabledKey(String scene) { return prefix(scene) + "enabled"; }
    public static String brightnessKey(String scene) { return prefix(scene) + "brightness"; }
    public static String blurKey(String scene) { return prefix(scene) + "blur"; }
    public static String revisionKey(String scene) { return prefix(scene) + "revision"; }
    public static String zoneEnabledKey(String scene) { return zonePrefix(scene) + "enabled"; }
    public static String zoneFollowKey(String scene) { return zonePrefix(scene) + "follow"; }
    public static String zoneBlurKey(String scene) { return zonePrefix(scene) + "blur"; }
    public static String zoneHeightKey(String scene) { return zonePrefix(scene) + "height"; }
    public static String zoneFeatherKey(String scene) { return zonePrefix(scene) + "feather"; }
    public static String zoneLiftKey(String scene) { return zonePrefix(scene) + "lift"; }
    public static String zoneSaturationKey(String scene) { return zonePrefix(scene) + "saturation"; }
    public static String zoneTintKey(String scene) { return zonePrefix(scene) + "tint"; }
    public static String zoneTintStrengthKey(String scene) { return zonePrefix(scene) + "tint_strength"; }
    private static String prefix(String scene) {
        if (!validScene(scene)) throw new IllegalArgumentException("Unknown wallpaper scene");
        return "shade_wallpaper_" + scene + "_";
    }
    private static String zonePrefix(String scene) {
        if (!hasZone(scene)) throw new IllegalArgumentException("Scene without a clock zone");
        return prefix(scene) + "zone_";
    }
    public static boolean validRevision(String revision) {
        if (revision == null || revision.length() != 64) return false;
        for (int i = 0; i < revision.length(); i++) {
            char c = revision.charAt(i);
            if (c < '0' || c > '9') if (c < 'a' || c > 'f') return false;
        }
        return true;
    }
    private static float number(Object value, float fallback, float min, float max) {
        float result = value instanceof Number ? ((Number) value).floatValue() : fallback;
        if (!Float.isFinite(result)) result = fallback;
        return Math.max(min, Math.min(max, result));
    }
    public static float brightness(Object value) { return number(value, 100f, 0f, 200f); }
    public static float blur(Object value) { return number(value, 0f, 0f, MAX_BLUR_DP); }
    public static float zoneBlur(Object value) { return number(value, DEFAULT_ZONE_BLUR_DP, 0f, MAX_BLUR_DP); }
    public static float zoneHeight(Object value) { return number(value, DEFAULT_ZONE_HEIGHT_DP, 0f, MAX_ZONE_HEIGHT_DP); }
    public static float zoneFeather(Object value) { return number(value, DEFAULT_ZONE_FEATHER_DP, 0f, MAX_ZONE_FEATHER_DP); }
    public static float zoneLift(Object value) { return number(value, DEFAULT_ZONE_LIFT, -100f, 100f); }
    public static float zoneSaturation(Object value) { return number(value, DEFAULT_ZONE_SATURATION, 0f, 200f); }
    public static float zoneTintStrength(Object value) { return number(value, DEFAULT_ZONE_TINT_STRENGTH, 0f, 100f); }
    public static String scene(String mode, boolean control, boolean landscape) {
        if ("classic".equals(mode)) return landscape ? CLASSIC_LANDSCAPE : CLASSIC_PORTRAIT;
        if (!"separate".equals(mode)) return null;
        if (control) return landscape ? CONTROL_LANDSCAPE : CONTROL_PORTRAIT;
        return landscape ? NOTIFICATION_LANDSCAPE : NOTIFICATION_PORTRAIT;
    }
    /** A scene is on when its page is on and its own switch is on. */
    public static boolean enabled(Bundle values, String scene) {
        return validScene(scene) && values != null && Boolean.TRUE.equals(values.get(masterKey(scene)))
                && Boolean.TRUE.equals(values.get(enabledKey(scene)));
    }
    public static String title(String scene) {
        if (CLASSIC_PORTRAIT.equals(scene)) return "经典下拉 · 竖屏";
        if (CLASSIC_LANDSCAPE.equals(scene)) return "经典下拉 · 横屏";
        if (NOTIFICATION_PORTRAIT.equals(scene)) return "通知中心 · 竖屏";
        if (NOTIFICATION_LANDSCAPE.equals(scene)) return "通知中心 · 横屏";
        if (CONTROL_PORTRAIT.equals(scene)) return "控制中心 · 竖屏";
        if (CONTROL_LANDSCAPE.equals(scene)) return "控制中心 · 横屏";
        throw new IllegalArgumentException("Unknown wallpaper scene");
    }
}
