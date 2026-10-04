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
 * needs its own horizontal slot before mobile_signal_data_outer, not a translated badge.
 * Clone the exact native constraints; never reparent IconManager children or mutate OEM LPs. */
final class SingleNetworkLabelPlacement {
    private final WeakReference<TextView> text;
    private WeakReference<View> signal = new WeakReference<>(null);
    private WeakReference<View> roaming = new WeakReference<>(null);
    private WeakReference<ViewGroup> parentRef = new WeakReference<>(null);
    private ViewGroup.LayoutParams nativeText, nativeSignal, appliedText, appliedSignal;
    private ViewGroup.LayoutParams nativeRoaming, appliedRoaming;
    private int nativeGravity;
    private boolean owned;
    SingleNetworkLabelPlacement(TextView view) { text = new WeakReference<>(view); }

    boolean apply() {
        TextView view = text.get();
        if (view == null || !(view.getParent() instanceof ViewGroup)) return false;
        if (owned && view.getParent() == parentRef.get() && view.getLayoutParams() == appliedText
                && signal.get() != null && signal.get().getLayoutParams() == appliedSignal
                && (appliedRoaming == null || roaming.get() != null
                    && roaming.get().getLayoutParams() == appliedRoaming)) return true;
        restore();
        ViewGroup parent = (ViewGroup) view.getParent();
        int id = view.getResources().getIdentifier("mobile_signal_data_outer", "id", "com.android.systemui");
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
            int roamingId = view.getResources().getIdentifier("mobile_roaming_text", "id", "com.android.systemui");
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
            set(label, "verticalBias", .5f);
            // A packed reciprocal chain measures label + optional R + signal as one slot.
            set(label, "horizontalChainStyle", 2); set(label, "horizontalBias", 1f);
            nativeText = textParams; nativeSignal = signalParams; nativeGravity = view.getGravity();
            nativeRoaming = roamingParams; appliedRoaming = roam; roaming = new WeakReference<>(roamingView);
            parentRef = new WeakReference<>(parent);
            signal = new WeakReference<>(target); appliedText = label; appliedSignal = icon; owned = true;
            if (roamingView != null) roamingView.setLayoutParams(roam);
            target.setLayoutParams(icon); view.setLayoutParams(label); view.setGravity(Gravity.CENTER);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            restore(); return false;
        }
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
    void restore() {
        if (!owned) return;
        TextView view = text.get(); View target = signal.get(), roamingView = roaming.get();
        if (view != null && view.getLayoutParams() == appliedText) {
            view.setLayoutParams(nativeText); view.setGravity(nativeGravity);
        }
        if (target != null && target.getLayoutParams() == appliedSignal) target.setLayoutParams(nativeSignal);
        if (roamingView != null && roamingView.getLayoutParams() == appliedRoaming) roamingView.setLayoutParams(nativeRoaming);
        owned = false; nativeText = nativeSignal = appliedText = appliedSignal = null; signal.clear();
        nativeRoaming = appliedRoaming = null; roaming.clear(); parentRef.clear();
    }
}
