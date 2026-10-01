// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Draws the existing Phone left views during the last part of the native panel close. */
public final class StatusBarClosingIcons {
    private static final float RETURN_FRACTION = .2f;
    private static final float LIFT_DP = 8f;
    private final Map<View, SourceState> sources = new WeakHashMap<>();
    private final int[] rootLocation = new int[2], sourceLocation = new int[2];
    private final Rect bounds = new Rect();
    private final RectF layerBounds = new RectF(), nodeBounds = new RectF();
    private final Matrix[] descendantMatrices = new Matrix[20];
    private WeakReference<View> root = new WeakReference<>(null);
    private WeakReference<View> failedRoot = new WeakReference<>(null);
    private ClosingSlot slot = new ClosingSlot();
    private ViewTreeObserver observer;
    private Runnable failureListener;
    private float fraction = 1f, opacity;
    private boolean added, busy;
    private long generation;
    private int measuredNodes, warnings;

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        if (!added) return true;
        try {
            if (!position()) unavailable(null, true);
            else slot.invalidateSelf();
        } catch (Throwable error) { unavailable(error, true); }
        return true;
    };
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { unavailable(null, true); }
    };

    /** Restore the caller's source/copy overrides after an asynchronous drawing failure. */
    public void setFailureListener(Runnable listener) { failureListener = listener; }

    /** An explicit feature/configuration reset permits retrying a source that threw while drawing. */
    public void resetFailure() { failedRoot.clear(); }

    /**
     * Call once for each native Phone source (clock and notification area) with the same fraction.
     * The caller owns suppression of the real views and native fake copies, and calls hide()
     * when the native panel settles closed. This helper never changes a source View property.
     */
    public boolean show(View shadeRoot, View source, float nativeFraction) {
        if (shadeRoot == null || source == null || !finite(nativeFraction)) {
            release();
            return false;
        }
        if (failedRoot.get() == shadeRoot) return false;
        // An empty notification area is normal. It must not cancel the clock's return slot.
        if (source.getVisibility() == View.GONE || source.getWidth() <= 0 || source.getHeight() <= 0) {
            sources.remove(source);
            source.removeOnAttachStateChangeListener(attachment);
            return true;
        }
        if (root.get() != shadeRoot) {
            release();
            failedRoot.clear();
            root = new WeakReference<>(shadeRoot);
        }
        fraction = Math.max(0f, Math.min(1f, nativeFraction));
        try {
            if (!sources.containsKey(source)) {
                sources.put(source, new SourceState(source));
                source.addOnAttachStateChangeListener(attachment);
            }
            if (!position()) { release(); return false; }
            if (!added) {
                added = true;
                shadeRoot.getOverlay().add(slot);
                observer = shadeRoot.getViewTreeObserver();
                if (!observer.isAlive()) { release(); return false; }
                observer.addOnPreDrawListener(preDraw);
                shadeRoot.addOnAttachStateChangeListener(attachment);
            }
            slot.invalidateSelf();
            return true;
        } catch (Throwable error) { unavailable(error, false); return false; }
    }

    public void hide() { release(); }

    /** Rebinding one native area must retain the other area's current return animation. */
    public void removeSource(View source) {
        if (source == null) return;
        sources.remove(source);
        source.removeOnAttachStateChangeListener(attachment);
        if (sources.isEmpty()) release();
        else slot.invalidateSelf();
    }

    private boolean position() {
        View host = root.get();
        if (host == null || !host.isAttachedToWindow() || host.getWindowToken() == null
                || host.getDisplay() == null || host.getWidth() <= 0 || host.getHeight() <= 0
                || sources.isEmpty()) return false;
        host.getLocationOnScreen(rootLocation);
        float progress = Math.max(0f, Math.min(1f, fraction / RETURN_FRACTION));
        float hidden = progress * progress * (3f - 2f * progress);
        opacity = 1f - hidden;
        float density = host.getResources().getDisplayMetrics().density;
        float lift = LIFT_DP * density * hidden;
        layerBounds.setEmpty();
        measuredNodes = 0;
        for (SourceState state : sources.values()) {
            View source = state.view.get();
            // Phone and shade commonly use different windows; only the display must match.
            if (source == null || source == host || !source.isAttachedToWindow()
                    || source.getWindowToken() == null || source.getDisplay() == null
                    || source.getDisplay().getDisplayId() != host.getDisplay().getDisplayId()) return false;
            if (source.getVisibility() == View.GONE || source.getWidth() <= 0 || source.getHeight() <= 0) continue;
            source.getLocationOnScreen(sourceLocation);
            state.x = sourceLocation[0] - rootLocation[0];
            state.y = sourceLocation[1] - rootLocation[1] - lift;
            // Screen location already includes this View's translation and pivot offset.
            // Keep its native linear transform without adding that movement twice.
            state.linear.set(source.getMatrix());
            state.linear.getValues(state.values);
            state.values[Matrix.MTRANS_X] = state.values[Matrix.MTRANS_Y] = 0f;
            state.linear.setValues(state.values);
            Matrix mapping = matrixAt(0);
            mapping.setTranslate(state.x, state.y);
            mapping.preConcat(state.linear);
            includeDescendants(source, mapping, 0);
        }
        if (layerBounds.isEmpty()) return false;
        float outset = Math.max(2f, 4f * density);
        layerBounds.inset(-outset, -outset);
        if (!layerBounds.intersect(0f, 0f, host.getWidth(), host.getHeight())) return false;
        layerBounds.roundOut(bounds);
        if (!slot.getBounds().equals(bounds)) slot.setBounds(bounds);
        return true;
    }

    private Matrix matrixAt(int depth) {
        Matrix matrix = descendantMatrices[depth];
        if (matrix == null) descendantMatrices[depth] = matrix = new Matrix();
        return matrix;
    }

    private void includeDescendants(View view, Matrix matrix, int depth) {
        if (++measuredNodes > 256 || depth >= descendantMatrices.length - 1) return;
        nodeBounds.set(0f, 0f, view.getWidth(), view.getHeight());
        matrix.mapRect(nodeBounds);
        layerBounds.union(nodeBounds);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            Matrix childMatrix = matrixAt(depth + 1);
            childMatrix.set(matrix);
            childMatrix.preTranslate(child.getLeft() - group.getScrollX(), child.getTop() - group.getScrollY());
            childMatrix.preConcat(child.getMatrix());
            includeDescendants(child, childMatrix, depth + 1);
        }
    }

    private void unavailable(Throwable error, boolean notifyCaller) {
        View host = root.get();
        if (error != null) failedRoot = new WeakReference<>(host);
        release();
        if (error != null && warnings++ < 3) {
            try { ModuleDiagnostics.error("closing-icons", "Closing status icons unavailable; native views retained", error); }
            catch (Throwable ignored) { }
        }
        Runnable listener = failureListener;
        long failureGeneration = generation;
        if (notifyCaller && host != null && listener != null) try {
            host.post(() -> {
                if (generation != failureGeneration || added || failureListener != listener) return;
                try { listener.run(); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }

    private void release() {
        View host = root.get();
        boolean remove = added;
        generation++;
        added = false;
        ClosingSlot retired = slot;
        if (observer != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(preDraw); } catch (Throwable ignored) { }
            observer = null;
        }
        if (host != null) {
            try { host.removeOnAttachStateChangeListener(attachment); } catch (Throwable ignored) { }
            if (remove && busy) {
                slot = new ClosingSlot();
                try { host.post(() -> removeSlot(host, retired)); } catch (Throwable ignored) { }
            } else if (remove) removeSlot(host, retired);
        }
        for (SourceState state : new ArrayList<>(sources.values())) {
            View source = state.view.get();
            if (source != null) try { source.removeOnAttachStateChangeListener(attachment); }
            catch (Throwable ignored) { }
        }
        sources.clear();
        if (!remove || host == null) retired.setCallback(null);
        root.clear();
        fraction = 1f; opacity = 0f;
    }

    private void removeSlot(View host, ClosingSlot drawable) {
        try { host.getOverlay().remove(drawable); } catch (Throwable ignored) { }
        drawable.setCallback(null);
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    private static final class SourceState {
        final WeakReference<View> view;
        final Matrix linear = new Matrix();
        final float[] values = new float[9];
        float x, y;
        SourceState(View source) { view = new WeakReference<>(source); }
    }

    private final class ClosingSlot extends Drawable {
        @Override public void draw(Canvas canvas) {
            if (this != slot || !added || busy) return;
            int alpha = Math.round(opacity * 255f);
            if (alpha <= 0) return;
            busy = true;
            try {
                // Native state may detach a source during View.draw; use a bounded snapshot.
                for (SourceState state : new ArrayList<>(sources.values())) {
                    if (!added || this != slot) break;
                    View source = state.view.get();
                    if (source == null) { unavailable(null, true); break; }
                    if (source.getVisibility() == View.GONE || source.getWidth() <= 0 || source.getHeight() <= 0) continue;
                    int saved = alpha >= 255 ? canvas.save() : canvas.saveLayerAlpha(layerBounds, alpha);
                    try {
                        canvas.translate(state.x, state.y);
                        canvas.concat(state.linear);
                        // View.draw preserves live glyphs, colors and notification child state,
                        // while the native Phone window's parent alpha does not hide the copy.
                        source.draw(canvas);
                    } finally { canvas.restoreToCount(saved); }
                }
            } catch (Throwable error) { unavailable(error, true); }
            finally { busy = false; }
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
