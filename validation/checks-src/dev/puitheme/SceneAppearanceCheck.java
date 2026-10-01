package dev.puitheme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Replays simultaneous native status-bar and shade colors through the compiled module. */
public final class SceneAppearanceCheck {
    private static int checks, failures;
    private static Class<?> module;
    private static void set(String name, Object value) throws Exception {
        try {
            Field field = module.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (NoSuchFieldException removedGlobalState) { }
    }
    private static void equal(String name, int expected, int actual) {
        checks++;
        if (expected != actual) {
            failures++;
            System.out.println("FAIL " + name + ": expected=0x" + Integer.toHexString(expected)
                    + " actual=0x" + Integer.toHexString(actual));
        }
    }
    private static int color(String item, int nativeTint) throws Exception {
        Method method = module.getDeclaredMethod("styleColor", String.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, item, nativeTint);
    }
    public static void main(String[] args) throws Exception {
        module = Class.forName("dev.puitheme.StatusBarModule");
        // The global dispatcher stays black while the expanded shade has white static icons.
        set("clusterTintKnown", true);
        set("dispatcherTintKnown", true);
        set("clusterDarkBackground", false);
        set("clusterNativeTint", 0xe6000000);
        set("styleColors", StatusBarSettings.COLOR_DEFAULTS);
        set("customColorAlpha", Collections.emptyMap());
        for (String item : new String[]{"wifi", "data", "label", "speed"}) {
            for (int nativeTint : new int[]{0xe6000000, 0xffffffff, 0xccffffff, 0xd9888888,
                    0x993366ff, 0x00ffffff, 0xe6000000, 0xffffffff}) {
                equal(item + " follows its own surface", nativeTint, color(item, nativeTint));
            }
        }
        Map<String,Integer> palette = new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        palette.put("wifi_color_light", 0xff3366ff);
        palette.put("wifi_color_dark", 0xfff08020);
        set("styleColors", palette);
        equal("Default-off color preserves status bar", 0xe6000000, color("wifi", 0xe6000000));
        equal("Default-off color preserves shade", 0xccffffff, color("wifi", 0xccffffff));
        Map<String,Object> enabled = new HashMap<>(); enabled.put("wifi_enabled", true);
        set("FEATURES", FeatureOptions.from(enabled));
        equal("Custom RGB retains black-icon opacity", 0xe63366ff, color("wifi", 0xe6000000));
        equal("Custom RGB retains white-icon opacity", 0xccf08020, color("wifi", 0xccffffff));
        equal("Shade custom RGB has the shade's own opacity", 0xfff08020, color("wifi", 0xffffffff));
        equal("Transition blends RGB without replacing native alpha", 0xd992738f, color("wifi", 0xd9808080));
        Map<String,Boolean> alpha = new HashMap<>();
        alpha.put("wifi_color_dark", true);
        palette.put("wifi_color_dark", 0x80f08020);
        set("customColorAlpha", alpha);
        equal("Explicit ARGB retained in shade", 0x80f08020, color("wifi", 0xffffffff));
        equal("Explicit ARGB retained in status bar", 0x80f08020, color("wifi", 0xccffffff));
        equal("RGB still inherits native alpha", 0xe63366ff, color("wifi", 0xe6000000));
        palette.put("wifi_color_dark", 0xfff08020);
        equal("Explicit opaque ARGB retained", 0xfff08020, color("wifi", 0xccffffff));
        palette.put("wifi_color_dark", 0x00f08020);
        equal("Explicit transparent ARGB retained", 0x00f08020, color("wifi", 0xccffffff));
        if (failures != 0) throw new AssertionError(failures + " of " + checks + " appearance checks failed");
        System.out.println(checks + " surface color and opacity regression checks passed");
    }
}
