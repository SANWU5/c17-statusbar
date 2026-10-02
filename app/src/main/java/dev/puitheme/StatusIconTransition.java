// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/** Pure policy for one shade icon row driven by native vertical and horizontal springs. */
public final class StatusIconTransition {
    public static final int RIGHT = 0, CLOCK = 1, NOTIFICATIONS = 2;
    public static final float ENTRY_TRAVEL_DP = 18f, CLOSING_LEFT_FRACTION = .2f;
    private StatusIconTransition() { }

    public static float pageFraction(float logicalOffset, float pageWidth) {
        return finite(logicalOffset) && finite(pageWidth) && pageWidth > 0f
                ? bounded(Math.abs(logicalOffset) / pageWidth) : 0f;
    }

    public static float horizontalReveal(float pageFraction) {
        // Both page endpoints show one row; the native spring owns reversal/cancellation.
        // Reading the same spring coordinate also handles reversal and cancelled drags.
        return smooth(Math.abs(2f * bounded(pageFraction) - 1f));
    }

    public static float reveal(float shadeFraction, float pageFraction) {
        return horizontalReveal(pageFraction);
    }

    public static float reveal(float shadeFraction, float pageFraction, float tileFraction) {
        return reveal(shadeFraction, pageFraction) * horizontalReveal(tileFraction);
    }

    // A vertical drag only hands ownership between the Phone window and the shade slot.
    // Neither its alpha nor its final geometry depends on vertical expansion.
    public static float verticalReveal(float shadeFraction) { return 1f; }

    public static float offsetY(float shadeFraction, float pageFraction, float density) {
        return travelPixels(forwardTravel(pageFraction), density);
    }

    public static float offsetY(float shadeFraction, float pageFraction, float tileFraction, float density) {
        return travelPixels(forwardTravel(pageFraction) + forwardTravel(tileFraction), density);
    }

    private static float forwardTravel(float pageFraction) {
        float p = bounded(pageFraction), distance = 1f - horizontalReveal(p);
        return p <= .5f ? distance : -distance;
    }

    public static float travelPixels(float travel, float density) {
        float scale = finite(density) && density > 0f ? density : 1f;
        return ENTRY_TRAVEL_DP * scale * Math.max(-1f, Math.min(1f, finite(travel) ? travel : 0f));
    }

    /**
     * Keep a gesture's origin even when its velocity reverses. Departure travels down
     * to +18dp; arrival starts at -18dp and travels down to the same destination.
     * The sign is reset only at the transparent midpoint, never on a direction change.
     */
    public static final class HorizontalTravel {
        private final boolean wrapping;
        private boolean started, boundary = true, fromEnd;
        public HorizontalTravel(boolean wrapping) { this.wrapping = wrapping; }
        public float fraction(float pageFraction) {
            float p = bounded(pageFraction);
            if (!started) { started = true; fromEnd = p > .5f; }
            if (p == 0f || p == 1f) {
                fromEnd = p == 1f; boundary = true;
                return 0f;
            }
            if (boundary) {
                // Inner tile phases wrap to zero at every page. A drag toward the
                // previous page starts just below one rather than just above zero.
                if (wrapping) fromEnd = p > .5f;
                boundary = false;
            }
            float distance = 1f - horizontalReveal(p);
            boolean leaving = fromEnd ? p >= .5f : p <= .5f;
            return leaving ? distance : -distance;
        }
        public void reset() { started = false; boundary = true; fromEnd = false; }
    }

    public static float anchorX(int screenX, int anchorWidth, int hostScreenX, int rowWidth) {
        return (float) screenX + anchorWidth - hostScreenX - rowWidth;
    }

    public static float anchorY(int screenY, int anchorHeight, int hostScreenY, int rowHeight) {
        return (float) screenY - hostScreenY + (anchorHeight - rowHeight) * .5f;
    }

    public static boolean suppressCopy(boolean owned, boolean drawingOwnedRow, int kind,
            boolean sameShade, boolean knownSource, boolean matchingRightSource,
            boolean phoneLeftSource, float shadeFraction) {
        if (!owned || drawingOwnedRow || !sameShade || !knownSource) return false;
        if (kind == RIGHT) return matchingRightSource;
        return (kind == CLOCK || kind == NOTIFICATIONS) && phoneLeftSource
                && finite(shadeFraction) && shadeFraction > CLOSING_LEFT_FRACTION;
    }

    private static float bounded(float value) { return finite(value) ? Math.max(0f, Math.min(1f, value)) : 0f; }
    private static float smooth(float value) { return value * value * (3f - 2f * value); }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
