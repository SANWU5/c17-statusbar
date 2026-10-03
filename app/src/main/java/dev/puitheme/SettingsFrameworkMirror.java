// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import io.github.libxposed.service.XposedService;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A framework-owned boot copy. The app's DE preferences remain authoritative;
 * only a successfully confirmed primary write is offered to this channel. */
final class SettingsFrameworkMirror {
    static final String GROUP="c17_saved_settings_v1";
    static final String SCHEMA="_c17_saved_snapshot_schema", REVISION="_c17_saved_revision";
    static final int VERSION=4;
    private static final String MAINTENANCE_PREFS="c17_maintenance", RESET_PENDING="framework_defaults_pending";
    private static final Object LOCK=new Object();
    private static final Map<XposedService,Target> TARGETS=new IdentityHashMap<>();
    // The service SDK caches RemotePreferences by group on the service object.
    // A reconnect of that same object must not acknowledge its failed local write.
    private static final Map<XposedService,Acknowledged> ACKNOWLEDGED=new java.util.WeakHashMap<>();
    private static final ScheduledThreadPoolExecutor WORK=new ScheduledThreadPoolExecutor(1,task->{
        Thread worker=new Thread(task,"C17-settings-framework");worker.setDaemon(true);return worker;
    });
    private static Context owner;
    private static Bundle desired;
    private static long generation;
    private static boolean queued;
    static { WORK.setRemoveOnCancelPolicy(true); }
    private SettingsFrameworkMirror(){}

