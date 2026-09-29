package dev.puitheme;

/** Monotonic charging animation. Holds need one wake-up; fades run at 30 fps. */
public final class ChargeCycle {
    private boolean charging;
    private long started;
    private long hold = 3000, fade = 1000;

    public void configure(float holdSeconds, float fadeSeconds) {
        hold = milliseconds(holdSeconds, 1f, 10f, 3f);
        fade = milliseconds(fadeSeconds, .15f, 3f, 1f);
    }

    private static long milliseconds(float seconds, float min, float max, float fallback) {
        if (Float.isNaN(seconds) || Float.isInfinite(seconds)) seconds = fallback;
        return Math.round(Math.max(min, Math.min(max, seconds)) * 1000f);
    }

    public void setCharging(boolean next, long uptime) {
        if (next && !charging) started = uptime;
        charging = next;
    }

    public boolean isCharging() { return charging; }

    private long position(long uptime) {
        return Math.max(0, uptime - started) % (2 * (hold + fade));
    }

    /** Start with a bolt. Complementary opacity keeps both endpoints unambiguous. */
    public float boltOpacity(long uptime) {
        if (!charging) return 0f;
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
        if (!charging) return Long.MAX_VALUE;
        long phase = position(uptime);
        if (phase < hold) return hold - phase;
        if (phase < hold + fade) return Math.min(33, hold + fade - phase);
        if (phase < 2 * hold + fade) return 2 * hold + fade - phase;
        return Math.min(33, 2 * (hold + fade) - phase);
    }
}
