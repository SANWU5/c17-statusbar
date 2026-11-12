package dev.puitheme;

import java.util.ArrayList;
import java.util.List;

/** App290 runtime.values: owner-scoped one-process values, change-only notifications, cleanup without residue. */
public final class CustomPluginValuesCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void rejects(Runnable action, String why) {
        try { action.run(); check(false, why); } catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        CustomPluginValues.resetForCheck();
        List<Object> seen = new ArrayList<>();
        Runnable stop = CustomPluginValues.observe("dev.c17.validation.qs-card/media_accent", seen::add);
        CustomPluginValues.publish("dev.c17.validation.qs-card", "media_accent", 0xFF123456);
        check(seen.size() == 1 && Integer.valueOf(0xFF123456).equals(seen.get(0)), "observer sees a published colour");
        CustomPluginValues.publish("dev.c17.validation.qs-card", "media_accent", 0xFF123456);
        check(seen.size() == 1, "republishing the same value notifies nobody");
        check(Integer.valueOf(0xFF123456).equals(CustomPluginValues.get("dev.c17.validation.qs-card/media_accent")), "readable by key");
        CustomPluginValues.publish("dev.c17.validation.qs-card", "media_accent", 3.5d);
        check(Double.valueOf(3.5d).equals(seen.get(1)), "numbers other than int/long are kept as double");
        CustomPluginValues.publish("dev.c17.validation.qs-card", "media_accent", null);
        check(seen.size() == 3 && seen.get(2) == null && CustomPluginValues.get("dev.c17.validation.qs-card/media_accent") == null, "null clears");
        CustomPluginValues.publish(CustomPluginValues.HOST, "media_accent", 0xFF00FF00);
        check(seen.size() == 3, "other keys do not reach this observer");
        check(Integer.valueOf(0xFF00FF00).equals(CustomPluginValues.get("host/media_accent")), "the host publishes under host/");
        stop.run();
        CustomPluginValues.publish("dev.c17.validation.qs-card", "media_accent", 7);
        check(seen.size() == 3 && CustomPluginValues.listenerCount() == 0, "a cancelled observer is gone");

        List<Object> other = new ArrayList<>();
        CustomPluginValues.observe("dev.c17.validation.qs-card/media_accent", other::add);
        CustomPluginValues.publish("dev.c17.validation.qs-card", "volume_hint", "short");
        CustomPluginValues.clearOwner("dev.c17.validation.qs-card");
        check(CustomPluginValues.get("dev.c17.validation.qs-card/media_accent") == null
                && CustomPluginValues.get("dev.c17.validation.qs-card/volume_hint") == null, "a stopped plugin leaves no values");
        check(other.size() == 1 && other.get(0) == null, "observers learn that the value is gone");
        check(Integer.valueOf(0xFF00FF00).equals(CustomPluginValues.get("host/media_accent")), "clearing one owner keeps the others");

        rejects(() -> CustomPluginValues.publish("dev.c17.x", "Bad-Name", 1), "names are lower-case words");
        rejects(() -> CustomPluginValues.publish("dev.c17.x", "ok", new Object()), "only simple values");
        rejects(() -> CustomPluginValues.publish("dev.c17.x", "ok", Double.NaN), "numbers must be finite");
        StringBuilder longText = new StringBuilder(); for (int i = 0; i < 300; i++) longText.append('x');
        rejects(() -> CustomPluginValues.publish("dev.c17.x", "ok", longText.toString()), "strings are short");
        rejects(() -> CustomPluginValues.observe("no-slash", value -> { }), "keys are owner/name");
        check(CustomPluginValues.validKey("host/media_accent") && CustomPluginValues.validKey("Dev.Plugin-1/a_b")
                && !CustomPluginValues.validKey("/x") && !CustomPluginValues.validKey("host/"), "key validation");
        CustomPluginValues.resetForCheck();
        System.out.println("CustomPluginValuesCheck: " + checks + " checks passed");
    }
}
