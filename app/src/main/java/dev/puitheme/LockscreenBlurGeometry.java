// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** Continuous top-to-bottom geometry; no segmented crops, bitmap, timer or UI state. */
final class LockscreenBlurGeometry {
    static final int MAX_RADIUS_PX = 120;
    static final class Plan {
        final int[] top, bottom;
        final float[] weight;
        final int radius;
        final int core, end;
        Plan(int[] top, int[] bottom, float[] weight, int radius, int core, int end) {
            this.top = top; this.bottom = bottom; this.weight = weight; this.radius = radius;
            this.core = core; this.end = end;
        }
        int size() { return top.length; }
        float strength(float y) {
            if (!Float.isFinite(y) || end == 0 || y >= end) return 0f;
            if (y <= core) return 1f;
            return 1f - smooth((y - core) / Math.max(1f, end - core));
        }
    }
    static float finite(float value, float fallback, float low, float high) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : fallback;
    }
    static float smooth(float value) {
        float p = finite(value, 0f, 0f, 1f);
        return p * p * (3f - 2f * p);
    }
    static Plan plan(int height, float density, float radiusDp, float rangeDp, float transitionDp) {
        int h = Math.max(0, height);
        float d = finite(density, 1f, .25f, 8f);
        int core = Math.min(h, Math.max(0, Math.round(finite(rangeDp, 56f, 0f, 640f) * d)));
        int tail = Math.min(h - core, Math.max(0, Math.round(finite(transitionDp, 32f, 0f, 320f) * d)));
        int radius = Math.min(MAX_RADIUS_PX, Math.max(0, Math.round(finite(radiusDp, 16f, 0f, 80f) * d)));
        int end = Math.min(h, core + tail);
        if (end == 0) return new Plan(new int[0], new int[0], new float[0], radius, 0, 0);
        return new Plan(new int[]{0}, new int[]{end}, new float[]{1f}, radius, core, end);
    }
    /** Never extinguish the AOD wallpaper blur merely because doze reached one. */
    static float visibility(boolean stateKnown, boolean keyguard, boolean dozing,
                            boolean keyguardKnown, boolean showing, boolean occluded,
                            float dismissAmount) {
        if (!stateKnown || (!keyguard && !dozing) || occluded || (keyguardKnown && !showing && !dozing)) return 0f;
        return dozing ? 1f : 1f - smooth(dismissAmount);
    }
    /**
     * A compositor blur region must not punch through the native screen-off scrim.
     * The final drawable alpha is already the paint alpha: tint's ARGB alpha is
     * not multiplied again (ScrimDrawable.draw overwrites it with setAlpha).
     * Known transparent AOD scrims retain wallpaper blur. Unknown OEM scrims use
     * the system's eased doze amount, never a separately timed animation.
     */
    static float wallpaperVisibility(boolean dozing, float easedDoze,
                                     float behindOpacity, float frontOpacity) {
        if (Float.isFinite(behindOpacity) && Float.isFinite(frontOpacity))
            return (1f - finite(behindOpacity, 1f, 0f, 1f))
                    * (1f - finite(frontOpacity, 1f, 0f, 1f));
        return Float.isFinite(easedDoze) ? 1f - finite(easedDoze, 1f, 0f, 1f)
                : dozing ? 0f : 1f;
    }
}
