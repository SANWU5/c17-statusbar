// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** Recognizes only a short, single-pointer tap. Native drags and long presses stay native. */
final class NotificationStackTap {
    private boolean candidate;
    private float downX, downY, slopSquared;
    private long downTime;
    private int longPress;

    boolean event(int action, int pointers, float x, float y, long time,
            boolean onFoldedCard, float slop, int longPressTimeout) {
        if (action == 0) {
            candidate = onFoldedCard && pointers == 1 && finite(x) && finite(y)
                    && finite(slop) && slop >= 0 && longPressTimeout > 0;
            downX = x; downY = y; downTime = time;
            slopSquared = slop * slop; longPress = longPressTimeout;
            return false;
        }
        if (!candidate) return false;
        float dx = x - downX, dy = y - downY;
        if (!onFoldedCard || pointers != 1 || !finite(x) || !finite(y)
                || dx * dx + dy * dy > slopSquared || time < downTime
                || time - downTime >= longPress || action == 3 || action == 5 || action == 6) {
            candidate = false;
            return false;
        }
        if (action != 1) return false;
        candidate = false;
        return true;
    }
    void reset() { candidate = false; }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
