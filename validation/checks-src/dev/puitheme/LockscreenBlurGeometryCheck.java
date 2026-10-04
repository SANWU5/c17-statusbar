// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

/** Production pure-geometry checks; never substitutes for the device compositor/AOD checks. */
public final class LockscreenBlurGeometryCheck {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Lockscreen blur " + checks); }
    private static void near(float expected, float actual, float tolerance) {
        check(Float.isFinite(actual) && Math.abs(expected - actual) <= tolerance);
    }
    public static void main(String[] args) {
        defaults(); singleRegion(); continuousTransition(); invalidInputs(); visibility(); nativeDimming();
        System.out.println("Lockscreen blur geometry checks passed: " + checks);
    }
    private static void defaults() {
        LockscreenBlurGeometry.Plan p = LockscreenBlurGeometry.plan(3168, 3f, 16f, 56f, 32f);
        check(p.size() == 1); check(p.top[0] == 0); check(p.bottom[0] == 264);
        check(p.core == 168); check(p.end == 264); check(p.radius == 48); check(p.weight[0] == 1f);
        check(p.strength(0f) == 1f); check(p.strength(168f) == 1f);
        check(p.strength(216f) == .5f); check(p.strength(264f) == 0f);
        check(LockscreenBlurGeometry.plan(100, 1f, 0f, 20f, 0f).radius == 0);
        check(LockscreenBlurGeometry.plan(100, 1f, 10f, 0f, 0f).size() == 0);
        check(LockscreenBlurGeometry.plan(0, 3f, 10f, 56f, 32f).size() == 0);
        LockscreenBlurGeometry.Plan tailOnly = LockscreenBlurGeometry.plan(100, 1f, 16f, 0f, 32f);
        check(tailOnly.size() == 1 && tailOnly.core == 0 && tailOnly.end == 32);
        check(tailOnly.strength(0f) == 1f); check(tailOnly.strength(16f) == .5f);
        check(tailOnly.strength(32f) == 0f);
        LockscreenBlurGeometry.Plan noTail = LockscreenBlurGeometry.plan(100, 1f, 16f, 20f, 0f);
        check(noTail.size() == 1 && noTail.core == noTail.end && noTail.end == 20);
        check(noTail.strength(19.99f) == 1f); check(noTail.strength(20f) == 0f);
    }
    private static void singleRegion() {
        for (int h : new int[]{-1, 0, 1, 2, 13, 56, 101, 1000, 3168, Integer.MAX_VALUE}) {
            for (float range : new float[]{0f, .1f, 1f, 56f, 300f, 999999f}) {
                for (float transition : new float[]{0f, .1f, 1f, 2f, 32f, 400f}) {
                    LockscreenBlurGeometry.Plan p = LockscreenBlurGeometry.plan(h, 3f, 16f, range, transition);
                    int screen = Math.max(0, h);
                    check(p.size() == (p.end == 0 ? 0 : 1));
                    check(p.core >= 0 && p.core <= p.end && p.end <= screen);
                    check(p.bottom.length == p.size() && p.weight.length == p.size());
                    if (p.size() == 1) {
                        // A single crop spans both the full-blur core and its fade. No
                        // independent strip boundaries are sent to the compositor.
                        check(p.top[0] == 0 && p.bottom[0] == p.end && p.end > 0);
                        check(p.weight[0] == 1f);
                        check(p.strength(p.end) == 0f && p.strength(p.end + 1f) == 0f);
                    } else {
                        check(p.core == 0 && p.end == 0 && p.strength(0f) == 0f);
                    }
                }
            }
        }
    }
    private static void continuousTransition() {
        for (float density : new float[]{.25f, 1f, 3.5f, 4f, 8f}) {
            LockscreenBlurGeometry.Plan p = LockscreenBlurGeometry.plan(3168, density, 16f, 56f, 32f);
            check(p.end > p.core);
            near(1f, p.strength(p.core - .001f), .00001f);
            near(1f, p.strength(p.core), .00001f);
            near(1f, p.strength(p.core + .001f), .00001f);
            near(0f, p.strength(p.end - .001f), .00001f);
            check(p.strength(p.end) == 0f && p.strength(p.end + .001f) == 0f);
            float previous = 1f;
            for (int i = 0; i <= 1000; i++) {
                float t = i / 1000f;
                float y = p.core + (p.end - p.core) * t;
                float strength = p.strength(y);
                check(Float.isFinite(strength) && strength >= 0f && strength <= previous + .000001f);
                near(1f - (t * t * (3f - 2f * t)), strength, .00001f);
                previous = strength;
            }
            // Smoothstep flattens at each end rather than producing a visible step.
            near(1f, p.strength(p.core + (p.end - p.core) * .001f), .00001f);
            near(0f, p.strength(p.core + (p.end - p.core) * .999f), .00001f);
        }
    }
    private static void invalidInputs() {
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                -999999f, Float.MAX_VALUE, -0f}) {
            LockscreenBlurGeometry.Plan p = LockscreenBlurGeometry.plan(3168, value, value, value, value);
            check(p.size() <= 1); check(p.radius >= 0 && p.radius <= 120);
            check(p.core >= 0 && p.core <= p.end && p.end <= 3168);
            for (int i = 0; i < p.size(); i++) check(p.bottom[i] <= 3168 && p.top[i] >= 0);
        }
        LockscreenBlurGeometry.Plan defaults = LockscreenBlurGeometry.plan(3168,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        check(defaults.core == 56 && defaults.end == 88 && defaults.radius == 16);
        LockscreenBlurGeometry.Plan clipped = LockscreenBlurGeometry.plan(100, 8f,
                Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        check(clipped.core == 100 && clipped.end == 100 && clipped.radius == 120);
        check(clipped.strength(99f) == 1f && clipped.strength(100f) == 0f);
        check(clipped.strength(Float.NaN) == 0f);
        check(clipped.strength(Float.NEGATIVE_INFINITY) == 0f);
        check(clipped.strength(Float.POSITIVE_INFINITY) == 0f);
        check(clipped.strength(-1f) == 1f);
        check(LockscreenBlurGeometry.smooth(-1f) == 0f);
        check(LockscreenBlurGeometry.smooth(2f) == 1f);
        check(LockscreenBlurGeometry.smooth(.5f) == .5f);
        check(LockscreenBlurGeometry.smooth(Float.NaN) == 0f);
    }
    private static void visibility() {
        check(LockscreenBlurGeometry.visibility(false, true, true, false, true, false, 0f) == 0f);
        check(LockscreenBlurGeometry.visibility(true, false, false, true, false, false, 0f) == 0f);
        check(LockscreenBlurGeometry.visibility(true, true, false, true, true, true, 0f) == 0f);
        check(LockscreenBlurGeometry.visibility(true, true, false, true, false, false, 0f) == 0f);
        check(LockscreenBlurGeometry.visibility(true, true, false, true, true, false, 0f) == 1f);
        check(LockscreenBlurGeometry.visibility(true, true, false, true, true, false, 1f) == 0f);
        check(LockscreenBlurGeometry.visibility(true, true, false, true, true, false, .5f) == .5f);
        // Full AOD and a transient native showing=false still retain the wallpaper strip.
        check(LockscreenBlurGeometry.visibility(true, true, true, true, true, false, 1f) == 1f);
        check(LockscreenBlurGeometry.visibility(true, false, true, true, false, false, 1f) == 1f);
        check(LockscreenBlurGeometry.visibility(true, false, true, true, false, true, 0f) == 0f);
        float previous = 1f;
        for (int i = 0; i <= 100; i++) {
            float value = LockscreenBlurGeometry.visibility(true, true, false, true, true, false, i / 100f);
            check(value <= previous && value >= 0f); previous = value;
        }
    }
    private static void nativeDimming() {
        // Full native black AOD cannot leave a bright wallpaper hole behind it.
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 1f, 1f, 0f) == 0f);
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 1f, 0f, 1f) == 0f);
        // Native wallpaper-capable AOD is retained, not guessed from dozing=true.
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 1f, 0f, 0f) == 1f);
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 1f, .25f, .5f) == .375f);
        // Native final opacity takes priority over the animator target/eased clock.
        check(LockscreenBlurGeometry.wallpaperVisibility(false, 1f, .5f, 0f) == .5f);
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 0f, 1f, 0f) == 0f);
        // Unknown/retired/hidden OEM scrims must not authorize bright full AOD.
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 1f, Float.NaN, 0f) == 0f);
        check(LockscreenBlurGeometry.wallpaperVisibility(true, Float.NaN, Float.NaN, Float.NaN) == 0f);
        check(LockscreenBlurGeometry.wallpaperVisibility(false, Float.NaN, Float.NaN, Float.NaN) == 1f);
        check(LockscreenBlurGeometry.wallpaperVisibility(false, .25f, Float.NaN, Float.NaN) == .75f);
        float old = 1f;
        for (int i = 0; i <= 1000; i++) {
            float alpha = i / 1000f;
            float known = LockscreenBlurGeometry.wallpaperVisibility(true, 1f, alpha, 0f);
            float fallback = LockscreenBlurGeometry.wallpaperVisibility(true, alpha, Float.NaN, Float.NaN);
            check(known <= old && known >= 0f); check(known == fallback);
            check(LockscreenBlurGeometry.wallpaperVisibility(false, alpha, Float.NaN, Float.NaN) == fallback);
            old = known;
        }
        for (int i = 1000; i >= 0; i--) {
            float alpha = i / 1000f;
            float value = LockscreenBlurGeometry.wallpaperVisibility(false, alpha, alpha, 0f);
            check(value >= old && value <= 1f); old = value;
        }
        check(LockscreenBlurGeometry.wallpaperVisibility(true, 1f, -1f, 2f) == 0f);
    }
}
