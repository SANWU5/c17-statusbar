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
import java.util.concurrent.ExecutionException;

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
    private static final Map<SharedPreferences,Map<String,Object>> BACKED=new WeakHashMap<>();
    private static final Map<SharedPreferences,Map<String,Object>> DIAGNOSTIC_SAVED=new WeakHashMap<>();
    private static final Map<SharedPreferences,Long> BACKED_REVISIONS=new WeakHashMap<>();
    private static final Map<SharedPreferences,PendingSave> SAVES=new IdentityHashMap<>();
    private static boolean maintenanceReset;
    private static final ScheduledThreadPoolExecutor WRITER=new ScheduledThreadPoolExecutor(1,task->{
        Thread thread=new Thread(task,"C17-settings-persistence");thread.setDaemon(true);return thread;
    });
    static { WRITER.setRemoveOnCancelPolicy(true); }

    private SettingsSnapshot(){}

    /** A failed commit may notify native listeners despite not reaching disk. */
    public static boolean notificationAllowed(SharedPreferences raw) {
        return PreferenceWrites.notificationAllowed(raw);
    }

    static Map<String,Object> readableValues(SharedPreferences raw) { return PreferenceWrites.values(raw); }

    /**
     * Invalid/unavailable IPC retains the last successfully applied snapshot in this process.
     * A fresh SystemUI process has no applied cache: null keeps its existing native/default state
     * until a complete provider read succeeds. The provider's private backup survives app restart.
     */
    public static Bundle read(Context context) {
        if(context==null)return null;
        if(ModuleLifecycle.removed())return ModuleLifecycle.nativeSettings();
        ContentResolver resolver=context.getContentResolver();
        try {
            Bundle candidate=resolver.call(Uri.parse(StatusBarSettings.CONTENT_URI),READ_METHOD,null,null);
            if(complete(candidate))return runtimeCopy(candidate);
            ModuleDiagnostics.info("settings","Incomplete settings snapshot; retaining last applied configuration");
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings snapshot unavailable; retaining last applied configuration",unavailable);
        }
        if(ModuleLifecycle.verifyAfterFailure(context))return ModuleLifecycle.nativeSettings();
        synchronized(LOCK) { Bundle previous=APPLIED.get(resolver);return previous==null?null:runtimeCopy(previous); }
    }

    /** Startup readiness must not confuse an applied-cache fallback with a fresh
     * successful IPC. PackageManager boot visibility is not proof of uninstall;
     * the exact lifecycle receiver still stops the loader after real removal. */
    static Bundle readFresh(Context context) {
        if(context==null||ModuleLifecycle.removed())return null;
        try {
            Bundle snapshot=context.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI),READ_METHOD,null,null);
            if(complete(snapshot))return runtimeCopy(snapshot);
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Startup settings provider not ready; retaining current configuration",unavailable);
        }
        return null;
    }

    /** Call only after all runtime consumers accepted the complete snapshot. */
    public static void rememberApplied(Context context,Bundle snapshot) {
        if(context==null||ModuleLifecycle.removed()||!complete(snapshot))return;
        synchronized(LOCK) { APPLIED.put(context.getContentResolver(),runtimeCopy(snapshot)); }
    }

    public static Bundle lastApplied(Context context) {
        if(context==null)return null;
        synchronized(LOCK) { Bundle saved=APPLIED.get(context.getContentResolver());return saved==null?null:runtimeCopy(saved); }
    }

    /** Retired same-app grouping is disabled even for a still-running older provider/cache. */
    static Bundle runtimeCopy(Bundle supplied) {
        Bundle result=new Bundle(supplied);
        result.putBoolean(NotificationGroupStack.MASTER,false);
        result.putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,!result.getBoolean(NativeDataActivity.MASTER,false));
        return result;
    }

    static void forgetApplied() { synchronized(LOCK) { APPLIED.clear(); } }

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
            if(!StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(key)
                    &&!StatusBarShadeIconSettings.MASTER.equals(key)
                    &&values.containsKey(key)&&!(values.get(key) instanceof Boolean))return false;
        return true;
    }

    /** Called under StatusBarSettings' startup lock; never overwrites a nonempty current store. */
    static void recoverIfEmpty(Context context,SharedPreferences raw) {
        synchronized(raw) { try {
            if(!raw.getAll().isEmpty())return;
            Map<String,Object> backup=backupValues(context);
            if(backup==null||backup.isEmpty()||fromPreferences(backup)==null)return;
            // Restore the original raw keys, including legacy and local font metadata, not defaults.
            SharedPreferences.Editor editor=raw.edit();
            for(Map.Entry<String,Object> entry:backup.entrySet())StatusBarSettings.copyPreference(editor,entry.getKey(),entry.getValue());
            if(!PreferenceWrites.commit(raw,editor,backup,false)) {
                ModuleDiagnostics.info("settings","Settings recovery could not reach disk; prior durable snapshot retained");
                return;
            }
            scheduleSave(context,raw,null,0L);
            ModuleDiagnostics.info("settings","Empty settings store recovered from last durable snapshot");
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings recovery unavailable; existing preferences retained",unavailable);
        } }
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
            // Missing/invalid data is not a durable snapshot. A later successful load may recover it.
            if(result!=null&&!DURABLE.containsKey(backup))DURABLE.put(backup,result);
            Map<String,Object> saved=DURABLE.get(backup);return saved==null?null:copyValues(saved);
        }
    }

    static void scheduleSave(Context context,SharedPreferences raw,Runnable failed) {
        scheduleSave(context,raw,failed,SAVE_DELAY_MS);
    }

    /** One real framework connection must seed/retry the boot copy even when the
     * app-private backup already matches. Confirmation remains off the UI thread. */
    static void frameworkConnected(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))return;
        WRITER.execute(()->{
            try {
                SharedPreferences raw=StatusBarSettings.preferences(context);
                synchronized(LOCK) { BACKED_REVISIONS.remove(raw); }
                scheduleSave(context,raw,null,0L);
            } catch(RuntimeException unavailable) {
                ModuleDiagnostics.error("settings","Could not confirm device configuration for framework boot storage",unavailable);
            }
        });
    }

    private static void scheduleSave(Context context,SharedPreferences raw,Runnable failed,long delay) {
        if(context==null||raw==null||!"dev.puitheme.iosstatusbar".equals(context.getPackageName()))return;
        if(!notificationAllowed(raw))return;
        Map<String,Object> requested=readableValues(raw);
        if(requested.isEmpty()||!bounded(requested)||fromPreferences(requested)==null)return;
        long revision=PreferenceWrites.revision(raw);
        Context storage=storageContext(context);
        synchronized(LOCK) {
            if(maintenanceReset)return;
            PendingSave previous=SAVES.get(raw);
            if(Long.valueOf(revision).equals(BACKED_REVISIONS.get(raw))&&requested.equals(BACKED.get(raw)))return;
            if(previous!=null&&previous.revision==revision&&requested.equals(previous.requested))return;
            if(previous!=null&&previous.future!=null)previous.future.cancel(false);
            Runnable failure=failed!=null?failed:previous==null?null:previous.failed;
            PendingSave next=new PendingSave(storage,raw,failure,requested,revision);
            SAVES.put(raw,next);
            next.future=WRITER.schedule(()->save(next),delay,TimeUnit.MILLISECONDS);
        }
    }

    public static final class ResetResult {
        public final boolean primaryReset,recoveryCleared,mirrorQueued,frameworkSynced;
        public final String message;
        ResetResult(boolean primary,boolean recovery,boolean queued,boolean synced,String message) {
            primaryReset=primary;recoveryCleared=recovery;mirrorQueued=queued;frameworkSynced=synced;this.message=message;
        }
        public boolean localSuccess(){return primaryReset&&recoveryCleared&&mirrorQueued;}
    }

    /** Background-only maintenance barrier. Existing saves finish before deletion; no old save can follow it. */
    public static ResetResult resetForMaintenance(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))
            throw new IllegalArgumentException("Own application context required");
        synchronized(LOCK) {
            if(maintenanceReset)return new ResetResult(false,false,false,false,"配置清理正在进行");
            maintenanceReset=true;
            for(PendingSave pending:SAVES.values())if(pending.future!=null)pending.future.cancel(false);
            SAVES.clear();
        }
        java.util.concurrent.Future<ResetResult> completed=WRITER.submit(()->resetOnWriter(context));
        boolean interrupted=false;
        try {
            // Do not release the UI's maintenance guard while the transaction can still write.
            for(;;)try {return completed.get();}
            catch(InterruptedException interruption){interrupted=true;}
            catch(ExecutionException unavailable){return new ResetResult(false,false,false,false,"配置清理失败，请重试");}
        } finally {if(interrupted)Thread.currentThread().interrupt();}
    }

    private static ResetResult resetOnWriter(Context context) {
        boolean primary=false,recovery=false,queued=false,synced=false;
        String message="配置清理失败，请重试";
        Context storage=storageContext(context);
        SharedPreferences raw=storage.getSharedPreferences(StatusBarSettings.PREFS,Context.MODE_PRIVATE);
        try {
            if(!NumericTrial.cancelForReset(storage,raw))
                return new ResetResult(false,false,false,false,"无法取消数值试用，已保留原配置");
            Map<String,Object> defaults=new LinkedHashMap<>();
            // A nonempty mandatory default prevents empty-store recovery or CE migration of old values.
            defaults.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true);
            synchronized(raw) {
                primary=PreferenceWrites.commit(raw,raw.edit().clear()
                        .putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true),defaults,true);
            }
            if(!primary)return new ResetResult(false,false,false,false,"配置未能写入磁盘，已保留原配置");
            SharedPreferences backup=storage.getSharedPreferences(BACKUP_PREFS,Context.MODE_PRIVATE);
            Map<String,Object> previous=backupValues(storage);
            synchronized(LOCK) { DURABLE.put(backup,previous); }
            boolean backupCleared=backup.edit().clear().commit();
            Context credential=context.createPackageContext(context.getPackageName(),0);
            boolean legacyCleared=!credential.isDeviceProtectedStorage()
                    &&credential.getSharedPreferences(StatusBarSettings.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
            recovery=backupCleared&&legacyCleared;
            synchronized(LOCK) {
                if(backupCleared)DURABLE.put(backup,null);
                BACKED.remove(raw);BACKED_REVISIONS.remove(raw);APPLIED.clear();
            }
            SettingsFrameworkMirror.ResetResult mirrored=SettingsFrameworkMirror.resetForMaintenance(storage,fromPreferences(defaults));
            queued=mirrored.queued;synced=mirrored.synced;
            message=!recovery?"当前配置已恢复默认，但旧恢复副本未能全部删除，请重试"
                    :!queued?"本地配置已恢复默认，但框架清理未能排队，请重试"
                    :synced?"所有配置已清理并恢复默认":"本地配置已恢复默认，框架连接后自动同步默认配置";
            return new ResetResult(primary,recovery,queued,synced,message);
        } catch(RuntimeException | android.content.pm.PackageManager.NameNotFoundException unavailable) {
            return new ResetResult(primary,recovery,queued,synced,"配置清理未全部完成，请重试");
        } finally {
            synchronized(LOCK) { maintenanceReset=false; }
            if(primary)try { context.getContentResolver().notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI),null); }
            catch(RuntimeException unavailable) { /* Primary defaults remain durable. */ }
        }
    }

    /** Requests a background durability barrier on leaving the editor; never waits on the UI thread. */
    public static void flushPending(Context context) {
        if(context==null)return;
        try {
            SharedPreferences raw=storageContext(context).getSharedPreferences(StatusBarSettings.PREFS,Context.MODE_PRIVATE);
            PendingSave pending;
            synchronized(LOCK) { pending=SAVES.get(raw); }
            if(pending!=null) {
                // Flush advances the existing deadline without taking LOCK before the primary store.
                synchronized(LOCK) {
                    if(SAVES.get(raw)==pending&&pending.future!=null) {
                        pending.future.cancel(false);
                        pending.future=WRITER.schedule(()->save(pending),0L,TimeUnit.MILLISECONDS);
                    }
                }
            }
            SettingsFrameworkMirror.retryConfirmed(context);
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings background flush unavailable; pending writes retained",unavailable);
        }
    }

    private static void save(PendingSave pending) {
        synchronized(LOCK) { if(SAVES.get(pending.raw)!=pending)return; }
        try {
            Map<String,Object> current;
            synchronized(pending.raw) {
                if(!notificationAllowed(pending.raw)||PreferenceWrites.revision(pending.raw)!=pending.revision)return;
                // Capture before the barrier. A newer apply after it cannot become a durable backup.
                current=readableValues(pending.raw);
                if(current.isEmpty()||!bounded(current)||fromPreferences(current)==null)return;
                // Android already updated memory with apply(). This empty commit waits for its queued
                // disk writes on this worker; it never replays an earlier editor over newer user edits.
                if(!pending.raw.edit().commit()) {
                    ModuleDiagnostics.info("settings","Settings disk write failed; prior recovery snapshot retained");
                    notifyFailure(pending);
                    return;
                }
                if(PreferenceWrites.revision(pending.raw)!=pending.revision
                        ||!current.equals(readableValues(pending.raw)))return;
                synchronized(LOCK) { if(SAVES.get(pending.raw)!=pending)return; }
            }
            // The DE barrier succeeded and the requested generation still owns it.
            // Framework synchronization does not depend on the secondary backup succeeding.
            SettingsFrameworkMirror.confirmed(pending.context,fromPreferences(current));
            Map<String,Object> saved=backupValues(pending.context);
            Map<String,Object> previous;
            synchronized(LOCK) {
                previous=DIAGNOSTIC_SAVED.containsKey(pending.raw)?DIAGNOSTIC_SAVED.get(pending.raw):saved;
                DIAGNOSTIC_SAVED.put(pending.raw,copyValues(current));
            }
            // Only the durable generation is recorded. Pending trials and failed writes never reach this point.
            if(previous!=null) {
                java.util.Set<String> changed=new java.util.LinkedHashSet<>(previous.keySet());
                changed.addAll(current.keySet());
                for(String key:changed)if(!java.util.Objects.equals(previous.get(key),current.get(key)))
                    ModuleDiagnostics.settingChange(pending.context,key,previous.get(key),current.get(key));
            }
            if(saved!=null&&saved.equals(current)) {
                synchronized(LOCK) {
                    BACKED.put(pending.raw,copyValues(current));BACKED_REVISIONS.put(pending.raw,pending.revision);
                }
                return;
            }
            SharedPreferences backup=pending.context.getSharedPreferences(BACKUP_PREFS,Context.MODE_PRIVATE);
            // A failed backup commit also changes its memory. Protect the independent old disk image.
            synchronized(LOCK) { DURABLE.put(backup,saved==null?null:copyValues(saved)); }
            SharedPreferences.Editor editor=backup.edit().clear().putInt(BACKUP_SCHEMA,SCHEMA);
            for(Map.Entry<String,?> entry:current.entrySet())StatusBarSettings.copyPreference(editor,entry.getKey(),entry.getValue());
            if(editor.commit())synchronized(LOCK) {
                DURABLE.put(backup,copyValues(current)); BACKED.put(pending.raw,copyValues(current));
                BACKED_REVISIONS.put(pending.raw,pending.revision);
            }
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
        final Map<String,Object> requested;final long revision;
        ScheduledFuture<?> future;
        PendingSave(Context context,SharedPreferences raw,Runnable failed,Map<String,Object> requested,long revision) {
            this.context=context;this.raw=raw;this.failed=failed;this.requested=requested;this.revision=revision;
        }
    }
}
