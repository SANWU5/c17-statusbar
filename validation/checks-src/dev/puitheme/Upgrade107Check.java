package dev.puitheme;
import android.os.Bundle;
import java.util.*;
/** Legacy network positions upgrade once; shared controls, restored badge choice and mirror boundaries stay coherent. */
public final class Upgrade107Check {
    private static int checks;
    private static void check(boolean value) { if(!value)throw new AssertionError("Upgrade107 "+(++checks));checks++; }
    public static void main(String[] args) throws Exception {
        check(SettingsFrameworkMirror.VERSION == 52 && Upgrade107.DEFAULTS.size()==4);
        Map<String,Object> values=new LinkedHashMap<>();
        check(!StatusBarSettings.bool(values,RadioState.BOTH_VISIBLE));
        check(!StatusBarSettings.bool(values,NetworkSlotGeometry.POSITION));
        values.put("wifi_enabled",true);values.put(StatusBarSettings.WIFI_OFFSET_X,7f);
        check(StatusBarSettings.bool(values,NetworkSlotGeometry.POSITION));
        check(StatusBarSettings.settingNumber(values,NetworkSlotGeometry.OFFSET_X,0f)==7f);
        values.put("label_enabled",true);values.put(StatusBarSettings.LABEL_OFFSET_X,-3f);
        check(StatusBarSettings.settingNumber(values,NetworkSlotGeometry.OFFSET_X,0f)==-3f);
        values.put("label_position_enabled",false);
        check(StatusBarSettings.settingNumber(values,NetworkSlotGeometry.OFFSET_X,0f)==7f);
        values.put(RadioState.ALWAYS_SHOWN,true);
        check(!StatusBarSettings.bool(values,RadioState.BOTH_VISIBLE));
        values.put(RadioState.BOTH_VISIBLE,false);
        check(!StatusBarSettings.bool(values,RadioState.BOTH_VISIBLE));
        Bundle full=SettingsSnapshot.fromPreferences(values);
        Map<String,Object> old=new LinkedHashMap<>();for(String key:full.keySet())old.put(key,full.get(key));
        for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        old.put(SettingsFrameworkMirror.SCHEMA,23);old.put(SettingsFrameworkMirror.REVISION,107L);
        Bundle upgraded=SettingsFrameworkMirror.decode(old);
        check(upgraded!=null && SettingsSnapshot.complete(upgraded));
        check(!upgraded.getBoolean(RadioState.BOTH_VISIBLE) && ((Number)upgraded.get(NetworkSlotGeometry.OFFSET_X)).floatValue()==7f);
        for(String key:Upgrade107.DEFAULTS.keySet()) {
            Map<String,Object> partial=new LinkedHashMap<>(old);partial.put(key,Upgrade107.DEFAULTS.get(key));
            check(SettingsFrameworkMirror.decode(partial)==null);
        }
        old.put(SettingsFrameworkMirror.SCHEMA,24);check(SettingsFrameworkMirror.decode(old)==null);
        values.put(NotificationMiniStack.HIDE_BADGE,true);
        check(StatusBarSettings.bool(values,NotificationMiniStack.HIDE_BADGE));
        check(SettingsSnapshot.fromPreferences(values).getBoolean(NotificationMiniStack.HIDE_BADGE));
        check(Boolean.TRUE.equals(ConfigTransfer.prepare(ConfigTransfer.exportJson(values),true).values().get(NotificationMiniStack.HIDE_BADGE)));
        check(SettingsCatalog.group("network")!=null);
        check("network".equals(SettingsCatalog.canonicalGroupId("wifi"))&&"network".equals(SettingsCatalog.canonicalGroupId("label")));
        check(SettingsCatalog.item(NetworkSlotGeometry.POSITION)==null);
        for(String key:new String[]{NetworkSlotGeometry.POSITION,NetworkSlotGeometry.OFFSET_X,NetworkSlotGeometry.OFFSET_Y,RadioState.BOTH_VISIBLE})
            check(SettingsCatalog.hiddenKeys().contains(key));
        check(SettingsCatalog.applicable(SettingsCatalog.item("wifi_position_enabled"),values));
        check(SettingsCatalog.applicable(SettingsCatalog.item("label_position_enabled"),values));
        values.put(RadioState.BOTH_VISIBLE,true);
        check(SettingsCatalog.applicable(SettingsCatalog.item("wifi_position_enabled"),values));
        check(SettingsCatalog.applicable(SettingsCatalog.item("label_position_enabled"),values));
        check(StatusBarSettings.bool(values,RadioState.BOTH_VISIBLE));
        check(SettingsCatalog.isVisible(NotificationMiniStack.HIDE_BADGE));
        ActivationGuardPreferencesCheck.MemoryPreferences preferences=new ActivationGuardPreferencesCheck.MemoryPreferences();
        preferences.values.put(NotificationMiniStack.HIDE_BADGE,true);
        Upgrade107.migrate(preferences);
        check(Boolean.TRUE.equals(preferences.values.get(NotificationMiniStack.HIDE_BADGE)));
        preferences.values.put("wifi_enabled",true);preferences.values.put(StatusBarSettings.WIFI_OFFSET_X,9f);
        Upgrade107.migrate(preferences);
        check(Boolean.TRUE.equals(preferences.values.get(NotificationMiniStack.HIDE_BADGE)));
        ConfigTransfer.PreparedImport unrelated=ConfigTransfer.prepare("{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{\"speed_enabled\":true}}",true);
        check(ConfigTransfer.commit(preferences,unrelated));
        check(Boolean.TRUE.equals(preferences.values.get(NotificationMiniStack.HIDE_BADGE)));
        System.out.println("Upgrade107Check passed "+checks+" checks");
    }
}
