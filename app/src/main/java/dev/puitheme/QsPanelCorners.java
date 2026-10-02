// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.WeakHashMap;

/** Owns native final shape parameters; never changes control layout or input geometry. */
public final class QsPanelCorners {
    public static final String SLIDER_CLASS = "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar";
    public static final String SLIDER_BASE_CLASS = "com.coui.appcompat.seekbar.COUIVerticalSeekBar";
    public static final String MEDIA_CLASS = "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView";
    public static final String EDITABLE_CONTAINER_CLASS = "com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1";
    public static final String EDITABLE_HOLDER_CLASS = "com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder";
    public static final String DEVICE_HOST_CLASS = "com.oplus.systemui.qs.mydevice.MyDevicePanel";
    public static final String DEVICE_SERVICE_CLASS = "com.oplus.deviceplugin.sdk.SysUiPluginService";
    public static final String DEVICE_CLASS = "com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView";
    public static final String DEVICE_BASE_CLASS = "com.oplus.deviceplugin.sdk.ui.view.separatecardview.d";
    public static final String[] DEVICE_CLASSES = {
            DEVICE_CLASS,
            "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView",
            "com.oplus.deviceplugin.sdk.ui.view.separatecardview.NoDeviceEntranceCardView",
            "com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleEntranceCardView",
            "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView"};
    private static final String LAYER_CLASS = "com.oplus.systemui.qs.base.res.drawable.TileLayerDrawable";
    private static final String OUTLINE_CLASS = "com.oplusos.systemui.common.outline.CornerOutlineProvider";
    private static final String FACTORY_CLASS = "com.oplus.systemui.qs.base.res.util.QSConstant";
    public static final String BLUR_MANAGER_CLASS = "com.oplus.systemui.qs.base.util.QsSeekBarBlurManager";
    private final Object lock = new Object();
    private final Map<View, Owned> controls = new WeakHashMap<>();
    private final Map<Drawable, WeakReference<View>> mediaOwners = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadLocal<SliderDrawing> drawing = new ThreadLocal<>();
    private final ThreadLocal<View> blurOwner = new ThreadLocal<>();
    private final ThreadLocal<SpotlightScope> spotlight = new ThreadLocal<>();
    private final ThreadLocal<Boolean> rewritingShape = new ThreadLocal<>();
    private final DrawScope noDraw = new DrawScope(null, null, null);
    private static final String[] BACKGROUND_PATHS = {"mBackgroundPath", "mBackgroundPathAdapter"};
    private static final String[] PROGRESS_PATHS = {"mClipProgressPath", "mProgressPathAdapter", "mProgressInnerSmoothPath",
            "mProgressInnerPathAdapter", "oplusPathAdapter", "clipPath", "inactivePath"};
    private static final String[] THUMB_PATHS = {"mThumbSmoothPath", "mThumbPathAdapter"};
    private volatile boolean enabled;
    private volatile float dp = QsTileCorners.DEFAULT_RADIUS;
    private boolean warning, logged;

    private static final class Owned {
        WeakReference<Drawable> drawable = new WeakReference<>(null);
        QsDeviceCorners device;
        Object nativeProvider, customProvider, cachedProvider, weight;
        Method factory;
        float radius, cachedPixels = -1f, nativeBlur = Float.NaN;
        boolean blurOwned, writingBlur, restoringBlur, restorePending, deviceOwned, mediaLightOwned;
        WeakReference<View> body = new WeakReference<>(null);
        View.OnLayoutChangeListener layout;
        final Map<String, Field> sliderFields = new HashMap<>();
        SliderDrawing sliderDrawing;
        Field nativeRadius;
        Method blurSetter;
        Object smoothUtils;
        Method smoothPath;
        final float[] spotlightRadii = new float[8];
        boolean blurCacheValid, blurStroke, blurDetail;
        float blurTarget, blurNative, blurWeight, blurMirror;
        int blurLeft, blurTop, blurRight, blurBottom;
        WeakReference<Object> blurBase = new WeakReference<>(null), blurActive = new WeakReference<>(null);
    }

    private static final class SliderDrawing {
        final WeakReference<View> owner;
        final IdentityHashMap<Object, Integer> paths = new IdentityHashMap<>();
        final IdentityHashMap<Object, Integer> paints = new IdentityHashMap<>();
        final IdentityHashMap<Object, Boolean> canvases = new IdentityHashMap<>();
        SliderDrawing(View view) { owner = new WeakReference<>(view); }
        View view() { return owner.get(); }
        void clear() { paths.clear(); paints.clear(); canvases.clear(); }
    }

    /** Call once around COUIVerticalSeekBar.draw(Canvas), with close in finally. */
    public final class DrawScope implements AutoCloseable {
        private final View view;
        private final SliderDrawing previous;
        private final SliderDrawing current;
        private boolean closed;
        private DrawScope(View view, SliderDrawing previous, SliderDrawing current) {
            this.view = view; this.previous = previous; this.current = current;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (view != null) {
                current.clear();
                if (previous == null) drawing.remove(); else drawing.set(previous);
            }
        }
    }

