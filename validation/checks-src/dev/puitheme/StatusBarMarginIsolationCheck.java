package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public settings contracts: standalone margins, unified network editor, exact legacy export subsets and safe restore. */
public final class StatusBarMarginIsolationCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] ignored) throws Exception {
        Map<String, String> owners = new LinkedHashMap<>();
        String[] tokens = {"clock", "notification", "carrier", "speed", "wifi", "mobile", "battery", "system", "label"};
        String[] groups = {"clock", "notification_icons", "carrier", "speed", "wifi", "data", "battery", "status_hint_icons", "label"};
        for (int i = 0; i < tokens.length; i++) owners.put(tokens[i], groups[i]);
        for (Map.Entry<String, String> element : owners.entrySet()) {
            String token = element.getKey(), group = element.getValue();
            String editorGroup = token.equals("wifi") || token.equals("label") ? "network" : group;
            for (String side : Arrays.asList("margin_left", "margin_right")) {
                String key = StatusBarLayoutSettings.key(token, side);
                SettingsCatalog.Item item = SettingsCatalog.item(key);
                check(item != null && item.groupId.equals(editorGroup), token + " margins live on the current element editor");
                check(ConfigScope.section(editorGroup).keys().contains(key), "current editor exports its margin");
                check(ConfigScope.section(group).keys().contains(key), "legacy element export selection retains its margin");
                if (token.equals("wifi") || token.equals("label")) {
                    String otherToken=token.equals("wifi") ? "label" : "wifi";
                    check(!ConfigScope.section(group).keys().contains(StatusBarLayoutSettings.key(otherToken,side)), "legacy network export subset cannot import the other element's margin");
                    check(ConfigScope.keys(Collections.singletonList(group)).equals(ConfigScope.section(group).keys()), "saved legacy network export selection remains an exact local subset");
                }
                check(!ConfigScope.section(StatusBarLayoutSettings.GROUP).keys().contains(key), "sorting page cannot overwrite margin");
                check(SettingsCatalog.unavailableReason(key, Collections.emptyMap()).isEmpty(), "margin editor available with style and sorting off");
                Map<String, Object> saved = new LinkedHashMap<>(); saved.put(key, 5.25f);
                saved.put(StatusBarLayoutSettings.MASTER, false);
                if (!token.equals("label")) saved.put(StatusBarLayoutSettings.key(token, "side"), "left");
                saved.put(StatusBarLayoutSettings.key(token, "order"), 2f);
                Bundle bundle = new Bundle(); bundle.putFloat(key, 5.25f);
                bundle.putBoolean(StatusBarLayoutSettings.MASTER, false);
                bundle.putFloat(StatusBarLayoutSettings.key(token, "order"), 2f);
                if (!token.equals("label")) bundle.putString(StatusBarLayoutSettings.key(token, "side"), "left");
                StatusBarLayoutSettings.Snapshot state = StatusBarLayoutSettings.Snapshot.parse(bundle);
                check(state.enabled && !state.orderingEnabled, "nonzero margin works independently");
                check(state.order(token) == 0 && state.side(token).equals("native"), "stored order/side do not leak while sorting off");
                check((side.equals("margin_left") ? state.left(token) : state.right(token)) == 5.25f, "chosen physical margin reaches runtime");
                check(StatusBarLayoutSettings.enabledValues(saved) && StatusBarLayoutSettings.enabled(bundle), "editor/runtime agree on independent margin");
                bundle.putBoolean(StatusBarLayoutSettings.MASTER, true);
                state = StatusBarLayoutSettings.Snapshot.parse(bundle);
                check(state.order(token) == 2 && state.orderingEnabled, "reenabling sorting recovers saved order");
                check((side.equals("margin_left") ? state.left(token) : state.right(token)) == 5.25f, "sorting toggle preserves margin");
                bundle.putBoolean(StatusBarSettings.SAFE_MODE, true);
                state = StatusBarLayoutSettings.Snapshot.parse(bundle);
                check(!state.enabled && state.left(token) == 0 && state.right(token) == 0 && state.order(token) == 0, "safe mode fully restores native placement");
                check(bundle.get(key).equals(5.25f), "runtime masking never overwrites saved margin");
                signedMargin(key, token, side);
            }
        }
        for (Object bad : Arrays.asList(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, "bad")) {
            Map<String, Object> values = new LinkedHashMap<>(); values.put(StatusBarLayoutSettings.key("wifi", "margin_left"), bad);
            check(!StatusBarLayoutSettings.enabledValues(values), "invalid margin cannot enable flow");
        }
        Bundle values = new Bundle(); values.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 3.5f);
        values.putFloat(StatusBarSettings.CLOCK_OFFSET_X, 17f); values.putFloat(StatusBarSettings.CLOCK_OFFSET_Y, -4f);
        Bundle effective = StatusBarLayoutSettings.runtime(values);
        check(((Number)effective.get(StatusBarSettings.CLOCK_OFFSET_X)).floatValue() == 0, "standalone margin disables legacy drawing offset");
        check(((Number)effective.get(StatusBarSettings.CLOCK_OFFSET_Y)).floatValue() == -4 && ((Number)values.get(StatusBarSettings.CLOCK_OFFSET_X)).floatValue() == 17,
                "vertical position and original preference survive activation");
        values.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 0f);
        check(!StatusBarLayoutSettings.enabled(values), "zero gaps plus sorting off return native ownership");
        check(((Number)StatusBarLayoutSettings.runtime(values).get(StatusBarSettings.CLOCK_OFFSET_X)).floatValue() == 17, "zero restores saved legacy contract");
        for (SettingsCatalog.Item item : SettingsCatalog.group(StatusBarLayoutSettings.GROUP).items)
            check(!StatusBarLayoutSettings.marginKey(item.key), "sorting UI has no margin fields");
        check(SettingsFrameworkMirror.VERSION == 52 && Upgrade144.DEFAULTS.size() == 36, "independent margin protocol remains unchanged by subsequent schemas");
        System.out.println("StatusBarMarginIsolationCheck: " + checks + " checks passed");
    }
    private static void signedMargin(String key, String token, String side) throws Exception {
        SettingsCatalog.Item item = SettingsCatalog.item(key);
        check(item.min == -40f && item.max == 40f, "margin slider suggests a symmetric range");
        check(NumericPolicy.signed(key), "all physical margins accept signed input");
        SettingEditor.Info info = SettingEditor.forItem(item, Collections.emptyMap());
        check(info.signed && !info.integer && info.description.contains("负数收紧")
                && info.description.contains("科学计数") && info.description.contains("20秒"), "editor explains signed unrestricted trial semantics");
        for (float margin : new float[]{-Float.MAX_VALUE, -1e20f, -1000.125f, -40.125f, -40f, -0.001f,
                -Float.MIN_VALUE, 0f, Float.MIN_VALUE, 40f, 40.125f, 1000.125f, Float.MAX_VALUE}) {
            Map<String, Object> saved = new LinkedHashMap<>(); saved.put(key, margin);
            check(SettingsCatalog.validationError(item, margin) == null, "every finite signed margin remains valid");
            check(SettingsCatalog.customNumber(item, Float.toString(margin)) == margin, "manual input preserves the finite float value");
            check(((Number) SettingsCatalog.value(saved, key)).floatValue() == margin, "viewing stored margins never clamps them");
            check(NumericPolicy.setting(key, margin, 0f) == margin, "runtime setting reader preserves signed margins");
            check(SettingsCatalog.requiresNumericTrial(item, margin) == (margin < -40f || margin > 40f), "trial follows suggestions rather than rejecting input");
            Bundle raw = new Bundle(); raw.putFloat(key, margin);
            StatusBarLayoutSettings.Snapshot parsed = StatusBarLayoutSettings.Snapshot.parse(raw);
            check((side.equals("margin_left") ? parsed.left(token) : parsed.right(token)) == margin, "snapshot preserves signed margin");
            check(parsed.enabled == (margin != 0f) && StatusBarLayoutSettings.enabled(raw) == (margin != 0f)
                    && StatusBarLayoutSettings.enabledValues(saved) == (margin != 0f), "negative-only margin activates without sorting");
            Map<String, Object> imported = ConfigTransfer.prepare(ConfigTransfer.exportJson(saved, saved.keySet()), true).values();
            check(saved.equals(imported), "negative and extreme margins export and import without normalization");
        }
        check(SettingsCatalog.validationError(item, Float.NaN) != null
                && SettingsCatalog.validationError(item, Float.POSITIVE_INFINITY) != null, "non-finite values remain invalid");
    }
}
