// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Map;
import java.util.HashMap;

/** A durable rollback journal is written before exposing an unconfirmed numeric value. */
public final class NumericTrial {
    public static final int SECONDS = 20;
    private static final String JOURNAL = "statusbar_numeric_trial";
    private static final Handler TIMER = new Handler(Looper.getMainLooper());
    private static boolean initialized;
    private static Context owner;
    private static SharedPreferences raw, journal;
    private static long deadline;
    private static Map<String, ?> pending;
    private static final Runnable EXPIRE = () -> { synchronized (NumericTrial.class) { rollback(); } };

    private NumericTrial() { }

    /** Cold starts discard any unconfirmed trial, even after force-stop, crash or reboot. */
    public static synchronized void recover(Context context, SharedPreferences preferences) {
        if (initialized) return;
        Context application = context.getApplicationContext();
        owner = (application == null ? context : application).createDeviceProtectedStorageContext();
        raw = preferences;
        journal = owner.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE);
        initialized = true;
        Map<String, ?> restored = journal.getAll();
        pending = restored.containsKey("key") ? new HashMap<>(restored) : null;
        if (pending != null) rollback();
    }

    public static synchronized boolean begin(Context context, SharedPreferences preferences,
            SharedPreferences guarded, String key, float value) {
        recover(context, preferences);
        if (!StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key) || !Float.isFinite(value)) return false;
        if (pending != null && !rollback()) return false;
        Map<String, ?> before = raw.getAll();
        SharedPreferences.Editor record = journal.edit().clear().putString("key", key);
        record.putBoolean("present", before.containsKey(key));
        if (before.containsKey(key)) StatusBarSettings.copyPreference(record, "value", before.get(key));
        boolean header = NotificationBigClockSettings.conflictsWith(key, value);
        record.putBoolean("header", header);
        if (header) {
            String master = NotificationBigClockSettings.MASTER;
            record.putBoolean("header_present", before.containsKey(master));
            if (before.containsKey(master)) StatusBarSettings.copyPreference(record, "header_value", before.get(master));
        }
        boolean clockScale = StatusBarSettings.CLOCK_SCALE.equals(key);
        record.putBoolean("clock_metadata", clockScale);
        if (clockScale) {
            recordClockMetadata(record, before, NativeClockMeasurement.SCALE_BASIS_VERSION, "clock_basis");
            recordClockMetadata(record, before, NativeClockMeasurement.LEGACY_SCALE_FACTOR, "clock_factor");
        }
        boolean recorded = commit(record);
        Map<String, ?> recordedValues = journal.getAll();
        pending = recordedValues.containsKey("key") ? new HashMap<>(recordedValues) : null;
        if (!recorded) { rollback(); return false; }
        deadline = SystemClock.elapsedRealtime() + SECONDS * 1000L;
        TIMER.removeCallbacks(EXPIRE);
        TIMER.postDelayed(EXPIRE, SECONDS * 1000L);
        try {
            SharedPreferences.Editor tested = guarded.edit().putFloat(key, value);
            if (clockScale) tested.putFloat(NativeClockMeasurement.SCALE_BASIS_VERSION,
                    NativeClockMeasurement.NATIVE_PIXEL_BASIS).putFloat(NativeClockMeasurement.LEGACY_SCALE_FACTOR, 0f);
            if (!commit(tested)) { rollback(); return false; }
        } catch (RuntimeException unavailable) { rollback(); return false; }
        notifyChanged();
        return true;
    }

    public static synchronized long deadline() { return deadline; }
    public static synchronized boolean active() { return initialized && pending != null; }

    /** Finish a trial before maintenance owns the next write, never leave an expiry able to replay later. */
    public static synchronized boolean cancelForReset(Context context, SharedPreferences preferences) {
        if (context == null || preferences == null
                || !ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName())) return false;
        if (!initialized) {
            Context application = context.getApplicationContext();
            owner = (application == null ? context : application).createDeviceProtectedStorageContext();
            raw = preferences;
            journal = owner.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE);
            initialized = true;
            Map<String, ?> record = journal.getAll();
            pending = record.containsKey("key") ? new HashMap<>(record) : null;
        }
        // If the following reset cannot reach disk, retain the last confirmed value rather than
        // accidentally accepting the temporary one when deleting its recovery journal.
        if(pending!=null&&!rollback())return false;
        TIMER.removeCallbacks(EXPIRE);
        if (!commit(journal.edit().clear())) {
            restoreJournal();
            if (pending != null) retryRollback();
            return false;
        }
        pending = null;
        deadline = 0;
        return true;
    }

    /** Clearing the journal is the commit: the tested setting is already durable. */
    public static synchronized boolean keep() {
        if (!active()) return false;
        if (SystemClock.elapsedRealtime() >= deadline) { rollback(); return false; }
        if (!commit(journal.edit().clear())) {
            // Android updates its in-memory preferences even when commit reports a disk failure.
            // Preserve the independent record, restore its durable marker, and revert the trial.
            restoreJournal();
            rollback();
            return false;
        }
        pending = null;
        TIMER.removeCallbacks(EXPIRE);
        deadline = 0;
        persistAndNotify();
        return true;
    }

    public static synchronized boolean rollback() {
        if (!initialized || pending == null) return true;
        Object recordedKey = pending.get("key");
        String key = recordedKey instanceof String ? (String) recordedKey : "";
        if (!StatusBarSettings.NUMERIC_DEFAULTS.containsKey(key)) {
            if (commit(journal.edit().clear())) pending = null;
            return false;
        }
        Map<String, ?> previous = pending;
        SharedPreferences.Editor restore = raw.edit();
        if (Boolean.TRUE.equals(previous.get("present"))) StatusBarSettings.copyPreference(restore, key, previous.get("value"));
        else restore.remove(key);
        if (Boolean.TRUE.equals(previous.get("header"))) {
            if (Boolean.TRUE.equals(previous.get("header_present")))
                StatusBarSettings.copyPreference(restore, NotificationBigClockSettings.MASTER, previous.get("header_value"));
            else restore.remove(NotificationBigClockSettings.MASTER);
        }
        if (Boolean.TRUE.equals(previous.get("clock_metadata"))) {
            restoreClockMetadata(restore, previous, NativeClockMeasurement.SCALE_BASIS_VERSION, "clock_basis");
            restoreClockMetadata(restore, previous, NativeClockMeasurement.LEGACY_SCALE_FACTOR, "clock_factor");
        }
        // Retain the journal on a failed write; a subsequent cold start can retry safely.
        if (!commit(restore)) { retryRollback(); return false; }
        TIMER.removeCallbacks(EXPIRE);
        deadline = 0;
        boolean cleared = commit(journal.edit().clear());
        if (cleared) pending = null;
        else { restoreJournal(); retryRollback(); }
        persistAndNotify();
        return cleared;
    }

    private static void restoreJournal() {
        if (pending == null) return;
        SharedPreferences.Editor record = journal.edit().clear();
        for (Map.Entry<String, ?> entry : pending.entrySet()) StatusBarSettings.copyPreference(record, entry.getKey(), entry.getValue());
        commit(record);
    }

    private static void recordClockMetadata(SharedPreferences.Editor record, Map<String, ?> before,
            String key, String marker) {
        record.putBoolean(marker + "_present", before.containsKey(key));
        if (before.containsKey(key)) StatusBarSettings.copyPreference(record, marker + "_value", before.get(key));
    }
    private static void restoreClockMetadata(SharedPreferences.Editor restore, Map<String, ?> previous,
            String key, String marker) {
        if (Boolean.TRUE.equals(previous.get(marker + "_present")))
            StatusBarSettings.copyPreference(restore, key, previous.get(marker + "_value"));
        else restore.remove(key);
    }

    private static void retryRollback() {
        TIMER.removeCallbacks(EXPIRE);
        TIMER.postDelayed(EXPIRE, 1000L);
    }
    private static boolean commit(SharedPreferences.Editor editor) {
        try { return editor.commit(); }
        catch (RuntimeException unavailable) { return false; }
    }

    private static void persistAndNotify() {
        SettingsSnapshot.scheduleSave(owner, raw, null);
        SettingsSnapshot.flushPending(owner);
        notifyChanged();
    }

    private static void notifyChanged() {
        try { owner.getContentResolver().notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI), null); }
        catch (RuntimeException unavailable) { /* The journal and timer remain authoritative. */ }
    }
}
