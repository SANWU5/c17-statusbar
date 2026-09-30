package dev.puitheme;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

/** Independent carrier panels inherit shared values only until a panel overrides them. */
public final class CarrierPanelSettingsCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static String n(String suffix) { return CarrierPanels.key(CarrierPanels.NOTIFICATION, suffix); }
    private static String c(String suffix) { return CarrierPanels.key(CarrierPanels.CONTROL, suffix); }
    private static String l(String suffix) { return CarrierPanels.key(CarrierPanels.LOCKSCREEN, suffix); }

    public static void main(String[] args) {
        equal(9, FeatureOptions.GROUPS.length);
        equal(3, CarrierPanels.GROUPS.length);
        equal(73, FeatureOptions.DEFAULTS.size());
        equal(n("mode"), CarrierPanels.key(CarrierPanels.NOTIFICATION, "_mode"));
        equal(null == CarrierPanels.legacyKey(n("enabled")), true);
        equal(null == CarrierPanels.legacyKey("carrier_mode"), true);
        equal(null == CarrierPanels.legacyKey(null), true);
        for (String panel : CarrierPanels.GROUPS) {
            for (String suffix : new String[]{"enabled", "replace_enabled", "position_enabled",
                    "size_enabled", "color_enabled", "text_style_enabled"}) {
                String key = CarrierPanels.key(panel, suffix);
                equal("carrier", FeatureOptions.GROUP_BY_KEY.get(key));
                equal(!(CarrierPanels.LOCKSCREEN.equals(panel) && "enabled".equals(suffix)),
                        StatusBarSettings.bool(null, key));
            }
            for (String suffix : new String[]{"offset_x", "offset_y", "scale", "weight", "spacing"})
                equal(true, StatusBarSettings.NUMERIC_DEFAULTS.containsKey(CarrierPanels.key(panel, suffix)));
            for (String suffix : new String[]{"mode", "pattern", "text"})
                equal(true, StatusBarSettings.STRING_DEFAULTS.containsKey(CarrierPanels.key(panel, suffix)));
            for (String suffix : new String[]{"color_light", "color_dark"})
                equal(true, StatusBarSettings.COLOR_DEFAULTS.containsKey(CarrierPanels.key(panel, suffix)));
        }

        Map<String, Object> values = new HashMap<>();
        values.put("carrier_mode", "time");
        values.put("carrier_pattern", "yyyy年MM月dd日 EEEE");
        values.put("carrier_text", "共同文本");
        values.put("carrier_offset_x", 3.25f);
        values.put("carrier_offset_y", -1.87f);
        values.put("carrier_scale", 117.25f);
        values.put("carrier_weight", 550.5f);
        values.put("carrier_spacing", 0.02f);
        values.put("carrier_color_light", 0x77020304);
        values.put("carrier_color_dark", 0xffb1b2b3);
        values.put("carrier_color_dark_custom_alpha", true);
        for (String panel : new String[]{CarrierPanels.NOTIFICATION, CarrierPanels.CONTROL}) {
            for (String suffix : new String[]{"mode", "pattern", "text"}) {
                String key = CarrierPanels.key(panel, suffix);
                equal(values.get("carrier_" + suffix), StatusBarSettings.string(values, key));
            }
            for (String suffix : new String[]{"offset_x", "offset_y", "scale", "weight", "spacing"}) {
                String key = CarrierPanels.key(panel, suffix);
                equal(values.get("carrier_" + suffix), StatusBarSettings.settingNumber(values, key, -99f));
                equal(values.get("carrier_" + suffix), StatusBarSettings.number(values, key, -99f));
            }
            equal(0x77020304, StatusBarSettings.color(values, CarrierPanels.key(panel, "color_light")));
            equal(0xffb1b2b3, StatusBarSettings.color(values, CarrierPanels.key(panel, "color_dark")));
            equal(true, StatusBarSettings.customAlpha(values, CarrierPanels.key(panel, "color_light")));
            equal(true, StatusBarSettings.customAlpha(values, CarrierPanels.key(panel, "color_dark")));
        }

        values.put(n("mode"), "text");
        values.put(n("pattern"), "HH:mm:ss");
        values.put(n("text"), "通知页独立文本");
        values.put(n("offset_x"), -6.37f);
        values.put(n("offset_y"), 8.99f);
        values.put(n("scale"), 91.11f);
        values.put(n("weight"), 700.5f);
        values.put(n("spacing"), -0.07f);
        values.put(n("color_light"), 0xff123456);
        values.put(n("color_dark"), 0x99123456);
        values.put(n("color_dark_custom_alpha"), false);
        equal("text", StatusBarSettings.string(values, n("mode")));
        equal("time", StatusBarSettings.string(values, c("mode")));
        equal("通知页独立文本", StatusBarSettings.string(values, n("text")));
        equal("共同文本", StatusBarSettings.string(values, c("text")));
        for (String suffix : new String[]{"offset_x", "offset_y", "scale", "weight", "spacing"}) {
            equal(values.get(n(suffix)), StatusBarSettings.settingNumber(values, n(suffix), -99f));
            equal(values.get("carrier_" + suffix), StatusBarSettings.settingNumber(values, c(suffix), -99f));
        }
        equal(0xff123456, StatusBarSettings.color(values, n("color_light")));
        equal(0x77020304, StatusBarSettings.color(values, c("color_light")));
        equal(false, StatusBarSettings.customAlpha(values, n("color_light")));
        equal(true, StatusBarSettings.customAlpha(values, c("color_light")));
        equal(false, StatusBarSettings.customAlpha(values, n("color_dark")));
        equal(true, StatusBarSettings.customAlpha(values, c("color_dark")));
        values.put(n("color_light_custom_alpha"), true);
        equal(true, StatusBarSettings.customAlpha(values, n("color_light")));
        values.put(n("color_light_custom_alpha"), false);
        equal(false, StatusBarSettings.customAlpha(values, n("color_light")));
        values.remove(n("color_light_custom_alpha"));

        for (String suffix : new String[]{"replace_enabled", "position_enabled", "size_enabled",
                "color_enabled", "text_style_enabled"}) {
            String legacy = "carrier_" + suffix;
            values.put(legacy, false);
            values.put(n(suffix), true);
            FeatureOptions options = FeatureOptions.from(values);
            equal(true, options.effective(CarrierPanels.NOTIFICATION, n(suffix)));
            equal(false, options.effective(CarrierPanels.CONTROL, c(suffix)));
            values.put(c(suffix), true);
            equal(true, FeatureOptions.from(values).effective(CarrierPanels.CONTROL, c(suffix)));
            values.put(n(suffix), false);
            equal(false, FeatureOptions.from(values).effective(CarrierPanels.NOTIFICATION, n(suffix)));
            equal(true, FeatureOptions.from(values).effective(CarrierPanels.CONTROL, c(suffix)));
            values.remove(n(suffix)); values.remove(c(suffix)); values.remove(legacy);
        }
        values.put(n("enabled"), false);
        FeatureOptions panelOff = FeatureOptions.from(values);
        equal(false, panelOff.enabled(CarrierPanels.NOTIFICATION));
        equal(true, panelOff.enabled(CarrierPanels.CONTROL));
        equal(false, panelOff.position(CarrierPanels.NOTIFICATION));
        equal(true, panelOff.position(CarrierPanels.CONTROL));
        values.put("carrier_enabled", false);
        FeatureOptions globalOff = FeatureOptions.from(values);
        equal(false, globalOff.enabled(CarrierPanels.NOTIFICATION));
        equal(false, globalOff.enabled(CarrierPanels.CONTROL));
        equal(false, globalOff.effective(CarrierPanels.CONTROL, c("replace_enabled")));
        equal(true, globalOff.isEnabled(c("enabled")));
        values.put("carrier_enabled", true);
        values.put(n("enabled"), true);

        // Older shared and shade-specific customisation must leave lock-screen defaults alone.
        FeatureOptions untouchedLock = FeatureOptions.from(values);
        equal(false, untouchedLock.enabled(CarrierPanels.LOCKSCREEN));
        equal(false, untouchedLock.position(CarrierPanels.LOCKSCREEN));
        equal(false, untouchedLock.size(CarrierPanels.LOCKSCREEN));
        equal(false, untouchedLock.color(CarrierPanels.LOCKSCREEN));
        equal(false, untouchedLock.textStyle(CarrierPanels.LOCKSCREEN));
        equal(false, untouchedLock.effective(CarrierPanels.LOCKSCREEN, l("replace_enabled")));
        for (String suffix : new String[]{"enabled", "mode", "pattern", "text", "offset_x", "offset_y", "scale",
                "weight", "spacing", "color_light", "color_dark", "color_light_custom_alpha", "replace_enabled"})
            equal(true, CarrierPanels.legacyKey(l(suffix)) == null);
        for (String suffix : new String[]{"mode", "pattern", "text"})
            equal(StatusBarSettings.STRING_DEFAULTS.get(l(suffix)), StatusBarSettings.string(values, l(suffix)));
        equal("original", StatusBarSettings.string(values, l("mode")));
        for (String suffix : new String[]{"offset_x", "offset_y", "scale", "weight", "spacing"})
            equal(StatusBarSettings.NUMERIC_DEFAULTS.get(l(suffix)), StatusBarSettings.settingNumber(values,
                    l(suffix), StatusBarSettings.NUMERIC_DEFAULTS.get(l(suffix))));
        equal(0xff000000, StatusBarSettings.color(values, l("color_light")));
        equal(0xffffffff, StatusBarSettings.color(values, l("color_dark")));
        equal(false, StatusBarSettings.customAlpha(values, l("color_light")));
        equal(false, StatusBarSettings.customAlpha(values, l("color_dark")));
        for (String suffix : new String[]{"replace_enabled", "position_enabled", "size_enabled",
                "color_enabled", "text_style_enabled"}) {
            values.put("carrier_" + suffix, false);
            equal(true, StatusBarSettings.bool(values, l(suffix)));
            values.remove("carrier_" + suffix);
        }

        values.put(l("enabled"), true);
        values.put(l("mode"), "text");
        values.put(l("text"), "锁屏独立文字");
        values.put(l("pattern"), "yyyy年MM月dd日 EEEE a");
        values.put(l("offset_x"), 22.31f);
        values.put(l("offset_y"), -11.27f);
        values.put(l("scale"), 137.91f);
        values.put(l("weight"), 472.5f);
        values.put(l("spacing"), 0.12f);
        values.put(l("color_light"), 0x55010203);
        values.put(l("color_dark"), 0xff987654);
        values.put(l("color_dark_custom_alpha"), true);
        FeatureOptions customLock = FeatureOptions.from(values);
        equal(true, customLock.enabled(CarrierPanels.LOCKSCREEN));
        equal(true, customLock.effective(CarrierPanels.LOCKSCREEN, l("replace_enabled")));
        equal("锁屏独立文字", StatusBarSettings.string(values, l("text")));
        equal("通知页独立文本", StatusBarSettings.string(values, n("text")));
        equal("共同文本", StatusBarSettings.string(values, c("text")));
        for (String suffix : new String[]{"offset_x", "offset_y", "scale", "weight", "spacing"}) {
            equal(values.get(l(suffix)), StatusBarSettings.settingNumber(values, l(suffix), -99f));
            equal(values.get(n(suffix)), StatusBarSettings.settingNumber(values, n(suffix), -99f));
            equal(values.get("carrier_" + suffix), StatusBarSettings.settingNumber(values, c(suffix), -99f));
        }
        equal(0x55010203, StatusBarSettings.color(values, l("color_light")));
        equal(true, StatusBarSettings.customAlpha(values, l("color_light")));
        equal(true, StatusBarSettings.customAlpha(values, l("color_dark")));
        values.put(l("color_dark_custom_alpha"), false);
        equal(false, StatusBarSettings.customAlpha(values, l("color_dark")));
        values.put(l("position_enabled"), false);
        equal(false, FeatureOptions.from(values).position(CarrierPanels.LOCKSCREEN));
        equal(true, FeatureOptions.from(values).position(CarrierPanels.NOTIFICATION));
        values.put("carrier_enabled", false);
        equal(false, FeatureOptions.from(values).enabled(CarrierPanels.LOCKSCREEN));
        equal(true, FeatureOptions.from(values).isEnabled(l("enabled")));
        values.put("carrier_enabled", true);
        values.put(l("enabled"), "true");
        values.put(l("mode"), 4);
        values.put(l("offset_x"), Float.NaN);
        values.put(l("color_light"), "bad");
        values.put(l("color_light_custom_alpha"), "true");
        equal(false, FeatureOptions.from(values).enabled(CarrierPanels.LOCKSCREEN));
        equal("original", StatusBarSettings.string(values, l("mode")));
        equal(0f, StatusBarSettings.settingNumber(values, l("offset_x"), 0f));
        equal(0xff000000, StatusBarSettings.color(values, l("color_light")));
        equal(false, StatusBarSettings.customAlpha(values, l("color_light")));

        // Bad storage types and non-finite numbers fall back through the same legacy parser.
        values.put(n("mode"), 7);
        values.put(n("offset_x"), Float.NaN);
        values.put(n("offset_y"), Float.POSITIVE_INFINITY);
        values.put(n("scale"), "91.11");
        values.put(n("color_light"), "#FFFFFF");
        values.put(n("color_light_custom_alpha"), "false");
        values.put("carrier_replace_enabled", false);
        values.put(n("replace_enabled"), "true");
        equal("time", StatusBarSettings.string(values, n("mode")));
        equal(3.25f, StatusBarSettings.settingNumber(values, n("offset_x"), 0f));
        equal(-1.87f, StatusBarSettings.settingNumber(values, n("offset_y"), 0f));
        equal(117.25f, StatusBarSettings.settingNumber(values, n("scale"), 100f));
        equal(0x77020304, StatusBarSettings.color(values, n("color_light")));
        equal(true, StatusBarSettings.customAlpha(values, n("color_light")));
        equal(false, StatusBarSettings.bool(values, n("replace_enabled")));
        values.put("carrier_offset_x", Float.NaN);
        values.put("carrier_mode", 9);
        values.put("carrier_color_light", "bad");
        equal(0f, StatusBarSettings.settingNumber(values, n("offset_x"), 0f));
        equal("original", StatusBarSettings.string(values, n("mode")));
        equal(0xff000000, StatusBarSettings.color(values, n("color_light")));
        equal(false, StatusBarSettings.customAlpha(values, n("color_light")));

        // Provider transport contains already resolved values, with identical feature gating.
        Bundle transported = new Bundle();
        for (String key : StatusBarSettings.BOOLEAN_DEFAULTS.keySet())
            transported.putBoolean(key, StatusBarSettings.bool(values, key));
        equal(FeatureOptions.from(values).values(), FeatureOptions.from(transported).values());
        System.out.println(checks + " checks passed (independent shade and lock-screen settings, safe upgrade defaults, alpha ownership, typed fallback)");
    }
}
