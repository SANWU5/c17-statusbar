package dev.puitheme;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;

/** A complete code105 (schema 22) mirror upgrades with the mini stack and its badge switch off; a partial new write is rejected. */
public final class Upgrade106Check {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Upgrade106 " + checks); }
    public static void main(String[] args) throws Exception {
        check(SettingsFrameworkMirror.VERSION == 52);
        check(Upgrade106.DEFAULTS.size() == 2 && Boolean.FALSE.equals(Upgrade106.DEFAULTS.get(NotificationMiniStack.ENABLED))
                && Boolean.FALSE.equals(Upgrade106.DEFAULTS.get(NotificationMiniStack.HIDE_BADGE)));
        Bundle defaults = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(defaults.containsKey(NotificationMiniStack.ENABLED) && !defaults.getBoolean(NotificationMiniStack.ENABLED));
        Map<String, Object> old = new LinkedHashMap<>();
        for (String key : defaults.keySet()) old.put(key, defaults.get(key));
        for (String key : Upgrade106.DEFAULTS.keySet()) old.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        old.put(SettingsFrameworkMirror.SCHEMA, 22); old.put(SettingsFrameworkMirror.REVISION, 106L);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded));
        check(!upgraded.getBoolean(NotificationMiniStack.ENABLED) && !upgraded.getBoolean(NotificationMiniStack.HIDE_BADGE));
        for (String key : Upgrade106.DEFAULTS.keySet()) {
            Map<String, Object> partial = new LinkedHashMap<>(old);
            partial.put(key, Upgrade106.DEFAULTS.get(key));
            check(SettingsFrameworkMirror.decode(partial) == null);
        }
        // A current (schema 23) copy without the keys is incomplete and refused.
        Map<String, Object> missing = new LinkedHashMap<>(old); missing.put(SettingsFrameworkMirror.SCHEMA, 24);
        check(SettingsFrameworkMirror.decode(missing) == null);
        // The mini stack and its restored count setting are portable; historical missing keys still default off.
        Map<String, Object> setting = new LinkedHashMap<>();
        setting.put(NotificationMiniStack.ENABLED, true); setting.put(NotificationMiniStack.HIDE_BADGE, true);
        ConfigTransfer.PreparedImport imported = ConfigTransfer.prepare(ConfigTransfer.exportJson(setting), true);
        check(Boolean.TRUE.equals(imported.values().get(NotificationMiniStack.ENABLED)));
        check(Boolean.TRUE.equals(imported.values().get(NotificationMiniStack.HIDE_BADGE)));
        check(SettingsCatalog.item(NotificationMiniStack.ENABLED) != null && SettingsCatalog.item(NotificationMiniStack.HIDE_BADGE) != null);
        check(!SettingsCatalog.hiddenKeys().contains(NotificationMiniStack.ENABLED));
        // The export never carries the source acknowledgement.
        check(!ConfigTransfer.exportJson(setting).contains("acknowledged"));
        System.out.println("Upgrade106Check passed " + checks + " checks");
    }
}
