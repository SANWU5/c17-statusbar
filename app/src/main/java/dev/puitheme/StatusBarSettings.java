// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

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
    public static final String LABEL_HIDDEN = "label_hidden";
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
    public static final String SHADE_CLOCK_CONTROLS_ENABLED = "shade_clock_controls_enabled";
    public static final String SHADE_CLOCK_ENABLED = "shade_clock_enabled", SHADE_CLOCK_PATTERN = "shade_clock_pattern";
    public static final String SHADE_CLOCK_OFFSET_X = "shade_clock_offset_x", SHADE_CLOCK_OFFSET_Y = "shade_clock_offset_y";
    public static final String SHADE_CLOCK_SCALE = "shade_clock_scale", SHADE_CLOCK_WEIGHT = "shade_clock_weight";
    public static final String SHADE_CLOCK_SPACING = "shade_clock_spacing";
    public static final String CARRIER_MODE = "carrier_mode", CARRIER_PATTERN = "carrier_pattern";
    public static final String CARRIER_TEXT = "carrier_text";
    public static final String CARRIER_OFFSET_X = "carrier_offset_x", CARRIER_OFFSET_Y = "carrier_offset_y";
    public static final String CARRIER_SCALE = "carrier_scale", CARRIER_WEIGHT = "carrier_weight";
    public static final String CARRIER_SPACING = "carrier_spacing";
    public static final String FONT_MODE = "font_mode", FONT_REVISION = "font_revision", FONT_NAME = "font_name";
    public static final String SIGNAL_LAYOUT = "signal_layout", SPEED_WEIGHT = "speed_weight";
    public static final String BATTERY_CHARGE_INSIDE = "battery_charge_inside";
    public static final String BATTERY_STYLE = "battery_style";
    public static final String BATTERY_HOLD = "battery_hold_seconds", BATTERY_FADE = "battery_fade_seconds";
    public static final String BATTERY_OFFSET_X = "battery_offset_x", BATTERY_OFFSET_Y = "battery_offset_y";
    public static final String BATTERY_SCALE = "battery_scale", BATTERY_WIDTH_SCALE = "battery_width_scale", BATTERY_HEIGHT_SCALE = "battery_height_scale";
    public static final String TILES_FADE_RANGE = "tiles_fade_range";
    public static final String TILES_BLUR_RADIUS = "tiles_blur_radius";
    public static final String TILES_STRENGTH = "tiles_strength";
    public static final String TILES_LEFT_RANGE = "tiles_left_range", TILES_RIGHT_RANGE = "tiles_right_range";
    public static final String TILES_LEFT_OFFSET_X = "tiles_left_offset_x", TILES_RIGHT_OFFSET_X = "tiles_right_offset_x";
    public static final String TILES_OFFSET_Y = "tiles_offset_y", TILES_REGION_HEIGHT = "tiles_region_height";
    public static final String TILES_VERTICAL_FEATHER = "tiles_vertical_feather";
    public static final String DIAGNOSTICS_ENABLED = "diagnostics_enabled";
    public static final String SAFE_MODE = "module_safe_mode";
    public static final String DATA_ACTIVITY_HIDDEN = "data_activity_hidden";
    public static final String FONT_URI = "content://" + AUTHORITY + "/font/current";
    public static final Map<String, Float> NUMERIC_DEFAULTS;
    public static final Map<String, Integer> COLOR_DEFAULTS;
    public static final Map<String, String> STRING_DEFAULTS;
    public static final Map<String, Boolean> BOOLEAN_DEFAULTS;

    static {
        Map<String, Boolean> booleans = new LinkedHashMap<>(FeatureOptions.DEFAULTS);
        booleans.put(DIAGNOSTICS_ENABLED, false);
        booleans.put(SAFE_MODE, false);
        booleans.put(NetworkIconOrder.SWAP, false);
        booleans.put(C17HighlightRemoval.ENABLED, false);
        booleans.put(C17HighlightRemoval.BACKGROUND_ENABLED, false);
        booleans.put(C17HighlightRemoval.NOTIFICATION_ENABLED, true);
        booleans.put(C17HighlightRemoval.HEADS_UP_ENABLED, true);
        booleans.put(C17HighlightRemoval.CONTROL_ENABLED, true);
        booleans.put(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED, false);
        booleans.put(NotificationClearMotion.LANDSCAPE_MASTER, false);
        booleans.put(LockscreenControls.DATE_ENABLED, false);
        booleans.put(LockscreenControls.HIDE_LOCK, false);
        booleans.put(NotificationGroupStack.MASTER, false);
        booleans.putAll(NativeStatusIcons.booleanDefaults());
        booleans.putAll(NativeNetworkBadgeControls.booleanDefaults());
        booleans.putAll(NetworkSpeedControls.BOOLEANS);
        booleans.putAll(QsTileAppearance.BOOLEANS);
        booleans.putAll(QsTileCorners.BOOLEANS);
        booleans.putAll(QsMediaAppearance.BOOLEANS);
        booleans.putAll(NotificationBigClockSettings.BOOLEANS);
        booleans.putAll(NotificationClearAppearance.BOOLEANS);
        booleans.putAll(NotificationIconArea.BOOLEANS);
        booleans.put(NotificationIconOverrides.MASTER, false);
        booleans.put(IconPackRepository.MASTER, false);
        booleans.putAll(ClassicTextSettings.BOOLEANS);
        booleans.putAll(ShadeWallpaperSettings.BOOLEANS);
        booleans.putAll(BatteryTextStyle.BOOLEANS);
        booleans.put(BatteryControls.HIDE_CHARGE, false);
        booleans.put(FluidCloudAccent.ENABLED, false);
        booleans.put(LockscreenStatusBlur.ENABLED, false);
        booleans.putAll(SpeedPosition.booleanDefaults());
        booleans.putAll(QsTileIconSize.BOOLEANS);
        booleans.putAll(StatusBarShadeIconSettings.BOOLEANS);
        BOOLEAN_DEFAULTS = Collections.unmodifiableMap(booleans);
        Map<String, Float> numbers = new LinkedHashMap<>();
        numbers.put(WIFI_OFFSET_X, 0f);
        numbers.put(WIFI_OFFSET_Y, 0f);
        numbers.put(WIFI_ICON_SCALE, 100f);
        numbers.put(DATA_OFFSET_X, 0f);
        numbers.put(DATA_OFFSET_Y, 0f);
        numbers.put(DATA_ICON_SCALE, 100f);
        numbers.put(DataBatterySpacing.SPACING, 0f);
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
        numbers.putAll(NetworkSpeedControls.NUMBERS);
        numbers.put(LockscreenStatusBlur.RADIUS, LockscreenStatusBlur.DEFAULT_RADIUS);
        numbers.put(LockscreenStatusBlur.RANGE, LockscreenStatusBlur.DEFAULT_RANGE);
        numbers.put(LockscreenStatusBlur.TRANSITION, LockscreenStatusBlur.DEFAULT_TRANSITION);
        numbers.putAll(SpeedPosition.numberDefaults());
        numbers.put(NativeClockMeasurement.SCALE_BASIS_VERSION, 2f);
        numbers.put(NativeClockMeasurement.LEGACY_SCALE_FACTOR, 0f);
        numbers.putAll(NativeStatusIcons.floatDefaults());
        numbers.putAll(NativeNetworkBadgeControls.floatDefaults());
        numbers.put(NativeDataActivity.X,0f);numbers.put(NativeDataActivity.Y,0f);numbers.put(NativeDataActivity.SCALE,100f);
        numbers.put(BATTERY_HOLD, 3f);
        numbers.put(BATTERY_FADE, 1f);
        numbers.put(BATTERY_OFFSET_X, 0f);numbers.put(BATTERY_OFFSET_Y, 0f);
        numbers.put(BATTERY_SCALE, 100f);numbers.put(BATTERY_WIDTH_SCALE, 100f);numbers.put(BATTERY_HEIGHT_SCALE, 100f);
        numbers.put(TILES_FADE_RANGE, 24f);
        numbers.put(TILES_BLUR_RADIUS, 8f);
        numbers.put(TILES_STRENGTH, 100f);
        numbers.put(TILES_LEFT_RANGE,24f);numbers.put(TILES_RIGHT_RANGE,24f);
        numbers.put(TILES_LEFT_OFFSET_X,0f);numbers.put(TILES_RIGHT_OFFSET_X,0f);
        numbers.put(TILES_OFFSET_Y,0f);numbers.put(TILES_REGION_HEIGHT,0f);numbers.put(TILES_VERTICAL_FEATHER,32f);
        for (String item : new String[]{"clock", "shade_clock", "carrier", CarrierPanels.NOTIFICATION, CarrierPanels.CONTROL,
                CarrierPanels.LOCKSCREEN}) {
            numbers.put(item + "_offset_x", 0f);
            numbers.put(item + "_offset_y", 0f);
            numbers.put(item + "_scale", 100f);
            numbers.put(item + "_weight", 600f);
            numbers.put(item + "_spacing", 0f);
        }
        numbers.putAll(QsTileAppearance.NUMBERS);
        numbers.putAll(QsTileCorners.NUMBERS);
        numbers.putAll(QsMediaAppearance.NUMBERS);
        numbers.putAll(NotificationBigClockSettings.NUMBERS);
        numbers.putAll(NotificationClearAppearance.NUMBERS);
        numbers.put(NotificationClearMotion.LANDSCAPE_OFFSET_X, 0f);
        numbers.put(NotificationClearMotion.LANDSCAPE_OFFSET_Y, 0f);
        numbers.putAll(NotificationIconArea.NUMBERS);
        numbers.putAll(BatteryTextStyle.NUMBERS);
        numbers.putAll(QsTileIconSize.NUMBERS);
        numbers.putAll(ClassicTextSettings.NUMBERS);
        numbers.putAll(ShadeWallpaperSettings.NUMBERS);
        NUMERIC_DEFAULTS = Collections.unmodifiableMap(numbers);
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String item : new String[]{"wifi", "data", "label", "speed", "clock", "shade_clock", "carrier",
                CarrierPanels.NOTIFICATION, CarrierPanels.CONTROL, CarrierPanels.LOCKSCREEN,
                "battery", "battery_text", "battery_bolt"}) {
            colors.put(item + "_color_light", 0xff000000);
            colors.put(item + "_color_dark", 0xffffffff);
        }
        colors.put("battery_charge_color_light",0xff00bd13);colors.put("battery_charge_color_dark",0xff00bd13);
        colors.put("battery_alert_color_light",0xffff3b30);colors.put("battery_alert_color_dark",0xffff3b30);
        colors.putAll(QsTileAppearance.COLORS);
        colors.putAll(QsMediaAppearance.COLORS);
        colors.putAll(NotificationBigClockSettings.COLORS);
        colors.put(C17HighlightRemoval.LIGHT_BACKGROUND, C17HighlightRemoval.DEFAULT_LIGHT_BACKGROUND);
        colors.put(C17HighlightRemoval.DARK_BACKGROUND, C17HighlightRemoval.DEFAULT_DARK_BACKGROUND);
        colors.putAll(NotificationClearAppearance.COLORS);
        colors.put(NativeDataActivity.COLOR_LIGHT,0xff000000);colors.put(NativeDataActivity.COLOR_DARK,0xffffffff);
        colors.putAll(NotificationIconArea.COLORS);
        colors.putAll(ClassicTextSettings.COLORS);
        colors.put(LockscreenStatusBlur.MASK_COLOR, LockscreenStatusBlur.DEFAULT_MASK_COLOR);
        COLOR_DEFAULTS = Collections.unmodifiableMap(colors);
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put(CLOCK_PATTERN, TimeFormat.CLOCK_DEFAULT);
        strings.put(SHADE_CLOCK_PATTERN, TimeFormat.CLOCK_DEFAULT);
        strings.put(CARRIER_PATTERN, TimeFormat.CARRIER_DEFAULT);
        strings.put(CARRIER_MODE, "original");
        strings.put(CARRIER_TEXT, "更好的C17状态栏");
        for (String panel : CarrierPanels.GROUPS) {
            strings.put(CarrierPanels.key(panel, "mode"), strings.get(CARRIER_MODE));
            strings.put(CarrierPanels.key(panel, "pattern"), strings.get(CARRIER_PATTERN));
            strings.put(CarrierPanels.key(panel, "text"), strings.get(CARRIER_TEXT));
        }
        strings.put(FONT_MODE, "system");
        strings.put(FONT_REVISION, "");
        strings.put(FONT_NAME, "未导入字体");
        strings.put(SIGNAL_LAYOUT, "system");
        strings.putAll(NetworkSpeedControls.STRINGS);
        strings.putAll(SpeedPosition.stringDefaults());
        strings.put(BATTERY_STYLE, "pui");
        strings.putAll(NotificationBigClockSettings.STRINGS);
        strings.putAll(NotificationIconArea.STRINGS);
        strings.put(NotificationIconOverrides.RULES, "[]");
        strings.put(IconPackRepository.LAYERS, "[]");
        strings.put(IconPackAssignments.ASSIGNMENTS, "[]");
        strings.putAll(ClassicTextSettings.STRINGS);
        strings.putAll(ShadeWallpaperSettings.STRINGS);
        strings.put("shade_wallpaper_manage", "");
        strings.putAll(NativeStatusIcons.stringDefaults());
        strings.putAll(NativeNetworkBadgeControls.stringDefaults());
        strings.put(LockscreenControls.DATE_FORMAT, "M月d日 {周}");
        STRING_DEFAULTS = Collections.unmodifiableMap(strings);
    }

    public static float number(SharedPreferences preferences, String key, float fallback) {
        return number(preferences.getAll(), key, fallback);
    }

    public static String string(Map<String, ?> values, String key) {
        if (NetworkSpeedControls.DISPLAY_STYLE.equals(key)) return "system";
        Object value = typedValue(values, key, String.class);
        return value instanceof String ? (String) value : STRING_DEFAULTS.get(key);
    }

    public static boolean bool(Map<String, ?> values, String key) {
        if (NotificationGroupStack.MASTER.equals(key)) return false;
        if (NetworkSpeedControls.STYLE_ENABLED.equals(key)) return false;
        if (QsTileCorners.MASTER.equals(key)) return QsTileCorners.enabled(values);
        // Old false/malformed values cannot enable arrows; the new independent switch owns this.
        if (DATA_ACTIVITY_HIDDEN.equals(key)) return !bool(values,NativeDataActivity.MASTER);
        if (StatusBarShadeIconSettings.MASTER.equals(key)) return false;
        Object value = typedValue(values, key, Boolean.class);
        return value instanceof Boolean ? (Boolean) value : Boolean.TRUE.equals(BOOLEAN_DEFAULTS.get(key));
    }

    public static int color(Map<String, ?> values, String key) {
        Object value = typedValue(values, key, Number.class);
        Integer fallback = COLOR_DEFAULTS.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback == null ? 0 : fallback;
    }

    private static Object typedValue(Map<String, ?> values, String key, Class<?> type) {
        Object value = values == null ? null : values.get(key);
        if (type.isInstance(value)) return value;
        String legacy = CarrierPanels.legacyKey(key);
        if(legacy==null)legacy=NotificationClearAppearance.legacyKey(key);
        if(legacy==null)legacy=NativeNetworkBadgeSettings.legacyKey(key);
        Object inherited = values == null || legacy == null ? null : values.get(legacy);
        return type.isInstance(inherited) ? inherited : null;
    }

    public static String alphaKey(String colorKey) {
        return colorKey + "_custom_alpha";
    }

    public static boolean customAlpha(Map<String, ?> values, String colorKey) {
        Object mode = values == null ? null : values.get(alphaKey(colorKey));
        if (mode instanceof Boolean) return (Boolean) mode;
        Object color = values == null ? null : values.get(colorKey);
        // This overlay's default is intentionally translucent, including on a fresh install.
        if (!(color instanceof Number) && LockscreenStatusBlur.MASK_COLOR.equals(colorKey)) return true;
        // A newly selected panel color owns its alpha. Unedited panels inherit the old pair.
        if (!(color instanceof Number)) {
            String legacy = CarrierPanels.legacyKey(colorKey);
            if(legacy==null)legacy=NotificationClearAppearance.legacyKey(colorKey);
            if (legacy != null) return customAlpha(values, legacy);
        }
        // Older settings that already contain an alpha value remain intentional custom colors.
        return color instanceof Number && ((((Number) color).intValue() >>> 24) != 255);
    }

    public static float number(Map<String, ?> values, String key, float fallback) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof Number) {
            float result = ((Number) value).floatValue();
            if (!Float.isNaN(result) && !Float.isInfinite(result)) return result;
        }
        String legacy = CarrierPanels.legacyKey(key);
        if(legacy==null)legacy=NotificationClearAppearance.legacyKey(key);
        if(legacy==null)legacy=NativeNetworkBadgeSettings.legacyKey(key);
        if (legacy != null) return number(values, legacy, fallback);
        return fallback;
    }

    public static float settingNumber(Map<String, ?> values, String key, float fallback) {
        if (NetworkSpeedControls.INTERVAL_MILLIS.equals(key) && values != null && !values.containsKey(key)
                && values.get(NetworkSpeedControls.INTERVAL_SECONDS) instanceof Number)
            return SettingsFrameworkMirror.migratedSpeedMillis(values.get(NetworkSpeedControls.INTERVAL_SECONDS));
        if (QsTileCorners.RADIUS.equals(key)) return QsTileCorners.radius(values);
        if (NotificationBigClockSettings.VISIBLE_COUNT.equals(NotificationBigClockSettings.portraitKey(key)))
            return NotificationBigClockSettings.visibleCount(values == null ? null : values.get(key));
        if (values == null || !values.containsKey(key)) {
            if(key.equals(TILES_LEFT_RANGE)||key.equals(TILES_RIGHT_RANGE))return number(values,TILES_FADE_RANGE,fallback);
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
        Context credentialContext = null;
        if (!migration.getBoolean(CREDENTIAL_MIGRATED, false)
                && userManager != null && userManager.isUserUnlocked()) {
            // Public createPackageContext starts with flags=0, rather than inheriting a DE
            // application's storage context. This app does not default to DE storage.
            try { credentialContext=context.createPackageContext(context.getPackageName(),0); }
            catch(android.content.pm.PackageManager.NameNotFoundException unavailable) {
                ModuleDiagnostics.error("settings","Credential settings context unavailable; current configuration retained",unavailable);
            }
        }
        SettingsSnapshot.recoverIfEmpty(deviceContext, preferences);
        if (!migration.getBoolean(CREDENTIAL_MIGRATED, false)
                && userManager != null && userManager.isUserUnlocked()
                && credentialContext != null && !credentialContext.isDeviceProtectedStorage()) {
            SharedPreferences legacy = credentialContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Map<String,?> legacyValues=legacy.getAll();
            SharedPreferences.Editor editor = preferences.edit();
            Map<String,Object> copied=new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : legacyValues.entrySet()) {
                // Values already written to device storage take precedence.
                if (!preferences.contains(entry.getKey())) {
                    copyPreference(editor, entry.getKey(), entry.getValue());
                    copied.put(entry.getKey(),entry.getValue());
                }
            }
            // Record migration only after the copied settings are durable.
            if (!legacyValues.isEmpty()&&SettingsSnapshot.fromPreferences(legacyValues)!=null
                    &&PreferenceWrites.commit(preferences,editor,copied,false)) {
                Map<String,Object> completed=new LinkedHashMap<>();completed.put(CREDENTIAL_MIGRATED,true);
                PreferenceWrites.commit(migration,migration.edit().putBoolean(CREDENTIAL_MIGRATED, true),completed,false);
            }
        }
        // A locked/temporarily empty store must stay empty, rather than acquire a defaults-only marker.
        if (!preferences.getAll().isEmpty()) {
            boolean hidden=bool(preferences.getAll(),DATA_ACTIVITY_HIDDEN);
            if (!Boolean.valueOf(hidden).equals(preferences.getAll().get(DATA_ACTIVITY_HIDDEN)))
                preferences.edit().putBoolean(DATA_ACTIVITY_HIDDEN, hidden).apply();
            QsTileCorners.migrate(preferences);
        }
        NumericTrial.recover(deviceContext, preferences);
        NotificationClearAppearance.migrate(preferences,migration,userManager!=null&&userManager.isUserUnlocked());
        return preferences;
    }

    @SuppressWarnings("unchecked")
    static void copyPreference(SharedPreferences.Editor editor, String key, Object value) {
        if (NotificationGroupStack.MASTER.equals(key)) {
            editor.putBoolean(key, false);
        } else if (DATA_ACTIVITY_HIDDEN.equals(key)) {
            editor.putBoolean(key, !(value instanceof Boolean)||(Boolean)value);
        } else if (value instanceof Integer) {
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
