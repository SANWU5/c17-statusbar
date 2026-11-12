// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;
import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Cold boot keeps every saved music accent/mask value while adding an opt-in lockscreen scope. */
public final class Upgrade77Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String why) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade77 "+checks+" "+why+": "+expected+" != "+actual);
    }
    private static Map<String,Object> wire(Bundle snapshot,int schema) {
        Map<String,Object> values=new LinkedHashMap<>();
        for(String key:snapshot.keySet())values.put(key,snapshot.get(key));
        if(schema<13)for(String key:Upgrade90.DEFAULTS.keySet())values.remove(key);
        if(schema<14)for(String key:Upgrade91.DEFAULTS.keySet())values.remove(key);
        if(schema<21)for(String key:Upgrade103.DEFAULTS.keySet())values.remove(key);
        if(schema<22)for(String key:Upgrade105.DEFAULTS.keySet())values.remove(key);
        if(schema<23)for(String key:Upgrade106.DEFAULTS.keySet())values.remove(key);
        if(schema<24)for(String key:Upgrade107.DEFAULTS.keySet())values.remove(key);
        if(schema<25)for(String key:Upgrade108.DEFAULTS.keySet())values.remove(key); if(schema<26)for(String key:Upgrade117.DEFAULTS.keySet())values.remove(key); if(schema<27)for(String key:Upgrade118.DEFAULTS.keySet())values.remove(key); if(schema<28)for(String key:Upgrade135.DEFAULTS.keySet())values.remove(key); if(schema<29)for(String key:Upgrade136.DEFAULTS.keySet())values.remove(key); if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())values.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())values.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())values.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())values.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())values.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())values.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())values.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())values.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())values.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())values.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())values.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())values.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())values.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())values.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())values.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())values.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())values.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())values.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())values.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())values.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())values.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) values.remove(key);
        if(schema<30)for(String key:Upgrade144.DEFAULTS.keySet())values.remove(key);
        if(schema<20)for(String key:Upgrade101.DEFAULTS.keySet())values.remove(key);
        if(schema<19)for(String key:Upgrade100.DEFAULTS.keySet())values.remove(key);
        if(schema<18)for(String key:Upgrade99.DEFAULTS.keySet())values.remove(key);
        if(schema<17)for(String key:Upgrade98.DEFAULTS.keySet())values.remove(key);
        if(schema<16)for(String key:Upgrade96.DEFAULTS.keySet())values.remove(key);
        if(schema<15)for(String key:Upgrade93.DEFAULTS.keySet())values.remove(key);
        if(schema<11)for(String key:Upgrade82.DEFAULTS.keySet())values.remove(key);
        if(schema<10)for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        if(schema<12)values.remove(FluidCloudStyle.FOLLOW_MEDIA);
        if(schema<12)values.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        values.put(SettingsFrameworkMirror.SCHEMA,schema);values.put(SettingsFrameworkMirror.REVISION,7701L);
        return values;
    }
    private static Map<String,Object> historical(Bundle snapshot,int schema) {
        Map<String,Object> values=wire(snapshot,schema);
        for(String key:Upgrade77.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade78.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade79.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        if(schema<6)for(String key:Upgrade76.DEFAULTS.keySet())values.remove(key);
        if(schema<5)for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        if(schema<4)for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        if(schema<2)for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    private static void oldSchemas(Bundle current) {
        for(int schema=1;schema<=6;schema++) {
            Map<String,Object> old=historical(current,schema),untouched=new LinkedHashMap<>(old);
            Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete history "+schema);
            equal(false,decoded.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"lockscreen stays opt-in "+schema);
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
                equal(old.get(key),decoded.get(key),"existing field "+schema+" "+key);
            equal(untouched,old,"read retains wire "+schema);
            for(Object partial:new Object[]{null,false,true,"false",0}) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(FluidCloudAccent.LOCKSCREEN_ENABLED,partial);
                equal(null,SettingsFrameworkMirror.decode(mixed),"historical write cannot include new field "+schema);
            }
            // Every released field remains required, especially the schema-6 mask batch.
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key)) {
                Map<String,Object> incomplete=new LinkedHashMap<>(old);incomplete.remove(key);
                equal(null,SettingsFrameworkMirror.decode(incomplete),"missing released field "+schema+" "+key);
            }
            ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();remote.values.putAll(old);
            int[] wakes={0};SettingsFrameworkReader reader=new SettingsFrameworkReader(()->remote,()->{},()->false);
            Bundle boot=reader.read(false,()->{wakes[0]++;throw new IllegalStateException("App must stay asleep");});
            equal(true,SettingsSnapshot.complete(boot),"cold boot "+schema);equal(0,wakes[0],"no app wake "+schema);
            equal(false,boot.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"cold boot lockscreen default");
            equal(untouched,remote.values,"boot never rewrites daemon "+schema);reader.stop();
        }
        Map<String,Object> schema6=historical(current,6);
        for(Map<String,Object> batch:java.util.Arrays.asList(Upgrade76.DEFAULTS,Upgrade70.DEFAULTS,Upgrade67.DEFAULTS,Upgrade66.DEFAULTS)) {
            Map<String,Object> missingBatch=new LinkedHashMap<>(schema6);
            for(String key:batch.keySet())missingBatch.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missingBatch),"schema 6 cannot default-fill an absent prior batch");
        }
    }
    private static void currentWire(Bundle current)throws Exception {
        for(boolean lockscreen:new boolean[]{false,true}) {
            Bundle saved=new Bundle(current);saved.putBoolean(FluidCloudAccent.LOCKSCREEN_ENABLED,lockscreen);
            Map<String,Object> modern=wire(saved,SettingsFrameworkMirror.VERSION),untouched=new LinkedHashMap<>(modern);
            Bundle decoded=SettingsFrameworkMirror.decode(modern);equal(true,SettingsSnapshot.complete(decoded),"complete current");
            for(String key:saved.keySet())equal(saved.get(key),decoded.get(key),"current wire keeps "+key);
            equal(untouched,modern,"current decode does not rewrite");
            Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(FluidCloudAccent.LOCKSCREEN_ENABLED);
            equal(null,SettingsFrameworkMirror.decode(missing),"current field required");
            for(Object bad:new Object[]{null,"true",0,1f,1L}) {
                Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(FluidCloudAccent.LOCKSCREEN_ENABLED,bad);
                equal(null,SettingsFrameworkMirror.decode(wrong),"typed current field");
            }
            ActivationGuardPreferencesCheck.MemoryPreferences durable=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(true,SettingsFrameworkMirror.write(durable,saved,7702L),"atomic publication");equal(1,durable.writes,"one durable write");
            equal(SettingsFrameworkMirror.VERSION,durable.values.get(SettingsFrameworkMirror.SCHEMA),"new wire version");
            equal(lockscreen,durable.values.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"persist lockscreen scope");
            Bundle copy=SettingsSnapshot.fromPreferences(modern);equal(lockscreen,copy.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"app preference");
            Map<String,Object> exported=ConfigTransfer.prepare(ConfigTransfer.exportJson(modern),true).values();
            for(String key:new String[]{FluidCloudAccent.ENABLED,FluidCloudAccent.MASK_ENABLED,FluidCloudAccent.MASK_COLOR,FluidCloudAccent.MASK_OPACITY,FluidCloudAccent.LOCKSCREEN_ENABLED})
                equal(saved.get(key),exported.get(key),"portable accent/mask "+key);
        }
        Bundle incomplete=new Bundle(current);incomplete.keySet().remove(FluidCloudAccent.LOCKSCREEN_ENABLED);
        ActivationGuardPreferencesCheck.MemoryPreferences rejected=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(false,SettingsFrameworkMirror.write(rejected,incomplete,7702L),"partial publication rejected");equal(0,rejected.writes,"no write on incomplete");
        Map<String,Object> old=historical(current,6);Bundle sparse=SettingsSnapshot.fromPreferences(old);
        equal(false,sparse.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"old app prefs default only new scope");
        for(String key:Upgrade76.DEFAULTS.keySet())equal(current.get(key),sparse.get(key),"old app mask preserved "+key);
    }
    public static void main(String[] args)throws Exception {
        equal(52,SettingsFrameworkMirror.VERSION,"schema");
        equal(Collections.singletonMap("fluid_cloud_accent_lockscreen_enabled",false),Upgrade77.DEFAULTS,"one independent new field");
        equal(true,Collections.disjoint(Upgrade77.DEFAULTS.keySet(),Upgrade76.DEFAULTS.keySet()),"older batch unchanged");
        equal(7,Upgrade76.DEFAULTS.size(),"previous batch complete");equal(12,Upgrade70.DEFAULTS.size(),"published batch unchanged");
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"registry default");
        equal("fluid_cloud_accent",FeatureOptions.GROUP_BY_KEY.get(FluidCloudAccent.LOCKSCREEN_ENABLED),"group ownership");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(FluidCloudAccent.LOCKSCREEN_ENABLED),"portable field type");
        Bundle current=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        current.putBoolean(FluidCloudAccent.ENABLED,true);current.putBoolean(FluidCloudAccent.MASK_ENABLED,true);
        current.putInt(FluidCloudAccent.MASK_COLOR,0x66336699);current.putBoolean(StatusBarSettings.alphaKey(FluidCloudAccent.MASK_COLOR),true);
        current.putFloat(FluidCloudAccent.MASK_OPACITY,47.25f);current.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);
        oldSchemas(current);currentWire(current);
        System.out.println("Upgrade77Check: "+checks+" checks passed");
    }
}
