// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;

/** Exact native geometry callbacks, including inherited callbacks on Phone/Keyguard roots. */
final class StatusBarFlowCallbacks {
    interface NativeCall { Object proceed() throws Throwable; }
    private final StatusBarFlowLayout flow;
    private final ThreadLocal<IdentityHashMap<View, boolean[]>> active =
            ThreadLocal.withInitial(IdentityHashMap::new);

    StatusBarFlowCallbacks(StatusBarFlowLayout flow) { this.flow = flow; }
    /** Structural layout can synchronously invoke StatusIconContainer.onLayout.
     * Its intermediate widths are not a second native animation transaction. */
    Object updateStates(ViewGroup parent, NativeCall nativeCall) throws Throwable {
        if (flow.internal()) return null;
        flow.beforeStates(parent);
        try { return nativeCall.proceed(); }
        finally { flow.afterStates(parent); }
    }
    Object applyState(Object state, View child, NativeCall nativeCall) throws Throwable {
        if (flow.deferNativeApply(state, child)) return null;
        flow.beforeApply(state, child);
        return nativeCall.proceed();
    }

    static Method virtual(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, arguments);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException inherited) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    Object dispatch(View view, boolean measure, NativeCall nativeCall) throws Throwable {
        if (flow.internal() || !flow.wanted() || !StatusBarFlowLayout.root(view)) return nativeCall.proceed();
        IdentityHashMap<View, boolean[]> scopes = active.get();
        boolean[] phases = scopes.get(view);
        int phase = measure ? 0 : 1;
        if (phases != null && phases[phase]) return nativeCall.proceed();
        if (phases == null) { phases = new boolean[2]; scopes.put(view, phases); }
        phases[phase] = true;
        ViewGroup root = (ViewGroup) view;
        boolean completed = false;
        try {
            if (measure) flow.beforeNativeMeasure(root); else flow.beforeNativeLayout(root);
            NetworkSharedSlotLayout bridge = measure ? flow.beginNativeContentMeasure(root) : null;
            Object result;
            boolean nativeSucceeded = false;
            try { result = nativeCall.proceed(); nativeSucceeded = true; }
            finally { if (bridge != null) bridge.endNativeMeasure(root, nativeSucceeded); }
            completed = true;
            if (measure) flow.afterMeasure(root); else flow.afterLayout(root);
            return result;
        } finally {
            phases[phase] = false;
            flow.endNativeCallback(root, phases[0] || phases[1]);
            if (!phases[0] && !phases[1]) scopes.remove(view);
            if (scopes.isEmpty()) active.remove();
            if (!completed) flow.detached(root);
        }
    }
}
