// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** New control-center options are opt-in; an existing value is never rewritten. */
final class Upgrade195 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> values=new LinkedHashMap<>();
        for(String key:new String[]{QsCardSettings.LARGE_MEDIA,QsCardSettings.GRID_6,QsCardSettings.RESIZE_NATIVE,
                QsCardSettings.HIDE_IDLE,QsCardSettings.WALLPAPER_ENABLED})values.put(key,false);
        values.put(QsCardSettings.WALLPAPER_OPACITY,100f);values.put(QsCardSettings.WALLPAPER_REVISION,"");
        DEFAULTS=Collections.unmodifiableMap(values);
    }
    static void migrate(SharedPreferences preferences) {
        synchronized(preferences) {
            Map<String,?> old=PreferenceWrites.values(preferences);
            if(old.isEmpty())return;
            Map<String,Object> missing=new LinkedHashMap<>();
            for(Map.Entry<String,Object> entry:DEFAULTS.entrySet())
                if(!old.containsKey(entry.getKey()))missing.put(entry.getKey(),entry.getValue());
            if(missing.isEmpty())return;
            SharedPreferences.Editor edit=preferences.edit();
            for(Map.Entry<String,Object> entry:missing.entrySet())
                StatusBarSettings.copyPreference(edit,entry.getKey(),entry.getValue());
            PreferenceWrites.commit(preferences,edit,missing,false);
        }
    }
    private Upgrade195(){ }
}
