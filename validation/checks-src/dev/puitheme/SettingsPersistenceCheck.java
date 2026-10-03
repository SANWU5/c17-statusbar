package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic memory/disk separation models Android failures, real cold reopen and concurrent edits. */
public final class SettingsPersistenceCheck {
    private static final String BACKUP="statusbar_settings_last_good", SCHEMA="_c17_snapshot_schema";
    private static int checks;
    private static void equal(Object expected,Object actual){
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Persistence "+checks+": "+expected+" != "+actual);
    }
    private static final class Store {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final Map<String,Object> disk=new LinkedHashMap<>();
        final List<Runnable> notifications=new ArrayList<>();
        int failCommits,throwCommits,throwReads,commits,applies,broadcasts;
        Runnable afterCommit,afterBarrier;
        Store(){this(new HashMap<>());}
        Store(Map<String,Object> initial){memory.values.putAll(initial);disk.putAll(initial);}
        final SharedPreferences raw=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(owner,method,args)->{
            if(method.getName().equals("getAll")&&throwReads>0){throwReads--;throw new IllegalStateException("synthetic unavailable read");}
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor editor=memory.edit();boolean[] dirty={false};
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(wrapper,operation,parameters)->{
                String name=operation.getName();
                if(!name.equals("commit")&&!name.equals("apply")){dirty[0]=true;operation.invoke(editor,parameters);return wrapper;}
                boolean changed=dirty[0];dirty[0]=false;
                // Native apply exposes memory before queued disk work; commit also mutates memory on failure.
                editor.commit();
                notifications.add(()->{if(SettingsSnapshot.notificationAllowed(this.raw))broadcasts++;});
                if(name.equals("apply")){applies++;return null;}
                commits++;
                boolean thrown=throwCommits>0,failed=failCommits>0;
                if(thrown)throwCommits--;if(failed)failCommits--;
                if(!thrown&&!failed){disk.clear();disk.putAll(memory.values);}
                Runnable event=afterCommit;afterCommit=null;if(event!=null)event.run();
                if(!changed){event=afterBarrier;afterBarrier=null;if(event!=null)event.run();}
                if(thrown)throw new IllegalStateException("synthetic disk failure after memory update");
                return !failed;
            });
        });
        void dispatch(){for(Runnable event:new ArrayList<>(notifications))event.run();notifications.clear();}
        Store reopen(){return new Store(new LinkedHashMap<>(disk));}
    }
    private static final class Storage extends Context {
        final Map<String,Store> stores=new HashMap<>();
        final UserManager user=new UserManager();
        Storage credential;
        final boolean device;
        Storage(){this(true);}
        Storage(boolean device){this.device=device;}
        Store store(String name){return stores.computeIfAbsent(name,ignored->new Store());}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return store(name).raw;}
        @Override public Context createDeviceProtectedStorageContext(){return this;}
        @Override public boolean isDeviceProtectedStorage(){return device;}
        @Override public Context createPackageContext(String name,int flags){return credential==null?this:credential;}
        @Override public Object getSystemService(String name){return "user".equals(name)?user:super.getSystemService(name);}
    }
    private static Map<String,Object> original(float value){
        Map<String,Object> values=new LinkedHashMap<>();values.put(StatusBarSettings.CLOCK_SCALE,value);
        values.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN,true);values.put(NotificationBigClockSettings.MASTER,true);
        values.put(NotificationBigClockSettings.STACK_ENABLED,true);values.put(StatusBarSettings.SAFE_MODE,true);
        values.put("font_name","synthetic retained asset");return values;
    }
    private static Object pending(Store store)throws Exception{
        Field field=SettingsSnapshot.class.getDeclaredField("SAVES");field.setAccessible(true);
        Object value=((Map<?,?>)field.get(null)).get(store.raw);
        if(value!=null){Field future=value.getClass().getDeclaredField("future");future.setAccessible(true);((Future<?>)future.get(value)).cancel(false);}
        return value;
    }
    private static void saveNow(Storage context,Store store)throws Exception{
        SettingsSnapshot.scheduleSave(context,store.raw,null);runPending(pending(store));
    }
    private static void runPending(Object pending)throws Exception{
        equal(true,pending!=null);Method save=SettingsSnapshot.class.getDeclaredMethod("save",pending.getClass());save.setAccessible(true);save.invoke(null,pending);
    }
    private static void recordBackup(Store store,Map<String,Object> values){
        store.memory.values.clear();store.memory.values.putAll(values);store.memory.values.put(SCHEMA,1);
        store.disk.clear();store.disk.putAll(store.memory.values);
    }
    private static void commitFailures()throws Exception{
        for(int kind:new int[]{0,1,2}){
            Store store=new Store(original(137.25f));Storage context=new Storage();context.stores.put(StatusBarSettings.PREFS,store);
            AtomicInteger failure=new AtomicInteger();SharedPreferences guard=new ActivationGuardPreferences(store.raw,()->true,()->{}).withPersistence(context,failure::incrementAndGet);
            if(kind==2)store.throwCommits=1;else store.failCommits=kind+1;
            equal(false,guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,999f).putString(StatusBarSettings.SHADE_CLOCK_PATTERN,"HH:mm:ss").commit());
            store.dispatch();equal(0,store.broadcasts);equal(1,failure.get());equal(false,SettingsSnapshot.notificationAllowed(store.raw));
            equal(137.25f,store.memory.values.get(StatusBarSettings.CLOCK_SCALE));equal(137.25f,store.disk.get(StatusBarSettings.CLOCK_SCALE));
            equal(true,store.memory.values.get(NotificationBigClockSettings.MASTER));
            equal(false,store.memory.values.containsKey(StatusBarSettings.SHADE_CLOCK_PATTERN));
            equal(137.25f,SettingsSnapshot.readableValues(store.raw).get(StatusBarSettings.CLOCK_SCALE));
            equal(137.25f,store.reopen().memory.values.get(StatusBarSettings.CLOCK_SCALE));
            equal(null,pending(store));
            equal(true,guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,150f).commit());store.dispatch();
            equal(true,SettingsSnapshot.notificationAllowed(store.raw));equal(true,store.broadcasts>0);
            equal(150f,store.reopen().memory.values.get(StatusBarSettings.CLOCK_SCALE));runPending(pending(store));
        }
        Store concurrent=new Store(original(137.25f));concurrent.failCommits=1;
        concurrent.afterCommit=()->{concurrent.memory.values.put(StatusBarSettings.CLOCK_SCALE,77f);concurrent.disk.put(StatusBarSettings.CLOCK_SCALE,77f);concurrent.memory.values.put("new_native_value",17);};
        SharedPreferences guard=new ActivationGuardPreferences(concurrent.raw,()->true,()->{});
        equal(false,guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,888f).putBoolean(NotificationBigClockSettings.MASTER,false).commit());
        equal(77f,concurrent.memory.values.get(StatusBarSettings.CLOCK_SCALE));equal(77f,concurrent.disk.get(StatusBarSettings.CLOCK_SCALE));
        equal(17,concurrent.memory.values.get("new_native_value"));equal(true,concurrent.memory.values.get(NotificationBigClockSettings.MASTER));
        Store cleared=new Store(original(41f));cleared.memory.values.put("local_set",new java.util.HashSet<>(java.util.Arrays.asList("synthetic","unchanged")));cleared.disk.putAll(cleared.memory.values);
        Map<String,Object> before=new LinkedHashMap<>(cleared.memory.values);cleared.failCommits=1;
        equal(false,new ActivationGuardPreferences(cleared.raw,()->true,()->{}).edit().clear().putFloat(StatusBarSettings.CLOCK_SCALE,999f).commit());
        equal(before,cleared.memory.values);equal(before,cleared.disk);
        Store imported=new Store(original(12f));imported.failCommits=1;
        String json="{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\"clock_scale\":233,\"wifi_enabled\":true,\"notification_big_clock_stack_enabled\":false}}";
        equal(false,ConfigTransfer.commit(new ActivationGuardPreferences(imported.raw,()->true,()->{}),ConfigTransfer.prepare(json,true)));
        equal(12f,imported.memory.values.get(StatusBarSettings.CLOCK_SCALE));equal(false,imported.memory.values.containsKey("wifi_enabled"));
        equal(true,imported.memory.values.get(NotificationBigClockSettings.STACK_ENABLED));
    }
    private static void backups()throws Exception{
        Storage context=new Storage();Store raw=new Store(original(137.25f));context.stores.put(StatusBarSettings.PREFS,raw);
        saveNow(context,raw);Store backup=context.store(BACKUP);
        equal(137.25f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));equal(true,backup.disk.get(StatusBarSettings.SAFE_MODE));
        equal(true,backup.disk.get(NotificationBigClockSettings.STACK_ENABLED));
        int primaryCommits=raw.commits,backupCommits=backup.commits;
        for(int n=0;n<1000;n++)SettingsSnapshot.scheduleSave(context,raw.raw,null);
        equal(primaryCommits,raw.commits);equal(backupCommits,backup.commits);equal(null,pending(raw));
        SharedPreferences guard=new ActivationGuardPreferences(raw.raw,()->true,()->{});
        guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,211f).apply();SettingsSnapshot.scheduleSave(context,raw.raw,null);Object old=pending(raw);
        raw.afterBarrier=()->{guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,338f).apply();SettingsSnapshot.scheduleSave(context,raw.raw,null);};
        runPending(old);equal(137.25f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));equal(211f,raw.disk.get(StatusBarSettings.CLOCK_SCALE));
        equal(338f,raw.memory.values.get(StatusBarSettings.CLOCK_SCALE));runPending(pending(raw));
        equal(338f,raw.disk.get(StatusBarSettings.CLOCK_SCALE));equal(338f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));
        // A->B->A still has a new write generation; equal final contents cannot validate the old barrier.
        guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,400f).apply();SettingsSnapshot.scheduleSave(context,raw.raw,null);old=pending(raw);
        raw.afterBarrier=()->{guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,401f).apply();SettingsSnapshot.scheduleSave(context,raw.raw,null);
            guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,400f).apply();SettingsSnapshot.scheduleSave(context,raw.raw,null);};
        runPending(old);equal(338f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));runPending(pending(raw));equal(400f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));
        guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,500f).apply();raw.failCommits=1;AtomicInteger failed=new AtomicInteger();
        SettingsSnapshot.scheduleSave(context,raw.raw,failed::incrementAndGet);runPending(pending(raw));
        equal(1,failed.get());equal(400f,raw.disk.get(StatusBarSettings.CLOCK_SCALE));equal(500f,raw.memory.values.get(StatusBarSettings.CLOCK_SCALE));
        equal(400f,SettingsSnapshot.durableSnapshot(context).get(StatusBarSettings.CLOCK_SCALE));
        saveNow(context,raw);equal(500f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));
        guard.edit().putFloat(StatusBarSettings.CLOCK_SCALE,600f).apply();backup.failCommits=1;saveNow(context,raw);
        equal(600f,raw.disk.get(StatusBarSettings.CLOCK_SCALE));equal(500f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));
        equal(600f,backup.memory.values.get(StatusBarSettings.CLOCK_SCALE));equal(500f,SettingsSnapshot.durableSnapshot(context).get(StatusBarSettings.CLOCK_SCALE));
        saveNow(context,raw);equal(600f,backup.disk.get(StatusBarSettings.CLOCK_SCALE));
        raw.memory.values.put(StatusBarSettings.CLOCK_SCALE,Float.NaN);int writes=raw.commits;SettingsSnapshot.scheduleSave(context,raw.raw,null);
        equal(writes,raw.commits);equal(null,pending(raw));equal(600f,SettingsSnapshot.durableSnapshot(context).get(StatusBarSettings.CLOCK_SCALE));
        // Missing/temporarily unavailable backup may become available without restarting the provider.
        Storage delayed=new Storage();equal(null,SettingsSnapshot.durableSnapshot(delayed));recordBackup(delayed.store(BACKUP),original(42f));
        equal(42f,SettingsSnapshot.durableSnapshot(delayed).get(StatusBarSettings.CLOCK_SCALE));
        Storage readFailure=new Storage();recordBackup(readFailure.store(BACKUP),original(43f));readFailure.store(BACKUP).throwReads=1;
        equal(null,SettingsSnapshot.durableSnapshot(readFailure));equal(43f,SettingsSnapshot.durableSnapshot(readFailure).get(StatusBarSettings.CLOCK_SCALE));
        Storage firstFailure=new Storage();Store firstRaw=new Store(original(45f));firstFailure.stores.put(StatusBarSettings.PREFS,firstRaw);firstFailure.store(BACKUP).failCommits=1;
        saveNow(firstFailure,firstRaw);equal(null,SettingsSnapshot.durableSnapshot(firstFailure));equal(0,firstFailure.store(BACKUP).disk.size());
        saveNow(firstFailure,firstRaw);equal(45f,SettingsSnapshot.durableSnapshot(firstFailure).get(StatusBarSettings.CLOCK_SCALE));
    }
    private static void recoveryAndMigration()throws Exception{
        Storage recovery=new Storage();Store raw=recovery.store(StatusBarSettings.PREFS);recordBackup(recovery.store(BACKUP),original(88f));
        raw.failCommits=2;SettingsSnapshot.recoverIfEmpty(recovery,raw.raw);
        equal(true,raw.memory.values.isEmpty());equal(true,raw.disk.isEmpty());equal(null,pending(raw));
        equal(88f,SettingsSnapshot.durableSnapshot(recovery).get(StatusBarSettings.CLOCK_SCALE));
        SettingsSnapshot.recoverIfEmpty(recovery,raw.raw);equal(88f,raw.reopen().memory.values.get(StatusBarSettings.CLOCK_SCALE));
        equal(true,raw.memory.values.get(StatusBarSettings.SAFE_MODE));runPending(pending(raw));
        raw.raw.edit().putFloat(StatusBarSettings.CLOCK_SCALE,99f).commit();SettingsSnapshot.recoverIfEmpty(recovery,raw.raw);
        equal(99f,raw.memory.values.get(StatusBarSettings.CLOCK_SCALE));
        Storage boot=new Storage();boot.user.unlocked=false;boot.credential=new Storage(false);recordInitial(boot.credential.store(StatusBarSettings.PREFS),original(154f));
        SharedPreferences locked=StatusBarSettings.preferences(boot);equal(true,locked.getAll().isEmpty());equal(0,boot.store(StatusBarSettings.PREFS).commits);
        equal(false,boot.store("statusbar_settings_storage").memory.values.containsKey("credential_preferences_migrated"));
        equal(true,StatusBarSettings.bool(locked.getAll(),StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        boot.user.unlocked=true;boot.store(StatusBarSettings.PREFS).failCommits=1;
        StatusBarSettings.preferences(boot);equal(false,boot.store(StatusBarSettings.PREFS).memory.values.containsKey(StatusBarSettings.CLOCK_SCALE));
        equal(false,boot.store("statusbar_settings_storage").memory.values.containsKey("credential_preferences_migrated"));
        SharedPreferences unlocked=StatusBarSettings.preferences(boot);equal(154f,unlocked.getAll().get(StatusBarSettings.CLOCK_SCALE));
        equal(true,boot.store("statusbar_settings_storage").disk.get("credential_preferences_migrated"));
        equal(true,unlocked.getAll().get(NotificationBigClockSettings.STACK_ENABLED));equal(true,unlocked.getAll().get(StatusBarSettings.SAFE_MODE));
        // The migration marker's failed memory update must not skip the next real migration.
        Storage marker=new Storage();marker.credential=new Storage(false);recordInitial(marker.credential.store(StatusBarSettings.PREFS),original(166f));
        marker.store("statusbar_settings_storage").failCommits=1;StatusBarSettings.preferences(marker);
        equal(false,marker.store("statusbar_settings_storage").memory.values.containsKey("credential_preferences_migrated"));
        marker.store(StatusBarSettings.PREFS).raw.edit().putFloat(StatusBarSettings.CLOCK_SCALE,177f).commit();StatusBarSettings.preferences(marker);
        equal(177f,marker.store(StatusBarSettings.PREFS).memory.values.get(StatusBarSettings.CLOCK_SCALE));
        equal(true,marker.store("statusbar_settings_storage").disk.get("credential_preferences_migrated"));
        Storage empty=new Storage();empty.credential=new Storage(false);StatusBarSettings.preferences(empty);
        equal(true,empty.store(StatusBarSettings.PREFS).memory.values.isEmpty());
        equal(false,empty.store("statusbar_settings_storage").memory.values.containsKey("credential_preferences_migrated"));
        recordInitial(empty.credential.store(StatusBarSettings.PREFS),original(199f));StatusBarSettings.preferences(empty);
        equal(199f,empty.store(StatusBarSettings.PREFS).disk.get(StatusBarSettings.CLOCK_SCALE));
    }
    private static void recordInitial(Store store,Map<String,Object> values){store.memory.values.putAll(values);store.disk.putAll(values);}
    public static void main(String[] args)throws Exception{
        commitFailures();backups();recoveryAndMigration();
        System.out.println("Settings persistence checks passed: "+checks);
    }
}
