// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** The OS17 independent mobile binder owns one native badge per subscription. */
final class NativeNetworkBadgeBindings {
    interface Access {
        TextView text(Object binding) throws ReflectiveOperationException;
        View image(Object binding) throws ReflectiveOperationException;
        View host(Object binding) throws ReflectiveOperationException;
        int subscription(Object binding) throws ReflectiveOperationException;
        Object model(Object binding) throws ReflectiveOperationException;
        Parts parts(Object model) throws ReflectiveOperationException;
    }
    interface TextStyler {
        void beforeNative(TextView view);
        void updated(TextView view, String prefix, String suffix, int part);
    }
    static final class Parts {
        static final Parts UNKNOWN = new Parts(null, null);
        final String prefix, suffix;
        Parts(String prefix, String suffix) { this.prefix = prefix; this.suffix = suffix; }
        boolean known() { return prefix != null && suffix != null; }
    }
    private Access access;
    private final TextStyler styler;
    private final SingleMobileIconControls signal;
    private FeatureOptions features = FeatureOptions.from(Collections.emptyMap());
    private int activeSubscription = -1;
    private boolean released, warned;
    private long nativeEvents, bindings, applications;
    private final Map<Object, Glyph> rows = new WeakHashMap<>();
    private final Map<TextView, Glyph> glyphs = new WeakHashMap<>();
    private static final class Glyph {
        final WeakReference<TextView> text;
        WeakReference<View> image = new WeakReference<>(null), host = new WeakReference<>(null);
        WeakReference<Object> owner = new WeakReference<>(null);
        View.OnAttachStateChangeListener attachment;
        int subscription = -1, depth, textVisibility, imageVisibility;
        boolean imageKnown, hidden, detached, eventModelKnown;
        Parts parts = Parts.UNKNOWN;
        Glyph(TextView view) {
            text = new WeakReference<>(view); textVisibility = view.getVisibility();
            detached = !view.isAttachedToWindow();
        }
    }
    NativeNetworkBadgeBindings(NativeNetworkBadgeControls controls, SingleMobileIconControls signal) {
        this(null, new TextStyler() {
            public void beforeNative(TextView view) { controls.beforeNativeTextViewUpdate(view); }
            public void updated(TextView view, String prefix, String suffix, int part) {
                controls.onTextViewNativeUpdated(view, prefix, suffix, part);
            }
        }, signal);
    }
    NativeNetworkBadgeBindings(Access access, TextStyler styler, SingleMobileIconControls signal) {
        this.access = access; this.styler = styler; this.signal = signal;
    }
    void resolve(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> binder = loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding");
        Class<?> model = loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel");
        Class<?> nativeType = loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.data.OplusNetworkTypeText");
        Class<?> oplus = loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusMobileIconViewModel");
        Method text = binder.getMethod("getMobileTypeText"), host = binder.getMethod("getViewGroup");
        Field image = binder.getDeclaredField("mobileTypeImage"); image.setAccessible(true);
        Method vm = binder.getMethod("getViewModel"), oplusVm = binder.getMethod("getOplusViewModel");
        Method subscription = vm.getReturnType().getMethod("getSubscriptionId");
        Method flow = oplus.getMethod("getNetworkTypeText");
        Method value = loader.loadClass("kotlinx.coroutines.flow.StateFlow").getMethod("getValue");
        Method type = model.getMethod("getNetworkTypeText");
        Method prefix = nativeType.getMethod("getPrefix"), suffix = nativeType.getMethod("getSuffix");
        access = new Access() {
            public TextView text(Object owner) throws ReflectiveOperationException { return (TextView) text.invoke(owner); }
            public View image(Object owner) throws ReflectiveOperationException { return (View) image.get(owner); }
            public View host(Object owner) throws ReflectiveOperationException { return (View) host.invoke(owner); }
            public int subscription(Object owner) throws ReflectiveOperationException { return ((Number) subscription.invoke(vm.invoke(owner))).intValue(); }
            public Object model(Object owner) throws ReflectiveOperationException { return value.invoke(flow.invoke(oplusVm.invoke(owner))); }
            public Parts parts(Object nativeModel) throws ReflectiveOperationException {
                Object current = nativeModel == null ? null : type.invoke(nativeModel);
                return current == null ? Parts.UNKNOWN : new Parts((String) prefix.invoke(current), (String) suffix.invoke(current));
            }
        };
    }
    /** Controls are already configured. Refresh only saved native baselines; no flow/tree/I/O read. */
    void configure(FeatureOptions options, int subscription) {
        if (released) return;
        features = options; activeSubscription = subscription; refresh();
    }
    void activeSubscription(int subscription) {
        if (released) return;
        activeSubscription = subscription; refresh();
    }
    private void refresh() { for (Glyph glyph : new ArrayList<>(glyphs.values())) apply(glyph); }
    private Glyph row(Object owner) throws ReflectiveOperationException {
        if (released || access == null || owner == null) return null;
        Glyph existing = rows.get(owner); if (existing != null) return existing.owner.get() == owner ? existing : null;
        TextView text = access.text(owner); if (text == null) return null;
        Glyph glyph = glyphs.get(text);
        if (glyph == null) { glyph = new Glyph(text); glyphs.put(text, glyph); watch(text, glyph); }
        else { restoreVisibility(glyph); styler.beforeNative(text); }
        glyph.owner = new WeakReference<>(owner);
        glyph.subscription = access.subscription(owner); glyph.host = new WeakReference<>(access.host(owner));
        View image = access.image(owner);
        if (glyph.image.get() != image) {
            glyph.image = new WeakReference<>(image); glyph.imageKnown = image != null;
            if (image != null) glyph.imageVisibility = image.getVisibility();
        }
        rows.put(owner, glyph); return glyph;
    }
    private void watch(TextView view, Glyph glyph) {
        glyph.attachment = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View attached) { glyph.detached = false; if (!released) apply(glyph); }
            public void onViewDetachedFromWindow(View detached) {
                glyph.detached = true; restoreVisibility(glyph);
                styler.updated((TextView) detached, null, null, 0);
            }
        };
        view.addOnAttachStateChangeListener(glyph.attachment);
    }
    /** Real createBinding result, including bindings created before their first native initViews. */
    void bound(Object owner) {
        if (released) return;
        try {
            Glyph glyph = row(owner); if (glyph == null) return; bindings++;
            glyph.parts = access.parts(access.model(owner)); apply(glyph);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { failure(unavailable); }
    }
    NativeScope beforeNative(Object owner, boolean modelEvent, Object model) {
        if (released || access == null) return new NativeScope(null);
        Glyph entered = null;
        try {
            Parts changed = modelEvent ? access.parts(model) : Parts.UNKNOWN;
            Glyph glyph = row(owner); if (glyph == null) return new NativeScope(null);
            nativeEvents++;
            entered = glyph;
            if (glyph.depth++ == 0) {
                glyph.eventModelKnown = false; restoreVisibility(glyph);
                TextView text = glyph.text.get(); if (text != null) styler.beforeNative(text);
            }
            if (modelEvent) { glyph.parts = changed; glyph.eventModelKnown = true; }
            return new NativeScope(glyph);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            if (entered != null) entered.depth--;
            failure(unavailable); return new NativeScope(null);
        }
    }
    final class NativeScope implements AutoCloseable {
        private Glyph glyph;
        NativeScope(Glyph glyph) { this.glyph = glyph; }
        public void close() {
            Glyph current = glyph; glyph = null;
            if (current == null || --current.depth != 0 || released || access == null) return;
            try {
                if (!current.eventModelKnown) {
                    Object owner = current.owner.get();
                    current.parts = owner == null ? Parts.UNKNOWN : access.parts(access.model(owner));
                }
                TextView text = current.text.get();
                // The inner custom-label scope has already applied its own text/visibility.
                // It must never become this bridge's native baseline.
                if (text != null && !customLabel(text)) {
                    current.textVisibility = text.getVisibility();
                    View image = current.image.get();
                    if (image != null) { current.imageVisibility = image.getVisibility(); current.imageKnown = true; }
                }
                apply(current);
            } catch (ReflectiveOperationException | RuntimeException unavailable) { failure(unavailable); }
        }
    }
    private boolean customLabel(TextView view) {
        return features.enabled("label") || SingleNetworkLabelControls.managed(view);
    }
    private void apply(Glyph glyph) {
        TextView text = glyph.text.get();
        if (text == null || glyph.depth != 0 || released) return;
        applications++;
        if (customLabel(text)) {
            restoreVisibility(glyph); styler.updated(text, null, null, 0); return;
        }
        int part = glyph.parts.known() && activeSubscription >= 0 && glyph.subscription >= 0
                ? glyph.subscription == activeSubscription ? 1 : 2 : 0;
        if (glyph.detached || !text.isAttachedToWindow()) part = 0;
        styler.updated(text, glyph.parts.prefix, glyph.parts.suffix, part);
        boolean hide = !glyph.detached && text.isAttachedToWindow()
                && signal.hideSecondaryBadge(glyph.subscription, glyph.host.get());
        if (hide) {
            if (text.getVisibility() != View.GONE) text.setVisibility(View.GONE);
            View image = glyph.image.get(); if (image != null && image.getVisibility() != View.GONE) image.setVisibility(View.GONE);
            glyph.hidden = true;
        } else restoreVisibility(glyph);
    }
    private void restoreVisibility(Glyph glyph) {
        if (!glyph.hidden) return;
        TextView text = glyph.text.get(); View image = glyph.image.get();
        if (text == null || !SingleNetworkLabelControls.managed(text)) {
            if (text != null && text.getVisibility() != glyph.textVisibility) text.setVisibility(glyph.textVisibility);
            if (image != null && glyph.imageKnown && image.getVisibility() != glyph.imageVisibility) image.setVisibility(glyph.imageVisibility);
        }
        glyph.hidden = false;
    }
    private void failure(Throwable unavailable) {
        if (warned) return;
        warned = true;
        ModuleDiagnostics.error("native_badge", "Independent native badge binding unavailable", unavailable);
    }
    void releaseRuntime() {
        if (released) return;
        for (Glyph glyph : new ArrayList<>(glyphs.values())) {
            restoreVisibility(glyph); TextView text = glyph.text.get();
            if (text != null) {
                styler.updated(text, null, null, 0);
                if (glyph.attachment != null) text.removeOnAttachStateChangeListener(glyph.attachment);
            }
        }
        rows.clear(); glyphs.clear(); access = null; released = true;
    }
    String diagnosticSummary() {
        int typed = 0, hidden = 0;
        for (Glyph glyph : glyphs.values()) { if (glyph.parts.known()) typed++; if (glyph.hidden) hidden++; }
        return "Native badge independent rows " + glyphs.size() + ", typed " + typed + ", primary known "
                + (activeSubscription >= 0) + ", hidden " + hidden + ", bindings " + bindings
                + ", native events " + nativeEvents + ", applications " + applications;
    }
}
