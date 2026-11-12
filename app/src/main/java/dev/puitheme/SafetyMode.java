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
        Bundle runtime = SettingsSnapshot.runtimeCopy(saved);
        if (!enabled(saved)) return runtime;
        for (String key : new String[]{FluidCloudAccent.LOCK_CUSTOM,FluidCloudAccent.LOCK_GRADIENT,
                FluidCloudAccent.AOD_CUSTOM,FluidCloudAccent.AOD_GRADIENT,FluidCloudStyle.NOTIFICATION_FOLLOW,
                FluidCloudStyle.LOCK_CAPSULE,FluidCloudStyle.SCOPE_LOCK,LockNotificationSettings.UNIFY_AOD,
                AodNotificationBackground.ENABLED}) runtime.putBoolean(key,false);
        for (String group : FeatureOptions.GROUPS) runtime.putBoolean(FeatureOptions.masterKey(group), false);
        runtime.putBoolean(QsTileAppearance.MASTER, false);
        runtime.putBoolean(IconManagementSettings.ENABLED, false);
        runtime.putBoolean(QsTileFullFill.ENABLED, false);
        runtime.putBoolean(QsTileCorners.MASTER, false);
        runtime.putBoolean(QsMediaAppearance.MASTER, false);
        runtime.putBoolean(QsWifiAlias.ENABLED, false);
        for(String key:QsCardSettings.BOOLEANS.keySet())runtime.putBoolean(key,false);
        runtime.putFloat(QsCardSettings.GRID_ROWS,4f);runtime.putString(QsCardSettings.SMALL_MODE,"always");
        runtime.putBoolean(NotificationClearAppearance.MASTER, false);
        runtime.putBoolean(NotificationBigClockSettings.MASTER, false);
        runtime.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER, false);
        runtime.putBoolean(NotificationGroupStack.MASTER, false);
        runtime.putBoolean(NotificationMiniStack.ENABLED, false);
        runtime.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,false);
        runtime.putBoolean(NativeDataActivity.MASTER,false);
        runtime.putBoolean(NotificationIconArea.MASTER, false);
        runtime.putBoolean(NetworkIconOrder.SWAP, false);
        runtime.putBoolean(C17HighlightRemoval.ENABLED, false);
        runtime.putBoolean(C17HighlightRemoval.BACKGROUND_ENABLED, false);
        runtime.putBoolean(NativeStatusIcons.MASTER, false);
        return runtime;
    }
}
