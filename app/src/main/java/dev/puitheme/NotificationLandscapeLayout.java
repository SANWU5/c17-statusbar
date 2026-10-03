// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps the real native notification column at its portrait width; never scales notification ink. */
final class NotificationLandscapeLayout {
    private static final Object[] NO_ARGUMENTS = new Object[0];
    private final Map<View, State> states = new WeakHashMap<>();
    private static final class State {
        int portraitWidth, nativeLeft, appliedLeft;
        int nativeSpec, appliedSpec, columnWidth, shortSide, longSide, density, orientation;
        int sidePadding;
        boolean measured, paddingResolved;
        Method paddingMethod;
        boolean owned;
    }
    int measure(View stack, int widthSpec, boolean managed) {
        return measure(stack,widthSpec,managed,100f);
    }
    int measure(View stack, int widthSpec, boolean managed,float percentage) {
        State state = states.get(stack);
        if (state == null) { state = new State(); states.put(stack, state); }
        android.util.DisplayMetrics metrics = stack.getResources().getDisplayMetrics();
        int shortSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        int longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        if (state.shortSide != shortSide || state.longSide != longSide || state.density != metrics.densityDpi) {
            state.portraitWidth = state.nativeSpec = state.appliedSpec = state.columnWidth = 0;
            state.measured = false;
            state.shortSide = shortSide; state.longSide = longSide; state.density = metrics.densityDpi;
        }
        int orientation = stack.getResources().getConfiguration().orientation;
        // A remeasure may echo our last constrained spec. Never make that output the
        // next native input (50% -> 25% -> 12.5%), or a portrait-width baseline on rotation.
        boolean echoed = state.measured && widthSpec == state.appliedSpec && state.appliedSpec != state.nativeSpec;
        int nativeWidth = View.MeasureSpec.getSize(widthSpec);
        if (orientation != Configuration.ORIENTATION_LANDSCAPE) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT && nativeWidth > 0
                    && nativeWidth <= Math.max(1, shortSide) && !(echoed && state.portraitWidth > nativeWidth))
                state.portraitWidth = nativeWidth;
            state.orientation = orientation; state.columnWidth = 0; state.measured = false;
            readPadding(stack, state);
            return widthSpec;
        }
        int nativeSpec = echoed && state.orientation == orientation ? state.nativeSpec : widthSpec;
        nativeWidth = View.MeasureSpec.getSize(nativeSpec);
        state.nativeSpec = nativeSpec; state.orientation = orientation;
        readPadding(stack, state);
        if (!managed || nativeWidth <= 0) {
            state.columnWidth = 0; state.appliedSpec = nativeSpec; state.measured = false;
            return nativeSpec;
        }
        int portrait = state.portraitWidth > 0 ? state.portraitWidth
                : shortSide;
        int width = width(nativeWidth, portrait,percentage);
        state.columnWidth = width;
        state.appliedSpec = width == nativeWidth ? nativeSpec : View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        state.measured = true;
        return state.appliedSpec;
    }
    /** Native NSSL measures every child from this column minus 2*mSidePaddings.
     * A grouped row's animated getWidth() is not a measurement source for the clock. */
    int columnWidth(View stack, int available, float percentage) {
        State state = states.get(stack);
        android.util.DisplayMetrics metrics = stack.getResources().getDisplayMetrics();
        boolean sameDisplay = state != null && state.shortSide == Math.min(metrics.widthPixels, metrics.heightPixels)
                && state.longSide == Math.max(metrics.widthPixels, metrics.heightPixels) && state.density == metrics.densityDpi;
        if (sameDisplay && state.measured && state.orientation == Configuration.ORIENTATION_LANDSCAPE)
            return Math.min(Math.max(1, available), state.columnWidth);
        int portrait = sameDisplay && state.portraitWidth > 0 ? state.portraitWidth
                : Math.min(metrics.widthPixels, metrics.heightPixels);
        return width(available, portrait, percentage);
    }
    int sidePadding(View stack, int fallback) {
        State state = states.get(stack);
        return state != null && state.paddingMethod != null ? state.sidePadding : Math.max(0, fallback);
    }
    int columnLeft(View stack, int width) {
        View parent = stack.getParent() instanceof View ? (View) stack.getParent() : null;
        return parent != null && parent.getWidth() > 0
                ? center(parent.getWidth(), parent.getPaddingLeft(), parent.getPaddingRight(), width) : stack.getLeft();
    }
    private static void readPadding(View stack, State state) {
        if (!state.paddingResolved) {
            state.paddingResolved = true;
            try { state.paddingMethod = stack.getClass().getMethod("getSidePaddings"); state.paddingMethod.setAccessible(true); }
            catch (ReflectiveOperationException ignored) { }
        }
        if (state.paddingMethod != null) try {
            Object value = state.paddingMethod.invoke(stack, NO_ARGUMENTS);
            if (value instanceof Number) state.sidePadding = Math.max(0, ((Number) value).intValue());
        } catch (ReflectiveOperationException ignored) { state.paddingMethod = null; }
    }
    void layout(View stack, boolean managed) {
        State state = states.get(stack);
        if (state == null) return;
        if (!managed || !(stack.getParent() instanceof ViewGroup)) { restore(stack, state); return; }
        View parent = (View) stack.getParent();
        if (parent.getWidth() <= 0 || stack.getWidth() <= 0) return;
        if (!state.owned || stack.getLeft() != state.appliedLeft) state.nativeLeft = stack.getLeft();
        int left = center(parent.getWidth(), parent.getPaddingLeft(), parent.getPaddingRight(), stack.getWidth());
        state.appliedLeft = left; state.owned = true;
        if (left != stack.getLeft()) stack.offsetLeftAndRight(left - stack.getLeft());
    }
    void release(View stack) {
        if (stack == null) return;
        State state = states.get(stack);
        if (state != null) restore(stack, state);
        stack.requestLayout();
    }
    void detach(View stack) { release(stack); states.remove(stack); }
    private static void restore(View stack, State state) {
        if (state.owned && stack.getLeft() == state.appliedLeft)
            stack.offsetLeftAndRight(state.nativeLeft - stack.getLeft());
        state.owned = false;
    }
    static int width(int available, int portrait) { return Math.max(1, Math.min(Math.max(1, available), Math.max(1, portrait))); }
    /** Measure the real column; changing its width must never scale text, icons or touch targets. */
    static int width(int available,int portrait,float percentage) {
        double ratio=Float.isNaN(percentage)||Float.isInfinite(percentage)?1d:Math.max(0d,percentage)/100d;
        double requested=Math.max(1,portrait)*ratio;
        return Math.max(1,(int)Math.min(Math.max(1,available),Math.min(Integer.MAX_VALUE,Math.round(requested))));
    }
    static int center(int parent, int left, int right, int column) {
        int usable = Math.max(0, parent - Math.max(0, left) - Math.max(0, right));
        return Math.max(0, left) + Math.max(0, usable - column) / 2;
    }
}
