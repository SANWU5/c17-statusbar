// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Complete settings reads and one debounced, app-private durable recovery snapshot. */
public final class SettingsSnapshot {
    private static final String READ_METHOD="read_statusbar_settings";
    private static final String BACKUP_PREFS="statusbar_settings_last_good";
    private static final String BACKUP_SCHEMA="_c17_snapshot_schema";
    private static final int SCHEMA=1;
    private static final long SAVE_DELAY_MS=500L;
    private static final int MAX_BACKUP_ENTRIES=2048;
    private static final long MAX_BACKUP_BYTES=512L*1024L;
    private static final Object LOCK=new Object();
    private static final Map<ContentResolver,Bundle> APPLIED=new WeakHashMap<>();
    private static final Map<SharedPreferences,Map<String,Object>> DURABLE=new WeakHashMap<>();
    private static final Map<SharedPreferences,PendingSave> SAVES=new IdentityHashMap<>();
    private static final ScheduledThreadPoolExecutor WRITER=new ScheduledThreadPoolExecutor(1,task->{
        Thread thread=new Thread(task,"C17-settings-persistence");thread.setDaemon(true);return thread;
    });
    static { WRITER.setRemoveOnCancelPolicy(true); }

    private SettingsSnapshot(){}

    /**
     * Invalid/unavailable IPC retains the last successfully applied snapshot in this process.
     * A fresh SystemUI process has no applied cache: null keeps its existing native/default state
     * until a complete provider read succeeds. The provider's private backup survives app restart.
     */
    public static Bundle read(Context context) {
        if(context==null)return null;
        ContentResolver resolver=context.getContentResolver();
        try {
            Bundle candidate=resolver.call(Uri.parse(StatusBarSettings.CONTENT_URI),READ_METHOD,null,null);
            if(complete(candidate))return new Bundle(candidate);
            ModuleDiagnostics.info("settings","Incomplete settings snapshot; retaining last applied configuration");
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings snapshot unavailable; retaining last applied configuration",unavailable);
        }
        synchronized(LOCK) { Bundle previous=APPLIED.get(resolver);return previous==null?null:new Bundle(previous); }
    }

    /** Call only after all runtime consumers accepted the complete snapshot. */
    public static void rememberApplied(Context context,Bundle snapshot) {
        if(context==null||!complete(snapshot))return;
        synchronized(LOCK) { APPLIED.put(context.getContentResolver(),new Bundle(snapshot)); }
    }

    public static Bundle lastApplied(Context context) {
        if(context==null)return null;
        synchronized(LOCK) { Bundle saved=APPLIED.get(context.getContentResolver());return saved==null?null:new Bundle(saved); }
    }

    /** Only notification-driven reads may skip; rebinding a new native consumer still applies. */
    public static boolean matchesApplied(Context context,Bundle candidate) {
        if(context==null||candidate==null)return false;
        synchronized(LOCK) {
            Bundle saved=APPLIED.get(context.getContentResolver());
            if(saved==null||!saved.keySet().equals(candidate.keySet()))return false;
            for(String key:saved.keySet())if(!java.util.Objects.equals(saved.get(key),candidate.get(key)))return false;
            return true;
        }
    }

    /** Reject missing/wrong types; false, zero, transparent colors and empty strings are valid. */
    public static boolean complete(Bundle snapshot) {
        if(snapshot==null)return false;
        try {
            for(String key:StatusBarSettings.NUMERIC_DEFAULTS.keySet()) {
                Object value=snapshot.get(key);
                if(!(value instanceof Float)||!Float.isFinite((Float)value))return false;
            }
            for(String key:StatusBarSettings.COLOR_DEFAULTS.keySet()) {
                if(!(snapshot.get(key) instanceof Integer)
                        ||!(snapshot.get(StatusBarSettings.alphaKey(key)) instanceof Boolean))return false;
            }
            for(String key:StatusBarSettings.STRING_DEFAULTS.keySet())if(!(snapshot.get(key) instanceof String))return false;
            for(String key:StatusBarSettings.BOOLEAN_DEFAULTS.keySet())if(!(snapshot.get(key) instanceof Boolean))return false;
            return true;
        } catch(RuntimeException malformed) { return false; }
    }

