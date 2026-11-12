// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete the network mirror defaults without changing an existing selection. */
final class Upgrade209 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> defaults = new LinkedHashMap<>();
        defaults.put(NetworkDisplaySettings.WIFI_HIDDEN, false);
        // Code 208 published these four settings under schema 50 without defaults
        // for older mirrors. Preserve that wire format as well as its saved values.
        defaults.put(QsWifiAlias.ENABLED, false);
        defaults.put(QsWifiAlias.MODE, "per_network");
        defaults.put(QsWifiAlias.UNIFIED, "");
        defaults.put(QsWifiAlias.RULES, "{}");
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    static void migrate(SharedPreferences preferences) {
        synchronized (preferences) {
            Map<String,?> previous = PreferenceWrites.values(preferences);
            if (previous.isEmpty()) return;
            Map<String,Object> missing = new LinkedHashMap<>();
            for (Map.Entry<String,Object> entry : DEFAULTS.entrySet())
                if (!previous.containsKey(entry.getKey())) missing.put(entry.getKey(), entry.getValue());
            if (missing.isEmpty()) return;
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String,Object> entry : missing.entrySet())
                StatusBarSettings.copyPreference(editor, entry.getKey(), entry.getValue());
            PreferenceWrites.commit(preferences, editor, missing, false);
        }
    }

    private Upgrade209() { }
}