    public void configure(Bundle settings) {
        QsDeviceCorners.configureDiagnostics(settings != null && Boolean.TRUE.equals(settings.get(ModuleDiagnostics.KEY_ENABLED)));
        boolean next = settings != null && Boolean.TRUE.equals(settings.get(QsTileCorners.MASTER))
                && !SafetyMode.enabled(settings);
        float radius = QsTileCorners.radiusDp(settings == null ? null : settings.get(QsTileCorners.RADIUS));
        if (enabled == next && dp == radius) return;
        QsDeviceCorners.resetGeometryDiagnostics();
        enabled = next; dp = radius;
        runOnMain(() -> {
            synchronized (lock) {
                warning = logged = false;
                for (View view : new ArrayList<>(controls.keySet())) if (view != null) refresh(view);
            }
        });
    }

    public static boolean isControl(View view) {
        return isSlider(view) || isMedia(view) || isDevice(view);
    }
    private static boolean isSlider(View view) { return QsTileAppearance.type(view, SLIDER_CLASS); }
    private static boolean isMedia(View view) { return QsTileAppearance.type(view, MEDIA_CLASS); }
    private static boolean isDevice(View view) {
        if (QsTileAppearance.type(view, DEVICE_BASE_CLASS)) return true;
        for (String type : DEVICE_CLASSES) if (QsTileAppearance.type(view, type)) return true;
        return false;
    }

    public void onNativeUpdate(View view) {
        if (!isControl(view)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> ref = new WeakReference<>(view);
            main.post(() -> { View current = ref.get(); if (current != null) onNativeUpdate(current); });
            return;
        }
        synchronized (lock) { refresh(view); }
    }

    public void detach(View view) {
        if (!isControl(view)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> ref = new WeakReference<>(view);
            main.post(() -> { View current = ref.get(); if (current != null) detach(current); });
            return;
        }
        synchronized (lock) {
            Owned state = controls.get(view);
            if (state != null && restore(view, state)) {
                Drawable drawable = state.drawable.get();
                if (drawable != null) mediaOwners.remove(drawable);
                stopObserving(view, state); controls.remove(view);
            }
            invalidate(view);
        }
    }

    /** Reconcile the light path when the ROM installs or clears a temporary media outline. */
    public void onDrawableUpdate(Drawable drawable) {
        View owner;
        synchronized (lock) {
            WeakReference<View> ref = mediaOwners.get(drawable);
            owner = ref == null ? null : ref.get();
        }
        if (owner != null) onNativeUpdate(owner);
    }

    public DrawScope beginDraw(View view) {
        return beginDraw(view, null);
    }

    public DrawScope beginDraw(View view, Canvas canvas) {
        SliderDrawing previous = drawing.get();
        if (!enabled || !isSlider(view) || !view.isAttachedToWindow()
                || previous != null && previous.view() == view) return noDraw;
        SliderDrawing current = null;
        try {
            float target = pixels(view, null);
            if (target < 0f) return noDraw;
            synchronized (lock) {
                Owned state = controls.get(view);
                if (state == null) { state = new Owned(); controls.put(view, state); }
                if (state.sliderDrawing == null) state.sliderDrawing = new SliderDrawing(view);
                current = state.sliderDrawing;
            }
            current.clear();
            addShapes(current, 1, BACKGROUND_PATHS);
            addShapes(current, 2, PROGRESS_PATHS);
            addShapes(current, 3, THUMB_PATHS);
            addPaint(current, 1, "mBackgroundPaint");
            addPaint(current, 2, "mProgressPaint");
            addPaint(current, 3, "mThumbPaint");
            if (canvas != null) current.canvases.put(canvas, true);
            drawing.set(current);
            synchronized (lock) {
                Owned state = controls.get(view);
                if (state == null) { state = new Owned(); controls.put(view, state); }
                state.nativeBlur = nativeRadius(view, state);
                updateBlur(view, state, sliderBlurCurve(view, state.nativeBlur));
            }
            applied("slider", target);
            return new DrawScope(view, previous, current);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (current != null) current.clear();
            if (previous == null) drawing.remove(); else drawing.set(previous);
            synchronized (lock) {
                Owned state = controls.get(view);
                if (state != null) restore(view, state);
            }
            unavailable(error);
            return noDraw;
        }
    }

    /** Keep the caller's native radius for safe-mode/off restoration, including new themes. */
    public float blurRadius(View view, float nativeRadius) {
        if (!isSlider(view)) return nativeRadius;
        synchronized (lock) {
            Owned state = controls.get(view);
            if (state == null) { state = new Owned(); controls.put(view, state); }
            // Native fields always remain native: they also determine track endpoints,
            // thumb position and hit geometry. Only the blur setter's curvature changes.
            if (state.writingBlur) return nativeRadius;
            state.blurCacheValid = false;
            if (Float.isFinite(nativeRadius)) state.nativeBlur = nativeRadius;
            float target = enabled && view.isAttachedToWindow() ? pixels(view, null) : -1f;
            state.blurOwned = target >= 0f;
            if (state.blurOwned) try { prepareContinuous(view, state, target, null); }
            catch (ReflectiveOperationException | RuntimeException error) { state.blurOwned = false; unavailable(error); }
            return state.blurOwned ? sliderBlurCurve(view, nativeRadius) : nativeRadius;
        }
    }