    /** Missing raw keys intentionally use defaults; malformed present keys never become defaults. */
    static Bundle fromPreferences(Map<String,?> supplied) {
        if(supplied==null)return null;
        Map<String,?> values=new HashMap<>(supplied);
        if(!validStoredTypes(values))return null;
        Bundle result=new Bundle();
        for(Map.Entry<String,Float> entry:StatusBarSettings.NUMERIC_DEFAULTS.entrySet())
            result.putFloat(entry.getKey(),StatusBarSettings.settingNumber(values,entry.getKey(),entry.getValue()));
        for(String key:StatusBarSettings.COLOR_DEFAULTS.keySet()) {
            result.putInt(key,StatusBarSettings.color(values,key));
            result.putBoolean(StatusBarSettings.alphaKey(key),StatusBarSettings.customAlpha(values,key));
        }
        for(String key:StatusBarSettings.STRING_DEFAULTS.keySet())result.putString(key,StatusBarSettings.string(values,key));
        for(String key:StatusBarSettings.BOOLEAN_DEFAULTS.keySet())result.putBoolean(key,StatusBarSettings.bool(values,key));
        return complete(result)?result:null;
    }

    private static boolean validStoredTypes(Map<String,?> values) {
        for(String key:StatusBarSettings.NUMERIC_DEFAULTS.keySet())if(values.containsKey(key)) {
            Object value=values.get(key);
            if(!(value instanceof Number)||!Float.isFinite(((Number)value).floatValue()))return false;
        }
        for(String key:StatusBarSettings.COLOR_DEFAULTS.keySet()) {
            if(values.containsKey(key)&&!(values.get(key) instanceof Integer))return false;
            String alpha=StatusBarSettings.alphaKey(key);
            if(values.containsKey(alpha)&&!(values.get(alpha) instanceof Boolean))return false;
        }
        for(String key:StatusBarSettings.STRING_DEFAULTS.keySet())
            if(values.containsKey(key)&&!(values.get(key) instanceof String))return false;
        for(String key:StatusBarSettings.BOOLEAN_DEFAULTS.keySet())
            if(values.containsKey(key)&&!(values.get(key) instanceof Boolean))return false;
        return true;
    }

    /** Called under StatusBarSettings' startup lock; never overwrites a nonempty current store. */
    static void recoverIfEmpty(Context context,SharedPreferences raw) {
        if(!raw.getAll().isEmpty())return;
        try {
            Map<String,Object> backup=backupValues(context);
            if(backup==null||backup.isEmpty()||fromPreferences(backup)==null)return;
            // Restore the original raw keys, including legacy and local font metadata, not defaults.
            SharedPreferences.Editor editor=raw.edit();
            for(Map.Entry<String,Object> entry:backup.entrySet())StatusBarSettings.copyPreference(editor,entry.getKey(),entry.getValue());
            editor.apply();
            scheduleSave(context,raw,null,0L);
            ModuleDiagnostics.info("settings","Empty settings store recovered from last durable snapshot");
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings recovery unavailable; existing preferences retained",unavailable);
        }
    }

    static Bundle durableSnapshot(Context context) {
        try { Map<String,Object> backup=backupValues(context);return backup==null?null:fromPreferences(backup); }
        catch(RuntimeException unavailable) { return null; }
    }

    private static Map<String,Object> backupValues(Context context) {
        SharedPreferences backup=storageContext(context).getSharedPreferences(BACKUP_PREFS,Context.MODE_PRIVATE);
        synchronized(LOCK) {
            if(DURABLE.containsKey(backup)) {
                Map<String,Object> saved=DURABLE.get(backup);return saved==null?null:copyValues(saved);
            }
        }
        Map<String,?> stored=backup.getAll();
        Map<String,Object> result=null;
        if(Integer.valueOf(SCHEMA).equals(stored.get(BACKUP_SCHEMA))) {
            result=copyValues(stored);result.remove(BACKUP_SCHEMA);
            if(result.isEmpty()||!bounded(result)||fromPreferences(result)==null)result=null;
        }
        synchronized(LOCK) {
            if(!DURABLE.containsKey(backup))DURABLE.put(backup,result);
            Map<String,Object> saved=DURABLE.get(backup);return saved==null?null:copyValues(saved);
        }
    }

    static void scheduleSave(Context context,SharedPreferences raw,Runnable failed) {
        scheduleSave(context,raw,failed,SAVE_DELAY_MS);
    }

