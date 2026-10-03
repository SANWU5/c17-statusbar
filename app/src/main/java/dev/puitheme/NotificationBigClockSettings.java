// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Notification-only big-clock defaults and reversible conflicts with the original header. */
public final class NotificationBigClockSettings {
    public static final String PORTRAIT_PREFIX="notification_big_clock_";
    public static final String LANDSCAPE_PREFIX="notification_big_clock_landscape_";
    public static final String LANDSCAPE_MASTER=LANDSCAPE_PREFIX+"enabled";
    public static final String LANDSCAPE_NOTIFICATION_WIDTH_ENABLED=LANDSCAPE_PREFIX+"notification_width_enabled";
    public static final String LANDSCAPE_NOTIFICATION_WIDTH=LANDSCAPE_PREFIX+"notification_width";
    public static final String MASTER="notification_big_clock_enabled";
    public static final String PATTERN="notification_big_clock_pattern";
    public static final String DATE_DEFAULT="M月d日{周} {干支}{农历日期}";
    public static final String DATE_PATTERN="notification_big_clock_date_pattern";
    // Retired multipliers remain storage/import compatible; heights use their own values directly.
    public static final String SCALE="notification_big_clock_scale";
    public static final String COMPACT_SCALE="notification_big_clock_compact_scale";
    public static final String WEIGHT="notification_big_clock_weight";
    public static final String COMPACT_WEIGHT="notification_big_clock_compact_weight";
    public static final String OFFSET_Y="notification_big_clock_offset_y";
    public static final String COLOR_LIGHT="notification_big_clock_color_light";
    public static final String COLOR_DARK="notification_big_clock_color_dark";
    public static final String GLASS="notification_big_clock_glass";
    public static final String GLASS_BORDER_ENABLED="notification_big_clock_glass_border_enabled";
    public static final String GLASS_BORDER_WIDTH="notification_big_clock_glass_border_width";
    public static final String GLASS_BORDER_COLOR_LIGHT="notification_big_clock_glass_border_color_light";
    public static final String GLASS_BORDER_COLOR_DARK="notification_big_clock_glass_border_color_dark";
    public static final String ENTRY_EFFECT_ENABLED="notification_big_clock_entry_effect_enabled";
    public static final String ENTRY_BLUR_RADIUS="notification_big_clock_entry_blur_radius";
    public static final String ENTRY_FADE_STRENGTH="notification_big_clock_entry_fade_strength";
    public static final String ENTRY_COMPLETION="notification_big_clock_entry_completion";
    public static final String ENTRY_TRAVEL="notification_big_clock_entry_travel";
    public static final String NOTIFICATION_EDGE_ENABLED="notification_big_clock_notification_edge_enabled";
    public static final String NOTIFICATION_EDGE_SAFE_DISTANCE="notification_big_clock_notification_edge_safe_distance";
    public static final String NOTIFICATION_EDGE_RANGE="notification_big_clock_notification_edge_range";
    public static final String NOTIFICATION_EDGE_BLUR_RADIUS="notification_big_clock_notification_edge_blur_radius";
    public static final String OFFSET_X="notification_big_clock_offset_x";
    public static final String MAX_SIZE="notification_big_clock_max_size";
    public static final String COMPACT_MAX_SIZE="notification_big_clock_compact_max_size";
    /** Native portrait clock fills the screen width between these two side margins. */
    public static final String SCREEN_PADDING="notification_big_clock_screen_padding";
    public static final String COMPACT_OFFSET_X="notification_big_clock_compact_offset_x";
    public static final String COMPACT_OFFSET_Y="notification_big_clock_compact_offset_y";
    public static final String LETTER_SPACING="notification_big_clock_letter_spacing";
    public static final String ALIGNMENT="notification_big_clock_alignment";
    public static final String FONT="notification_big_clock_font";
    public static final String DATE_ENABLED="notification_big_clock_date_enabled";
    public static final String DATE_SIZE="notification_big_clock_date_size";
    public static final String DATE_WEIGHT="notification_big_clock_date_weight";
    public static final String DATE_OFFSET_X="notification_big_clock_date_offset_x";
    public static final String DATE_OFFSET_Y="notification_big_clock_date_offset_y";
    public static final String DATE_GAP="notification_big_clock_date_gap";
    public static final String DATE_COLOR_LIGHT="notification_big_clock_date_color_light";
    public static final String DATE_COLOR_DARK="notification_big_clock_date_color_dark";
    public static final String DATE_ALIGNMENT="notification_big_clock_date_alignment";
    public static final String STACK_ENABLED="notification_big_clock_stack_enabled";
    public static final String INTERACTIVE_STACK="notification_big_clock_interactive_stack_enabled";
    public static final String VISIBLE_COUNT="notification_big_clock_visible_count";
    public static final String TAIL_WIDTH_1="notification_big_clock_tail_width_1";
    public static final String TAIL_WIDTH_2="notification_big_clock_tail_width_2";
    public static final String TAIL_WIDTH_3="notification_big_clock_tail_width_3";
    // Retired keys remain import-compatible; runtime and preview never use these values.
    public static final String STACK_GAP="notification_big_clock_stack_gap";
    public static final String STACK_INSET="notification_big_clock_stack_inset";
    public static final String NOTIFICATION_GAP="notification_big_clock_notification_gap";
    public static final String NOTIFICATION_GAP_ENABLED="notification_big_clock_notification_gap_enabled";
    public static final String STATUS_ICONS_FIXED="notification_big_clock_status_icons_fixed";
    public static final String FOOTER_ENABLED="notification_big_clock_footer_enabled";
    public static final String FOOTER_PATTERN="notification_big_clock_footer_pattern";
    public static final String FOOTER_TEXT="notification_big_clock_footer_text";
    public static final String FOOTER_SIZE="notification_big_clock_footer_size";
    public static final String FOOTER_WEIGHT="notification_big_clock_footer_weight";
    public static final String FOOTER_OFFSET_X="notification_big_clock_footer_offset_x";
    public static final String FOOTER_OFFSET_Y="notification_big_clock_footer_offset_y";
    public static final String FOOTER_MARGIN="notification_big_clock_footer_margin";
    public static final String FOOTER_COLOR_LIGHT="notification_big_clock_footer_color_light";
    public static final String FOOTER_COLOR_DARK="notification_big_clock_footer_color_dark";
    public static final String FOOTER_ALIGNMENT="notification_big_clock_footer_alignment";
    /** A runtime marker for the native notification-header clock, including its animation copy. */
    public static final String NATIVE_CLOCK="notification_clock";
    public static final Map<String,Boolean> BOOLEANS;
    public static final Map<String,Float> NUMBERS;
    public static final Map<String,Integer> COLORS;
    public static final Map<String,String> STRINGS;
    static {
        Map<String,Boolean> booleans=new LinkedHashMap<>();
        booleans.put(MASTER,false);booleans.put(GLASS,true);booleans.put(DATE_ENABLED,true);
        booleans.put(STACK_ENABLED,false);booleans.put(INTERACTIVE_STACK,false);booleans.put(FOOTER_ENABLED,false);
        booleans.put(GLASS_BORDER_ENABLED,true);booleans.put(ENTRY_EFFECT_ENABLED,true);
        booleans.put(NOTIFICATION_EDGE_ENABLED,true);
        booleans.put(NOTIFICATION_GAP_ENABLED,true);
        booleans.put(STATUS_ICONS_FIXED,true);
        copyLandscape(booleans);
        booleans.put(LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,false);
        BOOLEANS=Collections.unmodifiableMap(booleans);
        Map<String,Float> numbers=new LinkedHashMap<>();
        numbers.put(SCALE,100f);numbers.put(COMPACT_SCALE,36f);
        numbers.put(WEIGHT,600f);numbers.put(COMPACT_WEIGHT,300f);numbers.put(OFFSET_Y,0f);
        numbers.put(OFFSET_X,0f);numbers.put(MAX_SIZE,180f);numbers.put(COMPACT_MAX_SIZE,64f);
        numbers.put(COMPACT_OFFSET_X,0f);numbers.put(COMPACT_OFFSET_Y,0f);numbers.put(LETTER_SPACING,0f);
        numbers.put(DATE_SIZE,15f);numbers.put(DATE_WEIGHT,600f);numbers.put(DATE_OFFSET_X,0f);
        numbers.put(DATE_OFFSET_Y,0f);numbers.put(DATE_GAP,12f);
        numbers.put(VISIBLE_COUNT,1f);numbers.put(STACK_GAP,8f);numbers.put(NOTIFICATION_GAP,18f);
        numbers.put(STACK_INSET,8f);
        numbers.put(TAIL_WIDTH_1,96f);numbers.put(TAIL_WIDTH_2,92f);numbers.put(TAIL_WIDTH_3,88f);
        numbers.put(FOOTER_SIZE,13f);numbers.put(FOOTER_WEIGHT,400f);numbers.put(FOOTER_OFFSET_X,0f);
        numbers.put(FOOTER_OFFSET_Y,0f);numbers.put(FOOTER_MARGIN,24f);
        numbers.put(GLASS_BORDER_WIDTH,.65f);numbers.put(ENTRY_BLUR_RADIUS,12f);
        numbers.put(ENTRY_FADE_STRENGTH,100f);numbers.put(ENTRY_COMPLETION,85f);
        numbers.put(ENTRY_TRAVEL,32f);
        numbers.put(NOTIFICATION_EDGE_SAFE_DISTANCE,18f);numbers.put(NOTIFICATION_EDGE_RANGE,24f);
        numbers.put(NOTIFICATION_EDGE_BLUR_RADIUS,8f);
        copyLandscape(numbers);
        numbers.put(SCREEN_PADDING,24f);
        numbers.put(LANDSCAPE_NOTIFICATION_WIDTH,100f);
        NUMBERS=Collections.unmodifiableMap(numbers);
        Map<String,Integer> colors=new LinkedHashMap<>();
        colors.put(COLOR_LIGHT,0xffffffff);colors.put(COLOR_DARK,0xffffffff);
        colors.put(DATE_COLOR_LIGHT,0xffffffff);colors.put(DATE_COLOR_DARK,0xffffffff);
        colors.put(FOOTER_COLOR_LIGHT,0xffffffff);colors.put(FOOTER_COLOR_DARK,0xffffffff);
        colors.put(GLASS_BORDER_COLOR_LIGHT,0xffffffff);colors.put(GLASS_BORDER_COLOR_DARK,0xffffffff);
        copyLandscape(colors);
        COLORS=Collections.unmodifiableMap(colors);
        Map<String,String> strings=new LinkedHashMap<>();
        strings.put(PATTERN,TimeFormat.CLOCK_DEFAULT);strings.put(DATE_PATTERN,DATE_DEFAULT);
        strings.put(ALIGNMENT,"center");strings.put(FONT,"native");strings.put(DATE_ALIGNMENT,"center");
        strings.put(FOOTER_PATTERN,"{text}");strings.put(FOOTER_TEXT,"");strings.put(FOOTER_ALIGNMENT,"center");
        copyLandscape(strings);
        STRINGS=Collections.unmodifiableMap(strings);
    }
    /** Independent defaults: upgrading never enables landscape from the portrait switch. */
    private static <T> void copyLandscape(Map<String,T> values) {
        for(Map.Entry<String,T> item:new LinkedHashMap<>(values).entrySet()) {
            String key=item.getKey();
            if(!key.startsWith(PORTRAIT_PREFIX)||INTERACTIVE_STACK.equals(key)
                    ||STATUS_ICONS_FIXED.equals(key)||STACK_GAP.equals(key)||STACK_INSET.equals(key))continue;
            values.put(landscapeKey(key),item.getValue());
        }
    }
    public static String landscapeKey(String portraitKey) {
        return portraitKey!=null&&portraitKey.startsWith(PORTRAIT_PREFIX)
                &&!portraitKey.startsWith(LANDSCAPE_PREFIX)
                ? LANDSCAPE_PREFIX+portraitKey.substring(PORTRAIT_PREFIX.length()):portraitKey;
    }
    public static String portraitKey(String key) {
        return key!=null&&key.startsWith(LANDSCAPE_PREFIX)
                ? PORTRAIT_PREFIX+key.substring(LANDSCAPE_PREFIX.length()):key;
    }
    public static boolean enabled(android.os.Bundle source,boolean landscape) {
        return source!=null&&Boolean.TRUE.equals(source.get(landscape?LANDSCAPE_MASTER:MASTER));
    }
    private NotificationBigClockSettings(){}

