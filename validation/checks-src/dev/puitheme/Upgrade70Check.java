package dev.puitheme;

import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Schema 4 boot copies migrate only with an entirely absent schema 5 addition batch. */
public final class Upgrade70Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String why){
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade70 "+checks+" "+why+": "+expected+" != "+actual);
    }
    private static Map<String,Object> wire(Bundle snapshot,int schema){
        Map<String,Object> values=new LinkedHashMap<>();for(String key:snapshot.keySet())values.put(key,snapshot.get(key));
        values.put(SettingsFrameworkMirror.SCHEMA,schema);values.put(SettingsFrameworkMirror.REVISION,7001L);return values;
    }
    private static Map<String,Object> historical(Bundle current){
        Map<String,Object> values=wire(current,4);for(String key:Upgrade70.DEFAULTS.keySet())values.remove(key);return values;
    }
    private static Object changed(String key,Object value){
        if(NativeClockMeasurement.SCALE_BASIS_VERSION.equals(key))return 1f;
        if(NativeClockMeasurement.LEGACY_SCALE_FACTOR.equals(key))return .375f;
        if(value instanceof Boolean)return !(Boolean)value;
        if(value instanceof Float)return (Float)value+1.25f;
        if(value instanceof Integer)return (Integer)value^0x00112345;
        if(value instanceof String)return value+"custom";
        throw new AssertionError("Unexpected schema 5 field type");
    }
    private static void manifest(Bundle defaults){
        Map<String,Object> required=new LinkedHashMap<>();
        required.put(BatteryControls.HIDE_CHARGE,false);required.put(FluidCloudAccent.ENABLED,false);
        required.put(LockscreenStatusBlur.ENABLED,false);required.put(LockscreenStatusBlur.RADIUS,16f);
        required.put(LockscreenStatusBlur.RANGE,56f);required.put(LockscreenStatusBlur.TRANSITION,32f);
        required.put(LockscreenStatusBlur.MASK_COLOR,0x22000000);
        required.put(StatusBarSettings.alphaKey(LockscreenStatusBlur.MASK_COLOR),true);
        required.put(SpeedPosition.SAFE_GAP_ENABLED,false);required.put(SpeedPosition.SAFE_GAP,2f);
        required.put(NativeClockMeasurement.SCALE_BASIS_VERSION,1f);required.put(NativeClockMeasurement.LEGACY_SCALE_FACTOR,0f);
        for(Map.Entry<String,Object> item:required.entrySet())equal(item.getValue(),Upgrade70.DEFAULTS.get(item.getKey()),"independent manifest "+item.getKey());
        for(Map.Entry<String,Object> item:Upgrade70.DEFAULTS.entrySet())equal(NativeClockMeasurement.SCALE_BASIS_VERSION.equals(item.getKey())?2f:item.getValue(),defaults.get(item.getKey()),"current default "+item.getKey());
        equal(true,Collections.disjoint(Upgrade70.DEFAULTS.keySet(),Upgrade66.DEFAULTS.keySet()),"code 66 batch unchanged");
        equal(true,Collections.disjoint(Upgrade70.DEFAULTS.keySet(),Upgrade67.DEFAULTS.keySet()),"code 67 batch unchanged");
    }
    public static void main(String[] args)throws Exception{
        equal(5,SettingsFrameworkMirror.VERSION,"wire schema");
        Bundle defaults=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(true,SettingsSnapshot.complete(defaults),"new complete defaults");manifest(defaults);
        Bundle current=new Bundle(defaults);current.putFloat(StatusBarSettings.CLOCK_SCALE,147.125f);
        current.putFloat(StatusBarSettings.DATA_OFFSET_X,-8.24f);current.putBoolean(StatusBarSettings.SAFE_MODE,true);
        current.putBoolean(NotificationBigClockSettings.MASTER,true);current.putFloat(NotificationBigClockSettings.SCREEN_PADDING,37.125f);
        current.putBoolean(NativeNetworkBadgeSettings.key(1,"hidden"),true);current.putFloat(NativeNetworkBadgeSettings.key(2,"scale"),131.25f);
        Map<String,Object> old=historical(current),unchanged=new LinkedHashMap<>(old);
        Bundle migrated=SettingsFrameworkMirror.decode(old);equal(true,SettingsSnapshot.complete(migrated),"complete schema 4 is migrated");
        equal(unchanged,old,"migration never mutates source wire map");
        for(String key:current.keySet())if(!Upgrade70.DEFAULTS.containsKey(key))equal(current.get(key),migrated.get(key),"historical value "+key);
        for(String key:Upgrade70.DEFAULTS.keySet())equal(Upgrade70.DEFAULTS.get(key),migrated.get(key),"schema 4 migration default "+key);
        Map<String,Object> oldPreferences=new LinkedHashMap<>();oldPreferences.put(StatusBarSettings.CLOCK_SCALE,650.25f);
        Bundle oldSnapshot=SettingsSnapshot.fromPreferences(oldPreferences);
        equal(1f,oldSnapshot.get(NativeClockMeasurement.SCALE_BASIS_VERSION),"unmarked app clock keeps legacy basis");
        equal(0f,oldSnapshot.get(NativeClockMeasurement.LEGACY_SCALE_FACTOR),"unmarked app clock has no guessed factor");
        equal(650.25f,oldSnapshot.get(StatusBarSettings.CLOCK_SCALE),"legacy user percentage unchanged");
        equal(1,oldPreferences.size(),"reading old preferences never persists metadata or resets scale");
        Map<String,Object> portable=ConfigTransfer.prepare(ConfigTransfer.exportJson(oldPreferences),true).values();
        equal(1f,portable.get(NativeClockMeasurement.SCALE_BASIS_VERSION),"export/import keeps unmarked legacy clock basis");
        equal(0f,portable.get(NativeClockMeasurement.LEGACY_SCALE_FACTOR),"portable legacy clock has no guessed factor");
        equal(650.25f,portable.get(StatusBarSettings.CLOCK_SCALE),"export/import preserves old user percentage");
        String[] keys=Upgrade70.DEFAULTS.keySet().toArray(new String[0]);
        // Test every non-empty combination while the release batch remains small.
        if(keys.length<=14){
            for(int mask=1;mask<(1<<keys.length);mask++){
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<keys.length;i++)if((mask&(1<<i))!=0)mixed.put(keys[i],Upgrade70.DEFAULTS.get(keys[i]));
                equal(null,SettingsFrameworkMirror.decode(mixed),"schema 4 rejects mixed batch "+mask);
            }
        }else{
            // Bound future growth while testing every key and every presence count.
            for(int count=1;count<=keys.length;count++)for(int start=0;start<keys.length;start++){
                Map<String,Object> mixed=new LinkedHashMap<>(old);
                for(int i=0;i<count;i++){String key=keys[(start+i)%keys.length];mixed.put(key,Upgrade70.DEFAULTS.get(key));}
                equal(null,SettingsFrameworkMirror.decode(mixed),"schema 4 rejects rotated batch "+count+"/"+start);
            }
        }
        Map<String,Object> modern=wire(current,5);equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(modern)),"complete schema 5 retained");
        for(String key:keys){
            Map<String,Object> missing=new LinkedHashMap<>(modern);missing.remove(key);
            equal(null,SettingsFrameworkMirror.decode(missing),"schema 5 missing "+key);
            Bundle incomplete=new Bundle(current);incomplete.keySet().remove(key);equal(false,SettingsSnapshot.complete(incomplete),"provider missing "+key);
            for(Object invalid:new Object[]{null,new Object(),"wrong-type"}){
                if(invalid instanceof String&&current.get(key) instanceof String)continue;
                Map<String,Object> broken=new LinkedHashMap<>(modern);broken.put(key,invalid);
                equal(null,SettingsFrameworkMirror.decode(broken),"schema 5 invalid "+key);
            }
            if(current.get(key) instanceof Float)for(Object invalid:new Object[]{1,1d,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){
                Map<String,Object> broken=new LinkedHashMap<>(modern);broken.put(key,invalid);
                equal(null,SettingsFrameworkMirror.decode(broken),"schema 5 nonfinite/type "+key);
            }
            Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,null);
            equal(null,SettingsFrameworkMirror.decode(mixed),"even null new key is a partial schema 4 write "+key);
        }
        Map<String,Object> stored=new LinkedHashMap<>();
        for(String key:current.keySet())stored.put(key,current.get(key));
        for(String key:keys)stored.put(key,changed(key,defaults.get(key)));
        Bundle customized=SettingsSnapshot.fromPreferences(stored);
        equal(true,SettingsSnapshot.complete(customized),"new typed values accepted");
        Bundle loaded=SettingsFrameworkMirror.decode(wire(customized,5));
        equal(true,SettingsSnapshot.complete(loaded),"new customized complete wire accepted");
        for(String key:keys){equal(stored.get(key),customized.get(key),"saved override "+key);equal(stored.get(key),loaded.get(key),"wire override "+key);}
        Map<String,Object> imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(stored),true).values();
        equal(1f,imported.get(NativeClockMeasurement.SCALE_BASIS_VERSION),"portable explicit legacy basis");
        equal(.375f,imported.get(NativeClockMeasurement.LEGACY_SCALE_FACTOR),"portable explicit calibration");
        for(String key:current.keySet())if(!Upgrade70.DEFAULTS.containsKey(key))equal(current.get(key),loaded.get(key),"custom override preserves existing "+key);
        ActivationGuardPreferencesCheck.MemoryPreferences daemon=new ActivationGuardPreferencesCheck.MemoryPreferences();daemon.values.putAll(old);
        int[] providerReads={0};SettingsFrameworkReader reader=new SettingsFrameworkReader(()->daemon,()->{},()->false);
        Bundle boot=reader.read(false,()->{providerReads[0]++;throw new IllegalStateException("Provider must stay asleep");});
        equal(true,SettingsSnapshot.complete(boot),"old framework copy works on cold boot");equal(0,providerReads[0],"cold boot does not need app provider");
        equal(unchanged,daemon.values,"reader doesn't rewrite historical framework state");
        for(String key:current.keySet())if(!Upgrade70.DEFAULTS.containsKey(key))equal(current.get(key),boot.get(key),"boot historical value "+key);
        reader.stop();System.out.println("Upgrade70Check: "+checks+" checks passed");
    }
}
