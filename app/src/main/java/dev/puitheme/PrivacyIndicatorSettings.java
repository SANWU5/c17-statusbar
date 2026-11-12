// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;

/** Settings of the privacy-dot page: hide the system's corner dot and/or show a camera-area capsule instead. */
public final class PrivacyIndicatorSettings {
    public static final String HIDE_DOT = "privacy_dot_hidden";
    public static final String CAPSULE = "privacy_capsule_enabled";
    public static final String CAMERA = "privacy_capsule_camera";
    public static final String MICROPHONE = "privacy_capsule_microphone";
    public static final String LOCATION = "privacy_capsule_location";
    /** Seconds the capsule stays after a use starts; 0 keeps it for as long as the use lasts. */
    public static final String DURATION = "privacy_capsule_duration";
    /** Seconds, 1 to 60; {@link #CONTINUOUS} (the slider's right end) shows the capsule until use stops. A stored 0 is the
     *  same thing from before App280, when 0 meant "until use stops". */
    public static final float MIN_DURATION = 1f, CONTINUOUS = 61f, MAX_DURATION = CONTINUOUS, DEFAULT_DURATION = CONTINUOUS;

    public final boolean hideDot, capsule, camera, microphone, location;
    public final long durationMillis;

    private PrivacyIndicatorSettings(boolean hideDot, boolean capsule, boolean camera, boolean microphone,
                                     boolean location, long durationMillis) {
        this.hideDot = hideDot; this.capsule = capsule; this.camera = camera;
        this.microphone = microphone; this.location = location; this.durationMillis = durationMillis;
    }

    public static PrivacyIndicatorSettings from(Bundle settings) {
        if (settings == null || SafetyMode.enabled(settings)) return new PrivacyIndicatorSettings(false, false, true, true, false, 0L);
        float seconds = settings.getFloat(DURATION, DEFAULT_DURATION);
        // Continuous (0 ms): the right end, anything past it, a legacy 0, or an unreadable value.
        if (!(seconds > 0f) || seconds >= CONTINUOUS) seconds = 0f;
        else seconds = Math.max(MIN_DURATION, seconds);
        return new PrivacyIndicatorSettings(settings.getBoolean(HIDE_DOT, false), settings.getBoolean(CAPSULE, false),
                settings.getBoolean(CAMERA, true), settings.getBoolean(MICROPHONE, true),
                settings.getBoolean(LOCATION, false), Math.round(seconds * 1000f));
    }

    /** Any hook is needed only while one of the two features is on. */
    public boolean wanted() { return hideDot || capsule && (camera || microphone || location); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof PrivacyIndicatorSettings)) return false;
        PrivacyIndicatorSettings o = (PrivacyIndicatorSettings) other;
        return hideDot == o.hideDot && capsule == o.capsule && camera == o.camera && microphone == o.microphone
                && location == o.location && durationMillis == o.durationMillis;
    }
    @Override public int hashCode() {
        return (hideDot ? 1 : 0) | (capsule ? 2 : 0) | (camera ? 4 : 0) | (microphone ? 8 : 0) | (location ? 16 : 0)
                | (int) (durationMillis << 5);
    }
}