    private static void scheduleSave(Context context,SharedPreferences raw,Runnable failed,long delay) {
        if(context==null||raw==null||!"dev.puitheme.iosstatusbar".equals(context.getPackageName()))return;
        Context storage=storageContext(context);
        synchronized(LOCK) {
            PendingSave previous=SAVES.get(raw);
            if(previous!=null&&previous.future!=null)previous.future.cancel(false);
            Runnable failure=failed!=null?failed:previous==null?null:previous.failed;
            PendingSave next=new PendingSave(storage,raw,failure);
            SAVES.put(raw,next);
            next.future=WRITER.schedule(()->save(next),delay,TimeUnit.MILLISECONDS);
        }
    }

    /** Requests a background durability barrier on leaving the editor; never waits on the UI thread. */
    public static void flushPending(Context context) {
        if(context==null)return;
        try {
            SharedPreferences raw=storageContext(context).getSharedPreferences(StatusBarSettings.PREFS,Context.MODE_PRIVATE);
            synchronized(LOCK) {
                PendingSave pending=SAVES.get(raw);
                if(pending!=null)scheduleSave(context,raw,pending.failed,0L);
            }
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings background flush unavailable; pending writes retained",unavailable);
        }
    }

    private static void save(PendingSave pending) {
        synchronized(LOCK) { if(SAVES.get(pending.raw)!=pending)return; }
        try {
            // Android already updated memory with apply(). This empty commit waits for its queued
            // disk writes on this worker; it never replays an earlier editor over newer user edits.
            if(!pending.raw.edit().commit()) {
                ModuleDiagnostics.info("settings","Settings disk write failed; prior recovery snapshot retained");
                notifyFailure(pending);
                return;
            }
            Map<String,Object> current=copyValues(pending.raw.getAll());
            if(current.isEmpty()||!bounded(current)||fromPreferences(current)==null)return;
            Map<String,Object> saved=backupValues(pending.context);
            if(saved!=null&&saved.equals(current))return;
            SharedPreferences backup=pending.context.getSharedPreferences(BACKUP_PREFS,Context.MODE_PRIVATE);
            SharedPreferences.Editor editor=backup.edit().clear().putInt(BACKUP_SCHEMA,SCHEMA);
            for(Map.Entry<String,?> entry:current.entrySet())StatusBarSettings.copyPreference(editor,entry.getKey(),entry.getValue());
            if(editor.commit())synchronized(LOCK) { DURABLE.put(backup,copyValues(current)); }
            else ModuleDiagnostics.info("settings","Recovery snapshot write failed; primary settings remain durable");
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings persistence check failed; prior recovery snapshot retained",unavailable);
            notifyFailure(pending);
        } finally {
            synchronized(LOCK) { if(SAVES.get(pending.raw)==pending)SAVES.remove(pending.raw); }
        }
    }

    private static Context storageContext(Context context) {
        Context app=context.getApplicationContext();return (app==null?context:app).createDeviceProtectedStorageContext();
    }

    private static Map<String,Object> copyValues(Map<String,?> supplied) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(Map.Entry<String,?> entry:supplied.entrySet()) {
            Object value=entry.getValue();
            result.put(entry.getKey(),value instanceof Set?new HashSet<>((Set<?>)value):value);
        }
        return result;
    }

    private static boolean bounded(Map<String,?> values) {
        if(values.size()>MAX_BACKUP_ENTRIES)return false;
        long bytes=256L;
        for(Map.Entry<String,?> entry:values.entrySet()) {
            // XML escaping and UTF-8 are bounded by six bytes per UTF-16 code unit here.
            bytes+=6L*entry.getKey().length()+128L;
            Object value=entry.getValue();
            if(value instanceof String)bytes+=6L*((String)value).length();
            else if(value instanceof Set) {
                for(Object item:(Set<?>)value) {
                    if(!(item instanceof String))return false;
                    bytes+=6L*((String)item).length()+32L;
                    if(bytes>MAX_BACKUP_BYTES)return false;
                }
            } else if(!(value instanceof Number)&&!(value instanceof Boolean))return false;
            if(bytes>MAX_BACKUP_BYTES)return false;
        }
        return true;
    }

    private static void notifyFailure(PendingSave pending) {
        if(pending.failed==null)return;
        try { pending.failed.run(); }
        catch(RuntimeException unavailable) { ModuleDiagnostics.error("settings","Settings save feedback unavailable",unavailable); }
    }

    private static final class PendingSave {
        final Context context;final SharedPreferences raw;final Runnable failed;
        ScheduledFuture<?> future;
        PendingSave(Context context,SharedPreferences raw,Runnable failed) { this.context=context;this.raw=raw;this.failed=failed; }
    }
}
