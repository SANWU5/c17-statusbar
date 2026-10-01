// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.SharedPreferences;
import android.content.Context;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Activity-only write gate; provider reads and startup migration continue using raw preferences. */
public final class ActivationGuardPreferences implements SharedPreferences {
    private final SharedPreferences raw;
    private final BooleanSupplier allowed;
    private final Runnable denied;
    private Context persistenceContext;
    private Runnable persistenceFailed;

    /** The denied callback may run on a worker thread: the Activity should use runOnUiThread. */
    public ActivationGuardPreferences(SharedPreferences raw, BooleanSupplier allowed, Runnable denied) {
        this.raw = Objects.requireNonNull(raw);
        this.allowed = Objects.requireNonNull(allowed);
        this.denied = Objects.requireNonNull(denied);
    }

    /** Keeps normal apply() immediate; disk confirmation and recovery backup run after a burst. */
    public ActivationGuardPreferences withPersistence(Context context,Runnable failed) {
        Context app=context.getApplicationContext();persistenceContext=app==null?context:app;
        persistenceFailed=failed;return this;
    }

    @Override public Map<String, ?> getAll() { return raw.getAll(); }
    @Override public String getString(String key, String fallback) { return raw.getString(key, fallback); }
    @Override public Set<String> getStringSet(String key, Set<String> fallback) { return raw.getStringSet(key, fallback); }
    @Override public int getInt(String key, int fallback) { return raw.getInt(key, fallback); }
    @Override public long getLong(String key, long fallback) { return raw.getLong(key, fallback); }
    @Override public float getFloat(String key, float fallback) { return raw.getFloat(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) {
        return StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(key) || raw.getBoolean(key, fallback);
    }
    @Override public boolean contains(String key) { return raw.contains(key); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        raw.registerOnSharedPreferenceChangeListener(listener);
    }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        raw.unregisterOnSharedPreferenceChangeListener(listener);
    }
    @Override public Editor edit() { return new GuardedEditor(raw.edit()); }

    private final class GuardedEditor implements Editor {
        private final Editor pending;
        private final Map<String,Object> headerEdits=new LinkedHashMap<>();
        private boolean discarded;
        GuardedEditor(Editor pending) { this.pending = pending; }
        @Override public synchronized Editor putString(String key, String value) { if (!discarded) { pending.putString(key, value); record(key,value); } return this; }
        @Override public synchronized Editor putStringSet(String key, Set<String> value) { if (!discarded) { pending.putStringSet(key, value); record(key,value); } return this; }
        @Override public synchronized Editor putInt(String key, int value) { if (!discarded) { pending.putInt(key, value); record(key,value); } return this; }
        @Override public synchronized Editor putLong(String key, long value) { if (!discarded) { pending.putLong(key, value); record(key,value); } return this; }
        @Override public synchronized Editor putFloat(String key, float value) { if (!discarded) { pending.putFloat(key, value); record(key,value); } return this; }
        @Override public synchronized Editor putBoolean(String key, boolean value) { if (!discarded) { pending.putBoolean(key, value); record(key,value); } return this; }
        @Override public synchronized Editor remove(String key) { if (!discarded) { pending.remove(key); headerEdits.remove(key); } return this; }
        @Override public synchronized Editor clear() { if (!discarded) pending.clear(); return this; }
        private void record(String key,Object value) {
            if(value==null){headerEdits.remove(key);return;}
            if(NotificationBigClockSettings.MASTER.equals(key)||key!=null&&(key.startsWith("shade_clock_")||key.startsWith(CarrierPanels.NOTIFICATION+"_")))
                headerEdits.put(key,value);
        }
        private void resolveHeaderConflicts() {
            pending.putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN, true);
            // Bulk import explicitly chooses the big-clock mode; key iteration order cannot override it.
            if(headerEdits.get(NotificationBigClockSettings.MASTER) instanceof Boolean)return;
            for(Map.Entry<String,Object> edit:headerEdits.entrySet())if(NotificationBigClockSettings.conflictsWith(edit.getKey(),edit.getValue())) {
                pending.putBoolean(NotificationBigClockSettings.MASTER,false);return;
            }
        }
        private boolean mayWrite() {
            if (!discarded && allowed.getAsBoolean()) return true;
            // An editor rejected while inactive must never revive its pending changes later.
            discarded = true;
            headerEdits.clear();
            denied.run();
            return false;
        }
        @Override public synchronized boolean commit() {
            if(!mayWrite())return false;
            resolveHeaderConflicts();
            try {
                boolean saved=pending.commit();
                if(saved&&persistenceContext!=null)SettingsSnapshot.scheduleSave(persistenceContext,raw,persistenceFailed);
                return saved;
            } finally {headerEdits.clear();}
        }
        @Override public synchronized void apply() {
            if(!mayWrite())return;
            resolveHeaderConflicts();
            try {
                pending.apply();
                if(persistenceContext!=null)SettingsSnapshot.scheduleSave(persistenceContext,raw,persistenceFailed);
            } finally {headerEdits.clear();}
        }
    }
}
