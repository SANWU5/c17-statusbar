// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Portrait click defaults are additive; saved visual/material state remains authoritative. */
public final class Upgrade78Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;
        if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade78 "+checks+" "+reason+": "+expected+" != "+actual);
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
        values.put(SettingsFrameworkMirror.SCHEMA,schema);
        values.put(SettingsFrameworkMirror.REVISION,7801L);
        return values;
    }
    private static Map<String,Object> historical(Bundle snapshot,int schema) {
        Map<String,Object> values=wire(snapshot,schema);
        for(String key:Upgrade78.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade79.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        if(schema<7)for(String key:Upgrade77.DEFAULTS.keySet())values.remove(key);
        if(schema<6)for(String key:Upgrade76.DEFAULTS.keySet())values.remove(key);
        if(schema<5)for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        if(schema<4)for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        if(schema<2)for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    private static void manifest(Bundle defaults) {
        Map<String,Object> expected=new LinkedHashMap<>();
        expected.put("notification_big_clock_clock_click_enabled",true);
        expected.put("notification_big_clock_date_click_enabled",true);
        equal(52,SettingsFrameworkMirror.VERSION,"new wire version");
        equal(expected,Upgrade78.DEFAULTS,"exact independent two-field batch");
        equal(1,Upgrade77.DEFAULTS.size(),"published schema-7 batch unchanged");
        equal(7,Upgrade76.DEFAULTS.size(),"published schema-6 batch unchanged");
        equal(12,Upgrade70.DEFAULTS.size(),"published schema-5 batch unchanged");
        equal(true,Collections.disjoint(Upgrade78.DEFAULTS.keySet(),Upgrade77.DEFAULTS.keySet()),"old keys not repurposed");
        for(String key:expected.keySet()) {
            equal(true,defaults.get(key),"default enabled "+key);
            equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(key),"registry default "+key);
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key),"portable Boolean "+key);
            String landscape=key.replace("notification_big_clock_","notification_big_clock_landscape_");
            equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.containsKey(landscape),"no landscape click alias "+key);
        }
    }
    private static void historicalWires(Bundle current) {
        String[] added=Upgrade78.DEFAULTS.keySet().toArray(new String[0]);
        for(int schema=1;schema<=7;schema++) {
            Map<String,Object> old=historical(current,schema),untouched=new LinkedHashMap<>(old);
            Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete migration "+schema);
            for(String key:added)equal(true,decoded.get(key),"new opt-in action default "+schema+" "+key);
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
                equal(old.get(key),decoded.get(key),"saved field "+schema+" "+key);
            equal(untouched,old,"decode never edits source wire "+schema);
            for(int mask=1;mask<(1<<added.length);mask++) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<added.length;i++)if((mask&(1<<i))!=0)mixed.put(added[i],true);
                equal(null,SettingsFrameworkMirror.decode(mixed),"partial/full new batch rejected on old schema "+schema+" "+mask);
            }
            for(String key:added)for(Object partial:new Object[]{null,false,true,"true",0,1f}) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,partial);
                equal(null,SettingsFrameworkMirror.decode(mixed),"mixed-schema value rejected "+schema+" "+key);
            }
            ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();
            remote.values.putAll(old);int[] wakes={0};
            SettingsFrameworkReader reader=new SettingsFrameworkReader(()->remote,()->{},()->false);
            Bundle boot=reader.read(false,()->{wakes[0]++;throw new IllegalStateException("App must remain asleep");});
            equal(true,SettingsSnapshot.complete(boot),"cold boot complete "+schema);
            equal(0,wakes[0],"cold boot does not wake app "+schema);
            for(String key:added)equal(true,boot.get(key),"cold boot new default "+schema+" "+key);
            for(String key:new String[]{FluidCloudAccent.ENABLED,FluidCloudAccent.MASK_ENABLED,FluidCloudAccent.MASK_COLOR,FluidCloudAccent.MASK_OPACITY})
                if(old.containsKey(key))equal(old.get(key),boot.get(key),"cold boot saved music mask "+schema+" "+key);
            equal(untouched,remote.values,"cold boot does not rewrite daemon "+schema);reader.stop();
        }
        // Schema 7 already published all older fields. A truncated old wire must not look healthy.
        Map<String,Object> schema7=historical(current,7);
        for(String key:schema7.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key)) {
            Map<String,Object> incomplete=new LinkedHashMap<>(schema7);incomplete.remove(key);
            equal(null,SettingsFrameworkMirror.decode(incomplete),"schema 7 released field required "+key);
        }
        for(Map<String,Object> batch:Arrays.asList(Upgrade77.DEFAULTS,Upgrade76.DEFAULTS,Upgrade70.DEFAULTS)) {
            Map<String,Object> absent=new LinkedHashMap<>(schema7);
            for(String key:batch.keySet())absent.remove(key);
            equal(null,SettingsFrameworkMirror.decode(absent),"schema 7 cannot refill absent released batch");
        }
    }
    private static void currentWires(Bundle current)throws Exception {
        for(boolean clock:new boolean[]{false,true})for(boolean date:new boolean[]{false,true}) {
            Bundle saved=new Bundle(current);
            saved.putBoolean(NotificationBigClockSettings.CLOCK_CLICK_ENABLED,clock);
            saved.putBoolean(NotificationBigClockSettings.DATE_CLICK_ENABLED,date);
            Map<String,Object> modern=wire(saved,SettingsFrameworkMirror.VERSION),untouched=new LinkedHashMap<>(modern);
            Bundle decoded=SettingsFrameworkMirror.decode(modern);
            equal(true,SettingsSnapshot.complete(decoded),"complete current wire");
            for(String key:saved.keySet())equal(saved.get(key),decoded.get(key),"current value retained "+key);
            equal(untouched,modern,"current wire unchanged");
            for(String key:Upgrade78.DEFAULTS.keySet()) {
                Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(key);
                equal(null,SettingsFrameworkMirror.decode(missing),"current action field required "+key);
                for(Object bad:new Object[]{null,"true",0,1f,1L}) {
                    Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(key,bad);
                    equal(null,SettingsFrameworkMirror.decode(wrong),"strict action Boolean "+key);
                }
            }
            Map<String,Object> missingBoth=new LinkedHashMap<>(modern);
            for(String key:Upgrade78.DEFAULTS.keySet())missingBoth.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missingBoth),"current wire cannot refill both action fields");
            ActivationGuardPreferencesCheck.MemoryPreferences durable=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(true,SettingsFrameworkMirror.write(durable,saved,7802L),"publish complete snapshot");
            equal(1,durable.writes,"single atomic write");
            equal(SettingsFrameworkMirror.VERSION,durable.values.get(SettingsFrameworkMirror.SCHEMA),"publish current schema");
            equal(clock,durable.values.get(NotificationBigClockSettings.CLOCK_CLICK_ENABLED),"persist independent clock action");
            equal(date,durable.values.get(NotificationBigClockSettings.DATE_CLICK_ENABLED),"persist independent date action");
            Bundle app=SettingsSnapshot.fromPreferences(modern);
            for(String key:saved.keySet())equal(saved.get(key),app.get(key),"app preference retained "+key);
            Map<String,Object> exported=ConfigTransfer.prepare(ConfigTransfer.exportJson(modern),true).values();
            for(String key:new String[]{NotificationBigClockSettings.CLOCK_CLICK_ENABLED,NotificationBigClockSettings.DATE_CLICK_ENABLED,
                    FluidCloudAccent.ENABLED,FluidCloudAccent.MASK_ENABLED,FluidCloudAccent.MASK_COLOR,
                    StatusBarSettings.alphaKey(FluidCloudAccent.MASK_COLOR),FluidCloudAccent.MASK_OPACITY,FluidCloudAccent.LOCKSCREEN_ENABLED})
                equal(saved.get(key),exported.get(key),"export/import value retained "+key);
        }
        for(String key:Upgrade78.DEFAULTS.keySet()) {
            Bundle incomplete=new Bundle(current);incomplete.keySet().remove(key);
            ActivationGuardPreferencesCheck.MemoryPreferences rejected=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(false,SettingsFrameworkMirror.write(rejected,incomplete,7803L),"partial publication rejected "+key);
            equal(0,rejected.writes,"no partial durable write "+key);
        }
        Map<String,Object> old=historical(current,7);Bundle app=SettingsSnapshot.fromPreferences(old);
        for(String key:Upgrade78.DEFAULTS.keySet())equal(true,app.get(key),"old app preferences default only missing action "+key);
        for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
            equal(old.get(key),app.get(key),"old app preferences retained "+key);
    }
    public static void main(String[] args)throws Exception {
        Bundle current=SettingsSnapshot.fromPreferences(Collections.emptyMap());manifest(current);
        current.putBoolean(NotificationBigClockSettings.MASTER,true);
        current.putFloat(StatusBarSettings.CLOCK_SCALE,380f);
        current.putBoolean(FluidCloudAccent.ENABLED,true);current.putBoolean(FluidCloudAccent.MASK_ENABLED,true);
        current.putInt(FluidCloudAccent.MASK_COLOR,0x66336699);
        current.putBoolean(StatusBarSettings.alphaKey(FluidCloudAccent.MASK_COLOR),true);
        current.putFloat(FluidCloudAccent.MASK_OPACITY,47.25f);
        current.putBoolean(FluidCloudAccent.LOCKSCREEN_ENABLED,true);
        current.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);
        historicalWires(current);currentWires(current);
        System.out.println("Upgrade78Check: "+checks+" checks passed");
    }
}
