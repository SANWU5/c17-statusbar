package dev.puitheme;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Bundle;
import io.github.libxposed.service.XposedService;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Real reset barrier/journal/mirror ownership; no phone, root command or private external data. */
public final class MaintenanceResetCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Reset "+checks+": "+expected+" != "+actual);}
    private static final class Store {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final Map<String,Object> disk=new HashMap<>();int fail;volatile Runnable afterCommit;
        final SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(owner,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor editor=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(wrapper,operation,parameters)->{
                Object answer=operation.invoke(editor,parameters);
                if(operation.getName().equals("commit")){
                    if(fail>0){fail--;return false;}disk.clear();disk.putAll(memory.values);
                    Runnable callback=afterCommit;afterCommit=null;if(callback!=null)callback.run();
                }
                return answer instanceof SharedPreferences.Editor?wrapper:answer;
            });
        });
    }
    private static final class Storage extends Context {
        final boolean device;final Map<String,Store> stores=new HashMap<>();
        Storage credential;final ContentResolver resolver=new ContentResolver();
        Storage(boolean device){this.device=device;}
        Store store(String name){return stores.computeIfAbsent(name,ignored->new Store());}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return store(name).prefs;}
        @Override public boolean isDeviceProtectedStorage(){return device;}
        @Override public Context createDeviceProtectedStorageContext(){return this;}
        @Override public Context createPackageContext(String name,int flags){return credential;}
        @Override public ContentResolver getContentResolver(){return resolver;}
    }
    private static void numericCold()throws Exception {
        Field initialized=NumericTrial.class.getDeclaredField("initialized");initialized.setAccessible(true);initialized.set(null,false);
        Field pending=NumericTrial.class.getDeclaredField("pending");pending.setAccessible(true);pending.set(null,null);
    }
    private static void mirrorOwner(Storage context)throws Exception {
        Field owner=SettingsFrameworkMirror.class.getDeclaredField("owner");owner.setAccessible(true);owner.set(null,context);
        Field targets=SettingsFrameworkMirror.class.getDeclaredField("TARGETS");targets.setAccessible(true);((Map<?,?>)targets.get(null)).clear();
    }
    private static Storage configured()throws Exception {
        Storage storage=new Storage(true);storage.credential=new Storage(false);numericCold();mirrorOwner(storage);
        Store main=storage.store(StatusBarSettings.PREFS);
        main.prefs.edit().putFloat(StatusBarSettings.CLOCK_SCALE,123f).putBoolean(NotificationBigClockSettings.MASTER,true)
                .putBoolean(StatusBarSettings.SAFE_MODE,true).putString("font_name","synthetic imported font").commit();
        Store backup=storage.store("statusbar_settings_last_good");backup.prefs.edit().putInt("_c17_snapshot_schema",1)
                .putFloat(StatusBarSettings.CLOCK_SCALE,88f).putBoolean(NotificationBigClockSettings.MASTER,true).commit();
        storage.credential.store(StatusBarSettings.PREFS).prefs.edit().putFloat(StatusBarSettings.CLOCK_SCALE,77f).commit();
        storage.credential.store("app_free_notice").prefs.edit().putInt("accepted_version",1).commit();
        storage.store("app_access").prefs.edit().putBoolean("root_requested",true).commit();return storage;
    }
    private static void successful()throws Exception {
        Storage context=configured();Store primary=context.store(StatusBarSettings.PREFS);
        NumericTrial.recover(context,primary.prefs);
        SharedPreferences guard=new ActivationGuardPreferences(primary.prefs,()->true,()->{});
        equal(true,NumericTrial.begin(context,primary.prefs,guard,StatusBarSettings.CLOCK_SCALE,9999f));
        SettingsSnapshot.scheduleSave(context,primary.prefs,null);
        SettingsSnapshot.ResetResult result=SettingsSnapshot.resetForMaintenance(context);
        equal(true,result.primaryReset);equal(true,result.recoveryCleared);equal(true,result.mirrorQueued);equal(false,result.frameworkSynced);
        equal(true,result.localSuccess());equal(Collections.singletonMap(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true),primary.disk);
        equal(Collections.emptyMap(),context.store("statusbar_numeric_trial").disk);
        equal(Collections.emptyMap(),context.store("statusbar_settings_last_good").disk);
        equal(Collections.emptyMap(),context.credential.store(StatusBarSettings.PREFS).disk);
        equal(1,context.credential.store("app_free_notice").disk.get("accepted_version"));
        equal(true,context.store("app_access").disk.get("root_requested"));equal(false,NumericTrial.active());equal(0L,NumericTrial.deadline());
        equal(true,context.store("c17_maintenance").disk.get("framework_defaults_pending"));
        Field timer=NumericTrial.class.getDeclaredField("TIMER");timer.setAccessible(true);equal(null,((android.os.Handler)timer.get(null)).delayed);
        Field expire=NumericTrial.class.getDeclaredField("EXPIRE");expire.setAccessible(true);((Runnable)expire.get(null)).run();
        equal(Collections.singletonMap(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true),primary.disk);
        // A pending old backup cannot resurrect its value after the serial barrier or during cold recovery.
        Thread.sleep(600);equal(Collections.emptyMap(),context.store("statusbar_settings_last_good").disk);
        SettingsSnapshot.recoverIfEmpty(context,primary.prefs);equal(Collections.singletonMap(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true),primary.disk);
        Bundle defaults=SettingsSnapshot.fromPreferences(primary.disk);equal(true,SettingsSnapshot.complete(defaults));
        equal(false,defaults.get(NotificationBigClockSettings.MASTER));equal(false,defaults.get(StatusBarSettings.SAFE_MODE));
        // Reconnection clears every previous remote key and uses the durable local defaults.
        Store remote=new Store();remote.prefs.edit().putString("obsolete_private_key","old").commit();
        XposedService service=new XposedService(remote.prefs);SettingsFrameworkMirror.bound(service);
        Field work=SettingsFrameworkMirror.class.getDeclaredField("WORK");work.setAccessible(true);
        for(int i=0;i<3;i++)((ScheduledThreadPoolExecutor)work.get(null)).submit(()->{}).get(3,TimeUnit.SECONDS);
        equal(false,remote.disk.containsKey("obsolete_private_key"));equal(false,remote.disk.get(NotificationBigClockSettings.MASTER));
        equal(true,remote.disk.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(remote.disk)));
        equal(false,context.store("c17_maintenance").disk.containsKey("framework_defaults_pending"));SettingsFrameworkMirror.unbound(service);
    }
    private static void failures()throws Exception {
        Storage context=configured();Store primary=context.store(StatusBarSettings.PREFS);primary.fail=1;
        SettingsSnapshot.ResetResult result=SettingsSnapshot.resetForMaintenance(context);equal(false,result.primaryReset);equal(false,result.localSuccess());
        equal(123f,primary.disk.get(StatusBarSettings.CLOCK_SCALE));equal(true,primary.disk.get(NotificationBigClockSettings.MASTER));
        equal(true,context.store("statusbar_settings_last_good").disk.containsKey("_c17_snapshot_schema"));
        context=configured();primary=context.store(StatusBarSettings.PREFS);NumericTrial.recover(context,primary.prefs);
        equal(true,NumericTrial.begin(context,primary.prefs,new ActivationGuardPreferences(primary.prefs,()->true,()->{}),StatusBarSettings.CLOCK_SCALE,9999f));
        context.store("statusbar_numeric_trial").fail=1;result=SettingsSnapshot.resetForMaintenance(context);
        equal(false,result.primaryReset);equal(true,NumericTrial.active());equal(123f,primary.disk.get(StatusBarSettings.CLOCK_SCALE));
        // Retry finishes the recovery journal before deleting primary values.
        result=SettingsSnapshot.resetForMaintenance(context);equal(true,result.localSuccess());equal(false,NumericTrial.active());
        context=configured();context.store("statusbar_settings_last_good").fail=1;
        result=SettingsSnapshot.resetForMaintenance(context);equal(true,result.primaryReset);equal(false,result.recoveryCleared);equal(false,result.localSuccess());
        equal(Collections.singletonMap(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true),context.store(StatusBarSettings.PREFS).disk);
        result=SettingsSnapshot.resetForMaintenance(context);equal(true,result.localSuccess());
        context=configured();context.store("c17_maintenance").fail=1;
        result=SettingsSnapshot.resetForMaintenance(context);equal(true,result.primaryReset);equal(false,result.mirrorQueued);equal(false,result.localSuccess());
        result=SettingsSnapshot.resetForMaintenance(context);equal(true,result.localSuccess());
    }
    private static void assets()throws Exception {
        File root=Files.createTempDirectory("c17-reset-check").toFile(),fonts=new File(root,"fonts"),images=new File(root,"notification-icons");
        equal(true,fonts.mkdir());equal(true,images.mkdir());
        for(String name:new String[]{"custom.font","import-123.font","previous-456.font","notes.txt"})equal(true,new File(fonts,name).createNewFile());
        for(String name:new String[]{"custom.png","pending-123.png","previous-456.png","donation-original.png"})equal(true,new File(images,name).createNewFile());
        equal(3,MaintenanceReset.clearImportedAssets(root,"fonts","custom.font","(?:import|previous)-[A-Za-z0-9_-]+\\.font"));
        equal(3,MaintenanceReset.clearImportedAssets(root,"notification-icons","custom.png","(?:pending|previous)-[A-Za-z0-9_-]+\\.png"));
        equal(true,new File(fonts,"notes.txt").exists());equal(true,new File(images,"donation-original.png").exists());
        equal(0,MaintenanceReset.clearImportedAssets(root,"fonts","custom.font","(?:import|previous)-[A-Za-z0-9_-]+\\.font"));
        // A directory named like an imported file is refused, never recursively erased.
        File unexpected=new File(fonts,"custom.font");equal(true,unexpected.mkdir());
        try{MaintenanceReset.clearImportedAssets(root,"fonts","custom.font","import-.*\\.font");throw new AssertionError("Expected directory refusal");}
        catch(IOException expected){checks++;}
        unexpected.delete();new File(fonts,"notes.txt").delete();new File(images,"donation-original.png").delete();fonts.delete();images.delete();root.delete();
    }
    private static void runningWriter()throws Exception {
        Storage context=configured();Store primary=context.store(StatusBarSettings.PREFS);
        CountDownLatch reached=new CountDownLatch(1),release=new CountDownLatch(1);
        primary.afterCommit=()->{
            reached.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("Writer timeout");}
            catch(InterruptedException interrupted){throw new AssertionError(interrupted);}
        };
        SettingsSnapshot.scheduleSave(context,primary.prefs,null);SettingsSnapshot.flushPending(context);
        equal(true,reached.await(3,TimeUnit.SECONDS));
        FutureTask<SettingsSnapshot.ResetResult> reset=new FutureTask<>(()->SettingsSnapshot.resetForMaintenance(context));
        Thread worker=new Thread(reset,"synthetic-reset");worker.start();
        // The writer already owns raw. Maintenance must queue behind it, not just cancel its Future.
        Thread.sleep(30);equal(false,reset.isDone());release.countDown();
        equal(true,reset.get(3,TimeUnit.SECONDS).localSuccess());
        equal(Collections.singletonMap(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true),primary.disk);
        equal(Collections.emptyMap(),context.store("statusbar_settings_last_good").disk);
        Thread.sleep(600);equal(Collections.emptyMap(),context.store("statusbar_settings_last_good").disk);
    }
    public static void main(String[] args)throws Exception {
        successful();failures();assets();runningWriter();
        Map<String,Object> old=new HashMap<>();old.put(StatusBarShadeIconSettings.MASTER,"obsolete damaged type");
        equal(true,SettingsSnapshot.complete(SettingsSnapshot.fromPreferences(old)));
        System.out.println("MaintenanceResetCheck passed "+checks+" checks");
    }
}
