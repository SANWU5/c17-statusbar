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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A framework-owned boot copy. The app's DE preferences remain authoritative;
 * only a successfully confirmed primary write is offered to this channel. */
final class SettingsFrameworkMirror {
    static final String GROUP="c17_saved_settings_v1";
    static final String SCHEMA="_c17_saved_snapshot_schema", REVISION="_c17_saved_revision";
    static final int VERSION=52;
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
    private static Bundle desired,defaultCandidate;
    private static SharedPreferences defaultRaw;
    private static long generation,pluginGeneration;
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

    /** Called after a library transaction reaches disk, even when host settings stayed the same. */
    static void pluginsChanged(Context context) {
        if(context==null||ModuleVariant.DABING||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))return;
        synchronized(LOCK) {
            pluginGeneration++;
            generation++;
            for(Target target:TARGETS.values())target.attempts=0;
            queueLocked();
        }
    }

    /** A first plugin install can use the same framework copy before any host setting has been edited. */
    static void offerDefaults(Context context,SharedPreferences raw) {
        if(context==null||ModuleVariant.DABING||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))return;
        attach(context);
        synchronized(LOCK) {
            if(desired!=null||defaultRaw==raw||NumericTrial.active())return;
            defaultCandidate=SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
            defaultRaw=raw;
            generation++;
            queueLocked();
        }
    }

    private static Bundle desiredFor(Target target,Bundle saved,Bundle defaults) {
        if(saved!=null)return saved;
        // Preserve any existing remote settings, even an unreadable/newer schema. A group
        // already holding these exact defaults can update its plugin pointer after reconnect.
        return target.emptyRemote||same(target.confirmed,defaults)?defaults:null;
    }

    /** A real editor flush/service event may retry a failed mirror; no permanent polling. */
    static void retryConfirmed(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))return;
        synchronized(LOCK) {
            if(desired==null&&defaultCandidate==null)return;
            for(Target target:TARGETS.values())target.attempts=0;
            queueLocked();
        }
    }

    /** Private structural evidence for the app/own-UID test, never configuration contents. */
    static Bundle status() {
        Bundle status=new Bundle();int synced=0,available=0,bootScopes=0;long revision=0L;
        synchronized(LOCK) {
            for(Target target:TARGETS.values()) {
                if(target.preferences==null)continue;
                available++;
                if(same(desiredFor(target,desired,defaultCandidate),target.confirmed)){
                    synced++;revision=Math.max(revision,target.revision);
                    if(target.boot!=null&&target.pluginGeneration==pluginGeneration)
                        bootScopes=Math.max(bootScopes,target.boot.scopes);
                }
            }
        }
        status.putBoolean("ready",synced>0);status.putInt("available_targets",available);
        status.putInt("synced_targets",synced);status.putLong("revision",revision);
        status.putBoolean("plugin_boot_ready",bootScopes>0);status.putInt("plugin_boot_scopes",bootScopes);
        return status;
    }

    private static void queueLocked() {
        if(queued||TARGETS.isEmpty())return;
        queued=true;WORK.execute(SettingsFrameworkMirror::publish);
    }

    private static void publish() {
        ArrayList<Target> targets;Bundle saved,defaults;Context context;SharedPreferences raw;long token,pluginToken;
        synchronized(LOCK) {
            queued=false;targets=new ArrayList<>(TARGETS.values());
            saved=desired;defaults=defaultCandidate;context=owner;raw=defaultRaw;token=generation;pluginToken=pluginGeneration;
        }
        if(saved==null&&defaults!=null)try {
            if(!SettingsSnapshot.freshForFramework(context,raw))defaults=null;
        } catch(RuntimeException | android.content.pm.PackageManager.NameNotFoundException unavailable) {
            defaults=null;
        }
        for(Target target:targets) {
            synchronized(LOCK) { if(TARGETS.get(target.service)!=target||token!=generation)continue; }
            try {
                if(target.preferences==null) {
                    if((target.service.getFrameworkProperties()&XposedService.PROP_CAP_REMOTE)==0)continue;
                    target.preferences=target.service.getRemotePreferences(GROUP);
                    if(!target.initialized) {
                        Map<String,?> initial=target.preferences.getAll();
                        target.emptyRemote=initial.isEmpty();
                        target.confirmed=decode(initial);
                        Object revision=initial.get(REVISION);
                        target.revision=revision instanceof Long?(Long)revision:0L;
                        Object metadata=initial.get(CustomPluginFrameworkPublisher.KEY);
                        target.metadata=metadata instanceof String?(String)metadata:null;
                        target.initialized=true;
                        synchronized(LOCK) { ACKNOWLEDGED.put(target.service,new Acknowledged(target)); }
                    }
                }
                Bundle candidate=desiredFor(target,saved,defaults);
                if(candidate==null)continue;
                boolean refreshBoot=!ModuleVariant.DABING&&context!=null&&(target.boot==null
                        ||target.pluginGeneration!=pluginToken||target.confirmed==null
                        ||target.confirmed.getBoolean(StatusBarSettings.SAFE_MODE)!=candidate.getBoolean(StatusBarSettings.SAFE_MODE));
                CustomPluginFrameworkPublisher.Staged boot=target.boot;
                if(refreshBoot)try {
                    CustomPluginFrameworkPublisher.Snapshot plugins=new CustomPluginRepository(context)
                            .frameworkPlans(candidate.getBoolean(StatusBarSettings.SAFE_MODE));
                    if(saved==null&&target.emptyRemote&&plugins.revision==0L)continue;
                    boot=CustomPluginFrameworkPublisher.stage(target.service,plugins,boot==null
                            ?java.util.Collections.emptySet():boot.referenced);
                } catch(Exception | LinkageError unavailable) {
                    // Keep host settings usable. Withdraw the old pointer so a reader can pause
                    // its previous plan until the current revision is ready.
                    boot=null;
                    ModuleDiagnostics.error("settings","Plugin boot files unavailable",unavailable);
                }
                if(saved==null&&target.emptyRemote&&boot==null){failed(target,token);continue;}
                String metadata=boot==null?(refreshBoot?CustomPluginFrameworkPublisher.WITHDRAWN:null):boot.metadata;
                boolean changed=!same(target.confirmed,candidate)||!java.util.Objects.equals(target.metadata,metadata);
                synchronized(LOCK) { if(TARGETS.get(target.service)!=target||token!=generation)continue; }
                if(changed) {
                    long revision=Math.max(System.currentTimeMillis(),target.revision+1L);
                    // The service SDK updates its writable preference map before the Binder commit.
                    // Acknowledge only after that commit succeeds.
                    if(!write(target.preferences,candidate,revision,metadata)){failed(target,token);continue;}
                    synchronized(LOCK) {
                        target.confirmed=new Bundle(candidate);target.revision=revision;target.metadata=metadata;
                        target.emptyRemote=false;
                        ACKNOWLEDGED.put(target.service,new Acknowledged(target));
                    }
                    ModuleDiagnostics.info("settings","Saved configuration synchronized to framework boot storage");
                }
                synchronized(LOCK) {
                    target.boot=boot;
                    target.pluginGeneration=boot==null?-1L:pluginToken;
                    if(!refreshBoot||boot!=null)target.attempts=0;
                }
                if(refreshBoot) {
                    if(boot==null)failed(target,token);
                    else schedulePrune(target,boot);
                }
            } catch(RuntimeException | LinkageError unavailable) {
                ModuleDiagnostics.error("settings","Framework boot copy unavailable; device configuration retained",unavailable);
                failed(target,token);
            }
        }
        synchronized(LOCK) { if(token!=generation)queueLocked(); }
        if(allSynced())clearResetMarker();
    }

    /** Let readers already holding a previous pointer finish opening their files. */
    private static void schedulePrune(Target target,CustomPluginFrameworkPublisher.Staged boot) {
        if(target.prune!=null)target.prune.cancel(false);
        if(boot.obsolete.isEmpty())return;
        target.prune=WORK.schedule(()->{
            synchronized(LOCK) {
                if(TARGETS.get(target.service)!=target||target.boot!=boot||target.pluginGeneration!=pluginGeneration)return;
            }
            for(String name:boot.obsolete)try {target.service.deleteRemoteFile(name);}
            catch(RuntimeException | LinkageError unavailable){return;}
        },60L,TimeUnit.SECONDS);
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
        return write(preferences,snapshot,revision,null);
    }

    private static boolean write(SharedPreferences preferences,Bundle snapshot,long revision,String boot) {
        if(!SettingsSnapshot.complete(snapshot)||revision<=0)return false;
        snapshot=SettingsSnapshot.runtimeCopy(snapshot);
        SharedPreferences.Editor editor=preferences.edit().clear().putInt(SCHEMA,VERSION).putLong(REVISION,revision);
        for(String key:snapshot.keySet())StatusBarSettings.copyPreference(editor,key,snapshot.get(key));
        if(boot!=null)editor.putString(CustomPluginFrameworkPublisher.KEY,boot);
        return editor.commit();
    }

    /** No defaults are synthesized for an absent, unknown-schema or partial framework copy. */
    static Bundle decode(Map<String,?> values) {
        if(values==null||!(values.get(SCHEMA) instanceof Integer)
                ||(Integer)values.get(SCHEMA)<1||(Integer)values.get(SCHEMA)>VERSION
                ||!(values.get(REVISION) instanceof Long)||(Long)values.get(REVISION)<=0L)return null;
        int schema=(Integer)values.get(SCHEMA);
        if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())
            if(values.containsKey(key)&&(schema<50||NetworkDisplaySettings.WIFI_HIDDEN.equals(key)))return null;
        if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())if(values.containsKey(key))return null;
        if(schema<39) {
            for(String key:Upgrade172.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade172.DEFAULTS);values=upgraded;
        }
        if(schema<38) {
            for(String key:Upgrade171.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade171.DEFAULTS);values=upgraded;
        }
        if(schema<37) {
            for(String key:Upgrade169.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade169.DEFAULTS);values=upgraded;
        }
        if(schema<36) {
            for(String key:Upgrade168.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade168.DEFAULTS);values=upgraded;
        }
        if(schema<35) {
            for(String key:Upgrade166.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade166.DEFAULTS);values=upgraded;
        }
        if(schema<34) {
            // A historical copy cannot claim the later page styles or see-through options, even as null.
            for(String key:Upgrade160.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade160.DEFAULTS);values=upgraded;
        }
        if(schema<33) {
            // A historical copy cannot claim the later 2x1 fill opt-in, even as null.
            for(String key:Upgrade157.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade157.DEFAULTS);values=upgraded;
        }
        if(schema<32) {
            // Earlier copies cannot claim a later AOD opt-in, including as null.
            for(String key:Upgrade154.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade154.DEFAULTS);values=upgraded;
        }
        if(schema<31) {
            // A historical copy cannot already contain the later shared-slot option, even as null.
            for(String key:Upgrade146.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade146.DEFAULTS);values=upgraded;
        }
        if(schema<30) {
            // Older wire copies cannot carry any member of the new layout batch.
            for(String key:Upgrade144.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade144.DEFAULTS);values=upgraded;
        }
        if(schema<29) {
            // Persistence did not exist in earlier schemas; never accept a mixed or partial wire copy.
            for(String key:Upgrade136.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade136.DEFAULTS);values=upgraded;
        }
        if(schema<28) {
            // Schema 28 adds only the two big-clock switches, independently for portrait and landscape.
            for(String key:Upgrade135.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade135.DEFAULTS);values=upgraded;
        }
        if(schema<27) {
            // Schema 27 adds only the lock-screen notification options and the pull-down wallpaper's blur, pull and clock-zone settings.
            for(String key:Upgrade118.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade118.DEFAULTS);Upgrade118.carryLockLook(upgraded,values);values=upgraded;
        }
        if(schema<26) {
            // Schema 26 adds only the big clock's "no rebound" switch and the lock-screen glass style (portrait and landscape).
            for(String key:Upgrade117.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade117.DEFAULTS);values=upgraded;
        }
        if(schema<25) {
            // Schema 25 adds only the shared network host's independent left/right spacing.
            for(String key:Upgrade108.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade108.DEFAULTS);values=upgraded;
        }
        if(schema<24) {
            for(String key:Upgrade107.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade107.from(values));values=upgraded;
        }
        if(schema<23) {
            // Schema 23 adds only the same-app mini stack and its count-badge switch, both off.
            for(String key:Upgrade106.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade106.DEFAULTS);values=upgraded;
        }
        if(schema<22) {
            // Schema 22 adds only "使隐藏可点击" for the control centre's hidden top buttons.
            for(String key:Upgrade105.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade105.DEFAULTS);values=upgraded;
        }
        if(schema<21) {
            for(String key:Upgrade103.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade103.DEFAULTS);values=upgraded;
        }
        if(schema<20) {
            // Schema 20 adds only the code101 pair: always show the 4G/5G text, merge Wi-Fi into its slot.
            for(String key:Upgrade101.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade101.DEFAULTS);values=upgraded;
        }
        if(schema<19) {
            // Schema 19 adds only the code100 batch: gradient transition ranges, the capsule cover radius, the control-centre buttons.
            for(String key:Upgrade100.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade100.DEFAULTS);values=upgraded;
        }
        if(schema<18) {
            // Schema 18 adds only the code99 batch: blur mask colour, six running-light colours, glow, spectrum colour, rebound fade.
            for(String key:Upgrade99.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade99.DEFAULTS);values=upgraded;
        }
        if(schema<17) {
            // Schema 17 adds only the code98 batch: lock capsule switch, colour style, capsule gradient/blur/marquee/spectrum, clock rebound blur.
            for(String key:Upgrade98.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade98.DEFAULTS);values=upgraded;
        }
        if(schema<16) {
            // Schema 16 adds only the centred clock colons and the camera-area capsule batch.
            for(String key:Upgrade96.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade96.DEFAULTS);values=upgraded;
        }
        if(schema<15) {
            // Schema 15 adds only the media-accent opacity, follow-while-playing and lock-screen custom batch.
            for(String key:Upgrade93.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade93.DEFAULTS);values=upgraded;
        }
        if(schema<14) {
            // Schema 14 adds only the two-row clock's own position, spacing and colours.
            for(String key:Upgrade91.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade91.DEFAULTS);values=upgraded;
        }
        if(schema<13) {
            // Schema 13 adds only the clock digit-alignment/two-row/margin batch; a mixed batch is a partial write.
            for(String key:Upgrade90.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade90.DEFAULTS);values=upgraded;
        }
        if(schema<12) {
            for(String key:Upgrade85.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.put(FluidCloudStyle.FOLLOW_MEDIA,Upgrade85.legacyMedia(values));values=upgraded;
        }
        if(schema<11) {
            for(String key:Upgrade82.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade82.DEFAULTS);values=upgraded;
        }
        if(schema<10) {
            for(String key:Upgrade80.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade80.inheritedDefaults(values));
            values=upgraded;
        }
        if(schema<9) {
            for(String key:Upgrade79.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade79.inheritedDefaults(values));
            values=upgraded;
        }
        if(schema<8) {
            for(String key:Upgrade78.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade78.DEFAULTS);
            values=upgraded;
        }
        if(schema<7) {
            // Schema 7 adds only lockscreen accent participation; never reinterpret
            // an old partial/current write as a valid historical snapshot.
            for(String key:Upgrade77.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade77.DEFAULTS);
            values=upgraded;
        }
        if(schema<6) {
            // Schemas 1..5 predate this entire batch. Even a present null is a partial write.
            for(String key:Upgrade76.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade76.DEFAULTS);
            values=upgraded;
        }
        if(schema<5) {
            for(String key:Upgrade70.DEFAULTS.keySet())if(values.containsKey(key))return null;
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade70.DEFAULTS);
            values=upgraded;
        }
        if(Integer.valueOf(1).equals(values.get(SCHEMA))||Integer.valueOf(2).equals(values.get(SCHEMA))
                ||Integer.valueOf(3).equals(values.get(SCHEMA))) {
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
            // Only the code62 batch tells: the later landscape transition range (schema 19) was already added above.
            for(String key:NotificationClearAppearance.LANDSCAPE_LEGACY.keySet())
                if(values.containsKey(key)&&!Upgrade100.DEFAULTS.containsKey(key)){before62=false;break;}
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
        if(schema<40) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade173.inheritedDefaults(values));values=upgraded;
        }
        if(schema<41) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade174.DEFAULTS);values=upgraded;
        }
        if(schema<42) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade175.DEFAULTS);values=upgraded;
        }
        if(schema<43) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade177.DEFAULTS);values=upgraded;
        }
        if(schema<44) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade184.DEFAULTS);values=upgraded;
        }
        if(schema<45) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade185.DEFAULTS);values=upgraded;
        }
        if(schema<46) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade186.DEFAULTS);values=upgraded;
        }
        if(schema<47) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade187.DEFAULTS);values=upgraded;
        }
        if(schema<48) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade188.DEFAULTS);values=upgraded;
        }
        if(schema<49) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade195.DEFAULTS);values=upgraded;
        }
        if(schema<50) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            upgraded.putAll(Upgrade196.inheritedDefaults(values));values=upgraded;
        }
        if(schema<51) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            for(Map.Entry<String,Object> entry:Upgrade209.DEFAULTS.entrySet())
                if(!upgraded.containsKey(entry.getKey()))upgraded.put(entry.getKey(),entry.getValue());
            values=upgraded;
        }
        if(schema<52) {
            Map<String,Object> upgraded=new java.util.LinkedHashMap<>(values);
            for(Map.Entry<String,Object> entry:Upgrade279.DEFAULTS.entrySet())
                if(!upgraded.containsKey(entry.getKey()))upgraded.put(entry.getKey(),entry.getValue());
            values=upgraded;
        }
        Bundle result=new Bundle();
        for(String key:StatusBarSettings.NUMERIC_DEFAULTS.keySet()) {
            Object value=values.get(key);
            // The customer editor stores counts as integers so values above 2^24 stay exact.
            // Existing C17 copies and all other numeric fields retain their float wire format.
            if(ModuleVariant.DABING&&NotificationBigClockSettings.VISIBLE_COUNT.equals(key)&&value instanceof Integer) {
                if((Integer)value<1)return null;
                result.putInt(key,(Integer)value);continue;
            }
            if(!(value instanceof Float)||!Float.isFinite((Float)value))return null;
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
            // This optional editing marker was added within schema 12. Its absence
            // is a complete old copy; all previously required fields stay strict.
            if(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED.equals(key)&&!values.containsKey(key)) {
                result.putBoolean(key,false);continue;
            }
            Object value=values.get(key);if(!(value instanceof Boolean))return null;result.putBoolean(key,(Boolean)value);
        }
        result.putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN,!result.getBoolean(NativeDataActivity.MASTER,false));
        result.putBoolean(NotificationGroupStack.MASTER,false);
        result.putBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,false);
        result.putBoolean(FluidCloudStyle.SCOPE_SHADE,false);
        result.putString(StatusBarSettings.BATTERY_STYLE,
                StatusBarSettings.currentBatteryStyle(result.getString(StatusBarSettings.BATTERY_STYLE,"pui")));
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
        // Later landscape options belong to their own historical batches, never the first code58 release.
        if(Upgrade117.DEFAULTS.containsKey(key)||Upgrade135.DEFAULTS.containsKey(key)||Upgrade160.DEFAULTS.containsKey(key)
                ||Upgrade166.DEFAULTS.containsKey(key)||Upgrade168.DEFAULTS.containsKey(key)||Upgrade169.DEFAULTS.containsKey(key)||Upgrade171.DEFAULTS.containsKey(key)||Upgrade172.DEFAULTS.containsKey(key)||Upgrade173.DEFAULTS.containsKey(key)||Upgrade174.DEFAULTS.containsKey(key))return false;
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
        String metadata;
        long pluginGeneration=-1L;
        CustomPluginFrameworkPublisher.Staged boot;
        ScheduledFuture<?> prune;
        boolean initialized,emptyRemote;
        Target(XposedService service,Acknowledged previous){
            this.service=service;
            if(previous!=null){confirmed=previous.snapshot==null?null:new Bundle(previous.snapshot);revision=previous.revision;
                metadata=previous.metadata;emptyRemote=previous.emptyRemote;initialized=true;}
        }
    }
    private static final class Acknowledged {
        final Bundle snapshot;final long revision;final String metadata;final boolean emptyRemote;
        Acknowledged(Target target){snapshot=target.confirmed==null?null:new Bundle(target.confirmed);revision=target.revision;
            metadata=target.metadata;emptyRemote=target.emptyRemote;}
    }
}