    /** Complete cards are bounded by the actual native rows, never by the slider suggestion. */
    public static int visibleCount(Object stored) {
        double value=stored instanceof Number?((Number)stored).doubleValue():1d;
        if(Double.isNaN(value)||Double.isInfinite(value))value=1d;
        return (int)Math.max(1L,Math.min(Integer.MAX_VALUE,Math.round(value)));
    }

    public static boolean positiveSize(String key) {
        key=portraitKey(key);
        return MAX_SIZE.equals(key)||COMPACT_MAX_SIZE.equals(key)||DATE_SIZE.equals(key)||FOOTER_SIZE.equals(key);
    }
    /** Only the phone's native clock family has the verified HGHT axis. */
    public static boolean nativeHeightFont(Object mode) {
        return "native".equals(mode);
    }
    /** Pure user text is passed as content, never interpreted as a date pattern. */
    public static String formatFooter(String pattern,String text,long now) {
        return TimeFormat.formatContent(pattern,text,now);
    }
    public static String footerValidationError(String pattern) {
        try { TimeFormat.formatContent(pattern,"",0L);return null; }
        catch(IllegalArgumentException invalid) { return invalid.getMessage(); }
    }
    public static boolean footerHasSeconds(String pattern) { return TimeFormat.contentHasSeconds(pattern); }

    /** Ignore originals only while the replacement is actually shown; never erase their saved values. */
    public static boolean suppressed(String group,boolean portrait,boolean notification,boolean bigEnabled) {
        return notification&&bigEnabled&&("shade_clock".equals(group)||"clock".equals(group)
                ||NATIVE_CLOCK.equals(group)||CarrierPanels.NOTIFICATION.equals(group));
    }

    /** Explicitly editing an original header opts back into it; disabling one of its switches does not. */
    public static boolean conflictsWith(String key,Object value) {
        return key!=null&&value!=null&&!Boolean.FALSE.equals(value)
                &&(key.startsWith("shade_clock_")||key.startsWith(CarrierPanels.NOTIFICATION+"_"));
    }
}
