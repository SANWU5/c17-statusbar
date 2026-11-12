package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Scope defaults preserve old coverage; disabled categories remain independent across every persistence boundary. */
public final class Upgrade185Check {
    private static int checks;
    private static final String[] IDS={"media","ride_hailing","food_delivery","parcel_pickup","food_pickup","timer","alarm","navigation","travel","battery","earphones","smart_suggestions","other"};
    private static String key(String id){return "fluid_cloud_scope_"+id+"_enabled";}
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade185 "+checks+" "+why+": "+expected+" != "+actual);}
    private static Map<String,Object> additions(){Map<String,Object> values=new LinkedHashMap<>();for(String id:IDS)values.put(key(id),true);return values;}
    private static Map<String,Object> values(Bundle bundle){Map<String,Object> values=new LinkedHashMap<>();for(String key:bundle.keySet())values.put(key,bundle.get(key));return values;}
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception{
        Method method=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);method.setAccessible(true);
        return (Map<String,Object>)method.invoke(null,full,schema);
    }
    private static String document(String key,String raw){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+key+"\":"+raw+"}}";}
    private static void registration(Bundle full){
        equal(52,SettingsFrameworkMirror.VERSION,"mirror schema");equal(1,ConfigTransfer.SCHEMA_VERSION,"portable JSON unchanged");
        equal(1000, full.keySet().size(),"complete setting count");equal(987,ConfigTransfer.types().size(),"portable setting count");
        equal(additions(),Upgrade185.DEFAULTS,"exact frozen thirteen Boolean additions");equal(additions(),FluidCloudScope.BOOLEANS,"runtime default registry matches migration");
        equal(false,FluidCloudScope.isScopeKey(null),"null is not a scope key");equal(false,FluidCloudScope.isScopeKey("fluid_cloud_scope_unknown_enabled"),"unpublished key is not registered");
        for(String id:IDS){String key=key(id);
            equal(true,full.get(key),"fresh effective default keeps category enabled");equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(key),"Boolean registered");
            equal(true,FluidCloudScope.isScopeKey(key),"scope key recognized");equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key),"portable Boolean");
            for(Map<String,Object> older:new Map[]{Upgrade169.DEFAULTS,Upgrade171.DEFAULTS,Upgrade172.DEFAULTS,Upgrade173.DEFAULTS,Upgrade174.DEFAULTS,Upgrade175.DEFAULTS,Upgrade177.DEFAULTS,Upgrade184.DEFAULTS})
                equal(false,older.containsKey(key),"published earlier batch remains frozen");
            Map<String,Object> saved=new LinkedHashMap<>();saved.put(key,false);equal(false,SettingsSnapshot.fromPreferences(saved).get(key),"explicit opt-out retained");equal(1,saved.size(),"reading never writes defaults to source");
        }
    }
    private static void mirrors(Bundle full)throws Exception{
        for(int schema=1;schema<45;schema++){
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete old mirror upgrades "+schema);equal(before,old,"old mirror input immutable");
            for(String id:IDS){String key=key(id);equal(false,old.containsKey(key),"old wire omits future scope");equal(true,decoded.get(key),"old mirror preserves effective coverage");
                for(Object forged:new Object[]{true,false,null,1,1f,"true"}){Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,forged);equal(null,SettingsFrameworkMirror.decode(mixed),"old schema rejects a future key");}
            }
            if(schema==44)equal(965,old.size(),"published schema44 count frozen with metadata");
        }
        Map<String,Object> current=wire(full,45);equal(978,current.size(),"current wire includes two metadata fields");equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"complete45 accepted");
        for(String id:IDS){String key=key(id);
            for(boolean enabled:new boolean[]{true,false}){Map<String,Object> saved=new LinkedHashMap<>(current);saved.put(key,enabled);Bundle decoded=SettingsFrameworkMirror.decode(saved);equal(true,SettingsSnapshot.complete(decoded),"single category choice complete");equal(enabled,decoded.get(key),"single category choice retained");}
            Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing),"schema45 requires every scope");
            for(Object wrong:new Object[]{null,0,1L,0f,"false"}){Map<String,Object> invalid=new LinkedHashMap<>(current);invalid.put(key,wrong);equal(null,SettingsFrameworkMirror.decode(invalid),"schema45 scope requires Boolean");}
        }
        current.put(SettingsFrameworkMirror.SCHEMA,46);equal(null,SettingsFrameworkMirror.decode(current),"future mirror rejected");
    }
    private static Map<String,Object> siblings(){Map<String,Object> values=new LinkedHashMap<>();values.put(FluidCloudStyle.MASTER,true);values.put(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE,9.41f);values.put("private_note","keep");return values;}
    private static void migration(){
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade185.migrate(store);equal(0,store.writes,"empty XML stays empty");equal(Collections.emptyMap(),store.values,"empty reset is not populated");
        store.values.putAll(siblings());Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade185.migrate(store);equal(1,store.writes,"missing scopes use one durable write");
        for(String id:IDS)equal(true,store.values.get(key(id)),"old XML keeps category enabled");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"existing sibling retained");
        Upgrade185.migrate(store);equal(1,store.writes,"migration idempotent");
        for(Object present:new Object[]{true,false,null,0,"invalid"}){for(String id:IDS)store.values.put(key(id),present);before=new LinkedHashMap<>(store.values);Upgrade185.migrate(store);equal(before,store.values,"present XML values are never rewritten");equal(1,store.writes,"present entries need no write");}
        store.values.clear();store.values.putAll(siblings());store.values.put(key("timer"),false);before=new LinkedHashMap<>(store.values);Upgrade185.migrate(store);equal(2,store.writes,"partial XML adds missing scopes once");
        equal(false,store.values.get(key("timer")),"disabled category survives migration");equal(true,store.values.get(key("other")),"unknown cards preserve previous coverage");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"partial migration preserves saved values");
    }
    private static void imports(Bundle full)throws Exception{
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(siblings());for(String id:IDS)store.values.put(key(id),false);
        for(String id:IDS)for(boolean enabled:new boolean[]{true,false}){
            String key=key(id);Map<String,Object> before=new LinkedHashMap<>(store.values);ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(document(key,Boolean.toString(enabled)),true);
            equal(1,prepared.count,"scope import stays partial");equal(true,ConfigTransfer.commit(store,prepared),"scope Boolean import commits");equal(enabled,store.values.get(key),"explicit category imported");
            for(String other:before.keySet())if(!key.equals(other))equal(before.get(other),store.values.get(other),"scope import preserves other choices");
        }
        ConfigTransfer.PreparedImport oldPartial=ConfigTransfer.prepare(document(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE,"60"),true);equal(1,oldPartial.count,"old partial JSON stays partial");
        for(String id:IDS)equal(false,oldPartial.values().containsKey(key(id)),"old import never invents scopes");equal(true,ConfigTransfer.commit(store,oldPartial),"old partial import commits");
        Upgrade185.migrate(store);for(String id:IDS)equal(false,store.values.get(key(id)),"old import and migration preserve category opt-outs");
        for(String id:IDS)for(String raw:new String[]{"null","0","1","\"true\"","[]","{}"}){checks++;try{ConfigTransfer.prepare(document(key(id),raw),true);throw new AssertionError("accepted non-Boolean scope");}catch(IOException expected){}}
        Map<String,Object> saved=values(full);for(String id:IDS)saved.put(key(id),false);Map<String,Object> before=new LinkedHashMap<>(saved);
        ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true);equal(987,prepared.count,"full portable count");equal(before,saved,"export input immutable");
        for(String id:IDS)equal(false,prepared.values().get(key(id)),"full export retains opt-out");
    }
    private static void editor(){
        SettingsCatalog.Group group=SettingsCatalog.group("fluid_cloud_style");SettingsCatalog.DetailPage scope=null;
        for(SettingsCatalog.DetailPage page:SettingsCatalog.detailPages(group))if("scope".equals(page.id))scope=page;
        equal(true,scope!=null,"scope subpage reachable");equal("适配范围",scope.title,"scope subpage title");equal(13,scope.items.size(),"each published category has a switch");
        Map<String,Object> values=new LinkedHashMap<>();for(String id:IDS){String key=key(id);SettingsCatalog.Item item=SettingsCatalog.item(key);
            equal(true,scope.items.contains(item),"scope item belongs to subpage");equal(group.id,item.groupId,"scope shares existing configuration owner");equal(SettingsCatalog.BOOLEAN,item.type,"Boolean editor");equal(true,item.defaultValue,"UI preserves old coverage");
            equal("请先开启流体云样式",SettingsCatalog.unavailableReason(key,values),"scope depends on style master");values.put(FluidCloudStyle.MASTER,true);equal("",SettingsCatalog.unavailableReason(key,values),"style master enables scope editing");values.clear();
            equal(true,ConfigScope.section(group.id).keys().contains(key),"partial sharing includes category choice");
        }
        equal("其他与未识别",SettingsCatalog.item(key("other")).title,"unknown category remains visible");
    }
    public static void main(String[] args)throws Exception{Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());registration(full);mirrors(full);migration();imports(full);editor();System.out.println("Upgrade185Check: "+checks+" checks passed");}
}
