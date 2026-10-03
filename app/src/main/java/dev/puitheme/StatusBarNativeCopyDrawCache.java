// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Invalidates only native fake frames whose dispatchDraw was actually suppressed. */
final class StatusBarNativeCopyDrawCache {
    interface Gate { boolean suppress(View frame, View source, int kind); }
    private final Gate gate;
    private final Map<View, RecordedCopy> copies = new WeakHashMap<>();

    StatusBarNativeCopyDrawCache(Gate policy) { gate = policy; }

    /** Called at the exact native dispatchDraw hook, after its ownership policy is resolved. */
    void observed(View frame, View source, int kind, boolean suppressed) {
        if (frame == null) return;
        RecordedCopy state = copies.get(frame);
        // An unmodified frame is never registered, including arbitrary Phone/native views.
        if (state == null) {
            if (!suppressed || source == null || source == frame || !frame.isAttachedToWindow()
                    || kind < StatusIconTransition.RIGHT || kind > StatusIconTransition.NOTIFICATIONS) return;
            copies.put(frame, new RecordedCopy(source, kind));
            return;
        }
        if (source != null) state.source = new WeakReference<>(source);
        state.kind = kind;
        if (state.suppressed != suppressed) {
            state.suppressed = suppressed;
            frame.invalidate();
        }
    }

    /** Alpha/transitionAlpha property updates cannot rerecord an empty child display list. */
    void refresh() {
        for (Iterator<Map.Entry<View, RecordedCopy>> it = copies.entrySet().iterator(); it.hasNext();) {
            Map.Entry<View, RecordedCopy> entry = it.next();
            View frame = entry.getKey(); RecordedCopy state = entry.getValue();
            if (frame == null || !frame.isAttachedToWindow()) { it.remove(); continue; }
            View source = state.source.get();
            boolean next = source != null && source.isAttachedToWindow() && gate != null
                    && gate.suppress(frame, source, state.kind);
            if (next != state.suppressed) {
                state.suppressed = next;
                // The recorded frame can be below the alpha owner; invalidate it, not its parent.
                frame.invalidate();
            }
            if (source == null || !source.isAttachedToWindow()) it.remove();
        }
    }

    /** Configuration/uninstall teardown makes every previously intercepted frame native again. */
    void release() {
        for (View frame : copies.keySet())
            if (frame != null && frame.isAttachedToWindow()) frame.invalidate();
        copies.clear();
    }

    private static final class RecordedCopy {
        WeakReference<View> source;
        int kind;
        boolean suppressed = true;
        RecordedCopy(View view, int value) { source = new WeakReference<>(view); kind = value; }
    }
}
