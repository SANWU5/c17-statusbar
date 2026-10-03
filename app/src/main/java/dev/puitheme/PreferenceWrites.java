// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** SharedPreferences updates memory even on a failed commit; never publish that failed edit. */
final class PreferenceWrites {
    private static final Map<SharedPreferences, State> STATES = new WeakHashMap<>();
    private static final class State {
        long revision;
        int writing;
        boolean failed;
        Map<String, Object> rejected, originals;
    }
    private PreferenceWrites() { }
    private static State state(SharedPreferences raw) {
        synchronized (STATES) { return STATES.computeIfAbsent(raw, ignored -> new State()); }
    }
    static long revision(SharedPreferences raw) {
        synchronized (raw) { return state(raw).revision; }
    }
    static boolean notificationAllowed(SharedPreferences raw) {
        if (raw == null) return false;
        synchronized (raw) { State state = state(raw); return !state.failed && state.writing == 0; }
    }
    static Map<String, Object> values(SharedPreferences raw) {
        synchronized (raw) {
            Map<String, Object> current = copy(raw.getAll());
            State state = state(raw);
            if (state.rejected != null) for (String key : state.rejected.keySet()) {
                if (!matches(current, key, state.rejected.get(key))) continue;
                Object original = state.originals.get(key);
                if (original == null) current.remove(key); else current.put(key, original);
            }
            return current;
        }
    }
    static boolean commit(SharedPreferences raw, SharedPreferences.Editor editor,
            Map<String, Object> changes, boolean clear) {
        synchronized (raw) {
            Map<String, Object> before = values(raw);
            Map<String, Object> expected = new LinkedHashMap<>();
            if (clear) for (String key : before.keySet()) expected.put(key, null);
            expected.putAll(changes);
            State state = state(raw);
            state.writing++; state.revision++;
            boolean saved = false;
            try { saved = editor.commit(); }
            catch (RuntimeException failure) {
                ModuleDiagnostics.error("settings", "Settings commit failed; restoring the previous edit", failure);
            } finally {
                if (!saved) restoreOwned(raw, before, expected, state);
                else { state.rejected = null; state.originals = null; }
                state.failed = !saved; state.writing--;
            }
            return saved;
        }
    }
    static void apply(SharedPreferences raw, SharedPreferences.Editor editor,
            Map<String, Object> changes, boolean clear) {
        synchronized (raw) {
            Map<String, Object> before = values(raw);
            Map<String, Object> expected = new LinkedHashMap<>();
            if (clear) for (String key : before.keySet()) expected.put(key, null);
            expected.putAll(changes);
            State state = state(raw);
            state.writing++; state.revision++;
            boolean accepted = false;
            try { editor.apply(); accepted = true; }
            finally {
                if (!accepted) restoreOwned(raw, before, expected, state);
                else { state.rejected = null; state.originals = null; }
                state.failed = !accepted; state.writing--;
            }
        }
    }
    private static void restoreOwned(SharedPreferences raw, Map<String, Object> before,
            Map<String, Object> expected, State state) {
        state.rejected = new LinkedHashMap<>(); state.originals = new LinkedHashMap<>();
        try {
            Map<String, Object> current = copy(raw.getAll());
            SharedPreferences.Editor restore = raw.edit();
            boolean changed = false;
            for (String key : expected.keySet()) {
                Object projected = expected.get(key), original = before.get(key);
                if (!matches(current, key, projected) || matches(before, key, projected)) continue;
                // Another writer's different value wins; only this editor's unchanged value is owned.
                state.rejected.put(key, projected); state.originals.put(key, original);
                if (original == null) restore.remove(key);
                else StatusBarSettings.copyPreference(restore, key, original);
                changed = true;
            }
            if (changed && !restore.commit())
                ModuleDiagnostics.info("settings", "Previous settings restored in memory; disk confirmation remains unavailable");
        } catch (RuntimeException failure) {
            // The independent original values remain available to provider reads after a partial restore.
            if (state.rejected.isEmpty()) for (String key : expected.keySet()) {
                state.rejected.put(key, expected.get(key)); state.originals.put(key, before.get(key));
            }
            ModuleDiagnostics.error("settings", "Previous settings disk restoration unavailable", failure);
        }
    }
    private static boolean matches(Map<String, ?> values, String key, Object expected) {
        return expected == null ? !values.containsKey(key) : values.containsKey(key) && Objects.equals(values.get(key), expected);
    }
    private static Map<String, Object> copy(Map<String, ?> values) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value instanceof Set ? new HashSet<>((Set<?>) value) : value);
        }
        return result;
    }
}
