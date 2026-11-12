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
        final Map<String, Object> rejected = new LinkedHashMap<>(), originals = new LinkedHashMap<>();
        final Map<String, Long> owners = new HashMap<>();
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
            for (String key : state.rejected.keySet()) {
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
            if (clear) for (String key : raw.getAll().keySet()) expected.put(key, null);
            expected.putAll(changes);
            State state = state(raw);
            state.writing++; long operation = ++state.revision;
            boolean saved = false;
            try {
                repairPending(raw, editor, expected, clear, state);
                for (String key : expected.keySet()) state.owners.put(key, operation);
                saved = editor.commit();
            }
            catch (RuntimeException failure) {
                ModuleDiagnostics.error("settings", "Settings commit failed; restoring the previous edit", failure);
            } finally {
                if (!saved) restoreOwned(raw, before, expected, state, operation);
                else acceptOwned(expected, state, operation);
                state.failed = !saved || !state.rejected.isEmpty(); state.writing--;
            }
            return saved;
        }
    }
    static void apply(SharedPreferences raw, SharedPreferences.Editor editor,
            Map<String, Object> changes, boolean clear) {
        synchronized (raw) {
            Map<String, Object> before = values(raw);
            Map<String, Object> expected = new LinkedHashMap<>();
            if (clear) for (String key : raw.getAll().keySet()) expected.put(key, null);
            expected.putAll(changes);
            State state = state(raw);
            state.writing++; long operation = ++state.revision;
            boolean accepted = false;
            try {
                repairPending(raw, editor, expected, clear, state);
                for (String key : expected.keySet()) state.owners.put(key, operation);
                editor.apply(); accepted = true;
            }
            finally {
                if (!accepted) restoreOwned(raw, before, expected, state, operation);
                else acceptOwned(expected, state, operation);
                state.failed = !accepted || !state.rejected.isEmpty(); state.writing--;
            }
        }
    }
    private static void repairPending(SharedPreferences raw, SharedPreferences.Editor editor,
            Map<String, Object> expected, boolean clear, State state) {
        Map<String, ?> current = raw.getAll();
        for (String key : new HashSet<>(state.rejected.keySet())) {
            if (!matches(current, key, state.rejected.get(key))) {
                state.rejected.remove(key); state.originals.remove(key); continue;
            }
            if (clear || expected.containsKey(key)) continue;
            // Repair in the SAME commit: an unrelated successful write must never persist a rejected value.
            Object original = state.originals.get(key);
            if (original == null) editor.remove(key); else StatusBarSettings.copyPreference(editor, key, original);
            expected.put(key, original);
        }
    }
    private static void acceptOwned(Map<String, Object> expected, State state, long operation) {
        for (String key : expected.keySet()) if (Objects.equals(state.owners.get(key), operation)) {
            state.rejected.remove(key); state.originals.remove(key);
        }
    }
    private static void restoreOwned(SharedPreferences raw, Map<String, Object> before,
            Map<String, Object> expected, State state, long operation) {
        try {
            Map<String, Object> current = copy(raw.getAll());
            Map<String, Object> restoreValues = new LinkedHashMap<>();
            for (String key : expected.keySet()) {
                Object projected = expected.get(key), original = before.get(key);
                if (!Objects.equals(state.owners.get(key), operation)) continue;
                if (!matches(current, key, projected) || matches(before, key, projected)) continue;
                // A later guarded writer wins even if it writes the same value (A -> B -> A).
                state.rejected.put(key, projected); state.originals.put(key, original);
                restoreValues.put(key, original);
            }
            if (restoreValues.isEmpty()) return;
            SharedPreferences.Editor restore = raw.edit();
            for (Map.Entry<String, Object> entry : restoreValues.entrySet()) {
                if (entry.getValue() == null) restore.remove(entry.getKey());
                else StatusBarSettings.copyPreference(restore, entry.getKey(), entry.getValue());
            }
            if (!restore.commit())
                ModuleDiagnostics.info("settings", "Previous settings restored in memory; disk confirmation remains unavailable");
        } catch (RuntimeException failure) {
            // The independent original values remain available to provider reads after a partial restore.
            for (String key : expected.keySet()) if (Objects.equals(state.owners.get(key), operation)
                    && !state.rejected.containsKey(key) && !matches(before, key, expected.get(key))) {
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
