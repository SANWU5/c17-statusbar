// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** Newly prepared artwork fades in from its own immediately sampled solid color. */
final class QsMediaTransition {
    static final long DIRECT_MS = 250L;
    static final long IN_MS = 240L;
    private QsMediaTransition() { }

    static float next(long elapsed, boolean fromSolid) {
        return smooth(elapsed, fromSolid ? IN_MS : DIRECT_MS);
    }

    static float previous(long elapsed, boolean fromSolid) {
        return 1f - next(elapsed, fromSolid);
    }

    private static float smooth(long elapsed, long duration) {
        if (elapsed <= 0L) return 0f;
        if (elapsed >= duration) return 1f;
        float time = elapsed / (float) duration;
        return time * time * (3f - 2f * time);
    }
}
