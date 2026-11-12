// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit Wi-Fi hiding is opt-in; historical settings and the JSON1 protocol remain exact. */
public final class Upgrade209Check {
    private static final String KEY=NetworkDisplaySettings.WIFI_HIDDEN;
    private static int checks;
    private static void equal(Object expected,Object actual,String why) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade209 "+checks+" "+why+": "+expected+" != "+actual);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception {
        Method helper=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);helper.setAccessible(true);
        return (Map<String,Object>)helper.invoke(null,full,schema);
    }
    private static Map<String,Object> choices() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("wifi_enabled",false);result.put("label_enabled",false);result.put("label_hidden",false);
        result.put("data_badge_hidden",true);result.put(StatusBarSettings.WIFI_ICON_SCALE,125f);result.put(StatusBarSettings.LABEL_SCALE,87.5f);
        result.put(StatusBarSettings.CLOCK_SCALE,112.5f);return result;
    }
    private static Map<String,Object> aliasChoices() {
        Map<String,Object> result=new LinkedHashMap<>();result.put(QsWifiAlias.ENABLED,true);
        result.put(QsWifiAlias.MODE,"unified");result.put(QsWifiAlias.UNIFIED,"家庭网络");
        result.put(QsWifiAlias.RULES,"{\"Home\":\"家里\"}");return result;
    }
    private static void registrationAndMigration() {
        equal(52,SettingsFrameworkMirror.VERSION,"current wire schema");
        Map<String,Object> defaults=new LinkedHashMap<>();defaults.put(KEY,false);defaults.put(QsWifiAlias.ENABLED,false);defaults.put(QsWifiAlias.MODE,"per_network");defaults.put(QsWifiAlias.UNIFIED,"");defaults.put(QsWifiAlias.RULES,"{}");
        equal(defaults,Upgrade209.DEFAULTS,"one new visibility choice and four historical missing defaults");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(KEY),"portable Boolean registered");
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(1000,full.keySet().size(),"complete current snapshot");equal(987,ConfigTransfer.types().size(),"current portable registry");
        equal(false,full.getBoolean(KEY),"fresh configuration keeps native Wi-Fi visible");
        ActivationGuardPreferencesCheck.MemoryPreferences empty=new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade209.migrate(empty);equal(0,empty.writes,"empty reset must not be reseeded");equal(Collections.emptyMap(),empty.values,"empty reset unchanged");
        ActivationGuardPreferencesCheck.MemoryPreferences old=new ActivationGuardPreferencesCheck.MemoryPreferences();
        old.values.putAll(choices());old.values.put("private_note","keep");Map<String,Object> before=new LinkedHashMap<>(old.values);
        Upgrade209.migrate(old);equal(1,old.writes,"one missing-field transaction");equal(false,old.values.get(KEY),"existing users do not opt into hiding");
        Map<String,Object> expected=new LinkedHashMap<>(before);expected.putAll(Upgrade209.DEFAULTS);equal(expected,old.values,"only missing new field is added");
        Upgrade209.migrate(old);equal(1,old.writes,"migration idempotent");
        for(Object explicit:Arrays.asList(false,true,null,"invalid",1,1f)) {
            old.values.put(KEY,explicit);before=new LinkedHashMap<>(old.values);int writes=old.writes;
            Upgrade209.migrate(old);equal(before,old.values,"present value is preserved even when malformed");equal(writes,old.writes,"present value causes no write");
        }
        ActivationGuardPreferencesCheck.MemoryPreferences published=new ActivationGuardPreferencesCheck.MemoryPreferences();
        published.values.putAll(choices());published.values.putAll(aliasChoices());before=new LinkedHashMap<>(published.values);
        Upgrade209.migrate(published);expected=new LinkedHashMap<>(before);expected.put(KEY,false);
        equal(expected,published.values,"published alias choices survive missing-only migration");equal(1,published.writes,"published schema adds only visibility choice");
        for(String key:Upgrade209.DEFAULTS.keySet()) {
            published.values.put(key,null);before=new LinkedHashMap<>(published.values);int writes=published.writes;
            Upgrade209.migrate(published);equal(before,published.values,"every already present key remains authoritative");equal(writes,published.writes,"no rewrite for an already present malformed value");
        }
    }
    private static final class Storage extends Context {
        final Map<String,ActivationGuardPreferencesCheck.MemoryPreferences> stores=new LinkedHashMap<>();
        ActivationGuardPreferencesCheck.MemoryPreferences store(String name){return stores.computeIfAbsent(name,ignored->new ActivationGuardPreferencesCheck.MemoryPreferences());}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return store(name);}
        @Override public Object getSystemService(String name){return null;}
    }
    private static void realPreferencesEntry() {
        Storage empty=new Storage();StatusBarSettings.preferences(empty);equal(Collections.emptyMap(),empty.store(StatusBarSettings.PREFS).values,"real reset startup stays empty");
        for(boolean hidden:new boolean[]{false,true}) {
            Storage storage=new Storage();ActivationGuardPreferencesCheck.MemoryPreferences primary=storage.store(StatusBarSettings.PREFS);
            Bundle full=SettingsSnapshot.fromPreferences(choices());
            for(String key:full.keySet())if(!Upgrade209.DEFAULTS.containsKey(key))primary.values.put(key,full.get(key));
            Map<String,Object> before=new LinkedHashMap<>(primary.values);StatusBarSettings.preferences(storage);
            for(Map.Entry<String,Object> entry:Upgrade209.DEFAULTS.entrySet())equal(entry.getValue(),primary.values.get(entry.getKey()),"real startup invokes missing defaults");
            for(Map.Entry<String,Object> entry:before.entrySet())equal(entry.getValue(),primary.values.get(entry.getKey()),"real startup preserves every previous setting");
            primary.values.put(KEY,hidden);int writes=primary.writes;StatusBarSettings.preferences(storage);
            equal(hidden,primary.values.get(KEY),"real startup keeps explicit visibility");equal(writes,primary.writes,"real startup idempotent");
        }
    }
    private static void mirrors()throws Exception {
        Map<String,Object> saved=choices();saved.putAll(aliasChoices());saved.put(KEY,true);Bundle full=SettingsSnapshot.fromPreferences(saved);
        for(int schema=1;schema<=50;schema++) {
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle restored=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(restored),"historical schema decodes "+schema);equal(false,restored.getBoolean(KEY),"historical schema defaults to visible "+schema);
            equal(before,old,"historical wire input immutable");
            for(Object forged:Arrays.asList(false,true,null,"false",1,1f)) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(KEY,forged);Map<String,Object> mixedBefore=new LinkedHashMap<>(mixed);
                equal(null,SettingsFrameworkMirror.decode(mixed),"old schema rejects each future-field type "+schema);equal(mixedBefore,mixed,"rejected input immutable");
            }
            if(schema==50) {
                equal(991,old.size(),"schema50 remains 989 fields plus metadata");
                for(Map.Entry<String,Object> entry:saved.entrySet())if(!Upgrade209.DEFAULTS.containsKey(entry.getKey()))equal(entry.getValue(),restored.get(entry.getKey()),"schema50 preserves existing network choices");
            }
        }
        for(boolean hidden:new boolean[]{false,true}) {
            full.putBoolean(KEY,hidden);Map<String,Object> current=wire(full,51),before=new LinkedHashMap<>(current);
            Bundle restored=SettingsFrameworkMirror.decode(current);equal(true,SettingsSnapshot.complete(restored),"current wire complete");equal(hidden,restored.getBoolean(KEY),"current Boolean lossless");equal(before,current,"current input immutable");
            for(Map.Entry<String,Object> entry:Upgrade209.DEFAULTS.entrySet()) {
                Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(entry.getKey());equal(null,SettingsFrameworkMirror.decode(missing),"schema51 requires all five fields");
                for(Object bad:Arrays.asList(null,0,1,1f,new Object(),entry.getValue() instanceof Boolean?"true":false)) {
                    Map<String,Object> invalid=new LinkedHashMap<>(current);invalid.put(entry.getKey(),bad);equal(null,SettingsFrameworkMirror.decode(invalid),"schema51 strictly requires each declared type");
                }
            }
            ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(true,SettingsFrameworkMirror.write(remote,full,209L),"full publication succeeds");equal(52,remote.values.get(SettingsFrameworkMirror.SCHEMA),"publication advertises current schema");equal(hidden,remote.values.get(KEY),"publication retains explicit Boolean");
        }
        Map<String,Object> future=wire(full,51);future.put(SettingsFrameworkMirror.SCHEMA,52);equal(null,SettingsFrameworkMirror.decode(future),"future schema rejected");
    }
    private static void publishedAliasCompatibility()throws Exception {
        Bundle full=SettingsSnapshot.fromPreferences(choices());Map<String,Object> aliases=aliasChoices();
        String[] keys=aliases.keySet().toArray(new String[0]);
        // App205-208 actually published these four keys under schema50. Support
        // complete snapshots and every partial combination without losing an
        // explicit value or accepting future Wi-Fi visibility.
        for(int selected=0;selected<16;selected++) {
            Map<String,Object> published=wire(full,50);
            for(int index=0;index<keys.length;index++)if((selected&(1<<index))!=0)published.put(keys[index],aliases.get(keys[index]));
            Map<String,Object> before=new LinkedHashMap<>(published);Bundle decoded=SettingsFrameworkMirror.decode(published);
            equal(true,SettingsSnapshot.complete(decoded),"published schema50 alias subset decodes");equal(before,published,"published snapshot remains immutable");
            for(int index=0;index<keys.length;index++)equal((selected&(1<<index))!=0?aliases.get(keys[index]):Upgrade209.DEFAULTS.get(keys[index]),decoded.get(keys[index]),"explicit alias wins; only missing defaults filled");
            equal(false,decoded.getBoolean(KEY),"schema50 does not invent new visibility");
            for(String key:keys)for(Object bad:Arrays.asList(null,0,1f,aliases.get(key) instanceof Boolean?"true":false)) {
                Map<String,Object> invalid=new LinkedHashMap<>(published);invalid.put(key,bad);
                equal(null,SettingsFrameworkMirror.decode(invalid),"published alias field still requires its declared type");
            }
            for(boolean hidden:new boolean[]{false,true}) {
                Map<String,Object> mixed=new LinkedHashMap<>(published);mixed.put(KEY,hidden);
                equal(null,SettingsFrameworkMirror.decode(mixed),"schema50 refuses the new visibility field even when aliases are present");
            }
        }
        for(int schema=1;schema<50;schema++)for(String key:keys)for(Object forged:Arrays.asList(aliases.get(key),null)) {
            Map<String,Object> mixed=wire(full,schema);mixed.put(key,forged);
            equal(null,SettingsFrameworkMirror.decode(mixed),"schema before50 cannot contain a future alias field");
        }
    }
    private static String document(Map<String,Object> values)throws IOException {
        Map<String,Object> root=new LinkedHashMap<>();root.put("package",ConfigTransfer.PACKAGE_NAME);root.put("schema",1);root.put("settings",values);return IconManagementJson.encode(root);
    }
    private static void portable()throws Exception {
        equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON remains schema1");
        for(boolean hidden:new boolean[]{false,true}) {
            Map<String,Object> saved=choices();saved.putAll(aliasChoices());saved.put(KEY,hidden);Map<String,Object> before=new LinkedHashMap<>(saved);
            ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);
            equal(987,prepared.count,"complete portable export");equal(before,saved,"export never mutates saved choices");
            for(Map.Entry<String,Object> entry:saved.entrySet())equal(entry.getValue(),prepared.values().get(entry.getKey()),"portable roundtrip preserves explicit old and new settings");
            ConfigTransfer.PreparedImport partial=ConfigTransfer.prepare(document(Collections.singletonMap(KEY,hidden)),true);
            equal(Collections.singletonMap(KEY,hidden),partial.values(),"partial visibility import remains partial");
        }
        for(Object bad:Arrays.asList(null,"true",0,1,1f)) {
            checks++;try {ConfigTransfer.prepare(document(Collections.singletonMap(KEY,bad)),true);throw new AssertionError("Accepted non-Boolean Wi-Fi visibility: "+bad);}catch(IOException expected){ }
            equal(null,SettingsSnapshot.fromPreferences(Collections.singletonMap(KEY,bad)),"stored type is strict");
        }
    }
    public static void main(String[] args)throws Exception {
        registrationAndMigration();realPreferencesEntry();mirrors();publishedAliasCompatibility();portable();System.out.println("Upgrade209Check passed: "+checks);
    }
}
