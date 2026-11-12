package dev.puitheme;
import android.os.Bundle;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/** The optional row swap is one new Boolean; all 173 tint choices remain independent. */
public final class Upgrade174Check {
    private static int checks;
    private static final String KEY="notification_big_clock_widget_swap_with_date";
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(why+": "+expected+" != "+actual);}
    private static String document(String raw){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+KEY+"\":"+raw+"}}";}
    @SuppressWarnings("unchecked") private static Map<String,Object> wire(Bundle full,int schema)throws Exception {
        Method method=Upgrade173Check.class.getDeclaredMethod("wire",Bundle.class,Integer.TYPE);method.setAccessible(true);
        return (Map<String,Object>)method.invoke(null,full,schema);
    }
    public static void main(String[] args)throws Exception {
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(KEY,BigClockWidgetsSettings.SWAP_WITH_DATE,"stable portrait key");
        equal(Collections.<String,Object>singletonMap(KEY,false),Upgrade174.DEFAULTS,"singleton frozen batch");
        equal(52,SettingsFrameworkMirror.VERSION,"new mirror schema");equal(1,ConfigTransfer.SCHEMA_VERSION,"JSON unchanged");
        equal(1000,full.keySet().size(),"full count");equal(987,ConfigTransfer.types().size(),"portable count");
        equal(false,full.get(KEY),"fresh layout keeps its current order");equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(KEY),"portable Boolean");
        equal(6,Upgrade173.DEFAULTS.size(),"six 173 strengths stay frozen");
        equal(false,NotificationBigClockSettings.BOOLEANS.containsKey(KEY),"not duplicated into landscape");
        equal(false,full.containsKey(NotificationBigClockSettings.landscapeKey(KEY)),"no landscape swap key");
        for(int schema=1;schema<=40;schema++) {
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete historical mirror "+schema);equal(false,decoded.get(KEY),"old mirror keeps old order");equal(before,old,"old mirror input immutable");
            Map<String,Object> forged=new LinkedHashMap<>(old);forged.put(KEY,null);equal(null,SettingsFrameworkMirror.decode(forged),"historical schema rejects future key even null");
            if(schema==40)equal(952,old.size()-2,"published schema40 frozen count");
        }
        Map<String,Object> current=wire(full,41);equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"new complete mirror accepted");
        for(Object value:new Object[]{true,false}){Map<String,Object> saved=new LinkedHashMap<>(current);saved.put(KEY,value);equal(value,SettingsFrameworkMirror.decode(saved).get(KEY),"current explicit value retained");}
        for(Object value:new Object[]{null,0,0f,"true"}){Map<String,Object> saved=new LinkedHashMap<>(current);saved.put(KEY,value);equal(null,SettingsFrameworkMirror.decode(saved),"current requires Boolean");}
        current.remove(KEY);equal(null,SettingsFrameworkMirror.decode(current),"current cannot omit singleton");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade174.migrate(store);equal(0,store.writes,"empty store untouched");
        store.values.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,0f);
        int index=0;for(String key:Upgrade173.DEFAULTS.keySet())store.values.put(key,(float)index++);
        store.values.put(NotificationBigClockSettings.DATE_OFFSET_Y,-25f);store.values.put(BigClockWidgetsSettings.LEFT_TEXT,"（天气）");
        Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade174.migrate(store);equal(1,store.writes,"one durable migration");equal(false,store.values.get(KEY),"old XML gains disabled swap only");
        for(String key:before.keySet())equal(before.get(key),store.values.get(key),"existing 173 strengths, text and offsets stay unchanged");Upgrade174.migrate(store);equal(1,store.writes,"idempotent");
        for(Object value:new Object[]{true,false,null,0,"invalid"}){store.values.put(KEY,value);before=new LinkedHashMap<>(store.values);Upgrade174.migrate(store);equal(before,store.values,"any present choice survives migration");}
        for(boolean enabled:new boolean[]{false,true}){ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(document(Boolean.toString(enabled)),true);equal(1,prepared.count,"partial import stays partial");equal(true,ConfigTransfer.commit(store,prepared),"merge commits");equal(enabled,store.values.get(KEY),"explicit choice survives JSON");}
        for(String bad:new String[]{"null","0","1","\"true\"","[]","{}"}){checks++;try{ConfigTransfer.prepare(document(bad),true);throw new AssertionError("accepted non Boolean");}catch(IOException expected){}}
        equal(987,ConfigTransfer.prepare(ConfigTransfer.exportJson(store.values),true).count,"full portable export");
        SettingsCatalog.Item item=SettingsCatalog.item(KEY);equal(true,item!=null,"reachable editor");equal("与日期互换位置",item.title,"clear action");equal("组件布局",item.section,"component page layout group");equal(false,item.defaultValue,"UI default off");equal(true,item.description.contains("空间不足"),"bounded fallback explained");
        Map<String,Object> values=new LinkedHashMap<>();values.put(NotificationBigClockSettings.DATE_ENABLED,false);values.put(BigClockWidgetsSettings.LEFT_ENABLED,true);values.put(KEY,true);
        equal("请先开启有效日期",SettingsCatalog.unavailableReason(KEY,values),"date dependency");values.put(NotificationBigClockSettings.DATE_ENABLED,true);values.put(BigClockWidgetsSettings.LEFT_ENABLED,false);
        equal("请先开启至少一侧组件",SettingsCatalog.unavailableReason(KEY,values),"component dependency");values.put(BigClockWidgetsSettings.RIGHT_ENABLED,true);equal("",SettingsCatalog.unavailableReason(KEY,values),"either side enables editor");
        values.put(NotificationBigClockSettings.DATE_SIZE,0f);equal("请先开启有效日期",SettingsCatalog.unavailableReason(KEY,values),"zero size cannot enable swap");equal(true,values.get(KEY),"dependencies never rewrite saved choice");
        System.out.println("Upgrade174Check: "+checks+" checks passed");
    }
}
