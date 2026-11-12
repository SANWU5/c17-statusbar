package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.*;

/** Frozen schema37 text settings, historical wire boundaries, and portable editing contracts. */
public final class Upgrade169Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade169 "+checks+" "+reason+": "+expected+" != "+actual);
    }
    private static Map<String,Object> additions() {
        Map<String,Object> values=new LinkedHashMap<>();
        for(String side:new String[]{"left","right"}) {
            String prefix="notification_big_clock_widget_"+side+"_";
            values.put(prefix+"text","left".equals(side)?"（天气）":"（日期）");
            values.put(prefix+"size",18f);values.put(prefix+"offset_x",0f);values.put(prefix+"offset_y",0f);
            values.put(prefix+"follow_time_color",true);
            values.put(prefix+"color_light",0xff000000);values.put(prefix+"color_dark",0xffffffff);
            values.put(prefix+"color_light_custom_alpha",false);values.put(prefix+"color_dark_custom_alpha",false);
        }
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
        for(int index=0;index<batches.size();index++)if(schema<index+2)
            for(String key:batches.get(index).keySet())result.remove(key);
        if(schema<12)result.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,169L);return result;
    }
    private static String document(String settings) {
        return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"+settings+"}}";
    }
    private static void invalid(String settings,String reason) throws Exception {
        checks++;try{ConfigTransfer.prepare(document(settings),true);}catch(IOException expected){return;}
        throw new AssertionError("Unexpected accepted import: "+reason);
    }
    private static void registration(Bundle full) {
        equal(52,SettingsFrameworkMirror.VERSION,"current schema");equal(18,Upgrade169.DEFAULTS.size(),"only eighteen new text fields");
        equal(additions(),Upgrade169.DEFAULTS,"frozen keys, types and defaults");
        equal(1000,full.keySet().size(),"full snapshot count");equal(987,ConfigTransfer.types().size(),"portable count");
        equal(890,wire(full,35).size()-2,"schema35 stays frozen");equal(904,wire(full,36).size()-2,"schema36 stays frozen");
        Map<ConfigTransfer.Type,Integer> kinds=new EnumMap<>(ConfigTransfer.Type.class);
        for(Map.Entry<String,Object> item:additions().entrySet()) {
            equal(item.getValue(),full.get(item.getKey()),"registered snapshot default "+item.getKey());
            ConfigTransfer.Type kind=ConfigTransfer.types().get(item.getKey());
            equal(true,kind!=null,"registered portable type "+item.getKey());
            kinds.put(kind,kinds.getOrDefault(kind,0)+1);
        }
        equal(6,kinds.get(ConfigTransfer.Type.BOOLEAN),"two follow switches and four alpha flags");
        equal(6,kinds.get(ConfigTransfer.Type.NUMBER),"size and two offsets per side");
        equal(4,kinds.get(ConfigTransfer.Type.COLOR),"two theme colours per side");equal(2,kinds.get(ConfigTransfer.Type.STRING),"one content string per side");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade169.migrate(store);equal(0,store.writes,"empty locked preferences stay empty");
        store.values.put("private_note","keep");store.values.put(BigClockWidgetsSettings.LEFT_ENABLED,false);
        store.values.put(BigClockWidgetsSettings.RIGHT_PROVIDER,"com.example.weather/.WeatherWidget");
        store.values.put(NotificationBigClockSettings.DIGIT_REVEAL_AMOUNT,3f);
        Map<String,Object> expected=new LinkedHashMap<>(store.values);expected.putAll(additions());
        Upgrade169.migrate(store);equal(expected,store.values,"adds only missing text defaults, preserves old widget and mask choices");
        equal(1,store.writes,"one durable batch");Upgrade169.migrate(store);equal(1,store.writes,"idempotent migration");
        for(Map.Entry<String,Object> item:additions().entrySet())for(Object explicit:new Object[]{item.getValue(),null,"invalid",false}) {
            store.values.put(item.getKey(),explicit);Map<String,Object> before=new LinkedHashMap<>(store.values);
            Upgrade169.migrate(store);equal(before,store.values,"present value remains untouched "+item.getKey());equal(1,store.writes,"present values need no write");
        }
        equal(false,store.values.get(BigClockWidgetsSettings.LEFT_ENABLED),"migration never enables a component");
    }
    private static void mirrors(Bundle full) {
        for(int schema=1;schema<=36;schema++) {
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete old wire upgrades "+schema);equal(before,old,"old input is immutable "+schema);
            for(Map.Entry<String,Object> item:additions().entrySet()) {
                equal(false,old.containsKey(item.getKey()),"old wire excludes future field "+schema+" "+item.getKey());
                equal(item.getValue(),decoded.get(item.getKey()),"old wire gets text default "+schema+" "+item.getKey());
                for(Object supplied:new Object[]{item.getValue(),null,"invalid"}) {
                    Map<String,Object> forged=new LinkedHashMap<>(old);forged.put(item.getKey(),supplied);
                    equal(null,SettingsFrameworkMirror.decode(forged),"old schema rejects future field "+schema+" "+item.getKey());
                }
            }
            Map<String,Object> damaged=new LinkedHashMap<>(old);damaged.remove(StatusBarSettings.CLOCK_OFFSET_X);
            equal(null,SettingsFrameworkMirror.decode(damaged),"upgrade cannot repair damage to original fields "+schema);
        }
        Map<String,Object> current=wire(full,37),before=new LinkedHashMap<>(current);
        equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"current wire accepted");equal(before,current,"current input is immutable");
        for(Map.Entry<String,Object> item:additions().entrySet()) {
            Map<String,Object> bad=new LinkedHashMap<>(current);bad.remove(item.getKey());
            equal(null,SettingsFrameworkMirror.decode(bad),"current wire requires text field "+item.getKey());
            bad.put(item.getKey(),null);equal(null,SettingsFrameworkMirror.decode(bad),"current wire rejects null "+item.getKey());
            Object wrong;if(item.getValue() instanceof Boolean)wrong=1;else if(item.getValue() instanceof Float)wrong=18;
            else if(item.getValue() instanceof Integer)wrong=0f;else wrong=true;
            bad.put(item.getKey(),wrong);
            equal(null,SettingsFrameworkMirror.decode(bad),"current wire requires exact field type "+item.getKey());
        }
    }
    private static void portable() throws Exception {
        Map<String,Object> choices=new LinkedHashMap<>();
        for(int side:new int[]{0,1}) {
            String text=BigClockWidgetsSettings.textKey(side),size=BigClockWidgetsSettings.sizeKey(side),x=BigClockWidgetsSettings.offsetXKey(side),y=BigClockWidgetsSettings.offsetYKey(side);
            choices.put(BigClockWidgetsSettings.enabledKey(side),side==0);choices.put(text,"天气：（天气） 日期：（日期）");
            choices.put(size,23.5f);choices.put(x,-1000f);choices.put(y,1000f);choices.put(BigClockWidgetsSettings.followTimeColorKey(side),false);
            choices.put(BigClockWidgetsSettings.colorKey(side,false),0x804488cc);choices.put(BigClockWidgetsSettings.colorKey(side,true),0xff778899);
            choices.put(StatusBarSettings.alphaKey(BigClockWidgetsSettings.colorKey(side,false)),true);
            choices.put(BigClockWidgetsSettings.providerKey(side),"com.example.weather/.WeatherWidget");choices.put(BigClockWidgetsSettings.heightKey(side),128f);
            for(String raw:new String[]{"null","1","true","[]"})invalid("\""+text+"\":"+raw,"content must be String");
            invalid("\""+text+"\":\"两行\\n文字\"","content must be single-line");
            invalid("\""+text+"\":\""+new String(new char[BigClockWidgetsSettings.MAX_TEXT+1]).replace('\0','字')+"\"","content max length");
            for(String raw:new String[]{"7.99","64.01","1e9","\"18\"","null"})invalid("\""+size+"\":"+raw,"bounded finite size");
            for(String key:new String[]{x,y})for(String raw:new String[]{"-1000.00001","1000.00001","1e9","true","null"})invalid("\""+key+"\":"+raw,"exact offset boundary before float rounding");
            for(String key:new String[]{BigClockWidgetsSettings.followTimeColorKey(side),StatusBarSettings.alphaKey(BigClockWidgetsSettings.colorKey(side,true))})
                for(String raw:new String[]{"0","\"true\"","null"})invalid("\""+key+"\":"+raw,"strict Boolean choice");
            for(String valid:new String[]{"","普通文字","（天气）",new String(new char[BigClockWidgetsSettings.MAX_TEXT]).replace('\0','字')}) {
                Map<String,Object> one=Collections.singletonMap(text,valid);
                equal(valid,ConfigTransfer.prepare(ConfigTransfer.exportJson(one,one.keySet()),true).values().get(text),"accepted content boundary");
            }
        }
        ConfigTransfer.PreparedImport partial=ConfigTransfer.prepare(ConfigTransfer.exportJson(choices,choices.keySet()),true);
        equal(choices,partial.values(),"text, exact colours, offsets and legacy widget fields round trip");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.put("private_note","keep");store.values.put(NotificationBigClockSettings.MASTER,false);
        equal(true,ConfigTransfer.commit(store,partial),"partial import commits");
        for(Map.Entry<String,Object> item:choices.entrySet())equal(item.getValue(),store.values.get(item.getKey()),"partial import stores explicit choice "+item.getKey());
        equal("keep",store.values.get("private_note"),"import preserves unrelated preference");equal(false,store.values.get(NotificationBigClockSettings.MASTER),"import never enables big clock");
        equal(987,ConfigTransfer.prepare(ConfigTransfer.exportJson(choices),true).count,"full portable count");
        for(String privateKey:new String[]{"0.id","0.pending_id","0.pending_token","clock_widget_slots_v1"}) {
            equal(false,ConfigTransfer.types().containsKey(privateKey),"private AppWidget grants stay local");invalid("\""+privateKey+"\":123","private grant rejected");
        }
    }
    public static void main(String[] args) throws Exception {
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());registration(full);migration();mirrors(full);portable();
        System.out.println("Upgrade169Check: "+checks+" checks passed");
    }
}
