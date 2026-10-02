// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exact separate-QS native ownership. Notification springs, clocks and list geometry are untouched. */
final class StatusBarQsIconAccess {
    static final String COMPONENT = "com.oplus.systemui.plugins.qs.OplusQSRootViewComponent";
    static final String LISTENER = COMPONENT + "$qsPanelExpandFractionListener$1";
    static final String FAKE = "com.oplus.systemui.plugins.qs.seamless.SeparateQSFakeStatusController";
    interface AnchorReader { View read(View nativeCopy); }
    private final NotificationBigClock receiver;
    private final AnchorReader anchorReader;
    private final Class<?> componentType;
    final Class<?> listenerType, fakeType;
    private final Field listenerOwner, view, fakeController, nativeIcons, fakeIcons;
    private final Field rawFraction, animatorManager, tracking, stub, expandFraction, pendingPosition;
    private final Method running, fractionAnimation, spring, finalPosition, keyguard;
    private final LeftAccess left;
    private WeakReference<Object> current = new WeakReference<>(null);
    private boolean warned;

    StatusBarQsIconAccess(ClassLoader loader, NotificationBigClock receiver, AnchorReader anchorReader)
            throws ReflectiveOperationException {
        this.receiver = receiver; this.anchorReader = anchorReader;
        componentType = loader.loadClass(COMPONENT);
        listenerType = loader.loadClass(LISTENER); fakeType = loader.loadClass(FAKE);
        listenerOwner = field(listenerType, "this$0");
        view = field(componentType, "mView");
        fakeController = field(componentType, "fakeStatusContainerController");
        nativeIcons = field(fakeType, "statusIconsView");
        fakeIcons = field(fakeType, "fakeStatusIconContainer");
        rawFraction = field(componentType, "curRawFraction");
        animatorManager = field(componentType, "qsPanelAnimatorManager");
        tracking = field(componentType, "isTracking");
        stub = field(componentType, "stub");
        running = method(componentType, "isFractionAnimationRunning");
        expandFraction = field(animatorManager.getType(), "qsPanelExpandFraction");
        fractionAnimation = method(expandFraction.getType(), "getFractionAnimation");
        pendingPosition = field(fractionAnimation.getReturnType(), "mPendingPosition");
        spring = method(fractionAnimation.getReturnType(), "getSpring");
        finalPosition = method(spring.getReturnType(), "getFinalPosition");
        keyguard = method(stub.getType(), "isKeyguardShowing");
        LeftAccess optionalLeft = null;
        try { optionalLeft = new LeftAccess(loader, fakeType); }
        catch (ReflectiveOperationException ignored) { }
        left = optionalLeft;
    }

    Class<?> componentType() { return componentType; }

    void listenerChanged(Object listener) {
        if (ModuleLifecycle.removed()) return;
        try { changed(listenerOwner.get(listener)); }
        catch (Throwable error) { unavailable(error); }
    }

    void fakeChanged(Object nativeFake) {
        if (ModuleLifecycle.removed()) return;
        Object component = current.get();
        if (component == null) return;
        try { if (fakeController.get(component) == nativeFake) changed(component); }
        catch (Throwable error) { unavailable(error); }
    }

