// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** The OEM single-SIM mobile_type_text is a bottom/end badge. A custom network label
 * uses the same signal-then-label logical slots as the dual-SIM Compose renderer.
 * Clone the exact native constraints; never reparent IconManager children or mutate OEM LPs. */
final class SingleNetworkLabelPlacement implements NetworkSharedSlotLayout.Backend {
    private final WeakReference<TextView> text;
    private WeakReference<View> signal = new WeakReference<>(null);
    private WeakReference<View> roaming = new WeakReference<>(null);
    private WeakReference<ViewGroup> parentRef = new WeakReference<>(null);
    private ViewGroup.LayoutParams nativeText, nativeSignal, appliedText, appliedSignal;
    private ViewGroup.LayoutParams nativeRoaming, appliedRoaming;
    private int nativeGravity, appliedDirection;
    private boolean owned;
    private int order;
    private float left, right;
    private NetworkSharedSlotLayout sharedLayout;
    private WeakReference<View> sharedHost = new WeakReference<>(null);
    private NetworkSharedSlotLayout.Target sharedTarget;
    private NetworkSharedSlotLayout.Target appliedTarget;
    private ViewGroup.LayoutParams nativeParent, appliedParent;
    private int nativeLabelLeft, nativeSignalLeft, nativeWidth, nativeParentLeft, labelInset, signalInset;
    private boolean sharedMeasured, nativeLabelPresent, awaitingNativeLayout, awaitingNativeMeasure;
    private int measuredLabelWidth, measuredSignalWidth, geometryRevision, appliedGeometryRevision;
    private boolean measuredRoaming;
    /** Screen configuration the native sample was taken in; a rotation or a density/font change must resample. */
    private String nativeConfiguration = "";
    private java.util.Set<String> diagnosedFailures;
    private static final int MAX_FAILURE_FACTS = 8;
    SingleNetworkLabelPlacement(TextView view) { text = new WeakReference<>(view); }
    void sharedLayout(NetworkSharedSlotLayout value) {
        if (sharedLayout == value) return;
        boolean pending = sharedTarget != null || awaitingNativeMeasure || awaitingNativeLayout;
        if (sharedLayout != null) sharedLayout.unregister(this);
        sharedLayout = value; sharedHost.clear(); sharedMeasured = false; awaitingNativeLayout = awaitingNativeMeasure = pending;
    }
    void observeSharedHost() {
        TextView view = text.get();
        View host = sharedLayout == null ? null : sharedLayout.host(view);
        if (host == sharedHost.get()) return;
        boolean pending = sharedTarget != null || awaitingNativeMeasure || awaitingNativeLayout;
        if (sharedLayout != null) sharedLayout.unregister(this);
        sharedHost = new WeakReference<>(host); sharedMeasured = false; awaitingNativeLayout = awaitingNativeMeasure = pending;
        if (host != null) sharedLayout.register(host, this);
    }
    void configure(int value, float before, float after) {
        if (order == value && left == before && right == after) return;
        restore(); order = value; left = before; right = after;
    }

