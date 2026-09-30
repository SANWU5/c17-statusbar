// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Shares temporary clipping changes across features and restores them after the last owner. */
public final class OverflowControls {
    private static final OverflowControls SHARED = new OverflowControls();
    private final Map<View, Snapshot> snapshots = new WeakHashMap<>();
    private final Map<View, Lease> owners = new WeakHashMap<>();
    // This list keeps the lease, not the owner, alive so collected owners can release their ancestors.
    private final List<Lease> leases = new ArrayList<>();
    private OverflowControls() { }

    private static final class Snapshot {
        final Rect bounds;
        final boolean children, padding;
        int users;
        Snapshot(View view) {
            Rect clip = view.getClipBounds();
            bounds = clip == null ? null : new Rect(clip);
            children = view instanceof ViewGroup && ((ViewGroup) view).getClipChildren();
            padding = view instanceof ViewGroup && ((ViewGroup) view).getClipToPadding();
        }
    }
    private static final class Lease {
        final WeakReference<View> owner;
        final List<WeakReference<View>> nodes = new ArrayList<>();
        Lease(View owner) { this.owner = new WeakReference<>(owner); }
    }

    public static OverflowControls shared() { return SHARED; }

    /** Repeating an acquisition refreshes native flags without increasing its reference count. */
    public synchronized void acquire(View owner, boolean includeTree) {
        sweep();
        if (owner == null) return;
        List<View> nodes = collect(owner, includeTree);
        Lease previous = owners.get(owner);
        if (!same(previous, nodes)) {
            if (previous != null) { owners.remove(owner); leases.remove(previous); restore(previous); }
            Lease next = new Lease(owner);
            for (View node : nodes) {
                Snapshot state = snapshots.get(node);
                if (state == null) { state = new Snapshot(node); snapshots.put(node, state); }
                state.users++;
                next.nodes.add(new WeakReference<>(node));
            }
            owners.put(owner, next);
            leases.add(next);
        }
        for (View node : nodes) {
            if (node.getClipBounds() != null) node.setClipBounds(null);
            if (node instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) node;
                if (group.getClipChildren()) group.setClipChildren(false);
                if (group.getClipToPadding()) group.setClipToPadding(false);
            }
        }
    }

    public synchronized void release(View owner) {
        sweep();
        if (owner == null) return;
        Lease previous = owners.remove(owner);
        if (previous == null) return;
        leases.remove(previous);
        restore(previous);
    }

    private void sweep() {
        for (Iterator<Lease> iterator = leases.iterator(); iterator.hasNext();) {
            Lease lease = iterator.next();
            if (lease.owner.get() == null) { iterator.remove(); restore(lease); }
        }
    }

    private void restore(Lease lease) {
        for (WeakReference<View> reference : lease.nodes) {
            View view = reference.get();
            Snapshot state = view == null ? null : snapshots.get(view);
            if (state == null || --state.users != 0) continue;
            view.setClipBounds(state.bounds == null ? null : new Rect(state.bounds));
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                group.setClipChildren(state.children);
                group.setClipToPadding(state.padding);
            }
            snapshots.remove(view);
        }
    }

    private static boolean same(Lease lease, List<View> nodes) {
        if (lease == null || lease.nodes.size() != nodes.size()) return false;
        for (int index = 0; index < nodes.size(); index++)
            if (lease.nodes.get(index).get() != nodes.get(index)) return false;
        return true;
    }

    private static List<View> collect(View owner, boolean includeTree) {
        List<View> result = new ArrayList<>();
        IdentityHashMap<View, Boolean> seen = new IdentityHashMap<>();
        if (includeTree) tree(owner, 0, result, seen);
        else add(owner, result, seen);
        ViewParent parent = owner.getParent();
        for (int depth = 0; parent instanceof View && depth < 12; depth++) {
            View view = (View) parent;
            add(view, result, seen);
            parent = view.getParent();
        }
        return result;
    }

    private static void tree(View view, int depth, List<View> result, IdentityHashMap<View, Boolean> seen) {
        if (depth > 8 || seen.containsKey(view)) return;
        add(view, result, seen);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++)
                tree(group.getChildAt(index), depth + 1, result, seen);
        }
    }

    private static void add(View view, List<View> result, IdentityHashMap<View, Boolean> seen) {
        if (!seen.containsKey(view)) { seen.put(view, true); result.add(view); }
    }
}
