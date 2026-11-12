// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict complete historical copies, explicit master values and existing config survive migration. */
public final class Upgrade79Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade79 "+checks+" "+reason+": "+expected+" != "+actual);
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
        values.put(SettingsFrameworkMirror.SCHEMA,schema);values.put(SettingsFrameworkMirror.REVISION,7901L);
        return values;
    }
    private static Map<String,Object> historical(Bundle snapshot,int schema) {
        Map<String,Object> values=wire(snapshot,schema);
        for(String key:Upgrade79.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade80.DEFAULTS.keySet())values.remove(key);
        if(schema<8)for(String key:Upgrade78.DEFAULTS.keySet())values.remove(key);
        if(schema<7)for(String key:Upgrade77.DEFAULTS.keySet())values.remove(key);
        if(schema<6)for(String key:Upgrade76.DEFAULTS.keySet())values.remove(key);
        if(schema<5)for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        if(schema<4)for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        if(schema<2)for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> expected=new LinkedHashMap<>();
        expected.put("notification_big_clock_clock_launch_app","");
        expected.put("notification_big_clock_date_launch_app","");
        expected.put("notification_icons_follow_launcher_pack",false);
        expected.put("fluid_cloud_style_enabled",false);
        equal(expected,Upgrade79.DEFAULTS,"exact four fields");equal(52,SettingsFrameworkMirror.VERSION,"schema");
        Bundle defaults=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(true,SettingsSnapshot.complete(defaults),"new install complete");
        for(String key:expected.keySet())equal(expected.get(key),defaults.get(key),"new default "+key);
        for(String key:new String[]{NotificationBigClockSettings.CLOCK_LAUNCH_APP,NotificationBigClockSettings.DATE_LAUNCH_APP}) {
            equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(key),"app string "+key);
            equal(false,StatusBarSettings.STRING_DEFAULTS.containsKey(NotificationBigClockSettings.landscapeKey(key)),"portrait only "+key);
        }
        String[] added=expected.keySet().toArray(new String[0]);
        String[] children={FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_HIGHLIGHTS,FluidCloudStyle.DESKTOP_BACKGROUND};
        for(int flags=0;flags<8;flags++)for(int schema=1;schema<=8;schema++) {
            Bundle current=new Bundle(defaults);current.putFloat(StatusBarSettings.CLOCK_SCALE,380f);
            for(int i=0;i<children.length;i++)current.putBoolean(children[i],(flags&(1<<i))!=0);
            Map<String,Object> old=historical(current,schema),untouched=new LinkedHashMap<>(old);
            Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete old "+schema+" flags "+flags);
            // Schema 1..5 did not yet offer any of these children.
            equal(schema>=6&&flags!=0,decoded.get(FluidCloudStyle.MASTER),"infer active legacy page");
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key))
                equal(old.get(key),decoded.get(key),"old value retained "+key);
            equal(untouched,old,"source never mutated");
            for(int mask=1;mask<16;mask++) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<added.length;i++)if((mask&(1<<i))!=0)mixed.put(added[i],expected.get(added[i]));
                equal(null,SettingsFrameworkMirror.decode(mixed),"partial batch rejected "+schema+" "+mask);
            }
            old.remove(StatusBarSettings.CLOCK_SCALE);
            equal(null,SettingsFrameworkMirror.decode(old),"missing historical field still rejected");
        }
        for(int flags=0;flags<8;flags++) {
            Map<String,Object> sparse=new LinkedHashMap<>();
            for(int i=0;i<children.length;i++)sparse.put(children[i],(flags&(1<<i))!=0);
            equal(flags!=0,StatusBarSettings.bool(sparse,FluidCloudStyle.MASTER),"sparse app legacy inference");
            sparse.put(FluidCloudStyle.MASTER,false);
            equal(false,StatusBarSettings.bool(sparse,FluidCloudStyle.MASTER),"explicit off preserved");
            sparse.put(FluidCloudStyle.MASTER,true);
            equal(true,StatusBarSettings.bool(sparse,FluidCloudStyle.MASTER),"explicit on preserved");
        }
        Bundle saved=new Bundle(defaults);
        saved.putString(NotificationBigClockSettings.CLOCK_LAUNCH_APP,"com.coloros.alarmclock");
        saved.putString(NotificationBigClockSettings.DATE_LAUNCH_APP,"com.coloros.calendar");
        saved.putBoolean(NotificationIconArea.MASTER,true);saved.putBoolean(NotificationIconArea.FOLLOW_LAUNCHER,true);
        saved.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);saved.putBoolean(FluidCloudStyle.MASTER,false);
        Map<String,Object> modern=wire(saved,12);
        Bundle round=SettingsFrameworkMirror.decode(modern);
        for(String key:saved.keySet())equal(
                !modern.containsKey(key)&&Upgrade90.DEFAULTS.containsKey(key)?Upgrade90.DEFAULTS.get(key):saved.get(key),
                round.get(key),"schema12 persisted value or frozen later default "+key);
        Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(modern),true).values();
        for(String key:added)equal(saved.get(key),portable.get(key),"portable "+key);
        for(String key:added) {
            Map<String,Object> partial=new LinkedHashMap<>(modern);partial.remove(key);
            equal(null,SettingsFrameworkMirror.decode(partial),"modern missing rejected "+key);
            partial.put(key,null);equal(null,SettingsFrameworkMirror.decode(partial),"modern null rejected "+key);
        }
        ActivationGuardPreferencesCheck.MemoryPreferences durable=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true,SettingsFrameworkMirror.write(durable,saved,7902L),"durable publication");
        equal(SettingsFrameworkMirror.VERSION,durable.values.get(SettingsFrameworkMirror.SCHEMA),"write schema");
        equal(false,durable.values.get(FluidCloudStyle.MASTER),"off survives write");
        for(Object invalid:new Object[]{0,15,13L,"13",null}) {
            Map<String,Object> broken=new LinkedHashMap<>(modern);broken.put(SettingsFrameworkMirror.SCHEMA,invalid);
            equal(null,SettingsFrameworkMirror.decode(broken),"strict schema type/range "+invalid);
        }
        try{Upgrade79.DEFAULTS.put("illegal",true);throw new AssertionError("mutable defaults");}
        catch(UnsupportedOperationException immutable){checks++;}
        System.out.println("Upgrade79Check: "+checks+" checks passed");
    }
}
