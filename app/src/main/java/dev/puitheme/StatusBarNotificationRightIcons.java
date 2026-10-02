// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import android.content.res.Configuration;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Each exact notification header keeps its own native alpha baseline; no copies or geometry. */
final class StatusBarNotificationRightIcons {
    private final Map<View, Scene> scenes = new WeakHashMap<>();
    // These values contain only weak View references. Global setAlpha never traverses headers.
    private final Map<View, StatusBarPhoneRightIcons> alphaOwners = new WeakHashMap<>();
    private final Map<View, ClockVisibility> visibilityOwners = new WeakHashMap<>();
    private final Map<View, Scene> copyOwners = new WeakHashMap<>();
    private final ArrayList<WeakReference<Scene>> observed = new ArrayList<>();
    private boolean enabled, allowed, disableClock, released;
    private float fraction, opacity = 1f;

    private static final class Scene {
        final WeakReference<View> header;
        final StatusBarPhoneRightIcons row = new StatusBarPhoneRightIcons(),
                fake = new StatusBarPhoneRightIcons(), clock = new StatusBarPhoneRightIcons();
        final ClockVisibility visibility = new ClockVisibility();
        float currentFraction;
        boolean currentPermitted, clockDisabled, iconsEnabled;
        final View.OnAttachStateChangeListener clockAttachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { refresh(currentFraction, currentPermitted); }
            @Override public void onViewDetachedFromWindow(View view) { clock.clear(); visibility.restore(); }
        };
        WeakReference<View> icons = new WeakReference<>(null), copy = new WeakReference<>(null),
                phone = new WeakReference<>(null), smallClock = new WeakReference<>(null);
        Scene(View nativeHeader) { header = new WeakReference<>(nativeHeader); }
        boolean valid() {
            View h = header.get(), r = icons.get(), c = copy.get();
            return h != null && r != null && c != null && h.isAttachedToWindow()
                    && r.isAttachedToWindow() && c.isAttachedToWindow()
                    && within(r, h) && r != c && r.getRootView() == c.getRootView();
        }
        void refresh(float fraction, boolean permitted) {
            currentFraction = fraction; currentPermitted = permitted;
            boolean valid = permitted && valid();
            row.progress(fraction, valid); fake.progress(fraction, valid);
            View h = header.get(), c = smallClock.get();
            boolean portrait = portrait(c != null ? c : h);
            // Parent onAttachedToWindow precedes the children's attach dispatch. Keep
            // the exact pending clock, then bind it when that child really attaches.
            clock.bind(c); visibility.bind(c); visibility.configure(clockDisabled && portrait);
            clock.configure(iconsEnabled && !clockDisabled && portrait);
            clock.progress(fraction, portrait && !clockDisabled && permitted && h != null && c != null
                    && h.isAttachedToWindow() && c.isAttachedToWindow() && within(c, h));
        }
        void configure(boolean enabled, boolean disableClock, float fraction, boolean permitted) {
            clockDisabled = disableClock;
            iconsEnabled = enabled;
            row.configure(enabled); fake.configure(enabled);
            refresh(fraction, enabled && permitted);
        }
        void restore() { row.restore(); fake.restore(); clock.restore(); if (!clockDisabled || !portrait(smallClock.get())) visibility.restore(); }
    }

    /** The replaced native header clock stays in its layout; it never participates in opacity motion. */
    private static final class ClockVisibility {
        WeakReference<View> source = new WeakReference<>(null);
        boolean enabled, owned, writing, releasingAlpha;
        int nativeValue, applied;
        void bind(View target) {
            if (source.get() == target) return;
            clear(); source = new WeakReference<>(target);
            if (target != null) nativeValue = target.getVisibility();
            apply();
        }
        void configure(boolean on) { enabled = on; if (on) apply(); else restore(); }
        int nativeVisibility(View target, int value) {
            if (writing || source.get() != target) return value;
            nativeValue = value;
            if (!enabled || !portrait(target) || ModuleLifecycle.removed()) { owned = false; return value; }
            applied = value == View.GONE ? View.GONE : View.INVISIBLE; owned = true; return applied;
        }
        void apply() {
            View view = source.get();
            if (!enabled || !portrait(view) || ModuleLifecycle.removed() || view == null) { restore(); return; }
            if (!owned || view.getVisibility() != applied) nativeValue = view.getVisibility();
            applied = nativeValue == View.GONE ? View.GONE : View.INVISIBLE;
            owned = true; write(view, applied);
        }
        void write(View view, int value) {
            if (view.getVisibility() == value) return;
            writing = true; try { view.setVisibility(value); } finally { writing = false; }
        }
        void restore() {
            View view = source.get();
            if (owned && view != null) {
                if (view.getVisibility() != applied) nativeValue = view.getVisibility();
                else write(view, nativeValue);
            }
            owned = false;
        }
        void clear() { restore(); source.clear(); }
    }

    void configure(boolean on) {
        configure(on, false);
    }
    void configure(boolean on, boolean hideNativeClock) {
        if (ModuleLifecycle.removed()) { releaseRuntime(); return; }
        enabled = on && !released; disableClock = hideNativeClock && !released;
        for (int i = observed.size() - 1; i >= 0; i--) {
            Scene scene = observed.get(i).get();
            if (scene == null) observed.remove(i);
            else scene.configure(enabled, disableClock, fraction, allowed);
        }
    }

    private Scene scene(View nativeHeader) {
        if (released || ModuleLifecycle.removed() || nativeHeader == null) return null;
        Scene scene = scenes.get(nativeHeader);
        if (scene == null) {
            scene = new Scene(nativeHeader); scenes.put(nativeHeader, scene);
            observed.add(new WeakReference<>(scene));
            scene.configure(enabled, disableClock, fraction, allowed);
        }
        return scene;
    }

    /** Called only by OplusSimpleQSFakeController with its own OplusQSSimpleHeader. */
    void bind(View nativeHeader, View nativeIcons, View nativeCopy, View nativePhone) {
        if (ModuleLifecycle.removed()) { releaseRuntime(); return; }
        Scene scene = scene(nativeHeader);
        if (scene == null) return;
        if (scene.icons.get() != nativeIcons) {
            bindSlot(scene.row, scene.icons.get(), nativeIcons);
            scene.icons = new WeakReference<>(nativeIcons);
        }
        if (scene.copy.get() != nativeCopy) {
            View previous = scene.copy.get();
            if (previous != null && copyOwners.get(previous) == scene) copyOwners.remove(previous);
            bindSlot(scene.fake, previous, nativeCopy);
            scene.copy = new WeakReference<>(nativeCopy);
            if (nativeCopy != null) copyOwners.put(nativeCopy, scene);
        }
        if (scene.phone.get() != nativePhone) scene.phone = new WeakReference<>(nativePhone);
        // A child can detach/re-attach while its header and controller fields stay identical.
        // bind() is O(1) for a live source and re-registers a released same-object child.
        scene.row.bind(nativeIcons); scene.fake.bind(nativeCopy);
        scene.refresh(fraction, enabled && allowed);
    }

    void clock(View nativeHeader, View nativeClock) {
        if (ModuleLifecycle.removed()) { releaseRuntime(); return; }
        Scene scene = scene(nativeHeader);
        if (scene == null) return;
        if (scene.smallClock.get() != nativeClock) {
            View previous = scene.smallClock.get();
            if (previous != null) {
                previous.removeOnAttachStateChangeListener(scene.clockAttachment);
                if (visibilityOwners.get(previous) == scene.visibility) visibilityOwners.remove(previous);
            }
            bindSlot(scene.clock, previous, nativeClock);
            scene.smallClock = new WeakReference<>(nativeClock);
            if (nativeClock != null) {
                nativeClock.addOnAttachStateChangeListener(scene.clockAttachment);
                visibilityOwners.put(nativeClock, scene.visibility);
            }
        }
        scene.clock.bind(nativeClock);
        scene.refresh(fraction, enabled && allowed);
    }

    private void bindSlot(StatusBarPhoneRightIcons owner, View previous, View target) {
        if (previous != null && alphaOwners.get(previous) == owner) alphaOwners.remove(previous);
        owner.bind(target);
        if (target != null) alphaOwners.put(target, owner);
    }

    void progress(float nativeFraction, boolean permitted) {
        allowed = permitted && Float.isFinite(nativeFraction) && !released && !ModuleLifecycle.removed();
        fraction = nativeFraction;
        opacity = allowed ? StatusBarClosingIcons.closingOpacity(nativeFraction) : 1f;
        for (int i = observed.size() - 1; i >= 0; i--) {
            Scene scene = observed.get(i).get();
            if (scene == null) observed.remove(i);
            else scene.refresh(fraction, enabled && allowed);
        }
    }

    /** O(1) exact setter lookup, including new native zero while disabled/safe. */
    float nativeAlpha(View target, float value) {
        if (released || ModuleLifecycle.removed()) return value;
        StatusBarPhoneRightIcons owner = alphaOwners.get(target);
        ClockVisibility visibility = visibilityOwners.get(target);
        if (owner != null && visibility != null && !portrait(target) && !visibility.releasingAlpha) {
            visibility.releasingAlpha = true;
            try { owner.configure(false); } finally { visibility.releasingAlpha = false; }
        }
        return owner == null ? value : owner.nativeAlpha(target, value);
    }
    int nativeVisibility(View target, int value) {
        if (released || ModuleLifecycle.removed()) return value;
        ClockVisibility owner = visibilityOwners.get(target);
        return owner == null ? value : owner.nativeVisibility(target, value);
    }

    private Scene copyScene(View nativeCopy) {
        for (int depth = 0; nativeCopy != null && depth < 16; depth++) {
            Scene scene = copyOwners.get(nativeCopy);
            if (scene != null) return scene;
            nativeCopy = nativeCopy.getParent() instanceof View ? (View) nativeCopy.getParent() : null;
        }
        return null;
    }

    boolean ownsCopy(View nativeCopy) {
        if (!enabled || !allowed || released || ModuleLifecycle.removed() || opacity > .5f / 255f) return false;
        Scene scene = copyScene(nativeCopy);
        return scene != null && scene.valid();
    }

    /** A fake dispatch can source.draw() directly; only its exact scene may be skipped. */
    boolean suppressCopy(View nativeCopy, View nativePhone) {
        if (!ownsCopy(nativeCopy) || nativePhone == null) return false;
        Scene scene = copyScene(nativeCopy);
        return scene != null && nativePhone == scene.phone.get() && nativePhone.isAttachedToWindow()
                && nativePhone.getRootView() != nativeCopy.getRootView();
    }

    void copyBound(View nativeCopy, View nativePhone) {
        if (released || ModuleLifecycle.removed()) return;
        Scene scene = copyScene(nativeCopy);
        if (scene != null && scene.phone.get() != nativePhone) scene.phone = new WeakReference<>(nativePhone);
    }

    void detached(View nativeHeader) {
        Scene scene = scenes.remove(nativeHeader);
        if (scene == null) return;
        clear(scene);
        for (int i = observed.size() - 1; i >= 0; i--)
            if (observed.get(i).get() == null || observed.get(i).get() == scene) observed.remove(i);
    }

    private void clear(Scene scene) {
        View r = scene.icons.get(), c = scene.copy.get(), clock = scene.smallClock.get();
        if (r != null && alphaOwners.get(r) == scene.row) alphaOwners.remove(r);
        if (c != null && alphaOwners.get(c) == scene.fake) alphaOwners.remove(c);
        if (clock != null) {
            if (alphaOwners.get(clock) == scene.clock) alphaOwners.remove(clock);
            if (visibilityOwners.get(clock) == scene.visibility) visibilityOwners.remove(clock);
            clock.removeOnAttachStateChangeListener(scene.clockAttachment);
        }
        if (c != null && copyOwners.get(c) == scene) copyOwners.remove(c);
        scene.row.clear(); scene.fake.clear(); scene.clock.clear(); scene.visibility.clear();
        scene.header.clear(); scene.icons.clear(); scene.copy.clear(); scene.phone.clear(); scene.smallClock.clear();
    }

    void restore() {
        for (int i = observed.size() - 1; i >= 0; i--) {
            Scene scene = observed.get(i).get();
            if (scene != null) scene.restore();
        }
    }

    void releaseRuntime() {
        if (!ModuleLifecycle.removed() || released) return;
        for (int i = observed.size() - 1; i >= 0; i--) {
            Scene scene = observed.get(i).get();
            if (scene != null) {
                clear(scene); scene.row.releaseRuntime(); scene.fake.releaseRuntime(); scene.clock.releaseRuntime();
            }
        }
        scenes.clear(); alphaOwners.clear(); visibilityOwners.clear(); copyOwners.clear(); observed.clear();
        released = true; enabled = allowed = false;
    }

    private static boolean within(View child, View parent) {
        if (parent == null) return false;
        for (int depth = 0; child != null && depth < 16; depth++) {
            if (child == parent) return true;
            child = child.getParent() instanceof View ? (View) child.getParent() : null;
        }
        return false;
    }
    private static boolean portrait(View view) {
        return view != null && view.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }
}
