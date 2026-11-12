// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/** V3 adds one wire record while retiring only the three old portable icon fields. */
public final class Upgrade187Check {
    private static int checks;
    private static final String KEY="icon_management_v3_records";
    private static final String DEFAULT="{\"version\":3,\"scopePacks\":{},\"overrides\":{},\"appearances\":{},\"animations\":{}}";
    private static final Set<String> RETIRED=new LinkedHashSet<>(Arrays.asList("status_icon_pack_enabled","status_icon_pack_layers","status_icon_pack_assignments"));
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade187 "+checks+" "+why+": "+expected+" != "+actual);}
    private interface Task{void run()throws Exception;}
    private static void rejected(Task task,String why)throws Exception{checks++;try{task.run();throw new AssertionError("Accepted invalid v3 records: "+why);}catch(IOException expected){}}
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception{
        Method method=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);method.setAccessible(true);return (Map<String,Object>)method.invoke(null,full,schema);
    }
    private static String document(Map<String,Object> settings)throws Exception{
        Map<String,Object> root=new LinkedHashMap<>();root.put("package",ConfigTransfer.PACKAGE_NAME);root.put("schema",1);root.put("settings",settings);return IconManagementJson.encode(root);
    }
    private static Map<String,Object> saved(){
        Map<String,Object> values=new LinkedHashMap<>();values.put("status_icon_pack_enabled",true);values.put("status_icon_pack_layers","legacy-layers");values.put("status_icon_pack_assignments","legacy-assignments");
        values.put(FontScopes.KEY,"{\"network_speed\":\"pingfang\"}");values.put(StatusBarSettings.FONT_MODE,"custom");values.put(StatusBarSettings.FONT_REVISION,"a".repeat(64));values.put("clock_enabled",true);values.put("private_note","keep");return values;
    }
    private static void registration(Bundle full){
        equal(KEY,IconManagementSettings.KEY,"frozen record identity");equal(DEFAULT,IconManagementSettings.DEFAULT,"frozen empty v3 record");
        equal(Collections.singletonMap(KEY,DEFAULT),Upgrade187.DEFAULTS,"one independent upgrade addition");equal(RETIRED,IconManagementSettings.LEGACY_KEYS,"exact retired boundary");
        equal(52,SettingsFrameworkMirror.VERSION,"current wire schema");equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON stays schema1");
        equal(1000, full.keySet().size(),"complete wire settings count");equal(987,ConfigTransfer.types().size(),"current portable registry includes opt-in card settings");
        equal(DEFAULT,StatusBarSettings.STRING_DEFAULTS.get(KEY),"registered string default");equal(DEFAULT,full.getString(KEY),"fresh effective default");equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(KEY),"new string is portable");
        for(String retired:RETIRED){equal(true,full.containsKey(retired),"old field retains complete-schema identity");equal(false,ConfigTransfer.types().containsKey(retired),"old field not portable");equal(null,SettingsCatalog.item(retired),"no retired editor");equal(true,SettingsCatalog.hiddenKeys().contains(retired),"retired field explicitly hidden");}
        SettingsCatalog.Group group=SettingsCatalog.group("status_icon_library");equal("图标管理",group.title,"retained route new title");equal(SettingsCatalog.OTHER,group.category,"other page destination");equal(IconManagementSettings.ENABLED,group.masterKey,"current independent v3 master");
        equal(new LinkedHashSet<>(Arrays.asList(IconManagementSettings.ENABLED,KEY)),ConfigScope.section(group.id).keys(),"partial share owns v3 record and current pause switch");
    }
    private static void migration(){
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade187.migrate(store);equal(0,store.writes,"empty reset remains empty");
        store.values.putAll(saved());Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade187.migrate(store);equal(1,store.writes,"one durable addition");equal(DEFAULT,store.values.get(KEY),"new domain starts empty");
        for(String key:before.keySet())equal(before.get(key),store.values.get(key),"unrelated/retired source remains untouched: "+key);
        Upgrade187.migrate(store);equal(1,store.writes,"idempotent upgrade");
        for(Object existing:new Object[]{DEFAULT,"{\"version\":3}","not-json",null,0}){
            store.values.put(KEY,existing);before=new LinkedHashMap<>(store.values);Upgrade187.migrate(store);equal(before,store.values,"present source is never silently overwritten");equal(1,store.writes,"present key has no migration write");
        }
    }
    private static void mirrors(Bundle full)throws Exception{
        for(int schema=1;schema<47;schema++){
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);equal(false,old.containsKey(KEY),"historical wire excludes future v3 record");
            Bundle upgraded=SettingsFrameworkMirror.decode(old);equal(true,SettingsSnapshot.complete(upgraded),"complete historical wire upgrades "+schema);equal(DEFAULT,upgraded.getString(KEY),"old wire receives empty new domain");equal(before,old,"historical input immutable");
            for(Object forged:new Object[]{DEFAULT,"{}",null,true,0f,Collections.emptyMap()}){Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(KEY,forged);equal(null,SettingsFrameworkMirror.decode(mixed),"historical schema rejects future key even when null");}
        }
        equal(979,wire(full,46).size(),"schema46 frozen 977 plus metadata");equal(978,wire(full,45).size(),"schema45 frozen 976 plus metadata");
        Map<String,Object> current=wire(full,47);equal(980,current.size(),"current complete wire plus metadata");equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"current complete record accepted");
        Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(KEY);equal(null,SettingsFrameworkMirror.decode(missing),"current schema requires new field");
        for(Object wrong:new Object[]{null,true,0,0f,Collections.emptyList()}){Map<String,Object> invalid=new LinkedHashMap<>(current);invalid.put(KEY,wrong);equal(null,SettingsFrameworkMirror.decode(invalid),"current wire field requires string type");}
        current.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(current),"future schema rejected");
        ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();equal(true,SettingsFrameworkMirror.write(remote,full,187L),"publication succeeds");equal(52, remote.values.get(SettingsFrameworkMirror.SCHEMA),"publication advertises current schema");equal(DEFAULT,remote.values.get(KEY),"publication carries independent field");
        for(String retired:RETIRED)equal(full.get(retired),remote.values.get(retired),"old wire identity remains readable");
    }
    private static void portable()throws Exception{
        String selected=IconManagementSettings.applyAppearance(DEFAULT,"scope:wifi",new IconManagementSettings.Appearance("custom",0xff123456,0xffabcdef,50f));
        Map<String,Object> imported=new LinkedHashMap<>();imported.put(KEY,selected);imported.put("clock_enabled",false);
        for(String retired:RETIRED)imported.put(retired,Collections.singletonMap("obsolete","ignored"));
        ConfigTransfer.PreparedImport planned=ConfigTransfer.prepare(document(imported),true);equal(2,planned.count,"only current legitimate values imported");equal(3,planned.skippedLegacyIcons,"explicit old-field skip count");equal(true,planned.warning.contains("3"),"review warning names skipped fields");equal(selected,planned.values().get(KEY),"v3 choice retained");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(saved());Map<String,Object> before=new LinkedHashMap<>(store.values);
        equal(true,ConfigTransfer.commit(store,planned),"current record import commits");equal(selected,store.values.get(KEY),"new choice saved");equal(false,store.values.get("clock_enabled"),"other selected current field imported");
        for(String key:before.keySet())if(!key.equals("clock_enabled"))equal(before.get(key),store.values.get(key),"import does not revive or modify retired/unselected field");
        ConfigTransfer.PreparedImport full=ConfigTransfer.prepare(ConfigTransfer.exportJson(store.values),true);equal(987,full.count,"complete export uses only current portable registry");equal(0,full.skippedLegacyIcons,"current export contains no retired fields");
        for(String retired:RETIRED)equal(false,full.values().containsKey(retired),"retired field absent from current export");
        ConfigTransfer.PreparedImport partial=ConfigTransfer.prepare(ConfigTransfer.exportJson(store.values,Collections.singleton(KEY)),true);equal(1,partial.count,"v3 partial export has one field");equal(selected,partial.values().get(KEY),"v3 partial export preserves appearance");
        for(String invalid:new String[]{"[]","{\"version\":2}","{\"version\":3,\"version\":3}","{\"version\":3,\"scopePacks\":{\"wifi\":\"not-a-hash\"}}"})rejected(()->ConfigTransfer.prepare(document(Collections.singletonMap(KEY,invalid)),true),"strict v3 JSON import");
        equal(false,ConfigTransfer.prepare(document(Collections.singletonMap("clock_enabled",true)),true).values().containsKey(KEY),"old partial import does not invent v3 choices");
    }
    public static void main(String[] args)throws Exception{
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());registration(full);migration();mirrors(full);portable();System.out.println("Upgrade187Check passed: "+checks);
    }
}
