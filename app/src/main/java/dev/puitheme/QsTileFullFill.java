// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Gives active 2x1 tiles a fresh native tile fill inside their original shaped wrapper. */
public final class QsTileFullFill {
    public static final String ENABLED = "qs_tile_2x1_full_fill_enabled";
    public static final String TILE = "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne";
    public static final String LABEL_MANAGER = "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSHighlightTileViewLabelColorManager";
    public static final Map<String, Boolean> BOOLEANS;
    private static final String WRAPPER = "com.oplus.systemui.qs.base.res.drawable.TileDrawableWrapper";
    private static final String TAGS = "com.oplusos.systemui.common.util.QSBlurConfigProvider";
    static {
        Map<String, Boolean> flags = new LinkedHashMap<>(); flags.put(ENABLED, false);
        BOOLEANS = Collections.unmodifiableMap(flags);
    }
    /** The seam also checks the real pool/build/wrapper calls without sharing a live icon drawable. */
    interface Factory {
        Object builder(View tile, boolean theme) throws ReflectiveOperationException;
        Drawable build(Object builder, View body, boolean theme) throws ReflectiveOperationException;
    }
    private static final class NativeFactory implements Factory {
        public Object builder(View tile, boolean theme) throws ReflectiveOperationException {
            Class<?> poolType = tile.getClass().getClassLoader().loadClass("com.oplus.systemui.qs.base.res.SepQSResPool");
            Object pool = poolType.getField("INSTANCE").get(null);
            Object flow = QsTileAppearance.invoke(pool, theme ? "getTileThemeDrawable" : "getTileViewDrawable");
            return QsTileAppearance.invoke(flow, "getValue");
        }
        public Drawable build(Object builder, View body, boolean theme) throws ReflectiveOperationException {
            // Native MixColor/Gradient builders return the drawable cached in the body's tag,
            // which is the 2x1 wrapper's own fill. Hide both caches only for this build.
            Class<?> tags = builder.getClass().getClassLoader().loadClass(TAGS);
            Method getBlur = tags.getMethod("getBlurDrawableTag", View.class);
            Method setBlur = tags.getMethod("setBlurDrawableTag", View.class, Drawable.class);
            Method getGradient = tags.getMethod("getGradientDrawableTag", View.class);
            Method setGradient = tags.getMethod("setGradientDrawableTag", View.class, Drawable.class);
            Object blur = getBlur.invoke(null, body), gradient = getGradient.invoke(null, body);
            setBlur.invoke(null, body, null); setGradient.invoke(null, body, null);
            try {
                // Native OneXOne uses mask 6 for normal (true), mask 4 for theme (false).
                Object value = QsTileAppearance.invoke(builder, "build", body, !theme, null);
                return value instanceof Drawable ? (Drawable) value : null;
            } finally { setBlur.invoke(null, body, blur); setGradient.invoke(null, body, gradient); }
        }
    }
    private static final class Fill {
        final WeakReference<Drawable> wrapper;
        final Drawable original, replacement;
        final Object builder;
        final boolean card, night;
        Fill(Drawable wrapper, Drawable original, Drawable replacement, Object builder,boolean card,boolean night) {
            this.wrapper = new WeakReference<>(wrapper); this.original = original;
            this.replacement = replacement; this.builder = builder;
            this.card=card;this.night=night;
        }
    }
    private static final class TextColor {
        final WeakReference<TextView> view;
        ColorStateList original, applied;
        TextColor(TextView view) { this.view = new WeakReference<>(view); original = view.getTextColors(); }
    }
    private static final class State {
        final List<Fill> fills = new ArrayList<>();
        final List<TextColor> text = new ArrayList<>();
        WeakReference<View> circle;
        int circleVisibility;
        boolean busy, owned;
        volatile boolean blocked, restoring;
        volatile Object nativeColors;
        String diagnostic;
    }
    private final QsTileAppearance appearance;
    private final Factory factory;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<View, State> tiles = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, WeakReference<View>> managers = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Boolean> pending = new WeakHashMap<>();
    /** Current replacement fills and their owning tiles; drawn as one solid colour. */
    private final Map<Drawable, WeakReference<View>> pureFills = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, Integer> peakAlpha = Collections.synchronizedMap(new WeakHashMap<>());
    private final Paint purePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pureBounds = new RectF();
    private volatile boolean enabled, released, controlGlass;
    private boolean failureLogged;
    private int diagnosticCount;

