package dev.puitheme;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercise the actual journal across timeout, revocation and simulated process death. */
public final class NumericTrialCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Numeric trial "+checks+": "+expected+" != "+actual);
    }
    private static final class Storage extends Context {
        final Map<String,SharedPreferences> files=new HashMap<>();
        final ContentResolver resolver=new ContentResolver();
        @Override public String getPackageName(){return "validation.numeric";}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return files.computeIfAbsent(name,key->new ActivationGuardPreferencesCheck.MemoryPreferences());}
        @Override public ContentResolver getContentResolver(){return resolver;}
    }
    private static final class FailingJournal {
        final SharedPreferences delegate=new ActivationGuardPreferencesCheck.MemoryPreferences();
        int failClears;
        int failWrites,throwWrites;
        final SharedPreferences proxy=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(object,method,args)->{
            if (!method.getName().equals("edit")) return method.invoke(delegate,args);
            SharedPreferences.Editor editor=delegate.edit();boolean[] clearing={false};
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(wrapper,operation,parameters)->{
                if(operation.getName().equals("clear"))clearing[0]=true;
                Object result=operation.invoke(editor,parameters);
                if(operation.getName().equals("commit")&&!clearing[0]&&throwWrites>0){throwWrites--;throw new IllegalStateException("write failed after memory mutation");}
                if(operation.getName().equals("commit")&&!clearing[0]&&failWrites>0){failWrites--;return false;}
                if(operation.getName().equals("commit")&&clearing[0]&&failClears>0){failClears--;return false;}
                return result instanceof SharedPreferences.Editor?wrapper:result;
            });
        });
    }
    /** Android commit failures mutate memory while leaving the last durable file untouched. */
    private static final class DurablePreferences {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final Map<String,Object> disk=new HashMap<>();
        int failCommits;
        int throwCommits;
        DurablePreferences(Map<String,Object> saved) { memory.values.putAll(saved);disk.putAll(saved); }
        final SharedPreferences proxy=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(object,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor editor=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(wrapper,operation,parameters)->{
                Object result=operation.invoke(editor,parameters);
                if(operation.getName().equals("commit")) {
                    if(throwCommits>0){throwCommits--;throw new IllegalStateException("disk commit unavailable");}
                    if(failCommits>0){failCommits--;return false;}
                    disk.clear();disk.putAll(memory.values);
                }
                return result instanceof SharedPreferences.Editor?wrapper:result;
            });
        });
        DurablePreferences reopen() { return new DurablePreferences(new HashMap<>(disk)); }
    }
    private static void retryTimer() throws Exception {
        Field field=NumericTrial.class.getDeclaredField("TIMER");field.setAccessible(true);
        android.os.Handler timer=(android.os.Handler)field.get(null);
        equal(1000L,timer.delay);equal(true,timer.delayed!=null);timer.delayed.run();
    }
    private static void durableFailureCases(String key) throws Exception {
        Storage storage=new Storage();Map<String,Object> original=new HashMap<>();
        original.put(key,17f);original.put("unrelated","retained");
        DurablePreferences raw=new DurablePreferences(original),journal=new DurablePreferences(new HashMap<>());
        storage.files.put(StatusBarSettings.PREFS,raw.proxy);storage.files.put("statusbar_numeric_trial",journal.proxy);
        coldStart(storage,raw.proxy);
        SharedPreferences guarded=new ActivationGuardPreferences(raw.proxy,()->true,()->{});
        // If preparation cannot reach disk, the trial never becomes visible or durable.
        journal.failCommits=1;
        equal(false,NumericTrial.begin(storage,raw.proxy,guarded,key,999f));
        equal(17f,raw.memory.values.get(key));equal(17f,raw.disk.get(key));equal(false,NumericTrial.active());
        equal(false,journal.disk.containsKey("key"));
        equal(true,NumericTrial.begin(storage,raw.proxy,guarded,key,999f));
        equal(999f,raw.disk.get(key));equal(key,journal.disk.get("key"));
        // A failed confirmation clear and failed recovery leave a durable recovery record.
        journal.failCommits=1;raw.failCommits=1;
        equal(false,NumericTrial.keep());equal(true,NumericTrial.active());
        equal(17f,raw.memory.values.get(key));equal(999f,raw.disk.get(key));
        equal(key,journal.disk.get("key"));equal(17f,journal.disk.get("value"));
        // This recreates stores from durable bytes, rather than reusing the mutated memory.
        raw=raw.reopen();journal=journal.reopen();
        storage.files.put(StatusBarSettings.PREFS,raw.proxy);storage.files.put("statusbar_numeric_trial",journal.proxy);
        equal(999f,raw.memory.values.get(key));coldStart(storage,raw.proxy);
        equal(17f,raw.memory.values.get(key));equal(17f,raw.disk.get(key));
        equal("retained",raw.disk.get("unrelated"));equal(false,journal.disk.containsKey("key"));equal(false,NumericTrial.active());
        guarded=new ActivationGuardPreferences(raw.proxy,()->true,()->{});
        float retryValue=NetworkSpeedControls.INTERVAL_MILLIS.equals(key)?2000f:-999f;
        equal(true,NumericTrial.begin(storage,raw.proxy,guarded,key,retryValue));
        // A transient rollback failure retains its deadline task and retries without user input.
        raw.failCommits=2;equal(false,NumericTrial.rollback());equal(true,NumericTrial.active());
        equal(retryValue,raw.disk.get(key));retryTimer();equal(true,NumericTrial.active());
        equal(retryValue,raw.disk.get(key));retryTimer();equal(false,NumericTrial.active());
        equal(17f,raw.disk.get(key));equal(false,journal.disk.containsKey("key"));
        // A thrown commit has the same recoverable boundary; editor creation is not simulated as failing.
        equal(true,NumericTrial.begin(storage,raw.proxy,guarded,key,777f));
        raw.throwCommits=1;equal(false,NumericTrial.rollback());equal(true,NumericTrial.active());
        equal(777f,raw.disk.get(key));equal(key,journal.disk.get("key"));retryTimer();
        equal(17f,raw.disk.get(key));equal(false,NumericTrial.active());equal(false,journal.disk.containsKey("key"));
    }
    private static void coldStart(Storage storage,SharedPreferences raw) throws Exception {
        Field field=NumericTrial.class.getDeclaredField("initialized");field.setAccessible(true);field.setBoolean(null,false);
        NumericTrial.recover(storage,raw);
    }
    private static void suggestedRangeSaveOrTrial(String key) throws Exception {
        Storage storage=new Storage();
        Map<String,Object> original=new HashMap<>();original.put(key,3f);original.put("unrelated","retained");
        DurablePreferences raw=new DurablePreferences(original),journal=new DurablePreferences(new HashMap<>());
        storage.files.put(StatusBarSettings.PREFS,raw.proxy);storage.files.put("statusbar_numeric_trial",journal.proxy);
        coldStart(storage,raw.proxy);
        SharedPreferences guarded=new ActivationGuardPreferences(raw.proxy,()->true,()->{});
        SettingsCatalog.Item item=SettingsCatalog.item(key);
        equal(true,item!=null);
        float outside=item.max+1f,kept=item.max+3f;
        // The same policy used by the actual editor chooses a normal saved write in-range.
        for(float value:new float[]{item.min,(item.min+item.max)/2f,item.max}) {
            equal(false,SettingsCatalog.requiresNumericTrial(item,value));
            equal(true,guarded.edit().putFloat(key,value).commit());
            equal(value,raw.disk.get(key));equal(false,NumericTrial.active());
            equal(false,journal.disk.containsKey("key"));
        }
        equal(true,SettingsCatalog.requiresNumericTrial(item,outside));
        equal(true,NumericTrial.begin(storage,raw.proxy,guarded,key,outside));
        equal(outside,raw.disk.get(key));equal(key,journal.disk.get("key"));equal(true,NumericTrial.active());
        equal(20000L,NumericTrial.deadline()-SystemClock.elapsedRealtime());
        equal(true,NumericTrial.rollback());equal(item.max,raw.disk.get(key));equal(false,NumericTrial.active());
        equal(true,NumericTrial.begin(storage,raw.proxy,guarded,key,kept));
        equal(true,NumericTrial.keep());equal(kept,raw.disk.get(key));equal(false,journal.disk.containsKey("key"));
        equal(true,NumericTrial.begin(storage,raw.proxy,guarded,key,1000000f));
        raw=raw.reopen();journal=journal.reopen();
        storage.files.put(StatusBarSettings.PREFS,raw.proxy);storage.files.put("statusbar_numeric_trial",journal.proxy);
        coldStart(storage,raw.proxy);equal(kept,raw.disk.get(key));equal(false,NumericTrial.active());
        equal("retained",raw.disk.get("unrelated"));
        equal(false,raw.disk.containsKey(NotificationBigClockSettings.MASTER));
        equal(false,raw.disk.containsKey(NotificationBigClockSettings.STACK_ENABLED));
    }
    public static void main(String[] args) throws Exception {
        Storage storage=new Storage();
        SharedPreferences raw=storage.getSharedPreferences(StatusBarSettings.PREFS,0);
        AtomicBoolean permission=new AtomicBoolean(true);
        SharedPreferences guarded=new ActivationGuardPreferences(raw,permission::get,()->{});
        coldStart(storage,raw);
        String key=StatusBarSettings.CLOCK_OFFSET_X;
        raw.edit().putFloat(key,12.75f).putString("unrelated","untouched").commit();
        equal(true,NumericTrial.begin(storage,raw,guarded,key,-2000f));
        equal(-2000f,raw.getFloat(key,0));equal(true,NumericTrial.active());
        equal(true,NumericTrial.rollback());equal(12.75f,raw.getFloat(key,0));equal("untouched",raw.getString("unrelated",""));
        equal(false,NumericTrial.active());
        equal(true,NumericTrial.begin(storage,raw,guarded,key,2000f));
        equal(true,NumericTrial.keep());equal(2000f,raw.getFloat(key,0));equal(false,NumericTrial.active());
        equal(true,NumericTrial.begin(storage,raw,guarded,key,99999f));
        SystemClock.uptime=NumericTrial.deadline();
        equal(false,NumericTrial.keep());equal(2000f,raw.getFloat(key,0));equal(false,NumericTrial.active());
        equal(true,NumericTrial.begin(storage,raw,guarded,key,-99999f));
        coldStart(storage,raw);equal(2000f,raw.getFloat(key,0));equal(false,NumericTrial.active());
        raw.edit().remove(key).commit();equal(true,NumericTrial.begin(storage,raw,guarded,key,5000f));
        coldStart(storage,raw);equal(false,raw.contains(key));
        raw.edit().putInt(key,7).commit();equal(true,NumericTrial.begin(storage,raw,guarded,key,1234f));
        equal(true,NumericTrial.rollback());equal(7,raw.getInt(key,0));
        permission.set(false);equal(false,NumericTrial.begin(storage,raw,guarded,key,9876f));
        equal(7,raw.getInt(key,0));equal(false,NumericTrial.active());permission.set(true);
        raw.edit().putBoolean(NotificationBigClockSettings.MASTER,true).putFloat(StatusBarSettings.SHADE_CLOCK_SCALE,135f).commit();
        equal(true,NumericTrial.begin(storage,raw,guarded,StatusBarSettings.SHADE_CLOCK_SCALE,2500f));
        equal(false,raw.getBoolean(NotificationBigClockSettings.MASTER,true));
        coldStart(storage,raw);equal(true,raw.getBoolean(NotificationBigClockSettings.MASTER,false));equal(135f,raw.getFloat(StatusBarSettings.SHADE_CLOCK_SCALE,0));
        equal(false,NumericTrial.begin(storage,raw,guarded,"unknown",100f));
        equal(false,NumericTrial.begin(storage,raw,guarded,key,Float.NaN));
        equal(false,NumericTrial.begin(storage,raw,guarded,key,Float.POSITIVE_INFINITY));
        equal(true,storage.resolver.notifications>0);
        Storage failingStorage=new Storage();FailingJournal failing=new FailingJournal();
        failingStorage.files.put("statusbar_numeric_trial",failing.proxy);
        SharedPreferences failingRaw=failingStorage.getSharedPreferences(StatusBarSettings.PREFS,0);
        failingRaw.edit().putFloat(key,10f).commit();coldStart(failingStorage,failingRaw);
        equal(true,NumericTrial.begin(failingStorage,failingRaw,new ActivationGuardPreferences(failingRaw,()->true,()->{}),key,500f));
        failing.failClears=1;equal(false,NumericTrial.keep());equal(10f,failingRaw.getFloat(key,0));equal(false,NumericTrial.active());
        equal(true,NumericTrial.begin(failingStorage,failingRaw,new ActivationGuardPreferences(failingRaw,()->true,()->{}),key,700f));
        failing.failClears=4;equal(false,NumericTrial.rollback());equal(10f,failingRaw.getFloat(key,0));equal(true,NumericTrial.active());
        // No new trial can overwrite a recovery record that is not durably cleared yet.
        equal(false,NumericTrial.begin(failingStorage,failingRaw,failingRaw,key,800f));equal(10f,failingRaw.getFloat(key,0));
        failing.failClears=0;equal(true,NumericTrial.rollback());equal(false,NumericTrial.active());
        Storage writeStorage=new Storage();FailingJournal writeFailure=new FailingJournal();
        writeStorage.files.put(StatusBarSettings.PREFS,writeFailure.proxy);
        SharedPreferences writeRaw=writeStorage.getSharedPreferences(StatusBarSettings.PREFS,0);
        writeRaw.edit().putFloat(key,32f).commit();coldStart(writeStorage,writeRaw);
        SharedPreferences writeGuard=new ActivationGuardPreferences(writeRaw,()->true,()->{});
        writeFailure.failWrites=1;equal(false,NumericTrial.begin(writeStorage,writeRaw,writeGuard,key,999f));
        equal(32f,writeRaw.getFloat(key,0));equal(false,NumericTrial.active());
        writeFailure.throwWrites=1;equal(false,NumericTrial.begin(writeStorage,writeRaw,writeGuard,key,777f));
        equal(32f,writeRaw.getFloat(key,0));equal(false,NumericTrial.active());
        durableFailureCases(key);
        // Retain recovery of old journals, but only the new millisecond field has an editor.
        durableFailureCases(NetworkSpeedControls.INTERVAL_SECONDS);
        durableFailureCases(NetworkSpeedControls.INTERVAL_MILLIS);
        suggestedRangeSaveOrTrial(NotificationBigClockSettings.VISIBLE_COUNT);
        equal(1f,SettingsCatalog.item(NetworkSpeedControls.INTERVAL_MILLIS).min);
        equal(500f,SettingsCatalog.item(NetworkSpeedControls.INTERVAL_MILLIS).max);
        suggestedRangeSaveOrTrial(NetworkSpeedControls.INTERVAL_MILLIS);
        System.out.println("Numeric trial checks passed: "+checks);
    }
}
