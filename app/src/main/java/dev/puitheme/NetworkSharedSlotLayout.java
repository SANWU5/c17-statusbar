// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Instance bridge between measured native mobile fragments and the outer status-bar flow.
 * All coordinates are physical pixels inside the original mobile host. Neither this
 * bridge nor its backends change that host's translation, subscription or visibility. */
public final class NetworkSharedSlotLayout {
    interface Backend {
        Frame frame(View host);
        boolean target(Target value);
        void restore();
        default void disabled() { restore(); }
        default void nativeMeasured() { }
        default void nativeLaidOut() { }
        default void nativeMeasureFailed() { }
        default void beginHullMeasure() { }
        default void endHullMeasure() { }
    }
    public static final class Frame {
        public final boolean valid, labelPresent;
        public final int labelWidthPx, signalWidthPx, nativeLabelLeftPx, nativeSignalLeftPx, nativeHostWidthPx;
        /** Current measured placement, rather than the native-order baseline. */
        public final int currentSignalLeftPx;
        private final WeakReference<View> host;
        private final Backend backend;
        Frame(View host, Backend backend, boolean valid, boolean present, int labelWidth, int signalWidth,
                int labelLeft, int signalLeft, int nativeWidth) {
            this(host, backend, valid, present, labelWidth, signalWidth, labelLeft, signalLeft, nativeWidth, signalLeft);
        }
        Frame(View host, Backend backend, boolean valid, boolean present, int labelWidth, int signalWidth,
                int labelLeft, int signalLeft, int nativeWidth, int currentSignalLeft) {
            this.host = new WeakReference<>(host); this.backend = backend; this.valid = valid;
            labelPresent = present; labelWidthPx = labelWidth; signalWidthPx = signalWidth;
            nativeLabelLeftPx = labelLeft; nativeSignalLeftPx = signalLeft; nativeHostWidthPx = nativeWidth;
            currentSignalLeftPx = currentSignalLeft;
        }
        public View host() { return host.get(); }
    }
    static final class Target {
        final int width, labelLeft, signalLeft;
        final boolean labelVisible, signalVisible;
        Target(int width, int label, int signal, boolean labelVisible, boolean signalVisible) {
            this.width = width; labelLeft = label; signalLeft = signal;
            this.labelVisible = labelVisible; this.signalVisible = signalVisible;
        }
        boolean same(Target other) { return other != null && width == other.width && labelLeft == other.labelLeft
                && signalLeft == other.signalLeft && labelVisible == other.labelVisible && signalVisible == other.signalVisible; }
    }
    private final Map<View, Backend> backends = new WeakHashMap<>();
    // Only live synchronous measure scopes retain their participants. A Row can
    // be replaced inside measure; both old and new backend must leave the scope.
    private final Map<View, ArrayList<ArrayList<Backend>>> hullMeasures = new WeakHashMap<>();
    private final Map<View, ArrayList<ArrayList<Backend>>> nativeMeasures = new WeakHashMap<>();
    // A Compose replay may arrive after the original backend is removed or the
    // feature toggles. Remember that this host has held our compact hull until
    // the host itself is collected, so a new row needs a real native traversal.
    private final Map<View, Boolean> hullHosts = new WeakHashMap<>();
    private Class<?> nativeContainer, displayable;
    private Method slot;
    private boolean enabled, released;
    private String lastFailure = "none";
    public String failure() { return lastFailure; }

