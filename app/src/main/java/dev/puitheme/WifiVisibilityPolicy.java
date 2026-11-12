// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

/** Per-native-slot visibility ownership. Never stores a module-produced request as native. */
final class WifiVisibilityPolicy {
    private static final int VISIBLE = 0, GONE = 8;
    private int nativeVisibility;
    private int moduleWrites;

    WifiVisibilityPolicy(int nativeVisibility) { this.nativeVisibility = nativeVisibility; }

    int requested(int visibility, boolean hideWifi, boolean legacyLabel) {
        if (moduleWrites == 0) nativeVisibility = visibility;
        return target(hideWifi, legacyLabel);
    }

    int target(boolean hideWifi, boolean legacyLabel) {
        if (legacyLabel) return nativeVisibility == GONE ? VISIBLE : nativeVisibility;
        return hideWifi ? GONE : nativeVisibility;
    }

    static boolean iconVisible(boolean nativeVisible, boolean hideWifi, boolean legacyLabel) {
        return legacyLabel || nativeVisible && !hideWifi;
    }

    void beginModuleWrite() { moduleWrites++; }
    void endModuleWrite() {
        if (moduleWrites <= 0) throw new IllegalStateException("Unbalanced Wi-Fi visibility write");
        moduleWrites--;
    }
}
