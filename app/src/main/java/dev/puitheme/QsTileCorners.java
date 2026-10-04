// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
// Technical reference: MCGA TwoXOneTileHook.kt, Copyright (C) 2026
// Zhuangzhi Meng (Gustate XiaoMeng), licensed GPL-3.0-or-later.

package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Owns only the native path provider of individual ColorOS 17 quick-settings tile drawables. */
public final class QsTileCorners {
    public static final String MASTER = "qs_tile_corners_enabled";
    public static final String RADIUS = "qs_tile_corner_radius";
    public static final String LEGACY_RADIUS = "qs_tile_2x1_corner_radius";
    static final String MIGRATED = "qs_tile_corners_all_tiles_migrated";
    public static final float DEFAULT_RADIUS = 24f;
    public static final float MAX_RADIUS = 30f;
    public static final String TILE_CLASS = "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView";
    public static final String[] TILE_SUBCLASSES = {
            "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne",
            "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne",
            "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXTwo",
            "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableThreeStageView"
    };
    private static final String LAYER_CLASS = "com.oplus.systemui.qs.base.res.drawable.TileLayerDrawable";
    private static final String OUTLINE_CLASS = "com.oplusos.systemui.common.outline.CornerOutlineProvider";
    private static final String FACTORY_CLASS = "com.oplus.systemui.qs.base.res.util.QSConstant";
    private static final String DEFORM_CLASS = "com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed.TileDeformOutlineProvider";
    public static final String FIXED_RADIUS_CLASS = "com.oplus.systemui.plugins.qs.customize.view.animation.deform.tile.fixed.TileFixedRadiusShareTransitionProperty";
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    static {
        Map<String, Boolean> flags = new LinkedHashMap<>(); flags.put(MASTER, false);
        Map<String, Float> numbers = new LinkedHashMap<>(); numbers.put(RADIUS, DEFAULT_RADIUS);
        BOOLEANS = Collections.unmodifiableMap(flags); NUMBERS = Collections.unmodifiableMap(numbers);
    }

    private static final class Policy {
        final boolean enabled; final float dp;
        Policy(boolean enabled, float dp) { this.enabled = enabled; this.dp = dp; }
    }
    private static final class Owned {
        // Drawable callbacks can reference their View: never retain the drawable strongly here.
        WeakReference<Drawable> drawable;
        Object nativeProvider, customProvider, cachedProvider, weight;
        Object nativeBlock, customBlock;
        float cachedPixels = -1f, radius;
        float blockBaseRadius = Float.NaN;
        Object blockBaseWeight;
        boolean restorePending, writingBlock;
        Method factory;
        WeakReference<View> body = new WeakReference<>(null);
        View.OnLayoutChangeListener layout;
    }
    private final Object lock = new Object();
    private final Map<View, Owned> tiles = new WeakHashMap<>();
    private final Map<Drawable, WeakReference<View>> owners = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Policy policy = new Policy(false, DEFAULT_RADIUS);
    private volatile boolean fixedTransitionAvailable;
    private boolean loggedError, loggedApplied;

    public static boolean isTile(View view) { return QsTileAppearance.type(view, TILE_CLASS); }
    public static float radiusDp(Object value) {
        return Math.max(0f, Math.min(MAX_RADIUS, QsTileAppearance.number(value, DEFAULT_RADIUS)));
    }
    public static float radius(Map<String, ?> values) {
        Object value = values == null ? null : values.get(RADIUS);
        if (value == null && values != null) value = values.get(LEGACY_RADIUS);
        return radiusDp(value);
    }
    /** Missing/malformed switches stay off. Migration never changes an explicit saved choice. */
    public static boolean enabled(Map<String, ?> values) {
        return values != null && Boolean.TRUE.equals(values.get(MASTER));
    }
    static void migrate(SharedPreferences preferences) {
        Map<String, ?> values = preferences.getAll();
        if (Boolean.TRUE.equals(values.get(MIGRATED))) return;
        SharedPreferences.Editor editor = preferences.edit().putBoolean(MIGRATED, true);
        if (!values.containsKey(RADIUS) && values.containsKey(LEGACY_RADIUS))
            editor.putFloat(RADIUS, radius(values));
        editor.apply();
    }
    /** The requested radius is converted using the tile's density and bounded by its real shape. */
    static float radiusPixels(float dp, float density, int width, int height) {
        if (!Float.isFinite(density) || density <= 0f || width <= 0 || height <= 0) return -1f;
        return Math.min(radiusDp(dp) * density, Math.min(width, height) / 2f);
    }

