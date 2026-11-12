package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/** Schema38 freezes the exact new appearance and component-click protocol. */
public final class Upgrade171Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade171 "+checks+" "+why+": "+expected+" != "+actual);}
    private static Map<String,Object> additions() {
        Map<String,Object> values=new LinkedHashMap<>();values.put("notification_text_follow_clock_backdrop",false);
        for(String prefix:new String[]{"notification_big_clock_","notification_big_clock_landscape_"}) {
            prefix+="backdrop_";values.put(prefix+"edge_enabled",true);values.put(prefix+"edge_width",.6f);
            values.put(prefix+"edge_color",0xffffffff);values.put(prefix+"edge_color_custom_alpha",false);values.put(prefix+"edge_opacity",60f);
            values.put(prefix+"highlight_angle",315f);values.put(prefix+"highlight_strength",40f);values.put(prefix+"shade_strength",0f);values.put(prefix+"depth_width",1.5f);
        }
        for(String side:new String[]{"left","right"}){String prefix="notification_big_clock_widget_"+side+"_";values.put(prefix+"click_enabled",false);values.put(prefix+"launch_app","");}
        return values;
    }
    private static Map<String,Object> wire(Bundle full,int schema) {
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
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,171L);return result;
    }
    private static String document(String settings){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"+settings+"}}";}
    private static void invalid(String settings,String why)throws Exception {
        checks++;try{ConfigTransfer.prepare(document(settings),true);}catch(IOException expected){return;}throw new AssertionError("accepted "+why);
    }
    private static void registration(Bundle full) {
        equal(52,SettingsFrameworkMirror.VERSION,"current schema");equal(23,Upgrade171.DEFAULTS.size(),"exact new batch size");equal(additions(),Upgrade171.DEFAULTS,"frozen literals/types/defaults");
        equal(1000,full.keySet().size(),"complete snapshot size");equal(987,ConfigTransfer.types().size(),"portable size");equal(922,wire(full,37).size()-2,"schema37 stays frozen");equal(904,wire(full,36).size()-2,"schema36 stays frozen");equal(890,wire(full,35).size()-2,"schema35 stays frozen");
        Map<ConfigTransfer.Type,Integer> counts=new EnumMap<>(ConfigTransfer.Type.class);
        for(Map.Entry<String,Object> item:additions().entrySet()){
            equal(item.getValue(),full.get(item.getKey()),"snapshot default "+item.getKey());ConfigTransfer.Type type=ConfigTransfer.types().get(item.getKey());equal(true,type!=null,"portable "+item.getKey());counts.put(type,counts.getOrDefault(type,0)+1);
        }
        equal(7,counts.get(ConfigTransfer.Type.BOOLEAN),"edge, alpha, click and shared notification switches");equal(12,counts.get(ConfigTransfer.Type.NUMBER),"six numbers per direction");equal(2,counts.get(ConfigTransfer.Type.COLOR),"one edge color per direction");equal(2,counts.get(ConfigTransfer.Type.STRING),"component launch packages");
        equal(false,NotificationBigClockSettings.BOOLEANS.containsKey(ClockBackdropSettings.NOTIFICATION_FOLLOW),"shared notification switch never enters landscape duplication");
        equal(false,full.get(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED),"upgrade does not enable material");equal(false,full.get(NotificationBigClockSettings.FOLLOW_STYLE),"date follow remains opt-in");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade171.migrate(store);equal(0,store.writes,"locked empty store stays empty");
        store.values.put("private_note","keep");store.values.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,true);store.values.put(NotificationBigClockSettings.GLASS,true);store.values.put(NotificationBigClockSettings.LOCK_STYLE,true);
        store.values.put(BigClockWidgetsSettings.LEFT_PROVIDER,"com.example.weather/.Widget");Map<String,Object> wanted=new LinkedHashMap<>(store.values);wanted.putAll(additions());
        Upgrade171.migrate(store);equal(wanted,store.values,"adds only missing new fields, preserving prior appearance/provider");equal(1,store.writes,"one durable batch");Upgrade171.migrate(store);equal(1,store.writes,"idempotent");
        for(Map.Entry<String,Object> item:additions().entrySet())for(Object explicit:new Object[]{item.getValue(),null,"invalid",false}){
            store.values.put(item.getKey(),explicit);Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade171.migrate(store);equal(before,store.values,"existing value preserved "+item.getKey());equal(1,store.writes,"no redundant write");
        }
    }
    private static void mirrors(Bundle full) {
        for(int schema=1;schema<=37;schema++){
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);equal(true,SettingsSnapshot.complete(decoded),"old schema upgrades "+schema);equal(before,old,"old input is immutable");
            for(Map.Entry<String,Object> item:additions().entrySet()){
                equal(false,old.containsKey(item.getKey()),"future field absent "+schema);equal(item.getValue(),decoded.get(item.getKey()),"old schema receives exact default");
                for(Object forged:new Object[]{item.getValue(),null,"invalid"}){Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(item.getKey(),forged);equal(null,SettingsFrameworkMirror.decode(mixed),"historical wire refuses future key "+schema+" "+item.getKey());}
            }
            Map<String,Object> damaged=new LinkedHashMap<>(old);damaged.remove(StatusBarSettings.CLOCK_OFFSET_X);equal(null,SettingsFrameworkMirror.decode(damaged),"new defaults cannot conceal damaged historical field");
        }
        Map<String,Object> current=wire(full,38),before=new LinkedHashMap<>(current);equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"current copy is complete");equal(before,current,"current input is immutable");
        for(Map.Entry<String,Object> item:additions().entrySet()){
            Map<String,Object> bad=new LinkedHashMap<>(current);bad.remove(item.getKey());equal(null,SettingsFrameworkMirror.decode(bad),"current schema requires each new key");bad.put(item.getKey(),null);equal(null,SettingsFrameworkMirror.decode(bad),"null current value rejected");
            Object wrong;if(item.getValue() instanceof Boolean)wrong=1;else if(item.getValue() instanceof Float)wrong=1;else if(item.getValue() instanceof Integer)wrong=1f;else wrong=true;
            bad.put(item.getKey(),wrong);equal(null,SettingsFrameworkMirror.decode(bad),"current copy requires exact field type");
        }
    }
    private static void portable()throws Exception {
        Map<String,Object> choices=new LinkedHashMap<>();choices.put(ClockBackdropSettings.NOTIFICATION_FOLLOW,true);
        for(boolean landscape:new boolean[]{false,true})for(String base:ClockBackdropSettings.NUMBERS.keySet()){
            String key=landscape?NotificationBigClockSettings.landscapeKey(base):base;float max=ClockBackdropSettings.maximum(key);
            for(String boundary:new String[]{"0",Float.toString(max)})equal(Float.valueOf(boundary),ConfigTransfer.prepare(document("\""+key+"\":"+boundary),true).values().get(key),"numeric endpoint "+key);
            for(String bad:new String[]{"-0.0000000001",BigDecimal.valueOf(max).add(new BigDecimal("0.0000000001")).toPlainString(),"1e99","null","true","\"1\""})invalid("\""+key+"\":"+bad,"hard numeric boundary "+key);
            equal(null,SettingsCatalog.numericInputError(key,BigDecimal.ZERO),"editor accepts exact lower bound");equal(true,SettingsCatalog.numericInputError(key,BigDecimal.valueOf(max).add(new BigDecimal("0.0000000001")))!=null,"editor rejects overflow before float rounding");
            choices.put(key,max);
        }
        for(boolean landscape:new boolean[]{false,true}){
            String color=landscape?NotificationBigClockSettings.landscapeKey(ClockBackdropSettings.EDGE_COLOR):ClockBackdropSettings.EDGE_COLOR;
            choices.put(color,0x804488cc);choices.put(StatusBarSettings.alphaKey(color),true);choices.put(landscape?NotificationBigClockSettings.landscapeKey(ClockBackdropSettings.EDGE_ENABLED):ClockBackdropSettings.EDGE_ENABLED,false);
        }
        for(int side:new int[]{0,1}){
            String app=BigClockWidgetsSettings.launchAppKey(side);choices.put(BigClockWidgetsSettings.clickEnabledKey(side),true);choices.put(app,"com.example.clock");
            for(String malformed:new String[]{"intent://clock","com.example/.Activity","android.intent.action.VIEW:clock","bad package","com.example\nclock"})invalid("\""+app+"\":\""+malformed.replace("\n","\\n")+"\"","only app package targets");
            for(String malformed:new String[]{"null","true","1","[]"})invalid("\""+app+"\":"+malformed,"strict target String");
            equal("",ConfigTransfer.prepare(document("\""+app+"\":\"\""),true).values().get(app),"empty launch package is neutral");
        }
        for(Map.Entry<String,Object> item:additions().entrySet())if(item.getValue() instanceof Boolean)for(String malformed:new String[]{"null","0","\"true\""})invalid("\""+item.getKey()+"\":"+malformed,"strict new Boolean");
        ConfigTransfer.PreparedImport imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(choices,choices.keySet()),true);equal(choices,imported.values(),"new settings round trip exactly");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.put("private_note","keep");store.values.put(NotificationBigClockSettings.MASTER,false);equal(true,ConfigTransfer.commit(store,imported),"partial import commits");
        for(Map.Entry<String,Object> item:choices.entrySet())equal(item.getValue(),store.values.get(item.getKey()),"partial choice saved");equal(false,store.values.get(NotificationBigClockSettings.MASTER),"import never enables parent");equal("keep",store.values.get("private_note"),"unrelated value remains");equal(987,ConfigTransfer.prepare(ConfigTransfer.exportJson(choices),true).count,"full portable export count");
    }
    public static void main(String[] args)throws Exception {Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());registration(full);migration();mirrors(full);portable();System.out.println("Upgrade171Check: "+checks+" checks passed");}
}