    public final class BlurScope implements AutoCloseable {
        public final float radius;
        private final Owned state;
        private final boolean previous;
        private final View previousOwner;
        private boolean closed;
        BlurScope(float radius, Owned state, boolean previous, View previousOwner) {
            this.radius = radius; this.state = state; this.previous = previous; this.previousOwner = previousOwner;
        }
        @Override public void close() {
            if (!closed && state != null) synchronized (lock) { state.writingBlur = previous; }
            if (!closed) { if (previousOwner == null) blurOwner.remove(); else blurOwner.set(previousOwner); }
            closed = true;
        }
    }
    /** The native update setter forwards its radius into both blur configs. */
    public BlurScope beginBlurUpdate(View view, float nativeRadius) {
        float target = blurRadius(view, nativeRadius);
        synchronized (lock) {
            Owned state = controls.get(view);
            View previousOwner = blurOwner.get();
            if (state == null) return new BlurScope(target, null, false, previousOwner);
            boolean previous = state.writingBlur; state.writingBlur = true;
            if (enabled && state.blurOwned && !state.restoringBlur && view.isAttachedToWindow()) blurOwner.set(view);
            return new BlurScope(target, state, previous, previousOwner);
        }
    }

    public Object cornerRadius(View view, Object nativeResult) {
        synchronized (lock) {
            Owned state = controls.get(view);
            Drawable drawable = state == null ? null : state.drawable.get();
            return enabled && isMedia(view) && state != null && state.customProvider != null
                    && drawable == QsTileAppearance.call(view, "getTransitionDrawable")
                    && QsTileCorners.baseProvider(drawable) == state.customProvider
                    && QsTileCorners.blockProvider(drawable) == null
                    ? state.radius : nativeResult;
        }
    }

