// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Changes only the native 1x1 glyph dimension; tile, background, ratio and touch geometry stay native. */
public final class QsTileIconSize {
    public static final String MASTER = "qs_small_tile_icon_enabled";
    public static final String SIZE = "qs_small_tile_icon_size";
    public static final String TILE = "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne";
    public static final String ICON = "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSIconView";
    /** MAX_SIZE is the editor's suggested range, never a stored-value limit. */
    public static final float DEFAULT_SIZE = 28f, MAX_SIZE = 64f;
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    static {
        Map<String, Boolean> flags = new LinkedHashMap<>(); flags.put(MASTER, false);
        Map<String, Float> numbers = new LinkedHashMap<>(); numbers.put(SIZE, DEFAULT_SIZE);
        BOOLEANS = Collections.unmodifiableMap(flags); NUMBERS = Collections.unmodifiableMap(numbers);
    }
    private static final class Binding {
        final WeakReference<View> tile;
        final int entryId, switchId;
        final Method scale;
        float ratio = 1f;
        Binding(View tile, View icon) {
            this.tile = new WeakReference<>(tile);
            entryId = icon.getResources().getIdentifier("std_1x1_entry_icon_size", "dimen", "com.android.systemui");
            switchId = icon.getResources().getIdentifier("std_1x1_switch_icon_size", "dimen", "com.android.systemui");
            Method getter = null;
            try { getter = icon.getClass().getMethod("getIconScaleRatio"); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { }
            scale = getter;
            updateRatio(icon);
        }
        void updateRatio(View icon) {
            if (scale == null) return;
            try { ratio = QsTileAppearance.number(scale.invoke(icon), 1f); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { ratio = 1f; }
        }
    }
    private final Map<View, Binding> icons = new WeakHashMap<>();
    private final Map<View, WeakReference<View>> tiles = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final QsDeviceIconSize devices = new QsDeviceIconSize();
    private volatile boolean enabled;
    private volatile float dp = DEFAULT_SIZE;

    public static float sizeDp(Object value) {
        return Math.max(0f, QsTileAppearance.number(value, DEFAULT_SIZE));
    }

    public void configure(Bundle settings) {
        boolean next = settings != null && Boolean.TRUE.equals(settings.get(MASTER)) && !SafetyMode.enabled(settings);
        float size = sizeDp(settings == null ? null : settings.get(SIZE));
        if (enabled == next && dp == size) return;
        enabled = next; dp = size;
        runOnMain(() -> {
            devices.configure(next, size);
            for (View icon : new ArrayList<>(icons.keySet())) if (icon != null) icon.requestLayout();
        });
    }

    /** After native tile attachment, state/theme changes and cell constraint updates. */
    public void onNativeUpdate(View tile) {
        if (ModuleLifecycle.removed() || !QsTileAppearance.type(tile, TILE)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> target = new WeakReference<>(tile);
            main.post(() -> { View current = target.get(); if (current != null) onNativeUpdate(current); });
            return;
        }
        bind(tile, true);
    }

    /** Before the native icon's own measurement, not after its parent has already measured it.
     * This also discovers icons inflated while masters were off or recycled into an existing tile. */
    public void onIconMeasure(View icon) {
        if (ModuleLifecycle.removed() || !QsTileAppearance.type(icon, ICON)) return;
        Binding current = icons.get(icon);
        View tile = current == null ? null : current.tile.get();
        if (tile == null || !descendant(icon, tile)) {
            if (current != null) {
                icons.remove(icon);
                WeakReference<View> previous = tile == null ? null : tiles.get(tile);
                if (previous != null && previous.get() == icon) tiles.remove(tile);
            }
            ViewParent parent = icon.getParent();
            for (int depth = 0; parent instanceof View && depth < 8; depth++) {
                View owner = (View) parent;
                if (QsTileAppearance.type(owner, TILE)) { bind(owner, false); break; }
                parent = owner.getParent();
            }
            current = icons.get(icon);
        }
        if (current != null) current.updateRatio(icon);
    }

    private void bind(View tile, boolean requestLayout) {
        Object candidate = QsTileAppearance.call(tile, "getIconView");
        View icon = candidate instanceof View && QsTileAppearance.type(candidate, ICON) ? (View) candidate : null;
        WeakReference<View> previous = tiles.get(tile);
        View retired = previous == null ? null : previous.get();
        if (retired == icon && icon != null) return;
        if (retired != null) { icons.remove(retired); retired.requestLayout(); }
        if (icon == null) { tiles.remove(tile); return; }
        tiles.put(tile, new WeakReference<>(icon)); icons.put(icon, new Binding(tile, icon));
        if (enabled && requestLayout) icon.requestLayout();
    }

    /** After native dimen(int), only these two glyph resources of an explicitly bound 1x1 tile. */
    public int dimension(View icon, int resourceId, int nativePixels) {
        if (!enabled || ModuleLifecycle.removed()) return nativePixels;
        Binding binding = icons.get(icon);
        View tile = binding == null ? null : binding.tile.get();
        if (tile == null || !tile.isAttachedToWindow() || !icon.isAttachedToWindow()
                || icon.getRootView() != tile.getRootView()
                || resourceId == 0 || resourceId != binding.entryId && resourceId != binding.switchId)
            return nativePixels;
        float density = icon.getResources().getDisplayMetrics().density;
        if (!Float.isFinite(density) || density <= 0f) return nativePixels;
        double pixels = (double) dp * density;
        float ratio = binding.ratio;
        int width = icon.getMeasuredWidth(), height = icon.getMeasuredHeight();
        if (Float.isFinite(ratio) && ratio > 0f) {
            double extent = width > 0 && height > 0
                    ? Math.min(width, height) : NumericPolicy.MAX_LAYOUT_PIXELS;
            pixels = Math.min(pixels, Math.min(extent, NumericPolicy.MAX_LAYOUT_PIXELS) / ratio);
        }
        return NumericPolicy.layoutPixels(pixels);
    }

    /** No native value was written: restoring the original measurement needs only a new layout. */
    public void detach(View tile) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> target = new WeakReference<>(tile);
            main.post(() -> { View current = target.get(); if (current != null) detach(current); });
            return;
        }
        WeakReference<View> binding = tiles.remove(tile);
        View icon = binding == null ? null : binding.get();
        if (icon != null) { icons.remove(icon); icon.requestLayout(); }
    }
    public void onDeviceUpdate(View card) {
        if (Looper.myLooper() == Looper.getMainLooper()) devices.update(card);
        else { WeakReference<View> target = new WeakReference<>(card);
            main.post(() -> { View current = target.get(); if (current != null) devices.update(current); }); }
    }
    public void onDeviceGlyphUpdate(View holder) {
        if (Looper.myLooper() == Looper.getMainLooper()) devices.updateGlyphParent(holder);
        else { WeakReference<View> target = new WeakReference<>(holder);
            main.post(() -> { View current = target.get(); if (current != null) devices.updateGlyphParent(current); }); }
    }
    public void detachDevice(View card) {
        if (Looper.myLooper() == Looper.getMainLooper()) devices.detach(card);
        else { WeakReference<View> target = new WeakReference<>(card);
            main.post(() -> { View current = target.get(); if (current != null) devices.detach(current); }); }
    }
    public android.view.ViewGroup.LayoutParams deviceLayout(View glyph, android.view.ViewGroup.LayoutParams params) {
        return devices.nativeLayout(glyph, params);
    }
    public float deviceAlpha(View glyph, float nativeAlpha) { return devices.nativeAlpha(glyph, nativeAlpha); }
    private void runOnMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run(); else main.post(task);
    }

    public void releaseRuntime() {
        enabled = false;
        runOnMain(() -> { devices.release(); icons.clear(); tiles.clear(); });
    }

    private static boolean descendant(View icon, View tile) {
        ViewParent parent = icon.getParent();
        for (int depth = 0; parent instanceof View && depth < 8; depth++) {
            if (parent == tile) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
