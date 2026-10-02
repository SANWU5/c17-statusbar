// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Only masks verified notification-page copies while expanded or switching pages.
 * Phone and control-center LEFT use the same complete native handoff as RIGHT. */
final class StatusBarPageLeftIcons implements StatusBarFixedIcons.LeftCopyPolicy {
    interface PageVisibilityReader { float qsVisibility(View shadeRoot); }
    private final Map<View, Copy> copies = new WeakHashMap<>();
    private boolean hiding, released;
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { retire(view); }
    };

    void notification(View copy, View source, boolean hide, int kind) {
        if (copy == null) return;
        Copy state = copies.get(copy);
        if (released || ModuleLifecycle.removed() || !copy.isAttachedToWindow()
                || source != null && (!source.isAttachedToWindow() || source.getRootView() == copy.getRootView())) {
            retire(copy); return;
        }
        if (state == null) {
            if (source == null || !hide) return;
            state = new Copy(source); copies.put(copy, state);
            state.alpha.bind(copy); copy.addOnAttachStateChangeListener(attachment);
        } else if (state.source.get() != source) state.source = new WeakReference<>(source);
        state.kind = kind;
        state.alpha.configure(hide); state.alpha.progress(1f, hide);
    }

    void progress(boolean hide) {
        hiding = hide && !released && !ModuleLifecycle.removed();
        for (Copy copy : copies.values()) { copy.alpha.configure(hiding); copy.alpha.progress(1f, hiding); }
    }

    float notificationAlpha(View target, float value) {
        Copy state = copies.get(target);
        return state == null ? value : state.alpha.nativeAlpha(target, value);
    }

    boolean notificationSourceBound(View view, View source) {
        Copy state = stateFor(view);
        if (state == null) return false;
        if (state.source.get() != source) state.source = new WeakReference<>(source);
        return hiding;
    }

    boolean suppressNotification(View view, View source) {
        if (!hiding || released || ModuleLifecycle.removed() || view == null || source == null
                || !view.isAttachedToWindow() || !source.isAttachedToWindow()
                || view.getRootView() == source.getRootView()) return false;
        Copy state = stateFor(view);
        return state != null && state.source.get() == source
                && (state.kind == StatusIconTransition.CLOCK || state.kind == StatusIconTransition.NOTIFICATIONS);
    }

    private Copy stateFor(View view) {
        for (int depth = 0; view != null && depth < 16; depth++) {
            Copy state = copies.get(view);
            if (state != null) return state;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return null;
    }

    private void retire(View view) {
        Copy state = copies.remove(view);
        if (state != null) state.alpha.clear();
        view.removeOnAttachStateChangeListener(attachment);
    }

    void restoreNotificationCopies() {
        for (Map.Entry<View, Copy> entry : copies.entrySet()) {
            entry.getValue().alpha.clear();
            if (entry.getKey() != null) entry.getKey().removeOnAttachStateChangeListener(attachment);
        }
        copies.clear(); hiding = false;
    }

    void release() {
        restoreNotificationCopies();
        if (ModuleLifecycle.removed()) released = true;
    }

    @Override public boolean nativeQsCopy(View copy, View source, int kind) { return false; }
    @Override public boolean suppressNotificationCopy(View copy, View source, int kind) {
        return (kind == StatusIconTransition.CLOCK || kind == StatusIconTransition.NOTIFICATIONS)
                && suppressNotification(copy, source);
    }

    private static final class Copy {
        WeakReference<View> source;
        final StatusBarPhoneRightIcons alpha = new StatusBarPhoneRightIcons();
        int kind;
        Copy(View view) { source = new WeakReference<>(view); }
    }
}
