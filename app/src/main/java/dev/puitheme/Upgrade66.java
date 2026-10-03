// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One complete historical addition batch; never repairs a partial current snapshot. */
final class Upgrade66 {
    static final Map<String,Object> DEFAULTS;
    static {
        Map<String,Object> values=new LinkedHashMap<>();
        values.putAll(ClassicTextSettings.BOOLEANS);
        values.putAll(ClassicTextSettings.NUMBERS);
        values.putAll(ClassicTextSettings.COLORS);
        for(String color:ClassicTextSettings.COLORS.keySet())values.put(StatusBarSettings.alphaKey(color),false);
        values.putAll(ClassicTextSettings.STRINGS);
        values.putAll(ShadeWallpaperSettings.BOOLEANS);
        values.putAll(ShadeWallpaperSettings.NUMBERS);
        values.putAll(ShadeWallpaperSettings.STRINGS);
        values.put("shade_wallpaper_manage","");
        values.putAll(IconPackAssignments.stringDefaults());
        values.putAll(NativeNetworkBadgeSettings.inheritedDefaults(Collections.emptyMap()));
        values.put(NotificationIconArea.SPACING_ENABLED,false);
        values.put(NotificationIconArea.SPACING,0f);
        DEFAULTS=Collections.unmodifiableMap(values);
    }
    static Map<String,Object> inheritedDefaults(Map<String,?> historical) {
        Map<String,Object> values=new LinkedHashMap<>(DEFAULTS);
        values.putAll(NativeNetworkBadgeSettings.inheritedDefaults(historical));
        return values;
    }
    private Upgrade66(){}
}
