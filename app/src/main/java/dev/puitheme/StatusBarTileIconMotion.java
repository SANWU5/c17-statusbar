// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exact inner QS tile paging, independent of tile appearance and the outer shade pager. */
final class StatusBarTileIconMotion implements StatusBarFixedIcons.HorizontalProgressReader {
    static final String MANAGER = "com.oplus.systemui.qs.base.widget.recyclerview.StaggeredPagerLayoutManager";
    static final String PAGER = "com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView";
    private static final Object[] NO_ARGS = new Object[0];
    final Class<?> managerType;
    private final Field owner, pages, layoutState, scrollX;
    private final Method horizontal, actualManager;
    private WeakReference<View> current = new WeakReference<>(null);
    private WeakReference<Object> currentManager = new WeakReference<>(null);
    private boolean warned;

    StatusBarTileIconMotion(ClassLoader loader) throws ReflectiveOperationException {
        managerType = loader.loadClass(MANAGER);
        owner = field(managerType, "recyclerView");
        pages = field(managerType, "pageCount");
        layoutState = field(managerType, "layoutState");
        scrollX = field(layoutState.getType(), "scrollX");
        horizontal = managerType.getDeclaredMethod("canScrollHorizontally");
        horizontal.setAccessible(true);
        actualManager = loader.loadClass(PAGER).getMethod("getLayoutManager");
        actualManager.setAccessible(true);
    }

    void changed(Object manager) {
        if (ModuleLifecycle.removed() || manager == null || !managerType.isInstance(manager)) return;
        try {
            View view = (View) owner.get(manager);
            if (view == null || !PAGER.equals(view.getClass().getName())) return;
            if (current.get() != view) current = new WeakReference<>(view);
            if (currentManager.get() != manager) currentManager = new WeakReference<>(manager);
        } catch (Throwable error) {
            unavailable(error);
        }
    }

    @Override public float fraction() { return 0f; }
    @Override public float fraction(View shadeRoot) {
        if (ModuleLifecycle.removed()) return 0f;
        View view = current.get();
        Object manager = currentManager.get();
        if (view == null || manager == null || !view.isAttachedToWindow() || shadeRoot == null
                || view.getRootView() != shadeRoot) return 0f;
        try {
            // Reopen and native layout reset need not send a page callback. Read only the
            // already bound manager's live primitive and width, never a cached phase.
            if (owner.get(manager) != view || actualManager.invoke(view, NO_ARGS) != manager) return 0f;
            int width = view.getMeasuredWidth();
            Object state = layoutState.get(manager);
            if (width <= 0 || pages.getInt(manager) <= 1 || state == null
                    || !Boolean.TRUE.equals(horizontal.invoke(manager, NO_ARGS))) return 0f;
            return (Math.abs((long) scrollX.getInt(state)) % width) / (float) width;
        } catch (Throwable error) { unavailable(error); return 0f; }
    }

    private void unavailable(Throwable error) {
        current.clear(); currentManager.clear();
        if (!warned) {
            warned = true;
            ModuleDiagnostics.error("hook", "Native inner tile icon progress unavailable; fixed endpoint retained", error);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field field = current.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
