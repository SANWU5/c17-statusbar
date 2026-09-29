package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Collections;

/* JADX INFO: loaded from: classes.dex */
public final class StatusBarSettings {
    private static final String MIGRATION_PREFS = "statusbar_settings_storage";
    private static final String CREDENTIAL_MIGRATED = "credential_preferences_migrated";
    public static final String AUTHORITY = "dev.puitheme.iosstatusbar.settings";
    public static final String CONTENT_URI = "content://dev.puitheme.iosstatusbar.settings/values";
    public static final String DATA_ICON_SCALE = "data_icon_scale";
    public static final String DATA_OFFSET_X = "data_offset_x";
    public static final String DATA_OFFSET_Y = "data_offset_y";
    public static final int DEFAULT_DATA_ICON_SCALE = 100;
    public static final int DEFAULT_DATA_OFFSET_X = 0;
    public static final int DEFAULT_DATA_OFFSET_Y = 0;
    public static final int DEFAULT_FONT_WEIGHT = 600;
    public static final int DEFAULT_ICON_SCALE = 100;
    public static final int DEFAULT_LABEL_SCALE = 100;
    public static final int DEFAULT_OFFSET_X = 0;
    public static final int DEFAULT_OFFSET_Y = 0;
    public static final int DEFAULT_SLOT_WIDTH = 22;
    public static final int DEFAULT_WIFI_ICON_SCALE = 100;
    public static final int DEFAULT_WIFI_OFFSET_X = 0;
    public static final int DEFAULT_WIFI_OFFSET_Y = 0;
    public static final String FONT_WEIGHT = "font_weight";
    public static final String ICON_SCALE = "icon_scale";
    public static final String LABEL_SCALE = "label_scale";
    public static final String LABEL_OFFSET_X = "label_offset_x";
    public static final String LABEL_OFFSET_Y = "label_offset_y";
    public static final String OFFSET_X = "offset_x";
    public static final String OFFSET_Y = "offset_y";
    public static final String PREFS = "statusbar_settings";
    public static final String SLOT_WIDTH = "slot_width";
    public static final String WIFI_ICON_SCALE = "wifi_icon_scale";
    public static final String WIFI_OFFSET_X = "wifi_offset_x";
    public static final String WIFI_OFFSET_Y = "wifi_offset_y";
    public static final String SPEED_OFFSET_X = "speed_offset_x";
    public static final String SPEED_OFFSET_Y = "speed_offset_y";
    public static final String SPEED_SCALE = "speed_scale";
    public static final String SPEED_NUMBER_SCALE = "speed_number_scale";
    public static final String SPEED_UNIT_SCALE = "speed_unit_scale";
    public static final String SPEED_LINE_GAP = "speed_line_gap";
    public static final String CLOCK_ENABLED = "clock_enabled", CLOCK_PATTERN = "clock_pattern";
    public static final String CLOCK_OFFSET_X = "clock_offset_x", CLOCK_OFFSET_Y = "clock_offset_y";
    public static final String CLOCK_SCALE = "clock_scale", CLOCK_WEIGHT = "clock_weight";
    public static final String CLOCK_SPACING = "clock_spacing";
    public static final String CARRIER_MODE = "carrier_mode", CARRIER_PATTERN = "carrier_pattern";
    public static final String CARRIER_TEXT = "carrier_text";
    public static final String CARRIER_OFFSET_X = "carrier_offset_x", CARRIER_OFFSET_Y = "carrier_offset_y";
    public static final String CARRIER_SCALE = "carrier_scale", CARRIER_WEIGHT = "carrier_weight";
    public static final String CARRIER_SPACING = "carrier_spacing";
    public static final String FONT_MODE = "font_mode", FONT_REVISION = "font_revision", FONT_NAME = "font_name";
    public static final String SIGNAL_LAYOUT = "signal_layout", SPEED_WEIGHT = "speed_weight";
    public static final String BATTERY_CHARGE_INSIDE = "battery_charge_inside";
    public static final String BATTERY_HOLD = "battery_hold_seconds", BATTERY_FADE = "battery_fade_seconds";
    public static final String BATTERY_OFFSET_X = "battery_offset_x", BATTERY_OFFSET_Y = "battery_offset_y";
    public static final String BATTERY_SCALE = "battery_scale", BATTERY_WIDTH_SCALE = "battery_width_scale", BATTERY_HEIGHT_SCALE = "battery_height_scale";
    public static final String FONT_URI = "content://" + AUTHORITY + "/font/current";
    public static final Map<String, Float> NUMERIC_DEFAULTS;
    public static final Map<String, Integer> COLOR_DEFAULTS;
    public static final Map<String, String> STRING_DEFAULTS;

