package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Published font weights must survive all persistence boundaries independently. */
public final class Upgrade177Check {
    private static int checks;
    private static final String LEFT="notification_big_clock_widget_left_weight",RIGHT="notification_big_clock_widget_right_weight";
    private static final String[] KEYS={LEFT,RIGHT};
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade177 "+checks+" "+why+": "+expected+" != "+actual);}
    private static Map<String,Object> additions(){Map<String,Object> values=new LinkedHashMap<>();values.put(LEFT,400f);values.put(RIGHT,400f);return values;}
    private static Map<String,Object> values(Bundle bundle){Map<String,Object> values=new LinkedHashMap<>();for(String key:bundle.keySet())values.put(key,bundle.get(key));return values;}
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception {
        Method method=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);method.setAccessible(true);
        return (Map<String,Object>)method.invoke(null,full,schema);
    }
    private static String document(String key,String raw){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+key+"\":"+raw+"}}";}
    private static void registration(Bundle full) {
        equal(52,SettingsFrameworkMirror.VERSION,"current mirror schema");equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON unchanged");
        equal(1000,full.keySet().size(),"complete settings count");equal(987,ConfigTransfer.types().size(),"portable count");
        equal(additions(),Upgrade177.DEFAULTS,"exact two Float400 additions");equal(LEFT,BigClockWidgetsSettings.LEFT_WEIGHT,"left protocol");equal(RIGHT,BigClockWidgetsSettings.RIGHT_WEIGHT,"right protocol");
        equal(400f,BigClockWidgetsSettings.DEFAULT_WEIGHT,"default weight");equal(100f,BigClockWidgetsSettings.MIN_WEIGHT,"minimum");equal(1000f,BigClockWidgetsSettings.MAX_WEIGHT,"maximum");
        equal(18,Upgrade169.DEFAULTS.size(),"169 batch frozen");equal(23,Upgrade171.DEFAULTS.size(),"171 batch frozen");equal(6,Upgrade173.DEFAULTS.size(),"173 batch frozen");equal(1,Upgrade174.DEFAULTS.size(),"174 batch frozen");equal(4,Upgrade175.DEFAULTS.size(),"175 batch frozen");
        for(String key:KEYS){equal(400f,full.get(key),"fresh effective default");equal(400f,StatusBarSettings.NUMERIC_DEFAULTS.get(key),"registered Float default");equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(key),"portable number");
            equal(false,Upgrade169.DEFAULTS.containsKey(key),"not retroactively added to169");equal(false,Upgrade171.DEFAULTS.containsKey(key),"not retroactively added to171");equal(false,Upgrade173.DEFAULTS.containsKey(key),"not retroactively added to173");equal(false,Upgrade174.DEFAULTS.containsKey(key),"not retroactively added to174");equal(false,Upgrade175.DEFAULTS.containsKey(key),"not retroactively added to175");}
        for(String key:Upgrade175.DEFAULTS.keySet()){equal(true,full.get(key),"retired parent default stays wire compatible");equal(null,SettingsCatalog.item(key),"retired parent stays hidden");}
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(LEFT,687.5f);Bundle chosen=SettingsSnapshot.fromPreferences(saved);
        equal(687.5f,chosen.get(LEFT),"explicit effective left weight retained");equal(400f,chosen.get(RIGHT),"missing effective right defaults independently");equal(1,saved.size(),"snapshot does not seed defaults into source preferences");
        equal(14L,RootPerformanceModule.VERSION_CODE,"root companion current version (code14: the five Volume Up rescue command)");
        Map<String,String> oldModule=new LinkedHashMap<>();oldModule.put("installed","1");oldModule.put("module_version","2.0");
        oldModule.put("module_version_code","11");oldModule.put("rescue_enabled","1");oldModule.put("rescue_state","idle");
        oldModule.put("rescue_error","none");oldModule.put("rescue_time","0");
        RootPerformanceModule.Snapshot legacy=new RootPerformanceModule.Snapshot(oldModule,true);
        equal(11L,legacy.moduleVersionCode,"published companion 11 remains readable");equal("2.0",legacy.moduleVersion,"published companion version retained");
        equal(true,legacy.outdated,"published companion 11 requires the explicit code14 upgrade");
        equal(false,legacy.rescueAvailable,"old module cannot claim boot-key recovery even with matching status fields");
        oldModule.put("module_version","2.1");oldModule.put("module_version_code","12");
        RootPerformanceModule.Snapshot previous=new RootPerformanceModule.Snapshot(oldModule,true);
        equal(true,previous.outdated,"published companion 12 receives the explicit UI upgrade");
        equal(true,previous.rescueAvailable,"published companion 12 recovery status remains readable");
    }
    private static void mirrors(Bundle full)throws Exception {
        for(int schema=1;schema<43;schema++) {
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete old mirror upgrades: "+schema);equal(before,old,"decoding never mutates wire");
            for(String key:KEYS){equal(false,old.containsKey(key),"historical wire omits future weight");equal(400f,decoded.get(key),"historical mirror gains default400");
                for(Object forged:new Object[]{400f,650f,400,400d,true,"400",null,Float.NaN}){Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,forged);equal(null,SettingsFrameworkMirror.decode(mixed),"historical schema rejects even present-null future weight");}}
            if(schema==42)equal(957,old.size()-2,"published175 count frozen");if(schema==41)equal(953,old.size()-2,"published174 count frozen");if(schema==40)equal(952,old.size()-2,"published173 count frozen");
        }
        Map<String,Object> current=wire(full,43);equal(961,current.size(),"current wire and two metadata fields");equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"complete43 accepted");
        for(float left:new float[]{100f,400f,687.5f,1000f})for(float right:new float[]{100f,400f,912.5f,1000f}){
            Map<String,Object> saved=new LinkedHashMap<>(current);saved.put(LEFT,left);saved.put(RIGHT,right);Bundle decoded=SettingsFrameworkMirror.decode(saved);
            equal(true,SettingsSnapshot.complete(decoded),"independent current weights complete");equal(left,decoded.get(LEFT),"left preserved");equal(right,decoded.get(RIGHT),"right preserved");}
        for(String key:KEYS){Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing),"43 cannot omit weight");
            for(Object invalid:new Object[]{null,400,400L,400d,true,"400",Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){Map<String,Object> wrong=new LinkedHashMap<>(current);wrong.put(key,invalid);equal(null,SettingsFrameworkMirror.decode(wrong),"43 requires finite Float");}}
        current.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(current),"future mirror rejected");
    }
    private static Map<String,Object> siblings(){Map<String,Object> values=new LinkedHashMap<>();values.put(BigClockWidgetsSettings.LEFT_TEXT,"（天气）");values.put(BigClockWidgetsSettings.RIGHT_TEXT,"（电池）");values.put(BigClockWidgetsSettings.SWAP_WITH_DATE,true);values.put(NotificationBigClockSettings.DATE_WEIGHT,550f);values.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,0f);values.put(FeatureGroups.LAYOUT,false);values.put("private_note","keep");return values;}
    private static void migration(){
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade177.migrate(store);equal(0,store.writes,"empty XML stays empty");equal(Collections.emptyMap(),store.values,"no defaults written on reset");
        store.values.putAll(siblings());Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade177.migrate(store);equal(1,store.writes,"both missing weights use one durable write");for(String key:KEYS)equal(400f,store.values.get(key),"missing XML weight400");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"every saved sibling preserved");Upgrade177.migrate(store);equal(1,store.writes,"migration idempotent");
        for(Object present:new Object[]{100f,652.5f,1000f,null,400,Float.NaN,"invalid"}){for(String key:KEYS)store.values.put(key,present);before=new LinkedHashMap<>(store.values);Upgrade177.migrate(store);equal(before,store.values,"any present XML is not rewritten");equal(1,store.writes,"present entries produce no write");}
        store.values.clear();store.values.putAll(siblings());store.values.put(LEFT,725f);before=new LinkedHashMap<>(store.values);Upgrade177.migrate(store);equal(2,store.writes,"partial XML adds only missing side");equal(725f,store.values.get(LEFT),"explicit side retained");equal(400f,store.values.get(RIGHT),"absent side400");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"partial migration preserves siblings");
    }
    private static void imports(Bundle full)throws Exception {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(siblings());store.values.put(LEFT,500f);store.values.put(RIGHT,800f);
        for(String key:KEYS)for(String raw:new String[]{"100","400","575.5","1000"}){Map<String,Object> before=new LinkedHashMap<>(store.values);ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(document(key,raw),true);equal(1,prepared.count,"partial weight import stays partial");equal(true,ConfigTransfer.commit(store,prepared),"partial weight import commits");equal(Float.valueOf(raw),store.values.get(key),"numeric value retained");for(String other:before.keySet())if(!key.equals(other))equal(before.get(other),store.values.get(other),"weight import leaves sibling/group choices unchanged");}
        Map<String,Object> before=new LinkedHashMap<>(store.values);ConfigTransfer.PreparedImport text=ConfigTransfer.prepare(document(BigClockWidgetsSettings.LEFT_TEXT,"\"天气（天气）\""),true);equal(1,text.count,"legacy partial remains one key");for(String key:KEYS)equal(false,text.values().containsKey(key),"legacy partial does not invent weight");equal(true,ConfigTransfer.commit(store,text),"legacy partial commits");for(String key:before.keySet())if(!BigClockWidgetsSettings.LEFT_TEXT.equals(key))equal(before.get(key),store.values.get(key),"legacy partial preserves weights and siblings");
        for(String key:KEYS)for(String raw:new String[]{"null","true","\"400\"","[]","{}","99.99999999999999999999","1000.00000000000000000001","1e309"}){checks++;try{ConfigTransfer.prepare(document(key,raw),true);throw new AssertionError("accepted invalid weight "+raw);}catch(IOException expected){}}
        Map<String,Object> saved=values(full);saved.putAll(siblings());saved.remove("private_note");saved.put(LEFT,375.25f);saved.put(RIGHT,950.5f);Map<String,Object> sourceBefore=new LinkedHashMap<>(saved);
        ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);equal(987,prepared.count,"full portable count");equal(sourceBefore,saved,"export leaves source unchanged");equal(375.25f,prepared.values().get(LEFT),"full left exact");equal(950.5f,prepared.values().get(RIGHT),"full right exact");
        ActivationGuardPreferencesCheck.MemoryPreferences imported=new ActivationGuardPreferencesCheck.MemoryPreferences();equal(true,ConfigTransfer.commit(imported,prepared),"full local import commits");for(Map.Entry<String,Object> item:prepared.values().entrySet())equal(item.getValue(),imported.values.get(item.getKey()),"full import lossless");Upgrade177.migrate(imported);equal(375.25f,imported.values.get(LEFT),"postimport migration preserves left");equal(950.5f,imported.values.get(RIGHT),"postimport migration preserves right");
        String future=ConfigTransfer.exportJson(saved).replace("\"schema\": 1","\"schema\": 2").replace("\"schema\":1","\"schema\":2");checks++;try{ConfigTransfer.prepare(future,true);throw new AssertionError("accepted future portable schema");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception{Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());registration(full);mirrors(full);migration();imports(full);System.out.println("Upgrade177Check: "+checks+" checks passed");}
}
