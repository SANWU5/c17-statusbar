// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/** Monotonic charging animation. Holds need one wake-up; fades run at 30 fps. */
public final class ChargeCycle {
    private boolean charging;
    private long started;
    private long hold = 3000, fade = 1000;

    public void configure(float holdSeconds, float fadeSeconds) {
        hold = NumericPolicy.milliseconds(holdSeconds, 3f);
        fade = NumericPolicy.milliseconds(fadeSeconds, 1f);
    }

    public void setCharging(boolean next, long uptime) {
        if (next && !charging) started = uptime;
        charging = next;
    }

    public boolean isCharging() { return charging; }

    private long position(long uptime) {
        long period = 2 * (hold + fade);
        return period == 0 ? 0 : Math.max(0, uptime - started) % period;
    }

    /** Start with a bolt. Complementary opacity keeps both endpoints unambiguous. */
    public float boltOpacity(long uptime) {
        if (!charging) return 0f;
        if (hold + fade == 0) return 1f;
        long phase = position(uptime);
        if (phase < hold) return 1f;
        if (phase < hold + fade) return 1f - ease((phase - hold) / (float) fade);
        if (phase < 2 * hold + fade) return 0f;
        return ease((phase - 2 * hold - fade) / (float) fade);
    }

    private static float ease(float progress) {
        return (float) ((1 - Math.cos(Math.PI * progress)) / 2);
    }

    public long nextDelay(long uptime) {
        if (!charging || hold + fade == 0) return Long.MAX_VALUE;
        long phase = position(uptime);
        if (phase < hold) return hold - phase;
        if (phase < hold + fade) return Math.min(33, hold + fade - phase);
        if (phase < 2 * hold + fade) return 2 * hold + fade - phase;
        return Math.min(33, 2 * (hold + fade) - phase);
    }
}
