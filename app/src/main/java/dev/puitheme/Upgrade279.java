// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Schema 52 adds the privacy-dot page. Both features start off; the capsule's type choices follow the camera/microphone default. */
final class Upgrade279 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> defaults = new LinkedHashMap<>();
        defaults.put(PrivacyIndicatorSettings.HIDE_DOT, false);
        defaults.put(PrivacyIndicatorSettings.CAPSULE, false);
        defaults.put(PrivacyIndicatorSettings.CAMERA, true);
        defaults.put(PrivacyIndicatorSettings.MICROPHONE, true);
        defaults.put(PrivacyIndicatorSettings.LOCATION, false);
        defaults.put(PrivacyIndicatorSettings.DURATION, PrivacyIndicatorSettings.DEFAULT_DURATION);
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }
    static void migrate(SharedPreferences preferences) {
        synchronized (preferences) {
            Map<String,?> previous = PreferenceWrites.values(preferences);
            if (previous.isEmpty()) return;
            Map<String,Object> missing = new LinkedHashMap<>();
            for (Map.Entry<String,Object> entry : DEFAULTS.entrySet())
                if (!previous.containsKey(entry.getKey())) missing.put(entry.getKey(), entry.getValue());
            // App280: the display time starts at 1 second and its right end means "until use stops", which 0 used to mean.
            Object duration = previous.get(PrivacyIndicatorSettings.DURATION);
            if (duration instanceof Number && !(((Number) duration).floatValue() > 0f))
                missing.put(PrivacyIndicatorSettings.DURATION, PrivacyIndicatorSettings.CONTINUOUS);
            if (missing.isEmpty()) return;
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String,Object> entry : missing.entrySet())
                StatusBarSettings.copyPreference(editor, entry.getKey(), entry.getValue());
            PreferenceWrites.commit(preferences, editor, missing, false);
        }
    }
    private Upgrade279() { }
}