    boolean apply() {
        observeSharedHost();
        if (dropStaleTarget()) return false;
        if (sharedTarget != null) return applyShared();
        TextView view = text.get();
        if (view == null || !(view.getParent() instanceof ViewGroup)) return false;
        if (appliedDirection == view.getLayoutDirection() && ownsCurrentLayout(view)) return true;
        restore();
        ViewGroup parent = (ViewGroup) view.getParent();
        int id = ResourceIds.get(view.getResources(), "mobile_signal_data_outer", "id", "com.android.systemui");
        View target = id == 0 ? null : parent.findViewById(id);
        if (target == null || target.getParent() != parent || view.getId() <= 0
                || target.getId() <= 0 || target == view) return false;
        ViewGroup.LayoutParams textParams = view.getLayoutParams(), signalParams = target.getLayoutParams();
        if (textParams == null || signalParams == null || textParams.getClass() != signalParams.getClass()
                || !textParams.getClass().getName().endsWith("ConstraintLayout$LayoutParams")) return false;
        try {
            Class<?> type = textParams.getClass();
            Constructor<?> copy = type.getConstructor(ViewGroup.LayoutParams.class);
            ViewGroup.MarginLayoutParams label = (ViewGroup.MarginLayoutParams) copy.newInstance(textParams);
            ViewGroup.MarginLayoutParams icon = (ViewGroup.MarginLayoutParams) copy.newInstance(signalParams);
            int roamingId = ResourceIds.get(view.getResources(), "mobile_roaming_text", "id", "com.android.systemui");
            View roamingView = roamingId == 0 ? null : parent.findViewById(roamingId);
            if (roamingView != null && (roamingView.getParent() != parent || roamingView.getId() <= 0
                    || roamingView == view || roamingView == target)) roamingView = null;
            ViewGroup.LayoutParams roamingParams = roamingView == null ? null : roamingView.getLayoutParams();
            if (roamingView != null && (roamingParams == null || roamingParams.getClass() != type)) return false;
            ViewGroup.MarginLayoutParams roam = roamingParams == null ? null
                    : (ViewGroup.MarginLayoutParams) copy.newInstance(roamingParams);
            clear(label, "leftToLeft", "leftToRight", "rightToLeft", "rightToRight", "startToEnd",
                    "endToEnd", "topToBottom", "bottomToTop", "baselineToBaseline", "baselineToTop", "baselineToBottom");
            set(label, "startToStart", 0); set(label, "endToStart", roam == null ? target.getId() : roamingView.getId());
            set(label, "topToTop", 0); set(label, "bottomToBottom", 0);
            clear(icon, "leftToLeft", "leftToRight", "rightToLeft", "rightToRight", "startToStart", "endToStart");
            set(icon, "startToEnd", roam == null ? view.getId() : roamingView.getId()); set(icon, "endToEnd", 0);
            if (roam != null) {
                // OEM R sits in the exact same start-to-signal slot. Make it a real
                // chain member so a visible roaming indicator cannot overlap text.
                // Its native bottom anchor, size, visibility and spacing are retained.
                clear(roam, "leftToLeft", "leftToRight", "rightToLeft", "rightToRight", "startToStart", "endToEnd");
                set(roam, "startToEnd", view.getId()); set(roam, "endToStart", target.getId());
            }
            label.width = ViewGroup.LayoutParams.WRAP_CONTENT; label.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            label.topMargin = label.bottomMargin = 0;
            label.leftMargin = label.rightMargin = 0;
            label.setMarginStart(0); label.setMarginEnd(0);
            float density = view.getResources().getDisplayMetrics().density;
            boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            if (left != 0f || right != 0f || order != 0) {
                label.leftMargin = NetworkIconOrder.marginPixels(left, density);
                label.rightMargin = NetworkIconOrder.marginPixels(right, density);
                label.setMarginStart(rtl ? label.rightMargin : label.leftMargin);
                label.setMarginEnd(rtl ? label.leftMargin : label.rightMargin);
            }
            // The current dual-SIM Compose Row is signal then independent label
            // in logical order. Only explicit 1/2 choose physical left/right.
            // Starting with the native single-SIM badge's opposite order also
            // gave shared flow the wrong sort points after a SystemUI reload.
            boolean afterSignal = order == 0 || order == 2 && !rtl || order == 1 && rtl;
            if (afterSignal) {
                    clear(label, "startToStart", "endToStart");
                    set(label, "startToEnd", target.getId()); set(label, "endToEnd", 0);
                    clear(icon, "startToEnd", "endToEnd");
                    set(icon, "startToStart", roam == null ? 0 : -1);
                    if (roam != null) {
                        clear(roam, "endToStart", "startToEnd");
                        set(roam, "startToStart", 0); set(roam, "endToStart", target.getId());
                        set(roam, "horizontalChainStyle", 2); set(roam, "horizontalBias", 1f);
                        set(icon, "startToEnd", roamingView.getId());
                    }
                    set(icon, "endToStart", view.getId()); set(icon, "horizontalChainStyle", 2); set(icon, "horizontalBias", 1f);
            }
            set(label, "verticalBias", .5f);
            // A packed reciprocal chain measures label + optional R + signal as one slot.
            set(label, "horizontalChainStyle", 2); set(label, "horizontalBias", 1f);
            nativeText = textParams; nativeSignal = signalParams; nativeGravity = view.getGravity();
            nativeRoaming = roamingParams; appliedRoaming = roam; roaming = new WeakReference<>(roamingView);
            parentRef = new WeakReference<>(parent);
            signal = new WeakReference<>(target); appliedText = label; appliedSignal = icon; owned = true; appliedDirection = view.getLayoutDirection();
            if (roamingView != null) roamingView.setLayoutParams(roam);
            target.setLayoutParams(icon); view.setLayoutParams(label); view.setGravity(Gravity.CENTER);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            restore(); return false;
        }
    }
    /** Split only the audited OEM text/signal/R siblings, using genuine physical constraints. */
    @Override public NetworkSharedSlotLayout.Frame frame(View host) {
        observeSharedHost();
        // Restored LPs need their next native measure before capturing geometry;
        // do not save the previous expanded hull as a new natural measurement.
        if (dropStaleTarget()) return null;
        TextView view = text.get();
        if (view == null || host != sharedHost.get() || !(view.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) view.getParent();
        View icon = sibling(parent, view, "mobile_signal_data_outer"), roam = sibling(parent, view, "mobile_roaming_text");
        if (icon == null || !constraintShape(view, icon, roam)) return null;
        // Restoring a vanished label's constraints requests a real native
        // traversal, but another radio/outer-flow callback may arrive first.
        // Until that traversal finishes, the measured host can still be our
        // old EXACT data hull. Never adopt it as Wi-Fi-only natural geometry.
        if (awaitingNativeMeasure || awaitingNativeLayout) return null;
        int parentLeft = NetworkSharedSlotLayout.offset(parent, host);
        if (parentLeft < 0 || icon.getVisibility() == View.GONE || icon.getMeasuredWidth() <= 0) return null;
        boolean present = view.getVisibility() == View.VISIBLE && view.getMeasuredWidth() > 0;
        int signalMin = icon.getLeft(), signalMax = signalMin + icon.getMeasuredWidth();
        boolean roamingPresent = roam != null && roam.getVisibility() != View.GONE && roam.getMeasuredWidth() > 0;
        if (roamingPresent) {
            signalMin = Math.min(signalMin, roam.getLeft()); signalMax = Math.max(signalMax, roam.getLeft() + roam.getMeasuredWidth());
        }
        // A binder can install the new chain before the next native layout.
        // Do not freeze old bottom-badge coordinates as the shared natural
        // order. setLayoutParams already requested that one native traversal.
        if (sharedTarget == null && owned && appliedTarget == null && present && left == 0f && right == 0f) {
            boolean labelAfter = order == 0 ? view.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL : order == 2;
            if (labelAfter ? view.getLeft() < signalMax : view.getLeft() + view.getMeasuredWidth() > signalMin) return null;
        }
        if (sharedTarget == null) {
            nativeLabelLeft = parentLeft + view.getLeft(); nativeSignalLeft = parentLeft + signalMin;
            nativeWidth = host.getMeasuredWidth(); nativeParentLeft = parentLeft; nativeLabelPresent = present; sharedMeasured = true;
            nativeConfiguration = configurationKey(view);
        }
        if (!sharedMeasured || present != nativeLabelPresent || nativeWidth <= 0 || signalMax <= signalMin) return null;
        int currentLabelWidth = present ? view.getMeasuredWidth() : 0, currentSignalWidth = signalMax - signalMin;
        if (currentLabelWidth != measuredLabelWidth || currentSignalWidth != measuredSignalWidth || roamingPresent != measuredRoaming) {
            measuredLabelWidth = currentLabelWidth; measuredSignalWidth = currentSignalWidth; measuredRoaming = roamingPresent; geometryRevision++;
        }
        // The OEM linear row may have real native siblings before this constraint
        // parent. Keep that prefix in the first fragment's budget, as Compose does
        // for its native Row origin. A target at zero then retains the real prefix
        // instead of asking ConstraintLayout to place text at a negative margin.
        boolean labelFirst = present && nativeLabelLeft < nativeSignalLeft;
        labelInset = labelFirst ? nativeParentLeft : 0;
        signalInset = labelFirst ? 0 : nativeParentLeft;
        int labelBudget = (present ? view.getMeasuredWidth() : 0) + labelInset;
        int signalBudget = signalMax - signalMin + signalInset;
        if (labelBudget > 65535 || signalBudget > 65535) return null;
        return new NetworkSharedSlotLayout.Frame(host, this, true, present, labelBudget,
                signalBudget, nativeLabelLeft - labelInset, nativeSignalLeft - signalInset,
                nativeWidth, parentLeft + signalMin - signalInset);
    }
    @Override public boolean target(NetworkSharedSlotLayout.Target value) {
        if (!sharedMeasured || value == null) return rejected("unmeasured", -1, value, null);
        if (!value.signalVisible || value.labelVisible != nativeLabelPresent) return rejected("native_visibility", -1, value, null);
        // The destination can stay unchanged while a native binder replaces
        // the constraints or hands these same leaves to a new parent. Validate
        // current ownership through applyShared before accepting that target.
        if (value.same(sharedTarget) && appliedTarget == sharedTarget && appliedGeometryRevision == geometryRevision) return applyShared();
        // Restore only our own LP identities before cloning a different hull.
        restoreOwned(); sharedTarget = value;
        if (!applyShared()) { sharedTarget = null; return false; }
        return true;
    }
    private boolean applyShared() {
        if (dropStaleTarget()) return false;
        TextView view = text.get(); View host = sharedHost.get();
        if (view == null || host == null || sharedTarget == null || !(view.getParent() instanceof ViewGroup)) return rejected("missing_host", -1, sharedTarget, null);
        if (appliedTarget == sharedTarget && appliedGeometryRevision == geometryRevision && ownsCurrentLayout(view)) return true;
        restoreOwned();
        ViewGroup parent = (ViewGroup) view.getParent();
        View icon = sibling(parent, view, "mobile_signal_data_outer"), roam = sibling(parent, view, "mobile_roaming_text");
        if (icon == null || !constraintShape(view, icon, roam)) return rejected("constraint_shape", -1, sharedTarget, null);
        int parentLeft = NetworkSharedSlotLayout.offset(parent, host);
        if (parentLeft < 0 || sharedTarget.width <= parentLeft) return rejected("parent_geometry", parentLeft, sharedTarget, null);
        String stage = "constraint_copy";
        try {
            ViewGroup.LayoutParams textParams = view.getLayoutParams(), signalParams = icon.getLayoutParams();
            Constructor<?> copy = textParams.getClass().getConstructor(ViewGroup.LayoutParams.class);
            ViewGroup.MarginLayoutParams label = (ViewGroup.MarginLayoutParams) copy.newInstance(textParams);
            ViewGroup.MarginLayoutParams signalCopy = (ViewGroup.MarginLayoutParams) copy.newInstance(signalParams);
            ViewGroup.LayoutParams roamParams = roam == null ? null : roam.getLayoutParams();
            ViewGroup.MarginLayoutParams roamCopy = roamParams == null ? null : (ViewGroup.MarginLayoutParams) copy.newInstance(roamParams);
            int signalMin = icon.getLeft();
            if (roam != null && roam.getVisibility() != View.GONE && roam.getMeasuredWidth() > 0) signalMin = Math.min(signalMin, roam.getLeft());
            stage = "label_position";
            if (sharedTarget.labelVisible) physical(label, sharedTarget.labelLeft + labelInset - parentLeft);
            stage = "signal_position";
            physical(signalCopy, sharedTarget.signalLeft + signalInset - parentLeft + icon.getLeft() - signalMin);
            stage = "roaming_position";
            if (roamCopy != null && roam.getVisibility() != View.GONE && roam.getMeasuredWidth() > 0)
                physical(roamCopy, sharedTarget.signalLeft + signalInset - parentLeft + roam.getLeft() - signalMin);
            stage = "constraint_fields";
            // The real OEM badge retains a 38px bottom margin. Once this text
            // is an independent centered slot, match apply()'s contract rather
            // than centering the old badge margin as part of the glyph height.
            label.width = label.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            label.topMargin = label.bottomMargin = 0;
            clear(label, "topToBottom", "bottomToTop", "baselineToBaseline", "baselineToTop", "baselineToBottom");
            set(label, "topToTop", 0); set(label, "bottomToBottom", 0); set(label, "verticalBias", .5f);
            ViewGroup.LayoutParams parentParams = parent.getLayoutParams(), parentCopy = null;
            if (parent != host) {
                if (parentParams == null) return rejected("parent_params_missing", parentLeft, sharedTarget, null);
                stage = "parent_copy";
                parentCopy = (ViewGroup.LayoutParams) parentParams.getClass().getConstructor(ViewGroup.LayoutParams.class).newInstance(parentParams);
                parentCopy.width = sharedTarget.width - parentLeft;
            }
            nativeText = textParams; nativeSignal = signalParams; nativeRoaming = roamParams;
            appliedText = label; appliedSignal = signalCopy; appliedRoaming = roamCopy;
            nativeParent = parentParams; appliedParent = parentCopy;
            parentRef = new WeakReference<>(parent); signal = new WeakReference<>(icon); roaming = new WeakReference<>(roam);
            nativeGravity = view.getGravity(); owned = true; appliedDirection = view.getLayoutDirection(); appliedTarget = sharedTarget; appliedGeometryRevision = geometryRevision;
            stage = "constraint_install";
            if (parentCopy != null) parent.setLayoutParams(parentCopy);
            if (roamCopy != null) roam.setLayoutParams(roamCopy);
            icon.setLayoutParams(signalCopy); view.setLayoutParams(label); view.setGravity(Gravity.CENTER);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            rejected(stage, parentLeft, sharedTarget, unsupported); restoreOwned(); return false;
        }
    }
    /** Companion to native_flow's generic native_target_rejected record. Only a
     * bounded set of structural shapes/positions is logged; never text or SIM IDs. */
    private boolean rejected(String reason, int parentLeft, NetworkSharedSlotLayout.Target target, Throwable failure) {
        if (!ModuleDiagnostics.enabled() || diagnosedFailures != null && diagnosedFailures.size() >= MAX_FAILURE_FACTS) return false;
        TextView view = text.get();
        ViewGroup parent = view != null && view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        View icon = parent == null ? null : sibling(parent, view, "mobile_signal_data_outer");
        String labelShape = shape(view == null ? null : view.getLayoutParams());
        String signalShape = shape(icon == null ? null : icon.getLayoutParams());
        String parentShape = shape(parent == null ? null : parent.getLayoutParams());
        String failureClass = failure == null ? "none" : failure.getClass().getSimpleName();
        int width = target == null ? -1 : target.width, label = target == null ? -1 : target.labelLeft, signal = target == null ? -1 : target.signalLeft;
        String key = reason + ':' + parentLeft + ':' + nativeLabelLeft + ':' + label + ':' + signal + ':' + width
                + ':' + labelShape + ':' + signalShape + ':' + parentShape + ':' + failureClass;
        if (diagnosedFailures == null) diagnosedFailures = new java.util.HashSet<>();
        if (!diagnosedFailures.add(key)) return false;
        ModuleDiagnostics.fact("radio", "label", "单卡共享槽位校验", "backend", "native_constraint", "reason", reason,
                "parent_left_px", parentLeft, "native_label_left_px", nativeLabelLeft, "native_signal_left_px", nativeSignalLeft,
                "hull_px", width, "label_left_px", label, "signal_left_px", signal,
                "target_x_px", target == null ? -1 : label + labelInset - parentLeft,
                "label_lp", labelShape, "signal_lp", signalShape, "parent_lp", parentShape, "failure_class", failureClass);
        return false;
    }
    private static String shape(ViewGroup.LayoutParams params) {
        if (params == null) return "missing";
        String name = params.getClass().getName();
        if (name.endsWith("ConstraintLayout$LayoutParams")) return "constraint";
        if (name.endsWith("FrameLayout$LayoutParams")) return "frame";
        if (name.endsWith("LinearLayout$LayoutParams")) return "linear";
        return params instanceof ViewGroup.MarginLayoutParams ? "margin" : "plain";
    }
    private boolean dropStaleTarget() {
        TextView view = text.get();
        if (sharedTarget == null || view == null) return false;
        boolean present = view.getVisibility() == View.VISIBLE && view.getMeasuredWidth() > 0;
        // The native sample is frozen while a target is applied. A label shown or hidden, or a different screen
        // configuration (rotation, density, font scale), makes that sample stale: drop the target and sample again.
        if (present == sharedTarget.labelVisible && configurationKey(view).equals(nativeConfiguration)) return false;
        sharedTarget = null; appliedTarget = null; sharedMeasured = false; awaitingNativeLayout = awaitingNativeMeasure = true;
        // setLayoutParams restores each owned native identity once and already
        // requests its necessary native layout. No extra host request is added.
        restoreOwned(); return true;
    }
    private static void physical(ViewGroup.MarginLayoutParams params, int position) throws ReflectiveOperationException {
        if (position < 0 || position > 65535) throw new IllegalArgumentException("Fragment outside native hull");
        clear(params, "leftToRight", "rightToLeft", "rightToRight", "startToStart", "startToEnd", "endToStart", "endToEnd");
        set(params, "leftToLeft", 0); params.leftMargin = position; params.rightMargin = 0;
        params.setMarginStart(Integer.MIN_VALUE); params.setMarginEnd(Integer.MIN_VALUE);
    }
    private static String configurationKey(View view) {
        try {
            android.content.res.Configuration configuration = view.getResources().getConfiguration();
            return configuration == null ? "" : configuration.orientation + ":" + configuration.screenWidthDp + ":"
                    + configuration.densityDpi + ":" + configuration.fontScale;
        } catch (RuntimeException unavailable) {
            return "";
        }
    }
    private static View sibling(ViewGroup parent, TextView view, String name) {
        int id = ResourceIds.get(view.getResources(), name, "id", "com.android.systemui");
        View result = id == 0 ? null : parent.findViewById(id);
        return result != null && result != view && result.getParent() == parent ? result : null;
    }
    private static boolean constraintShape(TextView view, View icon, View roam) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams && params.getClass().getName().endsWith("ConstraintLayout$LayoutParams")
                && icon.getLayoutParams() != null && icon.getLayoutParams().getClass() == params.getClass()
                && (roam == null || roam.getLayoutParams() != null && roam.getLayoutParams().getClass() == params.getClass());
    }
    /** Parameter identity alone does not establish that a still-live old
     * signal/parent is the native binding currently hosting this label. */
    private boolean ownsCurrentLayout(TextView view) {
        ViewGroup parent = parentRef.get(); View icon = signal.get(), roam = roaming.get();
        return owned && parent != null && view.getParent() == parent && view.getLayoutParams() == appliedText
                && icon != null && icon.getParent() == parent && icon.getLayoutParams() == appliedSignal
                && (appliedRoaming == null || roam != null && roam.getParent() == parent && roam.getLayoutParams() == appliedRoaming)
                && (appliedParent == null || parent.getLayoutParams() == appliedParent);
    }
    private static void clear(Object params, String... fields) throws ReflectiveOperationException {
        for (String name : fields) {
            // Older OEM ConstraintLayout versions omit the two newer baseline fields.
            try { set(params, name, -1); }
            catch (NoSuchFieldException unavailable) {
                if (!name.equals("baselineToTop") && !name.equals("baselineToBottom")) throw unavailable;
            }
        }
    }
    private static void set(Object params, String name, int value) throws ReflectiveOperationException {
        Field field = params.getClass().getField(name); field.setInt(params, value);
    }
    private static void set(Object params, String name, float value) throws ReflectiveOperationException {
        Field field = params.getClass().getField(name); field.setFloat(params, value);
    }
    @Override public void restore() {
        boolean changed = sharedTarget != null; sharedTarget = null; appliedTarget = null;
        restoreOwned();
        if (changed) { sharedMeasured = false; awaitingNativeLayout = awaitingNativeMeasure = true; }
        View host = sharedHost.get(); if (changed && host != null) host.requestLayout();
    }
    @Override public void nativeMeasured() { awaitingNativeMeasure = false; }
    @Override public void nativeLaidOut() { if (!awaitingNativeMeasure) awaitingNativeLayout = false; }
    private void restoreOwned() {
        if (!owned) return;
        TextView view = text.get(); View target = signal.get(), roamingView = roaming.get();
        if (view != null && view.getLayoutParams() == appliedText) {
            view.setLayoutParams(nativeText); view.setGravity(nativeGravity);
        }
        if (target != null && target.getLayoutParams() == appliedSignal) target.setLayoutParams(nativeSignal);
        if (roamingView != null && roamingView.getLayoutParams() == appliedRoaming) roamingView.setLayoutParams(nativeRoaming);
        ViewGroup parent = parentRef.get();
        if (parent != null && appliedParent != null && parent.getLayoutParams() == appliedParent) parent.setLayoutParams(nativeParent);
        owned = false; nativeText = nativeSignal = appliedText = appliedSignal = null; signal.clear();
        nativeRoaming = appliedRoaming = null; roaming.clear(); parentRef.clear();
        nativeParent = appliedParent = null; appliedTarget = null;
    }
}
