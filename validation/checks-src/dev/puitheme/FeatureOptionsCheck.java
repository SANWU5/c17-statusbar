package dev.puitheme;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

/** Checks upgrade defaults, isolated switches, group gating, and immutable snapshots. */
public final class FeatureOptionsCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        FeatureOptions defaults = FeatureOptions.from(new HashMap<String, Object>());
        Map<String,Boolean> expectedBooleans = new HashMap<>(FeatureOptions.DEFAULTS);
        expectedBooleans.put(StatusBarSettings.DIAGNOSTICS_ENABLED, false);
        expectedBooleans.putAll(QsTileAppearance.BOOLEANS);
        expectedBooleans.putAll(NotificationBigClockSettings.BOOLEANS);
        equal(expectedBooleans, StatusBarSettings.BOOLEAN_DEFAULTS);
        equal(false, StatusBarSettings.bool(new HashMap<String,Object>(), QsTileAppearance.MASTER));
        equal(false, StatusBarSettings.bool(new HashMap<String,Object>(), StatusBarSettings.DIAGNOSTICS_ENABLED));
        equal("pui", StatusBarSettings.STRING_DEFAULTS.get(StatusBarSettings.BATTERY_STYLE));
        equal(false, defaults.isEnabled("clock_enabled"));
        equal(true, defaults.enabled("clock"));
        equal(true, defaults.isEnabled("battery_charge_inside"));
        equal(true, defaults.effective("wifi", "wifi_activity_hidden"));
        equal(false, defaults.enabled("unknown"));
        equal(false, defaults.isEnabled("unknown"));
        equal(10, FeatureOptions.GROUPS.length);
        equal(79, FeatureOptions.DEFAULTS.size());
        equal("shade_clock_controls_enabled", FeatureOptions.masterKey("shade_clock"));
        equal(false, defaults.enabled("shade_clock"));
        equal(false, defaults.isEnabled("shade_clock_enabled"));
        Map<String,Object> independentClock = new HashMap<>();
        independentClock.put("clock_controls_enabled", false); independentClock.put("shade_clock_controls_enabled", true);
        independentClock.put("shade_clock_enabled", true);
        FeatureOptions shade = FeatureOptions.from(independentClock);
        equal(false, shade.enabled("clock")); equal(true, shade.enabled("shade_clock"));
        equal(true, shade.effective("shade_clock", "shade_clock_enabled"));
        equal(true, shade.position("shade_clock")); equal(true, shade.size("shade_clock"));
        equal(true, shade.color("shade_clock")); equal(true, shade.textStyle("shade_clock"));
        equal(true, defaults.enabled("tiles"));
        equal(true, defaults.effective("tiles", "tiles_fade_enabled"));
        equal(false, defaults.effective("tiles", "tiles_blur_enabled"));
        equal(true, defaults.effective("tiles", "tiles_portrait_enabled"));
        equal(true, defaults.effective("tiles", "tiles_landscape_enabled"));
        equal(false, defaults.enabled(CarrierPanels.LOCKSCREEN));
        for (String suffix : new String[]{"position", "size", "color", "text_style"})
            equal(false, FeatureOptions.DEFAULTS.containsKey("tiles_" + suffix + "_enabled"));
        equal(false, defaults.position("tiles"));
        equal(false, defaults.size("tiles"));
        equal(false, defaults.color("tiles"));
        equal(false, defaults.textStyle("tiles"));

        for (String group : FeatureOptions.GROUPS) {
            Map<String, Object> values = new HashMap<>();
            values.put(FeatureOptions.masterKey(group), false);
            FeatureOptions off = FeatureOptions.from(values);
            equal(false, off.enabled(group));
            for (Map.Entry<String, String> entry : FeatureOptions.GROUP_BY_KEY.entrySet()) {
                String key = entry.getKey(), owner = entry.getValue();
                // Turning off one group must leave every other group untouched.
                equal(owner.equals(group) ? false : defaults.effective(owner, key), off.effective(owner, key));
            }
            equal(false, off.position(group));
            equal(false, off.size(group));
            equal(false, off.color(group));
            equal(false, off.textStyle(group));
        }

        for (String key : FeatureOptions.DEFAULTS.keySet()) {
            String group = FeatureOptions.GROUP_BY_KEY.get(key);
            equal(true, group != null);
            Map<String, Object> values = new HashMap<>();
            values.put(key, false);
            FeatureOptions oneOff = FeatureOptions.from(values);
            equal(false, oneOff.isEnabled(key));
            equal(false, oneOff.effective(group, key));
            for (String other : FeatureOptions.DEFAULTS.keySet())
                if (!other.equals(key)) equal(StatusBarSettings.bool(values, other), oneOff.isEnabled(other));
            values.put(key, true);
            // A pending UI editor cannot mutate already configured hook state.
            equal(false, oneOff.isEnabled(key));
            FeatureOptions oneOn = FeatureOptions.from(values);
            equal(true, oneOn.isEnabled(key));
            values.put(key, "false");
            equal(FeatureOptions.DEFAULTS.get(key), FeatureOptions.from(values).isEnabled(key));
            equal(FeatureOptions.DEFAULTS.get(key), StatusBarSettings.bool(values, key));
            Bundle bundle = new Bundle();
            bundle.putBoolean(key, false);
            equal(oneOff.values(), FeatureOptions.from(bundle).values());
        }

        Map<String, Object> legacy = new HashMap<>();
        legacy.put("clock_enabled", true);
        legacy.put("battery_charge_inside", false);
        FeatureOptions upgraded = FeatureOptions.from(legacy);
        equal(true, upgraded.enabled("clock"));
        equal(true, upgraded.effective("clock", "clock_enabled"));
        equal(false, upgraded.enabled("shade_clock"));
        equal(false, upgraded.effective("battery", "battery_charge_inside"));
        equal(true, upgraded.enabled("battery"));
        equal(FeatureOptions.from(new HashMap<String, Object>()).values(), FeatureOptions.from((Bundle) null).values());
        try {
            defaults.values().put("data_enabled", false);
            throw new AssertionError("Snapshot is mutable");
        } catch (UnsupportedOperationException expected) { checks++; }
        try {
            FeatureOptions.DEFAULTS.put("wifi_enabled", false);
            throw new AssertionError("Defaults are mutable");
        } catch (UnsupportedOperationException expected) { checks++; }
        System.out.println(checks + " checks passed (feature defaults, independent switches, group gating, bundle transport)");
    }
}
