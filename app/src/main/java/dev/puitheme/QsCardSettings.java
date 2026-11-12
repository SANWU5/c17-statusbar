// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in display settings; no value is a native tile size, location or Room grid record. */
public final class QsCardSettings {
    public static final String LARGE_MEDIA="qs_card_large_media_enabled", GRID_6="qs_card_six_rows_enabled",
            RESIZE_NATIVE="qs_card_native_resize_enabled", HIDE_IDLE="qs_card_hide_idle_controls_enabled",
            WALLPAPER_ENABLED="qs_card_wallpaper_enabled", WALLPAPER_REVISION="qs_card_wallpaper_revision",
            WALLPAPER_OPACITY="qs_card_wallpaper_opacity", WALLPAPER_SCENE="media_card",
            GRID_ROWS="qs_card_grid_rows", SMALL_TILES="qs_card_small_tiles_2x1_enabled",
            SMALL_MODE="qs_card_small_tiles_mode";
    public static final String SMALL_ALWAYS="always",SMALL_HIDDEN="hidden",SMALL_MORE="more",SMALL_LEGACY_SWIPE="swipe";
    public static final String[] SMALL_MODES={SMALL_ALWAYS,SMALL_HIDDEN,SMALL_MORE};
    public static final Map<String,Boolean> BOOLEANS;
    public static final Map<String,Float> NUMBERS;
    public static final Map<String,String> STRINGS;
    static {
        Map<String,Boolean> flags=new LinkedHashMap<>();
        for(String key:new String[]{LARGE_MEDIA,GRID_6,RESIZE_NATIVE,HIDE_IDLE,WALLPAPER_ENABLED,SMALL_TILES})flags.put(key,false);
        BOOLEANS=Collections.unmodifiableMap(flags);
        Map<String,Float> numbers=new LinkedHashMap<>();numbers.put(WALLPAPER_OPACITY,100f);numbers.put(GRID_ROWS,4f);
        NUMBERS=Collections.unmodifiableMap(numbers);
        Map<String,String> strings=new LinkedHashMap<>();strings.put(WALLPAPER_REVISION,"");strings.put(SMALL_MODE,"always");
        STRINGS=Collections.unmodifiableMap(strings);
    }
    public static boolean wallpaperScene(String scene){return WALLPAPER_SCENE.equals(scene);}
    public static boolean validSmallMode(Object value) {
        return SMALL_ALWAYS.equals(value)||SMALL_HIDDEN.equals(value)||SMALL_MORE.equals(value)||SMALL_LEGACY_SWIPE.equals(value);
    }
    public static boolean validSmallMode(String value) {return validSmallMode((Object)value);}
    /** Compatibility is applied to runtime copies only; persisted choices are not rewritten. */
    public static String normalizeSmallMode(String value) {
        if(SMALL_MORE.equals(value)||SMALL_LEGACY_SWIPE.equals(value))return SMALL_MORE;
        return SMALL_HIDDEN.equals(value)?SMALL_HIDDEN:SMALL_ALWAYS;
    }
    private QsCardSettings(){ }
}
