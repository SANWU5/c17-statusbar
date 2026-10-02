// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** The two native square device cards expose separate glyphs; their surfaces and battery rings stay native. */
final class QsDeviceIconSize {
    static final String DEVICE = "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView";
    static final String ENTRANCE = "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView";
    static final String BATTERY = "com.oplus.deviceplugin.sdk.ui.view.progressbar.BatteryCircularProgressLayout";
    private final Map<View, State> cards = new WeakHashMap<>(), glyphs = new WeakHashMap<>(), holders = new WeakHashMap<>();
    private boolean enabled, released;
    private float dp = QsTileIconSize.DEFAULT_SIZE;
    private static final class State {
        final WeakReference<View> card, glyph, holder;
        int nativeWidth, nativeHeight, appliedWidth, appliedHeight;
        float nativeAlpha;
        boolean owned, alphaOwned, writingLayout, writingAlpha;
        State(View card, View glyph) {
            this.card = new WeakReference<>(card); this.glyph = new WeakReference<>(glyph);
            ViewParent parent = glyph.getParent(); this.holder = new WeakReference<>(parent instanceof View ? (View) parent : null);
            ViewGroup.LayoutParams params = glyph.getLayoutParams();
            nativeWidth = params.width; nativeHeight = params.height; nativeAlpha = glyph.getAlpha();
        }
        boolean valid() {
            View c = card.get(), g = glyph.get();
            return c != null && g != null && c.isAttachedToWindow() && g.isAttachedToWindow()
                    && holder.get() == g.getParent() && c.getRootView() == g.getRootView() && within(g, c);
        }
    }
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {
            State state = glyphs.get(view); if (state != null) apply(state);
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if (cards.containsKey(view)) detach(view);
            else { State state = glyphs.get(view); if (state != null) restore(state); }
        }
    };
    private final View.OnLayoutChangeListener layout = (view, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l == or - ol && b - t == ob - ot) return;
        State state = cards.get(view); if (state == null) state = holders.get(view);
        if (state != null) apply(state);
    };

    void configure(boolean on, float value) {
        enabled = on && !released && !ModuleLifecycle.removed(); dp = value;
        for (State state : new ArrayList<>(glyphs.values())) {
            if (state.card.get() == null) retire(state); else apply(state);
        }
    }

    /** Exact final native fields: SquareDevice.p.f=device_image; SquareEntrance.m=entrance icon. */
    void update(View card) {
        if (released || ModuleLifecycle.removed() || !supported(card)) return;
        Object candidate;
        if (QsTileAppearance.type(card, DEVICE)) {
            Object progress = QsTileAppearance.field(card, "p");
            candidate = QsTileAppearance.type(progress, BATTERY) ? QsTileAppearance.field(progress, "f") : null;
        } else candidate = QsTileAppearance.field(card, "m");
        View glyph = candidate instanceof ImageView ? (View) candidate : null;
        State previous = cards.get(card);
        if (previous != null && (previous.glyph.get() != glyph
                || glyph != null && previous.holder.get() != glyph.getParent())) detach(card);
        if (glyph == null || glyph.getLayoutParams() == null || !within(glyph, card)) {
            if (previous != null) detach(card); return;
        }
        State occupied = glyphs.get(glyph);
        if (occupied != null && occupied.card.get() != card) {
            View retired = occupied.card.get();
            if (retired != null) detach(retired); else retire(occupied);
        }
        State state = cards.get(card);
        if (state == null) {
            state = new State(card, glyph); cards.put(card, state); glyphs.put(glyph, state);
            card.addOnAttachStateChangeListener(attachment); card.addOnLayoutChangeListener(layout);
            glyph.addOnAttachStateChangeListener(attachment);
            View holder = state.holder.get();
            if (holder != null && holder != card) { holders.put(holder, state); holder.addOnLayoutChangeListener(layout); }
        }
        apply(state);
    }

    /** An inner native icon/theme callback does not require walking or scanning the whole QS page. */
    void updateGlyphParent(View view) {
        for (int depth = 0; view != null && depth < 8; depth++) {
            if (supported(view)) { update(view); return; }
            ViewParent parent = view.getParent(); view = parent instanceof View ? (View) parent : null;
        }
    }

    /** Observe exact native zero/match-constraint/new-size writes, including while disabled or safe. */
    ViewGroup.LayoutParams nativeLayout(View glyph, ViewGroup.LayoutParams params) {
        State state = glyphs.get(glyph);
        if (state == null || params == null || state.writingLayout || released || ModuleLifecycle.removed()) return params;
        if (!state.valid()) {
            // The caller may hold a separate copy of our previous dimensions. Restore
            // those owned axes in the incoming pair too, before native proceed reapplies it.
            if (state.owned && params.width == state.appliedWidth) params.width = state.nativeWidth;
            if (state.owned && params.height == state.appliedHeight) params.height = state.nativeHeight;
            View card = state.card.get(); if (card != null) detach(card); else restore(state);
            return params;
        }
        // Native ConstraintLayout updates can copy our pair and change only margins/anchors.
        // Preserve each original axis until that axis actually carries a new native dimension.
        if (!state.owned || params.width != state.appliedWidth) state.nativeWidth = params.width;
        if (!state.owned || params.height != state.appliedHeight) state.nativeHeight = params.height;
        state.owned = false;
        int pixels = extent(state);
        if (pixels >= 0) {
            // ConstraintLayout interprets a 0 dimension as MATCH_CONSTRAINT, not a zero glyph.
            int size = Math.max(1, pixels); params.width = params.height = size;
            state.appliedWidth = state.appliedHeight = size; state.owned = true;
        }
        applyAlpha(state, pixels == 0);
        return params;
    }

    float nativeAlpha(View glyph, float alpha) {
        State state = glyphs.get(glyph);
        if (state == null || state.writingAlpha || released || ModuleLifecycle.removed()) return alpha;
        state.nativeAlpha = alpha;
        return state.alphaOwned && enabled && state.valid() ? 0f : alpha;
    }

    private int extent(State state) {
        if (!enabled || released || ModuleLifecycle.removed() || !state.valid()) return -1;
        View card = state.card.get(), glyph = state.glyph.get();
        float density = glyph.getResources().getDisplayMetrics().density;
        if (!Float.isFinite(density) || density <= 0f) return -1;
        int width = card.getWidth() - card.getPaddingLeft() - card.getPaddingRight();
        int height = card.getHeight() - card.getPaddingTop() - card.getPaddingBottom();
        ViewParent parent = glyph.getParent();
        if (parent instanceof View) {
            View holder = (View) parent;
            int nativeWidth = holder.getWidth() - holder.getPaddingLeft() - holder.getPaddingRight();
            int nativeHeight = holder.getHeight() - holder.getPaddingTop() - holder.getPaddingBottom();
            if (nativeWidth <= 0 || nativeHeight <= 0) return -1;
            width = Math.min(width, nativeWidth); height = Math.min(height, nativeHeight);
        }
        if (width <= 0 || height <= 0) return -1;
        return NumericPolicy.layoutPixels(Math.min((double) dp * density, Math.min(width, height)));
    }

    private void apply(State state) {
        if (state.writingLayout) return;
        View glyph = state.glyph.get();
        ViewGroup.LayoutParams params = glyph == null ? null : glyph.getLayoutParams();
        if (params == null) { restoreAlpha(state); return; }
        int pixels = extent(state);
        if (pixels < 0) { restore(state); return; }
        if (!state.owned || params.width != state.appliedWidth) state.nativeWidth = params.width;
        if (!state.owned || params.height != state.appliedHeight) state.nativeHeight = params.height;
        int size = Math.max(1, pixels);
        state.appliedWidth = state.appliedHeight = size; state.owned = true;
        applyAlpha(state, pixels == 0);
        writeLayout(state, glyph, params, size, size);
    }

    private void writeLayout(State state, View glyph, ViewGroup.LayoutParams params, int width, int height) {
        if (params.width == width && params.height == height) return;
        state.writingLayout = true;
        try { params.width = width; params.height = height; glyph.setLayoutParams(params); }
        finally { state.writingLayout = false; }
    }

    private void applyAlpha(State state, boolean zero) {
        if (!zero) { restoreAlpha(state); return; }
        View glyph = state.glyph.get(); if (glyph == null) return;
        if (!state.alphaOwned || glyph.getAlpha() != 0f) state.nativeAlpha = glyph.getAlpha();
        state.alphaOwned = true; writeAlpha(state, glyph, 0f);
    }
    private void writeAlpha(State state, View glyph, float alpha) {
        if (glyph.getAlpha() == alpha) return;
        state.writingAlpha = true;
        try { glyph.setAlpha(alpha); } finally { state.writingAlpha = false; }
    }
    private void restoreAlpha(State state) {
        View glyph = state.glyph.get();
        if (glyph != null && state.alphaOwned) {
            if (glyph.getAlpha() != 0f) state.nativeAlpha = glyph.getAlpha();
            else writeAlpha(state, glyph, state.nativeAlpha);
        }
        state.alphaOwned = false;
    }
    private void restore(State state) {
        View glyph = state.glyph.get();
        ViewGroup.LayoutParams params = glyph == null ? null : glyph.getLayoutParams();
        if (params != null && state.owned) {
            if (params.width != state.appliedWidth) state.nativeWidth = params.width;
            if (params.height != state.appliedHeight) state.nativeHeight = params.height;
            writeLayout(state, glyph, params, state.nativeWidth, state.nativeHeight);
        }
        state.owned = false; restoreAlpha(state);
    }
    void detach(View card) {
        State state = cards.remove(card); if (state == null) return;
        retire(state);
    }
    private void retire(State state) {
        restore(state);
        View card = state.card.get();
        if (card != null) {
            if (cards.get(card) == state) cards.remove(card);
            card.removeOnAttachStateChangeListener(attachment); card.removeOnLayoutChangeListener(layout);
        }
        View glyph = state.glyph.get();
        if (glyph != null) {
            if (glyphs.get(glyph) == state) glyphs.remove(glyph);
            glyph.removeOnAttachStateChangeListener(attachment);
        }
        View holder = state.holder.get();
        if (holder != null && holder != card) {
            if (holders.get(holder) == state) holders.remove(holder);
            holder.removeOnLayoutChangeListener(layout);
        }
        state.card.clear(); state.glyph.clear(); state.holder.clear();
    }
    void release() {
        enabled = false;
        for (State state : new ArrayList<>(glyphs.values())) retire(state);
        cards.clear(); glyphs.clear(); holders.clear(); if (ModuleLifecycle.removed()) released = true;
    }
    static boolean supported(View view) {
        return QsTileAppearance.type(view, DEVICE) || QsTileAppearance.type(view, ENTRANCE);
    }
    private static boolean within(View glyph, View card) {
        for (int depth = 0; glyph != null && depth < 8; depth++) {
            if (glyph == card) return true;
            ViewParent parent = glyph.getParent(); glyph = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
