// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/**
 * Separates stored settings from the editor's suggested slider ranges.
 * Rendering guards below protect Android and Skia; they never rewrite a preference.
 */
public final class NumericPolicy {
    public static final float MAX_DRAW_PIXELS = 1048576f;
    public static final float MAX_LAYOUT_PIXELS = 65535f;
    public static final float MAX_TEXT_PIXELS = 4096f;
    private static final long MAX_ANIMATION_MILLIS = Long.MAX_VALUE / 8;

    private NumericPolicy() { }

    /** min/max describe the slider. Only its signed/nonnegative meaning applies here. */
    public static float number(Object stored, float fallback, float min, float max) {
        float value = finite(stored, fallback);
        return min < 0f ? value : Math.max(0f, value);
    }

    /** Read a runtime setting without imposing an editor slider's upper or lower range. */
    public static float setting(String key, Object stored, float fallback) {
        float value = finite(stored, fallback);
        if (NotificationBigClockSettings.positiveSize(key) && value <= 0f)
            return Math.max(Float.MIN_NORMAL, finite(fallback, 1f));
        if (key != null && (key.equals("font_weight") || key.endsWith("_weight")))
            return Math.max(1f, Math.min(1000f, value));
        return signed(key) ? value : Math.max(0f, value);
    }

    /** Positions and spacing may move in either direction. */
    public static boolean signed(String key) {
        return key != null && (NativeStatusIcons.X.equals(key) || NativeStatusIcons.Y.equals(key)
                || key.endsWith("offset_x") || key.endsWith("offset_y")
                || key.endsWith("_spacing") || key.endsWith("_line_gap"));
    }

    public static float finite(Object stored, float fallback) {
        float value = stored instanceof Number ? ((Number) stored).floatValue() : fallback;
        if (Float.isNaN(value) || Float.isInfinite(value)) value = fallback;
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f : value;
    }

    /** A canvas coordinate; multiply in double precision before applying the safe pixel limit. */
    public static float pixels(float dp, float density) {
        return drawPixels((double) finite(dp, 0f) * validDensity(density));
    }

    public static float pixels(double pixels) {
        return drawPixels(pixels);
    }

    public static float drawPixels(double pixels) {
        if (Double.isNaN(pixels)) return 0f;
        return (float) Math.max(-MAX_DRAW_PIXELS, Math.min(MAX_DRAW_PIXELS, pixels));
    }

    /** A measured dimension; huge saved values must not create huge native text/layout objects. */
    public static float layoutPixels(float dp, float density) {
        return (float) Math.max(0d, Math.min(MAX_LAYOUT_PIXELS,
                (double) finite(dp, 0f) * validDensity(density)));
    }

    public static int layoutPixels(double pixels) {
        if (Double.isNaN(pixels)) return 0;
        return (int) Math.round(Math.max(0d, Math.min(MAX_LAYOUT_PIXELS, pixels)));
    }

    public static float layoutDp(float dp, float density) {
        return layoutPixels(dp, density) / validDensity(density);
    }

    public static float textPixels(double pixels) {
        if (Double.isNaN(pixels)) return 0f;
        return (float) Math.max(0d, Math.min(MAX_TEXT_PIXELS, pixels));
    }

    /** Scale a source glyph/icon without overflowing its canvas transform. Zero remains zero. */
    public static float scale(float factor, float sourcePixels) {
        return scale(factor, 1f, sourcePixels);
    }

    public static float scale(double factor, float sourcePixels) {
        if (Double.isNaN(factor)) factor = 1d;
        double extent = Math.max(1d, Math.abs((double) finite(sourcePixels, 1f)));
        return (float) Math.min(MAX_DRAW_PIXELS / extent, Math.max(0d, factor));
    }

    public static float scale(float first, float second, float sourcePixels) {
        double factor = (double) Math.max(0f, finite(first, 1f))
                * Math.max(0f, finite(second, 1f));
        return scale(factor, sourcePixels);
    }

    /** Timers use their requested duration, with only a long-arithmetic overflow guard. */
    public static long milliseconds(float seconds, float fallback) {
        double millis = Math.max(0d, finite(seconds, fallback)) * 1000d;
        if (millis <= 0d) return 0L;
        return Math.max(1L, Math.min(MAX_ANIMATION_MILLIS, Math.round(millis)));
    }

    private static float validDensity(float density) {
        return Float.isNaN(density) || Float.isInfinite(density) || density <= 0f ? 1f : density;
    }
}
