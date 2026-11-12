package dev.puitheme;

import android.os.Bundle;
import java.util.*;

/** The covered mini-stack preference travels without enabling its parent, including old snapshots. */
public final class Upgrade166Check {
    private static int checks;
    private static final String KEY=NotificationMiniStack.AUTO_HIDE_IN_STACK;
    private static void equal(Object expected,Object actual,String reason) {
        checks++;
        if(!Objects.equals(expected,actual))throw new AssertionError(reason+": "+expected+" != "+actual);
    }
    public static void main(String[] args) throws Exception {
        equal(52,SettingsFrameworkMirror.VERSION,"current mirror schema");
        Map<String,Object> additions=new LinkedHashMap<>();additions.put(KEY,true);additions.put(NativeStatusIcons.LAYOUT,"{}");
        equal(additions,Upgrade166.DEFAULTS,"only two neutral additions in the unpublished schema35 batch");
        ActivationGuardPreferencesCheck.MemoryPreferences store=new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade166.migrate(store);equal(0,store.writes,"empty locked store remains empty");
        store.values.put(NotificationBigClockSettings.STACK_ENABLED,false);
        store.values.put("private_note","kept");
        Upgrade166.migrate(store);equal(true,store.values.get(KEY),"absent child gets default");
        equal(false,store.values.get(NotificationBigClockSettings.STACK_ENABLED),"migration never enables parent");
        equal("kept",store.values.get("private_note"),"unrelated value preserved");
        int writes=store.writes;
        for(Object explicit:new Object[]{false,true,null,"invalid"}) {
            store.values.put(KEY,explicit);Upgrade166.migrate(store);
            equal(explicit,store.values.get(KEY),"explicit preference preserved");
            equal(writes,store.writes,"idempotent migration");
        }
        Bundle full=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(1000,full.keySet().size(),"current snapshot includes every subsequent independent batch");
        historicalCopies(full);
        currentRequired(full);
        hintLayoutCompatibility(full);
        Map<String,Object> old=new LinkedHashMap<>();
        for(String key:full.keySet())if(!Upgrade166.DEFAULTS.containsKey(key)&&!Upgrade168.DEFAULTS.containsKey(key)&&!Upgrade169.DEFAULTS.containsKey(key)&&!Upgrade171.DEFAULTS.containsKey(key)&&!Upgrade172.DEFAULTS.containsKey(key)&&!Upgrade173.DEFAULTS.containsKey(key)&&!Upgrade174.DEFAULTS.containsKey(key)&&!Upgrade175.DEFAULTS.containsKey(key)&&!Upgrade177.DEFAULTS.containsKey(key)&&!Upgrade184.DEFAULTS.containsKey(key)&&!Upgrade185.DEFAULTS.containsKey(key)&&!Upgrade186.DEFAULTS.containsKey(key)&&!Upgrade187.DEFAULTS.containsKey(key)&&!Upgrade188.DEFAULTS.containsKey(key)&&!Upgrade195.DEFAULTS.containsKey(key)&&!Upgrade196.DEFAULTS.containsKey(key)&&!Upgrade209.DEFAULTS.containsKey(key)&&!Upgrade279.DEFAULTS.containsKey(key))old.put(key,full.get(key));
        old.put(SettingsFrameworkMirror.SCHEMA,34);old.put(SettingsFrameworkMirror.REVISION,166L);
        Map<String,Object> untouched=new LinkedHashMap<>(old);
        Bundle decoded=SettingsFrameworkMirror.decode(old);
        equal(true,SettingsSnapshot.complete(decoded),"complete schema34 upgrades");
        equal(true,decoded.get(KEY),"schema34 supplies child default");
        equal(untouched,old,"decode never mutates input");
        for(Object forged:new Object[]{true,false,null,0}) {
            Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(KEY,forged);
            equal(null,SettingsFrameworkMirror.decode(mixed),"historical schema cannot carry future key");
        }
        Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(StatusBarSettings.CLOCK_OFFSET_X);
        equal(null,SettingsFrameworkMirror.decode(missing),"later default cannot mask missing original field");
        equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(KEY),"portable type");
        for(boolean child:new boolean[]{true,false}) {
            Map<String,Object> choice=Collections.<String,Object>singletonMap(KEY,child);
            ConfigTransfer.PreparedImport partial=ConfigTransfer.prepare(ConfigTransfer.exportJson(choice,choice.keySet()),true);
            equal(choice,partial.values(),"partial import is independent");
            ActivationGuardPreferencesCheck.MemoryPreferences target=new ActivationGuardPreferencesCheck.MemoryPreferences();
            target.values.put(NotificationBigClockSettings.STACK_ENABLED,false);target.values.put(NotificationMiniStack.ENABLED,false);
            target.values.put("private_note","kept");
            Map<String,Object> expected=new LinkedHashMap<>(target.values);expected.put(KEY,child);
            equal(true,ConfigTransfer.commit(target,partial),"partial child choice commits");
            // The shared commit also writes its existing retired-feature normalization keys.
            for(Map.Entry<String,Object> kept:expected.entrySet())equal(kept.getValue(),target.values.get(kept.getKey()),
                    "partial import preserves the child choice, both disabled parents and unrelated values: "+kept.getKey());
        }
        String prefix="{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\""+KEY+"\":";
        for(String malformed:new String[]{"null","0","1","\"true\""}) {
            checks++;
            try{ConfigTransfer.prepare(prefix+malformed+"}}",true);throw new AssertionError("non-Boolean child imported: "+malformed);}
            catch(java.io.IOException expected) { }
        }
        equal("notification_stack",SettingsCatalog.item(KEY).groupId,"parent page owns child switch");
        equal(true,ConfigScope.section("notification_stack").keys().contains(KEY),"parent export includes child");
        Map<String,Object> choices=new LinkedHashMap<>();
        choices.put(NotificationBigClockSettings.STACK_ENABLED,false);choices.put(NotificationMiniStack.ENABLED,true);
        equal("请先开启堆叠通知",SettingsCatalog.unavailableReason(KEY,choices),"parent off blocks enabling child");
        choices.put(NotificationBigClockSettings.STACK_ENABLED,true);choices.put(NotificationMiniStack.ENABLED,false);
        equal("请先开启小堆叠（同应用）",SettingsCatalog.unavailableReason(KEY,choices),"same-app feature required");
        choices.put(NotificationMiniStack.ENABLED,true);
        equal("",SettingsCatalog.unavailableReason(KEY,choices),"both parents permit editing");
        List<SettingsCatalog.DetailPage> pages=SettingsCatalog.detailPages(SettingsCatalog.group("clock"));
        equal(3,pages.size(),"clock has a separate common tab");
        SettingsCatalog.DetailPage common=pages.get(2);
        equal("common",common.id,"stable common page identity");equal("通用设置",common.title,"requested name");
        Set<String> commonKeys=new LinkedHashSet<>();for(SettingsCatalog.Item item:common.items)commonKeys.add(item.key);
        equal(new HashSet<>(Arrays.asList(ClockLayoutSettings.digitKey("clock"),ClockLayoutSettings.digitSpacingKey("clock"),
                ClockLayoutSettings.colonKey("clock"),StatusBarLayoutSettings.key(StatusBarLayoutSettings.CLOCK,"margin_left"),
                StatusBarLayoutSettings.key(StatusBarLayoutSettings.CLOCK,"margin_right"))),commonKeys,"only truly shared controls");
        equal(true,SettingsCatalog.commonDetailItems(SettingsCatalog.group("clock")).isEmpty(),"common settings are not duplicated outside tabs");
        for(String id:new String[]{"shade_wallpaper_portrait","shade_wallpaper_landscape"})
            equal(true,SettingsCatalog.group(id).description.contains("功能未完成"),"unfinished wallpaper notice "+id);
        System.out.println("Upgrade166Check: "+checks+" checks passed");
    }
    private static Map<String,Object> wire(Bundle full,int schema) {
        Map<String,Object> result=new LinkedHashMap<>();for(String key:full.keySet())result.put(key,full.get(key));
        // Frozen introduction boundaries; each historical copy contains only settings that existed then.
        List<Map<String,Object>> batches=Arrays.asList(Upgrade65.DEFAULTS,Upgrade66.DEFAULTS,Upgrade67.DEFAULTS,
                Upgrade70.DEFAULTS,Upgrade76.DEFAULTS,Upgrade77.DEFAULTS,Upgrade78.DEFAULTS,Upgrade79.DEFAULTS,
                Upgrade80.DEFAULTS,Upgrade82.DEFAULTS,Upgrade85.DEFAULTS,Upgrade90.DEFAULTS,Upgrade91.DEFAULTS,
                Upgrade93.DEFAULTS,Upgrade96.DEFAULTS,Upgrade98.DEFAULTS,Upgrade99.DEFAULTS,Upgrade100.DEFAULTS,
                Upgrade101.DEFAULTS,Upgrade103.DEFAULTS,Upgrade105.DEFAULTS,Upgrade106.DEFAULTS,Upgrade107.DEFAULTS,
                Upgrade108.DEFAULTS,Upgrade117.DEFAULTS,Upgrade118.DEFAULTS,Upgrade135.DEFAULTS,Upgrade136.DEFAULTS,
                Upgrade144.DEFAULTS,Upgrade146.DEFAULTS,Upgrade154.DEFAULTS,Upgrade157.DEFAULTS,Upgrade160.DEFAULTS,Upgrade166.DEFAULTS,Upgrade168.DEFAULTS,Upgrade169.DEFAULTS,Upgrade171.DEFAULTS,Upgrade172.DEFAULTS,Upgrade173.DEFAULTS,Upgrade174.DEFAULTS,Upgrade175.DEFAULTS,Upgrade177.DEFAULTS,Upgrade184.DEFAULTS,Upgrade185.DEFAULTS,Upgrade186.DEFAULTS,Upgrade187.DEFAULTS,Upgrade188.DEFAULTS,Upgrade195.DEFAULTS,Upgrade196.DEFAULTS,Upgrade209.DEFAULTS,Upgrade279.DEFAULTS);
        for(int index=0;index<batches.size();index++)if(schema<index+2)
            for(String key:batches.get(index).keySet())result.remove(key);
        if(schema<12)result.remove(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED);
        result.put(SettingsFrameworkMirror.SCHEMA,schema);result.put(SettingsFrameworkMirror.REVISION,166L);return result;
    }
    private static void historicalCopies(Bundle full) {
        for(int schema=1;schema<35;schema++) {
            Map<String,Object> old=wire(full,schema),untouched=new LinkedHashMap<>(old);
            equal(false,old.containsKey(KEY),"historical fixture has no future child "+schema);
            Bundle upgraded=SettingsFrameworkMirror.decode(old);
            equal(true,SettingsSnapshot.complete(upgraded),"historical schema upgrades completely "+schema);
            equal(true,upgraded.get(KEY),"only absent child takes default "+schema);
            equal("{}",upgraded.get(NativeStatusIcons.LAYOUT),"old snapshot has no per-slot overrides "+schema);
            for(Object supplied:new Object[]{"{}",null,false}) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(NativeStatusIcons.LAYOUT,supplied);
                equal(null,SettingsFrameworkMirror.decode(mixed),"historical schema rejects future hint layout "+schema);
            }
            equal(false,upgraded.get(NotificationBigClockSettings.STACK_ENABLED),"stack parent remains off "+schema);
            equal(false,upgraded.get(NotificationMiniStack.ENABLED),"mini parent remains off "+schema);
            equal(untouched,old,"historical decode does not alter source "+schema);
            for(Object present:new Object[]{false,true,null,"false"}) {
                Map<String,Object> mixed=new LinkedHashMap<>(old);mixed.put(KEY,present);
                equal(null,SettingsFrameworkMirror.decode(mixed),"historical schema rejects future key even null "+schema);
            }
            Map<String,Object> missing=new LinkedHashMap<>(old);missing.remove(StatusBarSettings.CLOCK_OFFSET_X);
            equal(null,SettingsFrameworkMirror.decode(missing),"later default cannot repair missing old field "+schema);
        }
    }
    private static void currentRequired(Bundle full) {
        Map<String,Object> current=wire(full,35),untouched=new LinkedHashMap<>(current);
        equal(true,SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)),"current schema accepts complete copy");
        equal(untouched,current,"current decode does not alter source");
        for(boolean child:new boolean[]{false,true}) {
            current.put(KEY,child);Bundle decoded=SettingsFrameworkMirror.decode(current);
            equal(child,decoded.get(KEY),"current explicit child retained");
            equal(false,decoded.get(NotificationBigClockSettings.STACK_ENABLED),"current parent remains off");
        }
        current.remove(KEY);equal(null,SettingsFrameworkMirror.decode(current),"schema35 requires child key");
        for(Object wrong:new Object[]{null,0,1L,0f,"true",new Object()}) {
            current.put(KEY,wrong);equal(null,SettingsFrameworkMirror.decode(current),"schema35 child type stays strictly Boolean");
        }
    }

    private static void hintLayoutCompatibility(Bundle full) throws Exception {
        Map<String,Object> current=wire(full,35);current.remove(NativeStatusIcons.LAYOUT);
        equal(null,SettingsFrameworkMirror.decode(current),"current schema requires hint layout");
        for(Object wrong:new Object[]{null,0,false,new Object()}) {
            current.put(NativeStatusIcons.LAYOUT,wrong);
            equal(null,SettingsFrameworkMirror.decode(current),"current hint layout remains strictly String");
        }
        String value="{\"bluetooth\":{\"side\":\"left\",\"order\":1},\"alarm_clock\":{\"side\":\"right\",\"order\":0}}";
        Map<String,Object> choice=Collections.<String,Object>singletonMap(NativeStatusIcons.LAYOUT,value);
        ConfigTransfer.PreparedImport prepared=ConfigTransfer.prepare(ConfigTransfer.exportJson(choice,choice.keySet()),true);
        equal(NativeStatusIconLayout.validate(value),prepared.values().get(NativeStatusIcons.LAYOUT),"new hint layout roundtrips canonically");
        ActivationGuardPreferencesCheck.MemoryPreferences target=new ActivationGuardPreferencesCheck.MemoryPreferences();
        target.values.put(StatusBarLayoutSettings.MASTER,false);target.values.put(NativeStatusIcons.MASTER,false);
        target.values.put(NativeStatusIcons.PRIORITY,"location,bluetooth");target.values.put("private_note","kept");
        equal(true,ConfigTransfer.commit(target,prepared),"partial hint layout import commits");
        equal(false,target.values.get(StatusBarLayoutSettings.MASTER),"partial layout import never enables global sorting");
        equal(false,target.values.get(NativeStatusIcons.MASTER),"partial layout import never enables native hint controls");
        equal("location,bluetooth",target.values.get(NativeStatusIcons.PRIORITY),"retention priority stays independent");
        equal("kept",target.values.get("private_note"),"unrelated values remain intact");
        equal(true,ConfigScope.section(StatusBarLayoutSettings.GROUP).keys().contains(NativeStatusIcons.LAYOUT),"shared sorting page owns hint layout export");
        String blank=ConfigTransfer.exportJson(Collections.singletonMap(NativeStatusIcons.LAYOUT,"{}"),Collections.singleton(NativeStatusIcons.LAYOUT));
        for(String bad:new String[]{"[]","null","{\"bluetooth\":{\"side\":\"left\",\"order\":-1}}",
                "{\"bluetooth\":{\"side\":\"center\",\"order\":0}}","{\"bluetooth\":{\"side\":\"left\",\"order\":0.5}}",
                "{\"unknown_slot\":{\"side\":\"left\",\"order\":0}}","{\"bluetooth\":{\"side\":\"left\",\"side\":\"right\",\"order\":0}}"}) {
            String json=blank.replace("\"{}\"",org.json.JSONObject.quote(bad));checks++;
            try { ConfigTransfer.prepare(json,true);throw new AssertionError("malformed hint layout imported "+bad); }
            catch(java.io.IOException expected) { }
        }
        ActivationGuardPreferencesCheck.MemoryPreferences oldStore=new ActivationGuardPreferencesCheck.MemoryPreferences();
        oldStore.values.put(KEY,false);Upgrade166.migrate(oldStore);
        equal(false,oldStore.values.get(KEY),"existing child choice does not block adding missing hint layout");
        equal("{}",oldStore.values.get(NativeStatusIcons.LAYOUT),"only absent hint layout initializes");
        for(Object explicit:new Object[]{value,"{}",null,"bad"}) {
            oldStore.values.put(NativeStatusIcons.LAYOUT,explicit);int before=oldStore.writes;Upgrade166.migrate(oldStore);
            equal(explicit,oldStore.values.get(NativeStatusIcons.LAYOUT),"existing hint layout never overwritten");
            equal(before,oldStore.writes,"existing hint layout needs no migration write");
        }
    }
}