    public void configure(Bundle settings) {
        boolean enabled = settings != null && Boolean.TRUE.equals(settings.get(MASTER));
        Object radius = settings == null ? null : settings.get(RADIUS);
        if (radius == null && settings != null) radius = settings.get(LEGACY_RADIUS);
        Policy next = new Policy(enabled && !SafetyMode.enabled(settings), radiusDp(radius));
        Policy previous = policy; policy = next;
        if (previous.enabled == next.enabled && previous.dp == next.dp) return;
        runOnMain(() -> {
            synchronized (lock) {
                loggedError = false; loggedApplied = false;
                for (View view : new ArrayList<>(tiles.keySet())) if (view != null) refresh(view);
            }
        });
    }
    /** Enable cloning only after the concrete OEM spring callback hook was installed.
     * A ROM with only the static drawable API retains its animated block provider. */
    public void setFixedTransitionAvailable(boolean available) {
        if (fixedTransitionAvailable == available) return;
        fixedTransitionAvailable = available;
        runOnMain(() -> {
            synchronized (lock) {
                for (View view : new ArrayList<>(tiles.keySet())) if (view != null) refresh(view);
            }
        });
    }

    /** Run after the native method once; its latest provider remains the restoration source. */
    public void onNativeUpdate(View view) {
        if (!isTile(view)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> target = new WeakReference<>(view);
            main.post(() -> { View current = target.get(); if (current != null) onNativeUpdate(current); });
            return;
        }
        synchronized (lock) { refresh(view); }
    }

    /** Release ownership before a recycled tile is reused, or after its native detach. */
    public void detach(View view) {
        if (!isTile(view)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> target = new WeakReference<>(view);
            main.post(() -> { View current = target.get(); if (current != null) detach(current); });
            return;
        }
        synchronized (lock) {
            Owned state = tiles.get(view);
            if (state == null) return;
            if (restore(state)) {
                Drawable drawable = state.drawable == null ? null : state.drawable.get();
                if (drawable != null) owners.remove(drawable);
                stopObserving(view, state); tiles.remove(view);
            }
            invalidate(view);
        }
    }

    public Object cornerRadius(View view, Object nativeResult) {
        synchronized (lock) {
            Owned state = owned(view);
            return state == null ? nativeResult : state.radius;
        }
    }
    public Object cornerWeight(View view, Object nativeResult) {
        synchronized (lock) {
            Owned state = owned(view);
            // A null native smooth weight is meaningful and must not fall back to the old weight.
            return state == null ? nativeResult : state.weight;
        }
    }
    /** The editable stroke follows the installed provider during the ROM's size transition.
     * getCornerRadius remains the configured endpoint used to capture native spring values. */
    public Object viewRadius(View view, Object nativeResult) {
        synchronized (lock) {
            Owned state = owned(view);
            if (state == null) return nativeResult;
            Drawable drawable = state.drawable == null ? null : state.drawable.get();
            Object provider = state.customBlock == null ? state.customProvider : state.customBlock;
            Object radius = QsTileAppearance.call(provider, "getCornerRadius", drawable);
            return validRadius(radius) ? ((Number) radius).floatValue() : nativeResult;
        }
    }
    private Owned owned(View view) {
        if (!policy.enabled || !isTile(view)) return null;
        Owned state = tiles.get(view);
        if (state == null || state.customProvider == null || state.restorePending) return null;
        Drawable drawable = state.drawable == null ? null : state.drawable.get();
        return drawable != null && drawable == QsTileAppearance.call(view, "getTransitionDrawable")
                && baseProvider(drawable) == state.customProvider
                && (blockProvider(drawable) == null || blockProvider(drawable) == state.customBlock) ? state : null;
    }

