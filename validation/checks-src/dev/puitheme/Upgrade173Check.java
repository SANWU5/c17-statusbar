package dev.puitheme;
import android.os.Bundle;
import java.io.IOException;
import java.util.*;

/** Independent tint strengths preserve old shared values through every supported settings boundary. */
public final class Upgrade173Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade173 "+checks+" "+why+": "+expected+" != "+actual);}
    private static String direction(String key,boolean landscape){return landscape?NotificationBigClockSettings.landscapeKey(key):key;}
    private static Map<String,Object> additions(){
        Map<String,Object> result=new LinkedHashMap<>();
        for(boolean landscape:new boolean[]{false,true})for(String suffix:new String[]{"date","footer"})
            result.put("notification_big_clock_"+(landscape?"landscape_":"")+"backdrop_"+suffix+"_mask_opacity",20f);
        result.put("notification_big_clock_widget_left_mask_opacity",20f);result.put("notification_big_clock_widget_right_mask_opacity",20f);return result;
    }
    private static Map<String,Object> saved(){Map<String,Object> result=new LinkedHashMap<>();result.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,80f);result.put(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.REVEAL_MASK_OPACITY),35f);return result;}
    private static float inherited(String key){return key.startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX)?35f:80f;}
    private static Map<String,Object> wire(Bundle full,int schema){
        Map<String,Object> result=new LinkedHashMap<>();for(String key:full.keySet())result.put(key,full.get(key));
        List<Map<String,Object>> batches=Arrays.asList(Upgrade65.DEFAULTS,Upgrade66.DEFAULTS,Upgrade67.DEFAULTS,
                Upgrade70.DEFAULTS,Upgrade76.DEFAULTS,Upgrade77.DEFAULTS,Upgrade78.DEFAULTS,Upgrade79.DEFAULTS,
                Upgrade80.DEFAULTS,Upgrade82.DEFAULTS,Upgrade85.DEFAULTS,Upgrade90.DEFAULTS,Upgrade91.DEFAULTS,
                Upgrade93.DEFAULTS,Upgrade96.DEFAULTS,Upgrade98.DEFAULTS,Upgrade99.DEFAULTS,Upgrade100.DEFAULTS,
                Upgrade101.DEFAULTS,Upgrade103.DEFAULTS,Upgrade105.DEFAULTS,Upgrade106.DEFAULTS,Upgrade107.DEFAULTS,
                Upgrade108.DEFAULTS,Upgrade117.DEFAULTS,Upgrade118.DEFAULTS,Upgrade135.DEFAULTS,Upgrade136.DEFAULTS,
                Upgrade144.DEFAULTS,Upgrade146.DEFAULTS,Upgrade154.DEFAULTS,Upgrade157.DEFAULTS,Upgrade160.DEFAULTS,
                Upgrade166.DEFAULTS,Upgrade168.DEFAULTS,Upgrade169.DEFAULTS,Upgrade171.DEFAULTS,Upgrade172.DEFAULTS,Upgrade173.DEFAULTS,Upgrade174.DEFAULTS,Upgrade175.DEFAULTS,Upgrade177.DEFAULTS,Upgrade184.DEFAULTS,Upgrade185.DEFAULTS,Upgrade186.DEFAULTS,Upgrade187.DEFAULTS,Upgrade188.DEFAULTS,Upgrade195.DEFAULTS,Upgrade196.DEFAULTS,Upgrade209.DEFAULTS,Upgrade279.DEFAULTS);
        for(int index=0;index<batches.size();index++)if(schema<index+2)for(String key:batches.get(index).keySet())result.remove(key);
        if(schema<12)result.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,173L);return result;
    }
    private static String document(String key,String value){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+key+"\":"+value+"}}";}
    private static void registration(Bundle full){
        equal(additions(),Upgrade173.DEFAULTS,"six exact published numeric keys");equal(52,SettingsFrameworkMirror.VERSION,"mirror version");equal(1,ConfigTransfer.SCHEMA_VERSION,"JSON format unchanged");
        equal(1000,full.keySet().size(),"full snapshot count");equal(987,ConfigTransfer.types().size(),"portable count");equal(946,wire(full,39).size()-2,"published schema39 unchanged");equal(945,wire(full,38).size()-2,"published schema38 unchanged");equal(23,Upgrade171.DEFAULTS.size(),"schema38 additions frozen");equal(1,Upgrade172.DEFAULTS.size(),"schema39 additions frozen");
        Bundle fresh=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        for(String key:additions().keySet()){
            equal(20f,StatusBarSettings.NUMERIC_DEFAULTS.get(key),"fresh default");equal(20f,fresh.get(key),"fresh effective default");equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(key),"portable numeric type");
            equal(inherited(key),full.get(key),"missing saved strength inherits main time");equal(false,Upgrade171.DEFAULTS.containsKey(key),"not retroactively added to published171");equal(false,Upgrade172.DEFAULTS.containsKey(key),"not retroactively added to published172");
        }
        equal("notification_big_clock_backdrop_date_mask_opacity",ClockBackdropSettings.DATE_MASK_OPACITY,"date protocol");equal("notification_big_clock_backdrop_footer_mask_opacity",ClockBackdropSettings.FOOTER_MASK_OPACITY,"footer protocol");
        for(int side:new int[]{0,1}){String key=BigClockWidgetsSettings.maskOpacityKey(side);equal(true,BigClockWidgetsSettings.maskOpacityKey(key),"widget helper recognizes key");equal(side,BigClockWidgetsSettings.textSide(key),"widget side mapping");equal(20f,BigClockWidgetsSettings.NUMBERS.get(key),"widget registration");}
    }
    private static void migration(){
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade173.migrate(store);equal(0,store.writes,"empty store stays empty");store.values.putAll(saved());store.values.put("private_note","keep");
        Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade173.migrate(store);equal(1,store.writes,"one durable write");
        for(String key:additions().keySet())equal(inherited(key),store.values.get(key),"old shared concentration preserved");for(String key:before.keySet())equal(before.get(key),store.values.get(key),"old value remains unchanged");Upgrade173.migrate(store);equal(1,store.writes,"idempotent");
        for(Object explicit:new Object[]{0f,100f,7.5f,null,"invalid",0}){for(String key:additions().keySet())store.values.put(key,explicit);before=new LinkedHashMap<>(store.values);Upgrade173.migrate(store);equal(before,store.values,"every present independent value retained");equal(1,store.writes,"no rewrite of present values");}
        for(Object invalid:new Object[]{null,"80",Float.NaN,Float.POSITIVE_INFINITY}){Map<String,Object> values=new LinkedHashMap<>();values.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,invalid);equal(20f,Upgrade173.inheritedOpacity(values,ClockBackdropSettings.DATE_MASK_OPACITY),"invalid old strength uses neutral default");}
        for(float main:new float[]{0f,100f,23.75f,-1f,101f}){Map<String,Object> values=new LinkedHashMap<>();values.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,main);equal(Math.max(0f,Math.min(100f,main)),Upgrade173.inheritedOpacity(values,BigClockWidgetsSettings.LEFT_MASK_OPACITY),"seed preserves effective bounded strength");}
    }
    private static void mirrors(Bundle full){
        for(int schema=1;schema<=39;schema++){
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);equal(true,SettingsSnapshot.complete(decoded),"complete historical wire "+schema);equal(before,old,"decode is immutable");
            for(String key:additions().keySet()){
                equal(schema>=36?inherited(key):20f,decoded.get(key),"historical strength dynamically seeded "+schema);
                for(Object forged:new Object[]{20f,null}){Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(key,forged);equal(null,SettingsFrameworkMirror.decode(mixed),"old schema rejects future key including null");}
            }
            Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(StatusBarSettings.CLOCK_OFFSET_X);equal(null,SettingsFrameworkMirror.decode(missing),"new additions cannot repair missing original field");
        }
        Map<String,Object> current=wire(full,40);equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"complete schema40 accepted");
        for(String key:additions().keySet()){
            for(float explicit:new float[]{0f,100f,7.25f}){Map<String,Object> values=new LinkedHashMap<>(current);values.put(key,explicit);equal(explicit,SettingsFrameworkMirror.decode(values).get(key),"explicit independent value survives");}
            for(Object wrong:new Object[]{null,0,0d,"20",false,Float.NaN,Float.POSITIVE_INFINITY}){Map<String,Object> bad=new LinkedHashMap<>(current);bad.put(key,wrong);equal(null,SettingsFrameworkMirror.decode(bad),"current strength requires finite Float");}
            Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(key);equal(null,SettingsFrameworkMirror.decode(missing),"current schema requires each addition");
        }
    }
    private static void editingAndImport()throws Exception{
        equal("主时间遮罩强度",SettingsCatalog.item(NotificationBigClockSettings.REVEAL_MASK_OPACITY).title,"existing main key has clear name");
        for(String key:additions().keySet()){
            SettingsCatalog.Item item=SettingsCatalog.item(key);equal(true,item!=null,"editor reachable");equal(SettingsCatalog.NUMERIC,item.type,"numeric editor");equal(20f,item.defaultValue,"editor neutral default");equal(0f,item.min,"minimum");equal(100f,item.max,"maximum");
            for(String raw:new String[]{"0","100","42.75"}){float expected=Float.parseFloat(raw);equal(expected,SettingsCatalog.customNumber(item,raw),"exact valid edit");equal(expected,ConfigTransfer.prepare(document(key,raw),true).values().get(key),"valid JSON numeric");}
            for(String raw:new String[]{"-0.00000000000001","100.00000000000001","null","true","\"20\"","[]","{}","1e1000"}){checks++;try{ConfigTransfer.prepare(document(key,raw),true);throw new AssertionError("accepted invalid strength "+raw);}catch(IOException expected){}}
            Map<String,Object> values=saved();values.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,true);values.put(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED),true);
            if(BigClockWidgetsSettings.maskOpacityKey(key)){
                int side=BigClockWidgetsSettings.textSide(key);values.put(BigClockWidgetsSettings.enabledKey(side),true);values.put(BigClockWidgetsSettings.followTimeColorKey(side),false);equal(true,SettingsCatalog.unavailableReason(key,values).contains("组件颜色跟随"),"widget follow gate");values.put(BigClockWidgetsSettings.followTimeColorKey(side),true);equal("",SettingsCatalog.unavailableReason(key,values),"widget enabled");values.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,false);equal(true,SettingsCatalog.unavailableReason(key,values).contains("竖屏"),"landscape source cannot enable portrait widget");
            }else{
                String base=NotificationBigClockSettings.portraitKey(key);boolean landscape=!key.equals(base);String follow=direction(NotificationBigClockSettings.FOLLOW_STYLE,landscape);values.put(follow,false);equal(true,SettingsCatalog.unavailableReason(key,values).contains("跟随"),"own follow gate");values.put(follow,true);equal("",SettingsCatalog.unavailableReason(key,values),"own follow enabled");values.put(direction(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,landscape),false);equal("请先开启字内背景透出",SettingsCatalog.unavailableReason(key,values),"own direction source required");
            }
        }
        Map<String,Object> source=saved(),unchanged=new LinkedHashMap<>(source);Map<String,Object> exported=ConfigTransfer.prepare(ConfigTransfer.exportJson(source),true).values();equal(987,exported.size(),"complete portable snapshot");equal(unchanged,source,"legacy export never mutates saved configuration");
        for(String key:additions().keySet()){equal(inherited(key),exported.get(key),"export missing independent field inherits main strength");equal(inherited(key),ConfigTransfer.prepare(ConfigTransfer.exportJson(source,Collections.singleton(key)),true).values().get(key),"partial export also inherits");}
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(source);int index=0;for(String key:additions().keySet())store.values.put(key,(float)index++);
        Map<String,Object> independent=new LinkedHashMap<>(store.values);ConfigTransfer.PreparedImport oldImport=ConfigTransfer.prepare(document(NotificationBigClockSettings.REVEAL_MASK_OPACITY,"65"),true);equal(1,oldImport.count,"JSON1 preparation does not invent missing fields");equal(true,ConfigTransfer.commit(store,oldImport),"existing merge import commits");equal(65f,store.values.get(NotificationBigClockSettings.REVEAL_MASK_OPACITY),"imported main strength applies");
        for(String key:additions().keySet())equal(independent.get(key),store.values.get(key),"old merge cannot overwrite independent settings");Upgrade173.migrate(store);for(String key:additions().keySet())equal(independent.get(key),store.values.get(key),"post-import migration also preserves choices");
        for(String key:additions().keySet()){ConfigTransfer.PreparedImport explicit=ConfigTransfer.prepare(document(key,"0"),true);equal(true,ConfigTransfer.commit(store,explicit),"explicit independent import");equal(0f,store.values.get(key),"explicit zero survives merge");}
        equal(true,store.values.containsKey(BigClockWidgetsSettings.LEFT_MASK_OPACITY),"widgets remain portable");
    }
    private static void retiredNotification()throws Exception {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.putAll(saved());
        for(String key:new String[]{ClockBackdropSettings.NOTIFICATION_FOLLOW,ClockBackdropSettings.NOTIFICATION_RELIEF}){
            equal(null,SettingsCatalog.item(key),"retired notification editor absent");equal(false,SettingsCatalog.isVisible(key),"retired notification setting not visible");equal(true,SettingsCatalog.hiddenKeys().contains(key),"published key explicitly hidden");equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(key),"published Boolean remains portable");
            for(boolean explicit:new boolean[]{true,false}){
                ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(document(key,Boolean.toString(explicit)),true);equal(true,ConfigTransfer.commit(store,prepared),"historical Boolean import supported");equal(explicit,store.values.get(key),"retirement does not overwrite stored old choice");
                equal(explicit,ConfigTransfer.prepare(ConfigTransfer.exportJson(store.values,Collections.singleton(key)),true).values().get(key),"historical Boolean export stays lossless");
            }
            store.values.put(key,true);
        }
        Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade173.migrate(store);for(String key:before.keySet())equal(before.get(key),store.values.get(key),"strength migration leaves retired settings untouched");
        for(boolean landscape:new boolean[]{false,true}){
            String removed="notification_big_clock_"+(landscape?"landscape_":"")+"backdrop_notification_mask_opacity";
            equal(false,StatusBarSettings.NUMERIC_DEFAULTS.containsKey(removed),"unpublished notification strength removed");equal(false,ConfigTransfer.types().containsKey(removed),"removed strength not portable");equal(false,Upgrade173.DEFAULTS.containsKey(removed),"removed strength outside migration");equal(null,SettingsCatalog.item(removed),"removed strength has no UI");
            checks++;try{ConfigTransfer.prepare(document(removed,"20"),true);throw new AssertionError("accepted unpublished removed notification strength");}catch(IOException expected){ }
        }
    }
    public static void main(String[] args)throws Exception{Bundle full=SettingsSnapshot.fromPreferences(saved());registration(full);migration();mirrors(full);editingAndImport();retiredNotification();System.out.println("Upgrade173Check: "+checks+" checks passed");}
}
