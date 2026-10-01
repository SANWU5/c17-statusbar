// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads the actual native progressive spring, rather than the page's prewritten target X. */
final class StatusBarIconPageMotion implements StatusBarFixedIcons.HorizontalProgressReader {
    final Method animatedValue;
    private final Field animation, helperDriver, screenWidth, pagerView;
    private final Method horizontalValue, progressiveController, progressiveHelper, logicalOffset;
    private WeakReference<Object> pager = new WeakReference<>(null);
    private WeakReference<Object> driver = new WeakReference<>(null);
    private boolean warned;

    StatusBarIconPageMotion(ClassLoader loader, Class<?> pagerType) throws ReflectiveOperationException {
        animation = field(pagerType, "slidSwitchAnimation");
        screenWidth = field(pagerType, "screenWidth");
        pagerView = field(pagerType, "mView");
        horizontalValue = method(animation.getType(), "getHorizontalTranslationValue");
        progressiveController = method(horizontalValue.getReturnType(), "getProgressiveController");
        progressiveHelper = method(progressiveController.getReturnType(), "getProgressiveHelper");
        helperDriver = field(progressiveHelper.getReturnType(), "progressDriver");
        Class<?> driverType = loader.loadClass("com.coui.animatekit.progressive.internal.ProgressiveProgressDriver");
        logicalOffset = method(driverType, "getLogicalOffset");
        animatedValue = method(driverType, "onAnimatedValue", Float.TYPE);
    }

    void bind(Object nativePager) {
        pager = new WeakReference<>(nativePager);
        try { currentDriver(); } catch (Throwable error) { unavailable(error); }
    }

    boolean owns(Object nativeDriver) {
        if (nativeDriver == null) return false;
        try { return nativeDriver == currentDriver(); }
        catch (Throwable error) { unavailable(error); return false; }
    }

    @Override public float fraction() {
        try {
            Object nativePager = pager.get(), nativeDriver = currentDriver();
            if (nativePager == null || nativeDriver == null) return 0f;
            float width = ((Number) screenWidth.get(nativePager)).floatValue();
            if (!(width > 0f)) {
                Object view = pagerView.get(nativePager);
                width = view instanceof View ? ((View) view).getWidth() : 0f;
            }
            return StatusIconTransition.pageFraction(((Number) logicalOffset.invoke(nativeDriver)).floatValue(), width);
        } catch (Throwable error) { unavailable(error); return 0f; }
    }

    private Object currentDriver() throws ReflectiveOperationException {
        Object owner = pager.get();
        if (owner == null) { driver.clear(); return null; }
        Object nativeAnimation = animation.get(owner);
        Object value = nativeAnimation == null ? null : horizontalValue.invoke(nativeAnimation);
        Object controller = value == null ? null : progressiveController.invoke(value);
        Object helper = controller == null ? null : progressiveHelper.invoke(controller);
        Object current = helper == null ? null : helperDriver.get(helper);
        if (driver.get() != current) driver = new WeakReference<>(current);
        return current;
    }

    private void unavailable(Throwable error) {
        driver.clear();
        if (!warned) {
            warned = true;
            ModuleDiagnostics.error("shade-icons", "Native horizontal icon progress unavailable; vertical transition retained", error);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field result = current.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Method result = current.getDeclaredMethod(name, arguments); result.setAccessible(true); return result;
        } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
