package dev.puitheme;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 网速漂移回归：PLK110 / App261 诊断日志 2026-11-06（phone 宿主 901 ↔ 827）。
 *
 * 只设一个状态栏边距（未打开排序），流布局即接管网速。code284 起流布局不再在网速后面
 * 预留无线宽度（Wi-Fi 或数据缺一个时会露出空格，且由「垂直位置调整」开关控制）：网速像原厂
 * 一样紧贴信号，Wi-Fi 出现/消失时整组按实际宽度靠拢；所有边距照常生效，绝不重叠。
 */
public final class SpeedFlowWifiDriftCheck {
    private static final String LEFT = StatusBarLayoutSettings.LEFT, RIGHT = StatusBarLayoutSettings.RIGHT;
    private static final float DENSITY = 3.5f;
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

    public static void main(String[] args) {
        leadingRadioMargin();
        gatesAndCapacity();
        marginsAcrossToggle();
        // 1. 只有一个非零边距：流布局生效，并带上网速的固定无线预留。
        Bundle raw = speedSettings(true, SpeedPosition.DEFAULT_RESERVE);
        raw.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI, "margin_left"), -1f);
        check(StatusBarLayoutSettings.enabled(raw), "a single nonzero margin enables the status-bar flow");
        StatusBarLayoutSettings.Snapshot snapshot = StatusBarLayoutSettings.Snapshot.parse(raw);
        check(snapshot.speedRadioReserve == 0f, "the flow keeps no radio reserve after the speed: " + snapshot.speedRadioReserve);
        check(snapshot.withoutOrdering().speedRadioReserve == 0f, "a capsule's margin-only copy keeps none either");
        check(StatusBarLayoutSettings.Snapshot.parse(speedSettings(false, 49f)).speedRadioReserve == 0f, "the vertical switch off holds nothing");
        Bundle wider = speedSettings(true, 60f);
        wider.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.WIFI, "margin_left"), -1f);
        check(snapshot.sameGeometry(StatusBarLayoutSettings.Snapshot.parse(wider)), "the hidden legacy reserve value no longer changes the flow");
        Bundle vertical = new Bundle(raw); vertical.putBoolean("speed_position_enabled", false);
        check(snapshot.sameGeometry(StatusBarLayoutSettings.Snapshot.parse(vertical)), "the vertical position switch does not change horizontal layout");

        // 2. 日志实测宽度：时钟 146、通知 56 在左；网速 77、蜂窝 93、Wi-Fi 81、电池 107 在右。
        // code279: the layout remembers the radio width seen with both present and hands it to the next plan.
        StatusBarFlowPlanner.Plan full = plan(snapshot, true, Float.NaN), one = plan(snapshot, false, Float.NaN);
        check(Math.abs(x(full, "mobile") - x(full, "speed") - 77f) < .5f, "with Wi-Fi and data the speed sits beside the signal");
        check(Math.abs(x(one, "mobile") - x(one, "speed") - 77f) < .5f, "with data only no gap is left after the speed");
        check(hold(full) == 0f && hold(one) == 0f, "nothing is held after the speed in either state");
        float with = x(full, "speed"), without = x(one, "speed");
        // Wi-Fi 81px plus its own -1dp (-3.5px) left margin set above.
        check(Math.abs((without - with) - (81f - DENSITY)) < .5f, "like the native bar, Wi-Fi leaving closes up by its width and margin: " + (without - with));

        // 3. 边距照常生效：Wi-Fi 左边距 -1dp 让 Wi-Fi 相对蜂窝左移 3.5px；网速左边距推动网速。
        Bundle plain = speedSettings(true, SpeedPosition.DEFAULT_RESERVE);
        plain.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.BATTERY, "margin_left"), 2f);
        StatusBarFlowPlanner.Plan a = plan(StatusBarLayoutSettings.Snapshot.parse(plain), true);
        StatusBarFlowPlanner.Plan b = plan(snapshot, true, Float.NaN);
        float gapA = x(a, "wifi") - x(a, "mobile"), gapB = x(b, "wifi") - x(b, "mobile");
        check(Math.abs((gapA - gapB) - 3.5f) < .01f, "Wi-Fi left margin still applies: " + gapA + " vs " + gapB);
        check(Math.abs((x(a, "battery") - x(a, "wifi")) - (81f + 7f)) < .01f, "battery left margin 2dp still applies");
        Bundle speedMargin = new Bundle(plain);
        speedMargin.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.SPEED, "margin_right"), 4f);
        StatusBarFlowPlanner.Plan c = plan(StatusBarLayoutSettings.Snapshot.parse(speedMargin), true);
        check(Math.abs((x(a, "speed") - x(c, "speed")) - 14f) < .01f, "speed right margin 4dp moves the speed away from the radios");
        // No reserve: with a speed margin, Wi-Fi leaving still closes up by exactly its 81px width.
        float shift = speedX(StatusBarLayoutSettings.Snapshot.parse(speedMargin), false) - x(c, "speed");
        check(Math.abs(shift - 81f) < .01f, "with a speed margin Wi-Fi leaving moves it by the Wi-Fi width: " + shift);

        // 4. 真实无线比预留更宽：只让出超出部分，绝不与网速重叠。
        Bundle narrow = speedSettings(true, 20f); narrow.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.BATTERY, "margin_left"), 1f);
        StatusBarFlowPlanner.Plan d = plan(StatusBarLayoutSettings.Snapshot.parse(narrow), true);
        check(x(d, "speed") + 77f <= x(d, "mobile") + .01f, "a wider real radio is never overlapped");

        // 5. 网速在最右侧时不留空位；关闭网速位置开关恢复普通流布局。
        Bundle off = speedSettings(false, 49f); off.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.BATTERY, "margin_left"), 1f);
        StatusBarLayoutSettings.Snapshot plainFlow = StatusBarLayoutSettings.Snapshot.parse(off);
        check(Math.abs((speedX(plainFlow, false) - speedX(plainFlow, true)) - 81f) < .01f, "switch off keeps the plain flow");
        System.out.println("SpeedFlowWifiDriftCheck " + checks + " checks passed");
    }
    private static void leadingRadioMargin() {
        Bundle base = speedSettings(true, 80f);
        base.putBoolean(StatusBarLayoutSettings.MASTER, true);
        List<StatusBarFlowPlanner.Node> nodes = Arrays.asList(node("speed", RIGHT, 0, 77),
                node("mobile", RIGHT, 1, 30), node("wifi", RIGHT, 2, 20), node("battery", RIGHT, 3, 20));
        StatusBarFlowPlanner.Bounds bounds = new StatusBarFlowPlanner.Bounds(0, 300, 0, 300);
        StatusBarFlowPlanner.Plan before = StatusBarFlowPlanner.plan(bounds, nodes, StatusBarLayoutSettings.Snapshot.parse(base), 1f);
        for (float margin : new float[]{10f, -10f}) {
            Bundle changed = new Bundle(base);
            changed.putFloat(StatusBarLayoutSettings.key("mobile", "margin_left"), margin);
            StatusBarFlowPlanner.Plan after = StatusBarFlowPlanner.plan(bounds, nodes, StatusBarLayoutSettings.Snapshot.parse(changed), 1f);
            float oldGap = x(before, "mobile") - x(before, "speed") - 77f;
            float newGap = x(after, "mobile") - x(after, "speed") - 77f;
            check(Math.abs(newGap - oldGap - margin) < .01f,
                    "the first radio's left margin must change its distance from the speed: " + oldGap + " -> " + newGap + " margin=" + margin);
        }
        base.putInt(StatusBarLayoutSettings.key("speed", "order"), 1);
        base.putInt(StatusBarLayoutSettings.key("wifi", "order"), 2);
        base.putInt(StatusBarLayoutSettings.key("mobile", "order"), 3);
        base.putInt(StatusBarLayoutSettings.key("battery", "order"), 4);
        before = StatusBarFlowPlanner.plan(bounds, nodes, StatusBarLayoutSettings.Snapshot.parse(base), 1f);
        check(x(before, "wifi") < x(before, "mobile"), "custom sorting puts Wi-Fi first");
        for (float margin : new float[]{10f, -10f}) {
            Bundle changed = new Bundle(base);
            changed.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), margin);
            StatusBarFlowPlanner.Plan after = StatusBarFlowPlanner.plan(bounds, nodes, StatusBarLayoutSettings.Snapshot.parse(changed), 1f);
            float oldGap = x(before, "wifi") - x(before, "speed") - 77f;
            float newGap = x(after, "wifi") - x(after, "speed") - 77f;
            check(Math.abs(newGap - oldGap - margin) < .01f, "the sorted first Wi-Fi retains its signed left margin");
            budget(after);
        }
    }
    private static void gatesAndCapacity() {
        Bundle base = speedSettings(true, 80f);
        base.putBoolean(StatusBarLayoutSettings.MASTER, true);
        List<StatusBarFlowPlanner.Node> nodes = Arrays.asList(node("clock", LEFT, 0, 40),
                node("speed", RIGHT, 1, 30), node("mobile", RIGHT, 2, 20), node("battery", RIGHT, 3, 20));
        StatusBarFlowPlanner.Bounds roomy = new StatusBarFlowPlanner.Bounds(0, 300, 0, 300);
        StatusBarFlowPlanner.Plan ordinary = custom(roomy, nodes, base);
        check(Math.abs(ordinary.rightUsed - 70f) < .01f, "only the real speed, radio and battery widths use capacity");
        check(hold(ordinary) == 0f, "no radio reserve is held after the speed");
        budget(ordinary);

        Bundle off = new Bundle(base); off.putBoolean("speed_position_enabled", false);
        check(hold(custom(roomy, nodes, off)) == 0f, "the vertical switch off holds nothing");
        off.putBoolean(SpeedPosition.SAFE_GAP_ENABLED, true);
        check(hold(custom(roomy, nodes, off)) == 0f, "the hidden legacy adjacent-gap switch holds nothing in the flow");
        off.putBoolean("speed_enabled", false);
        check(hold(custom(roomy, nodes, off)) == 0f, "disabled speed customization reserves nothing");
        Bundle safe = new Bundle(base); safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
        check(hold(custom(roomy, nodes, safe)) == 0f, "safe mode restores native allocation");
        Bundle nativeLayout = speedSettings(true, 80f);
        check(!StatusBarLayoutSettings.enabled(nativeLayout) && hold(custom(roomy, nodes, nativeLayout)) == 0f,
                "no margins and sorting off leave allocation to native positioning");

        for (boolean split : new boolean[]{false, true}) {
            for (int missing : new int[]{0, 1}) {
                StatusBarFlowPlanner.Bounds bounds = split
                        ? new StatusBarFlowPlanner.Bounds(0, 40, 60, 190 - missing)
                        : new StatusBarFlowPlanner.Bounds(0, 170 - missing, 0, 170 - missing);
                StatusBarFlowPlanner.Plan result = custom(bounds, nodes, base);
                check(hold(result) == 0f, "no reserve at any capacity");
                check(result.overflowCount == 0, "extra reserve never evicts an otherwise fitting icon");
                budget(result);
            }
        }
        List<StatusBarFlowPlanner.Node> absent = Arrays.asList(node("speed", RIGHT, 0, 30), node("battery", RIGHT, 2, 20));
        List<StatusBarFlowPlanner.Node> zero = Arrays.asList(node("speed", RIGHT, 0, 30),
                node("wifi", RIGHT, 1, 0), node("battery", RIGHT, 2, 20));
        StatusBarFlowPlanner.Plan hidden = custom(roomy, absent, base), empty = custom(roomy, zero, base);
        check(hold(hidden) == 0f && x(hidden, "speed") == x(empty, "speed"), "removed and zero-width radios leave the same packing");
        budget(hidden); budget(empty);
        StatusBarFlowPlanner.Plan last = custom(roomy, Arrays.asList(node("battery", RIGHT, 0, 20), node("speed", RIGHT, 1, 30)), base);
        check(hold(last) == 0f, "rightmost speed needs no trailing reserve");
        StatusBarFlowPlanner.Plan left = custom(roomy, Arrays.asList(node("speed", LEFT, 0, 30), node("wifi", LEFT, 1, 20)), base);
        check(hold(left) == 0f, "left-anchored speed is unaffected by following radios");
        for (float reserve : new float[]{-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE}) {
            Bundle changed = new Bundle(base); changed.putFloat(SpeedPosition.EDGE_RESERVE, reserve);
            float parsed = StatusBarLayoutSettings.Snapshot.parse(changed).speedRadioReserve;
            check(parsed == 0f, "any saved reserve value gives no reserve in the flow: " + parsed);
            budget(custom(roomy, nodes, changed));
        }
    }
    /** code284: every user margin keeps working with Wi-Fi on and off; the speed packs beside the signal like the native bar. */
    private static void marginsAcrossToggle() {
        Bundle base = speedSettings(true, SpeedPosition.DEFAULT_RESERVE);
        base.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.BATTERY, "margin_left"), 2f);
        StatusBarLayoutSettings.Snapshot plain = StatusBarLayoutSettings.Snapshot.parse(base);
        StatusBarFlowPlanner.Plan plainFull = plan(plain, true, Float.NaN), plainOne = plan(plain, false, plainFull.fullRadios);
        String[][] cases = {{"speed", "margin_left", "6"}, {"speed", "margin_left", "-4"}, {"speed", "margin_right", "4"},
                {"mobile", "margin_left", "3"}, {"mobile", "margin_right", "-2"}, {"wifi", "margin_left", "-1"},
                {"wifi", "margin_right", "-2"}, {"battery", "margin_left", "5"}};
        for (String[] item : cases) {
            Bundle values = new Bundle(base);
            values.putFloat(StatusBarLayoutSettings.key(item[0], item[1]), Float.parseFloat(item[2]));
            StatusBarLayoutSettings.Snapshot snapshot = StatusBarLayoutSettings.Snapshot.parse(values);
            StatusBarFlowPlanner.Plan full = plan(snapshot, true, Float.NaN), one = plan(snapshot, false, full.fullRadios);
            String label = item[0] + "." + item[1] + "=" + item[2];
            float margin = Float.parseFloat(item[2]);
            // A speed right / signal left margin opens that gap. The innermost speed's own left
            // margin moves the speed itself: a negative one towards the centre, a positive one is
            // stopped by the signal (code160/162 rule).
            float userGap = (item[0].equals("speed") && item[1].equals("margin_right")) || (item[0].equals("mobile") && item[1].equals("margin_left"))
                    ? margin * DENSITY : item[0].equals("speed") && item[1].equals("margin_left") ? Math.max(0f, -margin * DENSITY) : 0f;
            check(Math.abs(x(full, "mobile") - x(full, "speed") - 77f - userGap) < .5f
                    && Math.abs(x(one, "mobile") - x(one, "speed") - 77f - userGap) < .5f,
                    label + ": only the user margin separates the speed from the signal, with and without Wi-Fi");
            check(x(full, "speed") + 77f <= x(full, "mobile") + .01f && x(one, "speed") + 77f <= x(one, "mobile") + .01f, label + ": the speed never paints over the signal");
            check(x(full, "mobile") < x(full, "wifi") && x(full, "wifi") < x(full, "battery"), label + ": radios keep their order (a negative margin may overlap on purpose)");
        }
        // The visible effect of each margin, in both states (DENSITY 3.5 px per dp).
        check(Math.abs(x(margin("speed", "margin_left", -4f), true) - x(plainFull, "speed") + 14f) < .01f, "a negative speed left margin moves the speed towards the centre");
        check(Math.abs(x(margin("speed", "margin_left", -4f), false) - x(plainOne, "speed") + 14f) < .01f, "the same with Wi-Fi off");
        check(Math.abs(x(margin("speed", "margin_right", 4f), true) - x(plainFull, "speed") + 14f) < .01f, "a speed right margin opens a gap before the signal");
        check(Math.abs(x(margin("mobile", "margin_left", 3f), true) - x(plainFull, "speed") + 10.5f) < .01f, "a signal left margin opens a gap after the speed");
        check(Math.abs(x(margin("mobile", "margin_left", 3f), false) - x(plainOne, "speed") + 10.5f) < .01f, "the same with Wi-Fi off");
        StatusBarFlowPlanner.Plan wifiRight = plan(StatusBarLayoutSettings.Snapshot.parse(withMargin(base, "wifi", "margin_right", -2f)), true, Float.NaN);
        check(Math.abs((x(wifiRight, "battery") - x(wifiRight, "wifi")) - (x(plainFull, "battery") - x(plainFull, "wifi")) + 7f) < .01f, "a Wi-Fi right margin moves Wi-Fi closer to the battery");
    }
    private static float x(StatusBarFlowPlanner.Plan[] pair, boolean wifi) { return x(wifi ? pair[0] : pair[1], "speed"); }
    private static StatusBarFlowPlanner.Plan[] margin(String token, String side, float dp) {
        Bundle values = speedSettings(true, SpeedPosition.DEFAULT_RESERVE);
        values.putFloat(StatusBarLayoutSettings.key(StatusBarLayoutSettings.BATTERY, "margin_left"), 2f);
        StatusBarLayoutSettings.Snapshot snapshot = StatusBarLayoutSettings.Snapshot.parse(withMargin(values, token, side, dp));
        StatusBarFlowPlanner.Plan full = plan(snapshot, true, Float.NaN);
        return new StatusBarFlowPlanner.Plan[]{full, plan(snapshot, false, full.fullRadios)};
    }
    private static Bundle withMargin(Bundle source, String token, String side, float dp) {
        Bundle values = new Bundle(source); values.putFloat(StatusBarLayoutSettings.key(token, side), dp); return values;
    }
    private static StatusBarFlowPlanner.Plan custom(StatusBarFlowPlanner.Bounds bounds, List<StatusBarFlowPlanner.Node> nodes, Bundle settings) {
        return StatusBarFlowPlanner.plan(bounds, nodes, StatusBarLayoutSettings.Snapshot.parse(settings), 1f);
    }
    private static float hold(StatusBarFlowPlanner.Plan plan) {
        float total = 0f;
        for (StatusBarFlowPlanner.Placement placement : plan.placements) total += placement.heldWidth;
        return total;
    }
    private static void budget(StatusBarFlowPlanner.Plan plan) {
        float left = 0f, right = 0f;
        for (StatusBarFlowPlanner.Placement placement : plan.placements) {
            check(Float.isFinite(placement.heldWidth) && placement.heldWidth >= 0f, "extra allocation is finite and nonnegative");
            if (!placement.visible) { check(placement.heldWidth == 0f, "hidden icons consume no extra allocation"); continue; }
            if (LEFT.equals(placement.side)) left += placement.reservedWidth + placement.heldWidth;
            else right += placement.reservedWidth + placement.heldWidth;
        }
        check(Math.abs(left - plan.leftUsed) < .01f && Math.abs(right - plan.rightUsed) < .01f, "public allocations account for the entire budget");
    }
    private static Bundle speedSettings(boolean position, float reserve) {
        Bundle values = new Bundle();
        values.putBoolean("speed_enabled", true);
        values.putBoolean("speed_position_enabled", position);
        values.putFloat(SpeedPosition.EDGE_RESERVE, reserve);
        return values;
    }
    private static float speedX(StatusBarLayoutSettings.Snapshot snapshot, boolean wifi) { return x(plan(snapshot, wifi, Float.NaN), "speed"); }
    private static StatusBarFlowPlanner.Plan plan(StatusBarLayoutSettings.Snapshot snapshot, boolean wifi) { return plan(snapshot, wifi, Float.NaN); }
    private static StatusBarFlowPlanner.Plan plan(StatusBarLayoutSettings.Snapshot snapshot, boolean wifi, float knownFullRadios) {
        List<StatusBarFlowPlanner.Node> nodes = new ArrayList<>(Arrays.asList(node("clock", LEFT, 0, 146), node("notification", LEFT, 1, 56),
                node("speed", RIGHT, 2, 77), node("mobile", RIGHT, 3, 93)));
        if (wifi) nodes.add(node("wifi", RIGHT, 4, 81));
        nodes.add(node("battery", RIGHT, 5, 107));
        return StatusBarFlowPlanner.plan(new StatusBarFlowPlanner.Bounds(84f, 1178f, 84f, 1178f), nodes, snapshot, DENSITY, knownFullRadios);
    }
    private static float x(StatusBarFlowPlanner.Plan plan, String token) {
        for (StatusBarFlowPlanner.Placement placement : plan.placements)
            if (placement.node.token.equals(token)) { check(placement.visible, token + " placed"); return placement.x; }
        throw new AssertionError("missing " + token);
    }
    private static StatusBarFlowPlanner.Node node(String token, String side, int ordinal, float width) {
        return new StatusBarFlowPlanner.Node(token, side, ordinal, width, 0f, 0f);
    }
}