    public void resolve(ClassLoader loader) throws ReflectiveOperationException {
        nativeContainer = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
        displayable = loader.loadClass("com.android.systemui.statusbar.StatusIconDisplayable");
        slot = displayable.getMethod("getSlot");
    }
    public void configure(boolean value) {
        value &= !released;
        if (enabled == value) return;
        enabled = value;
        if (!enabled) for (Backend backend : new ArrayList<>(backends.values())) backend.disabled();
    }
    public boolean enabled() { return enabled && !released; }
    /** Exact native mobile child of StatusIconContainer; never an arbitrary nearby row. */
    View host(View anchor) {
        if (!enabled() || slot == null) return null;
        View current = anchor;
        for (int depth = 0; current != null && depth < 16; depth++) {
            ViewParent parent = current.getParent();
            if (parent instanceof ViewGroup && nativeContainer.isInstance(parent) && displayable.isInstance(current)) {
                try {
                    Object name = slot.invoke(current);
                    if (name instanceof String && mobile((String) name)) return current;
                } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
                return null;
            }
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
    private static boolean mobile(String name) { return name.equals("mobile") || name.startsWith("mobile_")
            || name.equals("stacked_mobile") || name.startsWith("stacked_mobile_"); }
    /** Physical native layout offset, excluding all transient translations/animation layers. */
    static int offset(View child, View ancestor) {
        long left = 0;
        for (int depth = 0; child != null && depth < 16; depth++) {
            if (child == ancestor) return left >= 0 && left <= 65535 ? (int) left : -1;
            left += child.getLeft();
            ViewParent parent = child.getParent(); child = parent instanceof View ? (View) parent : null;
        }
        return -1;
    }
    void register(View host, Backend backend) {
        if (!enabled() || host == null || backend == null) return;
        Backend previous = backends.put(host, backend);
        if (previous != null && previous != backend) previous.restore();
        ArrayList<ArrayList<Backend>> scopes = hullMeasures.get(host);
        if (scopes != null) for (ArrayList<Backend> participants : scopes) {
            boolean present = false;
            for (Backend participant : participants) if (participant == backend) { present = true; break; }
            if (!present) { participants.add(backend); backend.beginHullMeasure(); }
        }
    }
    /** Bracket only C17's exact host-hull measure, always with finally. */
    public void beginHullMeasure(View host) {
        if (host == null || released) return;
        hullHosts.put(host, Boolean.TRUE);
        ArrayList<ArrayList<Backend>> scopes = hullMeasures.get(host);
        if (scopes == null) { scopes = new ArrayList<>(); hullMeasures.put(host, scopes); }
        ArrayList<Backend> participants = new ArrayList<>(); scopes.add(participants);
        Backend backend = backends.get(host);
        if (backend != null) { participants.add(backend); backend.beginHullMeasure(); }
    }
    public void endHullMeasure(View host) {
        ArrayList<ArrayList<Backend>> scopes = hullMeasures.get(host);
        if (scopes == null || scopes.isEmpty()) return;
        ArrayList<Backend> participants = scopes.remove(scopes.size() - 1);
        if (scopes.isEmpty()) hullMeasures.remove(host);
        for (Backend backend : participants) backend.endHullMeasure();
    }
    /** A real root traversal, not a Compose-only replay of our cached hull. */
    public void beginNativeMeasure(View root) {
        if (root == null || released) return;
        ArrayList<ArrayList<Backend>> scopes = nativeMeasures.get(root);
        if (scopes == null) { scopes = new ArrayList<>(); nativeMeasures.put(root, scopes); }
        scopes.add(new ArrayList<>());
    }
    public void endNativeMeasure(View root) { endNativeMeasure(root, true); }
    /** A parent may throw after a Row succeeds. Invalidate only the Row backends
     * actually visited by this traversal, even if they moved or were replaced. */
    public void endNativeMeasure(View root, boolean succeeded) {
        ArrayList<ArrayList<Backend>> scopes = nativeMeasures.get(root);
        if (scopes == null || scopes.isEmpty()) return;
        ArrayList<Backend> participants = scopes.remove(scopes.size() - 1);
        if (scopes.isEmpty()) nativeMeasures.remove(root);
        if (!succeeded) for (Backend participant : participants) {
            try { participant.nativeMeasureFailed(); } catch (RuntimeException ignored) { }
        }
    }
    void nativeParticipant(View host, Backend backend) {
        for (int depth = 0; host != null && depth < 16; depth++) {
            ArrayList<ArrayList<Backend>> scopes = nativeMeasures.get(host);
            if (scopes != null) for (ArrayList<Backend> participants : scopes) {
                boolean present = false;
                for (Backend participant : participants) if (participant == backend) { present = true; break; }
                if (!present) participants.add(backend);
            }
            ViewParent parent = host.getParent(); host = parent instanceof View ? (View) parent : null;
        }
    }
    boolean mayReplayHull(View host) { return host != null && hullHosts.containsKey(host); }
    void preserveHullWitness(View host) { if (host != null) hullHosts.put(host, Boolean.TRUE); }
    boolean nativeMeasuring(View host) {
        for (int depth = 0; host != null && depth < 16; depth++) {
            if (nativeMeasures.containsKey(host)) return true;
            ViewParent parent = host.getParent(); host = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    void unregister(Backend backend) {
        for (Map.Entry<View, Backend> entry : new ArrayList<>(backends.entrySet()))
            if (entry.getValue() == backend) { backend.restore(); backends.remove(entry.getKey()); }
    }
    public Frame frame(View nativeMobileHost) {
        if (!enabled() || nativeMobileHost == null || host(nativeMobileHost) != nativeMobileHost) return null;
        Backend backend = backends.get(nativeMobileHost);
        if (backend == null) return null;
        try { return backend.frame(nativeMobileHost); }
        catch (RuntimeException unsupported) { backend.restore(); return null; }
    }
    /** Notify backends after a real root traversal; the host witness remains
     * until the host is released because Compose may replay its former hull. */
    public void nativeMeasured(View root) {
        nativeTraversal(root, true);
    }
    public void nativeLaidOut(View root) {
        nativeTraversal(root, false);
    }
    private void nativeTraversal(View root, boolean measured) {
        if (!enabled() || root == null) return;
        for (Map.Entry<View, Backend> entry : new ArrayList<>(backends.entrySet())) {
            View current = entry.getKey();
            for (int depth = 0; current != null && depth < 16; depth++) {
                if (current == root) {
                    if (measured) entry.getValue().nativeMeasured(); else entry.getValue().nativeLaidOut();
                    break;
                }
                ViewParent parent = current.getParent(); current = parent instanceof View ? (View) parent : null;
            }
        }
    }
    /** Store a native placement target and invalidate its measurement once. The outer
     * flow owns synchronous host.measure(EXACTLY hullWidth) and native host.layout(). */
    public boolean apply(Frame frame, int hullWidthPx, int labelLeftPx, int signalLeftPx,
            boolean labelVisible, boolean signalVisible) {
        return apply(frame, hullWidthPx, labelLeftPx, signalLeftPx, labelVisible, signalVisible, false);
    }
    /** A deliberate negative outer margin may overlap fragments. The scene adapter
     * opts in only for that signed spacing; hull and native visibility guards remain. */
    public boolean apply(Frame frame, int hullWidthPx, int labelLeftPx, int signalLeftPx,
            boolean labelVisible, boolean signalVisible, boolean allowOverlap) {
        lastFailure = "bridge_validation";
        if (!enabled() || frame == null || !frame.valid || frame.host() == null
                || backends.get(frame.host()) != frame.backend || hullWidthPx < 0 || hullWidthPx > 65535
                || labelVisible && (!fits(labelLeftPx, frame.labelWidthPx, hullWidthPx) || !frame.labelPresent)
                || signalVisible && !fits(signalLeftPx, frame.signalWidthPx, hullWidthPx)
                || !allowOverlap && labelVisible && signalVisible && frame.labelWidthPx > 0 && frame.signalWidthPx > 0
                    && labelLeftPx < (long) signalLeftPx + frame.signalWidthPx
                    && signalLeftPx < (long) labelLeftPx + frame.labelWidthPx) return false;
        try {
            boolean applied = frame.backend.target(new Target(hullWidthPx, labelLeftPx, signalLeftPx, labelVisible, signalVisible));
            lastFailure = applied ? "none" : "native_target_rejected"; return applied;
        }
        catch (RuntimeException unsupported) { frame.backend.restore(); lastFailure = "native_target_exception"; return false; }
    }
    private static boolean fits(int left, int width, int total) { return left >= 0 && width >= 0 && (long) left + width <= total; }
    public void restore(View host) { Backend backend = backends.get(host); if (backend != null) backend.restore(); }
    public void releaseRuntime() {
        configure(false); released = true;
        for (View host : new ArrayList<>(hullMeasures.keySet())) while (hullMeasures.containsKey(host)) endHullMeasure(host);
        nativeMeasures.clear(); hullHosts.clear(); backends.clear(); nativeContainer = null; displayable = null; slot = null;
    }
    public String summary() { return "enabled=" + enabled() + ", measuredHosts=" + backends.size(); }
}
