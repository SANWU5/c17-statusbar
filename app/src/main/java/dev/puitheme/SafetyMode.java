// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Bundle;

/** Disables runtime ownership without changing any saved feature, color, font or layout value. */
public final class SafetyMode {
    private SafetyMode() { }

    public static boolean enabled(Bundle settings) {
        return settings != null && Boolean.TRUE.equals(settings.get(StatusBarSettings.SAFE_MODE));
    }

    /** Apply this copy to consumers; keep the original snapshot for persistence and rollback. */
    public static Bundle runtimeSettings(Bundle saved) {
        if (saved == null) throw new IllegalArgumentException("Missing settings snapshot");
        Bundle runtime = new Bundle(saved);
        runtime.putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN, true);
        if (!enabled(saved)) return runtime;
        for (String group : FeatureOptions.GROUPS) runtime.putBoolean(FeatureOptions.masterKey(group), false);
        runtime.putBoolean(QsTileAppearance.MASTER, false);
        runtime.putBoolean(QsTileCorners.MASTER, false);
        runtime.putBoolean(QsMediaAppearance.MASTER, false);
        runtime.putBoolean(NotificationClearAppearance.MASTER, false);
        runtime.putBoolean(NotificationBigClockSettings.MASTER, false);
        runtime.putBoolean(NotificationIconArea.MASTER, false);
        return runtime;
    }
}
