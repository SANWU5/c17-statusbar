// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent rows use primary/secondary SIM styles; the stacked renderer retains its native prefix/suffix controls. */
public final class NativeNetworkBadgeSettings {
    public static final String MASTER = "native_network_badge_enabled";
    public static final String PART_ONE = "native_network_badge_1", PART_TWO = "native_network_badge_2";
    private static final String LEGACY = "native_network_badge";
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String, Boolean> booleans = new LinkedHashMap<>();
        Map<String, Float> numbers = new LinkedHashMap<>();
        Map<String, String> strings = new LinkedHashMap<>();
        booleans.put(MASTER, false);
        for (int part = 0; part <= 2; part++) {
            String prefix = part == 0 ? LEGACY : group(part);
            if (part != 0) {
                booleans.put(prefix + "_enabled", true);
                booleans.put(prefix + "_hidden", false);
            }
            numbers.put(prefix + "_offset_x", 0f); numbers.put(prefix + "_offset_y", 0f);
            numbers.put(prefix + "_scale", 100f); numbers.put(prefix + "_weight", 400f);
            strings.put(prefix + "_font", "native");
        }
        BOOLEANS = Collections.unmodifiableMap(booleans);
        NUMBERS = Collections.unmodifiableMap(numbers);
        STRINGS = Collections.unmodifiableMap(strings);
    }
    public static String group(int part) {
        if (part != 1 && part != 2) throw new IllegalArgumentException("Unknown native badge part");
        return part == 1 ? PART_ONE : PART_TWO;
    }
    public static String key(int part, String suffix) { return group(part) + '_' + suffix; }
    public static Map<String, Boolean> booleanDefaults() { return BOOLEANS; }
    public static Map<String, Float> floatDefaults() { return NUMBERS; }
    public static Map<String, String> stringDefaults() { return STRINGS; }
    /** Both new parts inherit a previously tuned overall badge; a present new value wins. */
    public static String legacyKey(String key) {
        if (key == null) return null;
        for (String prefix : new String[]{PART_ONE, PART_TWO}) if (key.startsWith(prefix + '_')) {
            String suffix = key.substring(prefix.length() + 1);
            if ("offset_x".equals(suffix) || "offset_y".equals(suffix) || "scale".equals(suffix)
                    || "weight".equals(suffix) || "font".equals(suffix)) return LEGACY + '_' + suffix;
        }
        return null;
    }
    public static Map<String, Object> inheritedDefaults(Map<String, ?> saved) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int part = 1; part <= 2; part++) {
            String enabled = key(part, "enabled"); Object toggle = saved == null ? null : saved.get(enabled);
            values.put(enabled, toggle instanceof Boolean ? toggle : true);
            for (String suffix : new String[]{"offset_x", "offset_y", "scale", "weight", "font"}) {
                String key = key(part, suffix); Object value = saved == null ? null : saved.get(key);
                if (value == null && saved != null) value = saved.get(legacyKey(key));
                if ("font".equals(suffix)) values.put(key, value instanceof String ? value : STRINGS.get(key));
                else values.put(key, value instanceof Number && Float.isFinite(((Number) value).floatValue())
                        ? ((Number) value).floatValue() : NUMBERS.get(key));
            }
        }
        return values;
    }
    private NativeNetworkBadgeSettings() { }
}
