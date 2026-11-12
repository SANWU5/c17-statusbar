// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keep the previous six-row choice when adding the independent row selector. */
final class Upgrade196 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> values=new LinkedHashMap<>();values.put(QsCardSettings.GRID_ROWS,4f);
        values.put(QsCardSettings.SMALL_TILES,false);values.put(QsCardSettings.SMALL_MODE,"always");
        DEFAULTS=Collections.unmodifiableMap(values);
    }
    static Map<String,Object> inheritedDefaults(Map<String,?> previous) {
        Map<String,Object> defaults=new LinkedHashMap<>(DEFAULTS);
        defaults.put(QsCardSettings.GRID_ROWS,Boolean.TRUE.equals(previous.get(QsCardSettings.GRID_6))?6f:4f);
        return defaults;
    }
    static boolean validRows(Object value) {
        if(!(value instanceof Number))return false;double rows=((Number)value).doubleValue();
        if(!Double.isFinite(rows)||rows<4d||rows>7d||rows!=Math.rint(rows))return false;
        return !(value instanceof java.math.BigDecimal)||((java.math.BigDecimal)value).compareTo(java.math.BigDecimal.valueOf((long)rows))==0;
    }
    static boolean validMode(Object value) {
        return QsCardSettings.validSmallMode(value);
    }
    static void migrate(SharedPreferences preferences) {
        synchronized(preferences) {
            Map<String,?> previous=PreferenceWrites.values(preferences);if(previous.isEmpty())return;
            Map<String,Object> missing=new LinkedHashMap<>();
            for(Map.Entry<String,Object> entry:inheritedDefaults(previous).entrySet())
                if(!previous.containsKey(entry.getKey()))missing.put(entry.getKey(),entry.getValue());
            if(missing.isEmpty())return;
            SharedPreferences.Editor editor=preferences.edit();
            for(Map.Entry<String,Object> entry:missing.entrySet())StatusBarSettings.copyPreference(editor,entry.getKey(),entry.getValue());
            PreferenceWrites.commit(preferences,editor,missing,false);
        }
    }
    private Upgrade196(){ }
}
