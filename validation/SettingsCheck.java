package dev.puitheme;

import java.util.HashMap;
import java.util.Map;

public final class SettingsCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        String[][] labels = {
            {"E","E"}, {"5.5G","5G"}, {"5G","5G"}, {"5GA","5G"}, {"5GBasic","5G"},
            {"5GE","5G"}, {"5G+","5G"}, {"5G++","5G"}, {"5GSA","5G"}, {"5GUC","5G"},
            {"5GUWB","5G"}, {"4.5G","4G"}, {"4.5G+","4G"}, {"4G","4G"}, {"4G+","4G"},
            {"G","G"}, {"H","3G"}, {"H+","3G"}, {"LTE","4G"}, {"5Ge","5G"}, {"LTE+","4G"},
            {"5G_PLUS","5G"}, {"1X","1X"}, {"3G","3G"}, {"3G+","3G"},
            {"TIGO_5G_CONNECTED","5G"}, {"TIGO_5G_NOT_RESTRICTED","5G"}, {"2G","2G"}, {"Unknown",""},
            {"NR","5G"}, {"NR_SA","5G"}, {"NR_NSA","5G"}, {"NR+","5G"}, {"NR5G","5G"},
            {"LTE-A","4G"}, {"4","4G"}, {"3","3G"}, {"HSPA+","3G"}, {"HSPAP","3G"},
            {"UMTS","3G"}, {"WCDMA","3G"}, {"TD-SCDMA","3G"}, {"EVDO_A","3G"},
            {" 5g a ","5G"}, {"NoService",""}, {"OutOfService",""}, {"None",""}, {"",""}, {null,""}
        };
        for (String[] row : labels) equal(row[1], NetworkLabel.normalize(row[0]));
        Map<String,Object> settings = new HashMap<>();
        settings.put("old", 90);
        settings.put("precise", 6.25f);
        settings.put("nan", Float.NaN);
        settings.put("inf", Float.POSITIVE_INFINITY);
        settings.put("bad", "wrong");
        equal(90f, StatusBarSettings.number(settings, "old", 100f));
        equal(6.25f, StatusBarSettings.number(settings, "precise", 0f));
        for (String key : new String[]{"nan","inf","bad","missing"}) equal(8f, StatusBarSettings.number(settings, key, 8f));
        settings.put(StatusBarSettings.OFFSET_X, -2.75f);
        equal(-2.75f, StatusBarSettings.settingNumber(settings, StatusBarSettings.DATA_OFFSET_X, 0f));
        settings.put(StatusBarSettings.DATA_OFFSET_X, 1.87f);
        equal(1.87f, StatusBarSettings.settingNumber(settings, StatusBarSettings.DATA_OFFSET_X, 0f));
        String color = "wifi_color_dark";
        equal(false, StatusBarSettings.customAlpha(settings, color));
        settings.put(color, 0xffffffff);
        equal(false, StatusBarSettings.customAlpha(settings, color));
        settings.put(color, 0x80ffffff);
        equal(true, StatusBarSettings.customAlpha(settings, color));
        settings.put(StatusBarSettings.alphaKey(color), false);
        equal(false, StatusBarSettings.customAlpha(settings, color));
        settings.put(color, 0xffffffff);
        settings.put(StatusBarSettings.alphaKey(color), true);
        equal(true, StatusBarSettings.customAlpha(settings, color));
        for (String tileKey : new String[]{StatusBarSettings.TILES_FADE_RANGE, StatusBarSettings.TILES_BLUR_RADIUS,
                StatusBarSettings.TILES_STRENGTH}) {
            float fallback = StatusBarSettings.NUMERIC_DEFAULTS.get(tileKey);
            equal(fallback, StatusBarSettings.settingNumber(settings, tileKey, fallback));
            settings.put(tileKey, 0f);
            equal(0f, StatusBarSettings.settingNumber(settings, tileKey, fallback));
            settings.put(tileKey, 13456.78f);
            equal(13456.78f, StatusBarSettings.settingNumber(settings, tileKey, fallback));
            settings.put(tileKey, Float.NaN);
            equal(fallback, StatusBarSettings.settingNumber(settings, tileKey, fallback));
            settings.put(tileKey, Float.POSITIVE_INFINITY);
            equal(fallback, StatusBarSettings.settingNumber(settings, tileKey, fallback));
            settings.put(tileKey, "wrong");
            equal(fallback, StatusBarSettings.settingNumber(settings, tileKey, fallback));
        }
        equal(24f, StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.TILES_FADE_RANGE));
        equal(8f, StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.TILES_BLUR_RADIUS));
        equal(100f, StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.TILES_STRENGTH));
        System.out.println(checks + " checks passed (network names, numeric compatibility, alpha modes)");
    }
}
