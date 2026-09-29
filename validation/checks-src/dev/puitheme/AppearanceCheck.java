package dev.puitheme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Runs the compiled module's pure appearance decisions on the host; it does not simulate UI hooks. */
public final class AppearanceCheck {
    private static int checks;
    private static Class<?> module;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void set(String name, Object value) throws Exception {
        Field field = module.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = module.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
    private static int nativeTint;
    private static int style(String item) throws Exception {
        return (Integer) call("styleColor", new Class<?>[]{String.class, int.class}, item, nativeTint);
    }
    private static boolean cellular() throws Exception {
        return (Boolean) call("cellularMode", new Class<?>[]{});
    }
    public static void main(String[] args) throws Exception {
        module = Class.forName("dev.puitheme.StatusBarModule");
        nativeTint = 0xe6000000;
        for (String item : new String[]{"wifi","data","label","speed"}) equal(0xe6000000, style(item));
        nativeTint = 0xccffffff;
        for (String item : new String[]{"wifi","data","label","speed"}) equal(0xccffffff, style(item));
        Map<String,Integer> palette = new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        palette.put("wifi_color_dark", 0xff3366ff);
        set("styleColors", palette);
        equal(0xcc3366ff, style("wifi"));
        Map<String,Boolean> alpha = new HashMap<>();
        alpha.put("wifi_color_dark", true);
        set("customColorAlpha", alpha);
        equal(0xff3366ff, style("wifi"));
        palette.put("wifi_color_dark", 0x803366ff);
        equal(0x803366ff, style("wifi"));
        palette.put("wifi_color_dark", 0x003366ff);
        equal(0x003366ff, style("wifi"));
        set("customColorAlpha", Collections.emptyMap());
        set("styleColors", StatusBarSettings.COLOR_DEFAULTS);
        nativeTint = 0x993366ff;
        // Defaults retain regional native RGB and alpha without global dispatcher state.
        for (String item : new String[]{"wifi","data","label","speed"}) equal(nativeTint, style(item));
        for (boolean compose : new boolean[]{false,true}) {
            for (boolean wifi : new boolean[]{false,true}) {
                for (boolean airplane : new boolean[]{false,true}) {
                    for (String label : new String[]{"", "5G"}) {
                        set("composeStatusBar", compose);
                        set("wifiConnected", wifi);
                        set("airplaneMode", airplane);
                        set("networkLabel", label);
                        equal(!compose && !wifi && !airplane && !label.isEmpty(), cellular());
                    }
                }
            }
        }
        equal(6.25f, call("clamp", new Class<?>[]{float.class,float.class,float.class}, 6.25f,-80f,80f));
        equal(-80f, call("clamp", new Class<?>[]{float.class,float.class,float.class}, -100f,-80f,80f));
        equal(80f, call("clamp", new Class<?>[]{float.class,float.class,float.class}, 100f,-80f,80f));
        String[] expected = {"", "2G", "2G", "3G", "2G", "3G", "3G", "2G", "3G", "3G", "3G", "2G", "3G", "4G", "3G", "3G", "2G", "3G", "4G", "4G", "5G"};
        for (int type = 0; type < expected.length; type++) equal(expected[type], call("labelForNetworkType", new Class<?>[]{int.class}, type));
        System.out.println(checks + " checks passed (compiled color policy, opacity, backend isolation, radio states, bounds)");
    }
}
