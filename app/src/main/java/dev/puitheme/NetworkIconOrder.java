// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

/** Changes the input order of the OEM layout; native measurement, RTL and state animation remain authoritative. */
public final class NetworkIconOrder {
    public static final String SWAP = "network_icons_swap_enabled";
    private volatile boolean enabled, released;
    private volatile int revision;
    private final Map<ViewGroup, Container> containers = new WeakHashMap<>();
    private final ThreadLocal<IndexScope> indexes = new ThreadLocal<>();
    private final ThreadLocal<RowScope> rows = new ThreadLocal<>();
    private final Map<Object, Integer> nodeKinds = new WeakHashMap<>();
    private final Map<Object, Integer> modifierKinds = new WeakHashMap<>();
    private final Map<Object, TagBinding> taggedModifiers = new WeakHashMap<>();
    private final Map<Object, Map<Object, Map<Object, Integer>>> composerRevisions = new WeakHashMap<>();
    private final Map<Object, Object> policyCache = new LinkedHashMap<>();
    private Method measuredNode, lookaheadNode;
    private Method modifierThen, modifierFold;
    private Object signalTag, textTag, modifierVisitor;
    private Class<?> nativeContainer, measurePolicy;
    private Method slot, visible, blocked;
    private Field ignored;

    public boolean configure(Bundle settings) {
        boolean wanted = !released && settings != null && settings.getBoolean(SWAP, false)
                && !settings.getBoolean(StatusBarSettings.SAFE_MODE, false);
        if (enabled == wanted) return false;
        enabled = wanted; revision++;
        synchronized (policyCache) { policyCache.clear(); }
        synchronized (containers) {
            for (ViewGroup parent : containers.keySet()) if (parent != null) parent.requestLayout();
        }
        return true;
    }

    public void resolve(ClassLoader loader) throws ReflectiveOperationException {
        nativeContainer = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
        Class<?> displayable = loader.loadClass("com.android.systemui.statusbar.StatusIconDisplayable");
        slot = displayable.getMethod("getSlot");
        visible = displayable.getMethod("isIconVisible");
        blocked = displayable.getMethod("isIconBlocked");
        ignored = nativeContainer.getDeclaredField("mIgnoredSlots"); ignored.setAccessible(true);
    }

