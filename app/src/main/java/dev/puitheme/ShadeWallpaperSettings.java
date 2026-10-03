// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Each native panel mode and orientation has an independent, opt-in image. */
public final class ShadeWallpaperSettings {
    public static final String MASTER = "shade_wallpaper_enabled";
    public static final String UNAVAILABLE_REASON = "未完成的开发";
    public static final String CLASSIC_PORTRAIT = "classic_portrait";
    public static final String CLASSIC_LANDSCAPE = "classic_landscape";
    public static final String NOTIFICATION_PORTRAIT = "notification_portrait";
    public static final String NOTIFICATION_LANDSCAPE = "notification_landscape";
    public static final String CONTROL_PORTRAIT = "control_portrait";
    public static final String CONTROL_LANDSCAPE = "control_landscape";
    public static final List<String> SCENES = Collections.unmodifiableList(Arrays.asList(
            CLASSIC_PORTRAIT, CLASSIC_LANDSCAPE, NOTIFICATION_PORTRAIT,
            NOTIFICATION_LANDSCAPE, CONTROL_PORTRAIT, CONTROL_LANDSCAPE));
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String, Boolean> booleans = new LinkedHashMap<>();
        Map<String, Float> numbers = new LinkedHashMap<>();
        Map<String, String> strings = new LinkedHashMap<>();
        booleans.put(MASTER, false);
        for (String scene : SCENES) {
            booleans.put(enabledKey(scene), false);
            numbers.put(brightnessKey(scene), 100f);
            strings.put(revisionKey(scene), "");
        }
        BOOLEANS = Collections.unmodifiableMap(booleans);
        NUMBERS = Collections.unmodifiableMap(numbers);
        STRINGS = Collections.unmodifiableMap(strings);
    }
    private ShadeWallpaperSettings() { }
    /** Suspended development cannot be enabled by old preferences, imports or startup snapshots. */
    public static boolean available() { return false; }
    public static String unavailableReason() { return available() ? "" : UNAVAILABLE_REASON; }
    public static String unavailableReason(String key) {
        return key != null && key.startsWith("shade_wallpaper_") ? unavailableReason() : "";
    }
    public static boolean validScene(String scene) { return SCENES.contains(scene); }
    public static String enabledKey(String scene) { return prefix(scene) + "enabled"; }
    public static String brightnessKey(String scene) { return prefix(scene) + "brightness"; }
    public static String revisionKey(String scene) { return prefix(scene) + "revision"; }
    private static String prefix(String scene) {
        if (!validScene(scene)) throw new IllegalArgumentException("Unknown wallpaper scene");
        return "shade_wallpaper_" + scene + "_";
    }
    public static boolean validRevision(String revision) {
        if (revision == null || revision.length() != 64) return false;
        for (int i = 0; i < revision.length(); i++) {
            char c = revision.charAt(i);
            if (c < '0' || c > '9') if (c < 'a' || c > 'f') return false;
        }
        return true;
    }
    public static float brightness(Object value) {
        float result = value instanceof Number ? ((Number) value).floatValue() : 100f;
        if (!Float.isFinite(result)) result = 100f;
        return Math.max(0f, Math.min(200f, result));
    }
    public static String scene(String mode, boolean control, boolean landscape) {
        if ("classic".equals(mode)) return landscape ? CLASSIC_LANDSCAPE : CLASSIC_PORTRAIT;
        if (!"separate".equals(mode)) return null;
        if (control) return landscape ? CONTROL_LANDSCAPE : CONTROL_PORTRAIT;
        return landscape ? NOTIFICATION_LANDSCAPE : NOTIFICATION_PORTRAIT;
    }
    public static boolean enabled(Bundle values, String scene) {
        return available() && validScene(scene) && values != null && Boolean.TRUE.equals(values.get(enabledKey(scene)));
    }
    public static String title(String scene) {
        if (CLASSIC_PORTRAIT.equals(scene)) return "经典下拉 · 竖屏";
        if (CLASSIC_LANDSCAPE.equals(scene)) return "经典下拉 · 横屏";
        if (NOTIFICATION_PORTRAIT.equals(scene)) return "分离式通知栏 · 竖屏";
        if (NOTIFICATION_LANDSCAPE.equals(scene)) return "分离式通知栏 · 横屏";
        if (CONTROL_PORTRAIT.equals(scene)) return "分离式控制中心 · 竖屏";
        if (CONTROL_LANDSCAPE.equals(scene)) return "分离式控制中心 · 横屏";
        throw new IllegalArgumentException("Unknown wallpaper scene");
    }
}
