// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Actual compiled NotificationBigClock ownership gate; no substitute policy implementation. */
public final class StatusBarNotificationVerticalCheck {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < .00002f, message + ": " + actual + " expected " + expected);
    }
    private static void set(Object receiver, String name, Object value) throws Exception {
        Field f = receiver.getClass().getDeclaredField(name); f.setAccessible(true); f.set(receiver, value);
    }
    private static Object get(Object receiver, String name) throws Exception {
        Field f = receiver.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(receiver);
    }
    private static void refresh(NotificationBigClock receiver) throws Exception {
        Method m = NotificationBigClock.class.getDeclaredMethod("refreshStatusIcons"); m.setAccessible(true); m.invoke(receiver);
    }
    private static final class Phone extends View {
        NotificationBigClock receiver;
        int alphaWrites;
        Phone() { super(new Context()); }
        @Override public void setAlpha(float value) {
            alphaWrites++; super.setAlpha(receiver == null ? value : receiver.statusIconPhoneRightAlpha(this, value));
        }
    }
    private static Bundle on() {
        Bundle b = new Bundle(); b.putBoolean(StatusBarShadeIconSettings.MASTER, true); return b;
    }
    private static void notification(NotificationBigClock receiver, float fraction) throws Exception {
        set(receiver, "barState", 0); set(receiver, "fraction", fraction);
        set(receiver, "panelSettledClosed", false); set(receiver, "separateQsSettledClosed", true);
        set(receiver, "qsExpanded", false); refresh(receiver);
    }
    public static void main(String[] args) throws Exception {
        NotificationBigClock receiver = new NotificationBigClock(); Phone phone = new Phone();
        phone.setAlpha(.8f); phone.setTranslationX(3f); phone.setTranslationY(4f); phone.receiver = receiver;
        StatusBarPhoneRightIcons right = (StatusBarPhoneRightIcons) get(receiver, "phoneRightIcons");
        right.bind(phone); receiver.configure(on());
        for (int pass = 0; pass < 30; pass++) {
            for (int step = 0; step <= 100; step++) {
                float fraction = step / 100f; notification(receiver, fraction);
                near(phone.getAlpha(), .8f * StatusBarClosingIcons.closingOpacity(fraction), "notification pull opacity " + step);
                near(phone.getTranslationX(), 3f, "right horizontal position stays native");
                near(phone.getTranslationY(), 4f, "right vertical position stays native");
            }
            for (int step = 100; step >= 0; step--) {
                float fraction = step / 100f; notification(receiver, fraction);
                near(phone.getAlpha(), .8f * StatusBarClosingIcons.closingOpacity(fraction), "notification retract opacity " + step);
            }
            receiver.onPanelMotionState(false, true);
            near(phone.getAlpha(), .8f, "real settled close restores exact native baseline");
        }
        notification(receiver, .1f); near(phone.getAlpha(), .4f, "notification half-opacity before CC gate");
        set(receiver, "separateQsSettledClosed", false); refresh(receiver);
        near(phone.getAlpha(), .8f, "CC ownership immediately restores Phone native handoff");
        for (int step = 0; step <= 100; step++) {
            set(receiver, "fraction", step / 100f); refresh(receiver);
            near(phone.getAlpha(), .8f, "CC callbacks never multiply Phone opacity");
        }
        set(receiver, "separateQsSettledClosed", true); set(receiver, "qsExpanded", true); refresh(receiver);
        near(phone.getAlpha(), .8f, "expanded CC gate leaves native opacity");
        notification(receiver, .1f); set(receiver, "barState", 1); refresh(receiver);
        near(phone.getAlpha(), .8f, "lockscreen gate restores native opacity");
        notification(receiver, .1f);
        phone.setAlpha(.6f); near(phone.getAlpha(), .3f, "native baseline changes remain multiplied once");
        notification(receiver, .2f); near(phone.getAlpha(), 0f, "fully hidden right row at end of entry fade");
        phone.setAlpha(0f); receiver.onPanelMotionState(false, true);
        near(phone.getAlpha(), 0f, "native zero written while module zero is preserved at close");
        phone.setAlpha(.7f); notification(receiver, .1f); near(phone.getAlpha(), .35f, "new baseline is not stale after close");
        Bundle safe = on(); safe.putBoolean(StatusBarSettings.SAFE_MODE, true); receiver.configure(safe);
        near(phone.getAlpha(), .7f, "safe mode restores the actual native alpha");
        receiver.configure(on()); notification(receiver, .1f);
        int writes = phone.alphaWrites, layouts = phone.layoutRequests, invalidations = phone.invalidations;
        for (int i = 0; i < 1000; i++) refresh(receiver);
        check(phone.alphaWrites == writes && phone.layoutRequests == layouts && phone.invalidations == invalidations,
                "identical notification callbacks add no write/layout/invalidate");
        receiver.configure(new Bundle()); near(phone.getAlpha(), .7f, "all masters off restores native alpha");
        check(phone.getOverlay().drawables.isEmpty() && phone.getViewTreeObserver().preDrawListeners.isEmpty(),
                "notification-only opacity adds no fake view, redraw observer or overlay");
        System.out.println("Notification vertical right-row checks passed: " + checks);
    }
}