    static void attach(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))return;
        Context app=context.getApplicationContext();
        synchronized(LOCK) {
            if(owner!=null)return;
            owner=app==null?context:app;
        }
        // A reset made while LSPosed was disconnected must never reseed its old remote values.
        try {
            Context storage=context.createDeviceProtectedStorageContext();
            if(storage.getSharedPreferences(MAINTENANCE_PREFS,Context.MODE_PRIVATE).getBoolean(RESET_PENDING,false)) {
                Bundle latest=SettingsSnapshot.fromPreferences(PreferenceWrites.values(
                        storage.getSharedPreferences(StatusBarSettings.PREFS,Context.MODE_PRIVATE)));
                if(latest!=null)synchronized(LOCK){desired=latest;generation++;queueLocked();}
            }
        }catch(RuntimeException unavailable){/* Normal provider confirmation remains authoritative. */}
    }

    static final class ResetResult {
        final boolean queued,synced;
        ResetResult(boolean queued,boolean synced){this.queued=queued;this.synced=synced;}
    }

    /** Serial with all Binder writes, including a publication that already passed its generation check. */
    static ResetResult resetForMaintenance(Context context,Bundle defaults) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName())
                ||!SettingsSnapshot.complete(defaults))return new ResetResult(false,false);
        Context storage=context.createDeviceProtectedStorageContext();
        if(!storage.getSharedPreferences(MAINTENANCE_PREFS,Context.MODE_PRIVATE)
                .edit().putBoolean(RESET_PENDING,true).commit())return new ResetResult(false,false);
        attach(context);
        synchronized(LOCK) {
            desired=SettingsSnapshot.runtimeCopy(defaults);generation++;ACKNOWLEDGED.clear();
            for(Target target:TARGETS.values()){target.confirmed=null;target.attempts=0;}
        }
        java.util.concurrent.Future<Boolean> completed=WORK.submit(()->{publish();return allSynced();});
        boolean interrupted=false;
        try {
            for(;;)try {return new ResetResult(true,completed.get());}
            catch(InterruptedException interruption){interrupted=true;}
            catch(java.util.concurrent.ExecutionException failure){return new ResetResult(true,false);}
        } finally {if(interrupted)Thread.currentThread().interrupt();}
    }

    /** The official helper already owns its one process-wide service listener. */
    static void bound(XposedService service) {
        Context context;
        synchronized(LOCK) {
            if(TARGETS.containsKey(service))return;
            TARGETS.put(service,new Target(service,ACKNOWLEDGED.get(service)));context=owner;
            queueLocked();
        }
        if(context!=null)SettingsSnapshot.frameworkConnected(context);
    }

    static void unbound(XposedService service) {
        synchronized(LOCK) { TARGETS.remove(service); }
    }

    /** Called only after the primary XML's successful durability barrier. */
    static void confirmed(Context context,Bundle snapshot) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName())
                ||!SettingsSnapshot.complete(snapshot)||NumericTrial.active())return;
        snapshot=SettingsSnapshot.runtimeCopy(snapshot);
        attach(context);
        synchronized(LOCK) {
            if(!same(desired,snapshot)) { desired=new Bundle(snapshot);generation++; }
            for(Target target:TARGETS.values())target.attempts=0;
            queueLocked();
        }
    }

    /** A real editor flush/service event may retry a failed mirror; no permanent polling. */
    static void retryConfirmed(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))return;
        synchronized(LOCK) {
            if(desired==null)return;
            for(Target target:TARGETS.values())target.attempts=0;
            queueLocked();
        }
    }

    /** Private structural evidence for the app/own-UID test, never configuration contents. */
    static Bundle status() {
        Bundle status=new Bundle();int synced=0,available=0;long revision=0L;
        synchronized(LOCK) {
            for(Target target:TARGETS.values()) {
                if(target.preferences==null)continue;
                available++;
                if(desired!=null&&same(desired,target.confirmed)){synced++;revision=Math.max(revision,target.revision);}
            }
        }
        status.putBoolean("ready",synced>0);status.putInt("available_targets",available);
        status.putInt("synced_targets",synced);status.putLong("revision",revision);
        return status;
    }

    private static void queueLocked() {
        if(queued||TARGETS.isEmpty())return;
        queued=true;WORK.execute(SettingsFrameworkMirror::publish);
    }

    private static void publish() {
        ArrayList<Target> targets;Bundle candidate;long token;
        synchronized(LOCK) {
            queued=false;targets=new ArrayList<>(TARGETS.values());
            candidate=desired==null?null:new Bundle(desired);token=generation;
        }
        for(Target target:targets) {
            synchronized(LOCK) { if(TARGETS.get(target.service)!=target||token!=generation)continue; }
            try {
                if(target.preferences==null) {
                    if((target.service.getFrameworkProperties()&XposedService.PROP_CAP_REMOTE)==0)continue;
                    target.preferences=target.service.getRemotePreferences(GROUP);
                    if(!target.initialized) {
                        Map<String,?> initial=target.preferences.getAll();
                        target.confirmed=decode(initial);
                        Object revision=initial.get(REVISION);
                        target.revision=revision instanceof Long?(Long)revision:0L;
                        target.initialized=true;
                        synchronized(LOCK) { ACKNOWLEDGED.put(target.service,new Acknowledged(target)); }
                    }
                }
                if(candidate==null||same(target.confirmed,candidate))continue;
                synchronized(LOCK) { if(TARGETS.get(target.service)!=target||token!=generation)continue; }
                long revision=Math.max(System.currentTimeMillis(),target.revision+1L);
                // libxposed's writable preferences update their local map before commit().
                // Never use that local map to acknowledge a failed Binder commit.
                if(write(target.preferences,candidate,revision)) {
                    target.confirmed=new Bundle(candidate);target.revision=revision;target.attempts=0;
                    synchronized(LOCK) { ACKNOWLEDGED.put(target.service,new Acknowledged(target)); }
                    ModuleDiagnostics.info("settings","Saved configuration synchronized to framework boot storage");
                } else failed(target,token);
            } catch(RuntimeException | LinkageError unavailable) {
                ModuleDiagnostics.error("settings","Framework boot copy unavailable; device configuration retained",unavailable);
                failed(target,token);
            }
        }
        synchronized(LOCK) { if(token!=generation)queueLocked(); }
        if(allSynced())clearResetMarker();
    }

    private static boolean allSynced() {
        synchronized(LOCK) {
            if(desired==null||TARGETS.isEmpty())return false;
            for(Target target:TARGETS.values())if(!same(desired,target.confirmed))return false;
            return true;
        }
    }
    private static void clearResetMarker() {
        Context context;
        synchronized(LOCK){context=owner;}
        if(context!=null)try {
            SharedPreferences maintenance=context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(MAINTENANCE_PREFS,Context.MODE_PRIVATE);
            if(maintenance.getBoolean(RESET_PENDING,false))maintenance.edit().remove(RESET_PENDING).commit();
        }catch(RuntimeException unavailable){/* The durable marker safely retries on the next connection. */}
    }

    private static void failed(Target target,long token) {
        int attempt;
        synchronized(LOCK) {
            if(TARGETS.get(target.service)!=target||token!=generation)return;
            attempt=++target.attempts;
        }
        ModuleDiagnostics.info("settings","Framework boot synchronization failed; last confirmed copy retained");
        if(attempt<=3)WORK.schedule(()->{
            synchronized(LOCK) {
                if(TARGETS.get(target.service)==target&&token==generation)queueLocked();
            }
        },attempt*1000L,TimeUnit.MILLISECONDS);
    }

    static boolean write(SharedPreferences preferences,Bundle snapshot,long revision) {
        if(!SettingsSnapshot.complete(snapshot)||revision<=0)return false;
        snapshot=SettingsSnapshot.runtimeCopy(snapshot);
        SharedPreferences.Editor editor=preferences.edit().clear().putInt(SCHEMA,VERSION).putLong(REVISION,revision);
        for(String key:snapshot.keySet())StatusBarSettings.copyPreference(editor,key,snapshot.get(key));
        return editor.commit();
    }

    /** No defaults are synthesized for an absent, unknown-schema or partial framework copy. */
    static Bundle decode(Map<String,?> values) {
        if(values==null||!(Integer.valueOf(VERSION).equals(values.get(SCHEMA))
                ||Integer.valueOf(3).equals(values.get(SCHEMA))||Integer.valueOf(2).equals(values.get(SCHEMA))
                ||Integer.valueOf(1).equals(values.get(SCHEMA)))
                ||!(values.get(REVISION) instanceof Long)||(Long)values.get(REVISION)<=0L)return null;
        if(!Integer.valueOf(VERSION).equals(values.get(SCHEMA))) {
            // Schemas 1/2/3 predate this entire batch. A mixed old/new
            // batch is a partial write, even when the present value is a default.
            for(String key:Upgrade67.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade67.DEFAULTS);
            values=upgraded;
        }
        if(Integer.valueOf(1).equals(values.get(SCHEMA))||Integer.valueOf(2).equals(values.get(SCHEMA))) {
            // Schema 3 already contains the complete code-66 batch and must retain it.
            for(String key:Upgrade66.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade66.inheritedDefaults(values));
            values=upgraded;
        }
        if(Integer.valueOf(1).equals(values.get(SCHEMA))) {
            // Schema 1 predates the code-65 additions. Upgrade only a complete historical
            // batch; schemas 2/3/4 must include all fields belonging to their release.
            for(String key:Upgrade65.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> positionUpgrade=new java.util.LinkedHashMap<>(values);
            positionUpgrade.putAll(Upgrade65.DEFAULTS);
            values=positionUpgrade;
            boolean before63=true;
            for(String key:Upgrade63.DEFAULTS.keySet())if(values.containsKey(key)){before63=false;break;}
            if(before63){
                Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
                upgraded.putAll(Upgrade63.DEFAULTS);
                values=upgraded;
            }
            boolean before62=true;
            for(String key:NotificationClearAppearance.LANDSCAPE_LEGACY.keySet())if(values.containsKey(key)){before62=false;break;}
            if(before62&&!before63)return null;
            if(before62){
                Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
                upgraded.putAll(NotificationClearAppearance.inheritedLandscape(values));
                values=upgraded;
            }
            boolean before61=true;
            for(String key:Upgrade61.DEFAULTS.keySet())if(values.containsKey(key)){before61=false;break;}
            if(before61&&(!before62||!before63))return null;
            if(before61) {
                Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
                upgraded.putAll(Upgrade61.DEFAULTS);
                values=upgraded;
            }
            boolean before60=true;
            for(String key:Upgrade60.DEFAULTS.keySet())if(values.containsKey(key)){before60=false;break;}
            if(before60&&(!before61||!before62||!before63))return null;
            if(before60) {
                Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
                upgraded.putAll(Upgrade60.DEFAULTS);
                if(values.get(NetworkSpeedControls.INTERVAL_SECONDS) instanceof Float)
                    upgraded.put(NetworkSpeedControls.INTERVAL_MILLIS,migratedSpeedMillis(values.get(NetworkSpeedControls.INTERVAL_SECONDS)));
                values=upgraded;
            }
            // Only complete historical batches are upgraded. A partial current copy or any
            // absent old field is still rejected, even when its new defaults can be supplied.
            Map<String,Object> defaults=Upgrade58.DEFAULTS;
            Map<String,Object> badgeDefaults=Upgrade59.DEFAULTS;
            boolean previous=true,beforeBadge=true;
            for(String key:defaults.keySet())if(values.containsKey(key)){previous=false;break;}
            for(String key:badgeDefaults.keySet())if(values.containsKey(key)){beforeBadge=false;break;}
            if(beforeBadge&&(!before60||!before61||!before62||!before63))return null;
            if(previous&&!beforeBadge)return null;
            if(beforeBadge) {
                Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
                if(previous) {
                    upgraded.putAll(defaults);
                    upgraded.put(NotificationBigClockSettings.LANDSCAPE_MASTER,false);
                    upgraded.put(NotificationGroupStack.MASTER,false);
                }
                upgraded.putAll(badgeDefaults);
                values=upgraded;
            }
        }
        Bundle result=new Bundle();
        for(String key:StatusBarSettings.NUMERIC_DEFAULTS.keySet()) {
            Object value=values.get(key);if(!(value instanceof Float)||!Float.isFinite((Float)value))return null;
            result.putFloat(key,(Float)value);
        }
        for(String key:StatusBarSettings.COLOR_DEFAULTS.keySet()) {
            Object value=values.get(key),alpha=values.get(StatusBarSettings.alphaKey(key));
            if(!(value instanceof Integer)||!(alpha instanceof Boolean))return null;
            result.putInt(key,(Integer)value);result.putBoolean(StatusBarSettings.alphaKey(key),(Boolean)alpha);
        }
        for(String key:StatusBarSettings.STRING_DEFAULTS.keySet()) {
            Object value=values.get(key);if(!(value instanceof String))return null;result.putString(key,(String)value);
        }
        for(String key:StatusBarSettings.BOOLEAN_DEFAULTS.keySet()) {
            Object value=values.get(key);if(!(value instanceof Boolean))return null;result.putBoolean(key,(Boolean)value);
        }
        result.putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,!result.getBoolean(NativeDataActivity.MASTER,false));
        result.putBoolean(NotificationGroupStack.MASTER,false);
        return SettingsSnapshot.complete(result)?result:null;
    }

    /** Convert persisted units, not the runtime timer's rounded/saturated long duration.
     * The original seconds key stays untouched; a finite float is the storage boundary. */
    static float migratedSpeedMillis(Object seconds) {
        if(!(seconds instanceof Number)||!Float.isFinite(((Number)seconds).floatValue()))
            return NetworkSpeedControls.DEFAULT_MILLIS;
        double converted=((Number)seconds).floatValue()*1000d;
        return (float)Math.max(-Float.MAX_VALUE,Math.min(Float.MAX_VALUE,converted));
    }

    private static boolean code58Key(String key) {
        return key.startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX)
                ||key.startsWith("status_hint_icons_")
                ||NotificationGroupStack.MASTER.equals(key)||C17HighlightRemoval.BACKGROUND_ENABLED.equals(key)
                ||C17HighlightRemoval.LIGHT_BACKGROUND.equals(key)||C17HighlightRemoval.DARK_BACKGROUND.equals(key)
                ||(C17HighlightRemoval.LIGHT_BACKGROUND+"_custom_alpha").equals(key)
                ||(C17HighlightRemoval.DARK_BACKGROUND+"_custom_alpha").equals(key);
    }
    private static final class Upgrade58 {
        static final Map<String,Object> DEFAULTS=defaults();
        private static Map<String,Object> defaults() {
            Bundle complete=SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
            Map<String,Object> result=new java.util.LinkedHashMap<>();
            for(String key:complete.keySet())if(code58Key(key)&&!Upgrade60.DEFAULTS.containsKey(key))result.put(key,complete.get(key));
            return java.util.Collections.unmodifiableMap(result);
        }
    }

    private static final class Upgrade59 {
        static final Map<String,Object> DEFAULTS=defaults();
        private static Map<String,Object> defaults() {
            Map<String,Object> values=new java.util.LinkedHashMap<>();
            // Keep the original historical batch fixed; later per-part additions belong to Upgrade66.
            values.put(NativeNetworkBadgeControls.MASTER,false);
            values.put(NativeNetworkBadgeControls.X,0f);values.put(NativeNetworkBadgeControls.Y,0f);
            values.put(NativeNetworkBadgeControls.SCALE,100f);values.put(NativeNetworkBadgeControls.WEIGHT,400f);
            return java.util.Collections.unmodifiableMap(values);
        }
    }
    private static final class Upgrade60 {
        static final Map<String,Object> DEFAULTS=defaults();
        private static Map<String,Object> defaults() {
            Map<String,Object> values=new java.util.LinkedHashMap<>();
            values.put(NativeNetworkBadgeControls.FONT,"native");
            values.put(NetworkSpeedControls.INTERVAL_MILLIS,NetworkSpeedControls.DEFAULT_MILLIS);
            values.put(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,false);
            values.put(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH,100f);
            return java.util.Collections.unmodifiableMap(values);
        }
    }

    private static final class Upgrade61 {
        static final Map<String,Object> DEFAULTS=defaults();
        private static Map<String,Object> defaults() {
            Map<String,Object> values=new java.util.LinkedHashMap<>();
            values.put(C17HighlightRemoval.NOTIFICATION_ENABLED,true);
            values.put(C17HighlightRemoval.CONTROL_ENABLED,true);
            values.put(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED,false);
            values.put(NotificationClearMotion.LANDSCAPE_MASTER,false);
            values.put(NotificationClearMotion.LANDSCAPE_OFFSET_X,0f);
            values.put(NotificationClearMotion.LANDSCAPE_OFFSET_Y,0f);
            values.put(LockscreenControls.DATE_ENABLED,false);
            values.put(LockscreenControls.DATE_FORMAT,"M月d日 {周}");
            values.put(LockscreenControls.HIDE_LOCK,false);
            return java.util.Collections.unmodifiableMap(values);
        }
    }
    private static final class Upgrade63 {
        static final Map<String,Object> DEFAULTS=defaults();
        private static Map<String,Object> defaults(){
            Map<String,Object> values=new java.util.LinkedHashMap<>();
            values.put(FeatureOptions.STACK_LANDSCAPE_ENABLED,false);
            values.put(NativeDataActivity.MASTER,false);values.put(NativeDataActivity.COLOR_ENABLED,false);
            values.put(NativeDataActivity.X,0f);values.put(NativeDataActivity.Y,0f);values.put(NativeDataActivity.SCALE,100f);
            values.put(NativeDataActivity.COLOR_LIGHT,0xff000000);values.put(NativeDataActivity.COLOR_DARK,0xffffffff);
            values.put(StatusBarSettings.alphaKey(NativeDataActivity.COLOR_LIGHT),false);
            values.put(StatusBarSettings.alphaKey(NativeDataActivity.COLOR_DARK),false);
            return java.util.Collections.unmodifiableMap(values);
        }
    }
    private static boolean same(Bundle first,Bundle second) {
        if(first==null||second==null||!first.keySet().equals(second.keySet()))return false;
        for(String key:first.keySet())if(!java.util.Objects.equals(first.get(key),second.get(key)))return false;
        return true;
    }
    private static final class Target {
        final XposedService service;SharedPreferences preferences;Bundle confirmed;long revision;int attempts;
        boolean initialized;
        Target(XposedService service,Acknowledged previous){
            this.service=service;
            if(previous!=null){confirmed=previous.snapshot==null?null:new Bundle(previous.snapshot);revision=previous.revision;initialized=true;}
        }
    }
    private static final class Acknowledged {
        final Bundle snapshot;final long revision;
        Acknowledged(Target target){snapshot=target.confirmed==null?null:new Bundle(target.confirmed);revision=target.revision;}
    }
}
