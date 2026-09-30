// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the current subscription's original RAT model before the vendor visibility gate. */
public final class NativeDataSource {
    private static final int MAX_SUBSCRIPTIONS = 16;
    private static final Map<Integer, Source> SOURCES = new LinkedHashMap<>();
    private NativeDataSource() { }

    private static final class Source {
        final WeakReference<Object> owner, interactor, flow;
        final Method subscription, value;
        Source(Object owner, Object interactor, Object flow, Method subscription, Method value) {
            this.owner = new WeakReference<>(owner);
            this.interactor = new WeakReference<>(interactor);
            this.flow = new WeakReference<>(flow);
            this.subscription = subscription;
            this.value = value;
        }
        boolean alive() { return owner.get() != null && interactor.get() != null && flow.get() != null; }
    }

    /** Call after OplusMobileIconsViewModel.viewModelForSub(subId) has created its native model. */
    public static void capture(Object owner, int subId) {
        if (subId < 0) return;
        Source found = null;
        if (owner != null) try {
            Object iconsInteractor = field(owner.getClass(), "interactor").get(owner);
            if (iconsInteractor != null) {
                Object interactor = method(iconsInteractor.getClass(), "getMobileConnectionInteractorForSubId", Integer.TYPE)
                        .invoke(iconsInteractor, subId);
                if (interactor != null) {
                    Method subscription = method(interactor.getClass(), "getSubscriptionId");
                    if (matches(subscription.invoke(interactor), subId)) {
                        Object flow = method(interactor.getClass(), "getNetworkTypeIconGroup").invoke(interactor);
                        if (flow != null) {
                            Class<?> stateFlow = Class.forName("kotlinx.coroutines.flow.StateFlow", false,
                                    flow.getClass().getClassLoader());
                            if (stateFlow.isInstance(flow)) found = new Source(owner, interactor, flow,
                                    subscription, method(stateFlow, "getValue"));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) {
            // Other ROMs may use another interactor or a cold Flow. Preserve their native behaviour.
        }
        synchronized (SOURCES) {
            prune();
            // A failed new capture must not keep a prior source belonging to the old model.
            SOURCES.remove(subId);
            if (found != null) SOURCES.put(subId, found);
            while (SOURCES.size() > MAX_SUBSCRIPTIONS) SOURCES.remove(SOURCES.keySet().iterator().next());
        }
    }

    /** Raw native name, never a remembered name; callers apply their own visibility/normalisation. */
    public static String label(int activeSubId) {
        if (activeSubId < 0) return "";
        Source source;
        synchronized (SOURCES) { prune(); source = SOURCES.get(activeSubId); }
        if (source == null) return "";
        Object owner = source.owner.get(), interactor = source.interactor.get(), flow = source.flow.get();
        if (owner == null || interactor == null || flow == null) return "";
        try {
            if (!matches(source.subscription.invoke(interactor), activeSubId)) return "";
            Object iconModel = source.value.invoke(flow);
            if (iconModel == null) return "";
            Object textModel = method(iconModel.getClass(), "getNetworkTypeTextModel").invoke(iconModel);
            if (textModel == null) return "";
            Object networkType = method(textModel.getClass(), "getNetworkTypeText").invoke(textModel);
            if (networkType == null) return "";
            Object name = method(networkType.getClass(), "getName").invoke(networkType);
            return name instanceof String ? (String) name : "";
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) {
            return "";
        }
    }

    public static void clear(int subId) { synchronized (SOURCES) { SOURCES.remove(subId); } }
    public static void clear() { synchronized (SOURCES) { SOURCES.clear(); } }

    private static boolean matches(Object value, int subId) {
        return value instanceof Number && ((Number) value).intValue() == subId;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> target = owner; target != null; target = target.getSuperclass()) try {
            Field result = target.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (NoSuchFieldException inherited) { }
        throw new NoSuchFieldException(name);
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        try {
            Method result = owner.getMethod(name, parameters); result.setAccessible(true); return result;
        } catch (NoSuchMethodException notPublic) {
            for (Class<?> target = owner; target != null; target = target.getSuperclass()) try {
                Method result = target.getDeclaredMethod(name, parameters); result.setAccessible(true); return result;
            } catch (NoSuchMethodException inherited) { }
            throw notPublic;
        }
    }

    /** SOURCES lock is held. Weak entries cannot retain a rebuilt SystemUI or removed SIM. */
    private static void prune() {
        Iterator<Map.Entry<Integer, Source>> entries = SOURCES.entrySet().iterator();
        while (entries.hasNext()) if (!entries.next().getValue().alive()) entries.remove();
    }
}