    static {
        Map<String, Float> numbers = new LinkedHashMap<>();
        numbers.put(WIFI_OFFSET_X, 0f);
        numbers.put(WIFI_OFFSET_Y, 0f);
        numbers.put(WIFI_ICON_SCALE, 100f);
        numbers.put(DATA_OFFSET_X, 0f);
        numbers.put(DATA_OFFSET_Y, 0f);
        numbers.put(DATA_ICON_SCALE, 100f);
        numbers.put(LABEL_OFFSET_X, 0f);
        numbers.put(LABEL_OFFSET_Y, 0f);
        numbers.put(LABEL_SCALE, 100f);
        numbers.put(SLOT_WIDTH, 22f);
        numbers.put(FONT_WEIGHT, (float) DEFAULT_FONT_WEIGHT);
        numbers.put(SPEED_OFFSET_X, 0f);
        numbers.put(SPEED_OFFSET_Y, 0f);
        numbers.put(SPEED_SCALE, 100f);
        numbers.put(SPEED_NUMBER_SCALE, 100f);
        numbers.put(SPEED_UNIT_SCALE, 100f);
        numbers.put(SPEED_LINE_GAP, 0f);
        numbers.put(SPEED_WEIGHT, 600f);
        numbers.put(BATTERY_HOLD, 3f);
        numbers.put(BATTERY_FADE, 1f);
        numbers.put(BATTERY_OFFSET_X, 0f);numbers.put(BATTERY_OFFSET_Y, 0f);
        numbers.put(BATTERY_SCALE, 100f);numbers.put(BATTERY_WIDTH_SCALE, 100f);numbers.put(BATTERY_HEIGHT_SCALE, 100f);
        for (String item : new String[]{"clock", "carrier"}) {
            numbers.put(item + "_offset_x", 0f);
            numbers.put(item + "_offset_y", 0f);
            numbers.put(item + "_scale", 100f);
            numbers.put(item + "_weight", 600f);
            numbers.put(item + "_spacing", 0f);
        }
        NUMERIC_DEFAULTS = Collections.unmodifiableMap(numbers);
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String item : new String[]{"wifi", "data", "label", "speed", "clock", "carrier", "battery", "battery_text", "battery_bolt"}) {
            colors.put(item + "_color_light", 0xff000000);
            colors.put(item + "_color_dark", 0xffffffff);
        }
        colors.put("battery_charge_color_light",0xff00bd13);colors.put("battery_charge_color_dark",0xff00bd13);
        colors.put("battery_alert_color_light",0xffff3b30);colors.put("battery_alert_color_dark",0xffff3b30);
        COLOR_DEFAULTS = Collections.unmodifiableMap(colors);
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put(CLOCK_PATTERN, TimeFormat.CLOCK_DEFAULT);
        strings.put(CARRIER_PATTERN, TimeFormat.CARRIER_DEFAULT);
        strings.put(CARRIER_MODE, "original");
        strings.put(CARRIER_TEXT, "更好的C17状态栏");
        strings.put(FONT_MODE, "system");
        strings.put(FONT_REVISION, "");
        strings.put(FONT_NAME, "未导入字体");
        strings.put(SIGNAL_LAYOUT, "system");
        STRING_DEFAULTS = Collections.unmodifiableMap(strings);
    }

    public static float number(SharedPreferences preferences, String key, float fallback) {
        return number(preferences.getAll(), key, fallback);
    }

    public static String string(Map<String, ?> values, String key) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : STRING_DEFAULTS.get(key);
    }

    public static String alphaKey(String colorKey) {
        return colorKey + "_custom_alpha";
    }

    public static boolean customAlpha(Map<String, ?> values, String colorKey) {
        Object mode = values.get(alphaKey(colorKey));
        if (mode instanceof Boolean) return (Boolean) mode;
        Object color = values.get(colorKey);
        // Older settings that already contain an alpha value remain intentional custom colors.
        return color instanceof Number && ((((Number) color).intValue() >>> 24) != 255);
    }

    public static float number(Map<String, ?> values, String key, float fallback) {
        Object value = values.get(key);
        if (value instanceof Number) {
            float result = ((Number) value).floatValue();
            if (!Float.isNaN(result) && !Float.isInfinite(result)) return result;
        }
        return fallback;
    }

    public static float settingNumber(Map<String, ?> values, String key, float fallback) {
        if (!values.containsKey(key)) {
            if (key.equals(WIFI_OFFSET_X) || key.equals(DATA_OFFSET_X)) return number(values, OFFSET_X, fallback);
            if (key.equals(WIFI_OFFSET_Y) || key.equals(DATA_OFFSET_Y)) return number(values, OFFSET_Y, fallback);
            if (key.equals(WIFI_ICON_SCALE) || key.equals(DATA_ICON_SCALE)) return number(values, ICON_SCALE, fallback);
        }
        return number(values, key, fallback);
    }

    public static synchronized SharedPreferences preferences(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        SharedPreferences preferences = deviceContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences migration = deviceContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Context credentialContext = context.isDeviceProtectedStorage()
                ? context.getApplicationContext() : context;
        if (!migration.getBoolean(CREDENTIAL_MIGRATED, false)
                && userManager != null && userManager.isUserUnlocked()
                && credentialContext != null && !credentialContext.isDeviceProtectedStorage()) {
            SharedPreferences legacy = credentialContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                // Values already written to device storage take precedence.
                if (!preferences.contains(entry.getKey())) {
                    copyPreference(editor, entry.getKey(), entry.getValue());
                }
            }
            // Record migration only after the copied settings are durable.
            if (editor.commit()) {
                migration.edit().putBoolean(CREDENTIAL_MIGRATED, true).commit();
            }
        }
        return preferences;
    }

    @SuppressWarnings("unchecked")
    private static void copyPreference(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, new HashSet<>((Set<String>) value));
        }
    }

    private StatusBarSettings() {
    }
}
