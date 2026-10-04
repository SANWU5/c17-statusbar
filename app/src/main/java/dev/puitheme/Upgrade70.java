// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact addition batch for schema 5. A partial framework write is never default-filled. */
final class Upgrade70 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put(BatteryControls.HIDE_CHARGE, false);
        values.put(FluidCloudAccent.ENABLED, false);
        values.put(LockscreenStatusBlur.ENABLED, false);
        values.put(LockscreenStatusBlur.RADIUS, LockscreenStatusBlur.DEFAULT_RADIUS);
        values.put(LockscreenStatusBlur.RANGE, LockscreenStatusBlur.DEFAULT_RANGE);
        values.put(LockscreenStatusBlur.TRANSITION, LockscreenStatusBlur.DEFAULT_TRANSITION);
        values.put(LockscreenStatusBlur.MASK_COLOR, LockscreenStatusBlur.DEFAULT_MASK_COLOR);
        values.put(StatusBarSettings.alphaKey(LockscreenStatusBlur.MASK_COLOR), true);
        values.put(SpeedPosition.SAFE_GAP_ENABLED, false);
        values.put(SpeedPosition.SAFE_GAP, 2f);
        values.put(NativeClockMeasurement.SCALE_BASIS_VERSION, 1f);
        values.put(NativeClockMeasurement.LEGACY_SCALE_FACTOR, 0f);
        DEFAULTS = Collections.unmodifiableMap(values);
    }
    private Upgrade70() { }
}
