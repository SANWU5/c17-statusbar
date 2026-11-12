// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.*;

/** One pause switch, lossless old v3 choices, strict mirror schema and portable Boolean round trips. */
public final class Upgrade188Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade188 "+checks+" "+why+": "+expected+" != "+actual);}
    private static Map<String,Object> wire(Bundle full,int schema){Map<String,Object> result=new LinkedHashMap<>();for(String key:full.keySet())result.put(key,full.get(key));if(schema<48)result.remove(IconManagementSettings.ENABLED);if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key);result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,188L);return result;}
    private static String document(Map<String,Object> values)throws IOException{return IconManagementJson.encode(new LinkedHashMap<String,Object>(){{put("package",ConfigTransfer.PACKAGE_NAME);put("schema",1);put("settings",values);}});}
    private static String records()throws IOException{return IconManagementSettings.applyAppearance(IconManagementSettings.DEFAULT,"scope:wifi",new IconManagementSettings.Appearance("custom",0xff123456,0xffabcdef,50f));}
    private static void registration(){
        Bundle fresh=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal("icon_management_v3_enabled",IconManagementSettings.ENABLED,"frozen identity");equal(Collections.singletonMap(IconManagementSettings.ENABLED,true),Upgrade188.DEFAULTS,"only one new Boolean");
        equal(52,SettingsFrameworkMirror.VERSION,"wire schema");equal(1000, fresh.keySet().size(),"full snapshot");equal(987,ConfigTransfer.types().size(),"portable registry");equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON schema unchanged");
        equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(IconManagementSettings.ENABLED),"code187 choices stay enabled by default");equal(true,fresh.getBoolean(IconManagementSettings.ENABLED),"fresh switch default");equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(IconManagementSettings.ENABLED),"Boolean is portable");
        equal(IconManagementSettings.ENABLED,FeatureOptions.masterKey("status_icon_library"),"feature ownership");equal("status_icon_library",FeatureOptions.GROUP_BY_KEY.get(IconManagementSettings.ENABLED),"feature group");equal(true,FeatureOptions.from(Collections.emptyMap()).enabled("status_icon_library"),"default feature permission");
        equal(IconManagementSettings.ENABLED,SettingsCatalog.group("status_icon_library").masterKey,"page master");equal(true,SettingsCatalog.item(IconManagementSettings.ENABLED).master,"searchable master item");
        equal(new LinkedHashSet<>(Arrays.asList(IconManagementSettings.ENABLED,IconManagementSettings.KEY)),ConfigScope.section("status_icon_library").keys(),"page shares switch plus existing record");
        equal(SettingsCatalog.OTHER,SettingsCatalog.group("font").category,"fonts moved to Other");equal("字体配置",SettingsCatalog.group("font").title,"font destination title");
        for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(SettingsCatalog.STATUSBAR))for(SettingsCatalog.Group group:section.groups)equal(false,"font".equals(group.id),"old status bar entry removed");
        boolean font=false;for(SettingsCatalog.NavigationSection section:SettingsCatalog.navigationSections(SettingsCatalog.OTHER))for(SettingsCatalog.Group group:section.groups)font|="font".equals(group.id);equal(true,font,"Other owns font destination");
    }
    private static void migration()throws Exception{
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade188.migrate(store);equal(0,store.writes,"empty reset stays empty");
        String original=records();store.values.put(IconManagementSettings.KEY,original);store.values.put(FontScopes.KEY,"{\"network_speed\":\"pingfang\"}");store.values.put("status_icon_pack_enabled",true);store.values.put("private_note","keep");Map<String,Object> before=new LinkedHashMap<>(store.values);
        Upgrade188.migrate(store);equal(1,store.writes,"one durable write");equal(true,store.values.get(IconManagementSettings.ENABLED),"existing v3 defaults active");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"saved source untouched "+key);Upgrade188.migrate(store);equal(1,store.writes,"idempotent");
        for(Object explicit:new Object[]{true,false,null,0,"invalid"}){store.values.put(IconManagementSettings.ENABLED,explicit);before=new LinkedHashMap<>(store.values);Upgrade188.migrate(store);equal(before,store.values,"present value never overwritten");equal(1,store.writes,"present value no migration write");}
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(IconManagementSettings.ENABLED,true);saved.put(IconManagementSettings.KEY,original);saved.put(StatusBarSettings.SAFE_MODE,true);Bundle full=SettingsSnapshot.fromPreferences(saved),runtime=SafetyMode.runtimeSettings(full);
        equal(true,full.getBoolean(IconManagementSettings.ENABLED),"safety retains saved master");equal(false,runtime.getBoolean(IconManagementSettings.ENABLED),"safety disables runtime master");equal(original,runtime.getString(IconManagementSettings.KEY),"safety does not erase choices");equal(false,FeatureOptions.from(full).enabled("status_icon_library"),"safe feature denied");
    }
    private static void mirrors()throws Exception{
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(IconManagementSettings.KEY,records());saved.put(IconManagementSettings.ENABLED,false);Bundle full=SettingsSnapshot.fromPreferences(saved);
        Map<String,Object> old=wire(full,47),before=new LinkedHashMap<>(old);equal(980,old.size(),"schema47 stays 978 plus metadata");Bundle upgraded=SettingsFrameworkMirror.decode(old);equal(true,SettingsSnapshot.complete(upgraded),"schema47 upgrades complete");equal(true,upgraded.getBoolean(IconManagementSettings.ENABLED),"schema47 preserves active behavior");equal(records(),upgraded.getString(IconManagementSettings.KEY),"schema47 choices intact");equal(before,old,"decode input immutable");
        for(Object future:new Object[]{false,true,null,0}){Map<String,Object> forged=new LinkedHashMap<>(old);forged.put(IconManagementSettings.ENABLED,future);equal(null,SettingsFrameworkMirror.decode(forged),"historical schema cannot claim later master");}
        Map<String,Object> current=wire(full,48);equal(981,current.size(),"schema48 full plus metadata");equal(false,SettingsFrameworkMirror.decode(current).getBoolean(IconManagementSettings.ENABLED),"current false survives mirror");
        current.remove(IconManagementSettings.ENABLED);equal(null,SettingsFrameworkMirror.decode(current),"current schema requires master");
        for(Object wrong:new Object[]{null,0,"false",Collections.emptyMap()}){current=wire(full,48);current.put(IconManagementSettings.ENABLED,wrong);equal(null,SettingsFrameworkMirror.decode(current),"current master must be Boolean");}
        current=wire(full,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(current),"future schema rejected");
        ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();equal(true,SettingsFrameworkMirror.write(remote,full,188L),"publication succeeds");equal(52, remote.values.get(SettingsFrameworkMirror.SCHEMA),"current schema published");equal(false,remote.values.get(IconManagementSettings.ENABLED),"false published losslessly");
    }
    private static void portable()throws Exception{
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(IconManagementSettings.KEY,records());saved.put(IconManagementSettings.ENABLED,false);saved.put("private_note","keep");
        ConfigTransfer.PreparedImport exported=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);equal(987,exported.count,"full portable settings");equal(false,exported.values().get(IconManagementSettings.ENABLED),"exported false");equal(records(),exported.values().get(IconManagementSettings.KEY),"exported choices");
        ConfigTransfer.PreparedImport partial=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved,ConfigScope.section("status_icon_library").keys()),true);equal(2,partial.count,"page export contains both keys");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(saved);ConfigTransfer.PreparedImport on=ConfigTransfer.prepare(document(Collections.singletonMap(IconManagementSettings.ENABLED,true)),true);equal(true,ConfigTransfer.commit(store,on),"switch import succeeds");equal(true,store.values.get(IconManagementSettings.ENABLED),"switch restored");equal(records(),store.values.get(IconManagementSettings.KEY),"switch-only import keeps record");
        store.values.put(IconManagementSettings.ENABLED,false);ConfigTransfer.PreparedImport legacy=ConfigTransfer.prepare(document(Collections.singletonMap(IconManagementSettings.KEY,records())),true);equal(false,legacy.values().containsKey(IconManagementSettings.ENABLED),"old partial import invents no switch");equal(true,ConfigTransfer.commit(store,legacy),"records-only import succeeds");equal(false,store.values.get(IconManagementSettings.ENABLED),"records-only import leaves paused master");equal("keep",store.values.get("private_note"),"unrelated preference retained");
        checks++;try{ConfigTransfer.prepare(document(Collections.singletonMap(IconManagementSettings.ENABLED,"false")),true);throw new AssertionError("string master accepted");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception{registration();migration();mirrors();portable();System.out.println("Upgrade188Check passed: "+checks);}
}