    private void refresh(View view) {
        Owned state = tiles.get(view);
        if (state == null) { state = new Owned(); tiles.put(view, state); }
        observe(view, state);
        Object candidate = QsTileAppearance.call(view, "getTransitionDrawable");
        if (!(candidate instanceof Drawable) || !QsTileAppearance.type(candidate, LAYER_CLASS)) {
            if (state != null) { restore(state); invalidate(view); }
            return;
        }
        Drawable drawable = (Drawable) candidate;
        Drawable previous = state.drawable == null ? null : state.drawable.get();
        if (previous != drawable) {
            if (!restore(state)) return;
            state.drawable = new WeakReference<>(drawable);
            state.nativeProvider = null; state.cachedProvider = null; state.cachedPixels = -1f;
            if (previous != null) owners.remove(previous);
        }
        owners.put(drawable, new WeakReference<>(view));
        // getPathProvider() returns a temporary block/deform provider during native animations.
        // Capture the underlying static provider, never commit a transient animation as native state.
        Object current = baseProvider(drawable);
        if (current == null || !QsTileAppearance.type(current, OUTLINE_CLASS)) return;
        if (current != state.customProvider && !(state.restorePending && current == state.nativeProvider)) {
            state.nativeProvider = current; state.customProvider = null;
            state.cachedProvider = null; state.restorePending = false;
        }
        if (!policy.enabled || !view.isAttachedToWindow()) {
            restore(state); invalidate(view); return;
        }
        // Finish a failed restoration before creating another custom provider.
        if (state.restorePending && !restore(state)) return;
        float pixels = pixels(view, drawable, policy.dp);
        if (pixels < 0f || state.nativeProvider == null) return;
        try {
            if (state.cachedProvider == null || state.cachedPixels != pixels) {
                if (state.factory == null) {
                    Class<?> factory = Class.forName(FACTORY_CLASS, false, view.getClass().getClassLoader());
                    state.factory = factory.getDeclaredMethod("getSmoothRoundRectOutlineProvider", Context.class, float.class);
                    state.factory.setAccessible(true);
                }
                Object provider = continuousProvider(state.factory, view.getContext(), pixels, drawable);
                if (!QsTileAppearance.type(provider, OUTLINE_CLASS)) throw new IllegalStateException("Unknown native outline provider");
                state.cachedProvider = provider; state.cachedPixels = pixels;
            }
            if (current == state.cachedProvider && !state.restorePending) {
                syncBlock(drawable, state); return;
            }
            Object radius = QsTileAppearance.invoke(state.cachedProvider, "getCornerRadius", drawable);
            Object weight = QsTileAppearance.invoke(state.cachedProvider, "getCornerWeight", drawable);
            if (!(radius instanceof Number) || !Float.isFinite(((Number) radius).floatValue())
                    || ((Number) radius).floatValue() < 0f
                    || weight != null && (!(weight instanceof Float) || !Float.isFinite((Float) weight)))
                throw new IllegalStateException("Invalid native corner geometry");
            // Record ownership before calling setters so a partial native failure can be restored.
            state.customProvider = state.cachedProvider; state.restorePending = false;
            state.radius = ((Number) radius).floatValue(); state.weight = weight;
            QsTileAppearance.invoke(drawable, "setPathProvider", state.customProvider);
            QsTileAppearance.invoke(drawable, "invalidatePath");
            if (baseProvider(drawable) != state.customProvider) {
                // The native delegate can retain an equal provider without changing its identity.
                state.customProvider = null; return;
            }
            syncBlock(drawable, state);
            drawable.invalidateSelf(); invalidate(view);
            if (!loggedApplied) {
                loggedApplied = true;
                ModuleDiagnostics.info("qs_corners", "Native tile corner provider applied; requestedDp "
                        + policy.dp + ", boundedPx " + pixels + ", actualPx " + state.radius);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            restore(state); invalidate(view); error(unavailable);
        }
    }

    private static float pixels(View view, Drawable drawable, float dp) {
        Object object = QsTileAppearance.call(view, "getBg");
        View body = object instanceof View ? (View) object : view;
        int width = body.getMeasuredWidth(), height = body.getMeasuredHeight();
        if (width <= 0) width = body.getWidth();
        if (height <= 0) height = body.getHeight();
        if (width <= 0 || height <= 0) {
            Rect bounds = drawable.getBounds();
            if (bounds != null) { width = bounds.width(); height = bounds.height(); }
        }
        return radiusPixels(dp, body.getResources().getDisplayMetrics().density, width, height);
    }
    private boolean restore(Owned state) {
        if (state.customProvider == null && state.customBlock == null) { state.restorePending = false; return true; }
        Drawable drawable = state.drawable == null ? null : state.drawable.get();
        if (drawable == null) {
            state.customProvider = state.customBlock = state.nativeBlock = null;
            state.blockBaseRadius = Float.NaN; state.blockBaseWeight = null;
            state.restorePending = false; return true;
        }
        try {
            Object block = blockProvider(drawable);
            if (state.customBlock != null && block == state.customBlock) {
                state.writingBlock = true;
                try { QsTileAppearance.invoke(drawable, "setBlockPathProvider", state.nativeBlock); }
                finally { state.writingBlock = false; }
            }
            state.customBlock = null; state.nativeBlock = null;
            state.blockBaseRadius = Float.NaN; state.blockBaseWeight = null;
            Object current = baseProvider(drawable);
            if (current != state.customProvider && !(state.restorePending && current == state.nativeProvider)) {
                state.customProvider = null; state.restorePending = false; return true;
            }
            state.restorePending = true;
            QsTileAppearance.invoke(drawable, "setPathProvider", state.nativeProvider);
            QsTileAppearance.invoke(drawable, "invalidatePath");
            drawable.invalidateSelf(); state.customProvider = null; state.restorePending = false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            state.restorePending = true; error(unavailable); return false;
        }
    }
    private static void invalidate(View view) {
        view.invalidate(); view.invalidateOutline();
        Object body = QsTileAppearance.call(view, "getBg");
        if (body instanceof View) ((View) body).invalidateOutline();
    }
    /** All owned QS surfaces use the same native continuous-corner template.
     * The OEM factory input is a design radius; the final provider radius is physical px. */
    static Object continuousProvider(Method factory, Context context, float pixels, Drawable drawable)
            throws ReflectiveOperationException {
        float reference = DEFAULT_RADIUS * context.getResources().getDisplayMetrics().density;
        Object provider = factory.invoke(null, context, reference);
        normalizeProvider(provider, drawable, pixels);
        return provider;
    }

    /** QSConstant maps design radii to larger smooth radii. Normalize only physical radius. */
    static void normalizeProvider(Object provider, Drawable drawable, float pixels) throws ReflectiveOperationException {
        Object radius = QsTileAppearance.invoke(provider, "getCornerRadius", drawable);
        Object weight = QsTileAppearance.invoke(provider, "getCornerWeight", drawable);
        if (!(radius instanceof Number) || !Float.isFinite(((Number) radius).floatValue())
                || ((Number) radius).floatValue() < 0f
                || weight != null && (!(weight instanceof Float) || !Float.isFinite((Float) weight)))
            throw new IllegalStateException("Invalid native corner geometry");
        QsTileAppearance.invoke(provider, "update", pixels, weight);
        Object actual = QsTileAppearance.invoke(provider, "getCornerRadius", drawable);
        if (!(actual instanceof Number) || !Float.isFinite(((Number) actual).floatValue())
                || Math.abs(((Number) actual).floatValue() - pixels) > .001f)
            throw new IllegalStateException("Native corner radius does not match physical bounds");
    }
    static Object baseProvider(Drawable drawable) {
        Object delegate = QsTileAppearance.field(drawable, "delegate");
        Object provider = QsTileAppearance.field(delegate, "pathProvider");
        return provider == null ? QsTileAppearance.call(drawable, "getPathProvider") : provider;
    }
    static Object blockProvider(Drawable drawable) {
        return QsTileAppearance.field(QsTileAppearance.field(drawable, "delegate"), "blockPathProvider");
    }
    /** A native fixed-size deformation provider stays installed at rest on this ROM.
     * Replace only that narrow provider with an instance-local native clone, preserving its span/stroke code.
     * Launch/dialog providers remain under the ROM's ownership. */
    public Object adaptBlock(Drawable drawable, Object nativeBlock) {
        synchronized (lock) {
            WeakReference<View> ref = owners.get(drawable);
            View view = ref == null ? null : ref.get();
            Owned state = view == null ? null : tiles.get(view);
            if (state == null || state.writingBlock) return nativeBlock;
            if (nativeBlock == state.customBlock) return nativeBlock;
            if (policy.enabled && fixedTransitionAvailable && view.isAttachedToWindow()
                    && nativeBlock == state.nativeBlock && state.customBlock != null
                    && state.blockBaseRadius == state.radius
                    && java.util.Objects.equals(state.blockBaseWeight, state.weight)) try {
                Object span = QsTileAppearance.invoke(nativeBlock, "getCurrentSpanSize");
                // A spring frame can legitimately differ from the configured endpoint.
                // Do not replace it with another static clone on a state/layout callback.
                if (!java.util.Objects.equals(span, QsTileAppearance.invoke(state.customBlock, "getCurrentSpanSize")))
                    QsTileAppearance.invoke(state.customBlock, "setCurrentSpanSize", span);
                return state.customBlock;
            } catch (ReflectiveOperationException | RuntimeException changedNativeProvider) { }
            state.nativeBlock = nativeBlock; state.customBlock = null;
            state.blockBaseRadius = Float.NaN; state.blockBaseWeight = null;
            if (!policy.enabled || !fixedTransitionAvailable || !view.isAttachedToWindow() || state.customProvider == null
                    || !QsTileAppearance.type(nativeBlock, DEFORM_CLASS)) return nativeBlock;
            try {
                Object custom = nativeBlock.getClass().getConstructor(float.class, Float.class)
                        .newInstance(state.radius, state.weight);
                Object span = QsTileAppearance.invoke(nativeBlock, "getCurrentSpanSize");
                QsTileAppearance.invoke(custom, "setCurrentSpanSize", span);
                state.customBlock = custom;
                state.blockBaseRadius = state.radius; state.blockBaseWeight = state.weight;
                return custom;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                error(unavailable); return nativeBlock;
            }
        }
    }
    /** Called before the concrete OEM setValue(View,float), never its synthetic bridge.
     * The ROM animates its own final deformPathProvider; only the displayed, per-tile clone
     * is mirrored here. Its subsequent native invalidatePath refreshes every material layer.
     * No outline, view scale, spring timing, source field or shared provider is overwritten. */
    public void beforeFixedTransitionValue(Object transition, Object nativeRadius) {
        if (!policy.enabled || !fixedTransitionAvailable || !validRadius(nativeRadius)
                || !QsTileAppearance.type(transition, FIXED_RADIUS_CLASS)) return;
        Object candidate = QsTileAppearance.field(transition, "tileDrawable");
        if (!(candidate instanceof Drawable)) return;
        Drawable drawable = (Drawable) candidate;
        synchronized (lock) {
            WeakReference<View> ref = owners.get(drawable);
            View view = ref == null ? null : ref.get();
            Owned state = view == null ? null : owned(view);
            Object source = QsTileAppearance.field(transition, "deformPathProvider");
            if (state == null || state.writingBlock || state.customBlock == null
                    || source != state.nativeBlock || blockProvider(drawable) != state.customBlock) return;
            Object weight = QsTileAppearance.field(transition, "deformCornerWeight");
            if (weight != null && (!(weight instanceof Float) || !Float.isFinite((Float) weight))) return;
            float radius = ((Number) nativeRadius).floatValue();
            Rect bounds = drawable.getBounds();
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0)
                radius = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2f);
            try {
                Object current = QsTileAppearance.invoke(state.customBlock, "getCornerRadius", drawable);
                Object currentWeight = QsTileAppearance.invoke(state.customBlock, "getCornerWeight", drawable);
                if (!validRadius(current) || ((Number) current).floatValue() != radius
                        || !java.util.Objects.equals(weight, currentWeight))
                    QsTileAppearance.invoke(state.customBlock, "update", radius, weight);
                Object span = QsTileAppearance.invoke(source, "getCurrentSpanSize");
                if (!java.util.Objects.equals(span, QsTileAppearance.invoke(state.customBlock, "getCurrentSpanSize")))
                    QsTileAppearance.invoke(state.customBlock, "setCurrentSpanSize", span);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                error(unavailable);
            }
        }
    }
    private static boolean validRadius(Object radius) {
        return radius instanceof Number && Float.isFinite(((Number) radius).floatValue())
                && ((Number) radius).floatValue() >= 0f;
    }
    private void syncBlock(Drawable drawable, Owned state) throws ReflectiveOperationException {
        Object current = blockProvider(drawable);
        Object nativeBlock = current == state.customBlock ? state.nativeBlock : current;
        Object next = adaptBlock(drawable, nativeBlock);
        if (next == current) return;
        state.writingBlock = true;
        try { QsTileAppearance.invoke(drawable, "setBlockPathProvider", next); }
        finally { state.writingBlock = false; }
        QsTileAppearance.invoke(drawable, "invalidatePath");
    }
    private void observe(View view, Owned state) {
        if (state.layout == null) {
            WeakReference<View> ref = new WeakReference<>(view);
            state.layout = (changed, l, t, r, b, ol, ot, or, ob) -> {
                View owner = ref.get();
                if (owner != null && (l != ol || t != ot || r != or || b != ob)) onNativeUpdate(owner);
            };
            view.addOnLayoutChangeListener(state.layout);
        }
        Object candidate = QsTileAppearance.call(view, "getBg");
        View body = candidate instanceof View && candidate != view ? (View) candidate : null;
        View old = state.body.get();
        if (old == body) return;
        if (old != null) old.removeOnLayoutChangeListener(state.layout);
        state.body = new WeakReference<>(body);
        if (body != null) body.addOnLayoutChangeListener(state.layout);
    }
    private static void stopObserving(View view, Owned state) {
        if (state.layout != null) {
            view.removeOnLayoutChangeListener(state.layout);
            View body = state.body.get();
            if (body != null) body.removeOnLayoutChangeListener(state.layout);
        }
    }
    private void runOnMain(Runnable update) {
        if (Looper.myLooper() == Looper.getMainLooper()) update.run(); else main.post(update);
    }
    private void error(Throwable failure) {
        if (!loggedError) {
            loggedError = true;
            ModuleDiagnostics.error("qs_corners", "Tile corner geometry unavailable; native material retained", failure);
        }
    }
}
