package dev.puitheme;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Real measured-space plans: finite budgets, physical margins, cross-side order and whole-element overflow. */
public final class StatusBarFlowPlannerCheck {
    private static int checks;
    private static final String LEFT = StatusBarLayoutSettings.LEFT, RIGHT = StatusBarLayoutSettings.RIGHT;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void near(float expected, float actual, String why) {
        check(Float.isFinite(actual) && Math.abs(expected - actual) < .02f, why + ": " + expected + " != " + actual);
    }
    private static StatusBarFlowPlanner.Node node(String token, String side, int ordinal, float width) {
        return new StatusBarFlowPlanner.Node(token, side, ordinal, width, 0f, 0f);
    }
    private static Bundle enabled() { Bundle result = new Bundle(); result.putBoolean(StatusBarLayoutSettings.MASTER, true); return result; }
    private static StatusBarFlowPlanner.Plan plan(StatusBarFlowPlanner.Bounds bounds,
            List<StatusBarFlowPlanner.Node> nodes, Bundle settings, float density) {
        return StatusBarFlowPlanner.plan(bounds, nodes, StatusBarLayoutSettings.Snapshot.parse(settings), density);
    }
    private static void invariant(StatusBarFlowPlanner.Plan result) {
        StatusBarFlowPlanner.Bounds b = result.bounds;
        double left = 0d, right = 0d;
        for (StatusBarFlowPlanner.Placement p : result.placements) {
            check(Float.isFinite(p.x) && Float.isFinite(p.width) && Float.isFinite(p.reservedWidth)
                    && Float.isFinite(p.leftMargin) && Float.isFinite(p.rightMargin) && Float.isFinite(p.heldWidth), "all fields finite");
            check(p.heldWidth >= 0f, "extra radio allocation is nonnegative");
            check(p.width >= 0f && p.reservedWidth >= 0f, "real allocation never has a negative budget");
            near(Math.max(0f, p.width + p.leftMargin + p.rightMargin), p.reservedWidth,
                    "signed margins change the allocation width without creating negative capacity");
            if (!p.visible) continue;
            check(b.valid, "invalid bounds never show content");
            float start = LEFT.equals(p.side) || b.shared ? b.leftStart : b.rightStart;
            float end = RIGHT.equals(p.side) || b.shared ? b.rightEnd : b.leftEnd;
            check(p.x - p.shift - p.leftMargin >= start - .02f && p.x - p.shift - p.leftMargin + p.reservedWidth + p.heldWidth <= end + .02f,
                    "allocation stays inside its safe interval; negative margins may deliberately overlap paint");
            if (p.shift != 0f) {
                float natural = p.x - p.shift;
                check(p.x >= Math.min(natural, start) - .02f && p.x <= Math.max(natural, end - p.width) + .02f,
                        "a centre-facing margin shift never moves paint further outside its safe interval");
            }
            if (p.leftMargin >= 0f && p.rightMargin >= 0f)
                check(p.x >= start - .02f && p.x + p.width <= end + .02f, "ordinary positive gaps retain paint inside safe bounds");
            if (LEFT.equals(p.side)) left += p.reservedWidth + p.heldWidth; else right += p.reservedWidth + p.heldWidth;
        }
        near((float) left, result.leftUsed, "left accounting"); near((float) right, result.rightUsed, "right accounting");
        if (b.valid && b.shared) check(left + right <= b.rightEnd - b.leftStart + .02f, "shared budget cannot collide");
        for (int i = 0; i < result.placements.size(); i++) for (int j = i + 1; j < result.placements.size(); j++) {
            StatusBarFlowPlanner.Placement first = result.placements.get(i), second = result.placements.get(j);
            if (!first.visible || !second.visible) continue;
            float firstSlot = first.x - first.shift - first.leftMargin, secondSlot = second.x - second.shift - second.leftMargin;
            check(firstSlot + first.reservedWidth + first.heldWidth <= secondSlot + .02f || secondSlot + second.reservedWidth + second.heldWidth <= firstSlot + .02f,
                    "allocations never double-spend capacity even when negative margins deliberately overlap paint");
        }
    }
    private static void signedMargins() {
        StatusBarFlowPlanner.Bounds bounds = new StatusBarFlowPlanner.Bounds(5, 100, 100, 205);
        StatusBarFlowPlanner.Node clock = node("clock", LEFT, 0, 40), wifi = node("wifi", LEFT, 1, 20);
        Bundle signed = enabled(); signed.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), -6);
        signed.putFloat(StatusBarLayoutSettings.key("clock", "margin_right"), -4);
        StatusBarFlowPlanner.Plan reduced = plan(bounds, Arrays.asList(clock, wifi), signed, 2);
        near(-7, reduced.find(clock).x, "negative left gap moves the glyph left relative to its native safe edge");
        near(20, reduced.find(clock).reservedWidth, "both negative gaps actively reduce the clock allocation");
        near(25, reduced.find(wifi).x, "following Wi-Fi consumes the reduced width rather than the old width");
        near(40, reduced.leftUsed, "negative margins reduce but never negate the consumed budget"); invariant(reduced);

        for (String side : new String[]{LEFT, RIGHT}) {
            StatusBarFlowPlanner.Node nativeMargins = new StatusBarFlowPlanner.Node("system", side, 0, 20, 2, 7);
            Bundle physical = enabled(); physical.putFloat(StatusBarLayoutSettings.key("system", "margin_left"), -3.5f);
            physical.putFloat(StatusBarLayoutSettings.key("system", "margin_right"), -5.5f);
            StatusBarFlowPlanner.Placement value = plan(bounds, Collections.singletonList(nativeMargins), physical, 2).find(nativeMargins);
            near(-5, value.leftMargin, "negative custom left gap combines with the native physical left margin");
            near(-4, value.rightMargin, "negative custom right gap combines with the native physical right margin");
            near(11, value.reservedWidth, "native gaps and signed custom gaps reserve their exact combined width");
            // A lone element is the innermost one: its centre-facing user gap moves it (11px right of
            // the left edge; 7px left of the right anchor). Native gaps stay ordinary spacing.
            near(LEFT.equals(side) ? 11 : 182, value.x, "physical left/right values do not exchange at the opposite anchor");
        }
        // Device code160: the network speed is the leftmost (innermost) element of the right group.
        StatusBarFlowPlanner.Node speed = node("speed", RIGHT, 0, 30), wifiRight = node("wifi", RIGHT, 1, 20), battery = node("battery", RIGHT, 2, 25);
        StatusBarFlowPlanner.Plan base = plan(bounds, Arrays.asList(speed, wifiRight, battery), enabled(), 2);
        for (float margin : new float[]{6, -4}) {
            Bundle moved = enabled(); moved.putFloat(StatusBarLayoutSettings.key("speed", "margin_left"), margin);
            StatusBarFlowPlanner.Plan shifted = plan(bounds, Arrays.asList(speed, wifiRight, battery), moved, 2);
            // Device code162: towards its touching neighbour the speed cannot move (it painted over
            // the signal bars); away from it the centre-facing margin visibly moves it.
            near(base.find(speed).x + (margin > 0 ? 0 : margin * 2), shifted.find(speed).x, "the speed's centre-facing left margin");
            check(shifted.find(speed).x + 30 <= shifted.find(wifiRight).x + .01f, "the speed never paints over its neighbour");
            near(base.find(wifiRight).x, shifted.find(wifiRight).x, "its right neighbours keep their places");
            near(base.find(battery).x, shifted.find(battery).x, "the right anchor is unchanged"); invariant(shifted);
        }
        // With a native 8px gap before Wi-Fi a +6dp (12px) margin closes that gap and stops there.
        StatusBarFlowPlanner.Node gapped = new StatusBarFlowPlanner.Node("speed", RIGHT, 0, 30, 0, 8);
        StatusBarFlowPlanner.Plan gappedBase = plan(bounds, Arrays.asList(gapped, wifiRight, battery), enabled(), 2);
        Bundle closer = enabled(); closer.putFloat(StatusBarLayoutSettings.key("speed", "margin_left"), 6);
        StatusBarFlowPlanner.Plan closed = plan(bounds, Arrays.asList(gapped, wifiRight, battery), closer, 2);
        near(gappedBase.find(gapped).x + 8, closed.find(gapped).x, "a positive centre-facing margin moves up to touching");
        near(gappedBase.find(wifiRight).x, closed.find(wifiRight).x, "the neighbour stays");
        Bundle spaced = enabled(); spaced.putFloat(StatusBarLayoutSettings.key("speed", "margin_right"), 5);
        StatusBarFlowPlanner.Plan gap = plan(bounds, Arrays.asList(speed, wifiRight, battery), spaced, 2);
        near(base.find(speed).x - 10, gap.find(speed).x, "a right margin still opens a real gap before Wi-Fi");
        near(base.find(wifiRight).x, gap.find(wifiRight).x, "the gap is taken on the open side");

        Bundle huge = enabled(); huge.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -Float.MAX_VALUE);
        huge.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -Float.MAX_VALUE);
        StatusBarLayoutSettings.Snapshot parsed = StatusBarLayoutSettings.Snapshot.parse(huge);
        near(-Float.MAX_VALUE, parsed.left("wifi"), "saved negative finite input has no artificial UI/protocol lower limit");
        near(-Float.MAX_VALUE, ((Number) huge.get(StatusBarLayoutSettings.key("wifi", "margin_left"))).floatValue(), "render safety never rewrites the saved input");
        StatusBarFlowPlanner.Node largeClock = node("clock", LEFT, 0, 180), negativeWifi = node("wifi", LEFT, 1, 20);
        StatusBarFlowPlanner.Node carrier = node("carrier", LEFT, 2, 100);
        StatusBarFlowPlanner.Plan saturated = plan(bounds, Arrays.asList(largeClock, negativeWifi, carrier), huge, Float.MAX_VALUE);
        near(-65535, saturated.find(negativeWifi).leftMargin, "huge negative render gap is finite after density multiplication");
        near(0, saturated.find(negativeWifi).reservedWidth, "over-compressed slot consumes zero rather than negative capacity");
        check(!saturated.find(carrier).visible, "a negative reservation cannot grant space to an otherwise overflowing carrier"); invariant(saturated);
        huge.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), Float.MAX_VALUE);
        huge.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -Float.MAX_VALUE);
        StatusBarFlowPlanner.Plan cancelled = plan(bounds, Collections.singletonList(negativeWifi), huge, 4);
        near(20, cancelled.find(negativeWifi).reservedWidth, "equal extreme positive/negative gaps leave a finite ordinary reservation");
        near(Float.MAX_VALUE, StatusBarLayoutSettings.Snapshot.parse(huge).left("wifi"), "positive stored margin is not capped to the rendering limit"); invariant(cancelled);

        Bundle half = enabled(); half.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), -70000);
        StatusBarFlowPlanner.Plan scaled = plan(bounds, Collections.singletonList(clock), half, .5f);
        near(-35000, scaled.find(clock).leftMargin, "negative stored gap is multiplied before render saturation"); invariant(scaled);
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            Bundle bad = enabled(); bad.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), invalid);
            near(0, plan(bounds, Collections.singletonList(clock), bad, 1).find(clock).leftMargin,
                    "non-finite input remains invalid rather than an unbounded coordinate");
        }
    }
    private static void splitHintSides(){
        StatusBarFlowPlanner.Node left=new StatusBarFlowPlanner.Node("system",RIGHT,0,20,0,0,LEFT);
        StatusBarFlowPlanner.Node right=new StatusBarFlowPlanner.Node("system",LEFT,1,30,0,0,RIGHT);
        Bundle values=enabled();values.putString(StatusBarLayoutSettings.key("system","side"),LEFT);
        values.putInt(StatusBarLayoutSettings.key("system","order"),1);
        values.putFloat(StatusBarLayoutSettings.key("system","margin_left"),3);
        values.putFloat(StatusBarLayoutSettings.key("system","margin_right"),5);
        StatusBarFlowPlanner.Bounds bounds=new StatusBarFlowPlanner.Bounds(0,200,200,400);
        StatusBarFlowPlanner.Plan split=plan(bounds,Arrays.asList(left,right),values,1);
        check(LEFT.equals(split.find(left).side)&&RIGHT.equals(split.find(right).side),"split SYSTEM anchors resolve independently of whole-group side");
        near(28,split.leftUsed,"left SYSTEM margin is counted once for its subgroup");
        near(38,split.rightUsed,"right SYSTEM margin is counted once for its subgroup");invariant(split);
        values.putBoolean(StatusBarLayoutSettings.MASTER,false);
        StatusBarFlowPlanner.Plan off=plan(bounds,Arrays.asList(left,right),values,1);
        check(RIGHT.equals(off.find(left).side)&&LEFT.equals(off.find(right).side),"sorting off ignores resolved side overrides while keeping independent margins");
        invariant(off);
    }
    public static void main(String[] ignored) {
        splitHintSides();
        StatusBarFlowPlanner.Bounds shared = new StatusBarFlowPlanner.Bounds(5f, 100f, 100f, 205f);
        StatusBarFlowPlanner.Node clock = node("clock", LEFT, 0, 40f), wifi = node("wifi", RIGHT, 1, 20f);
        StatusBarFlowPlanner.Node mobile = node("mobile", RIGHT, 2, 30f), battery = node("battery", RIGHT, 3, 25f);
        List<StatusBarFlowPlanner.Node> ordinary = Arrays.asList(clock, wifi, mobile, battery);
        StatusBarFlowPlanner.Plan nativePlan = plan(shared, ordinary, enabled(), 1f);
        near(5f, nativePlan.find(clock).x, "clock physical left"); near(130f, nativePlan.find(wifi).x, "right group start");
        near(150f, nativePlan.find(mobile).x, "right native sequence"); near(180f, nativePlan.find(battery).x, "right anchored battery");
        invariant(nativePlan);
        Bundle customized = enabled(); customized.putString(StatusBarLayoutSettings.key("clock", "side"), RIGHT);
        customized.putString(StatusBarLayoutSettings.key("battery", "side"), LEFT);
        customized.putInt(StatusBarLayoutSettings.key("mobile", "order"), 1);
        customized.putInt(StatusBarLayoutSettings.key("wifi", "order"), 2);
        customized.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), 2f);
        customized.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), 3f);
        StatusBarFlowPlanner.Plan moved = plan(shared, ordinary, customized, 2f);
        near(5f, moved.find(battery).x, "battery crosses to left with original identity");
        near(105f, moved.find(mobile).x, "order one is physical leftmost even in right group");
        near(139f, moved.find(wifi).x, "extra left gap converted to pixels");
        near(165f, moved.find(clock).x, "native follows custom ranked entries"); invariant(moved);
        customized.putBoolean(StatusBarSettings.SAFE_MODE, true);
        StatusBarFlowPlanner.Plan safe = plan(shared, ordinary, customized, 2f);
        near(nativePlan.find(clock).x, safe.find(clock).x, "safe mode ignores custom positions");
        near(nativePlan.find(wifi).x, safe.find(wifi).x, "safe mode ignores custom margins/order");

        StatusBarFlowPlanner.Node a = new StatusBarFlowPlanner.Node("system", LEFT, 10, 17f, 2f, 7f);
        StatusBarFlowPlanner.Node b = new StatusBarFlowPlanner.Node("system", RIGHT, 20, 19f, 3f, 8f);
        StatusBarFlowPlanner.Plan physical = plan(shared, Arrays.asList(a, b), enabled(), 1f);
        near(7f, physical.find(a).x, "left margin stays physical left"); near(178f, physical.find(b).x, "right group physical margins");
        near(26f, physical.find(a).reservedWidth, "left reserve exact"); near(30f, physical.find(b).reservedWidth, "right reserve exact");
        check(physical.find(a).node == a && physical.find(b).node == b, "duplicate token keeps each node identity"); invariant(physical);
        Bundle sameOrder = enabled(); sameOrder.putInt(StatusBarLayoutSettings.key("system", "order"), 2);
        StatusBarFlowPlanner.Node earlier = node("system", RIGHT, 5, 9f), later = node("system", RIGHT, 6, 7f);
        StatusBarFlowPlanner.Plan tied = plan(shared, Arrays.asList(later, earlier), sameOrder, 1f);
        check(tied.find(earlier).x < tied.find(later).x, "equal orders preserve native ordinal despite snapshot input order");
        check(tied.placements.get(0).node == later, "outputs retain input order");

        StatusBarFlowPlanner.Bounds cutout = new StatusBarFlowPlanner.Bounds(0f, 60f, 95f, 150f);
        StatusBarFlowPlanner.Plan priority = plan(cutout, Arrays.asList(node("carrier", LEFT, 0, 28f),
                node("notification", LEFT, 1, 25f), node("clock", LEFT, 2, 40f),
                node("system", RIGHT, 3, 25f), node("wifi", RIGHT, 4, 20f), node("battery", RIGHT, 5, 30f)), enabled(), 1f);
        check(!priority.placements.get(0).visible && !priority.placements.get(1).visible && priority.placements.get(2).visible,
                "clock wins left overflow before carrier/notification");
        check(!priority.placements.get(3).visible && priority.placements.get(4).visible && priority.placements.get(5).visible,
                "battery then network wins right overflow");
        check(priority.overflowCount == 3, "overflow explicitly recorded"); invariant(priority);
        StatusBarFlowPlanner.Plan sharedBudget = plan(new StatusBarFlowPlanner.Bounds(0f, 40f, 40f, 100f),
                Arrays.asList(node("clock", LEFT, 0, 65f), node("battery", RIGHT, 1, 25f), node("wifi", RIGHT, 2, 20f)), enabled(), 1f);
        check(sharedBudget.placements.get(0).visible && sharedBudget.placements.get(1).visible
                && !sharedBudget.placements.get(2).visible, "uncut row shares capacity but sides cannot overlap"); invariant(sharedBudget);

        Bundle huge = enabled(); huge.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), Float.MAX_VALUE);
        StatusBarFlowPlanner.Plan hugeGap = plan(shared, Arrays.asList(clock, wifi, battery), huge, Float.MAX_VALUE);
        check(!hugeGap.find(wifi).visible && "overflow".equals(hugeGap.find(wifi).reason), "huge margin is bounded overflow"); invariant(hugeGap);
        Bundle halfDensity = enabled(); halfDensity.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 70000f);
        StatusBarFlowPlanner.Plan multiplied = plan(new StatusBarFlowPlanner.Bounds(0f, 65535f, 65535f, 65535f),
                Collections.singletonList(node("clock", LEFT, 0, 3f)), halfDensity, .5f);
        near(35000f, multiplied.placements.get(0).leftMargin, "multiply stored margin before saturation"); invariant(multiplied);
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f}) {
            StatusBarFlowPlanner.Node broken = node("wifi", RIGHT, 0, bad);
            StatusBarFlowPlanner.Plan badWidth = plan(shared, Arrays.asList(clock, broken, battery), enabled(), 1f);
            check(!badWidth.find(broken).visible && "invalid_width".equals(badWidth.find(broken).reason), "invalid real width fails closed");
            invariant(badWidth);
        }
        StatusBarFlowPlanner.Node zero = new StatusBarFlowPlanner.Node("wifi", RIGHT, 0, 0f, 20f, 20f);
        StatusBarFlowPlanner.Plan empty = plan(shared, Collections.singletonList(zero), enabled(), 1f);
        check(!empty.find(zero).visible && "empty".equals(empty.find(zero).reason) && empty.rightUsed == 0f,
                "zero width consumes no ghost margins"); invariant(empty);
        for (StatusBarFlowPlanner.Bounds bad : Arrays.asList(new StatusBarFlowPlanner.Bounds(Float.NaN, 10f, 20f, 40f),
                new StatusBarFlowPlanner.Bounds(10f, 5f, 20f, 40f), new StatusBarFlowPlanner.Bounds(0f, 20f, 10f, 15f))) {
            StatusBarFlowPlanner.Plan invalid = plan(bad, ordinary, enabled(), 1f);
            check(!invalid.bounds.valid && invalid.leftUsed == 0f && invalid.rightUsed == 0f, "bad bounds consume no space");
            invariant(invalid);
        }
        check(plan(shared, null, null, Float.NaN).placements.isEmpty(), "empty source safe");
        try { nativePlan.placements.clear(); throw new AssertionError("output is mutable"); }
        catch (UnsupportedOperationException expected) { checks++; }

        signedMargins();
        Random random = new Random(144);
        for (int iteration = 0; iteration < 1000; iteration++) {
            float rootWidth = 80f + random.nextInt(1200), split = rootWidth / 2;
            boolean hole = random.nextBoolean(); float gap = hole ? 20f : 0f;
            StatusBarFlowPlanner.Bounds bounds = new StatusBarFlowPlanner.Bounds(3f, split - gap / 2, split + gap / 2, rootWidth - 3f);
            ArrayList<StatusBarFlowPlanner.Node> nodes = new ArrayList<>(); Bundle config = enabled();
            config.putBoolean("speed_enabled", random.nextBoolean());
            config.putBoolean("speed_position_enabled", random.nextBoolean());
            config.putBoolean(SpeedPosition.SAFE_GAP_ENABLED, random.nextBoolean());
            config.putFloat(SpeedPosition.EDGE_RESERVE, random.nextFloat() * 160f);
            for (int index = 0; index < StatusBarLayoutSettings.TOKENS.size(); index++) {
                String token = StatusBarLayoutSettings.TOKENS.get(index);
                config.putString(StatusBarLayoutSettings.key(token, "side"), random.nextBoolean() ? LEFT : RIGHT);
                config.putInt(StatusBarLayoutSettings.key(token, "order"), random.nextInt(5));
                config.putFloat(StatusBarLayoutSettings.key(token, "margin_left"), random.nextFloat() * 90 - 60);
                config.putFloat(StatusBarLayoutSettings.key(token, "margin_right"), random.nextFloat() * 90 - 60);
                // Absent network states contribute no slot at all; present zero width is tested separately.
                if (random.nextBoolean()) nodes.add(new StatusBarFlowPlanner.Node(token, random.nextBoolean() ? LEFT : RIGHT,
                        index, random.nextFloat() * 150, random.nextFloat() * 4, random.nextFloat() * 4));
            }
            float density = .5f + random.nextFloat() * 4;
            StatusBarFlowPlanner.Plan first = plan(bounds, nodes, config, density); invariant(first);
            StatusBarFlowPlanner.Plan again = plan(bounds, nodes, config, density);
            for (int index = 0; index < first.placements.size(); index++) {
                near(first.placements.get(index).x, again.placements.get(index).x, "repeated state never accumulates positions");
                check(first.placements.get(index).visible == again.placements.get(index).visible, "stable overflow selection");
            }
        }
        System.out.println("StatusBarFlowPlannerCheck: " + checks + " checks passed");
    }
}
