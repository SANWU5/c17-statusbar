package dev.puitheme;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;

/** A complete code104 (schema 21) mirror upgrades with "使隐藏可点击" off; a partial new write is still rejected. */
public final class Upgrade105Check {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Upgrade105 " + checks); }
    public static void main(String[] args) throws Exception {
        Bundle defaults = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(defaults.containsKey(QsEntrances.HIDDEN_CLICKABLE) && !defaults.getBoolean(QsEntrances.HIDDEN_CLICKABLE));
        Map<String, Object> old = new LinkedHashMap<>();
        for (String key : defaults.keySet()) old.put(key, defaults.get(key));
        for (String key : Upgrade105.DEFAULTS.keySet()) old.remove(key);
        for (String key : Upgrade106.DEFAULTS.keySet()) old.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        old.put(SettingsFrameworkMirror.SCHEMA, 21); old.put(SettingsFrameworkMirror.REVISION, 105L);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded));
        check(!upgraded.getBoolean(QsEntrances.HIDDEN_CLICKABLE));
        for (String key : Upgrade105.DEFAULTS.keySet()) {
            Map<String, Object> partial = new LinkedHashMap<>(old);
            partial.put(key, Upgrade105.DEFAULTS.get(key));
            check(SettingsFrameworkMirror.decode(partial) == null);
        }
        Map<String, Object> setting = new LinkedHashMap<>();
        setting.put(QsEntrances.MASTER, true); setting.put(QsEntrances.EDIT_HIDDEN, true); setting.put(QsEntrances.HIDDEN_CLICKABLE, true);
        ConfigTransfer.PreparedImport imported = ConfigTransfer.prepare(ConfigTransfer.exportJson(setting), true);
        check(Boolean.TRUE.equals(imported.values().get(QsEntrances.HIDDEN_CLICKABLE)));
        check(SettingsCatalog.item(QsEntrances.HIDDEN_CLICKABLE) != null);
        check(FeatureOptions.from(setting).effective(QsEntrances.GROUP, QsEntrances.HIDDEN_CLICKABLE));
        setting.put(QsEntrances.MASTER, false); check(!FeatureOptions.from(setting).effective(QsEntrances.GROUP, QsEntrances.HIDDEN_CLICKABLE));
        System.out.println("Upgrade105Check passed " + checks + " checks");
    }
}
