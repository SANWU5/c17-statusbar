package dev.puitheme;

import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Rows migrate from the old switch, while current geometry choices and safe recovery remain exact. */
public final class Upgrade196Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String why) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade196 "+checks+" "+why+": "+expected+" != "+actual);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception {
        Method helper=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);helper.setAccessible(true);
        return (Map<String,Object>)helper.invoke(null,full,schema);
    }
    private static String document(Map<String,Object> values)throws IOException {
        Map<String,Object> root=new LinkedHashMap<>();root.put("package",ConfigTransfer.PACKAGE_NAME);root.put("schema",1);root.put("settings",values);
        return IconManagementJson.encode(root);
    }
    private static void rejects(String key,Object value)throws Exception {
        checks++;try {ConfigTransfer.prepare(document(Collections.singletonMap(key,value)),true);throw new AssertionError("Accepted "+key+": "+value);}catch(IOException expected){ }
    }
    private static void registrationAndMigration() {
        equal(52,SettingsFrameworkMirror.VERSION,"current wire schema");equal(7,Upgrade195.DEFAULTS.size(),"code195 remains exactly seven keys");
        equal(3,Upgrade196.DEFAULTS.size(),"only three new settings");
        Bundle fresh=SettingsSnapshot.fromPreferences(Collections.emptyMap());equal(1000, fresh.keySet().size(),"complete size");equal(987,ConfigTransfer.types().size(),"portable size");
        ActivationGuardPreferencesCheck.MemoryPreferences empty=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade196.migrate(empty);equal(0,empty.writes,"empty reset untouched");
        for(boolean six:new boolean[]{false,true}) {
            ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.put("private_note","keep");store.values.put(QsCardSettings.GRID_6,six);
            Upgrade195.migrate(store);equal(false,store.values.containsKey(QsCardSettings.GRID_ROWS),"older batch cannot swallow inherited rows");
            int writes=store.writes;Upgrade196.migrate(store);equal(writes+1,store.writes,"all missing new settings committed together");
            equal(six?6f:4f,store.values.get(QsCardSettings.GRID_ROWS),"old row choice inherited");equal(false,store.values.get(QsCardSettings.SMALL_TILES),"small tiles opt in");
            equal("always",store.values.get(QsCardSettings.SMALL_MODE),"default visibility");equal("keep",store.values.get("private_note"),"unrelated value kept");
            Upgrade196.migrate(store);equal(writes+1,store.writes,"idempotent");
            for(Object explicit:new Object[]{4f,5f,6f,7f,null,8f,"invalid"}) {
                store.values.put(QsCardSettings.GRID_ROWS,explicit);Map<String,Object> before=new LinkedHashMap<>(store.values);
                Upgrade196.migrate(store);equal(before,store.values,"existing row value never rewritten, even malformed");
            }
        }
    }
    private static void mirrors()throws Exception {
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(QsCardSettings.GRID_6,true);saved.put(QsCardSettings.GRID_ROWS,7f);saved.put(QsCardSettings.SMALL_TILES,true);saved.put(QsCardSettings.SMALL_MODE,"swipe");
        Bundle full=SettingsSnapshot.fromPreferences(saved);
        for(int schema=1;schema<=50;schema++) {
            Map<String,Object> encoded=wire(full,schema),before=new LinkedHashMap<>(encoded);Bundle restored=SettingsFrameworkMirror.decode(encoded);
            equal(true,SettingsSnapshot.complete(restored),"historical wire decodes "+schema);equal(before,encoded,"wire input immutable");
            equal(schema==50?7f:schema==49?6f:4f,restored.get(QsCardSettings.GRID_ROWS),"rows follow only original published fields");
            equal(schema==50,restored.getBoolean(QsCardSettings.SMALL_TILES),"older schemas never invent small tiles");
            equal(schema==50?"swipe":"always",restored.getString(QsCardSettings.SMALL_MODE),"old mode is neutral");
            if(schema<50)for(Map.Entry<String,Object> entry:Upgrade196.DEFAULTS.entrySet())for(Object forged:Arrays.asList(entry.getValue(),null,true,4f,"always")) {
                Map<String,Object> mixed=new LinkedHashMap<>(encoded);mixed.put(entry.getKey(),forged);equal(null,SettingsFrameworkMirror.decode(mixed),"old schema refuses each future field");
            }
            if(schema==49)equal(988,encoded.size(),"schema49 remains 986 plus metadata");
            if(schema==48)equal(981,encoded.size(),"schema48 remains 979 plus metadata");
        }
        for(String key:Upgrade196.DEFAULTS.keySet()) {
            Map<String,Object> current=wire(full,50);current.remove(key);equal(null,SettingsFrameworkMirror.decode(current),"current requires all new fields");
        }
        Map<String,Object> future=wire(full,50);future.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(future),"future schema rejected");
    }
    private static final class Storage extends Context {
        final Map<String,ActivationGuardPreferencesCheck.MemoryPreferences> stores=new LinkedHashMap<>();
        ActivationGuardPreferencesCheck.MemoryPreferences store(String name){return stores.computeIfAbsent(name,ignored->new ActivationGuardPreferencesCheck.MemoryPreferences());}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return store(name);}
        @Override public Object getSystemService(String name){return null;}
    }
    private static void realPreferencesEntry() {
        Storage empty=new Storage();StatusBarSettings.preferences(empty);equal(Collections.emptyMap(),empty.store(StatusBarSettings.PREFS).values,"empty real startup remains reset");
        for(boolean six:new boolean[]{false,true}) {
            Storage storage=new Storage();ActivationGuardPreferencesCheck.MemoryPreferences primary=storage.store(StatusBarSettings.PREFS);
            Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());
            for(String key:full.keySet())if(!Upgrade196.DEFAULTS.containsKey(key))primary.values.put(key,full.get(key));
            primary.values.put(QsCardSettings.GRID_6,six);primary.values.put("private_note","keep");Map<String,Object> before=new LinkedHashMap<>(primary.values);
            StatusBarSettings.preferences(storage);
            for(Map.Entry<String,Object> entry:before.entrySet())equal(entry.getValue(),primary.values.get(entry.getKey()),"real startup preserves previous schema49 value");
            equal(six?6f:4f,primary.values.get(QsCardSettings.GRID_ROWS),"real startup invokes inherited migration");
            equal(false,primary.values.get(QsCardSettings.SMALL_TILES),"real startup keeps new feature off");
            int writes=primary.writes;StatusBarSettings.preferences(storage);equal(writes,primary.writes,"real startup idempotent");
        }
    }
    private static void portableAndTypes()throws Exception {
        for(int rows=4;rows<=7;rows++) {
            Map<String,Object> one=Collections.singletonMap(QsCardSettings.GRID_ROWS,rows);ConfigTransfer.PreparedImport imported=ConfigTransfer.prepare(document(one),true);
            equal((float)rows,imported.values().get(QsCardSettings.GRID_ROWS),"portable integer row accepted");
        }
        for(Object invalid:new Object[]{3,8,4.5,null,"6",true,new java.math.BigDecimal("4.000000000000000000000000000001")})rejects(QsCardSettings.GRID_ROWS,invalid);
        for(String mode:new String[]{"always","hidden","more","swipe"})equal(mode,ConfigTransfer.prepare(document(Collections.singletonMap(QsCardSettings.SMALL_MODE,mode)),true).values().get(QsCardSettings.SMALL_MODE),"portable current and legacy mode accepted without rewriting saved data");
        for(Object bad:new Object[]{"","native","hide","SWIPE",null,1,true})rejects(QsCardSettings.SMALL_MODE,bad);
        for(Object bad:new Object[]{null,"true",1})rejects(QsCardSettings.SMALL_TILES,bad);
        for(boolean old:new boolean[]{false,true}) {
            ConfigTransfer.PreparedImport imported=ConfigTransfer.prepare(document(Collections.singletonMap(QsCardSettings.GRID_6,old)),true);
            equal(old?6f:4f,imported.values().get(QsCardSettings.GRID_ROWS),"old portable switch explicitly migrates rows");equal(2,imported.count,"portable migration is visible in preview count");
            equal(old?6f:4f,ConfigTransfer.prepare(ConfigTransfer.exportJson(Collections.singletonMap(QsCardSettings.GRID_6,old)),true).values().get(QsCardSettings.GRID_ROWS),"export before persistence migration retains old row choice");
        }
        Map<String,Object> explicit=new LinkedHashMap<>();explicit.put(QsCardSettings.GRID_6,true);explicit.put(QsCardSettings.GRID_ROWS,5);
        equal(5f,ConfigTransfer.prepare(document(explicit),true).values().get(QsCardSettings.GRID_ROWS),"explicit current selector wins");
        equal(false,ConfigTransfer.prepare(document(Collections.singletonMap("clock_enabled",true)),true).values().containsKey(QsCardSettings.GRID_ROWS),"unrelated partial imports do not invent layout settings");
        for(Object bad:new Object[]{3f,8f,4.5f,"6",true,null})equal(null,SettingsSnapshot.fromPreferences(Collections.singletonMap(QsCardSettings.GRID_ROWS,bad)),"invalid stored row rejected");
        for(Object bad:new Object[]{"","other",null,true})equal(null,SettingsSnapshot.fromPreferences(Collections.singletonMap(QsCardSettings.SMALL_MODE,bad)),"invalid stored mode rejected");
        equal(6f,SettingsSnapshot.fromPreferences(Collections.singletonMap(QsCardSettings.GRID_6,true)).get(QsCardSettings.GRID_ROWS),"old raw preference remains six before writing migration");
    }
    private static void safeMode() {
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(StatusBarSettings.SAFE_MODE,true);saved.put(QsCardSettings.GRID_ROWS,7f);saved.put(QsCardSettings.SMALL_TILES,true);saved.put(QsCardSettings.SMALL_MODE,"swipe");
        Bundle full=SettingsSnapshot.fromPreferences(saved),runtime=SafetyMode.runtimeSettings(full);
        equal(4f,runtime.get(QsCardSettings.GRID_ROWS),"safe mode restores four rows");equal(false,runtime.getBoolean(QsCardSettings.SMALL_TILES),"safe mode releases compact tile ownership");equal("always",runtime.getString(QsCardSettings.SMALL_MODE),"safe mode releases bottom visibility");
        equal(7f,full.get(QsCardSettings.GRID_ROWS),"saved rows kept");equal(true,full.getBoolean(QsCardSettings.SMALL_TILES),"saved tile choice kept");equal("swipe",full.getString(QsCardSettings.SMALL_MODE),"saved visibility kept");
        full.putBoolean(StatusBarSettings.SAFE_MODE,false);runtime=SafetyMode.runtimeSettings(full);equal(7f,runtime.get(QsCardSettings.GRID_ROWS),"leaving safety resumes saved selector");
    }
    public static void main(String[] arguments)throws Exception {
        registrationAndMigration();realPreferencesEntry();mirrors();portableAndTypes();safeMode();System.out.println("Upgrade196Check passed: "+checks);
    }
}
