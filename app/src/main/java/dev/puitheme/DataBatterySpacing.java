// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Owns only the native battery's leading margin at an exact cellular/battery boundary. */
final class DataBatterySpacing {
    static final String ENABLED = "data_no_network_gap_enabled";
    static final String SPACING = "data_no_network_battery_spacing";
    private final Handler main;
    private final Map<ViewGroup, Boundary> boundaries = new WeakHashMap<>();
    private final Map<View, Boundary> batteries = new WeakHashMap<>();
    private final Map<View, Boolean> mobiles = new WeakHashMap<>();
    private final Map<View, String> slots = new WeakHashMap<>();
    private final Map<Object, Label> labels = new WeakHashMap<>();
    private Boolean wifiVisible, labelVisible;
    private int labelSources;
    private boolean enabled, applying, pending, removed;
    private boolean labelEnabled, labelHidden, labelNormalize, nativeBadgeHidden;
    private boolean wifiInUse, airplane;
    private Boolean wifiEnabled, dataEnabled;
    private String fallback = "";
    private float spacing;
    private final Runnable refresh = () -> {
        pending = false;
        if (removed) return;
        for (Map.Entry<ViewGroup, Boundary> entry : boundaries.entrySet()) apply(entry.getKey(), entry.getValue());
    };
    private static final class Boundary {
        final WeakReference<View> battery;
        final WeakReference<ViewGroup> parent;
        final WeakReference<View> wrapper;
        final List<WeakReference<ViewGroup>> pathParents = new ArrayList<>();
        final List<WeakReference<View>> pathChildren = new ArrayList<>(), trailing = new ArrayList<>();
        final List<Integer> pathIndices = new ArrayList<>(), pathCounts = new ArrayList<>();
        WeakReference<View> last = new WeakReference<>(null);
        final int batteryIndex;
        int nativeStart, appliedStart;
        boolean owned, measuredWifi, relative, appliedRtl;
        Boolean wifi;
        Boundary(ViewGroup container, View battery, View wrapper, ViewGroup parent, int batteryIndex) {
            this.battery = new WeakReference<>(battery); this.parent = new WeakReference<>(parent);
            this.wrapper = new WeakReference<>(wrapper);
            this.batteryIndex = batteryIndex;
            ViewGroup.LayoutParams params = battery.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                relative = margins.isMarginRelative(); appliedRtl = battery.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                nativeStart = readStart(margins, relative, appliedRtl);
            }
            for (View child = container; child != wrapper && child.getParent() instanceof ViewGroup;) {
                ViewGroup group = (ViewGroup) child.getParent();
                int index = childIndex(group, child);
                pathParents.add(new WeakReference<>(group)); pathChildren.add(new WeakReference<>(child));
                pathIndices.add(index); pathCounts.add(group.getChildCount());
                for (int i = index + 1; i < group.getChildCount(); i++) trailing.add(new WeakReference<>(group.getChildAt(i)));
                child = group;
            }
        }
    }
    private static final class Label {
        final WeakReference<Object> info;
        final boolean nullInfo;
        final String nativeText;
        Label(Object info, String nativeText) {
            this.info = new WeakReference<>(info); this.nullInfo = info == null;
            this.nativeText = nativeText;
        }
        boolean same(Object next) {
            return next == null ? nullInfo : !nullInfo && info.get() == next;
        }
    }
    DataBatterySpacing(Handler main) { this.main = main; }

    void configure(Bundle settings) {
        if (removed) return;
        FeatureOptions options = FeatureOptions.from(settings);
        boolean nextEnabled = options.effective("data", ENABLED);
        float nextSpacing = NumericPolicy.setting(SPACING, settings == null ? null : settings.get(SPACING), 0f);
        boolean nextLabelEnabled = options.enabled("label"), nextHidden = options.hideNetworkLabel();
        boolean nextNormalize = options.effective("label", "label_normalize_enabled");
        boolean nextNativeHidden = options.hideNativeNetworkBadge();
        boolean layoutChanged = enabled != nextEnabled || spacing != nextSpacing;
        enabled = nextEnabled; spacing = nextSpacing;
        if (labelEnabled != nextLabelEnabled || labelHidden != nextHidden || labelNormalize != nextNormalize
                || nativeBadgeHidden != nextNativeHidden) {
            labelEnabled = nextLabelEnabled; labelHidden = nextHidden; labelNormalize = nextNormalize;
            nativeBadgeHidden = nextNativeHidden;
            refreshLabels();
        }
        if (layoutChanged) refreshSoon();
    }
    void radioChanged(Boolean visible, boolean inUse, boolean airplane, Boolean wifiEnabled, Boolean dataEnabled) {
        if (removed) return;
        boolean markerChanged = !Objects.equals(wifiVisible, visible);
        boolean policyChanged = wifiInUse != inUse || this.airplane != airplane
                || !Objects.equals(this.wifiEnabled, wifiEnabled) || !Objects.equals(this.dataEnabled, dataEnabled);
        if (!markerChanged && !policyChanged) return;
        wifiVisible = visible; wifiInUse = inUse; this.airplane = airplane;
        this.wifiEnabled = wifiEnabled; this.dataEnabled = dataEnabled;
        if (policyChanged) refreshLabels();
        if (markerChanged) refreshSoon();
    }
    void dataChanged(Boolean dataEnabled) {
        if (removed || Objects.equals(this.dataEnabled, dataEnabled)) return;
        this.dataEnabled = dataEnabled; refreshLabels();
    }
    void fallbackChanged(String fallback) {
        String next = fallback == null ? "" : fallback;
        if (removed || this.fallback.equals(next)) return;
        this.fallback = next; refreshLabels();
    }
    /** The immutable native text model is decoded only on a real model/policy change. */
    boolean labelKnown(Object owner, Object info) {
        Label label = labels.get(owner);
        return !removed && label != null && label.same(info) && labels.size() == labelSources;
    }
    /** null means unreadable; an empty native string is a positively known absent marker. */
    void labelChanged(Object owner, Object info, String nativeText) {
        if (removed || owner == null) return;
        Label previous = labels.get(owner);
        if (previous != null && previous.same(info) && Objects.equals(previous.nativeText, nativeText)
                && labels.size() == labelSources) return;
        labels.put(owner, new Label(info, nativeText));
        refreshLabels();
    }
    private void refreshLabels() {
        labelSources = labels.size();
        Boolean next = labelSources == 0 ? null : Boolean.FALSE;
        for (Label label : labels.values()) {
            if (label.nativeText == null && (labelEnabled || !nativeBadgeHidden)) { next = null; break; }
            String shown = labelEnabled ? labelHidden ? "" : RadioState.cellularLabel(true,
                    wifiInUse, airplane, label.nativeText, fallback, labelNormalize, wifiEnabled, dataEnabled)
                    : nativeBadgeHidden ? "" : label.nativeText;
            if (!shown.trim().isEmpty()) next = Boolean.TRUE;
        }
        if (!Objects.equals(labelVisible, next)) { labelVisible = next; refreshSoon(); }
    }
    void invalidateLabels() {
        if (removed) return;
        labels.clear(); labelSources = 0;
        if (labelVisible != null) { labelVisible = null; refreshSoon(); }
    }
    void mobileBound(View view, String slot) {
        if (removed || view == null || slot == null) return;
        if (slot.startsWith("mobile") || slot.equals("stacked_mobile") || slot.startsWith("stacked_mobile_"))
            mobiles.put(view, Boolean.TRUE);
        else mobileDetached(view);
        slots.put(view, slot);
    }
    boolean slotKnown(View view) { return slots.containsKey(view); }
    void unknownSlot(View view) { if (!removed) slots.put(view, "?"); }
    void mobileDetached(View view) {
        if (removed) return;
        slots.remove(view);
        mobiles.remove(view);
        for (Map.Entry<ViewGroup, Boundary> entry : boundaries.entrySet()) if (entry.getValue().last.get() == view) {
            entry.getValue().last.clear(); apply(entry.getKey(), entry.getValue());
        }
    }
    /** Native two-sibling boundary, including the OEM's single icons wrapper. */
    ViewGroup batteryAttached(View battery, Class<?> containerClass) {
        if (removed || battery == null || !(battery.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) battery.getParent();
        if (parent.getChildCount() > 32) return null;
        for (int i = 1; i < parent.getChildCount(); i++) if (parent.getChildAt(i) == battery) {
            View previous = parent.getChildAt(i - 1);
            ViewGroup container = uniqueContainer(previous, containerClass, 0, new int[]{64});
            if (container == null) return null;
            Boundary old = boundaries.get(container);
            if (old != null && old.battery.get() == battery && old.wrapper.get() == previous
                    && old.parent.get() == parent && old.batteryIndex == i && pathValid(container, old)) return container;
            if (old != null) restore(container, old);
            Boundary boundary = new Boundary(container, battery, previous, parent, i);
            boundaries.put(container, boundary); batteries.put(battery, boundary);
            return container;
        }
        return null;
    }
    private static ViewGroup uniqueContainer(View view, Class<?> type, int depth, int[] budget) {
        if (view == null || --budget[0] < 0 || depth > 4) { budget[0] = -1; return null; }
        if (type.isInstance(view)) return view instanceof ViewGroup ? (ViewGroup) view : null;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        if (group.getChildCount() > 32) { budget[0] = -1; return null; }
        ViewGroup found = null;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup candidate = uniqueContainer(group.getChildAt(i), type, depth + 1, budget);
            if (budget[0] < 0 || candidate != null && found != null) { budget[0] = -1; return null; }
            if (candidate != null) found = candidate;
        }
        return found;
    }
    private static boolean pathValid(ViewGroup container, Boundary boundary) {
        View wrapper = boundary.wrapper.get();
        if (wrapper == null) return false;
        for (int i = 0; i < boundary.pathParents.size(); i++) {
            ViewGroup group = boundary.pathParents.get(i).get(); View child = boundary.pathChildren.get(i).get();
            if (group == null || child == null || child.getParent() != group
                    || group.getChildCount() != boundary.pathCounts.get(i)
                    || group.getChildAt(boundary.pathIndices.get(i)) != child) return false;
        }
        return boundary.pathParents.isEmpty() ? wrapper == container
                : boundary.pathParents.get(boundary.pathParents.size() - 1).get() == wrapper;
    }
    private static int childIndex(ViewGroup parent, View child) {
        if (parent.getChildCount() > 32) return -1;
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChildAt(i) == child) return i;
        return -1;
    }
    private static boolean adjacent(Boundary boundary) {
        View wrapper = boundary.wrapper.get(), battery = boundary.battery.get(); ViewGroup parent = boundary.parent.get();
        return wrapper != null && battery != null && parent != null && wrapper.getParent() == parent
                && battery.getParent() == parent && boundary.batteryIndex < parent.getChildCount()
                && parent.getChildAt(boundary.batteryIndex - 1) == wrapper && parent.getChildAt(boundary.batteryIndex) == battery;
    }
    /** Native measure is an authoritative fallback when attachment precedes hook installation. */
    void measured(ViewGroup container, View lastNativeVisible, Class<?> batteryClass, Class<?> containerClass) {
        if (removed) return;
        Boundary old = boundaries.get(container);
        if (old != null && (!pathValid(container, old) || !adjacent(old))) {
            restore(container, old); batteries.remove(old.battery.get()); boundaries.remove(container); old = null;
        }
        if (old == null) {
            View child = container;
            for (int depth = 0; depth <= 4 && child.getParent() instanceof ViewGroup; depth++) {
                ViewGroup parent = (ViewGroup) child.getParent();
                int index = childIndex(parent, child);
                if (index >= 0 && index + 1 < parent.getChildCount()) {
                    View battery = parent.getChildAt(index + 1);
                    if (batteryClass.isInstance(battery) && batteryAttached(battery, containerClass) == container) break;
                }
                child = parent;
            }
        }
        measured(container, lastNativeVisible);
    }
    void batteryDetached(View battery) {
        batteries.remove(battery);
        for (java.util.Iterator<Map.Entry<ViewGroup, Boundary>> iterator = boundaries.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<ViewGroup, Boundary> entry = iterator.next();
            if (entry.getValue().battery.get() == battery) { restore(entry.getKey(), entry.getValue()); iterator.remove(); }
        }
    }
    /** Uses the last element of the list already computed by native onMeasure; no extra tree walk. */
    void measured(ViewGroup container, View lastNativeVisible) {
        if (removed) return;
        Boundary boundary = boundaries.get(container);
        if (boundary == null) return;
        if (boundary.last.get() != lastNativeVisible) boundary.last = new WeakReference<>(lastNativeVisible);
        apply(container, boundary);
    }
    /** Native onMeasure already filtered visible/blocked/ignored icons. A disconnected
     * Wi-Fi model on another status row must not veto this row's actual absent marker. */
    void measuredNative(ViewGroup container, List<?> nativeVisible, Class<?> batteryClass, Class<?> containerClass) {
        Object last = nativeVisible.isEmpty() ? null : nativeVisible.get(nativeVisible.size() - 1);
        measured(container, last instanceof View ? (View) last : null, batteryClass, containerClass);
        Boundary boundary = boundaries.get(container);
        if (boundary == null) return;
        Boolean wifi = Boolean.FALSE;
        for (Object item : nativeVisible) {
            if (!(item instanceof View)) { wifi = null; break; }
            String slot = slots.get(item);
            if ("?".equals(slot)) { wifi = null; break; }
            if (slot != null && (slot.equals("wifi") || slot.startsWith("wifi_"))) { wifi = Boolean.TRUE; break; }
        }
        boundary.measuredWifi = true; boundary.wifi = wifi;
        apply(container, boundary);
    }
    void containerChanged(ViewGroup container) {
        Boundary boundary = boundaries.get(container);
        if (boundary != null) { boundary.last.clear(); restore(container, boundary); }
    }
    private boolean eligible(ViewGroup container, Boundary boundary) {
        Boolean actualWifi = boundary.measuredWifi ? boundary.wifi : wifiVisible;
        if (!enabled || spacing == 0f || !Boolean.FALSE.equals(actualWifi) || !Boolean.FALSE.equals(labelVisible)) return false;
        View battery = boundary.battery.get(), mobile = boundary.last.get();
        ViewGroup parent = boundary.parent.get();
        View wrapper = boundary.wrapper.get();
        if (!pathValid(container, boundary)) return false;
        for (WeakReference<View> child : boundary.trailing) {
            View view = child.get(); if (view == null || view.getVisibility() == View.VISIBLE) return false;
        }
        return battery != null && mobile != null && parent != null && wrapper != null && mobiles.containsKey(mobile)
                && wrapper.getParent() == parent && battery.getParent() == parent && mobile.getParent() == container
                && adjacent(boundary)
                && container.isAttachedToWindow() && battery.isAttachedToWindow() && mobile.isAttachedToWindow()
                && container.getVisibility() == View.VISIBLE && battery.getVisibility() == View.VISIBLE;
    }
    private void apply(ViewGroup container, Boundary boundary) {
        View battery = boundary.battery.get();
        ViewGroup.LayoutParams raw = battery == null ? null : battery.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) { restore(container, boundary); return; }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        boolean rtl = battery.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (boundary.owned && rtl != boundary.appliedRtl) restore(container, boundary);
        int actual = readStart(params, boundary.owned ? boundary.relative : params.isMarginRelative(), rtl);
        if (!boundary.owned || actual != boundary.appliedStart) {
            boundary.nativeStart = actual; boundary.relative = params.isMarginRelative(); boundary.appliedRtl = rtl;
        }
        if (!eligible(container, boundary)) { restore(container, boundary); return; }
        int target = targetMargin(container, boundary.nativeStart);
        boundary.appliedStart = target; boundary.owned = target != boundary.nativeStart;
        if (actual != target) writeMargin(battery, params, target, boundary);
    }
    /** Padding is native-only: its contribution to native measured width can cancel a
     * local updateStates displacement. The actual boundary belongs to the battery LP. */
    int requestedEnd(View view, int nativeEnd) {
        return nativeEnd;
    }
    /** Exact battery setter; copied module margins do not become a new native baseline. */
    void requestedLayout(View view, ViewGroup.LayoutParams raw) {
        if (removed || applying) return;
        Boundary boundary = batteries.get(view);
        if (boundary == null || !(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (boundary.owned && rtl != boundary.appliedRtl) restore(null, boundary);
        int start = readStart(params, boundary.owned ? boundary.relative : params.isMarginRelative(), rtl);
        if (!boundary.owned || start != boundary.appliedStart || params.isMarginRelative() != boundary.relative) {
            boundary.nativeStart = start; boundary.relative = params.isMarginRelative(); boundary.appliedRtl = rtl;
        }
        ViewGroup container = boundary.last.get() != null && boundary.last.get().getParent() instanceof ViewGroup
                ? (ViewGroup) boundary.last.get().getParent() : null;
        if (container == null || boundaries.get(container) != boundary || !eligible(container, boundary)) {
            if (boundary.owned && start == boundary.appliedStart) setStart(params, boundary.nativeStart, boundary);
            restore(container, boundary); boundary.owned = false; return;
        }
        int target = targetMargin(container, boundary.nativeStart);
        boundary.appliedStart = target; boundary.owned = target != boundary.nativeStart;
        if (start != target) setStart(params, target, boundary);
    }
    private int targetMargin(View container, int nativeStart) {
        float density = container.getResources().getDisplayMetrics().density;
        if (!Float.isFinite(density) || density <= 0f) density = 1f;
        double target = nativeStart + (double) spacing * density;
        return (int) Math.round(Math.max(-NumericPolicy.MAX_LAYOUT_PIXELS, Math.min(NumericPolicy.MAX_LAYOUT_PIXELS, target)));
    }
    private void restore(ViewGroup container, Boundary boundary) {
        if (!boundary.owned) return;
        boundary.owned = false;
        View battery = boundary.battery.get();
        ViewGroup.LayoutParams raw = battery == null ? null : battery.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            if (readStart(params, boundary.relative, boundary.appliedRtl) == boundary.appliedStart)
                writeMargin(battery, params, boundary.nativeStart, boundary);
        }
    }
    private static int readStart(ViewGroup.MarginLayoutParams params, boolean relative, boolean rtl) {
        return relative ? params.getMarginStart() : rtl ? params.rightMargin : params.leftMargin;
    }
    private static void setStart(ViewGroup.MarginLayoutParams params, int start, Boundary boundary) {
        if (boundary.relative) params.setMarginStart(start);
        else if (boundary.appliedRtl) params.rightMargin = start;
        else params.leftMargin = start;
    }
    private void writeMargin(View battery, ViewGroup.MarginLayoutParams params, int start, Boundary boundary) {
        applying = true;
        try { setStart(params, start, boundary); battery.setLayoutParams(params); }
        finally { applying = false; }
    }
    private void refreshSoon() {
        if (removed) return;
        if (Looper.myLooper() == main.getLooper()) { main.removeCallbacks(refresh); refresh.run(); }
        else if (!pending) { pending = true; main.post(refresh); }
    }
    void releaseRuntime() {
        if (removed) return;
        removed = true; enabled = false; main.removeCallbacks(refresh); pending = false;
        for (Map.Entry<ViewGroup, Boundary> entry : boundaries.entrySet()) restore(entry.getKey(), entry.getValue());
        boundaries.clear(); batteries.clear(); mobiles.clear(); slots.clear(); labels.clear(); wifiVisible = null; labelVisible = null;
    }
}
