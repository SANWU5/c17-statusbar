// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.os.Bundle;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Checks native-property ownership and notification clearance without replacing native views. */
public final class NotificationClearMotionCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(float value, float expected, String message) {
        check(Math.abs(value - expected) < .0005f, message + ": " + value + " / " + expected);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    public static final class Stack extends ViewGroup {
        public boolean mAnimationRunning;
        Stack() { super(new Context()); }
    }
    private static NotificationClearMotion.Frame frame(float f, float row, float bottom, float motion) {
        return NotificationClearMotion.frame(f, 600f, 48f, row, bottom, 18f, 32f, 0f, motion, 12f);
    }

    public static void main(String[] args) throws Exception {
        NotificationClearMotion motion = new NotificationClearMotion();
        Bundle settings = new Bundle();
        motion.configure(settings);
        check(!(Boolean) field(motion, "enabled"), "empty preferences retain native clear motion");
        check(!motion.needsNativeScope(), "disabled feature never creates controller fade scopes");
        check(!(Boolean) field(motion, "propertyTracking"), "disabled feature bypasses all native View setter lookups");
        settings.putBoolean(NotificationClearMotion.MOTION_ENABLED, true); motion.configure(settings);
        check(!(Boolean) field(motion, "enabled"), "independent motion still requires clear-button master");
        settings.putBoolean(NotificationClearAppearance.MASTER, true); motion.configure(settings);
        check((Boolean) field(motion, "enabled"), "clear motion can run with no big-clock preference");
        settings.putBoolean(StatusBarSettings.SAFE_MODE, true); motion.configure(settings);
        check(!(Boolean) field(motion, "enabled"), "safe mode restores native clear behavior");
        settings.putBoolean(StatusBarSettings.SAFE_MODE, false);
        settings.putBoolean(NotificationClearMotion.MOTION_ENABLED, false); motion.configure(settings);
        check(!(Boolean) field(motion, "enabled"), "motion switch restores native behavior independently");
        check(!NotificationClearAppearance.BOOLEANS.get(NotificationClearMotion.MOTION_ENABLED), "new motion defaults off");
        near(NotificationClearAppearance.NUMBERS.get(NotificationClearMotion.SAFE_DISTANCE), 18f, "safe gap registered");
        near(NotificationClearAppearance.NUMBERS.get(NotificationClearMotion.ENTRY_TRAVEL), 32f, "entry travel registered");
        near(NotificationClearAppearance.NUMBERS.get(NotificationClearMotion.OFFSET_Y), 0f, "native placement default");

        NotificationClearMotion.Frame entry = frame(0f, Float.NEGATIVE_INFINITY, 900f, 0f);
        near(entry.top, 568f, "starts above native position by requested travel");
        near(entry.alpha, 0f, "closed entry invisible"); check(entry.blocked, "invisible button rejects new down");
        NotificationClearMotion.Frame done = frame(.85f, Float.NEGATIVE_INFINITY, 900f, 0f);
        near(done.top, 600f, "85 percent native completion reaches original position");
        near(done.alpha, 1f, "85 percent native completion reaches original alpha factor");
        check(!done.blocked, "completed visible button accepts native touch");
        near(frame(1f, Float.NEGATIVE_INFINITY, 900f, 0f).top, done.top, "last panel segment does not add travel");
        near(frame(1f, Float.NEGATIVE_INFINITY, 900f, -11f).top, 589f, "last-row rebound follows native vertical motion");
        near(NotificationClearMotion.frame(1f, 600f, 48f, Float.NEGATIVE_INFINITY, 900f,
                18f, 32f, -25f, 7f, 12f).top, 582f, "signed user offset combines with native spring");
        near(frame(1f, 610f, 900f, 0f).top, 628f, "real visible notification bottom reserves requested gap");
        NotificationClearMotion.Frame blocked = frame(1f, 845f, 900f, 0f);
        near(blocked.alpha, 0f, "insufficient space hides clear button");
        check(blocked.blocked, "insufficient space blocks only new touch entry");
        check(blocked.top + 48f <= 900f, "button does not exceed native safe viewport");
        NotificationClearMotion.Frame partial = frame(1f, 828f, 900f, 0f);
        near(partial.alpha, .5f, "six pixels of fade band fades gradually");
        check(!partial.blocked, "partly visible safe button retains native click");
        NotificationClearMotion.Frame edge = frame(1f, 834f, 900f, 0f);
        near(edge.alpha, 0f, "zero remaining safety headroom never overlays notification");
        check(edge.blocked, "zero safety headroom rejects down");
        near(frame(Float.NaN, Float.NEGATIVE_INFINITY, 900f, 0f).alpha, 0f, "invalid panel fraction stays hidden");
        near(frame(2f, Float.NEGATIVE_INFINITY, 900f, 0f).alpha, 1f, "native overshoot bounded at full visibility");

        float previousAlpha = 0f, previousTop = 568f;
        for (int i = 0; i <= 85; i++) {
            float f = i / 100f;
            NotificationClearMotion.Frame opening = frame(f, Float.NEGATIVE_INFINITY, 900f, 0f);
            check(opening.alpha >= previousAlpha && opening.top >= previousTop, "entry moves down and fades in continuously");
            near(frame(f, Float.NEGATIVE_INFINITY, 900f, 0f).top, opening.top, "gesture reversal uses same native geometry");
            previousAlpha = opening.alpha; previousTop = opening.top;
        }
        for (int row = 780; row <= 860; row++) {
            NotificationClearMotion.Frame layout = frame(1f, row, 900f, 0f);
            if (!layout.blocked) {
                check(layout.top >= row + 18f, "visible clear button never overlaps current notification row");
                check(layout.top + 48f <= 900f, "visible clear button remains inside safe bottom");
            } else check(layout.alpha <= .08f, "unsafe geometry cannot leave a clickable opaque button");
        }

        NotificationClearMotion.Properties properties = new NotificationClearMotion.Properties(.8f, 5f);
        properties.apply(.5f, -32f);
        near(properties.appliedAlpha, .4f, "native alpha multiplied once");
        near(properties.appliedY, -27f, "native Y preserved underneath travel");
        for (int i = 0; i < 12; i++) properties.capture(.4f, -27f);
        near(properties.nativeAlpha, .8f, "own repeated frames cannot recapture composed alpha");
        near(properties.nativeY, 5f, "own repeated frames cannot recapture composed translation");
        near(properties.alpha(.6f, false), .3f, "native animation alpha remains multiplicative");
        near(properties.translation(9f, false), -23f, "native vertical writer continues under module offset");
        properties.release(.3f, -23f);
        near(properties.nativeAlpha, .6f, "off restores latest native alpha");
        near(properties.nativeY, 9f, "off restores latest native translation");
        check(!properties.owned, "released property ownership is cleared");
        properties.apply(0f, -9f);
        near(properties.appliedAlpha, 0f, "module may own zero alpha");
        near(properties.appliedY, 0f, "module may own zero translation");
        near(properties.alpha(0f, true), 0f, "new native zero in safe/native scope passes through");
        near(properties.translation(0f, true), 0f, "new native zero translation passes through");
        properties.release(0f, 0f);
        near(properties.nativeAlpha, 0f, "native zero is retained despite equal old module zero");
        near(properties.nativeY, 0f, "native zero Y is retained despite equal old module zero");
        properties.apply(.7f, 4f);
        properties.capture(.5f, 21f);
        properties.release(.5f, 21f);
        near(properties.nativeAlpha, .5f, "foreign alpha writer is retained at release");
        near(properties.nativeY, 21f, "foreign Y writer is retained at release");
        properties.apply(1f, 0f);
        near(properties.appliedAlpha, .5f, "re-enable starts from preserved foreign alpha");
        near(properties.appliedY, 21f, "re-enable starts from preserved foreign placement");
        properties.release(.5f, 21f); properties.release(.5f, 21f);
        check(!properties.owned, "repeated off is idempotent");

        Object nativeController = new Object();
        NotificationClearMotion.NativeScope outer = motion.beforeNative(nativeController);
        NotificationClearMotion.NativeScope inner = motion.beforeNative(nativeController);
        near(((ThreadLocal<Integer>) field(motion, "nativeDepth")).get(), 2f, "nested controller scopes recorded");
        inner.close(); inner.close();
        near(((ThreadLocal<Integer>) field(motion, "nativeDepth")).get(), 1f, "nested close is idempotent");
        outer.close(); check(((ThreadLocal<?>) field(motion, "nativeDepth")).get() == null, "outer scope releases native-depth guard");

        check(NotificationClearMotion.scrollRange(100, 90, 30) == 160, "scroll reserve adds only space unavailable natively");
        check(NotificationClearMotion.scrollRange(100, 30, 90) == 100, "existing empty space is not counted twice");
        check(Math.max(175, NotificationClearMotion.scrollRange(100, 90, 30)) == 175, "larger native-clock reserve is retained");
        check(Math.max(130, NotificationClearMotion.scrollRange(100, 90, 30)) == 160, "clear reserve combines by max with footer");
        check(NotificationClearMotion.scrollRange(Integer.MAX_VALUE-2, 10, 0) == Integer.MAX_VALUE, "scroll range arithmetic cannot wrap");
        check(NotificationClearMotion.scrollRange(-5, -8, -3) == 0, "invalid negative range bounded");

        // The real private State's stable pre-draw gate must not scan notifications,
        // allocate Frame buffers or feed animation/press transforms into layout.
        View parent = new View(new Context()), button = new View(new Context()); button.parent = parent;
        button.setAlpha(.8f); button.setTranslationY(5f);
        Stack stack = new Stack();
        Class<?> stateType = Class.forName("dev.puitheme.NotificationClearMotion$State");
        Constructor<?> constructor = stateType.getDeclaredConstructor(NotificationClearMotion.class,
                View.class, Object.class, View.class, ViewGroup.class); constructor.setAccessible(true);
        Object controller = new Object();
        Object state = constructor.newInstance(motion, button, controller, parent, stack);
        Method changed = stateType.getDeclaredMethod("geometryChanged", View.class, ViewGroup.class); changed.setAccessible(true);
        check((Boolean) changed.invoke(state, button, stack), "first real geometry signature requests measurement");
        Object frameBuffer = field(state, "frame");
        for (int i = 0; i < 10000; i++) {
            if ((Boolean) changed.invoke(state, button, stack)) throw new AssertionError("stable geometry marked dirty");
        }
        check(field(state, "frame") == frameBuffer, "ten thousand stable frames reuse the same geometry buffer");
        check(((java.util.Map<?,?>) field(motion, "rowAccess")).isEmpty(), "stable signature never scans notification rows");
        check(stack.layoutRequests == 0, "stable signature never requests stack layout");
        stack.mAnimationRunning = true;
        check((Boolean) changed.invoke(state, button, stack), "actual native row animation keeps geometry live");
        stack.mAnimationRunning = false;
        check(!(Boolean) changed.invoke(state, button, stack), "settled native animation returns to constant-time gate");
        button.setScaleY(.85f);
        check((Boolean) changed.invoke(state, button, stack), "native press-scale change updates safety geometry");
        check(!(Boolean) changed.invoke(state, button, stack), "stable press scale does not trigger repeated scans");
        stack.addView(new View(new Context()));
        check((Boolean) changed.invoke(state, button, stack), "new notification child invalidates geometry");
        check(!(Boolean) changed.invoke(state, button, stack), "unchanged child count settles without scan");

        @SuppressWarnings("unchecked") java.util.Map<View,Object> tracked = (java.util.Map<View,Object>) field(motion, "states");
        @SuppressWarnings("unchecked") java.util.ArrayList<Object> live = (java.util.ArrayList<Object>) field(motion, "liveStates");
        tracked.put(button, state); live.add(state); set(motion, "enabled", true); set(motion, "propertyTracking", true);
        check(motion.tracks(button), "exact native setter owner found without class reflection");
        check(!motion.tracks(new View(new Context())), "all other SystemUI views take fast native path");
        NotificationClearMotion.Properties scoped = (NotificationClearMotion.Properties) field(state, "properties");
        scoped.apply(.5f, -32f); button.setAlpha(.4f); button.setTranslationY(-27f);
        try (NotificationClearMotion.NativeScope scope = motion.beforeNative(controller)) {
            near(button.getAlpha(), .8f, "native fade start reads uncomposed alpha");
            near(button.getTranslationY(), -27f, "fade scope never restores/reapplies unrelated native Y");
            button.setAlpha(motion.nativeAlpha(button, 0f));
        }
        near(button.getAlpha(), 0f, "native zero survives optimized scope close");
        near(button.getTranslationY(), -27f, "optimized scope retains current visual translation");
        check(stack.layoutRequests == 0, "native fade scope does not trigger notification layout");
        check(((java.util.Map<?,?>) field(motion, "rowAccess")).isEmpty(), "native fade scope re-composes properties without row scan");
        Method restore = NotificationClearMotion.class.getDeclaredMethod("restore", stateType); restore.setAccessible(true);
        restore.invoke(motion, state);
        near(button.getAlpha(), 0f, "off restores latest native zero after optimized scope");
        near(button.getTranslationY(), 5f, "off restores stored native translation after optimized scope");
        System.out.println("NotificationClearMotionCheck passed: " + checks + " (native property ownership, geometry, entry/cancel/rebound, safety headroom, scroll reserve)");
    }
}
