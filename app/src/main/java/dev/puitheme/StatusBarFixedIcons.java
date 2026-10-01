// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Keeps native Phone/QS icon appearance in one bounded, touch-free slot outside the pager. */
public final class StatusBarFixedIcons {
    private static final Method TRANSITION_ALPHA = transitionAlphaMethod();
    private WeakReference<View> root = new WeakReference<>(null);
    private WeakReference<View> source = new WeakReference<>(null);
    private WeakReference<View> anchor = new WeakReference<>(null);
    private WeakReference<View> transitionCopy = new WeakReference<>(null);
    private WeakReference<View> failedRoot = new WeakReference<>(null);
    private WeakReference<View> failedSource = new WeakReference<>(null);
    private WeakReference<View> failedAnchor = new WeakReference<>(null);
    private final int[] rootLocation = new int[2], phoneLocation = new int[2];
    private final Rect target = new Rect(), viewport = new Rect(), sourceBounds = new Rect(), phoneBounds = new Rect();
    private final RectF layerBounds = new RectF(), nodeBounds = new RectF();
    private final Matrix[] descendantMatrices = new Matrix[20];
    private final Paint additivePaint = additivePaint();
    private IconSlot slot = new IconSlot();
    private ViewTreeObserver observer;
    private Runnable failureListener;
    private boolean added, busy;
    private int warnings;
    private int measuredNodes;
    private long generation;
    private float nativeSourceAlpha = 1f, nativeCopyAlpha = 1f, sourceOpacity = 1f, phoneOpacity;
    private boolean transitionReadFailed;

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        if (!added) return true;
        try {
            if (!position()) {
                unavailable(null);
                return true;
            }
            updateAppearance();
            // Source child/text/color changes already schedule this traversal. Mark the overlay
            // dirty inside that traversal so it redraws too; no timer or next-frame ticker exists.
            slot.invalidateSelf();
        } catch (Throwable error) { unavailable(error); }
        return true;
    };
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { unavailable(null); }
    };

    /** Optional notification, posted once after a failed frame so native properties can be restored. */
    public void setFailureListener(Runnable listener) { failureListener = listener; }

    /**
     * Pass the native alpha snapshots before suppressing the QS source and its transition copy.
     * Transition alpha remains native-owned and is sampled during the root's real draw traversal.
     * The copy supplies Phone-to-QS handoff timing; its translated geometry is never replayed.
     */
    public void setNativeAppearance(View qsSource, float sourceAlpha, View fakeCopy, float copyAlpha) {
        if (!added || source.get() != qsSource) return;
        nativeSourceAlpha = unit(sourceAlpha, 1f);
        nativeCopyAlpha = unit(copyAlpha, 1f);
        if (transitionCopy.get() != fakeCopy) transitionCopy = new WeakReference<>(fakeCopy);
        updateAppearance();
        slot.invalidateSelf();
    }

    /** Allows an explicit feature/configuration reset to retry a source that previously threw. */
    public void resetFailure() {
        failedRoot.clear(); failedSource.clear(); failedAnchor.clear();
        transitionReadFailed = false;
    }

    /** Returns false before taking a slot when geometry/window/source is unavailable. */
    public boolean show(View shadeRoot, View qsSource, View phoneAnchor) {
        if (shadeRoot == null || qsSource == null || phoneAnchor == null) {
            release();
            return false;
        }
        if (failedRoot.get() == shadeRoot && failedSource.get() == qsSource
                && failedAnchor.get() == phoneAnchor) return false;
        if (root.get() != shadeRoot || source.get() != qsSource || anchor.get() != phoneAnchor) {
            release();
            failedRoot.clear(); failedSource.clear(); failedAnchor.clear();
            root = new WeakReference<>(shadeRoot);
            source = new WeakReference<>(qsSource);
            anchor = new WeakReference<>(phoneAnchor);
        }
        try {
            if (!position()) { release(); return false; }
            if (!added) {
                // Set first so cleanup also handles an overlay implementation that adds then fails.
                added = true;
                shadeRoot.getOverlay().add(slot);
                observer = shadeRoot.getViewTreeObserver();
                if (!observer.isAlive()) { release(); return false; }
                observer.addOnPreDrawListener(preDraw);
                shadeRoot.addOnAttachStateChangeListener(attachment);
                qsSource.addOnAttachStateChangeListener(attachment);
                phoneAnchor.addOnAttachStateChangeListener(attachment);
                slot.invalidateSelf();
            }
            return true;
        } catch (Throwable error) {
            unavailable(error);
            return false;
        }
    }

    /** Releases the slot and observers. A failing source stays latched until replacement or resetFailure(). */
    public void hide() {
        release();
    }

    private boolean position() {
        View host = root.get(), icons = source.get(), phone = anchor.get();
        if (host == null || icons == null || phone == null || host == icons || host == phone
                || !host.isAttachedToWindow() || !icons.isAttachedToWindow() || !phone.isAttachedToWindow()
                || host.getWindowToken() == null || host.getWindowToken() != icons.getWindowToken()
                || host.getDisplay() == null || phone.getDisplay() == null
                || host.getDisplay().getDisplayId() != phone.getDisplay().getDisplayId()) return false;
        int width = icons.getWidth(), height = icons.getHeight();
        if (width <= 0 || height <= 0 || phone.getWidth() <= 0 || phone.getHeight() <= 0
                || host.getWidth() <= 0 || host.getHeight() <= 0
                || width > host.getWidth() || height > host.getHeight()) return false;
        boolean insideHost = false;
        for (View view = icons; view != null;
                view = view.getParent() instanceof View ? (View) view.getParent() : null) {
            if (view == host) { insideHost = true; break; }
        }
        if (!insideHost) return false;
        // These views can belong to different windows. Screen positions on both axes avoid
        // mixing the native fake frame's screen-X getter with its layout-only Y getter.
        phone.getLocationOnScreen(phoneLocation);
        host.getLocationOnScreen(rootLocation);
        int left = phone.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? phoneLocation[0] - rootLocation[0]
                : phoneLocation[0] - rootLocation[0] + phone.getWidth() - width;
        int top = Math.round(phoneLocation[1] - rootLocation[1] + (phone.getHeight() - height) * .5f);
        sourceBounds.set(left, top, left + width, top + height);
        phoneBounds.set(phoneLocation[0] - rootLocation[0], phoneLocation[1] - rootLocation[1],
                phoneLocation[0] - rootLocation[0] + phone.getWidth(),
                phoneLocation[1] - rootLocation[1] + phone.getHeight());
        target.set(sourceBounds);
        target.union(phoneBounds);
        viewport.set(0, 0, host.getWidth(), host.getHeight());
        if (!Rect.intersects(target, viewport)) return false;
        // Native child translations (including user icon offsets) may extend beyond either
        // group's natural dimensions. Include those descendants in dirty/layer bounds.
        layerBounds.set(target);
        measuredNodes = 0;
        Matrix matrix = matrixAt(0);
        matrix.setTranslate(sourceBounds.left, sourceBounds.top);
        includeDescendants(icons, matrix, 0);
        matrix.setTranslate(phoneBounds.left, phoneBounds.top);
        includeDescendants(phone, matrix, 0);
        float outset = Math.max(2f, host.getResources().getDisplayMetrics().density * 4f);
        layerBounds.inset(-outset, -outset);
        // Offscreen user/native translations must not allocate an oversized alpha layer.
        // This is the host's existing viewport, not a new boundary around the source group.
        if (!layerBounds.intersect(0f, 0f, host.getWidth(), host.getHeight())) return false;
        layerBounds.roundOut(target);
        if (!slot.getBounds().equals(target)) slot.setBounds(target);
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
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            Matrix childMatrix = matrixAt(depth + 1);
            childMatrix.set(matrix);
            childMatrix.preTranslate(child.getLeft() - group.getScrollX(), child.getTop() - group.getScrollY());
            if (!child.getMatrix().isIdentity()) childMatrix.preConcat(child.getMatrix());
            includeDescendants(child, childMatrix, depth + 1);
        }
    }

    private static Method transitionAlphaMethod() {
        try {
            Method method = View.class.getDeclaredMethod("getTransitionAlpha");
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) { return null; }
    }

    private void updateAppearance() {
        View icons = source.get(), copy = transitionCopy.get();
        View host = root.get();
        // Keeping only the QS source is the established fallback when there is no trustworthy
        // native handoff. Do not infer native alpha from our alpha-zero suppression writes.
        sourceOpacity = 1f;
        phoneOpacity = 0f;
        if (icons == null || copy == null || host == null || !copy.isAttachedToWindow()
                || copy.getWindowToken() != host.getWindowToken()
                || TRANSITION_ALPHA == null || transitionReadFailed) return;
        try {
            Object qs = TRANSITION_ALPHA.invoke(icons), fake = TRANSITION_ALPHA.invoke(copy);
            if (!(qs instanceof Number) || !(fake instanceof Number)) return;
            float qsAlpha = ((Number) qs).floatValue(), copyAlpha = ((Number) fake).floatValue();
            if (Float.isNaN(qsAlpha) || Float.isInfinite(qsAlpha)
                    || Float.isNaN(copyAlpha) || Float.isInfinite(copyAlpha)) return;
            sourceOpacity = nativeSourceAlpha * unit(qsAlpha, 1f);
            phoneOpacity = nativeCopyAlpha * unit(copyAlpha, 0f);
        } catch (Throwable error) {
            transitionReadFailed = true;
            if (warnings++ < 3) {
                try { ModuleDiagnostics.error("fixed-icons", "Native icon opacity unavailable; QS appearance retained", error); }
                catch (Throwable ignored) { }
            }
        }
    }

    private static float unit(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(0f, Math.min(1f, value));
    }

    private static Paint additivePaint() {
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        return paint;
    }

    private void unavailable(Throwable error) {
        View host = root.get(), icons = source.get();
        if (host == null && !added) return;
        // A temporary size/window change may recover on a later native event. Only a real draw
        // or overlay exception latches this source until explicit reset or replacement.
        if (error != null) {
            failedRoot = new WeakReference<>(host);
            failedSource = new WeakReference<>(icons);
            failedAnchor = new WeakReference<>(anchor.get());
        }
        release();
        if (error != null && warnings++ < 3) {
            try { ModuleDiagnostics.error("fixed-icons", "Fixed status icons unavailable; native icons retained", error); }
            catch (Throwable ignored) { }
        }
        Runnable listener = failureListener;
        long failureGeneration = generation;
        if (host != null && listener != null) {
            try {
                host.post(() -> {
                    if (generation != failureGeneration || added || failureListener != listener) return;
                    try { listener.run(); } catch (Throwable ignored) { }
                });
            } catch (Throwable ignored) { }
        }
    }

    private void release() {
        View host = root.get(), icons = source.get(), phone = anchor.get();
        boolean remove = added;
        generation++;
        added = false;
        IconSlot retired = slot;
        if (observer != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(preDraw); } catch (Throwable ignored) { }
            observer = null;
        }
        if (host != null) {
            try { host.removeOnAttachStateChangeListener(attachment); } catch (Throwable ignored) { }
            if (remove && busy) {
                // ViewOverlay can be iterating its drawable list right now. Retire this exact
                // drawable first, then remove it once that iteration has returned. A new
                // session gets a separate slot so delayed cleanup cannot remove its icons.
                slot = new IconSlot();
                try { host.post(() -> removeSlot(host, retired)); } catch (Throwable ignored) { }
            } else if (remove) removeSlot(host, retired);
        }
        if (icons != null) try { icons.removeOnAttachStateChangeListener(attachment); } catch (Throwable ignored) { }
        if (phone != null) try { phone.removeOnAttachStateChangeListener(attachment); } catch (Throwable ignored) { }
        if (!remove || host == null) retired.setCallback(null);
        root.clear(); source.clear(); anchor.clear();
        transitionCopy.clear();
        nativeSourceAlpha = nativeCopyAlpha = sourceOpacity = 1f;
        phoneOpacity = 0f;
    }

    private void removeSlot(View host, IconSlot drawable) {
        try { host.getOverlay().remove(drawable); } catch (Throwable ignored) { }
        drawable.setCallback(null);
    }

    private final class IconSlot extends Drawable {
        @Override public void draw(Canvas canvas) {
            View icons = source.get();
            if (this != slot || !added || busy || icons == null) return;
            busy = true;
            try {
                View phone = anchor.get();
                // Each group's live tint, child animations and natural dimensions remain native.
                // Only its outer movement is replaced. The native handoff fades Phone tint into
                // QS background tint at the same edge/center, without a new animator or timer.
                float qsWeight = sourceOpacity, phoneWeight = phone == null ? 0f : phoneOpacity;
                float total = qsWeight + phoneWeight;
                if (total > 1f) { qsWeight /= total; phoneWeight /= total; }
                if (phoneWeight > 0f && qsWeight > 0f) {
                    // Add weighted premultiplied pixels on a transparent icon-sized layer.
                    // Ordinary source-over would dim a .5/.5 handoff to .75 alpha; adding
                    // onto the actual window background would corrupt its colors instead.
                    int saved = canvas.saveLayer(layerBounds, null);
                    try {
                        drawGroup(canvas, phone, phoneBounds, phoneWeight, false);
                        if (added && this == slot) drawGroup(canvas, icons, sourceBounds, qsWeight, true);
                    } finally { canvas.restoreToCount(saved); }
                } else {
                    if (phoneWeight > 0f) drawGroup(canvas, phone, phoneBounds, phoneWeight, false);
                    if (added && this == slot && qsWeight > 0f) drawGroup(canvas, icons, sourceBounds, qsWeight, false);
                }
            } catch (Throwable error) { unavailable(error); }
            finally { busy = false; }
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private void drawGroup(Canvas canvas, View view, Rect position, float opacity, boolean additive) {
        int alpha = Math.round(unit(opacity, 1f) * 255f);
        if (alpha <= 0) return;
        int saved;
        if (additive) {
            additivePaint.setAlpha(alpha);
            saved = canvas.saveLayer(layerBounds, additivePaint);
        } else saved = alpha >= 255 ? canvas.save() : canvas.saveLayerAlpha(layerBounds, alpha);
        try {
            canvas.translate(position.left, position.top);
            // No artificial source-size clip: the bounded layer includes native descendant
            // overflow, and the host canvas retains its own native viewport clipping.
            view.draw(canvas);
        } finally { canvas.restoreToCount(saved); }
    }
}
