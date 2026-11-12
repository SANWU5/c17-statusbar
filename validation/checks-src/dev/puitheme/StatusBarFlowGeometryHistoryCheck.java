package dev.puitheme;

import android.view.View;
import com.android.systemui.statusbar.phone.StatusIconContainer.StatusIconState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Replays a root-only placement followed by the ROM's next icon transition.
 * The ROM reconstructs its X start from distanceToViewEnd, not View.translationX. */
public final class StatusBarFlowGeometryHistoryCheck {
    private static int checks;
    private static Object field(Object owner, String name) throws Exception {
        Field value = owner.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(owner);
    }
    private static void near(float expected, float actual, String why) {
        checks++;
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > .001f)
            throw new AssertionError(why + ": " + expected + " != " + actual);
    }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static float nativeStart(View parent, View icon, StatusIconState state, int direction) {
        // StatusIconState.applyToView: RTL 0x81, LTR 0x87..0x8f on the captured ROM.
        return direction == View.LAYOUT_DIRECTION_RTL ? state.distanceToViewEnd
                : parent.getWidth() - icon.getWidth() - state.distanceToViewEnd;
    }
    public static void main(String[] arguments) throws Exception {
        Constructor<?> factory = Class.forName("dev.puitheme.NativeXRebaseCheck$Rig").getDeclaredConstructor();
        factory.setAccessible(true);
        for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL})
            for (int visibility : new int[]{View.VISIBLE, View.INVISIBLE, View.GONE}) {
                Object rig = factory.newInstance();
                View parent = (View) field(rig, "parent"), icon = (View) field(rig, "icon");
                parent.useLayoutBoundsForCheck = true; icon.useLayoutBoundsForCheck = true;
                parent.layout(0, 0, 464, 80); icon.layout(0, 0, 88, 80);
                Field rtl = parent.getClass().getDeclaredField("direction"); rtl.setAccessible(true); rtl.setInt(parent, direction);
                StatusIconState state = (StatusIconState) field(rig, "state");
                StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(rig, "access");
                icon.setVisibility(visibility); icon.setAlpha(.37f); state.qsExpansionTransitioning = true;
                for (float target : new float[]{205f, 373f, 311f, -20f, 205f}) {
                    state.setXTranslation(target);
                    access.applyGeometry(state, icon);
                    near(target, icon.getTranslationX(), "root-only geometry reaches the projected target");
                    near(icon.getTranslationX(), nativeStart(parent, icon, state, direction),
                            "next native transition cannot reconstruct an obsolete start beside the battery");
                    // A new native destination still starts at the last rendered location.
                    state.setXTranslation(target + 12f);
                    near(target, nativeStart(parent, icon, state, direction), "new destination retains continuous native start");
                    near(.37f, icon.getAlpha(), "geometry bookkeeping preserves native alpha");
                    check(icon.getVisibility() == visibility && state.qsExpansionTransitioning,
                            "geometry bookkeeping does not consume visibility or QS lifecycle");
                }
                state.justAdded = true; state.distanceToViewEnd = -1f;
                access.applyGeometry(state, icon);
                near(-1f, state.distanceToViewEnd, "new icon keeps its native uninitialized sentinel");
                check(state.justAdded, "geometry never consumes the native add lifecycle");
            }
        for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
            Object rig=factory.newInstance();View parent=(View)field(rig,"parent"),icon=(View)field(rig,"icon");
            Field rtl=parent.getClass().getDeclaredField("direction");rtl.setAccessible(true);rtl.setInt(parent,direction);
            StatusIconState state=(StatusIconState)field(rig,"state");
            StatusBarFlowLayout.NativeAccess access=(StatusBarFlowLayout.NativeAccess)field(rig,"access");
            Method animate=rig.getClass().getDeclaredMethod("animate",long.class);animate.setAccessible(true);animate.invoke(rig,375L);
            android.animation.ObjectAnimator animator=(android.animation.ObjectAnimator)field(rig,"animator");
            access.applyGeometry(state,icon);
            near(205f,nativeStart(parent,icon,state,direction),"an ongoing curve records its committed end, not an intermediate sample");
            check(animator.getCurrentPlayTime()==375L&&animator.isRunning(),"root geometry retains the running native curve and progress");
            float current=icon.getTranslationX();state.setXTranslation(217f);access.retargetFromCurrent(state,icon);
            near(current,nativeStart(parent,icon,state,direction),"a subsequent new destination starts at the current interpolated position");
            check(icon.getTag(com.android.systemui.statusbar.notification.stack.ViewState.TAG_ANIMATOR_TRANSLATION_X)==animator,
                    "geometry/history handoff never replaces the native animator");
        }
        System.out.println("StatusBarFlowGeometryHistoryCheck: " + checks + " checks passed");
    }
}
