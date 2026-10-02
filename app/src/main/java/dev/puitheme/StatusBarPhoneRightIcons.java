// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import java.lang.ref.WeakReference;

/** Only the verified Phone row follows the existing left-clock opacity phase; no copies or geometry. */
final class StatusBarPhoneRightIcons {
    private WeakReference<View> source = new WeakReference<>(null);
    private boolean enabled, allowed, owned, writing, released;
    private float nativeAlpha = 1f, appliedAlpha = 1f, opacity = 1f;
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { if (source.get() == view) apply(); }
        @Override public void onViewDetachedFromWindow(View view) { if (source.get() == view) clear(); }
    };

    void configure(boolean on) {
        if (ModuleLifecycle.removed()) { releaseRuntime(); return; }
        enabled = on && !released;
        if (!enabled) restore(); else apply();
    }

    void bind(View phone) {
        if (released || ModuleLifecycle.removed()) { releaseRuntime(); return; }
        if (source.get() == phone) return;
        clear();
        if (phone == null) return;
        source = new WeakReference<>(phone);
        nativeAlpha = phone.getAlpha();
        phone.addOnAttachStateChangeListener(attachment);
        apply();
    }

    void progress(float fraction, boolean permitted) {
        allowed = permitted && Float.isFinite(fraction) && !released && !ModuleLifecycle.removed();
        if (!allowed) { restore(); return; }
        opacity = StatusBarClosingIcons.closingOpacity(fraction);
        apply();
    }

    /** Native setter observations also retain exact zero while the module currently applies zero. */
    float nativeAlpha(View view, float value) {
        if (writing || source.get() != view || released || ModuleLifecycle.removed()) return value;
        nativeAlpha = value;
        if (!enabled || !allowed || !owned || !Float.isFinite(value)) return value;
        appliedAlpha = value * opacity;
        return appliedAlpha;
    }

    private void apply() {
        View view = source.get();
        if (!enabled || !allowed || released || ModuleLifecycle.removed() || view == null
                || !view.isAttachedToWindow()) { restore(); return; }
        float actual = view.getAlpha();
        if (!owned || actual != appliedAlpha) nativeAlpha = actual;
        if (!Float.isFinite(nativeAlpha)) { restore(); return; }
        float next = nativeAlpha * opacity;
        write(view, next);
        appliedAlpha = next; owned = true;
    }

    private void write(View view, float value) {
        if (view.getAlpha() == value) return;
        writing = true;
        try { view.setAlpha(value); } finally { writing = false; }
    }

    void restore() {
        View view = source.get();
        if (view != null && owned) {
            float actual = view.getAlpha();
            if (actual != appliedAlpha) nativeAlpha = actual;
            else write(view, nativeAlpha);
        }
        owned = false;
    }

    void clear() {
        View previous = source.get();
        restore();
        if (previous != null) previous.removeOnAttachStateChangeListener(attachment);
        source.clear();
    }

    void releaseRuntime() {
        if (!ModuleLifecycle.removed() || released) return;
        clear(); released = true; enabled = allowed = false;
    }
}
