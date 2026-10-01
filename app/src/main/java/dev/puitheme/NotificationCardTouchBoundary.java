// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;

/** Resolves a native touch against the pixels of a real notification, without owning the gesture. */
final class NotificationCardTouchBoundary {
    private final Method hit, height, clipTop, clipBottom;
    private final Class<?> rowType;
    private final Matrix matrix = new Matrix();
    private final float[] global = new float[2], local = new float[2];
    private final Rect clip = new Rect();

    NotificationCardTouchBoundary(Class<?> stack, Class<?> row) throws ReflectiveOperationException {
        rowType = row;
        hit = stack.getDeclaredMethod("getChildAtPosition", Float.TYPE, Boolean.TYPE, Boolean.TYPE, Float.TYPE);
        hit.setAccessible(true);
        height = row.getMethod("getActualHeight");
        clipTop = row.getMethod("getClipTopAmount");
        clipBottom = row.getMethod("getClipBottomAmount");
    }

    boolean contains(View stack, View origin, float x, float y, float hardTop)
            throws ReflectiveOperationException {
        if (stack == null || origin == null || !finite(x) || !finite(y) || !finite(hardTop)
                || !stack.isAttachedToWindow() || stack.getRootView() != origin.getRootView()) return false;
        global[0] = x; global[1] = y;
        matrix.reset(); origin.transformMatrixToGlobal(matrix); matrix.mapPoints(global);
        mapTo(stack);
        if (!finite(local[0]) || !finite(local[1]) || local[1] < hardTop) return false;
        // Use SystemUI's real row ordering/group selection. Thin visible tail edges are included;
        // decor, shelf and media cannot turn into a synthetic notification touch target.
        Object value = hit.invoke(stack, local[0], false, true, local[1]);
        if (!(value instanceof View) || !rowType.isInstance(value)) return false;
        View card = (View) value;
        if (!inside(card, true)) return false;
        boolean foundStack = card == stack;
        View child = card;
        for (int depth = 0; depth < 32 && child.getParent() instanceof View; depth++) {
            View parent = (View) child.getParent();
            // Explicit clip bounds always apply. Ordinary ancestor rectangles only clip when
            // the native parent actually clips children; unclipped clock/page wrappers stay free.
            boolean clipsChildren = parent instanceof ViewGroup && ((ViewGroup) parent).getClipChildren();
            if (!inside(parent, clipsChildren || rowType.isInstance(parent))) return false;
            if (parent instanceof ViewGroup && ((ViewGroup) parent).getClipToPadding()
                    && (parent.getPaddingLeft() != 0 || parent.getPaddingTop() != 0
                        || parent.getPaddingRight() != 0 || parent.getPaddingBottom() != 0)) {
                if (local[0] < parent.getPaddingLeft() || local[0] >= parent.getWidth() - parent.getPaddingRight()
                        || local[1] < parent.getPaddingTop() || local[1] >= parent.getHeight() - parent.getPaddingBottom())
                    return false;
            }
            foundStack |= parent == stack;
            child = parent;
        }
        return foundStack;
    }

    private boolean inside(View view, boolean bounds) throws ReflectiveOperationException {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return false;
        mapTo(view);
        float x = local[0], y = local[1];
        if (!finite(x) || !finite(y)) return false;
        if (bounds) {
            float top = 0f, bottom = view.getHeight();
            if (rowType.isInstance(view)) {
                top = Math.max(0, ((Number) clipTop.invoke(view)).intValue());
                bottom = ((Number) height.invoke(view)).intValue()
                        - Math.max(0, ((Number) clipBottom.invoke(view)).intValue());
            }
            if (x < 0f || x >= view.getWidth() || y < top || y >= bottom) return false;
        }
        return !view.getClipBounds(clip) || x >= clip.left && x < clip.right && y >= clip.top && y < clip.bottom;
    }

    private void mapTo(View view) {
        local[0] = global[0]; local[1] = global[1];
        matrix.reset(); view.transformMatrixToLocal(matrix); matrix.mapPoints(local);
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
