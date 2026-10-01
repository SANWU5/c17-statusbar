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
        // Both page endpoints show one row. Leaving goes up/out; arriving goes down/in.
        // Reading the same spring coordinate also handles reversal and cancelled drags.
        return smooth(Math.abs(2f * bounded(pageFraction) - 1f));
    }

    public static float reveal(float shadeFraction, float pageFraction) {
        return verticalReveal(shadeFraction) * horizontalReveal(pageFraction);
    }

    public static float reveal(float shadeFraction, float pageFraction, float tileFraction) {
        return reveal(shadeFraction, pageFraction) * horizontalReveal(tileFraction);
    }

    public static float verticalReveal(float shadeFraction) { return smooth(bounded(shadeFraction / .65f)); }

    public static float offsetY(float shadeFraction, float pageFraction, float density) {
        float scale = finite(density) && density > 0f ? density : 1f;
        // Both native springs move away from the fixed Phone destination toward the
        // top. Multiplying reveal bounds combined travel to the same 18dp distance.
        return -ENTRY_TRAVEL_DP * scale * (1f - reveal(shadeFraction, pageFraction));
    }

    public static float offsetY(float shadeFraction, float pageFraction, float tileFraction, float density) {
        float scale = finite(density) && density > 0f ? density : 1f;
        return -ENTRY_TRAVEL_DP * scale * (1f - reveal(shadeFraction, pageFraction, tileFraction));
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
