package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Four neutral parents extend schema 42 without rewriting any saved child setting. */
public final class Upgrade175Check {
    private static int checks;
    private static final String[] KEYS={"statusbar_layout_adjustments_enabled","notification_stack_effects_enabled",
            "notification_clear_controls_enabled","lock_notification_effects_enabled"};
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade175 "+checks+" "+why+": "+expected+" != "+actual);}
    private static Map<String,Object> additions(){Map<String,Object> result=new LinkedHashMap<>();for(String key:KEYS)result.put(key,true);return result;}
    private static Map<String,Object> values(Bundle bundle){Map<String,Object> result=new LinkedHashMap<>();for(String key:bundle.keySet())result.put(key,bundle.get(key));return result;}
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception{
        Method method=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);method.setAccessible(true);
        return (Map<String,Object>)method.invoke(null,full,schema);
    }
    private static String document(String key,String raw){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+key+"\":"+raw+"}}";}
    private static void registration(Bundle full){
        equal(52,SettingsFrameworkMirror.VERSION,"current wire schema");equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON stays schema1");
        equal(1000,full.keySet().size(),"complete snapshot");equal(987,ConfigTransfer.types().size(),"portable snapshot");
        equal(additions(),Upgrade175.DEFAULTS,"exact frozen four Boolean defaults");equal(additions(),FeatureGroups.BOOLEANS,"parent registry matches frozen batch");
        equal(KEYS[0],FeatureGroups.LAYOUT,"layout key");equal(KEYS[1],FeatureGroups.STACKS,"stack key");equal(KEYS[2],FeatureGroups.CLEAR,"clear key");equal(KEYS[3],FeatureGroups.LOCK,"lock key");
        equal(6,Upgrade173.DEFAULTS.size(),"published173 batch remains frozen");equal(1,Upgrade174.DEFAULTS.size(),"published174 batch remains frozen");
        for(String key:KEYS){equal(true,full.get(key),"fresh defaults preserve historical enabled behavior");equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(key),"registered Boolean true");equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key),"portable Boolean");equal(null,SettingsCatalog.item(key),"retired parent remains hidden while its published wire field stays compatible");}
    }
    private static void mirrors(Bundle full)throws Exception{
        for(int schema=1;schema<=41;schema++){
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"historical mirror complete "+schema);equal(before,old,"decode leaves historical wire unchanged");
            for(String key:KEYS){equal(false,old.containsKey(key),"old schema omits future parent");equal(true,decoded.get(key),"historical behavior inherited as enabled");
                for(Object value:new Object[]{true,false,null,0,"true"}){Map<String,Object> forged=new LinkedHashMap<>(old);forged.put(key,value);equal(null,SettingsFrameworkMirror.decode(forged),"old schema rejects future key even null");}}
            if(schema==41)equal(953,old.size()-2,"published174 schema41 count frozen");
            if(schema==40)equal(952,old.size()-2,"published173 schema40 count frozen");
            if(schema==39)equal(946,old.size()-2,"published172 schema39 count frozen");
        }
        Map<String,Object> current=wire(full,42);equal(959,current.size(),"current full wire includes metadata and all parents");
        equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"complete42 accepted");
        for(int flags=0;flags<16;flags++){
            Map<String,Object> saved=new LinkedHashMap<>(current);for(int index=0;index<KEYS.length;index++)saved.put(KEYS[index],(flags&(1<<index))!=0);
            Bundle decoded=SettingsFrameworkMirror.decode(saved);equal(true,SettingsSnapshot.complete(decoded),"all explicit parent combinations accepted");
            for(String key:KEYS)equal(saved.get(key),decoded.get(key),"current explicit parent choice retained");
        }
        for(String key:KEYS){Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing),"current mirror cannot omit any parent");
            for(Object value:new Object[]{null,0,1,0f,"true"}){Map<String,Object> invalid=new LinkedHashMap<>(current);invalid.put(key,value);equal(null,SettingsFrameworkMirror.decode(invalid),"current mirror requires Boolean");}}
        current.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION+1);equal(null,SettingsFrameworkMirror.decode(current),"future wire schema rejected");
    }
    private static Map<String,Object> children(){
        Map<String,Object> result=new LinkedHashMap<>();result.put(StatusBarLayoutSettings.MASTER,true);result.put(StatusBarSettings.CLOCK_OFFSET_X,17f);
        result.put(NotificationBigClockSettings.STACK_ENABLED,true);result.put(NotificationMiniStack.ENABLED,false);
        result.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,0f);result.put(BigClockWidgetsSettings.LEFT_TEXT,"（天气）");
        result.put(BigClockWidgetsSettings.SWAP_WITH_DATE,true);result.put(FluidCloudStyle.LOCK_CAPSULE,true);
        result.put(NotificationClearAppearance.MASTER,false);result.put("private_note","preserve");return result;
    }
    private static void migration(){
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade175.migrate(store);equal(0,store.writes,"empty store remains empty");
        store.values.putAll(children());Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade175.migrate(store);
        equal(1,store.writes,"missing parents use one durable write");for(String key:KEYS)equal(true,store.values.get(key),"legacy saved settings gain neutral true parent");
        for(String key:before.keySet())equal(before.get(key),store.values.get(key),"migration leaves every old choice untouched");Upgrade175.migrate(store);equal(1,store.writes,"migration idempotent");
        for(Object explicit:new Object[]{false,true,null,0,"invalid"}){
            for(String key:KEYS)store.values.put(key,explicit);before=new LinkedHashMap<>(store.values);Upgrade175.migrate(store);
            equal(before,store.values,"every present parent value preserved by migration");equal(1,store.writes,"present parent never rewritten");
        }
        store.values.clear();store.values.putAll(children());store.values.put(KEYS[0],false);store.values.put(KEYS[2],true);before=new LinkedHashMap<>(store.values);Upgrade175.migrate(store);
        equal(2,store.writes,"partly migrated XML writes only missing defaults");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"partly migrated choices retained");
        equal(true,store.values.get(KEYS[1]),"missing stack parent true");equal(true,store.values.get(KEYS[3]),"missing lock parent true");
    }
    private static void imports(Bundle full)throws Exception{
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(children());
        for(String key:KEYS)store.values.put(key,false);
        Map<String,Object> before=new LinkedHashMap<>(store.values);
        ConfigTransfer.PreparedImport oldPartial=ConfigTransfer.prepare(document(BigClockWidgetsSettings.LEFT_TEXT,"\"文字（天气）\""),true);
        equal(1,oldPartial.count,"old partial JSON stays partial");for(String key:KEYS)equal(false,oldPartial.values().containsKey(key),"partial import does not invent parents");
        equal(true,ConfigTransfer.commit(store,oldPartial),"partial child import commits");for(String key:KEYS)equal(false,store.values.get(key),"old partial cannot reenable saved parent");
        for(String key:before.keySet())if(!BigClockWidgetsSettings.LEFT_TEXT.equals(key))equal(before.get(key),store.values.get(key),"partial child leaves other saved choices intact");
        for(String key:KEYS)for(boolean enabled:new boolean[]{true,false}){
            Map<String,Object> childrenBefore=new LinkedHashMap<>(store.values);ConfigTransfer.PreparedImport part=ConfigTransfer.prepare(document(key,Boolean.toString(enabled)),true);
            equal(1,part.count,"parent import stays one key");equal(true,ConfigTransfer.commit(store,part),"parent Boolean import commits");equal(enabled,store.values.get(key),"explicit parent imported");
            for(String other:childrenBefore.keySet())if(!key.equals(other))equal(childrenBefore.get(other),store.values.get(other),"parent import does not rewrite children or siblings");
        }
        for(String key:KEYS)for(String raw:new String[]{"null","0","1","\"true\"","[]","{}"}){
            checks++;try{ConfigTransfer.prepare(document(key,raw),true);throw new AssertionError("accepted non-Boolean parent");}catch(IOException expected){}
        }
        Map<String,Object> saved=values(full);saved.putAll(children());saved.remove("private_note");for(String key:KEYS)saved.put(key,false);
        ConfigTransfer.PreparedImport complete=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);equal(987,complete.count,"complete local export keeps all portable fields");
        for(String key:KEYS)equal(false,complete.values().get(key),"full export carries explicit disabled parents");
        Map<String,Object> exportBefore=new LinkedHashMap<>(saved);ActivationGuardPreferencesCheck.MemoryPreferences local=new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true,ConfigTransfer.commit(local,complete),"full local import commits");equal(exportBefore,saved,"export does not mutate source");
        for(Map.Entry<String,Object> item:complete.values().entrySet())equal(item.getValue(),local.values.get(item.getKey()),"full import lossless for every portable key");
        Upgrade175.migrate(local);for(String key:KEYS)equal(false,local.values.get(key),"post-import migration preserves disabled parent");
        String future=ConfigTransfer.exportJson(saved).replace("\"schema\": 1","\"schema\": 2").replace("\"schema\":1","\"schema\":2");
        checks++;try{ConfigTransfer.prepare(future,true);throw new AssertionError("accepted future portable schema");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception{Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());registration(full);mirrors(full);migration();imports(full);System.out.println("Upgrade175Check: "+checks+" checks passed");}
}
