// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Complete schema-5 state survives the independent fluid-cloud addition batch. */
public final class Upgrade76Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade76 "+checks+" "+reason+": "+expected+" != "+actual);
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
        values.put(SettingsFrameworkMirror.SCHEMA,schema);values.put(SettingsFrameworkMirror.REVISION,7600L);
        return values;
    }
    private static Map<String,Object> historical(Bundle snapshot,int schema) {
        Map<String,Object> values=wire(snapshot,schema);
        for(String key:Upgrade76.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade77.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade78.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade79.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        if(schema<5)for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        if(schema<4)for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        if(schema<2)for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    private static void manifest(Bundle defaults) {
        Map<String,Object> expected=new LinkedHashMap<>();
        expected.put("fluid_cloud_follow_notification_enabled",false);
        expected.put("fluid_cloud_desktop_highlight_removal_enabled",false);
        expected.put("fluid_cloud_desktop_background_removal_enabled",false);
        expected.put("fluid_cloud_accent_mask_enabled",false);
        expected.put("fluid_cloud_accent_mask_color",0xff000000);
        expected.put("fluid_cloud_accent_mask_color_custom_alpha",false);
        expected.put("fluid_cloud_accent_mask_opacity",20f);
        equal(expected,Upgrade76.DEFAULTS,"exact new batch including color-alpha metadata");
        equal(true,Collections.disjoint(Upgrade70.DEFAULTS.keySet(),Upgrade76.DEFAULTS.keySet()),"published schema-5 batch is unchanged");
        equal(12,Upgrade70.DEFAULTS.size(),"published batch length");
        for(Map.Entry<String,Object> entry:expected.entrySet())equal(entry.getValue(),defaults.get(entry.getKey()),"new default "+entry.getKey());
        equal(1,ConfigTransfer.SCHEMA_VERSION,"portable format unchanged");
    }
    private static void historicalBatches(Bundle current) {
        String[] added=Upgrade76.DEFAULTS.keySet().toArray(new String[0]);
        for(int schema=1;schema<=5;schema++) {
            Map<String,Object> old=historical(current,schema),untouched=new LinkedHashMap<>(old);
            Bundle migrated=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(migrated),"complete old schema "+schema);
            equal(untouched,old,"read never mutates historical wire");
            for(Map.Entry<String,Object> entry:Upgrade76.DEFAULTS.entrySet())equal(entry.getValue(),migrated.get(entry.getKey()),"new default from schema "+schema);
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
                equal(old.get(key),migrated.get(key),"historical value retained "+schema+" "+key);
            for(int mask=1;mask<1<<added.length;mask++) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<added.length;i++)if((mask&(1<<i))!=0)mixed.put(added[i],Upgrade76.DEFAULTS.get(added[i]));
                equal(null,SettingsFrameworkMirror.decode(mixed),"mixed schema "+schema+" mask "+mask);
            }
            for(String key:added) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,null);
                equal(null,SettingsFrameworkMirror.decode(mixed),"even null is a partial old-schema write");
            }
            ActivationGuardPreferencesCheck.MemoryPreferences daemon=new ActivationGuardPreferencesCheck.MemoryPreferences();
            daemon.values.putAll(old);int[] providerReads={0};
            SettingsFrameworkReader reader=new SettingsFrameworkReader(()->daemon,()->{},()->false);
            Bundle boot=reader.read(false,()->{providerReads[0]++;throw new IllegalStateException("Provider must stay asleep");});
            equal(true,SettingsSnapshot.complete(boot),"cold boot migrates old schema "+schema);
            equal(0,providerReads[0],"no app/provider wake");equal(untouched,daemon.values,"no daemon rewrite");reader.stop();
        }
        for(boolean accent:new boolean[]{false,true}) {
            Bundle saved=new Bundle(current);saved.putBoolean(FluidCloudAccent.ENABLED,accent);
            Map<String,Object> old=historical(saved,5);
            Bundle restored=SettingsFrameworkMirror.decode(old);
            equal(accent,restored.get(FluidCloudAccent.ENABLED),"published accent selection preserved");
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key)) {
                Map<String,Object> partial=new LinkedHashMap<>(old);partial.remove(key);
                equal(null,SettingsFrameworkMirror.decode(partial),"schema 5 missing historical key "+key);
            }
        }
    }
    private static void typedCurrent(Bundle current) {
        Map<String,Object> modern=wire(current,SettingsFrameworkMirror.VERSION);
        equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(modern)),"complete current schema");
        for(String key:Upgrade76.DEFAULTS.keySet()) {
            Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missing),"current wire never default-fills "+key);
            Bundle incomplete=new Bundle(current);incomplete.keySet().remove(key);
            equal(false,SettingsSnapshot.complete(incomplete),"provider complete requires "+key);
            ActivationGuardPreferencesCheck.MemoryPreferences rejected=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(false,SettingsFrameworkMirror.write(rejected,incomplete,7601L),"incomplete mirror never publishes "+key);
            equal(0,rejected.writes,"incomplete mirror never opens a durable write");
            for(Object invalid:new Object[]{null,new Object(),"wrong-type",1L}) {
                Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(key,invalid);
                equal(null,SettingsFrameworkMirror.decode(wrong),"current typed field "+key);
                if(!(invalid instanceof Number&&current.get(key) instanceof Float))
                    equal(null,SettingsSnapshot.fromPreferences(wrong),"private present wrong type "+key);
            }
        }
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(FluidCloudAccent.MASK_OPACITY,invalid);
            equal(null,SettingsFrameworkMirror.decode(wrong),"nonfinite opacity rejected");
            equal(null,SettingsSnapshot.fromPreferences(wrong),"private nonfinite opacity rejected");
        }
        for(Object invalid:new Object[]{null,0,SettingsFrameworkMirror.VERSION+1,Long.valueOf(SettingsFrameworkMirror.VERSION),Integer.toString(SettingsFrameworkMirror.VERSION)}) {
            Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(SettingsFrameworkMirror.SCHEMA,invalid);
            equal(null,SettingsFrameworkMirror.decode(wrong),"strict schema metadata");
        }
        for(Object invalid:new Object[]{null,0L,-1L,7600,"7600"}) {
            Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(SettingsFrameworkMirror.REVISION,invalid);
            equal(null,SettingsFrameworkMirror.decode(wrong),"strict revision metadata");
        }
    }
    private static void portableAndPrivate(Bundle current)throws Exception {
        String[] independent={FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_HIGHLIGHTS,FluidCloudStyle.DESKTOP_BACKGROUND};
        for(int flags=0;flags<16;flags++)for(boolean accent:new boolean[]{false,true}) {
            Map<String,Object> stored=new LinkedHashMap<>();
            for(String key:current.keySet())stored.put(key,current.get(key));
            stored.put(FluidCloudAccent.ENABLED,accent);
            for(int i=0;i<independent.length;i++)stored.put(independent[i],(flags&(1<<i))!=0);
            stored.put(FluidCloudAccent.MASK_ENABLED,(flags&8)!=0);stored.put(FluidCloudAccent.MASK_COLOR,0x55336699);
            stored.put(StatusBarSettings.alphaKey(FluidCloudAccent.MASK_COLOR),true);stored.put(FluidCloudAccent.MASK_OPACITY,37.25f);
            Map<String,Object> untouched=new LinkedHashMap<>(stored);
            Bundle snapshot=SettingsSnapshot.fromPreferences(stored);
            equal(true,SettingsSnapshot.complete(snapshot),"all independent options persisted");
            Bundle restored=SettingsFrameworkMirror.decode(wire(snapshot,SettingsFrameworkMirror.VERSION));
            ActivationGuardPreferencesCheck.MemoryPreferences mirror=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(true,SettingsFrameworkMirror.write(mirror,snapshot,7601L),"complete mirror publication");
            equal(1,mirror.writes,"one atomic durable mirror write");
            equal(SettingsFrameworkMirror.VERSION,mirror.values.get(SettingsFrameworkMirror.SCHEMA),"published schema metadata");
            equal(7601L,mirror.values.get(SettingsFrameworkMirror.REVISION),"published revision metadata");
            Map<String,Object> exported=ConfigTransfer.prepare(ConfigTransfer.exportJson(stored),true).values();
            for(String key:Upgrade76.DEFAULTS.keySet()) {
                equal(stored.get(key),snapshot.get(key),"private override");
                equal(stored.get(key),restored.get(key),"framework override");
                equal(stored.get(key),mirror.values.get(key),"durable framework field");
                equal(stored.get(key),exported.get(key),"portable override");
            }
            equal(accent,exported.get(FluidCloudAccent.ENABLED),"portable old accent independent");
            equal(untouched,stored,"read/export no source writes");
        }
        Map<String,Object> old=historical(current,5),untouched=new LinkedHashMap<>(old);
        Bundle sparse=SettingsSnapshot.fromPreferences(old);
        equal(true,SettingsSnapshot.complete(sparse),"private sparse snapshot fills new defaults");equal(untouched,old,"private read leaves source untouched");
        ActivationGuardPreferencesCheck.MemoryPreferences saved=new ActivationGuardPreferencesCheck.MemoryPreferences();
        saved.values.put(FluidCloudStyle.DESKTOP_HIGHLIGHTS,true);saved.values.put(FluidCloudAccent.ENABLED,true);saved.values.put(FluidCloudAccent.MASK_OPACITY,42f);
        String partial="{\"package\":\"dev.puitheme.iosstatusbar\",\"schema\":1,\"settings\":{\"fluid_cloud_follow_notification_enabled\":true}}";
        ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(partial,true);
        equal(1,prepared.count,"partial import only requested field");equal(true,ConfigTransfer.commit(saved,prepared),"overlay import");
        equal(true,saved.values.get(FluidCloudStyle.DESKTOP_HIGHLIGHTS),"partial import preserves desktop option");
        equal(true,saved.values.get(FluidCloudAccent.ENABLED),"partial import preserves old accent");
        equal(42f,saved.values.get(FluidCloudAccent.MASK_OPACITY),"partial import preserves mask parameter");
        for(String key:new String[]{FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_HIGHLIGHTS,FluidCloudStyle.DESKTOP_BACKGROUND,FluidCloudAccent.MASK_ENABLED}) {
            equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key),"portable bool registry");
            try {
                ConfigTransfer.prepare(partial.replace("fluid_cloud_follow_notification_enabled",key).replace(":true}",":\"true\"}"),true);
                throw new AssertionError("String boolean accepted: "+key);
            }catch(java.io.IOException expected){checks++;}
        }
        equal(ConfigTransfer.Type.COLOR,ConfigTransfer.types().get(FluidCloudAccent.MASK_COLOR),"portable color");
        equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(FluidCloudAccent.MASK_OPACITY),"portable opacity");
        Storage backup=new Storage();backup.preferences.values.putAll(old);backup.preferences.values.put("_c17_snapshot_schema",1);
        Map<String,Object> backupBefore=new LinkedHashMap<>(backup.preferences.values);
        equal(true,SettingsSnapshot.complete(SettingsSnapshot.durableSnapshot(backup)),"old private backup upgrades");
        equal(backupBefore,backup.preferences.values,"backup recovery never writes its source");
    }
    private static final class Storage extends Context {
        final ActivationGuardPreferencesCheck.MemoryPreferences preferences=new ActivationGuardPreferencesCheck.MemoryPreferences();
        @Override public SharedPreferences getSharedPreferences(String name,int mode) {
            if(!"statusbar_settings_last_good".equals(name))throw new AssertionError("Unexpected store "+name);
            return preferences;
        }
    }
    public static void main(String[] args)throws Exception {
        equal(52,SettingsFrameworkMirror.VERSION,"wire schema");
        Bundle current=SettingsSnapshot.fromPreferences(Collections.emptyMap());manifest(current);
        current.putBoolean(FluidCloudAccent.ENABLED,true);current.putBoolean(C17HighlightRemoval.ENABLED,true);
        current.putBoolean(C17HighlightRemoval.BACKGROUND_ENABLED,true);current.putFloat(StatusBarSettings.CLOCK_SCALE,147.25f);
        historicalBatches(current);typedCurrent(current);portableAndPrivate(current);
        System.out.println("Upgrade76Check: "+checks+" checks passed");
    }
}
