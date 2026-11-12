package dev.puitheme;
import android.os.Bundle;
import java.io.IOException;
import java.util.*;

/** Notification relief is a separate neutral opt-in; published batches stay frozen. */
public final class Upgrade172Check {
    private static int checks;
    private static final String KEY="notification_text_backdrop_relief_enabled";
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Upgrade172 "+checks+" "+why+": "+expected+" != "+actual);}
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
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,172L);return result;
    }
    private static String document(String raw){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+KEY+"\":"+raw+"}}";}
    private static void registration(Bundle full) {
        equal(KEY,ClockBackdropSettings.NOTIFICATION_RELIEF,"stable global protocol name");equal(52,SettingsFrameworkMirror.VERSION,"mirror schema");
        equal(Collections.<String,Object>singletonMap(KEY,false),Upgrade172.DEFAULTS,"one neutral addition");equal(23,Upgrade171.DEFAULTS.size(),"published schema38 batch stays frozen");equal(false,Upgrade171.DEFAULTS.containsKey(KEY),"schema38 cannot claim the later global option");
        equal(1000,full.keySet().size(),"full snapshot");equal(987,ConfigTransfer.types().size(),"portable count");equal(945,wire(full,38).size()-2,"published schema38 wire count unchanged");equal(922,wire(full,37).size()-2,"published schema37 wire count unchanged");
        equal(false,full.get(KEY),"notification relief defaults off");equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(KEY),"portable Boolean type");equal(false,NotificationBigClockSettings.BOOLEANS.containsKey(KEY),"global option is not copied by orientation");
        equal(true,full.get(ClockBackdropSettings.EDGE_ENABLED),"big clock keeps its own edge default");equal(40f,full.get(ClockBackdropSettings.HIGHLIGHT_STRENGTH),"big clock keeps its highlight default");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();Upgrade172.migrate(store);equal(0,store.writes,"empty locked store remains empty");
        store.values.put(ClockBackdropSettings.NOTIFICATION_FOLLOW,true);store.values.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,true);store.values.put(NotificationBigClockSettings.FOLLOW_STYLE,true);store.values.put(ClockBackdropSettings.EDGE_ENABLED,true);store.values.put(ClockBackdropSettings.HIGHLIGHT_STRENGTH,73f);store.values.put("private_note","keep");
        Map<String,Object> expected=new LinkedHashMap<>(store.values);expected.put(KEY,false);Upgrade172.migrate(store);equal(expected,store.values,"new default does not alter saved follow, main-clock edges or unrelated values");equal(1,store.writes,"one durable migration");Upgrade172.migrate(store);equal(1,store.writes,"idempotent");
        for(Object explicit:new Object[]{true,false,null,"invalid",0}){store.values.put(KEY,explicit);Map<String,Object> before=new LinkedHashMap<>(store.values);Upgrade172.migrate(store);equal(before,store.values,"any present choice is preserved");equal(1,store.writes,"present values cause no writes");}
    }
    private static void mirrors(Bundle full) {
        for(int schema=1;schema<=38;schema++){
            Map<String,Object> old=wire(full,schema),before=new LinkedHashMap<>(old);Bundle decoded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(decoded),"complete old wire upgrades "+schema);equal(before,old,"old input immutable");equal(false,old.containsKey(KEY),"old wire has no future relief flag");equal(false,decoded.get(KEY),"old wire never opts into notification relief");
            for(Object forged:new Object[]{false,true,null,0,0f,"false"}){Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(KEY,forged);equal(null,SettingsFrameworkMirror.decode(mixed),"old schema rejects a present future key "+schema);}
            Map<String,Object> damaged=new LinkedHashMap<>(old);damaged.remove(StatusBarSettings.CLOCK_OFFSET_X);equal(null,SettingsFrameworkMirror.decode(damaged),"new default cannot repair a missing original field");
            if(schema==38){equal(true,decoded.get(ClockBackdropSettings.NOTIFICATION_FOLLOW),"published notification follow survives");equal(2f,decoded.get(ClockBackdropSettings.EDGE_WIDTH),"published digit appearance survives");}
        }
        Map<String,Object> current=wire(full,39);equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"complete schema39 accepted");
        for(Object explicit:new Object[]{true,false}){Map<String,Object> saved=new LinkedHashMap<>(current);saved.put(KEY,explicit);equal(explicit,SettingsFrameworkMirror.decode(saved).get(KEY),"explicit current flag retained");}
        for(Object wrong:new Object[]{null,0,0f,"false"}){Map<String,Object> bad=new LinkedHashMap<>(current);bad.put(KEY,wrong);equal(null,SettingsFrameworkMirror.decode(bad),"current flag requires Boolean");}
        Map<String,Object> missing=new LinkedHashMap<>(current);missing.remove(KEY);equal(null,SettingsFrameworkMirror.decode(missing),"current schema requires its new field");
    }
    private static void editingAndImport()throws Exception {
        equal(null,SettingsCatalog.item(KEY),"retired relief has no editor");equal(false,SettingsCatalog.isVisible(KEY),"retired relief not visible");equal(true,SettingsCatalog.hiddenKeys().contains(KEY),"published key explicitly hidden");
        int copies=0;for(SettingsCatalog.Group group:SettingsCatalog.groups())for(SettingsCatalog.Item entry:group.items)if(KEY.equals(entry.key))copies++;equal(0,copies,"retired notification effect has no page or search entry");
        Map<String,Object> values=new LinkedHashMap<>();values.put(KEY,true);values.put(ClockBackdropSettings.NOTIFICATION_FOLLOW,true);values.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,true);Map<String,Object> unchanged=new LinkedHashMap<>(values);
        equal(true,SettingsCatalog.unavailableReason(KEY,values).contains("已移除"),"old flag cannot reactivate an editor");equal(unchanged,values,"retirement never clears saved compatibility values");
        for(boolean enabled:new boolean[]{false,true}){
            ConfigTransfer.PreparedImport imported=ConfigTransfer.prepare(document(Boolean.toString(enabled)),true);equal(Collections.<String,Object>singletonMap(KEY,enabled),imported.values(),"partial strict Boolean import");
            ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();store.values.put(ClockBackdropSettings.NOTIFICATION_FOLLOW,false);store.values.put(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED,false);store.values.put(ClockBackdropSettings.EDGE_ENABLED,true);
            equal(true,ConfigTransfer.commit(store,imported),"partial import commits");equal(enabled,store.values.get(KEY),"explicit relief flag stored");equal(false,store.values.get(ClockBackdropSettings.NOTIFICATION_FOLLOW),"import never enables follow");equal(false,store.values.get(NotificationBigClockSettings.DIGIT_REVEAL_ENABLED),"import never enables background material");equal(true,store.values.get(ClockBackdropSettings.EDGE_ENABLED),"import leaves main-clock edge choice intact");
        }
        for(String bad:new String[]{"null","0","1","\"true\"","[]","{}"}){checks++;try{ConfigTransfer.prepare(document(bad),true);throw new AssertionError("accepted non-Boolean relief: "+bad);}catch(IOException expected){}}
        equal(987,ConfigTransfer.prepare(ConfigTransfer.exportJson(Collections.emptyMap()),true).count,"full JSON1 export count");
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(ClockBackdropSettings.NOTIFICATION_FOLLOW,true);saved.put(ClockBackdropSettings.EDGE_WIDTH,2f);Bundle full=SettingsSnapshot.fromPreferences(saved);
        registration(full);migration();mirrors(full);editingAndImport();System.out.println("Upgrade172Check: "+checks+" checks passed");
    }
}
