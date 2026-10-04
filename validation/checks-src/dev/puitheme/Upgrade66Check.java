package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Cold boot migration uses the real codec/reader and rejects damaged complete batches. */
public final class Upgrade66Check {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade66 "+checks+": "+expected+" != "+actual);
    }
    private static Map<String,Object> wire(Bundle snapshot,int schema) {
        Map<String,Object> values=new LinkedHashMap<>();
        for(String key:snapshot.keySet())values.put(key,snapshot.get(key));
        values.put(SettingsFrameworkMirror.SCHEMA,schema);values.put(SettingsFrameworkMirror.REVISION,6600L);
        return values;
    }
    private static Map<String,Object> historical(Bundle current,int schema) {
        Map<String,Object> values=wire(current,schema);
        for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);
        for(String key:Upgrade67.DEFAULTS.keySet())values.remove(key);
        if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet())values.remove(key);
        if(schema==1)for(String key:Upgrade65.DEFAULTS.keySet())values.remove(key);
        return values;
    }
    private static Object historicalExpected(Bundle current,String key) {
        return NativeClockMeasurement.SCALE_BASIS_VERSION.equals(key)?1f:current.get(key);
    }
    private static final class Storage extends Context {
        final ActivationGuardPreferencesCheck.MemoryPreferences backup=new ActivationGuardPreferencesCheck.MemoryPreferences();
        @Override public SharedPreferences getSharedPreferences(String name,int mode) {
            if(!"statusbar_settings_last_good".equals(name))throw new AssertionError("Unexpected store: "+name);
            return backup;
        }
    }
    private static void rawRecovery() {
        Storage old=new Storage();
        old.backup.values.put("_c17_snapshot_schema",1);
        old.backup.values.put(StatusBarSettings.CLOCK_SCALE,147.125f);
        old.backup.values.put(NotificationBigClockSettings.MASTER,true);
        Map<String,Object> untouched=new LinkedHashMap<>(old.backup.values);
        Bundle snapshot=SettingsSnapshot.durableSnapshot(old);
        equal(true,SettingsSnapshot.complete(snapshot));equal(147.125f,snapshot.get(StatusBarSettings.CLOCK_SCALE));
        equal(true,snapshot.get(NotificationBigClockSettings.MASTER));equal(untouched,old.backup.values);
        for(String key:Upgrade66.DEFAULTS.keySet())equal(Upgrade66.DEFAULTS.get(key),snapshot.get(key));
        for(String key:Upgrade67.DEFAULTS.keySet())equal(Upgrade67.DEFAULTS.get(key),snapshot.get(key));
        // Raw private stores are intentionally sparse; present damaged new fields must not be repaired.
        Map<String,Object> additions=new LinkedHashMap<>(Upgrade66.DEFAULTS);additions.putAll(Upgrade67.DEFAULTS);additions.putAll(Upgrade70.DEFAULTS);
        for(String key:additions.keySet()) {
            Storage broken=new Storage();broken.backup.values.putAll(untouched);broken.backup.values.put(key,new Object());
            equal(null,SettingsSnapshot.durableSnapshot(broken));
        }
        Storage future=new Storage();future.backup.values.putAll(untouched);future.backup.values.put("_c17_snapshot_schema",2);
        equal(null,SettingsSnapshot.durableSnapshot(future));
    }
    private static void badgeMigration() {
        Bundle base=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        base.putBoolean(NativeNetworkBadgeControls.MASTER,true);
        base.putFloat(NativeNetworkBadgeControls.X,7.25f);base.putFloat(NativeNetworkBadgeControls.Y,-3.5f);
        base.putFloat(NativeNetworkBadgeControls.SCALE,123f);base.putFloat(NativeNetworkBadgeControls.WEIGHT,650f);
        base.putString(NativeNetworkBadgeControls.FONT,"custom");
        for(int schema:new int[]{1,2}) {
            Map<String,Object> old=historical(base,schema),untouched=new LinkedHashMap<>(old);
            Bundle loaded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(loaded));equal(untouched,old);
            equal(true,loaded.get(NativeNetworkBadgeControls.MASTER));
            for(int part=1;part<=2;part++) {
                equal(true,loaded.get(NativeNetworkBadgeSettings.key(part,"enabled")));
                equal(7.25f,loaded.get(NativeNetworkBadgeSettings.key(part,"offset_x")));
                equal(-3.5f,loaded.get(NativeNetworkBadgeSettings.key(part,"offset_y")));
                equal(123f,loaded.get(NativeNetworkBadgeSettings.key(part,"scale")));
                equal(650f,loaded.get(NativeNetworkBadgeSettings.key(part,"weight")));
                equal("custom",loaded.get(NativeNetworkBadgeSettings.key(part,"font")));
            }
        }
        Map<String,Object> raw=new LinkedHashMap<>();
        for(String key:new String[]{NativeNetworkBadgeControls.MASTER,NativeNetworkBadgeControls.X,NativeNetworkBadgeControls.Y,
                NativeNetworkBadgeControls.SCALE,NativeNetworkBadgeControls.WEIGHT,NativeNetworkBadgeControls.FONT})raw.put(key,base.get(key));
        raw.put(NativeNetworkBadgeSettings.key(1,"scale"),95f);
        raw.put(NativeNetworkBadgeSettings.key(2,"enabled"),false);
        Bundle split=SettingsSnapshot.fromPreferences(raw);
        equal(95f,split.get(NativeNetworkBadgeSettings.key(1,"scale")));
        equal(123f,split.get(NativeNetworkBadgeSettings.key(2,"scale")));
        equal(true,split.get(NativeNetworkBadgeSettings.key(1,"enabled")));
        equal(false,split.get(NativeNetworkBadgeSettings.key(2,"enabled")));
        Storage durable=new Storage();durable.backup.values.putAll(raw);durable.backup.values.put("_c17_snapshot_schema",1);
        Map<String,Object> unchanged=new LinkedHashMap<>(durable.backup.values);
        Bundle recovered=SettingsSnapshot.durableSnapshot(durable);
        equal(true,SettingsSnapshot.complete(recovered));equal(unchanged,durable.backup.values);
        equal(95f,recovered.get(NativeNetworkBadgeSettings.key(1,"scale")));
        equal(123f,recovered.get(NativeNetworkBadgeSettings.key(2,"scale")));
        equal(false,recovered.get(NativeNetworkBadgeSettings.key(2,"enabled")));
    }
    private static void schema3Migration() {
        Bundle current=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        current.putFloat(StatusBarSettings.DATA_OFFSET_X,-27.125f);current.putBoolean(StatusBarSettings.SAFE_MODE,true);
        current.putBoolean(ClassicTextSettings.CLOCK_MASTER,true);current.putFloat(PanelMode.CLOCK+"_scale",112.5f);
        current.putInt(PanelMode.CLOCK+"_color_light",0);current.putString(CarrierPanels.CLASSIC+"_text","");
        current.putBoolean(NativeNetworkBadgeSettings.key(1,"enabled"),false);
        current.putFloat(NativeNetworkBadgeSettings.key(2,"scale"),131.25f);
        Map<String,Object> old=historical(current,3),untouched=new LinkedHashMap<>(old);
        Bundle loaded=SettingsFrameworkMirror.decode(old);
        equal(true,SettingsSnapshot.complete(loaded));equal(untouched,old);
        for(String key:current.keySet())equal(historicalExpected(current,key),loaded.get(key));
        for(String key:Upgrade66.DEFAULTS.keySet()) {
            Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing));
            for(Object invalid:new Object[]{null,new Object()}) {
                Map<String,Object> wrong=new LinkedHashMap<>(old);wrong.put(key,invalid);equal(null,SettingsFrameworkMirror.decode(wrong));
            }
        }
        Map<String,Object> absentBatch=new LinkedHashMap<>(old);
        for(String key:Upgrade66.DEFAULTS.keySet())absentBatch.remove(key);
        equal(null,SettingsFrameworkMirror.decode(absentBatch));
        ActivationGuardPreferencesCheck.MemoryPreferences daemon=new ActivationGuardPreferencesCheck.MemoryPreferences();
        daemon.values.putAll(old);int[] providerReads={0};
        SettingsFrameworkReader reader=new SettingsFrameworkReader(()->daemon,()->{},()->false);
        Bundle boot=reader.read(false,()->{providerReads[0]++;throw new IllegalStateException("Provider must stay asleep");});
        equal(true,SettingsSnapshot.complete(boot));equal(0,providerReads[0]);equal(untouched,daemon.values);
        for(String key:current.keySet())equal(historicalExpected(current,key),boot.get(key));reader.stop();
    }
    private static void typedCurrent(Bundle current) {
        Map<String,Object> modern=wire(current,5);equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(modern)));
        for(String key:current.keySet()) {
            Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing));
            Bundle incomplete=new Bundle(current);incomplete.keySet().remove(key);equal(false,SettingsSnapshot.complete(incomplete));
            for(Object invalid:new Object[]{null,new Object()}) {
                Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(key,invalid);equal(null,SettingsFrameworkMirror.decode(wrong));
            }
            if(current.get(key) instanceof Float)for(Object invalid:new Object[]{1,1d,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
                Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(key,invalid);equal(null,SettingsFrameworkMirror.decode(wrong));
            }
            else {
                Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(key,1f);equal(null,SettingsFrameworkMirror.decode(wrong));
            }
        }
        Map<String,Object> absentBatch=new LinkedHashMap<>(modern);
        for(String key:Upgrade67.DEFAULTS.keySet())absentBatch.remove(key);
        equal(null,SettingsFrameworkMirror.decode(absentBatch));
        absentBatch=new LinkedHashMap<>(modern);
        for(String key:Upgrade66.DEFAULTS.keySet())absentBatch.remove(key);
        equal(null,SettingsFrameworkMirror.decode(absentBatch));
        for(Object invalid:new Object[]{null,0,6,4L,5L,"4","5",new Object()}) {
            Map<String,Object> wrong=new LinkedHashMap<>(modern);wrong.put(SettingsFrameworkMirror.SCHEMA,invalid);
            equal(null,SettingsFrameworkMirror.decode(wrong));
        }
    }
    private static void portableFields() throws Exception {
        Map<String,Object> stored=new LinkedHashMap<>(Upgrade66.DEFAULTS);stored.putAll(Upgrade67.DEFAULTS);
        ConfigTransfer.PreparedImport defaults=ConfigTransfer.prepare(ConfigTransfer.exportJson(stored),true);
        equal(ConfigTransfer.types().size(),defaults.count);equal(ConfigTransfer.types().keySet(),defaults.values().keySet());
        for(String key:stored.keySet()) {
            // This synthetic management row is runtime metadata, not a portable parameter.
            if("shade_wallpaper_manage".equals(key)){equal(null,ConfigTransfer.types().get(key));continue;}
            Object value=stored.get(key);
            ConfigTransfer.Type expected=value instanceof Boolean?ConfigTransfer.Type.BOOLEAN:
                    value instanceof Float?ConfigTransfer.Type.NUMBER:value instanceof Integer?ConfigTransfer.Type.COLOR:ConfigTransfer.Type.STRING;
            equal(expected,ConfigTransfer.types().get(key));equal(value,defaults.values().get(key));
        }
        for(Map.Entry<String,ConfigTransfer.Type> item:ConfigTransfer.types().entrySet()) {
            Object value=defaults.values().get(item.getKey());
            switch(item.getValue()) {
                case BOOLEAN: equal(true,value instanceof Boolean);break;
                case NUMBER: equal(true,value instanceof Float&&Float.isFinite((Float)value));break;
                case COLOR: equal(true,value instanceof Integer);break;
                default: equal(true,value instanceof String);
            }
        }
        stored.put("native_network_badge_1_hidden",true);stored.put("native_network_badge_2_hidden",false);
        for(float padding:new float[]{0f,24f,24.125f}) {
            stored.put("notification_big_clock_screen_padding",padding);
            Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(stored),true).values();
            equal(true,portable.get("native_network_badge_1_hidden"));equal(false,portable.get("native_network_badge_2_hidden"));
            equal(padding,portable.get("notification_big_clock_screen_padding"));
        }
    }
    public static void main(String[] args) throws Exception {
        Bundle current=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        current.putFloat(StatusBarSettings.CLOCK_SCALE,147.125f);
        current.putFloat(StatusBarSettings.DATA_OFFSET_X,-8.24f);current.putBoolean(StatusBarSettings.SAFE_MODE,true);
        current.putBoolean(NotificationBigClockSettings.MASTER,true);
        current.putString(IconPackRepository.LAYERS,"[]");current.putBoolean(IconPackRepository.MASTER,true);
        equal(69,Upgrade66.DEFAULTS.size());equal(5,SettingsFrameworkMirror.VERSION);
        Map<String,Object> expected67=new LinkedHashMap<>();
        expected67.put("native_network_badge_1_hidden",false);expected67.put("native_network_badge_2_hidden",false);
        expected67.put("notification_big_clock_screen_padding",24f);equal(expected67,Upgrade67.DEFAULTS);
        equal(true,Collections.disjoint(Upgrade66.DEFAULTS.keySet(),Upgrade67.DEFAULTS.keySet()));
        for(String key:Upgrade66.DEFAULTS.keySet())equal(Upgrade66.DEFAULTS.get(key),current.get(key));
        for(String key:Upgrade67.DEFAULTS.keySet())equal(Upgrade67.DEFAULTS.get(key),current.get(key));
        for(int schema:new int[]{1,2,3}) {
            Map<String,Object> old=historical(current,schema),untouched=new LinkedHashMap<>(old);
            Bundle loaded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(loaded));equal(untouched,old);
            for(String key:current.keySet())if(schema!=1||!Upgrade65.DEFAULTS.containsKey(key))equal(historicalExpected(current,key),loaded.get(key));
            if(schema<3)for(String key:Upgrade66.DEFAULTS.keySet()) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,Upgrade66.DEFAULTS.get(key));
                equal(null,SettingsFrameworkMirror.decode(mixed));
            }
            String[] additions=Upgrade67.DEFAULTS.keySet().toArray(new String[0]);
            for(int mask=1;mask<1<<additions.length;mask++) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<additions.length;i++)if((mask&(1<<i))!=0)mixed.put(additions[i],Upgrade67.DEFAULTS.get(additions[i]));
                equal(null,SettingsFrameworkMirror.decode(mixed));
            }
            for(String key:old.keySet())if(!SettingsFrameworkMirror.SCHEMA.equals(key)&&!SettingsFrameworkMirror.REVISION.equals(key)) {
                Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(key);
                equal(null,SettingsFrameworkMirror.decode(missing));
            }
            // A fresh SystemUI process can consume any historical daemon copy without launching the app/provider.
            ActivationGuardPreferencesCheck.MemoryPreferences daemon=new ActivationGuardPreferencesCheck.MemoryPreferences();
            daemon.values.putAll(old);int[] providerReads={0};
            SettingsFrameworkReader reader=new SettingsFrameworkReader(()->daemon,()->{},()->false);
            Bundle boot=reader.read(false,()->{providerReads[0]++;throw new IllegalStateException("Provider must stay asleep");});
            equal(true,SettingsSnapshot.complete(boot));equal(0,providerReads[0]);
            equal(-8.24f,boot.get(StatusBarSettings.DATA_OFFSET_X));equal(true,boot.get(StatusBarSettings.SAFE_MODE));
            equal(false,boot.get(ClassicTextSettings.CLOCK_MASTER));equal(false,boot.get(ShadeWallpaperSettings.MASTER));reader.stop();
        }
        typedCurrent(current);schema3Migration();portableFields();
        current.putBoolean(ClassicTextSettings.CLOCK_MASTER,true);current.putFloat(PanelMode.CLOCK+"_scale",112.5f);
        current.putInt(PanelMode.CLOCK+"_color_light",0);current.putString(CarrierPanels.CLASSIC+"_text","");
        current.putBoolean(NativeNetworkBadgeSettings.key(1,"hidden"),true);
        current.putFloat(NotificationBigClockSettings.SCREEN_PADDING,37.125f);
        Bundle loaded=SettingsFrameworkMirror.decode(wire(current,5));
        equal(true,loaded.get(ClassicTextSettings.CLOCK_MASTER));equal(112.5f,loaded.get(PanelMode.CLOCK+"_scale"));
        equal(0,loaded.get(PanelMode.CLOCK+"_color_light"));equal("",loaded.get(CarrierPanels.CLASSIC+"_text"));
        equal(true,loaded.get(NativeNetworkBadgeSettings.key(1,"hidden")));
        equal(false,loaded.get(NativeNetworkBadgeSettings.key(2,"hidden")));equal(37.125f,loaded.get(NotificationBigClockSettings.SCREEN_PADDING));
        rawRecovery();badgeMigration();
        System.out.println("Upgrade66Check passed "+checks+" checks");
    }
}
