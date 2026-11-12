package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import io.github.libxposed.service.XposedService;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Real production codec/publisher/reader plus synthetic framework memory/disk boundaries. */
public final class SettingsFrameworkMirrorCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Framework mirror "+checks+": "+expected+" != "+actual);
    }
    private static final class Store {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final Map<String,Object> disk=new LinkedHashMap<>();
        final java.util.List<SharedPreferences.OnSharedPreferenceChangeListener> listeners=new java.util.ArrayList<>();
        int commits,reads,fail,failListener;Runnable duringRegister;
        final SharedPreferences preferences=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(owner,method,args)->{
            String name=method.getName();
            if(name.equals("getAll"))reads++;
            if(name.equals("registerOnSharedPreferenceChangeListener")) {
                if(failListener>0){failListener--;throw new IllegalStateException("synthetic observer unavailable");}
                listeners.add((SharedPreferences.OnSharedPreferenceChangeListener)args[0]);Object answer=method.invoke(memory,args);
                Runnable callback=duringRegister;duringRegister=null;if(callback!=null)callback.run();return answer;
            }
            if(name.equals("unregisterOnSharedPreferenceChangeListener"))listeners.remove(args[0]);
            if(!name.equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor editor=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(wrapper,operation,parameters)->{
                if(!operation.getName().equals("commit")&&!operation.getName().equals("apply")){
                    operation.invoke(editor,parameters);return wrapper;
                }
                // The actual service SDK exposes memory before a potentially failed Binder commit.
                editor.commit();commits++;
                if(fail>0){fail--;return false;}
                disk.clear();disk.putAll(memory.values);return operation.getName().equals("commit")?true:null;
            });
        });
        void replace(Map<String,?> map){memory.values.clear();memory.values.putAll(map);disk.clear();disk.putAll(map);}
        void notifyKey(String key){for(SharedPreferences.OnSharedPreferenceChangeListener listener:new java.util.ArrayList<>(listeners))listener.onSharedPreferenceChanged(preferences,key);}
    }
    private static Bundle saved(float offset,boolean safe){
        Map<String,Object> values=new HashMap<>();values.put("data_enabled",true);
        values.put(StatusBarSettings.DATA_OFFSET_X,offset);values.put(StatusBarSettings.SAFE_MODE,safe);
        return SettingsSnapshot.fromPreferences(values);
    }
    private static Map<String,Object> wire(Bundle snapshot,long revision){
        Map<String,Object> values=new LinkedHashMap<>();for(String key:snapshot.keySet())values.put(key,snapshot.get(key));
        values.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION);values.put(SettingsFrameworkMirror.REVISION,revision);return values;
    }
    private static Map<String,Object> legacyWire(Bundle snapshot,long revision){
        Map<String,Object> values=wire(snapshot,revision);
        values.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        values.remove(FluidCloudStyle.FOLLOW_MEDIA);values.put(SettingsFrameworkMirror.SCHEMA,1);
        for(String key:Upgrade103.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade76.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade77.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade78.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade79.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade82.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade90.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade91.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade96.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade98.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade99.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade100.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade101.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade103.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade93.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade117.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())values.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())values.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    private static Object legacyExpected(Bundle current,String key){
        // A historical wire copy retains the old clock basis; clean installs use basis 2.
        // These schema1 fixtures predate dual rows, so their absent lower size uses the frozen schema13 default.
        if(ClockLayoutSettings.DUAL_LOWER_SCALE.equals(key))return Upgrade90.DEFAULTS.get(key);
        return NativeClockMeasurement.SCALE_BASIS_VERSION.equals(key)?1f:current.get(key);
    }
    private static void settle() throws Exception {
        Field field=SettingsFrameworkMirror.class.getDeclaredField("WORK");field.setAccessible(true);
        ScheduledThreadPoolExecutor worker=(ScheduledThreadPoolExecutor)field.get(null);
        for(int pass=0;pass<3;pass++)worker.submit(()->{}).get(3,TimeUnit.SECONDS);
    }
    private static void trial(boolean active) throws Exception {
        Field initialized=NumericTrial.class.getDeclaredField("initialized"),pending=NumericTrial.class.getDeclaredField("pending");
        initialized.setAccessible(true);pending.setAccessible(true);initialized.setBoolean(null,active);
        pending.set(null,active?java.util.Collections.singletonMap("key",StatusBarSettings.DATA_OFFSET_X):null);
    }
    private static boolean added58(String key) {
        return !added60(key)&&!Upgrade117.DEFAULTS.containsKey(key)&&!Upgrade135.DEFAULTS.containsKey(key)&&!Upgrade160.DEFAULTS.containsKey(key)
                &&!Upgrade166.DEFAULTS.containsKey(key)&&!Upgrade168.DEFAULTS.containsKey(key)&&!Upgrade169.DEFAULTS.containsKey(key)&&!Upgrade171.DEFAULTS.containsKey(key)&&!Upgrade172.DEFAULTS.containsKey(key)&&!Upgrade173.DEFAULTS.containsKey(key)&&!Upgrade174.DEFAULTS.containsKey(key)&&!Upgrade175.DEFAULTS.containsKey(key)&&!Upgrade177.DEFAULTS.containsKey(key)&&(key.startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX)
                ||key.startsWith("status_hint_icons_")
                ||NotificationGroupStack.MASTER.equals(key)||C17HighlightRemoval.BACKGROUND_ENABLED.equals(key)
                ||C17HighlightRemoval.LIGHT_BACKGROUND.equals(key)||C17HighlightRemoval.DARK_BACKGROUND.equals(key)
                ||StatusBarSettings.alphaKey(C17HighlightRemoval.LIGHT_BACKGROUND).equals(key)
                ||StatusBarSettings.alphaKey(C17HighlightRemoval.DARK_BACKGROUND).equals(key));
    }
    private static void upgrade57() {
        Bundle current=saved(138.25f,false);current.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);
        current.putString(NotificationBigClockSettings.DATE_PATTERN,"yyyy年M月d日{周}");
        Map<String,Object> legacy=legacyWire(current,5700L);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added58);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added59);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added60);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added61);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added62);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        legacy.keySet().removeIf(Upgrade160.DEFAULTS::containsKey);
        legacy.keySet().removeIf(Upgrade166.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade168.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade169.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade171.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade172.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade173.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade174.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade184.DEFAULTS::containsKey); legacy.keySet().removeIf(Upgrade185.DEFAULTS::containsKey);
        equal(384,legacy.size()-2);Map<String,Object> untouched=new LinkedHashMap<>(legacy);
        Bundle upgraded=SettingsFrameworkMirror.decode(legacy);
        equal(true,SettingsSnapshot.complete(upgraded));
        equal(untouched,legacy);equal(138.25f,upgraded.get(StatusBarSettings.DATA_OFFSET_X));
        equal("yyyy年M月d日{周}",upgraded.get(NotificationBigClockSettings.DATE_PATTERN));
        equal(false,upgraded.get(NotificationBigClockSettings.LANDSCAPE_MASTER));equal(false,upgraded.get(NotificationGroupStack.MASTER));
        equal(false,upgraded.get(C17HighlightRemoval.BACKGROUND_ENABLED));
        Bundle defaults=SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
        for(String key:defaults.keySet())if((added58(key)||added59(key)||added60(key)||added61(key)||added62(key)||added63(key))&&!NotificationGroupStack.MASTER.equals(key)
                &&!NetworkSpeedControls.INTERVAL_MILLIS.equals(key))equal(defaults.get(key),upgraded.get(key));
        equal(4000f,upgraded.get(NetworkSpeedControls.INTERVAL_MILLIS));
        for(String key:new String[]{StatusBarSettings.DATA_OFFSET_X,StatusBarSettings.SAFE_MODE,NotificationBigClockSettings.DATE_PATTERN,
                "wifi_color_light",StatusBarSettings.alphaKey("wifi_color_light")}) {
            Map<String,Object> missing=new LinkedHashMap<>(legacy);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing));
        }
        Map<String,Object> wrong=new LinkedHashMap<>(legacy);wrong.put(StatusBarSettings.DATA_OFFSET_X,138);equal(null,SettingsFrameworkMirror.decode(wrong));
        wrong=new LinkedHashMap<>(legacy);wrong.put(SettingsFrameworkMirror.SCHEMA,3);
        for(String key:Upgrade103.DEFAULTS.keySet())wrong.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())wrong.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())wrong.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())wrong.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())wrong.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())wrong.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())wrong.remove(key);equal(null,SettingsFrameworkMirror.decode(wrong));
        for(String key:defaults.keySet())if(added58(key)) {
            Map<String,Object> partial=new LinkedHashMap<>(legacy);partial.put(key,defaults.get(key));equal(null,SettingsFrameworkMirror.decode(partial));
            Map<String,Object> missing=new LinkedHashMap<>(wire(current,5800L));missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing));
        }
        legacy.put(NotificationBigClockSettings.STACK_ENABLED,false);legacy.put(NotificationBigClockSettings.INTERACTIVE_STACK,true);
        equal(false,SettingsFrameworkMirror.decode(legacy).get(NotificationGroupStack.MASTER));
        legacy.put(NotificationBigClockSettings.INTERACTIVE_STACK,false);equal(false,SettingsFrameworkMirror.decode(legacy).get(NotificationGroupStack.MASTER));
        for(String key:defaults.keySet())if(added59(key)) {
            Map<String,Object> partial=new LinkedHashMap<>(legacy);partial.put(key,defaults.get(key));
            equal(null,SettingsFrameworkMirror.decode(partial));
        }
    }
    private static boolean added59(String key) {
        return NativeNetworkBadgeControls.MASTER.equals(key)||NativeNetworkBadgeControls.X.equals(key)
                ||NativeNetworkBadgeControls.Y.equals(key)||NativeNetworkBadgeControls.SCALE.equals(key)
                ||NativeNetworkBadgeControls.WEIGHT.equals(key);
    }
    private static void upgrade58() {
        Bundle current=saved(-12.25f,false);
        Bundle defaults=SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
        Map<String,Object> legacy=legacyWire(current,5800L);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added59);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added60);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added61);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added62);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        Map<String,Object> untouched=new LinkedHashMap<>(legacy);
        Bundle upgraded=SettingsFrameworkMirror.decode(legacy);
        equal(true,SettingsSnapshot.complete(upgraded));equal(untouched,legacy);
        for(String key:current.keySet())if(!added59(key)&&!added60(key)&&!added61(key)&&!added62(key)&&!added63(key))equal(legacyExpected(current,key),upgraded.get(key));
        equal(4000f,upgraded.get(NetworkSpeedControls.INTERVAL_MILLIS));
        for(String key:current.keySet())if(added60(key)&&!NetworkSpeedControls.INTERVAL_MILLIS.equals(key))equal(defaults.get(key),upgraded.get(key));
        equal(false,upgraded.get(NativeNetworkBadgeControls.MASTER));
        for(String key:current.keySet())if(added59(key)) {
            equal(defaults.get(key),upgraded.get(key));
            Map<String,Object> partial=new LinkedHashMap<>(legacy);partial.put(key,current.get(key));
            equal(null,SettingsFrameworkMirror.decode(partial));
            Map<String,Object> missing=new LinkedHashMap<>(wire(current,5900L));missing.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missing));
        }
        for(String key:new String[]{StatusBarSettings.DATA_OFFSET_X,NotificationGroupStack.MASTER,"label_enabled"}) {
            Map<String,Object> missing=new LinkedHashMap<>(legacy);missing.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missing));
        }
    }
    private static boolean added60(String key) {
        return NativeNetworkBadgeControls.FONT.equals(key)||NetworkSpeedControls.INTERVAL_MILLIS.equals(key)
                ||NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED.equals(key)
                ||NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH.equals(key);
    }
    private static boolean added61(String key) {
        return C17HighlightRemoval.NOTIFICATION_ENABLED.equals(key)||C17HighlightRemoval.CONTROL_ENABLED.equals(key)
                ||C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED.equals(key)
                ||NotificationClearMotion.LANDSCAPE_MASTER.equals(key)
                ||NotificationClearMotion.LANDSCAPE_OFFSET_X.equals(key)||NotificationClearMotion.LANDSCAPE_OFFSET_Y.equals(key)
                ||LockscreenControls.DATE_ENABLED.equals(key)||LockscreenControls.DATE_FORMAT.equals(key)
                ||LockscreenControls.HIDE_LOCK.equals(key);
    }
    private static boolean added62(String key){return NotificationClearAppearance.LANDSCAPE_LEGACY.containsKey(key);}
    private static boolean added63(String key){
        return FeatureOptions.STACK_LANDSCAPE_ENABLED.equals(key)||key.startsWith("native_data_activity_");
    }
    private static void upgrade62(){
        Bundle current=saved(83.125f,false);
        current.putBoolean(NotificationGroupStack.MASTER,true);
        current.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);
        current.putFloat(NotificationClearAppearance.LANDSCAPE_OPACITY,72.625f);
        current.putInt(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,0x70112233);
        current.putBoolean(StatusBarSettings.alphaKey(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT),true);
        Map<String,Object> old=legacyWire(current,6203L);old.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        Map<String,Object> untouched=new LinkedHashMap<>(old);
        Bundle loaded=SettingsFrameworkMirror.decode(old);equal(true,SettingsSnapshot.complete(loaded));equal(untouched,old);
        equal(false,loaded.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));equal(false,loaded.get(NativeDataActivity.MASTER));
        equal(false,loaded.get(NotificationGroupStack.MASTER));equal(true,loaded.get(NotificationBigClockSettings.STACK_ENABLED));
        for(String key:current.keySet())if(!added63(key)&&!NotificationGroupStack.MASTER.equals(key))equal(legacyExpected(current,key),loaded.get(key));
        String[] keys=current.keySet().stream().filter(SettingsFrameworkMirrorCheck::added63).toArray(String[]::new);equal(10,keys.length);
        for(int mask=1;mask<(1<<keys.length)-1;mask++){
            Map<String,Object> mixed=new LinkedHashMap<>(old);
            for(int i=0;i<keys.length;i++)if((mask&(1<<i))!=0)mixed.put(keys[i],current.get(keys[i]));
            equal(null,SettingsFrameworkMirror.decode(mixed));
        }
        for(String key:keys){
            Map<String,Object> wrong=wire(current,6300L);wrong.put(key,new Object());equal(null,SettingsFrameworkMirror.decode(wrong));
            wrong.put(key,null);equal(null,SettingsFrameworkMirror.decode(wrong));
            Map<String,Object> missing=wire(current,6300L);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing));
        }
        Map<String,Object> partialClear=new LinkedHashMap<>(old);partialClear.remove(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT);
        equal(null,SettingsFrameworkMirror.decode(partialClear));
        Map<String,Object> mixedNew=wire(current,6300L);mixedNew.keySet().removeIf(SettingsFrameworkMirrorCheck::added62);
        equal(null,SettingsFrameworkMirror.decode(mixedNew));
        current.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);current.putBoolean(NativeDataActivity.MASTER,true);
        current.putFloat(NativeDataActivity.X,-3.125f);current.putFloat(NativeDataActivity.Y,2.25f);current.putFloat(NativeDataActivity.SCALE,131.25f);
        current.putBoolean(NativeDataActivity.COLOR_ENABLED,true);current.putInt(NativeDataActivity.COLOR_DARK,0x80112233);
        current.putBoolean(StatusBarSettings.alphaKey(NativeDataActivity.COLOR_DARK),true);
        Bundle modern=SettingsFrameworkMirror.decode(wire(current,6301L));
        equal(false,modern.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));equal(false,modern.get(NotificationGroupStack.MASTER));
        for(String key:current.keySet())if(added63(key))equal(current.get(key),modern.get(key));
        Store persisted=new Store();equal(true,SettingsFrameworkMirror.write(persisted.preferences,current,6301L));
        equal(false,persisted.disk.get(NotificationGroupStack.MASTER));equal(false,persisted.disk.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        Context context=new Context(){private final android.content.ContentResolver resolver=new android.content.ContentResolver();
            @Override public android.content.ContentResolver getContentResolver(){return resolver;}};
        SettingsSnapshot.rememberApplied(context,current);
        equal(false,SettingsSnapshot.lastApplied(context).get(NotificationGroupStack.MASTER));
        equal(false,SettingsSnapshot.lastApplied(context).get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        equal(true,current.get(NotificationGroupStack.MASTER));equal(true,current.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        SettingsSnapshot.forgetApplied();
    }
    private static void upgrade61(){
        Bundle current=saved(17.125f,false);
        current.putBoolean(NotificationClearAppearance.MASTER,true);
        current.putBoolean(NotificationClearAppearance.GRADIENT_ENABLED,true);
        current.putFloat(NotificationClearAppearance.OPACITY,37.125f);
        current.putFloat(NotificationClearAppearance.GRADIENT_ANGLE,-72.5f);
        int index=0;
        for(String key:new String[]{NotificationClearAppearance.COLOR_LIGHT,NotificationClearAppearance.COLOR_DARK,
                NotificationClearAppearance.GRADIENT_COLOR_LIGHT,NotificationClearAppearance.GRADIENT_COLOR_DARK}){
            current.putInt(key,0x40112233+index*0x10202020);current.putBoolean(StatusBarSettings.alphaKey(key),(index++&1)==0);
        }
        Map<String,Object> legacy=legacyWire(current,6103L);legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added62);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        Map<String,Object> untouched=new LinkedHashMap<>(legacy);
        Bundle upgraded=SettingsFrameworkMirror.decode(legacy);
        equal(true,SettingsSnapshot.complete(upgraded));equal(untouched,legacy);
        for(String key:current.keySet())if(!added62(key)&&!added63(key))equal(legacyExpected(current,key),upgraded.get(key));
        for(Map.Entry<String,String> item:NotificationClearAppearance.LANDSCAPE_LEGACY.entrySet())
            equal(current.get(item.getValue()),upgraded.get(item.getKey()));
        // Independent manifest assertions catch swapped light/dark or endpoint mappings.
        equal(true,upgraded.get(NotificationClearAppearance.LANDSCAPE_MASTER));
        equal(true,upgraded.get(NotificationClearAppearance.LANDSCAPE_GRADIENT_ENABLED));
        equal(37.125f,upgraded.get(NotificationClearAppearance.LANDSCAPE_OPACITY));
        equal(-72.5f,upgraded.get(NotificationClearAppearance.LANDSCAPE_GRADIENT_ANGLE));
        String[] newColors={NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_COLOR_DARK,
                NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_LIGHT,NotificationClearAppearance.LANDSCAPE_GRADIENT_COLOR_DARK};
        for(int i=0;i<newColors.length;i++){
            equal(0x40112233+i*0x10202020,upgraded.get(newColors[i]));
            equal((i&1)==0,upgraded.get(StatusBarSettings.alphaKey(newColors[i])));
        }
        // The code62 batch; the landscape transition range came with schema 19 and is checked with that batch.
        String[] keys=NotificationClearAppearance.LANDSCAPE_LEGACY.keySet().stream()
                .filter(key->!Upgrade100.DEFAULTS.containsKey(key)).toArray(String[]::new);equal(12,keys.length);
        int subsets=0;
        for(int mask=1;mask<(1<<keys.length)-1;mask++){
            Map<String,Object> partial=new LinkedHashMap<>(legacy);
            for(int i=0;i<keys.length;i++)if((mask&(1<<i))!=0)partial.put(keys[i],current.get(keys[i]));
            equal(null,SettingsFrameworkMirror.decode(partial));subsets++;
        }
        equal(4094,subsets);
        for(String key:keys){
            Map<String,Object> incomplete=new LinkedHashMap<>(wire(current,6200L));incomplete.remove(key);equal(null,SettingsFrameworkMirror.decode(incomplete));
            Map<String,Object> wrong=new LinkedHashMap<>(wire(current,6200L));wrong.put(key,new Object());equal(null,SettingsFrameworkMirror.decode(wrong));
            wrong.put(key,null);equal(null,SettingsFrameworkMirror.decode(wrong));
        }
        Map<String,Object> modern=wire(current,6201L);
        modern.put(NotificationClearAppearance.LANDSCAPE_MASTER,false);
        modern.put(NotificationClearAppearance.LANDSCAPE_OPACITY,83.5f);
        modern.put(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT,0x98123456);
        modern.put(StatusBarSettings.alphaKey(NotificationClearAppearance.LANDSCAPE_COLOR_LIGHT),true);
        Bundle result=SettingsFrameworkMirror.decode(modern);
        for(String key:current.keySet())equal(modern.get(key),result.get(key));
    }
    private static void upgrade60() {
        Bundle current=saved(-8.125f,true);
        current.putBoolean(C17HighlightRemoval.ENABLED,true);
        current.putBoolean(NotificationClearMotion.MOTION_ENABLED,true);
        current.putFloat(NotificationClearMotion.OFFSET_Y,38.25f);
        current.putBoolean(NetworkSpeedControls.STYLE_ENABLED,true);
        current.putString(NetworkSpeedControls.DISPLAY_STYLE,"inline");
        Map<String,Object> legacy=legacyWire(current,6100L);legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added61);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added62);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        Map<String,Object> untouched=new LinkedHashMap<>(legacy);
        Bundle upgraded=SettingsFrameworkMirror.decode(legacy);
        equal(true,SettingsSnapshot.complete(upgraded));equal(untouched,legacy);
        for(String key:current.keySet())if(!added61(key)&&!added62(key)&&!added63(key))equal(legacyExpected(current,key),upgraded.get(key));
        equal(true,upgraded.get(C17HighlightRemoval.NOTIFICATION_ENABLED));equal(true,upgraded.get(C17HighlightRemoval.CONTROL_ENABLED));
        equal(false,upgraded.get(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED));equal(false,upgraded.get(NotificationClearMotion.LANDSCAPE_MASTER));
        equal(false,upgraded.get(LockscreenControls.DATE_ENABLED));equal(false,upgraded.get(LockscreenControls.HIDE_LOCK));
        equal("M月d日 {周}",upgraded.get(LockscreenControls.DATE_FORMAT));
        equal(true,upgraded.get(NetworkSpeedControls.STYLE_ENABLED));equal("inline",upgraded.get(NetworkSpeedControls.DISPLAY_STYLE));
        String[] keys={C17HighlightRemoval.NOTIFICATION_ENABLED,C17HighlightRemoval.CONTROL_ENABLED,C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED,
                NotificationClearMotion.LANDSCAPE_MASTER,NotificationClearMotion.LANDSCAPE_OFFSET_X,NotificationClearMotion.LANDSCAPE_OFFSET_Y,
                LockscreenControls.DATE_ENABLED,LockscreenControls.DATE_FORMAT,LockscreenControls.HIDE_LOCK};
        equal(9,keys.length);
        int subsets=0;
        for(int mask=1;mask<(1<<keys.length)-1;mask++) {
            Map<String,Object> mixed=new LinkedHashMap<>(legacy);
            for(int i=0;i<keys.length;i++)if((mask&(1<<i))!=0)mixed.put(keys[i],current.get(keys[i]));
            equal(null,SettingsFrameworkMirror.decode(mixed));subsets++;
        }
        equal(510,subsets);
        for(String key:keys) {
            Map<String,Object> missing=new LinkedHashMap<>(wire(current,6101L));missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing));
            Map<String,Object> wrong=new LinkedHashMap<>(wire(current,6101L));wrong.put(key,new Object());equal(null,SettingsFrameworkMirror.decode(wrong));
            wrong.put(key,null);equal(null,SettingsFrameworkMirror.decode(wrong));
        }
        current.putBoolean(C17HighlightRemoval.NOTIFICATION_ENABLED,false);current.putBoolean(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED,true);
        current.putBoolean(NotificationClearMotion.LANDSCAPE_MASTER,true);current.putFloat(NotificationClearMotion.LANDSCAPE_OFFSET_X,-14.25f);
        current.putFloat(NotificationClearMotion.LANDSCAPE_OFFSET_Y,2.5f);
        current.putBoolean(LockscreenControls.DATE_ENABLED,true);current.putBoolean(LockscreenControls.HIDE_LOCK,true);
        current.putString(LockscreenControls.DATE_FORMAT,"yyyy年M月d日 {星期}");
        Bundle modern=SettingsFrameworkMirror.decode(wire(current,6102L));
        for(String key:current.keySet())equal(current.get(key),modern.get(key));
    }
    private static void upgrade59() {
        Bundle current=saved(27.125f,true);
        current.putFloat(NetworkSpeedControls.INTERVAL_SECONDS,.00125f);
        current.putBoolean(NetworkSpeedControls.INTERVAL_ENABLED,true);
        Map<String,Object> legacy=legacyWire(current,5900L);legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added60);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added61);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added62);
        legacy.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        Map<String,Object> untouched=new LinkedHashMap<>(legacy);
        Bundle upgraded=SettingsFrameworkMirror.decode(legacy);
        equal(true,SettingsSnapshot.complete(upgraded));equal(untouched,legacy);
        for(String key:current.keySet())if(!added60(key)&&!added61(key)&&!added62(key)&&!added63(key))equal(legacyExpected(current,key),upgraded.get(key));
        equal("native",upgraded.get(NativeNetworkBadgeControls.FONT));
        equal(1.25f,upgraded.get(NetworkSpeedControls.INTERVAL_MILLIS));
        equal(false,upgraded.get(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED));
        equal(100f,upgraded.get(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH));
        String[] keys={NativeNetworkBadgeControls.FONT,NetworkSpeedControls.INTERVAL_MILLIS,
                NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH};
        // Every mixed subset is incomplete, even if its values happen to match defaults.
        for(int mask=1;mask<15;mask++) {
            Map<String,Object> partial=new LinkedHashMap<>(legacy);
            for(int i=0;i<keys.length;i++)if((mask&(1<<i))!=0)partial.put(keys[i],current.get(keys[i]));
            equal(null,SettingsFrameworkMirror.decode(partial));
        }
        for(String key:keys) {
            Map<String,Object> missing=new LinkedHashMap<>(wire(current,6000L));missing.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missing));
            Map<String,Object> wrong=new LinkedHashMap<>(wire(current,6000L));wrong.put(key,new Object());
            equal(null,SettingsFrameworkMirror.decode(wrong));
            wrong.put(key,null);equal(null,SettingsFrameworkMirror.decode(wrong));
        }
        for(float seconds:new float[]{2.75f,.000125f,1000000f,Float.MAX_VALUE,-2.75f,0f}) {
            Map<String,Object> values=new LinkedHashMap<>(legacy);values.put(NetworkSpeedControls.INTERVAL_SECONDS,seconds);
            Bundle copy=SettingsFrameworkMirror.decode(values);
            equal(seconds,copy.get(NetworkSpeedControls.INTERVAL_SECONDS));
            equal(SettingsFrameworkMirror.migratedSpeedMillis(seconds),copy.get(NetworkSpeedControls.INTERVAL_MILLIS));
            equal(true,Float.isFinite((Float)copy.get(NetworkSpeedControls.INTERVAL_MILLIS)));
        }
        for(Object invalid:new Object[]{null,2,Float.NaN,Float.POSITIVE_INFINITY}) {
            Map<String,Object> values=new LinkedHashMap<>(legacy);values.put(NetworkSpeedControls.INTERVAL_SECONDS,invalid);
            equal(null,SettingsFrameworkMirror.decode(values));
        }
        current.putString(NativeNetworkBadgeControls.FONT,"custom");
        current.putFloat(NetworkSpeedControls.INTERVAL_MILLIS,1234.567f);
        current.putBoolean(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,true);
        current.putFloat(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH,137.5f);
        Bundle modern=SettingsFrameworkMirror.decode(wire(current,6001L));
        for(String key:current.keySet())equal(current.get(key),modern.get(key));
        // Old boot copies still load with no application/provider launch and no user edits.
        Store daemon=new Store();daemon.replace(legacy);int[] providerReads={0};
        SettingsFrameworkReader reader=new SettingsFrameworkReader(()->daemon.preferences,()->{},()->false);
        Bundle boot=reader.read(false,()->{providerReads[0]++;return null;});
        equal(1.25f,boot.get(NetworkSpeedControls.INTERVAL_MILLIS));equal(0,providerReads[0]);
        equal(true,boot.get(StatusBarSettings.SAFE_MODE));equal(27.125f,boot.get(StatusBarSettings.DATA_OFFSET_X));reader.stop();
    }
    private static void upgrade64() {
        Bundle current=saved(28.125f,true);
        current.putString("speed_position","clock_right");
        Map<String,Object> old=legacyWire(current,6400L),untouched=new LinkedHashMap<>(old);
        Bundle loaded=SettingsFrameworkMirror.decode(old);
        equal(true,SettingsSnapshot.complete(loaded));equal(untouched,old);
        equal("native",loaded.get("speed_position"));
        for(String key:current.keySet())if(!Upgrade65.DEFAULTS.containsKey(key))equal(legacyExpected(current,key),loaded.get(key));
        for(Map.Entry<String,Object> addition:Upgrade65.DEFAULTS.entrySet())equal(addition.getValue(),loaded.get(addition.getKey()));
        for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key)){
            Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missing));
        }
        // Missing an entire older batch while newer fields remain is corruption,
        // not a historical version eligible for default migration.
        for(java.util.function.Predicate<String> batch:java.util.Arrays.<java.util.function.Predicate<String>>asList(
                SettingsFrameworkMirrorCheck::added58,SettingsFrameworkMirrorCheck::added59,
                SettingsFrameworkMirrorCheck::added60,SettingsFrameworkMirrorCheck::added61,
                SettingsFrameworkMirrorCheck::added62)){
            Map<String,Object> missing=new LinkedHashMap<>(old);missing.keySet().removeIf(batch);
            equal(null,SettingsFrameworkMirror.decode(missing));
        }
        Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put("speed_position","clock_right");
        equal(null,SettingsFrameworkMirror.decode(mixed));
        for(String key:Upgrade65.DEFAULTS.keySet()){
            mixed=new LinkedHashMap<>(old);mixed.put(key,current.get(key));
            equal(null,SettingsFrameworkMirror.decode(mixed));
            Map<String,Object> partial=wire(current,6500L);partial.remove(key);
            equal(null,SettingsFrameworkMirror.decode(partial));
        }
        Map<String,Object> modern=wire(current,6500L);
        equal("clock_right",SettingsFrameworkMirror.decode(modern).get("speed_position"));
        for(Object invalid:new Object[]{null,17,true}){
            Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put("speed_position",invalid);
            equal(null,SettingsFrameworkMirror.decode(wrong));
        }
        modern.remove("speed_position");equal(null,SettingsFrameworkMirror.decode(modern));
        modern=wire(current,6500L);modern.keySet().removeIf(SettingsFrameworkMirrorCheck::added63);
        equal(null,SettingsFrameworkMirror.decode(modern));
        Store persisted=new Store();equal(true,SettingsFrameworkMirror.write(persisted.preferences,current,6500L));
        equal(SettingsFrameworkMirror.VERSION,persisted.disk.get(SettingsFrameworkMirror.SCHEMA));
        equal("clock_right",persisted.disk.get("speed_position"));
        Store daemon=new Store();daemon.replace(old);int[] providerReads={0};
        SettingsFrameworkReader reader=new SettingsFrameworkReader(()->daemon.preferences,()->{},()->false);
        equal("native",reader.read(false,()->{providerReads[0]++;return null;}).get("speed_position"));
        equal(0,providerReads[0]);reader.stop();
    }
    private static void nativeColorMarker() throws Exception {
        String marker=FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED;
        ActivationGuardPreferencesCheck.MemoryPreferences raw=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(false,StatusBarSettings.bool(raw.values,marker));
        SharedPreferences.Editor editor=raw.edit().putInt(FluidCloudStyle.BACKGROUND_COLOR,0xff303438);
        StatusBarSettings.markColorCustomized(editor,FluidCloudStyle.BACKGROUND_COLOR);editor.apply();
        equal(true,raw.values.get(marker)); // Saving the unchanged default color still expresses intent.
        Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(raw.values),true).values();
        equal(true,portable.get(marker));equal(0xff303438,portable.get(FluidCloudStyle.BACKGROUND_COLOR));
        Bundle snapshot=SettingsSnapshot.fromPreferences(raw.values);
        Map<String,Object> published=wire(snapshot,8800L),untouched=new LinkedHashMap<>(published);
        equal(true,SettingsFrameworkMirror.decode(published).get(marker));equal(untouched,published);
        published.remove(marker);untouched=new LinkedHashMap<>(published);
        equal(false,SettingsFrameworkMirror.decode(published).get(marker));equal(untouched,published);
        for(Object bad:new Object[]{null,0,1f,"true"}) {
            Map<String,Object> malformed=new LinkedHashMap<>(published);malformed.put(marker,bad);
            equal(null,SettingsFrameworkMirror.decode(malformed));
            equal(null,SettingsSnapshot.fromPreferences(malformed));
        }
        published.remove(StatusBarSettings.CLOCK_SCALE);
        equal(null,SettingsFrameworkMirror.decode(published)); // The editing marker is the sole new optional field.
        editor=raw.edit().remove(FluidCloudStyle.BACKGROUND_COLOR).remove(StatusBarSettings.alphaKey(FluidCloudStyle.BACKGROUND_COLOR));
        StatusBarSettings.clearColorCustomization(editor,FluidCloudStyle.BACKGROUND_COLOR);editor.apply();
        equal(false,raw.values.containsKey(marker));equal(false,StatusBarSettings.bool(raw.values,marker));
        editor=raw.edit().putInt("clock_color_light",0xff303438);
        StatusBarSettings.markColorCustomized(editor,"clock_color_light");editor.apply();
        equal(false,raw.values.containsKey(marker));equal(0xff303438,raw.values.get("clock_color_light"));
    }
    public static void main(String[] args) throws Exception {
        nativeColorMarker();
        upgrade57();
        upgrade58();
        upgrade59();
        upgrade60();
        upgrade61();
        upgrade62();
        upgrade64();
        Bundle first=saved(-8.24f,true),second=saved(3.25f,false);
        equal(true,SettingsSnapshot.complete(first));equal(null,SettingsFrameworkMirror.decode(new HashMap<>()));
        Map<String,Object> map=wire(first,1L);Bundle loaded=SettingsFrameworkMirror.decode(map);
        equal(-8.24f,loaded.get(StatusBarSettings.DATA_OFFSET_X));equal(true,loaded.get(StatusBarSettings.SAFE_MODE));
        equal(false,FeatureOptions.from(SafetyMode.runtimeSettings(loaded)).enabled("data"));
        equal(true,loaded.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        for(String key:loaded.keySet())equal(first.get(key),loaded.get(key));
        map.remove(StatusBarSettings.DATA_OFFSET_X);equal(null,SettingsFrameworkMirror.decode(map));
        map=wire(first,1L);map.put(StatusBarSettings.DATA_OFFSET_X,9);equal(null,SettingsFrameworkMirror.decode(map));
        map=wire(first,1L);map.put(StatusBarSettings.DATA_OFFSET_X,Float.NaN);equal(null,SettingsFrameworkMirror.decode(map));
        map=wire(first,1L);map.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(map));
        map=wire(first,0L);equal(null,SettingsFrameworkMirror.decode(map));
        map=wire(first,1L);map.put(SettingsFrameworkMirror.REVISION,1);equal(null,SettingsFrameworkMirror.decode(map));

        Store transport=new Store();equal(false,SettingsFrameworkMirror.write(transport.preferences,new Bundle(),1L));equal(0,transport.commits);
        equal(true,SettingsFrameworkMirror.write(transport.preferences,first,1L));
        transport.fail=1;equal(false,SettingsFrameworkMirror.write(transport.preferences,second,2L));
        equal(3.25f,transport.memory.values.get(StatusBarSettings.DATA_OFFSET_X));
        equal(-8.24f,transport.disk.get(StatusBarSettings.DATA_OFFSET_X));

        // A valid daemon copy is usable without any app/provider context or process.
        Store daemon=new Store();daemon.replace(wire(first,10L));int[] gets={0},events={0};boolean[] removed={false};
        SettingsFrameworkReader reader=new SettingsFrameworkReader(()->{gets[0]++;return daemon.preferences;},()->events[0]++,()->removed[0]);
        equal(-8.24f,reader.read().get(StatusBarSettings.DATA_OFFSET_X));equal(true,reader.observed());equal(1,gets[0]);
        equal(1,daemon.listeners.size());
        int[] providerReads={0};
        equal(-8.24f,reader.read(false,()->{providerReads[0]++;return second;}).get(StatusBarSettings.DATA_OFFSET_X));equal(0,providerReads[0]);
        equal(3.25f,reader.read(true,()->{providerReads[0]++;return second;}).get(StatusBarSettings.DATA_OFFSET_X));equal(1,providerReads[0]);
        equal(-8.24f,reader.read(true,()->{throw new IllegalStateException("synthetic OEM veto");}).get(StatusBarSettings.DATA_OFFSET_X));
        equal(-8.24f,reader.read(true,Bundle::new).get(StatusBarSettings.DATA_OFFSET_X));
        daemon.notifyKey(StatusBarSettings.DATA_OFFSET_X);equal(0,events[0]);
        daemon.replace(wire(second,11L));daemon.notifyKey(SettingsFrameworkMirror.REVISION);equal(1,events[0]);
        equal(3.25f,reader.read().get(StatusBarSettings.DATA_OFFSET_X));
        for(int frame=0;frame<1000;frame++)equal(true,reader.observed());equal(1,gets[0]);
        reader.stop();equal(false,reader.observed());equal(0,daemon.listeners.size());
        daemon.notifyKey(SettingsFrameworkMirror.REVISION);equal(1,events[0]);equal(null,reader.read());
        Store late=new Store();late.replace(wire(first,1L));SettingsFrameworkReader[] pending={null};
        pending[0]=new SettingsFrameworkReader(()->late.preferences,()->events[0]++,()->false);
        late.duringRegister=()->pending[0].stop();equal(null,pending[0].read());equal(0,late.listeners.size());
        Store retry=new Store();retry.replace(wire(first,1L));retry.failListener=1;
        SettingsFrameworkReader retried=new SettingsFrameworkReader(()->retry.preferences,()->events[0]++,()->false);
        equal(null,retried.read());equal(false,retried.observed());
        equal(-8.24f,retried.read().get(StatusBarSettings.DATA_OFFSET_X));equal(true,retried.observed());retried.stop();
        SettingsFrameworkReader absent=new SettingsFrameworkReader(()->{throw new UnsupportedOperationException("no remote capability");},()->{},()->false);
        equal(null,absent.read());equal(false,absent.observed());absent.stop();

        // Same-service reconnection uses the SDK's same cached, failed-memory preferences.
        Store remote=new Store();XposedService service=new XposedService(remote.preferences);
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("c17-shared-boot-");
        root.toFile().deleteOnExit();
        Context context=new Context(root.toFile());
        CustomPluginRepository repository=new CustomPluginRepository(context);
        java.nio.file.Path library=java.nio.file.Files.createDirectory(root.resolve("custom_plugins"));
        library.toFile().deleteOnExit();
        java.nio.file.Path database=library.resolve("library.json");database.toFile().deleteOnExit();
        byte[] emptyLibrary="{\"format\":1,\"revision\":0,\"plugins\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Store newRemote=new Store(),olderRemote=new Store();
        olderRemote.replace(wire(first,17L));
        XposedService newService=new XposedService(newRemote.preferences),olderService=new XposedService(olderRemote.preferences);
        ActivationGuardPreferencesCheck.MemoryPreferences raw=new ActivationGuardPreferencesCheck.MemoryPreferences();
        ActivationGuardPreferencesCheck.MemoryPreferences credential=new ActivationGuardPreferencesCheck.MemoryPreferences();
        ActivationGuardPreferencesCheck.MemoryPreferences backup=new ActivationGuardPreferencesCheck.MemoryPreferences();
        Map<String,SharedPreferences> other=new HashMap<>();
        android.os.UserManager users=new android.os.UserManager();
        Context fresh=new Context(root.toFile()){
            @Override public Object getSystemService(String name){return users;}
            @Override public Context createPackageContext(String name,int flags){return new Context(){
                @Override public boolean isDeviceProtectedStorage(){return false;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode){return credential;}
            };}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                if(StatusBarSettings.PREFS.equals(name))return raw;
                if("statusbar_settings_last_good".equals(name))return backup;
                return other.computeIfAbsent(name,ignored->new ActivationGuardPreferencesCheck.MemoryPreferences());
            }
        };
        users.unlocked=false;equal(false,SettingsSnapshot.freshForFramework(fresh,raw));
        users.unlocked=true;credential.edit().putBoolean(StatusBarSettings.SAFE_MODE,true).commit();
        equal(false,SettingsSnapshot.freshForFramework(fresh,raw));
        credential.edit().clear().commit();backup.edit().putBoolean(StatusBarSettings.SAFE_MODE,true).commit();
        equal(false,SettingsSnapshot.freshForFramework(fresh,raw));
        backup.edit().clear().commit();equal(true,SettingsSnapshot.freshForFramework(fresh,raw));
        SettingsFrameworkMirror.offerDefaults(fresh,raw);
        SettingsFrameworkMirror.bound(newService);SettingsFrameworkMirror.bound(olderService);settle();
        equal(0,newRemote.commits);equal(0,olderRemote.commits);
        java.nio.file.Files.write(database,emptyLibrary);repository.setAllDisabled();settle();
        equal(1,newRemote.commits);equal(0,olderRemote.commits);
        equal(true,raw.getAll().isEmpty());
        equal(false,newRemote.disk.get(StatusBarSettings.SAFE_MODE));
        equal(1L,new org.json.JSONObject((String)newRemote.disk.get(CustomPluginFrameworkPublisher.KEY)).getLong("revision"));
        SettingsFrameworkMirror.unbound(newService);SettingsFrameworkMirror.unbound(olderService);
        java.nio.file.Files.write(database,emptyLibrary);
        Map<String,Object> initial=wire(first,20L);
        initial.put(CustomPluginFrameworkPublisher.KEY,CustomPluginFrameworkPublisher.stage(service,
                repository.frameworkPlans(true),java.util.Collections.emptySet()).metadata);
        remote.replace(initial);
        SettingsFrameworkMirror.confirmed(context,first);SettingsFrameworkMirror.bound(service);settle();
        equal(true,SettingsFrameworkMirror.status().get("ready"));equal(0,remote.commits);
        equal(true,SettingsFrameworkMirror.status().get("plugin_boot_ready"));
        equal(1,SettingsFrameworkMirror.status().get("plugin_boot_scopes"));
        remote.fail=4;SettingsFrameworkMirror.confirmed(context,second);settle();
        equal(false,SettingsFrameworkMirror.status().get("ready"));equal(-8.24f,remote.disk.get(StatusBarSettings.DATA_OFFSET_X));
        int before=remote.commits;
        SettingsFrameworkMirror.unbound(service);SettingsFrameworkMirror.bound(service);settle();
        equal(before+1,remote.commits);equal(false,SettingsFrameworkMirror.status().get("ready"));
        remote.fail=0;SettingsFrameworkMirror.retryConfirmed(context);settle();
        equal(true,SettingsFrameworkMirror.status().get("ready"));equal(3.25f,remote.disk.get(StatusBarSettings.DATA_OFFSET_X));
        equal(true,(Long)remote.disk.get(SettingsFrameworkMirror.REVISION)>20L);
        before=remote.commits;int reads=remote.reads;
        for(int callback=0;callback<1000;callback++)SettingsFrameworkMirror.confirmed(context,second);
        settle();equal(before,remote.commits);equal(reads,remote.reads);
        trial(true);SettingsFrameworkMirror.confirmed(context,saved(77f,false));settle();
        equal(3.25f,remote.disk.get(StatusBarSettings.DATA_OFFSET_X));equal(before,remote.commits);trial(false);
        // A plugin-only durability event advances the shared revision and has a matching safe-mode value.
        long previousRevision=(Long)remote.disk.get(SettingsFrameworkMirror.REVISION);
        repository.setAllDisabled();settle();
        equal(true,(Long)remote.disk.get(SettingsFrameworkMirror.REVISION)>previousRevision);
        org.json.JSONObject boot=new org.json.JSONObject((String)remote.disk.get(CustomPluginFrameworkPublisher.KEY));
        equal(1L,boot.getLong("revision"));equal(false,boot.getBoolean("safeMode"));
        org.json.JSONObject reference=boot.getJSONObject("plans").getJSONObject(ModuleRuntimeStatus.SYSTEM_UI);
        String file=reference.getString("file");
        try(java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(service.openRemoteFileForCheck(file))) {
            byte[] bytes=stream.readAllBytes();
            equal(bytes.length,reference.getInt("bytes"));
            org.json.JSONObject plan=new org.json.JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            equal(1L,plan.getLong("revision"));equal(false,plan.getBoolean("safeMode"));
            equal(ModuleRuntimeStatus.SYSTEM_UI,plan.getString("targetPackage"));
            equal(0,plan.getJSONArray("runtimes").length());
        }
        service.filesUnavailable=true;
        repository.setAllDisabled();settle();
        equal(CustomPluginFrameworkPublisher.WITHDRAWN,remote.disk.get(CustomPluginFrameworkPublisher.KEY));
        equal(true,SettingsFrameworkMirror.status().get("ready"));
        equal(false,SettingsFrameworkMirror.status().get("plugin_boot_ready"));
        service.filesUnavailable=false;
        SettingsFrameworkMirror.retryConfirmed(context);settle();
        boot=new org.json.JSONObject((String)remote.disk.get(CustomPluginFrameworkPublisher.KEY));
        equal(2L,boot.getLong("revision"));equal(true,SettingsFrameworkMirror.status().get("plugin_boot_ready"));
        before=remote.commits;
        SettingsFrameworkMirror.unbound(service);equal(false,SettingsFrameworkMirror.status().get("ready"));
        equal(0,SettingsFrameworkMirror.status().get("available_targets"));
        SettingsFrameworkMirror.retryConfirmed(context);settle();equal(before,remote.commits);
        System.out.println("SettingsFrameworkMirrorCheck passed "+checks+" checks");
    }
}