    void changed(Object component) {
        if (ModuleLifecycle.removed() || component == null || !componentType.isInstance(component)) return;
        try {
            View panel = (View) view.get(component);
            if (panel == null || !panel.isAttachedToWindow()) { detached(component); return; }
            Object fake = fakeController.get(component);
            View icons = fake == null ? null : (View) nativeIcons.get(fake);
            View copy = fake == null ? null : (View) fakeIcons.get(fake);
            View phone = copy == null || anchorReader == null ? null : anchorReader.read(copy);
            float fraction = rawFraction.getFloat(component);
            Object manager = animatorManager.get(component);
            float target = actualTarget(manager);
            if (!finite(fraction) || !finite(target)) throw new IllegalStateException("Nonfinite native QS progress");
            boolean moving = tracking.getBoolean(component) || Boolean.TRUE.equals(running.invoke(component));
            Object host = stub.get(component);
            // Unknown keyguard state does not grant visual ownership.
            int state = host == null ? -1 : Boolean.TRUE.equals(keyguard.invoke(host)) ? 1 : 0;
            boolean closed = finite(fraction) && fraction <= 0f && finite(target) && target <= 0f && !moving;
            // Commit identity only after a complete snapshot; a failed replacement restores
            // the old receiver's actual owned sources, rather than detaching an unbound view.
            if (current.get() != component) current = new WeakReference<>(component);
            receiver.onSeparateQsStatusChanged(panel, icons, phone, copy, fraction, state, closed);
            if (left != null) try {
                View clockContainer = (View) left.clockContainer.get(fake);
                View notificationContainer = (View) left.notificationContainer.get(fake);
                Object clockElement = left.clockElement.get(fake), notificationElement = left.notificationElement.get(fake);
                View clockCopy = clockElement == null ? null : (View) left.elementView.invoke(clockElement);
                View notificationCopy = notificationElement == null ? null : (View) left.elementView.invoke(notificationElement);
                View clockSource = clockCopy == null || anchorReader == null ? null : anchorReader.read(clockCopy);
                View notificationSource = notificationCopy == null || anchorReader == null ? null : anchorReader.read(notificationCopy);
                receiver.onSeparateQsLeftChanged(panel, clockContainer, clockCopy, clockSource,
                        notificationContainer, notificationCopy, notificationSource, state, closed);
            } catch (Throwable ignored) { receiver.onSeparateQsLeftDetached(panel); }
        } catch (Throwable error) { unavailable(error); }
    }

    private float actualTarget(Object manager) throws ReflectiveOperationException {
        Object fraction = manager == null ? null : expandFraction.get(manager);
        Object animation = fraction == null ? null : fractionAnimation.invoke(fraction);
        if (animation == null) throw new IllegalStateException("Native QS spring unavailable");
        // skipToEnd(false) and tryAnimateToFinalPosition bypass curCalcFinalPosition.
        // A running COUI spring queues its next target until the next native frame.
        float pending = pendingPosition.getFloat(animation);
        if (pending != Float.MAX_VALUE) return pending;
        Object force = spring.invoke(animation);
        if (force == null) throw new IllegalStateException("Native QS spring force unavailable");
        return ((Number) finalPosition.invoke(force)).floatValue();
    }

    private static final class LeftAccess {
        final Field clockContainer, notificationContainer, clockElement, notificationElement;
        final Method elementView;
        LeftAccess(ClassLoader loader, Class<?> fake) throws ReflectiveOperationException {
            clockContainer = field(fake, "fakeClockContainer");
            notificationContainer = field(fake, "fakeNotificationIconContainer");
            clockElement = field(fake, "qsFakeClock"); notificationElement = field(fake, "qsFakeNotificationIcon");
            elementView = method(loader.loadClass("com.android.systemui.plugins.qs.QSFakeStatusElement"), "getElementView");
        }
    }

    void detached(Object component) {
        if (component == null || !componentType.isInstance(component)) return;
        // A retired component must not release its replacement's already acquired sources.
        if (current.get() != null && current.get() != component) return;
        try { receiver.onSeparateQsStatusDetached((View) view.get(component)); }
        catch (Throwable error) { unavailable(error); }
        current.clear();
    }

    private void unavailable(Throwable error) {
        Object component = current.get();
        current.clear();
        if (component != null) try { receiver.onSeparateQsStatusDetached((View) view.get(component)); }
        catch (Throwable ignored) { }
        if (!warned && !ModuleLifecycle.removed()) {
            warned = true;
            ModuleDiagnostics.error("hook", "Separate QS icon source unavailable; native row retained", error);
        }
    }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field field = current.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Method method = current.getDeclaredMethod(name, args); method.setAccessible(true); return method;
        } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