    public void resolveCompose(ClassLoader loader) throws ReflectiveOperationException {
        measurePolicy = loader.loadClass("androidx.compose.ui.layout.MeasurePolicy");
        measuredNode = loader.loadClass("androidx.compose.ui.node.MeasurePassDelegate").getDeclaredMethod("getLayoutNode");
        measuredNode.setAccessible(true);
        lookaheadNode = loader.loadClass("androidx.compose.ui.node.LookaheadPassDelegate").getDeclaredMethod("getLayoutNode");
        lookaheadNode.setAccessible(true);
        Class<?> modifier = loader.loadClass("androidx.compose.ui.Modifier");
        Class<?> function = loader.loadClass("kotlin.jvm.functions.Function2");
        modifierThen = modifier.getMethod("then", modifier);
        modifierFold = modifier.getMethod("foldIn", Object.class, function);
        Object empty = modifier.getField("Companion").get(null);
        Method testTag = loader.loadClass("androidx.compose.ui.platform.TestTagKt").getMethod("testTag", modifier, String.class);
        signalTag = testTag.invoke(null, empty, "c17_network_signal");
        textTag = testTag.invoke(null, empty, "c17_network_type");
        modifierVisitor = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) {
                int previous = (Integer) args[0];
                int current = args[1] == signalTag ? 1 : args[1] == textTag ? 2 : 0;
                return current == 0 ? previous : previous == 0 || previous == current ? current : 3;
            }
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return "NetworkModifierIdentity";
        });
    }

    public void changed(ViewGroup parent) {
        if (released || parent == null || nativeContainer == null || !nativeContainer.isInstance(parent)) return;
        synchronized (containers) {
            Container state = containers.get(parent);
            if (state == null) containers.put(parent, new Container()); else state.dirty = true;
        }
    }

    public void bound(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) changed((ViewGroup) view.getParent());
    }

    public void detached(ViewGroup parent) {
        synchronized (containers) { containers.remove(parent); }
    }

    /** Called only from the two exact OEM layout methods, never from draw/preDraw. */
    public IndexScope begin(ViewGroup parent) {
        if (released || parent == null || nativeContainer == null || !nativeContainer.isInstance(parent)) return null;
        Container state;
        synchronized (containers) {
            state = containers.get(parent);
            if (state == null) { state = new Container(); containers.put(parent, state); }
        }
        if (!enabled) return null;
        if (state.dirty || state.count != parent.getChildCount() || !state.valid(parent)) {
            IndexScope previous = indexes.get(); indexes.remove();
            try { state.capture(parent); } finally { if (previous != null) indexes.set(previous); }
        }
        if (state.order == null || !state.visible(parent)) return null;
        IndexScope scope = new IndexScope(parent, state.order, indexes.get()); indexes.set(scope); return scope;
    }

    public int childIndex(Object parent, int nativeIndex) {
        IndexScope scope = indexes.get();
        return enabled && scope != null && scope.parent == parent && nativeIndex >= 0 && nativeIndex < scope.order.length
                ? scope.order[nativeIndex] : nativeIndex;
    }

    public final class IndexScope implements AutoCloseable {
        private ViewGroup parent;
        private final int[] order;
        private IndexScope previous;
        private IndexScope(ViewGroup parent, int[] order, IndexScope previous) {
            this.parent = parent; this.order = order; this.previous = previous;
        }
        @Override public void close() {
            if (indexes.get() == this) { if (previous == null) indexes.remove(); else indexes.set(previous); }
            parent = null; previous = null;
        }
    }

    private final class Container {
        boolean dirty = true;
        int count;
        int[] order;
        final List<WeakReference<View>> sources = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        boolean valid(ViewGroup parent) {
            for (WeakReference<View> reference : sources) { View view = reference.get(); if (view == null || view.getParent() != parent) return false; }
            return true;
        }
        void capture(ViewGroup parent) {
            dirty = false; count = parent.getChildCount(); order = null; sources.clear(); names.clear();
            int wifi = -1, firstMobile = -1, lastMobile = -1;
            boolean ambiguous = false;
            try {
                for (int i = 0; i < count; i++) {
                    View child = parent.getChildAt(i);
                    if (!slot.getDeclaringClass().isInstance(child)) continue;
                    String name = (String) slot.invoke(child);
                    if (wifi(name)) { if (wifi != -1) ambiguous = true; wifi = i; sources.add(new WeakReference<>(child)); names.add(name); }
                    else if (mobile(name)) {
                        if (lastMobile != -1 && i != lastMobile + 1) ambiguous = true;
                        if (firstMobile == -1) firstMobile = i;
                        lastMobile = i; sources.add(new WeakReference<>(child)); names.add(name);
                    }
                }
                if (ambiguous || wifi == -1 || firstMobile == -1) return;
                int[] mapped = new int[count]; int next = 0;
                for (int i = 0; i < count; i++) {
                    if (i == wifi) { for (int j = firstMobile; j <= lastMobile; j++) mapped[next++] = j; }
                    else if (i == firstMobile) mapped[next++] = wifi;
                    else if (i < firstMobile || i > lastMobile) mapped[next++] = i;
                }
                if (next == count) order = mapped;
            } catch (ReflectiveOperationException | RuntimeException unavailable) { order = null; }
        }
        boolean visible(ViewGroup parent) {
            try {
                Object nativeIgnored = ignored.get(parent);
                Set<?> ignoredSlots = nativeIgnored instanceof Set ? (Set<?>) nativeIgnored : null;
                boolean wifiVisible = false, mobileVisible = false;
                for (int i = 0; i < sources.size(); i++) {
                    View view = sources.get(i).get();
                    if (view == null) return false;
                    boolean shown = view.getVisibility() != View.GONE && Boolean.TRUE.equals(visible.invoke(view))
                            && !Boolean.TRUE.equals(blocked.invoke(view)) && (ignoredSlots == null || !ignoredSlots.contains(names.get(i)));
                    if (wifi(names.get(i))) wifiVisible = shown; else mobileVisible |= shown;
                }
                return wifiVisible && mobileVisible;
            } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
    }

    private static boolean wifi(String name) { return name != null && (name.equals("wifi") || name.startsWith("wifi_")); }
    private static boolean mobile(String name) {
        return name != null && (name.equals("mobile") || name.startsWith("mobile_")
                || name.equals("stacked_mobile") || name.startsWith("stacked_mobile_"));
    }

    /** All three shipped strategies emit one outer Row: optional roaming, signal Box, network Text. */
    public RowScope beginRow(Object viewModel) {
        if (!enabled || released || measurePolicy == null) return null;
        RowScope scope = new RowScope(rows.get()); rows.set(scope); return scope;
    }

    public final class RowScope implements AutoCloseable {
        private boolean first = true;
        private RowScope previous;
        private RowScope(RowScope previous) { this.previous = previous; }
        @Override public void close() {
            if (rows.get() == this) { if (previous == null) rows.remove(); else rows.set(previous); }
            previous = null;
        }
    }

    public boolean forceStrategy(Object composer, Object viewModel, Object strategy) {
        if (released || composer == null || viewModel == null || strategy == null) return false;
        synchronized (composerRevisions) {
            Map<Object, Map<Object, Integer>> models = composerRevisions.get(composer);
            Map<Object, Integer> strategies = models == null ? null : models.get(viewModel);
            Integer previous = strategies == null ? null : strategies.get(strategy);
            if (!enabled && previous == null) return false;
            if (previous != null && previous == revision) return false;
            if (models == null) { models = new WeakHashMap<>(); composerRevisions.put(composer, models); }
            if (strategies == null) { strategies = new WeakHashMap<>(); models.put(viewModel, strategies); }
            strategies.put(strategy, revision); return true;
        }
    }

    public Object signalModifier(Object nativeModifier) { return tagged(nativeModifier, signalTag); }
    public Object textModifier(Object nativeModifier) { return tagged(nativeModifier, textTag); }
    private static final class TagBinding {
        final Object marker;
        final WeakReference<Object> result;
        TagBinding(Object marker,Object result){this.marker=marker;this.result=new WeakReference<>(result);}
    }
    private int modifierKind(Object modifier)throws ReflectiveOperationException {
        synchronized(modifierKinds){
            Integer known=modifierKinds.get(modifier);
            if(known!=null)return known;
            int kind=(Integer)modifierFold.invoke(modifier,0,modifierVisitor);
            modifierKinds.put(modifier,kind);return kind;
        }
    }
    private Object tagged(Object nativeModifier, Object marker) {
        if (!enabled || released || nativeModifier == null || modifierThen == null || marker == null) return nativeModifier;
        try {
            int kind=modifierKind(nativeModifier),wanted=marker==signalTag?1:2;
            // Native restart callbacks retain the complete Modifier chain. Appending another
            // marker around a saved typography layer would hide its exact restore identity.
            if(kind==wanted||kind==3)return nativeModifier;
            synchronized(taggedModifiers){
                TagBinding old=taggedModifiers.get(nativeModifier);
                Object existing=old==null||old.marker!=marker?null:old.result.get();
                if(existing!=null)return existing;
                Object result=modifierThen.invoke(nativeModifier,marker);
                taggedModifiers.put(nativeModifier,new TagBinding(marker,result));return result;
            }
        }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return nativeModifier; }
    }

    /** Composer applies modifiers later. The identity marker survives that deferred native setter. */
    public void nodeChanged(Object node, Object modifier) {
        if (released || node == null) return;
        int kind = 0;
        try { if (modifierFold != null && modifier != null) kind = modifierKind(modifier); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { }
        synchronized (nodeKinds) { if (kind == 1 || kind == 2) nodeKinds.put(node, kind); else nodeKinds.remove(node); }
    }

    private int kind(Object measurable) {
        if (measurable == null) return 0;
        try {
            Method getter = measuredNode.getDeclaringClass().isInstance(measurable) ? measuredNode
                    : lookaheadNode.getDeclaringClass().isInstance(measurable) ? lookaheadNode : null;
            if (getter == null) return 0;
            Object node = getter.invoke(measurable);
            synchronized (nodeKinds) { Integer kind = nodeKinds.get(node); return kind == null ? 0 : kind; }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return 0; }
    }

    /** The RowKt hook passes straight through outside the exact OEM strategy call. */
    public Object rowPolicy(Object nativePolicy) {
        RowScope scope = rows.get();
        if (released || scope == null || !scope.first || nativePolicy == null || measurePolicy == null) return nativePolicy;
        scope.first = false;
        if (!measurePolicy.isInstance(nativePolicy)
                || !nativePolicy.getClass().getName().equals("androidx.compose.foundation.layout.RowMeasurePolicy")) return nativePolicy;
        synchronized (policyCache) {
            Object cached = policyCache.get(nativePolicy);
            if (cached != null) return cached;
            Object policy = createPolicy(nativePolicy);
            if (policyCache.size() >= 8) policyCache.remove(policyCache.keySet().iterator().next());
            policyCache.put(nativePolicy, policy); return policy;
        }
    }

    private Object createPolicy(Object nativePolicy) {
        final int policyRevision = revision;
        return Proxy.newProxyInstance(measurePolicy.getClassLoader(), new Class<?>[]{measurePolicy}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return "NetworkIconOrder:" + policyRevision;
            }
            Object[] forwarded = args;
            if (enabled && !released && args != null && args.length > 1 && args[1] instanceof List) {
                List<?> children = (List<?>) args[1];
                if (children.size() == 2 || children.size() == 3) {
                    int signal = -1, text = -1; boolean ambiguous = false;
                    for (int i = 0; i < children.size(); i++) {
                        int kind = kind(children.get(i));
                        if (kind == 1) { if (signal != -1) ambiguous = true; signal = i; }
                        else if (kind == 2) { if (text != -1) ambiguous = true; text = i; }
                    }
                    if (!ambiguous && signal >= 0 && text >= 0) {
                        forwarded = args.clone(); forwarded[1] = new SwappedTail(children, signal, text);
                    }
                }
            }
            try { return method.invoke(nativePolicy, forwarded); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }

    private static final class SwappedTail extends AbstractList<Object> {
        private final List<?> nativeChildren;
        private final int a, b;
        SwappedTail(List<?> children, int signal, int text) { nativeChildren = children; a = signal; b = text; }
        @Override public int size() { return nativeChildren.size(); }
        @Override public Object get(int index) { return nativeChildren.get(index == a ? b : index == b ? a : index); }
    }

    public void releaseRuntime() {
        released = true;
        if (enabled) { enabled = false; revision++; }
        synchronized (containers) { for (ViewGroup parent : containers.keySet()) if (parent != null) parent.requestLayout(); containers.clear(); }
        indexes.remove(); rows.remove();
        synchronized (nodeKinds) { nodeKinds.clear(); }
        synchronized (modifierKinds) { modifierKinds.clear(); }
        synchronized (taggedModifiers) { taggedModifiers.clear(); }
        synchronized (composerRevisions) { composerRevisions.clear(); }
        synchronized (policyCache) { policyCache.clear(); }
        nativeContainer = null; measurePolicy = null; slot = null; visible = null; blocked = null; ignored = null;
        measuredNode = null; lookaheadNode = null;
        modifierThen = null; modifierFold = null; signalTag = null; textTag = null; modifierVisitor = null;
    }
}