    /** The native plugin wrapper owns a separate spotlight path, but does not clip its children. */
    public Object editableRadius(View wrapper, Object nativeResult) {
        if (!enabled || !QsTileAppearance.type(wrapper, EDITABLE_CONTAINER_CLASS)) return nativeResult;
        synchronized (lock) {
            View owner = panelChild(wrapper, 0);
            Owned state = owner == null ? null : controls.get(owner);
            if (state == null || !owner.isAttachedToWindow()) return nativeResult;
            if (isMedia(owner)) return cornerRadius(owner, nativeResult);
            if (isDevice(owner)) return state.deviceOwned ? state.radius : nativeResult;
            float radius = pixels(owner, null);
            return radius >= 0f ? radius : nativeResult;
        }
    }
    private static View panelChild(View candidate, int depth) {
        if (isControl(candidate)) return candidate;
        if (!(candidate instanceof ViewGroup) || depth >= 10) return null;
        ViewGroup group = (ViewGroup) candidate;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = panelChild(group.getChildAt(i), depth + 1);
            if (child != null) return child;
        }
        return null;
    }

    /** Inspect only the ROM's actual plugin container after native binding, never a global View tree. */
    public static List<View> pluginControls(View container) {
        List<View> result = new ArrayList<>();
        if (!QsTileAppearance.type(container, EDITABLE_CONTAINER_CLASS)
                && !QsTileAppearance.type(container, DEVICE_HOST_CLASS)) return result;
        collectPluginControls(container, 0, new int[]{0}, result);
        return result;
    }
    private static void collectPluginControls(View view, int depth, int[] visited, List<View> result) {
        if (view == null || depth > 10 || visited[0]++ >= 96) return;
        QsDeviceCorners.tracePluginChild(view, depth);
        if (isControl(view)) { result.add(view); return; }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount() && visited[0] < 96; i++)
            collectPluginControls(group.getChildAt(i), depth + 1, visited, result);
    }

    static float scaledRadius(float current, float base, float target, float limit) {
        float ratio = Float.isFinite(current) && Float.isFinite(base) && base > 0f ? current / base : 1f;
        return Math.max(0f, Math.min(limit, target * Math.max(0f, ratio)));
    }

    private Object value(View view, String name) {
        synchronized (lock) {
            Owned state = controls.get(view);
            if (state == null) { state = new Owned(); controls.put(view, state); }
            try {
                if (!state.sliderFields.containsKey(name)) {
                    Field found;
                    try { found = field(view.getClass(), name); }
                    catch (NoSuchFieldException absent) { found = null; }
                    state.sliderFields.put(name, found);
                }
                Field found = state.sliderFields.get(name);
                return found == null ? null : found.get(view);
            } catch (ReflectiveOperationException | RuntimeException absent) { return null; }
        }
    }
    private void addShapes(SliderDrawing scope, int kind, String... fields) {
        for (String name : fields) {
            Object shape = value(scope.view(), name);
            if (shape != null) scope.paths.put(shape, kind);
        }
    }
    private void addPaint(SliderDrawing scope, int kind, String name) {
        Object paint = value(scope.view(), name);
        if (paint instanceof Paint) scope.paints.put(paint, kind);
    }

    /** Bind only wrappers created from this control's actual paths/canvas in this draw. */
    public boolean isDrawingShapes() { return enabled && (drawing.get() != null || spotlight.get() != null); }

    /** Scoped to the exact native wrapper callback; its original Rect/content remain native. */
    public final class SpotlightScope implements AutoCloseable {
        private final SpotlightScope previous;
        private final Path path;
        private final Object adapter;
        private final Owned state;
        private boolean closed;
        private SpotlightScope(SpotlightScope previous, Path path, Object adapter, Owned state) {
            this.previous = previous; this.path = path; this.adapter = adapter; this.state = state;
        }
        @Override public void close() {
            if (closed) return;
            if (previous == null) spotlight.remove(); else spotlight.set(previous);
            closed = true;
        }
    }
    public SpotlightScope beginSpotlight(View wrapper, Path path, Object adapter) {
        SpotlightScope previous = spotlight.get();
        Owned state = null;
        if (enabled && path != null && adapter != null && wrapper.isAttachedToWindow()
                && QsTileAppearance.type(wrapper, EDITABLE_CONTAINER_CLASS)) synchronized (lock) {
            View owner = panelChild(wrapper, 0);
            Owned candidate = owner == null ? null : controls.get(owner);
            if (owner != null && owner.isAttachedToWindow() && candidate != null
                    && (candidate.deviceOwned || isMedia(owner) && candidate.customProvider != null
                    && QsTileCorners.blockProvider(candidate.drawable.get()) == null)) {
                try {
                    if (candidate.smoothPath == null) {
                        Class<?> type = Class.forName("com.oplus.posteffect.util.OplusPathAdapterCompatUtils", false,
                                wrapper.getClass().getClassLoader());
                        candidate.smoothUtils = type.getField("INSTANCE").get(null);
                        candidate.smoothPath = type.getDeclaredMethod("addSmoothRoundRect", Object.class,
                                RectF.class, float[].class, Path.Direction.class, float.class);
                        candidate.smoothPath.setAccessible(true);
                    }
                    if (candidate.weight instanceof Number) state = candidate;
                } catch (ReflectiveOperationException | RuntimeException unavailable) { unavailable(unavailable); }
            }
        }
        SpotlightScope current = new SpotlightScope(previous, path, adapter, state);
        if (state != null) spotlight.set(current);
        return current;
    }

    public void onShapeWrapper(Object wrapper, Object source) {
        SliderDrawing scope = drawing.get();
        if (scope == null || wrapper == null || source == null) return;
        Integer kind = scope.paths.get(source);
        if (kind != null) scope.paths.put(wrapper, kind);
        else if (scope.canvases.containsKey(source)) scope.canvases.put(wrapper, true);
    }

    /** Nested OEM wrappers must not apply the animated radius ratio twice. */
    public final class ShapeScope implements AutoCloseable {
        public final Object[] args;
        public final boolean handled;
        private final boolean changed;
        private boolean closed;
        ShapeScope(Object[] args, boolean changed) { this(args, changed, false); }
        ShapeScope(Object[] args, boolean changed, boolean handled) { this.args = args; this.changed = changed; this.handled = handled; }
        @Override public void close() {
            if (!closed && changed) rewritingShape.remove();
            closed = true;
        }
    }

    /** Only native track shape calls owned by the exact Oplus draw scope are rewritten. */
    public ShapeScope shape(Object receiver, String name, Object[] original) {
        SpotlightScope light = spotlight.get();
        if (enabled && light != null && !Boolean.TRUE.equals(rewritingShape.get())
                && receiver == light.path && name.equals("addRoundRect") && original != null
                && original.length == 4 && original[0] instanceof RectF && original[3] instanceof Path.Direction) {
            RectF rect = (RectF) original[0];
            float radius = Math.min(light.state.radius, Math.min(rect.width(), rect.height()) / 2f);
            if (Float.isFinite(radius) && radius >= 0f && rect.width() > 0f && rect.height() > 0f) {
                float[] radii = light.state.spotlightRadii;
                java.util.Arrays.fill(radii, radius);
                rewritingShape.set(true);
                try {
                    Object result = light.state.smoothPath.invoke(light.state.smoothUtils, light.adapter,
                            rect, radii, original[3], number(light.state.weight));
                    if (Boolean.TRUE.equals(result)) return new ShapeScope(original, false, true);
                } catch (ReflectiveOperationException | RuntimeException unavailable) { unavailable(unavailable); }
                finally { rewritingShape.remove(); }
            }
        }
        SliderDrawing scope = drawing.get();
        if (!enabled || scope == null || Boolean.TRUE.equals(rewritingShape.get()) || original == null)
            return new ShapeScope(original, false);
        Integer kind = scope.paths.get(receiver);
        if (kind == null && scope.canvases.containsKey(receiver)) {
            for (Object arg : original) if (scope.paints.containsKey(arg)) { kind = scope.paints.get(arg); break; }
        }
        if (kind == null || !(name.equals("addSmoothRoundRect") || name.equals("addRoundRect")
                || name.equals("drawSmoothRoundRect") || name.equals("drawRoundRect")))
            return new ShapeScope(original, false);
        int radiusStart;
        float width, height;
        if (original.length > 0 && original[0] instanceof RectF) {
            RectF rect = (RectF) original[0]; width = rect.width(); height = rect.height(); radiusStart = 1;
        } else if (original.length > 4 && original[0] instanceof Number && original[1] instanceof Number
                && original[2] instanceof Number && original[3] instanceof Number) {
            width = number(original[2]) - number(original[0]); height = number(original[3]) - number(original[1]); radiusStart = 4;
        } else return new ShapeScope(original, false);
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f)
            return new ShapeScope(original, false);
        Object[] next = original.clone();
        if (next[radiusStart] instanceof float[]) {
            float[] radii = ((float[]) next[radiusStart]).clone();
            if (radii.length != 8) return new ShapeScope(original, false);
            for (int i = 0; i < radii.length; i++) {
                if (!Float.isFinite(radii[i])) return new ShapeScope(original, false);
                // Native active tracks deliberately have square junction corners.
                if (radii[i] > 0f) radii[i] = sliderCurve(scope.view(), radii[i], kind, width, height);
            }
            next[radiusStart] = radii;
        } else if (next[radiusStart] instanceof Number) {
            next[radiusStart] = sliderCurve(scope.view(), number(next[radiusStart]), kind, width, height);
            // Adapter old API has radius+weight; new API has radiusX+radiusY+weight.
            // Path/Canvas standard APIs always have radiusX+radiusY.
            boolean two = name.equals("addRoundRect") || name.equals("drawRoundRect")
                    || radiusStart == 4 || original.length >= radiusStart + 4;
            if (two && next[radiusStart + 1] instanceof Number)
                next[radiusStart + 1] = sliderCurve(scope.view(), number(next[radiusStart + 1]), kind, width, height);
        } else return new ShapeScope(original, false);
        // The audited OEM APIs forward this dimensionless coefficient into the same
        // Oplus continuous-corner renderer used by tile/media/device outlines.
        if (name.equals("addSmoothRoundRect") || name.equals("drawSmoothRoundRect")) {
            int weightIndex = -1;
            if (name.equals("drawSmoothRoundRect")) weightIndex = next.length - 1;
            else if (next[radiusStart] instanceof float[]) weightIndex = next[next.length - 1] instanceof Number ? next.length - 1 : next.length - 2;
            else weightIndex = next.length - 2; // Direction is last in both old/new path APIs.
            if (weightIndex > radiusStart && next[weightIndex] instanceof Number)
                next[weightIndex] = sliderWeight(scope.view(), number(next[weightIndex]), kind);
        }
        rewritingShape.set(true);
        return new ShapeScope(next, true);
    }

    public boolean isRewritingBlur() { return enabled && (drawing.get() != null || blurOwner.get() != null); }

    /** Blur factories keep native Rect/material and use the same final continuous curve. */
    public Object[] blurShape(String name, Object[] original) {
        SliderDrawing scope = drawing.get();
        if (!enabled || scope == null && blurOwner.get() == null || original == null || original.length < 4
                || !(name.equals("applySeekBarBgBlurConfig") || name.equals("applySeekBarActiveBlurConfig")
                || name.equals("createSeekBarBlurDrawable"))) return original;
        // Both audited native methods pass the original rectangle as parameter 1.
        if (!(original[1] instanceof Rect) || !(original[2] instanceof Number)) return original;
        Object source = original[0];
        View view = blurOwner.get();
        if (view == null && scope != null) view = scope.view();
        if (view == null || !(source == view || source == view.getContext())) return original;
        Rect rect = (Rect) original[1];
        float target;
        synchronized (lock) {
            Owned state = controls.get(view);
            if (state != null && state.restoringBlur) return original;
            target = state != null && state.writingBlur
                    ? Math.max(0f, Math.min(number(original[2]), Math.min(rect.width(), rect.height()) / 2f))
                    : sliderCurve(view, number(original[2]), 2, rect.width(), rect.height());
        }
        Object[] next = original.clone(); next[2] = target;
        if (next[3] instanceof Number) next[3] = sliderWeight(view, number(next[3]), 1);
        return next;
    }

    private static float number(Object value) { return ((Number) value).floatValue(); }
    private float sliderBlurCurve(View view, float nativeRadius) {
        Object bounds = value(view, "mClipProgressRect");
        if (bounds instanceof Rect && ((Rect) bounds).width() > 0 && ((Rect) bounds).height() > 0) {
            Rect rect = (Rect) bounds;
            return sliderCurve(view, nativeRadius, 2, rect.width(), rect.height());
        }
        return sliderCurve(view, nativeRadius, 2, view.getWidth(), view.getHeight());
    }
    private float sliderCurve(View view, float nativeRadius, int kind, float width, float height) {
        if (!Float.isFinite(nativeRadius) || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0f || height <= 0f) return nativeRadius;
        Object raw = value(view, kind == 1 ? "mBackgroundRadius" : "mProgressRadius");
        if (!(raw instanceof Number) || !Float.isFinite(number(raw)) || number(raw) <= 0f) return nativeRadius;
        float target = dp * view.getResources().getDisplayMetrics().density;
        if (!Float.isFinite(target) || target < 0f) return nativeRadius;
        return scaledRadius(nativeRadius, number(raw), target, Math.min(width, height) / 2f);
    }
    private float sliderWeight(View view, float nativeWeight, int kind) {
        if (view == null || !Float.isFinite(nativeWeight)) return nativeWeight;
        synchronized (lock) {
            Owned state = controls.get(view);
            if (state == null || !(state.weight instanceof Number)) return nativeWeight;
            float target = number(state.weight);
            Object raw = value(view, kind == 1 ? "mBackgroundRoundCornerWeight" : "mProgressRoundCornerWeight");
            if (!(raw instanceof Number) || !Float.isFinite(number(raw))) return nativeWeight;
            float base = number(raw);
            // Native fields remain untouched. Any transient final-shape coefficient
            // relative to that native base still follows the native press animation.
            float result = base > 0f ? target * (nativeWeight / base) : target + nativeWeight - base;
            return Float.isFinite(result) && result >= 0f ? result : nativeWeight;
        }
    }

    private void prepareContinuous(View view, Owned state, float target, Drawable drawable)
            throws ReflectiveOperationException {
        if (state.cachedProvider == null || state.cachedPixels != target) {
            if (state.factory == null) {
                Class<?> factory = Class.forName(FACTORY_CLASS, false, view.getClass().getClassLoader());
                state.factory = factory.getDeclaredMethod("getSmoothRoundRectOutlineProvider", Context.class, float.class);
                state.factory.setAccessible(true);
            }
            state.cachedProvider = QsTileCorners.continuousProvider(state.factory, view.getContext(), target, drawable);
            state.weight = QsTileAppearance.invoke(state.cachedProvider, "getCornerWeight", drawable);
            state.cachedPixels = target;
        }
    }

    private void refresh(View view) {
        Owned state = controls.get(view);
        if (state == null) { state = new Owned(); controls.put(view, state); }
        observe(view, state);
        if (isSlider(view)) {
            state.blurCacheValid = false;
            if (enabled && view.isAttachedToWindow()) {
                float target = pixels(view, null);
                if (target >= 0f) try {
                    float nativeRadius = nativeRadius(view, state);
                    state.nativeBlur = nativeRadius;
                    updateBlur(view, state, sliderBlurCurve(view, nativeRadius));
                }
                catch (ReflectiveOperationException | RuntimeException error) { restore(view, state); unavailable(error); }
            } else restore(view, state);
            invalidate(view); return;
        }
        if (isMedia(view)) refreshMedia(view, state);
        else if (isDevice(view)) refreshDevice(view, state);
    }

    /** Bind every actual native card shape independently, including an old outer blur outline. */
    private void refreshDevice(View view, Owned state) {
        if (!enabled || !view.isAttachedToWindow()) { restore(view, state); invalidate(view); return; }
        try {
            if (state.device == null) state.device = new QsDeviceCorners();
            View body = deviceBody(view);
            if (body == null) { restore(view, state); return; }
            float target = QsTileCorners.radiusPixels(dp, body.getResources().getDisplayMetrics().density,
                    body.getWidth(), body.getHeight());
            if (target < 0f) return;
            prepareContinuous(view, state, target, null);
            if (!state.device.refresh(view, dp, state.weight instanceof Float ? (Float) state.weight : null)) { restore(view, state); return; }
            state.radius = state.device.radius; state.deviceOwned = true;
            applied("device", state.radius);
        } catch (ReflectiveOperationException | RuntimeException error) { restore(view, state); unavailable(error); }
    }
    private static View deviceBody(View view) { return QsDeviceCorners.body(view); }

    private void refreshMedia(View view, Owned state) {
        Object object = QsTileAppearance.call(view, "getTransitionDrawable");
        if (!(object instanceof Drawable) || !QsTileAppearance.type(object, LAYER_CLASS)) {
            restore(view, state); return;
        }
        Drawable drawable = (Drawable) object;
        if (drawable != state.drawable.get()) {
            if (!restore(view, state)) return;
            Drawable previous = state.drawable.get();
            if (previous != null) mediaOwners.remove(previous);
            state.drawable = new WeakReference<>(drawable);
            state.nativeProvider = state.cachedProvider = null; state.cachedPixels = -1f;
        }
        mediaOwners.put(drawable, new WeakReference<>(view));
        Object current = QsTileCorners.baseProvider(drawable);
        if (!QsTileAppearance.type(current, OUTLINE_CLASS)) return;
        if (current != state.customProvider && !(state.restorePending && current == state.nativeProvider)) {
            state.nativeProvider = current; state.customProvider = null;
        }
        if (!enabled || !view.isAttachedToWindow()) { restore(view, state); invalidate(view); return; }
        if (state.restorePending && !restore(view, state)) return;
        float target = pixels(view, drawable);
        if (target < 0f || state.nativeProvider == null) return;
        try {
            prepareContinuous(view, state, target, drawable);
            if (!QsTileAppearance.type(state.cachedProvider, OUTLINE_CLASS))
                throw new IllegalStateException("Unknown native media outline provider");
            Object radius = QsTileAppearance.invoke(state.cachedProvider, "getCornerRadius", drawable);
            Object weight = QsTileAppearance.invoke(state.cachedProvider, "getCornerWeight", drawable);
            if (!(radius instanceof Number) || !Float.isFinite(((Number) radius).floatValue())
                    || weight != null && (!(weight instanceof Float) || !Float.isFinite((Float) weight)))
                throw new IllegalStateException("Invalid native media geometry");
            state.radius = ((Number) radius).floatValue(); state.weight = weight;
            state.customProvider = state.cachedProvider; state.restorePending = false; state.mediaLightOwned = true;
            // A native outline update can refresh the media light path without replacing
            // the static provider. Reassert both native paths together after that update.
            QsTileAppearance.invoke(drawable, "setPathProvider", state.customProvider);
            QsTileAppearance.invoke(drawable, "invalidatePath");
            // Native launch/deformation providers temporarily win over the static path.
            // Their light path must match that same effective geometry until they clear.
            Object effective = QsTileAppearance.call(drawable, "getPathProvider");
            QsTileAppearance.invoke(drawable, "setCornerParams",
                    QsTileAppearance.invoke(effective, "getCornerRadius", drawable),
                    QsTileAppearance.invoke(effective, "getCornerWeight", drawable));
            drawable.invalidateSelf(); invalidate(view); applied("media", target);
        } catch (ReflectiveOperationException | RuntimeException error) { restore(view, state); unavailable(error); }
    }

    private void updateBlur(View view, Owned state, float radius) throws ReflectiveOperationException {
        prepareContinuous(view, state, pixels(view, null), null);
        if (!Float.isFinite(state.nativeBlur)) state.nativeBlur = nativeRadius(view, state);
        Object base = value(view, "baseMixColorDrawable"), active = value(view, "activeMixColorDrawable");
        Object rectangle = value(view, "mClipProgressRect");
        Rect rect = rectangle instanceof Rect ? (Rect) rectangle : null;
        int left = rect == null ? 0 : rect.left, top = rect == null ? 0 : rect.top;
        int right = rect == null ? 0 : rect.right, bottom = rect == null ? 0 : rect.bottom;
        float weight = nativeNumber(value(view, "mBackgroundRoundCornerWeight"));
        float mirror = nativeNumber(value(view, "mirrorScaleValue"));
        boolean stroke = Boolean.TRUE.equals(value(view, "isSupportStroke"));
        boolean detail = Boolean.TRUE.equals(value(view, "isDetailToggle"));
        // Read live native inputs every frame. Only an identical setter submission is cached;
        // new/late material drawables, press curvature, Rect bounds and native flags still win.
        if (state.blurCacheValid && sameFloat(state.blurTarget, radius) && sameFloat(state.blurNative, state.nativeBlur)
                && state.blurBase.get() == base && state.blurActive.get() == active
                && state.blurLeft == left && state.blurTop == top && state.blurRight == right && state.blurBottom == bottom
                && sameFloat(state.blurWeight, weight) && sameFloat(state.blurMirror, mirror)
                && state.blurStroke == stroke && state.blurDetail == detail) return;
        state.blurCacheValid = false;
        state.writingBlur = true;
        // Claim restoration before a setter that can mutate then throw.
        state.blurOwned = true;
        View previousOwner = blurOwner.get(); blurOwner.set(view);
        try {
            blurSetter(view, state).invoke(view, radius);
            state.blurTarget = radius; state.blurNative = state.nativeBlur;
            state.blurLeft = left; state.blurTop = top; state.blurRight = right; state.blurBottom = bottom;
            state.blurWeight = weight; state.blurMirror = mirror; state.blurStroke = stroke; state.blurDetail = detail;
            if (state.blurBase.get() != base) state.blurBase = new WeakReference<>(base);
            if (state.blurActive.get() != active) state.blurActive = new WeakReference<>(active);
            state.blurCacheValid = true;
        }
        finally { state.writingBlur = false; if (previousOwner == null) blurOwner.remove(); else blurOwner.set(previousOwner); }
    }
    private static float nativeNumber(Object value) { return value instanceof Number ? ((Number) value).floatValue() : Float.NaN; }
    private static boolean sameFloat(float left, float right) { return Float.floatToIntBits(left) == Float.floatToIntBits(right); }
    private static float nativeRadius(View view, Owned state) throws ReflectiveOperationException {
        if (state.nativeRadius == null) state.nativeRadius = field(view.getClass(), "mCurProgressRadius");
        return state.nativeRadius.getFloat(view);
    }
    private static Method blurSetter(View view, Owned state) throws NoSuchMethodException {
        if (state.blurSetter != null) return state.blurSetter;
        for (Class<?> owner = view.getClass(); owner != null; owner = owner.getSuperclass()) try {
            Method result = owner.getDeclaredMethod("updateBaseMixColorDrawableRadius", float.class);
            result.setAccessible(true); state.blurSetter = result; return result;
        } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(view.getClass().getName() + ".updateBaseMixColorDrawableRadius");
    }

    private boolean restore(View view, Owned state) {
        state.blurCacheValid = false;
        boolean success = true;
        Drawable drawable = state.drawable.get();
        if (drawable != null && (state.customProvider != null || state.mediaLightOwned)) try {
            Object current = QsTileCorners.baseProvider(drawable);
            boolean ownPath = current == state.customProvider || state.restorePending && current == state.nativeProvider;
            Object base = ownPath ? state.nativeProvider : current;
            if (ownPath) {
                state.restorePending = true;
                QsTileAppearance.invoke(drawable, "setPathProvider", state.nativeProvider);
                QsTileAppearance.invoke(drawable, "invalidatePath");
            }
            Object block = QsTileCorners.blockProvider(drawable);
            Object effective = block == null ? base : block;
            QsTileAppearance.invoke(drawable, "setCornerParams",
                    QsTileAppearance.invoke(effective, "getCornerRadius", drawable),
                    QsTileAppearance.invoke(effective, "getCornerWeight", drawable));
            // Continue observing until a native temporary path clears, so light and glass
            // also return to the static native shape when disabled during an animation.
            state.mediaLightOwned = block != null;
            drawable.invalidateSelf();
            state.customProvider = null; state.restorePending = false;
        } catch (ReflectiveOperationException | RuntimeException error) { success = false; unavailable(error); }
        if (state.blurOwned && Float.isFinite(state.nativeBlur)) try {
            state.writingBlur = true; state.restoringBlur = true;
            blurSetter(view, state).invoke(view, state.nativeBlur);
            state.blurOwned = false;
        } catch (ReflectiveOperationException | RuntimeException error) { success = false; unavailable(error); }
        finally { state.writingBlur = false; state.restoringBlur = false; }
        if (state.device != null) try {
            state.device.restore();
            View body = deviceBody(view);
            if (body != null) { body.invalidateOutline(); body.invalidate(); }
            invalidate(view);
            state.deviceOwned = false;
        } catch (ReflectiveOperationException | RuntimeException error) { success = false; unavailable(error); }
        return success;
    }

    private float pixels(View view, Drawable drawable) {
        Object bg = isMedia(view) ? QsTileAppearance.call(view, "getBg") : null;
        View body = bg instanceof View ? (View) bg : view;
        int width = body.getMeasuredWidth(), height = body.getMeasuredHeight();
        if (width <= 0) width = body.getWidth();
        if (height <= 0) height = body.getHeight();
        if ((width <= 0 || height <= 0) && drawable != null) {
            Rect bounds = drawable.getBounds(); width = bounds.width(); height = bounds.height();
        }
        return QsTileCorners.radiusPixels(dp, body.getResources().getDisplayMetrics().density, width, height);
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) try {
            Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static void invalidate(View view) { view.invalidate(); view.invalidateOutline(); }
    private void observe(View view, Owned state) {
        if (state.layout == null) {
            WeakReference<View> ref = new WeakReference<>(view);
            state.layout = (changed, l, t, r, b, ol, ot, or, ob) -> {
                View owner = ref.get();
                if (owner != null && (l != ol || t != ot || r != or || b != ob)) onNativeUpdate(owner);
            };
            view.addOnLayoutChangeListener(state.layout);
        }
        Object candidate = isMedia(view) ? QsTileAppearance.call(view, "getBg")
                : isDevice(view) ? deviceBody(view) : null;
        View body = candidate instanceof View && candidate != view ? (View) candidate : null;
        View old = state.body.get();
        if (old == body) return;
        if (old != null) old.removeOnLayoutChangeListener(state.layout);
        state.body = new WeakReference<>(body);
        if (body != null) body.addOnLayoutChangeListener(state.layout);
    }
    private static void stopObserving(View view, Owned state) {
        if (state.layout == null) return;
        view.removeOnLayoutChangeListener(state.layout);
        View body = state.body.get();
        if (body != null) body.removeOnLayoutChangeListener(state.layout);
    }
    private void runOnMain(Runnable work) {
        if (Looper.myLooper() == Looper.getMainLooper()) work.run(); else main.post(work);
    }
    private void applied(String kind, float target) {
        if (!logged) { logged = true; ModuleDiagnostics.info("qs_style", "Native " + kind + " panel corner geometry applied; boundedPx " + target); }
    }
    private void unavailable(Throwable error) {
        if (!warning) { warning = true; ModuleDiagnostics.error("qs_style", "Native panel corner update unavailable; original geometry retained", error); }
    }
}
