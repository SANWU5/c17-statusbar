// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent defaults; the combined classic panel never inherits separated header settings. */
public final class ClassicTextSettings {
    public static final String CLOCK = PanelMode.CLOCK, CARRIER = CarrierPanels.CLASSIC;
    public static final String CLOCK_MASTER = CLOCK + "_controls_enabled";
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, Integer> COLORS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String, Boolean> switches = new LinkedHashMap<>();
        switches.put(CLOCK_MASTER, false); switches.put(CLOCK + "_enabled", false);
        switches.put(CARRIER + "_enabled", false); switches.put(CARRIER + "_replace_enabled", true);
        Map<String, Float> numbers = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String group : new String[]{CLOCK, CARRIER}) {
            for (String suffix : new String[]{"position_enabled", "size_enabled", "color_enabled", "text_style_enabled"})
                switches.put(group + "_" + suffix, true);
            numbers.put(group + "_offset_x", 0f); numbers.put(group + "_offset_y", 0f);
            numbers.put(group + "_scale", 100f); numbers.put(group + "_weight", 600f); numbers.put(group + "_spacing", 0f);
            colors.put(group + "_color_light", 0xff000000); colors.put(group + "_color_dark", 0xffffffff);
        }
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put(CLOCK + "_pattern", TimeFormat.CLOCK_DEFAULT);
        strings.put(CARRIER + "_mode", "original"); strings.put(CARRIER + "_pattern", TimeFormat.CARRIER_DEFAULT);
        strings.put(CARRIER + "_text", "更好的C17状态栏");
        BOOLEANS = Collections.unmodifiableMap(switches); NUMBERS = Collections.unmodifiableMap(numbers);
        COLORS = Collections.unmodifiableMap(colors); STRINGS = Collections.unmodifiableMap(strings);
    }
    private ClassicTextSettings() { }
}
