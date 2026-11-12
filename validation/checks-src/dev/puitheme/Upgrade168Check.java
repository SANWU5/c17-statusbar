package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.*;

/** Schema36 has one frozen default batch; widget grants and IDs stay on this device. */
public final class Upgrade168Check {
    private static int checks;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;
        if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade168 "+checks+" "+reason+": "+expected+" != "+actual);
    }
    private static String document(String settings) {
        return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"+settings+"}}";
    }
    private static void invalid(String settings,String reason) throws Exception {
        checks++;
        try {ConfigTransfer.prepare(document(settings),true);}
        catch(IOException expected){return;}
        throw new AssertionError(reason);
    }
    private static Map<String,Object> additions() {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String prefix:new String[]{NotificationBigClockSettings.PORTRAIT_PREFIX,NotificationBigClockSettings.LANDSCAPE_PREFIX}) {
            result.put(prefix+"follow_style_enabled",false);
            result.put(prefix+"reveal_mask_color",0xffffffff);
            result.put(StatusBarSettings.alphaKey(prefix+"reveal_mask_color"),false);
            result.put(prefix+"reveal_mask_opacity",20f);
        }
        for(String side:new String[]{"left","right"}) {
            result.put("notification_big_clock_widget_"+side+"_enabled",false);
            result.put("notification_big_clock_widget_"+side+"_provider","");
            result.put("notification_big_clock_widget_"+side+"_height",96f);
        }
        return result;
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
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,168L);return result;
    }
    private static void registration(Bundle full) throws Exception {
        equal(52,SettingsFrameworkMirror.VERSION,"current schema");
        equal(14,Upgrade168.DEFAULTS.size(),"two orientation batches plus six portable widget choices");
        equal(additions(),Upgrade168.DEFAULTS,"exact frozen defaults and types");
        equal(1000,full.keySet().size(),"current snapshot includes the frozen appearance and new text batches");
        System.out.println("Upgrade168 registry: portable="+ConfigTransfer.types().size()+", leftProvider="
                +ConfigTransfer.types().containsKey(BigClockWidgetsSettings.LEFT_PROVIDER)+", rightProvider="
                +ConfigTransfer.types().containsKey(BigClockWidgetsSettings.RIGHT_PROVIDER));
        equal(987,ConfigTransfer.types().size(),"portable registry includes both provider choices");
        for(Map.Entry<String,Object> item:additions().entrySet()) {
            equal(item.getValue(),full.get(item.getKey()),"snapshot default "+item.getKey());
            equal(true,ConfigTransfer.types().containsKey(item.getKey()),"portable addition "+item.getKey());
        }
        equal(890,wire(full,35).size()-2,"schema35 stays frozen at 890 settings");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade168.migrate(store);equal(0,store.writes,"empty locked preferences stay empty");
        store.values.put(StatusBarSettings.CLOCK_OFFSET_X,17.5f);store.values.put("private_note","keep");
        Map<String,Object> expected=new LinkedHashMap<>(store.values);expected.putAll(additions());
        Upgrade168.migrate(store);equal(expected,store.values,"only missing fourteen defaults are added");
        equal(1,store.writes,"one durable default batch");
        Upgrade168.migrate(store);equal(1,store.writes,"idempotent defaults");
        for(Map.Entry<String,Object> item:additions().entrySet())for(Object explicit:new Object[]{item.getValue(),null,"invalid"}) {
            store.values.put(item.getKey(),explicit);Map<String,Object> before=new LinkedHashMap<>(store.values);
            Upgrade168.migrate(store);equal(before,store.values,"explicit values are never overwritten "+item.getKey());
            equal(1,store.writes,"present values need no write");
        }
        for(boolean landscape:new boolean[]{false,true}) {
            String follow=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FOLLOW_STYLE):NotificationBigClockSettings.FOLLOW_STYLE;
            String reveal=landscape?NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED):NotificationBigClockSettings.DIGIT_REVEAL_ENABLED;
            ActivationGuardPreferencesCheck.MemoryPreferences choices=new ActivationGuardPreferencesCheck.MemoryPreferences();
            choices.values.put(follow,true);choices.values.put(reveal,false);Upgrade168.migrate(choices);
            equal(true,choices.values.get(follow),"stored follow survives disabled parent");
            equal(false,choices.values.get(reveal),"migration never enables reveal");
            equal(false,NotificationBigClockSettings.effectiveFollowStyle(choices.values,landscape),"disabled parent gates follow");
            Bundle source=SettingsSnapshot.fromPreferences(choices.values);
            equal(true,source.get(follow),"full snapshot carries raw follow choice");
            equal(false,NotificationBigClockSettings.effectiveFollowStyle(source,landscape),"runtime bundle gates follow");
            choices.values.put(reveal,true);
            equal(true,NotificationBigClockSettings.effectiveFollowStyle(choices.values,landscape),"reopening parent restores saved follow");
        }
    }
    private static void mirrors(Bundle full) {
        for(int schema=1;schema<=35;schema++) {
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);
            Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete historical schema upgrades "+schema);
            equal(before,old,"decode never mutates input "+schema);
            for(Map.Entry<String,Object> item:additions().entrySet()) {
                equal(false,old.containsKey(item.getKey()),"historical copy contains no future key "+schema);
                equal(item.getValue(),decoded.get(item.getKey()),"historical missing default "+schema);
                for(Object value:new Object[]{item.getValue(),null,"invalid"}) {
                    Map<String,Object> future=new LinkedHashMap<>(old);future.put(item.getKey(),value);
                    equal(null,SettingsFrameworkMirror.decode(future),"historical future field rejected "+schema+" "+item.getKey());
                }
            }
            Map<String,Object> broken=new LinkedHashMap<>(old);broken.remove(StatusBarSettings.CLOCK_OFFSET_X);
            equal(null,SettingsFrameworkMirror.decode(broken),"defaults never repair damaged old fields "+schema);
        }
        Map<String,Object> current=wire(full,37),before=new LinkedHashMap<>(current);
        equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"complete current schema accepted");
        equal(before,current,"current decode does not mutate input");
        for(Map.Entry<String,Object> item:additions().entrySet()) {
            Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(item.getKey());
            equal(null,SettingsFrameworkMirror.decode(missing),"current schema requires every new field "+item.getKey());
            Map<String,Object> wrong=new LinkedHashMap<>(current);wrong.put(item.getKey(),null);
            equal(null,SettingsFrameworkMirror.decode(wrong),"null new field rejected "+item.getKey());
            wrong.put(item.getKey(),item.getValue() instanceof Boolean?1:item.getValue() instanceof Float?20:false);
            equal(null,SettingsFrameworkMirror.decode(wrong),"new fields retain exact wire type "+item.getKey());
        }
    }
    private static void battery(Bundle full) throws Exception {
        for(String choice:new String[]{"ios27","ios27_number","ios27_plain","pui","native"}) {
            String expected=choice.startsWith("ios27")?"pui":choice;
            ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();
            store.values.put(StatusBarSettings.BATTERY_STYLE,choice);store.values.put("private_note","keep");
            Upgrade168.migrate(store);equal(expected,store.values.get(StatusBarSettings.BATTERY_STYLE),"retired local battery normalizes "+choice);
            equal("keep",store.values.get("private_note"),"battery migration preserves unrelated data");
            equal(expected,StatusBarSettings.string(Collections.singletonMap(StatusBarSettings.BATTERY_STYLE,choice),StatusBarSettings.BATTERY_STYLE),"legacy read normalizes "+choice);
            for(int schema:new int[]{35,36}) {
                Map<String,Object> copy=wire(full,schema);copy.put(StatusBarSettings.BATTERY_STYLE,choice);
                equal(expected,SettingsFrameworkMirror.decode(copy).get(StatusBarSettings.BATTERY_STYLE),"wire battery normalizes "+choice);
            }
            ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(document("\"battery_style\":\""+choice+"\""),true);
            equal(expected,prepared.values().get(StatusBarSettings.BATTERY_STYLE),"legacy import normalizes "+choice);
            equal(true,ConfigTransfer.commit(store,prepared),"battery partial import commits");
            equal(expected,store.values.get(StatusBarSettings.BATTERY_STYLE),"stored import uses selectable value "+choice);
            equal("keep",store.values.get("private_note"),"partial import retains private data");
        }
    }
    private static void portable() throws Exception {
        Map<String,Object> choices=new LinkedHashMap<>();
        choices.put(NotificationBigClockSettings.FOLLOW_STYLE,true);
        choices.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,false);
        choices.put(NotificationBigClockSettings.REVEAL_MASK_COLOR,0x804488cc);
        choices.put(StatusBarSettings.alphaKey(NotificationBigClockSettings.REVEAL_MASK_COLOR),true);
        choices.put(NotificationBigClockSettings.REVEAL_MASK_OPACITY,27.5f);
        choices.put(BigClockWidgetsSettings.LEFT_ENABLED,true);choices.put(BigClockWidgetsSettings.RIGHT_ENABLED,false);
        choices.put(BigClockWidgetsSettings.LEFT_PROVIDER,"com.example.weather/.WeatherWidget");
        choices.put(BigClockWidgetsSettings.RIGHT_PROVIDER,"");
        choices.put(BigClockWidgetsSettings.LEFT_HEIGHT,128f);choices.put(BigClockWidgetsSettings.RIGHT_HEIGHT,96f);
        String json=ConfigTransfer.exportJson(choices,choices.keySet());
        ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(json,true);
        equal(choices,prepared.values(),"partial choices preserve provider identity and raw alpha exactly once");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();
        store.values.put(NotificationBigClockSettings.MASTER,false);store.values.put("private_note","keep");
        equal(true,ConfigTransfer.commit(store,prepared),"partial appearance and provider import commits");
        for(Map.Entry<String,Object> item:choices.entrySet())equal(item.getValue(),store.values.get(item.getKey()),"saved partial value "+item.getKey());
        equal(false,store.values.get(NotificationBigClockSettings.MASTER),"partial choices never enable the big clock");
        equal(false,NotificationBigClockSettings.effectiveFollowStyle(store.values,false),"partial saved follow cannot enable reveal");
        equal("keep",store.values.get("private_note"),"partial import preserves unrelated values");
        for(String providerKey:new String[]{BigClockWidgetsSettings.LEFT_PROVIDER,BigClockWidgetsSettings.RIGHT_PROVIDER}) {
            invalid("\""+providerKey+"\":\"https://example.invalid/widget\"","provider URI rejected");
            invalid("\""+providerKey+"\":3","provider exact String type");
        }
        for(String key:new String[]{"0.id","1.id","0.pending_id","1.pending_id","0.pending_token","1.pending_token", "clock_widget_slots_v1"}) {
            equal(false,ConfigTransfer.types().containsKey(key),"private grant is absent from portable registry "+key);
            invalid("\""+key+"\":123","private widget state cannot be imported "+key);
            choices.put(key,"PRIVATE_WIDGET_GRANT_SENTINEL");
        }
        String complete=ConfigTransfer.exportJson(choices);
        equal(false,complete.contains("PRIVATE_WIDGET_GRANT_SENTINEL"),"private IDs and pending journal never export");
        equal(987,ConfigTransfer.prepare(complete,true).count,"complete portable document count");
        for(String field:new String[]{NotificationBigClockSettings.FOLLOW_STYLE,NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FOLLOW_STYLE)})
            for(String wrong:new String[]{"null","0","\"true\""})invalid("\""+field+"\":"+wrong,"follow remains strictly Boolean");
    }
    public static void main(String[] args) throws Exception {
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        registration(full);migration();mirrors(full);battery(full);portable();
        System.out.println("Upgrade168Check: "+checks+" checks passed");
    }
}
