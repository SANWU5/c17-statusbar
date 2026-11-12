// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persisted material modes, old transparency, strict boot snapshots and UI dependencies. */
public final class Upgrade80Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade80 "+reason+": "+expected+" != "+actual);
    }
    private static Map<String,Object> wire(Bundle snapshot,int schema) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:snapshot.keySet())result.put(key,snapshot.get(key));
        if(schema<13)for(String key:Upgrade90.DEFAULTS.keySet())result.remove(key);
        if(schema<14)for(String key:Upgrade91.DEFAULTS.keySet())result.remove(key);
        if(schema<21)for(String key:Upgrade103.DEFAULTS.keySet())result.remove(key);
        if(schema<22)for(String key:Upgrade105.DEFAULTS.keySet())result.remove(key);
        if(schema<23)for(String key:Upgrade106.DEFAULTS.keySet())result.remove(key);
        if(schema<24)for(String key:Upgrade107.DEFAULTS.keySet())result.remove(key);
        if(schema<25)for(String key:Upgrade108.DEFAULTS.keySet())result.remove(key); if(schema<26)for(String key:Upgrade117.DEFAULTS.keySet())result.remove(key); if(schema<27)for(String key:Upgrade118.DEFAULTS.keySet())result.remove(key); if(schema<28)for(String key:Upgrade135.DEFAULTS.keySet())result.remove(key); if(schema<29)for(String key:Upgrade136.DEFAULTS.keySet())result.remove(key); if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if(schema<30)for(String key:Upgrade144.DEFAULTS.keySet())result.remove(key);
        if(schema<20)for(String key:Upgrade101.DEFAULTS.keySet())result.remove(key);
        if(schema<19)for(String key:Upgrade100.DEFAULTS.keySet())result.remove(key);
        if(schema<18)for(String key:Upgrade99.DEFAULTS.keySet())result.remove(key);
        if(schema<17)for(String key:Upgrade98.DEFAULTS.keySet())result.remove(key);
        if(schema<16)for(String key:Upgrade96.DEFAULTS.keySet())result.remove(key);
        if(schema<15)for(String key:Upgrade93.DEFAULTS.keySet())result.remove(key);
        if(schema<11)for(String key:Upgrade82.DEFAULTS.keySet())result.remove(key);
        if(schema<12)result.remove(FluidCloudStyle.FOLLOW_MEDIA);
        if(schema<12)result.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,8001L);
        return result;
    }
    private static Map<String,Object> old(Bundle snapshot,int schema) {
        Map<String,Object> result=wire(snapshot,schema);
        for(String key:Upgrade80.DEFAULTS.keySet())result.remove(key);
        if(schema<9)for(String key:Upgrade79.DEFAULTS.keySet())result.remove(key);
        if(schema<8)for(String key:Upgrade78.DEFAULTS.keySet())result.remove(key);
        if(schema<7)for(String key:Upgrade77.DEFAULTS.keySet())result.remove(key);
        if(schema<6)for(String key:Upgrade76.DEFAULTS.keySet())result.remove(key);
        if(schema<5)for(String key:Upgrade70.DEFAULTS.keySet())result.remove(key);
        if(schema<4)for(String key:Upgrade67.DEFAULTS.keySet())result.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())result.remove(key);
        if(schema<2)for(String key:Upgrade65.DEFAULTS.keySet())result.remove(key);
        return result;
    }
    public static void main(String[] args)throws Exception {
        equal(52,SettingsFrameworkMirror.VERSION,"schema");
        Map<String,Object> expected=new LinkedHashMap<>();
        expected.put("fluid_cloud_media_keep_highlight_enabled",false);
        expected.put("fluid_cloud_foreground_follow_system_enabled",false);
        expected.put("fluid_cloud_background_mode","native");
        expected.put("fluid_cloud_background_color",0xff303438);
        expected.put("fluid_cloud_background_color_custom_alpha",false);
        expected.put("fluid_cloud_background_opacity",100f);
        equal(expected,Upgrade80.DEFAULTS,"exact addition batch");
        Bundle defaults=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(true,SettingsSnapshot.complete(defaults),"fresh snapshot");
        for(String key:expected.keySet())equal(expected.get(key),defaults.get(key),"default "+key);
        for(int schema=1;schema<=9;schema++)for(boolean transparent:new boolean[]{false,true}) {
            Bundle saved=new Bundle(defaults);saved.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,transparent);
            saved.putBoolean(FluidCloudStyle.MASTER,false);saved.putFloat(StatusBarSettings.CLOCK_SCALE,380f);
            Map<String,Object> legacy=old(saved,schema),untouched=new LinkedHashMap<>(legacy);
            Bundle decoded=SettingsFrameworkMirror.decode(legacy);
            equal(true,SettingsSnapshot.complete(decoded),"historical "+schema);
            equal(schema>=6&&transparent?"custom":"native",decoded.get(FluidCloudStyle.BACKGROUND_MODE),"old background mode");
            equal(schema>=6&&transparent?0f:100f,decoded.get(FluidCloudStyle.BACKGROUND_OPACITY),"old transparency");
            for(String key:legacy.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
                equal(legacy.get(key),decoded.get(key),"preserve "+key);
            equal(untouched,legacy,"source immutable");
            for(String key:expected.keySet()) {
                Map<String,Object> mixed=new LinkedHashMap<>(legacy);mixed.put(key,expected.get(key));
                equal(null,SettingsFrameworkMirror.decode(mixed),"mixed rejected "+key);
            }
            legacy.remove(StatusBarSettings.CLOCK_SCALE);
            equal(null,SettingsFrameworkMirror.decode(legacy),"missing old field rejected");
        }
        Map<String,Object> sparse=new LinkedHashMap<>();sparse.put(FluidCloudStyle.DESKTOP_BACKGROUND,true);
        Bundle inherited=SettingsSnapshot.fromPreferences(sparse);
        equal("custom",inherited.get(FluidCloudStyle.BACKGROUND_MODE),"app sparse legacy mode");
        equal(0f,inherited.get(FluidCloudStyle.BACKGROUND_OPACITY),"app sparse legacy alpha");
        equal(true,StatusBarSettings.bool(sparse,FluidCloudStyle.MASTER),"legacy active page");
        sparse.put(FluidCloudStyle.BACKGROUND_MODE,"media");
        equal("native",StatusBarSettings.string(sparse,FluidCloudStyle.BACKGROUND_MODE),"retired media option migrates to independent feature");
        equal(true,StatusBarSettings.bool(sparse,FluidCloudStyle.FOLLOW_MEDIA),"legacy enabled media fill migrates without losing selection");
        equal(100f,StatusBarSettings.settingNumber(sparse,FluidCloudStyle.BACKGROUND_OPACITY,100f),"new mode fresh alpha");
        for(String mode:new String[]{"native","notification","media","custom"}) {
            Bundle saved=new Bundle(defaults);saved.putString(FluidCloudStyle.BACKGROUND_MODE,mode);
            saved.putInt(FluidCloudStyle.BACKGROUND_COLOR,0x12345678);saved.putBoolean(StatusBarSettings.alphaKey(FluidCloudStyle.BACKGROUND_COLOR),true);
            saved.putFloat(FluidCloudStyle.BACKGROUND_OPACITY,37f);saved.putBoolean(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,true);
            saved.putBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true);saved.putBoolean(FluidCloudStyle.MASTER,false);
            Map<String,Object> modern=wire(saved,12);
            Bundle decoded=SettingsFrameworkMirror.decode(modern);
            equal(true,SettingsSnapshot.complete(decoded),"modern complete");
            for(String key:saved.keySet())equal(FluidCloudStyle.FOLLOW_FOREGROUND.equals(key)?false
                    :!modern.containsKey(key)&&Upgrade90.DEFAULTS.containsKey(key)?Upgrade90.DEFAULTS.get(key):saved.get(key),
                    decoded.get(key),"schema12 retained or frozen later default except retired foreground "+key);
            equal(true,modern.get(FluidCloudStyle.FOLLOW_FOREGROUND),"decode never mutates original retired flag");
            Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(modern),true).values();
            for(String key:expected.keySet())equal(FluidCloudStyle.FOLLOW_FOREGROUND.equals(key)?false:FluidCloudStyle.BACKGROUND_MODE.equals(key)&&("media".equals(mode)||"notification".equals(mode))?"native":saved.get(key),portable.get(key),"portable retired switch and media option normalized "+key);
            for(String key:expected.keySet()) {
                Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(key);
                equal(null,SettingsFrameworkMirror.decode(missing),"missing modern rejected");
            }
            for(Object invalid:new Object[]{0,15,13L,"13",null}) {
                Map<String,Object> broken=new LinkedHashMap<>(modern);broken.put(SettingsFrameworkMirror.SCHEMA,invalid);
                equal(null,SettingsFrameworkMirror.decode(broken),"invalid schema");
            }
        }
        for(String bad:new String[]{"transparent","original","mediax",""}) {
            Map<String,Object> modern=wire(defaults,12);modern.put(FluidCloudStyle.BACKGROUND_MODE,bad);
            String exported=ConfigTransfer.exportJson(modern);
            equal("native",ConfigTransfer.prepare(exported,true).values().get(FluidCloudStyle.BACKGROUND_MODE),"invalid stored mode export fallback");
            String invalid=exported.replace("\""+FluidCloudStyle.BACKGROUND_MODE+"\": \"native\"", "\""+FluidCloudStyle.BACKGROUND_MODE+"\": \""+bad+"\"");
            try{ConfigTransfer.prepare(invalid,true);throw new AssertionError("accepted invalid mode "+bad);}
            catch(java.io.IOException rejected){checks++;}
        }
        Map<String,Object> controls=new LinkedHashMap<>();controls.put(FluidCloudStyle.MASTER,true);controls.put(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,true);
        equal(true,FeatureOptions.from(controls).effective("fluid_cloud_style",FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT),"keep is its own switch; the removal itself belongs to the C17 feature");
        equal(true,SettingsCatalog.unavailableReason(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,controls).contains("C17 高光去除"),"keep editor dependency points at the C17 switch");
        controls.put(C17HighlightRemoval.ENABLED,true);
        equal("",SettingsCatalog.unavailableReason(FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT,controls),"keep editor allowed");
        controls.put(FluidCloudStyle.MASTER,false);
        equal(true,FeatureOptions.from(controls).effective("fluid_cloud_style",FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT),"keep does not depend on the fluid style page master");
        controls.put(FluidCloudStyle.MASTER,true);controls.put(StatusBarSettings.SAFE_MODE,true);
        equal(false,FeatureOptions.from(controls).effective("fluid_cloud_style",FluidCloudStyle.MEDIA_KEEP_HIGHLIGHT),"safe mode");
        ActivationGuardPreferencesCheck.MemoryPreferences disk=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true,SettingsFrameworkMirror.write(disk,defaults,8002L),"durable publish");
        equal(SettingsFrameworkMirror.VERSION,disk.values.get(SettingsFrameworkMirror.SCHEMA),"durable schema");
        retiredForeground(defaults);
        System.out.println("Upgrade80Check: "+checks+" checks passed");
    }
    private static void retiredForeground(Bundle defaults)throws Exception {
        Bundle old=new Bundle(defaults);old.putBoolean(FluidCloudStyle.MASTER,true);old.putBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true);
        old.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);old.putString(FluidCloudStyle.BACKGROUND_MODE,"media");
        old.putFloat(StatusBarSettings.CLOCK_SCALE,380f);
        for(int schema:new int[]{10,11,12}) {
            Map<String,Object> source=wire(old,schema),untouched=new LinkedHashMap<>(source);
            Bundle decoded=SettingsFrameworkMirror.decode(source);
            equal(true,SettingsSnapshot.complete(decoded),"retired switch keeps complete schema "+schema);
            equal(false,decoded.getBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true),"legacy/current retired switch forcibly off "+schema);
            equal("media",decoded.getString(FluidCloudStyle.BACKGROUND_MODE),"retirement preserves background selection");
            equal(380f,decoded.get(StatusBarSettings.CLOCK_SCALE),"retirement preserves unrelated clock scale");
            equal(untouched,source,"retirement decode never rewrites source");
            ActivationGuardPreferencesCheck.MemoryPreferences remote=new ActivationGuardPreferencesCheck.MemoryPreferences();remote.values.putAll(source);
            int[] providerReads={0};SettingsFrameworkReader reader=new SettingsFrameworkReader(()->remote,()->{},()->false);
            Bundle boot=reader.read(false,()->{providerReads[0]++;return null;});
            equal(false,boot.getBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true),"cold boot cannot reactivate retired switch");
            equal(0,providerReads[0],"retired complete framework snapshot needs no provider");reader.stop();
            Map<String,Object> wrongType=new LinkedHashMap<>(source);wrongType.put(FluidCloudStyle.FOLLOW_FOREGROUND,"true");
            equal(null,SettingsFrameworkMirror.decode(wrongType),"retirement keeps strict Boolean wire type");
            Map<String,Object> missing=new LinkedHashMap<>(source);missing.remove(FluidCloudStyle.FOLLOW_FOREGROUND);
            equal(null,SettingsFrameworkMirror.decode(missing),"historical complete field still required");
        }
        equal(false,SettingsSnapshot.runtimeCopy(old).getBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true),"older provider/cache forcibly off");
        equal(true,old.getBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,false),"runtime copy does not mutate supplied cache");
        Map<String,Object> sparse=new LinkedHashMap<>();sparse.put(FluidCloudStyle.FOLLOW_FOREGROUND,true);
        equal(false,SettingsSnapshot.fromPreferences(sparse).getBoolean(FluidCloudStyle.FOLLOW_FOREGROUND,true),"local preferences read forcibly off");
        ActivationGuardPreferencesCheck.MemoryPreferences published=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true,SettingsFrameworkMirror.write(published,old,8301L),"retirement publication remains atomic");
        equal(1,published.writes,"retirement published in one transaction");
        equal(false,published.values.get(FluidCloudStyle.FOLLOW_FOREGROUND),"durable boot copy cannot reactivate retired switch");
        String header="{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{";
        ConfigTransfer.PreparedImport included=ConfigTransfer.prepare(header+"\""+FluidCloudStyle.FOLLOW_FOREGROUND+"\":true}}",true);
        equal(false,included.values().get(FluidCloudStyle.FOLLOW_FOREGROUND),"explicit retired import forced off");
        ConfigTransfer.PreparedImport unrelated=ConfigTransfer.prepare(header+"\"wifi_offset_x\":3.25}}",true);
        published.values.put(FluidCloudStyle.FOLLOW_FOREGROUND,true);published.values.put(StatusBarSettings.CLOCK_SCALE,380f);
        int writes=published.writes;
        equal(true,ConfigTransfer.commit(published,unrelated),"unrelated partial import retires old local request");
        equal(writes+1,published.writes,"partial import and retirement share one commit");
        equal(false,published.values.get(FluidCloudStyle.FOLLOW_FOREGROUND),"omitted retired import still forcibly off");
        equal(380f,published.values.get(StatusBarSettings.CLOCK_SCALE),"partial retirement import preserves unrelated saved values");
        try{ConfigTransfer.prepare(header+"\""+FluidCloudStyle.FOLLOW_FOREGROUND+"\":\"true\"}}",true);throw new AssertionError("retired import converted String");}
        catch(java.io.IOException strictType){checks++;}
    }
}