    public QsTileFullFill(QsTileAppearance appearance) { this(appearance, new NativeFactory()); }
    QsTileFullFill(QsTileAppearance appearance, Factory factory) { this.appearance = appearance; this.factory = factory; }
    public void configure(Bundle settings) {
        enabled = !released && settings != null && settings.getBoolean(ENABLED, false);
        boolean glass = CustomPluginMaterialIsolation.controlOwned(settings);
        onMain(() -> {
            boolean materialChanged = controlGlass != glass;
            controlGlass = glass;
            failureLogged = false;
            for (View tile : snapshot()) {
                refreshTile(tile);
                if (!materialChanged) continue;
                State state = tiles.get(tile);
                if (state == null || !state.owned) continue;
                // The same wrapper can switch draw paths without replacing its child.
                for (Fill fill : state.fills) {
                    Drawable wrapper = fill.wrapper.get();
                    if (wrapper != null) wrapper.invalidateSelf();
                }
                tile.invalidate();
            }
        });
    }
    private List<View> snapshot() { synchronized (tiles) { return new ArrayList<>(tiles.keySet()); } }
    private void onMain(Runnable action) { if (Looper.myLooper() == Looper.getMainLooper()) action.run(); else main.post(action); }
    private boolean dispatch(View tile, boolean detach) {
        if (Looper.myLooper() == Looper.getMainLooper()) return false;
        boolean post;
        synchronized (pending) { post = !pending.containsKey(tile); pending.put(tile, detach); }
        if (post) {
            WeakReference<View> reference = new WeakReference<>(tile);
            main.post(() -> {
                View owner = reference.get(); if (owner == null) return;
                Boolean remove; synchronized (pending) { remove = pending.remove(owner); }
                if (remove == null) return;
                if (remove || !owner.isAttachedToWindow()) detach(owner); else refreshTile(owner);
            });
        }
        return true;
    }
    private boolean eligible(View tile, Object tileState) {
        Object value = QsTileAppearance.field(tileState, "state");
        return circleEligible(tile) && value instanceof Number && ((Number) value).intValue() == 2;
    }
    /** Inactive and unavailable 2x1 tiles keep their native fill and labels but lose the circle too. */
    private boolean circleEligible(View tile) {
        return enabled && !released && QsTileAppearance.type(tile, TILE);
    }
    /** Measuring an already owned tile only changes native bounds/providers, never the fill source. */
    public void measuredTile(View tile) {
        if (!enabled || released || !QsTileAppearance.type(tile, TILE)) return;
        State state = tiles.get(tile);
        if (state == null || !state.owned) refreshTile(tile);
    }
    /** Call after native state, theme, drawable, attach and layout updates; never from a draw loop. */
    public void refreshTile(View tile) {
        if (!QsTileAppearance.type(tile, TILE) || dispatch(tile, false)) return;
        State state = tiles.get(tile);
        if (state == null) { state = new State(); tiles.put(tile, state); }
        if (state.busy) return;
        state.busy = true;
        try {
            Object manager = QsTileAppearance.call(tile, "getLabelResourceManager");
            if (manager != null) managers.put(manager, new WeakReference<>(tile));
            if (!eligible(tile, QsTileAppearance.call(tile, "getTileState"))) {
                boolean idle = !state.blocked && circleEligible(tile);
                restore(tile, state, idle);
                if (idle) hideCircle(tile, state);
                return;
            }
            Object object = QsTileAppearance.call(tile, "getBg");
            if (!(object instanceof View)) { restore(tile, state); return; }
            View body = (View) object;
            if (body.getWidth() <= 0 || body.getHeight() <= 0) { restore(tile, state); return; }
            List<Fill> next = new ArrayList<>();
            boolean card=appearance.fullFillStyle(tile)!=null;
            boolean night=(tile.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    ==android.content.res.Configuration.UI_MODE_NIGHT_YES;
            for (boolean theme : new boolean[]{false, true}) {
                Object candidate = QsTileAppearance.call(tile, theme ? "getThemeDrawable" : "getBgDrawable");
                if (!(candidate instanceof Drawable) || !QsTileAppearance.type(candidate, WRAPPER)) {
                    if (!theme) throw new IllegalStateException("Native tile wrapper unavailable");
                    continue;
                }
                Drawable wrapper = (Drawable) candidate;
                Object child = QsTileAppearance.call(wrapper, "getDrawable");
                if (child == null && theme) continue;
                if (!(child instanceof Drawable)) throw new IllegalStateException("Native tile fill unavailable");
                Object builder = factory.builder(tile, theme);
                Fill old = find(state, wrapper);
                if (old != null && child == old.replacement && old.builder == builder&&old.card==card&&old.night==night) { next.add(old); continue; }
                Drawable nativeChild = old != null && child == old.replacement ? old.original : (Drawable) child;
                Drawable replacement = card?QsTileCardFill.create(tile,body,wrapper):factory.build(builder, body, theme);
                if (replacement == null || replacement == child || replacement == nativeChild)
                    throw new IllegalStateException("Native builder did not create an independent tile fill");
                next.add(new Fill(wrapper, nativeChild, replacement, builder,card,night));
            }
            // Own before setters: native wrapper setters can partially mutate and then throw.
            List<Fill> previous = new ArrayList<>(state.fills);
            boolean changed = !state.owned || !previous.equals(next);
            state.fills.clear(); state.fills.addAll(next);
            for (Fill old : previous) if (find(state, old.wrapper.get()) != old) restoreFill(old);
            for (Fill fill : next) {
                Drawable wrapper = fill.wrapper.get();
                if (wrapper == null) throw new IllegalStateException("Native tile wrapper detached");
                pureFills.put(fill.replacement, new WeakReference<>(tile));
                if (QsTileAppearance.call(wrapper, "getDrawable") != fill.replacement)
                    QsTileAppearance.invoke(wrapper, "setDrawable", fill.replacement);
                fill.replacement.setState(wrapper.getState());
            }
            state.blocked = false; state.owned = true;
            hideCircle(tile, state);
            nativeLabels(tile, state, false);
            applyText(tile, state);
            traceFill(tile, state);
            if (changed) { appearance.fullFillChanged(tile, true); tile.invalidate(); }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            state.blocked = true; restore(tile, state);
            if (!failureLogged && ModuleDiagnostics.enabled()) {
                failureLogged = true;
                ModuleDiagnostics.error("qs_style", "qs_full_fill native factory/wrapper unavailable; native appearance retained", unavailable);
            }
        } finally { state.busy = false; }
    }
    private static Fill find(State state, Drawable wrapper) {
        if (wrapper != null) for (Fill fill : state.fills) if (fill.wrapper.get() == wrapper) return fill;
        return null;
    }
    /**
     * Device code161: the native "mix colour" fill is translucent glass, not the solid card the
     * connected earbuds use. The replacement paints one opaque colour inside the native shape;
     * native activation fades stay, scaled to this fill's own peak alpha.
     */
    public boolean drawPure(Drawable drawable, Canvas canvas) {
        // Keep the native full-width drawable in the glass plugin's drawing path.
        if (controlGlass) return false;
        WeakReference<View> owner = drawable == null ? null : pureFills.get(drawable);
        View tile = owner == null ? null : owner.get();
        State state = tile == null ? null : tiles.get(tile);
        if (state == null || !state.owned || canvas == null) return false;
        android.graphics.Rect bounds = drawable.getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return false;
        int alpha = drawable.getAlpha();
        Integer peak = peakAlpha.get(drawable);
        if (peak == null || alpha > peak) { peak = alpha; peakAlpha.put(drawable, alpha); }
        int shown = peak <= 0 ? 0 : Math.min(255, Math.round(alpha * 255f / peak));
        if (shown <= 0) return true;
        // Device code162: with a tile colour the 2x1 fill must look exactly like the other active
        // tiles, so it is their native glass, coloured by the same appearance code (not a flat paint).
        // Without a tile colour it stays the one opaque white the author accepted.
        if (appearance.fullFillStyle(tile) != null) return false;
        purePaint.setShader(null);
        purePaint.setColor(0xffffffff);
        purePaint.setAlpha(shown);
        float radius = Math.min(bounds.height(), bounds.width()) / 2f;
        Drawable wrapper = null;
        for (Fill fill : state.fills) if (fill.replacement == drawable) { wrapper = fill.wrapper.get(); break; }
        Object provider = wrapper == null ? null : QsTileAppearance.call(wrapper, "getPathProvider");
        Object nativeRadius = provider == null ? null : QsTileAppearance.call(provider, "getCornerRadius", wrapper);
        if (nativeRadius instanceof Number && ((Number) nativeRadius).floatValue() >= 0f)
            radius = Math.min(radius, ((Number) nativeRadius).floatValue());
        pureBounds.set(bounds);
        canvas.drawRoundRect(pureBounds, radius, radius, purePaint);
        return true;
    }
    private static void restoreFill(Fill fill) {
        Drawable wrapper = fill.wrapper.get();
        if (wrapper == null || QsTileAppearance.call(wrapper, "getDrawable") != fill.replacement) return;
        try {
            QsTileAppearance.invoke(wrapper, "setDrawable", fill.original);
            fill.original.setState(wrapper.getState());
            wrapper.invalidateSelf();
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    private static void restoreCircle(State state) {
        View circle = state.circle == null ? null : state.circle.get();
        if (circle != null && circle.getVisibility() == View.INVISIBLE)
            circle.setVisibility(state.circleVisibility);
        state.circle = null;
    }
    private static void hideCircle(View tile, State state) {
        Object icon = QsTileAppearance.call(tile, "getIconView");
        Object candidate = QsTileAppearance.field(icon, "iconBgView");
        if (!(candidate instanceof View)) throw new IllegalStateException("Native icon background view unavailable");
        View circle = (View) candidate;
        View old = state.circle == null ? null : state.circle.get();
        if (old != circle) {
            restoreCircle(state);
            state.circle = new WeakReference<>(circle);
            state.circleVisibility = circle.getVisibility();
        } else if (circle.getVisibility() != View.INVISIBLE) state.circleVisibility = circle.getVisibility();
        // Native OplusQSIconView puts both oval normal/theme layers on this separate
        // ImageView. Hide the whole view, including its foreground, without touching
        // alpha or drawables: native theme/press updates must survive restoration.
        if (circle.getVisibility() == View.VISIBLE) circle.setVisibility(View.INVISIBLE);
    }
    private static List<TextView> labelViews(View tile) {
        List<TextView> views = new ArrayList<>();
        for (String field : new String[]{"labelTitle", "labelDesc"}) {
            Object label = QsTileAppearance.field(tile, field);
            if (label instanceof TextView) views.add((TextView) label);
            else if (label instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) label;
                for (int index = 0; index < group.getChildCount(); index++)
                    if (group.getChildAt(index) instanceof TextView) views.add((TextView) group.getChildAt(index));
            }
        }
        return views;
    }
    private void applyText(View tile, State state) {
        for (TextView view : labelViews(tile)) {
            TextColor record = null;
            for (TextColor existing : state.text) if (existing.view.get() == view) { record = existing; break; }
            if (record == null) { record = new TextColor(view); state.text.add(record); }
            ColorStateList current = view.getTextColors();
            if (current != record.applied) record.original = current;
            ColorStateList desired = blackLabel(record.original);
            if (current.getDefaultColor() != desired.getDefaultColor() || current.isStateful() != desired.isStateful()) {
                record.applied = desired; view.setTextColor(desired);
            } else record.applied = current;
        }
    }
    /** Preserve native label transitions; alter only the exact 2x1 manager's active target pair. */
    public Object labelColors(Object manager, Object tileState, Object nativePair) {
        WeakReference<View> reference = managers.get(manager);
        View tile = reference == null ? null : reference.get();
        State state = tile == null ? null : tiles.get(tile);
        if (state == null) return nativePair;
        state.nativeColors = nativePair;
        if (state.restoring || state.blocked || !state.owned || !eligible(tile, tileState) || nativePair == null) return nativePair;
        Object first = QsTileAppearance.call(nativePair, "getFirst"), second = QsTileAppearance.call(nativePair, "getSecond");
        if (!(first instanceof ColorStateList) || !(second instanceof ColorStateList)) return nativePair;
        try {
            return nativePair.getClass().getConstructor(Object.class, Object.class).newInstance(
                    blackLabel((ColorStateList) first), blackLabel((ColorStateList) second));
        } catch (ReflectiveOperationException | RuntimeException unknownPair) { return nativePair; }
    }
    /** Match the native device card's black RGB, preserving each native label's alpha. */
    private static ColorStateList blackLabel(ColorStateList original) {
        return original == null ? null : ColorStateList.valueOf(original.getDefaultColor() & 0xff000000);
    }
    private void traceFill(View tile, State state) {
        if (!ModuleDiagnostics.enabled() || diagnosticCount >= 16) return;
        StringBuilder detail = new StringBuilder("qs_full_fill labels ").append(labelViews(tile).size());
        detail.append(" rgb black circle hidden");
        for (Fill fill : state.fills) {
            Drawable wrapper = fill.wrapper.get();
            detail.append(" fill ").append(fill.replacement.getClass().getSimpleName())
                    .append(" alpha ").append(wrapper == null ? -1 : wrapper.getAlpha());
        }
        String value = detail.toString();
        if (!value.equals(state.diagnostic)) {
            state.diagnostic = value; diagnosticCount++;
            ModuleDiagnostics.info("qs_style", value);
        }
    }
    private static void nativeLabels(View tile, State state, boolean restoring) {
        state.restoring = restoring;
        try {
            QsTileAppearance.invoke(tile, "refreshTileInnerLabelColor", QsTileAppearance.call(tile, "getTileState"));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            if (restoring && state.nativeColors != null) QsTileAppearance.call(tile, "handleLabelColorChanged", state.nativeColors);
        } finally { state.restoring = false; }
    }
    private void restore(View tile, State state) { restore(tile, state, false); }
    private void restore(View tile, State state, boolean keepCircle) {
        boolean hadChanges = state.owned || !state.fills.isEmpty() || !state.text.isEmpty();
        for (Fill fill : state.fills) restoreFill(fill);
        state.fills.clear();
        if (!keepCircle) restoreCircle(state);
        for (TextColor color : state.text) {
            TextView view = color.view.get();
            if (view != null && view.getTextColors() == color.applied && color.original != null) view.setTextColor(color.original);
        }
        state.text.clear();
        if (state.owned) { state.owned = false; appearance.fullFillChanged(tile, false); tile.invalidate(); }
        if (hadChanges) nativeLabels(tile, state, true);
    }
    public void detach(View tile) {
        if (tile == null || dispatch(tile, true)) return;
        State state = tiles.remove(tile);
        if (state != null) restore(tile, state);
        synchronized (managers) { managers.entrySet().removeIf(entry -> entry.getValue().get() == null || entry.getValue().get() == tile); }
    }
    interface NativeAction { Object run() throws Throwable; }
    /** Native recycle can call a hooked drag setter and acquire the fill again. */
    public Object aroundDetach(View tile, NativeAction action) throws Throwable {
        detach(tile);
        try { return action.run(); }
        finally { detach(tile); }
    }
    public void release() {
        released = true; enabled = false;
        onMain(() -> { for (View tile : snapshot()) detach(tile); managers.clear(); synchronized (pending) { pending.clear(); } });
    }
}
