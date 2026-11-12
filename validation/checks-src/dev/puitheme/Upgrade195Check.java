// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Opt-in card migration, frozen old wire formats, strict portable references and failed-write recovery. */
public final class Upgrade195Check {
    private static int checks;
    private static final String HASH="a".repeat(64);
    private static void equal(Object expected,Object actual,String why) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade195 "+checks+" "+why+": "+expected+" != "+actual);
    }
    private static Map<String,Object> additions() {
        Map<String,Object> expected=new LinkedHashMap<>();
        for(String key:Arrays.asList("qs_card_large_media_enabled","qs_card_six_rows_enabled",
                "qs_card_native_resize_enabled","qs_card_hide_idle_controls_enabled","qs_card_wallpaper_enabled"))expected.put(key,false);
        expected.put("qs_card_wallpaper_opacity",100f);expected.put("qs_card_wallpaper_revision","");return expected;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle snapshot,int schema)throws Exception {
        Method method=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);method.setAccessible(true);
        return (Map<String,Object>)method.invoke(null,snapshot,schema);
    }
    private static String document(Map<String,Object> values)throws IOException {
        Map<String,Object> root=new LinkedHashMap<>();root.put("package",ConfigTransfer.PACKAGE_NAME);
        root.put("schema",1);root.put("settings",values);return IconManagementJson.encode(root);
    }
    private static void rejects(String key,Object value)throws Exception {
        checks++;try{ConfigTransfer.prepare(document(Collections.singletonMap(key,value)),true);throw new AssertionError("Accepted invalid "+key+": "+value);}catch(IOException expected){ }
    }
    private static void registration() {
        Bundle fresh=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(additions(),Upgrade195.DEFAULTS,"seven frozen keys, types and defaults");
        equal(52,SettingsFrameworkMirror.VERSION,"new wire schema");equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON unchanged");
        equal(1000, fresh.keySet().size(),"full snapshot count");equal(987,ConfigTransfer.types().size(),"portable registry count");
        for(Map.Entry<String,Object> entry:additions().entrySet()) {
            String key=entry.getKey();Object value=entry.getValue();equal(value,fresh.get(key),"fresh default "+key);
            equal(value instanceof Boolean?ConfigTransfer.Type.BOOLEAN:value instanceof Float?ConfigTransfer.Type.NUMBER:ConfigTransfer.Type.STRING,
                    ConfigTransfer.types().get(key),"portable type "+key);
            for(Map<String,Object> previous:Arrays.asList(Upgrade184.DEFAULTS,Upgrade185.DEFAULTS,Upgrade186.DEFAULTS,Upgrade187.DEFAULTS,Upgrade188.DEFAULTS))
                equal(false,previous.containsKey(key),"new key is not inserted into historical batch");
        }
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade195.migrate(store);equal(0,store.writes,"empty reset stays empty");
        store.values.put("private_note","keep");store.values.put(QsCardSettings.LARGE_MEDIA,true);
        store.values.put(QsCardSettings.GRID_6,false);store.values.put(QsCardSettings.WALLPAPER_OPACITY,37.5f);
        store.values.put(QsCardSettings.WALLPAPER_REVISION,HASH);Map<String,Object> before=new LinkedHashMap<>(store.values);
        Upgrade195.migrate(store);equal(1,store.writes,"one durable write for all missing keys");
        for(Map.Entry<String,Object> entry:before.entrySet())equal(entry.getValue(),store.values.get(entry.getKey()),"existing choice preserved");
        for(String key:additions().keySet())equal(before.containsKey(key)?before.get(key):additions().get(key),store.values.get(key),"only missing defaults filled");
        Upgrade195.migrate(store);equal(1,store.writes,"idempotent migration");
        for(Object explicit:new Object[]{false,true,null,0,"invalid"}) {
            for(String key:QsCardSettings.BOOLEANS.keySet())store.values.put(key,explicit);
            before=new LinkedHashMap<>(store.values);Upgrade195.migrate(store);equal(before,store.values,"present values never silently rewritten");
            equal(1,store.writes,"no migration when all keys present");
        }
    }
    private static final class Storage extends Context {
        final Map<String,ActivationGuardPreferencesCheck.MemoryPreferences> stores=new LinkedHashMap<>();
        ActivationGuardPreferencesCheck.MemoryPreferences store(String name){return stores.computeIfAbsent(name,ignored->new ActivationGuardPreferencesCheck.MemoryPreferences());}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return store(name);}
        @Override public Object getSystemService(String name){return null;}
    }
    private static void applicationUpgrade() {
        Storage empty=new Storage();StatusBarSettings.preferences(empty);
        equal(Collections.emptyMap(),empty.store(StatusBarSettings.PREFS).values,"real application preferences entry leaves reset empty");
        Storage storage=new Storage();ActivationGuardPreferencesCheck.MemoryPreferences primary=storage.store(StatusBarSettings.PREFS);
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        for(String key:full.keySet())if(!Upgrade195.DEFAULTS.containsKey(key))primary.values.put(key,full.get(key));
        primary.values.put("private_note","keep");Map<String,Object> before=new LinkedHashMap<>(primary.values);
        StatusBarSettings.preferences(storage);
        for(Map.Entry<String,Object> entry:before.entrySet())equal(entry.getValue(),primary.values.get(entry.getKey()),"real application upgrade retains schema48 saved field");
        for(Map.Entry<String,Object> entry:additions().entrySet())equal(entry.getValue(),primary.values.get(entry.getKey()),"application entry invokes card migration");
        int writes=primary.writes;StatusBarSettings.preferences(storage);equal(writes,primary.writes,"opening migrated preferences does not repeatedly write");
    }
    private static void mirrors()throws Exception {
        Map<String,Object> saved=new LinkedHashMap<>();saved.putAll(additions());saved.put(QsCardSettings.LARGE_MEDIA,true);
        saved.put(QsCardSettings.WALLPAPER_OPACITY,37.5f);saved.put(QsCardSettings.WALLPAPER_REVISION,HASH);
        Bundle full=SettingsSnapshot.fromPreferences(saved);
        for(int schema=1;schema<49;schema++) {
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete historical schema upgrades "+schema);equal(before,old,"decode keeps historical input immutable");
            for(Map.Entry<String,Object> entry:additions().entrySet()) {
                String key=entry.getKey();equal(false,old.containsKey(key),"historical schema omits new key");equal(entry.getValue(),decoded.get(key),"old configuration remains opted out");
                for(Object forged:new Object[]{entry.getValue(),null,true,0f,""}) {
                    Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,forged);
                    equal(null,SettingsFrameworkMirror.decode(mixed),"historical schema refuses a future key even when null");
                }
            }
            if(schema==48)equal(981,old.size(),"schema48 freezes 979 settings plus two metadata fields");
        }
        Map<String,Object> current=wire(full,49);equal(988,current.size(),"schema49 full snapshot plus metadata");
        Bundle decoded=SettingsFrameworkMirror.decode(current);equal(true,SettingsSnapshot.complete(decoded),"current schema complete");
        for(String key:additions().keySet())equal(full.get(key),decoded.get(key),"current saved choice survives");
        for(String key:additions().keySet()) {
            Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing),"current schema requires each new key");
            Object wrong=additions().get(key) instanceof Boolean?"false":additions().get(key) instanceof Float?100:"invalid";
            if(wrong instanceof String&&additions().get(key) instanceof String)wrong=true;
            Map<String,Object> invalid=new LinkedHashMap<>(current);invalid.put(key,wrong);equal(null,SettingsFrameworkMirror.decode(invalid),"current schema requires exact wire type");
            invalid.put(key,null);equal(null,SettingsFrameworkMirror.decode(invalid),"current schema rejects null");
        }
        current.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(current),"future schema rejected");
        ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true,SettingsFrameworkMirror.write(remote,full,195L),"full snapshot publication succeeds");equal(52, remote.values.get(SettingsFrameworkMirror.SCHEMA),"publication advertises schema51");
    }
    private static void portable()throws Exception {
        Map<String,Object> saved=new LinkedHashMap<>();saved.putAll(additions());saved.put(QsCardSettings.WALLPAPER_ENABLED,true);
        saved.put(QsCardSettings.WALLPAPER_REVISION,HASH);saved.put(QsCardSettings.WALLPAPER_OPACITY,37.5f);saved.put("private_note","keep");
        Map<String,Object> before=new LinkedHashMap<>(saved);
        ConfigTransfer.PreparedImport all=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);equal(987,all.count,"full portable export");equal(before,saved,"export input immutable");
        ConfigTransfer.PreparedImport partial=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved,additions().keySet()),true);
        equal(8,partial.count,"old card export includes inferred row selector");for(String key:additions().keySet())equal(saved.get(key),partial.values().get(key),"portable value preserved");
        equal(true,partial.warning.contains("音乐卡片壁纸"),"referenced image is explicitly not transferred");
        equal("",partial.only(Collections.singleton(QsCardSettings.WALLPAPER_ENABLED)).warning,"unselected image does not leave a false warning");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.put("private_note","keep");
        equal(true,ConfigTransfer.commit(store,partial),"card settings import commits");equal("keep",store.values.get("private_note"),"unselected preference survives");
        for(String key:additions().keySet())equal(saved.get(key),store.values.get(key),"saved imported value");
        ConfigTransfer.PreparedImport empty=ConfigTransfer.prepare(document(Collections.singletonMap(QsCardSettings.WALLPAPER_REVISION,"")),true);
        equal("",empty.warning,"empty reference has no image warning");equal("",empty.values().get(QsCardSettings.WALLPAPER_REVISION),"empty clears reference");
        for(String value:new String[]{"a".repeat(63),"a".repeat(65),"g".repeat(64),"A".repeat(64)," "+HASH,HASH+" ","../"+HASH})rejects(QsCardSettings.WALLPAPER_REVISION,value);
        for(Object value:new Object[]{null,true,195,Collections.emptyMap()})rejects(QsCardSettings.WALLPAPER_REVISION,value);
        for(String key:QsCardSettings.BOOLEANS.keySet())for(Object value:new Object[]{null,"true",1,Collections.emptyMap()})rejects(key,value);
        for(Object value:new Object[]{null,"100",true,Collections.emptyList()})rejects(QsCardSettings.WALLPAPER_OPACITY,value);
        equal(false,ConfigTransfer.prepare(document(Collections.singletonMap("clock_enabled",true)),true).values().containsKey(QsCardSettings.LARGE_MEDIA),"legacy partial imports invent no card switch");
    }
    private static final class FailingStore {
        final ActivationGuardPreferencesCheck.MemoryPreferences memory=new ActivationGuardPreferencesCheck.MemoryPreferences();
        int fail=1;boolean thrown;
        final SharedPreferences raw=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(owner,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(memory,args);
            SharedPreferences.Editor editor=memory.edit();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(proxy,operation,parameters)->{
                if(!operation.getName().equals("commit")&&!operation.getName().equals("apply")){operation.invoke(editor,parameters);return proxy;}
                editor.commit();if(fail>0){fail--;if(thrown)throw new IllegalStateException("synthetic failed write after memory changed");return false;}
                return operation.getName().equals("commit")?true:null;
            });
        });
    }
    private static void failedWrites()throws Exception {
        for(boolean thrown:new boolean[]{false,true})for(int failures:new int[]{1,2}) {
            FailingStore store=new FailingStore();store.fail=failures;store.thrown=thrown;
            store.memory.values.put("private_note","keep");store.memory.values.put(QsCardSettings.LARGE_MEDIA,false);
            Map<String,Object> before=new LinkedHashMap<>(store.memory.values);Upgrade195.migrate(store.raw);
            equal(before,PreferenceWrites.values(store.raw),"failed migration exposes only pre-edit values");equal(false,PreferenceWrites.notificationAllowed(store.raw),"failed migration cannot publish a new snapshot");
            Upgrade195.migrate(store.raw);equal(true,PreferenceWrites.notificationAllowed(store.raw),"retry succeeds after failed disk/restore");
            for(String key:additions().keySet())equal(additions().get(key),PreferenceWrites.values(store.raw).get(key),"retry fills neutral defaults");
            equal("keep",PreferenceWrites.values(store.raw).get("private_note"),"failed/retry preserves unrelated source");
        }
    }
    private static void safeMode() {
        Map<String,Object> saved=new LinkedHashMap<>();saved.putAll(additions());for(String key:QsCardSettings.BOOLEANS.keySet())saved.put(key,true);
        saved.put(StatusBarSettings.SAFE_MODE,true);saved.put(QsCardSettings.WALLPAPER_REVISION,HASH);saved.put(QsCardSettings.WALLPAPER_OPACITY,37.5f);
        Bundle full=SettingsSnapshot.fromPreferences(saved),runtime=SafetyMode.runtimeSettings(full);
        for(String key:QsCardSettings.BOOLEANS.keySet()){equal(false,runtime.getBoolean(key),"safe mode turns off runtime effect");equal(true,full.getBoolean(key),"saved switch retained");}
        equal(HASH,runtime.getString(QsCardSettings.WALLPAPER_REVISION),"safe mode keeps image reference");equal(37.5f,runtime.get(QsCardSettings.WALLPAPER_OPACITY),"safe mode keeps opacity choice");
        full.putBoolean(StatusBarSettings.SAFE_MODE,false);runtime=SafetyMode.runtimeSettings(full);
        for(String key:QsCardSettings.BOOLEANS.keySet())equal(true,runtime.getBoolean(key),"leaving safe mode restores saved switches");
    }
    public static void main(String[] args)throws Exception {
        registration();migration();applicationUpgrade();mirrors();portable();failedWrites();safeMode();System.out.println("Upgrade195Check passed: "+checks);
    }
}
