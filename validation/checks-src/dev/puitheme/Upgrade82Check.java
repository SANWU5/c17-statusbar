// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Independent RAT text batch: strict boot copies, old scales and portable Unicode. */
public final class Upgrade82Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade82 "+reason+": "+expected+" != "+actual);
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
        if(schema<12)values.remove(FluidCloudStyle.FOLLOW_MEDIA);
        if(schema<12)values.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        values.put(SettingsFrameworkMirror.SCHEMA,schema);values.put(SettingsFrameworkMirror.REVISION,8201L);
        return values;
    }
    private static Map<String,Object> historical(Bundle snapshot,int schema) {
        Map<String,Object> values=wire(snapshot,schema);
        for(String key:Upgrade82.DEFAULTS.keySet())values.remove(key);
        if(schema<10)for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        if(schema<9)for(String key:Upgrade79.DEFAULTS.keySet())values.remove(key);
        if(schema<8)for(String key:Upgrade78.DEFAULTS.keySet())values.remove(key);
        if(schema<7)for(String key:Upgrade77.DEFAULTS.keySet())values.remove(key);
        if(schema<6)for(String key:Upgrade76.DEFAULTS.keySet())values.remove(key);
        if(schema<5)for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        if(schema<4)for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        if(schema<2)for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    private static void manifest(Bundle defaults) {
        equal(52,SettingsFrameworkMirror.VERSION,"current schema");
        String[] ids={"g","e","1x","2g","3g","h","h_plus","4g","4g_plus","4_5g","5g","5g_plus","5ga","5_5g","5g_sa","5g_nsa"};
        String[] texts={"G","E","1x","2G","3G","H","H+","4G","4G+","4.5G","5G","5G+","5GA","5.5G","5G SA","5G NSA"};
        Map<String,Object> expected=new LinkedHashMap<>();expected.put("label_custom_content_enabled",false);
        for(int i=0;i<ids.length;i++)expected.put("label_content_"+ids[i],texts[i]);
        equal(expected,Upgrade82.DEFAULTS,"exact independent 17-field addition batch");
        equal(16,NetworkLabelFormats.STRINGS.size(),"one text for every supported grade");
        equal(false,defaults.get(NetworkLabelFormats.ENABLED),"fresh custom content switch off");
        equal(false,defaults.get(FeatureOptions.masterKey("label")),"fresh label page off");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(NetworkLabelFormats.ENABLED),"portable master");
        for(String key:NetworkLabelFormats.STRINGS.keySet()) {
            equal(expected.get(key),defaults.get(key),"fresh grade default "+key);
            equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(key),"portable grade text "+key);
        }
        for(Map<String,Object> previous:Arrays.asList(Upgrade65.DEFAULTS,Upgrade66.DEFAULTS,Upgrade67.DEFAULTS,
                Upgrade70.DEFAULTS,Upgrade76.DEFAULTS,Upgrade77.DEFAULTS,Upgrade78.DEFAULTS,Upgrade79.DEFAULTS,Upgrade80.DEFAULTS))
            equal(true,Collections.disjoint(previous.keySet(),Upgrade82.DEFAULTS.keySet()),"previous batches not repurposed");
        try{Upgrade82.DEFAULTS.put("illegal",true);throw new AssertionError("mutable upgrade defaults");}
        catch(UnsupportedOperationException immutable){checks++;}
    }
    private static void historicalWires(Bundle defaults) {
        String[] added=Upgrade82.DEFAULTS.keySet().toArray(new String[0]);
        for(int schema=1;schema<=10;schema++)for(boolean label:new boolean[]{false,true})for(boolean unified:new boolean[]{false,true}) {
            Bundle saved=new Bundle(defaults);saved.putFloat(StatusBarSettings.CLOCK_SCALE,380f);
            saved.putBoolean(FeatureOptions.masterKey("clock"),true);saved.putBoolean("clock_enabled",false);
            saved.putBoolean(FeatureOptions.masterKey("label"),label);saved.putBoolean("label_normalize_enabled",unified);
            saved.putBoolean(StatusBarSettings.SAFE_MODE,false);saved.putFloat(StatusBarSettings.DATA_OFFSET_X,-138.25f);
            saved.putBoolean(NetworkLabelFormats.ENABLED,true);
            saved.putBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true);
            Map<String,Object> old=historical(saved,schema),unchanged=new LinkedHashMap<>(old);
            Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete old schema "+schema);
            equal(unchanged,old,"historical read never mutates source "+schema);
            equal(false,decoded.get(NetworkLabelFormats.ENABLED),"old upgrade remains opt-in "+schema);
            equal(380f,decoded.get(StatusBarSettings.CLOCK_SCALE),"old size not reset "+schema);
            equal(label,decoded.get(FeatureOptions.masterKey("label")),"old master authoritative "+schema);
            equal(unified,decoded.get("label_normalize_enabled"),"old normalization authoritative "+schema);
            equal(false,decoded.get(FluidCloudStyle.FOLLOW_FOREGROUND),"old retired foreground cannot reactivate "+schema);
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
                equal(FluidCloudStyle.FOLLOW_FOREGROUND.equals(key)?false:old.get(key),decoded.get(key),"old field unchanged except retired foreground "+schema+" "+key);
            for(String key:added) {
                equal(Upgrade82.DEFAULTS.get(key),decoded.get(key),"new field default "+schema+" "+key);
                for(Object value:new Object[]{null,Upgrade82.DEFAULTS.get(key)}) {
                    Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,value);
                    equal(null,SettingsFrameworkMirror.decode(mixed),"any present new key rejects old schema "+schema+" "+key);
                }
            }
            // Test all batch cardinalities and rotations, including a whole new
            // batch advertised as historical, without 2^17 duplicate allocations.
            for(int count=2;count<=added.length;count++)for(int start=0;start<added.length;start++) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<count;i++){String key=added[(start+i)%added.length];mixed.put(key,Upgrade82.DEFAULTS.get(key));}
                equal(null,SettingsFrameworkMirror.decode(mixed),"mixed batch "+schema+" "+count+"/"+start);
            }
            for(String key:new String[]{StatusBarSettings.CLOCK_SCALE,FeatureOptions.masterKey("label"),"label_normalize_enabled"}) {
                Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(key);
                equal(null,SettingsFrameworkMirror.decode(missing),"old released field still required "+schema+" "+key);
            }
            ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();remote.values.putAll(old);
            int[] providerReads={0};SettingsFrameworkReader reader=new SettingsFrameworkReader(()->remote,()->{},()->false);
            Bundle boot=reader.read(false,()->{providerReads[0]++;throw new IllegalStateException("App must remain asleep");});
            equal(true,SettingsSnapshot.complete(boot),"old cold boot complete "+schema);
            equal(0,providerReads[0],"old cold boot needs no provider "+schema);
            equal(380f,boot.get(StatusBarSettings.CLOCK_SCALE),"old boot size retained "+schema);
            equal(unified,boot.get("label_normalize_enabled"),"old boot unified request retained "+schema);
            equal(false,boot.get(FluidCloudStyle.FOLLOW_FOREGROUND),"old cold boot retired foreground off "+schema);
            equal(unchanged,remote.values,"boot never rewrites daemon "+schema);reader.stop();
        }
    }
    private static void currentWiresAndPortable(Bundle defaults)throws Exception {
        for(boolean custom:new boolean[]{false,true})for(boolean unified:new boolean[]{false,true}) {
            Bundle saved=new Bundle(defaults);saved.putBoolean(NetworkLabelFormats.ENABLED,custom);
            saved.putBoolean("label_normalize_enabled",unified);saved.putBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true);
            saved.putBoolean(FeatureOptions.masterKey("label"),true);saved.putFloat(StatusBarSettings.CLOCK_SCALE,380f);
            int index=0;
            for(String key:NetworkLabelFormats.STRINGS.keySet()) {
                saved.putString(key,index%3==0?"":index%3==1?"📶 "+index:"测试等级 "+index+" 🚀");index++;
            }
            Map<String,Object> modern=wire(saved,SettingsFrameworkMirror.VERSION),untouched=new LinkedHashMap<>(modern);
            Bundle decoded=SettingsFrameworkMirror.decode(modern);
            equal(true,SettingsSnapshot.complete(decoded),"current complete");equal(untouched,modern,"current source immutable");
            for(String key:saved.keySet())equal(FluidCloudStyle.FOLLOW_FOREGROUND.equals(key)?false:saved.get(key),decoded.get(key),"current saved value except retired foreground "+key);
            equal(custom,FeatureOptions.from(decoded).effective("label",NetworkLabelFormats.ENABLED),"custom contents run exactly when requested");
            equal(unified&&!custom,FeatureOptions.from(decoded).effective("label","label_normalize_enabled"),"unify runs only when custom is off");
            equal(custom,decoded.get(NetworkLabelFormats.ENABLED),"current wire retains the custom request");
            Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(modern),true).values();
            for(String key:Upgrade82.DEFAULTS.keySet())equal(saved.get(key),portable.get(key),"Unicode/empty portable "+key);
            equal(unified,portable.get("label_normalize_enabled"),"portable unified preference retained");
            equal(false,portable.get(FluidCloudStyle.FOLLOW_FOREGROUND),"portable retired foreground off");
            ActivationGuardPreferencesCheck.MemoryPreferences durable=new ActivationGuardPreferencesCheck.MemoryPreferences();
            equal(true,SettingsFrameworkMirror.write(durable,saved,8202L),"atomic publication");equal(1,durable.writes,"one durable write");
            equal(SettingsFrameworkMirror.VERSION,durable.values.get(SettingsFrameworkMirror.SCHEMA),"published current schema");
            equal(unified,durable.values.get("label_normalize_enabled"),"atomic publication retains unified preference");
            equal(false,durable.values.get(FluidCloudStyle.FOLLOW_FOREGROUND),"atomic publication retires old foreground");
            for(String key:Upgrade82.DEFAULTS.keySet()) {
                equal(saved.get(key),durable.values.get(key),"durable new field "+key);
                Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(key);
                equal(null,SettingsFrameworkMirror.decode(missing),"current missing field rejected "+key);
                Bundle incomplete=new Bundle(saved);incomplete.keySet().remove(key);
                equal(false,SettingsSnapshot.complete(incomplete),"provider copy cannot omit current key "+key);
                ActivationGuardPreferencesCheck.MemoryPreferences rejected=new ActivationGuardPreferencesCheck.MemoryPreferences();
                equal(false,SettingsFrameworkMirror.write(rejected,incomplete,8203L),"partial publication rejected "+key);
                equal(0,rejected.writes,"no partial writes "+key);
                for(Object bad:new Object[]{null,1,1L,1f,new Object()}) {
                    Map<String,Object> broken=new LinkedHashMap<>(modern);broken.put(key,bad);
                    equal(null,SettingsFrameworkMirror.decode(broken),"strict current type "+key);
                }
                Map<String,Object> crossType=new LinkedHashMap<>(modern);
                crossType.put(key,NetworkLabelFormats.ENABLED.equals(key)?"true":Boolean.TRUE);
                equal(null,SettingsFrameworkMirror.decode(crossType),"String/Boolean cannot cross-convert "+key);
            }
            Map<String,Object> absentBatch=new LinkedHashMap<>(modern);
            for(String key:Upgrade82.DEFAULTS.keySet())absentBatch.remove(key);
            equal(null,SettingsFrameworkMirror.decode(absentBatch),"current schema cannot default missing whole batch");
            for(Object bad:new Object[]{null,0,15,13L,"13"}) {
                Map<String,Object> broken=new LinkedHashMap<>(modern);broken.put(SettingsFrameworkMirror.SCHEMA,bad);
                equal(null,SettingsFrameworkMirror.decode(broken),"schema type/range "+bad);
            }
        }
        Map<String,Object> settings=new LinkedHashMap<>();settings.put(NetworkLabelFormats.ENABLED,true);
        equal(false,FeatureOptions.from(settings).effective("label",NetworkLabelFormats.ENABLED),"custom content requires label page");
        settings.put(FeatureOptions.masterKey("label"),true);
        equal(true,FeatureOptions.from(settings).effective("label",NetworkLabelFormats.ENABLED),"custom contents run on the label page");
        settings.put("label_normalize_enabled",false);
        equal(true,FeatureOptions.from(settings).effective("label",NetworkLabelFormats.ENABLED),"custom contents do not need unify off first");
        settings.put(StatusBarSettings.SAFE_MODE,true);
        equal(false,FeatureOptions.from(settings).effective("label",NetworkLabelFormats.ENABLED),"safe mode suppresses content");
    }
    public static void main(String[] args)throws Exception {
        Bundle defaults=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(true,SettingsSnapshot.complete(defaults),"fresh complete");
        manifest(defaults);historicalWires(defaults);currentWiresAndPortable(defaults);
        System.out.println("Upgrade82Check: "+checks+" checks passed");
    }
}
