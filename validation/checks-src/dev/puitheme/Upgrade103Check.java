package dev.puitheme;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;

/** A complete code102 mirror upgrades, while a partial new write is still rejected. */
public final class Upgrade103Check {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Upgrade103 " + checks); }
    public static void main(String[] args) throws Exception {
        Bundle defaults = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        Map<String, Object> old = new LinkedHashMap<>();
        for (String key : defaults.keySet()) old.put(key, defaults.get(key));
        for (String key : Upgrade103.DEFAULTS.keySet()) old.remove(key);for(String key:Upgrade105.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade106.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade107.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade108.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        old.put(SettingsFrameworkMirror.SCHEMA, 20); old.put(SettingsFrameworkMirror.REVISION, 103L);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded));
        check(!upgraded.getBoolean(FluidCloudCapsule.COVER_SIZE_ENABLED));
        check(Float.valueOf(100f).equals(upgraded.get(FluidCloudCapsule.COVER_SCALE)));
        for (String key : Upgrade103.DEFAULTS.keySet()) {
            Map<String, Object> partial = new LinkedHashMap<>(old);
            partial.put(key, Upgrade103.DEFAULTS.get(key));
            check(SettingsFrameworkMirror.decode(partial) == null);
        }
        Map<String, Object> setting = new LinkedHashMap<>();
        setting.put(FluidCloudCapsule.COVER_SIZE_ENABLED, true); setting.put(FluidCloudCapsule.COVER_SCALE, 135f);
        ConfigTransfer.PreparedImport imported = ConfigTransfer.prepare(ConfigTransfer.exportJson(setting), true);
        check(imported.values().get(FluidCloudCapsule.COVER_SCALE).equals(135f));
        check(imported.values().get(FluidCloudCapsule.COVER_SIZE_ENABLED).equals(true));
        check(SettingsCatalog.item(FluidCloudCapsule.COVER_SCALE) != null);
        // Post-1.8.2 saved network keys remain portable but never change the restored public renderer.
        setting.put(RadioState.WIFI_MERGED, false);setting.put(RadioState.BOTH_VISIBLE, true);
        check(FeatureOptions.from(setting).isEnabled(RadioState.BOTH_VISIBLE));
        check(!FeatureOptions.from(setting).effective("network",RadioState.BOTH_VISIBLE));
        setting.put(RadioState.WIFI_MERGED,true);setting.put(StatusBarSettings.SAFE_MODE,true);
        check(!FeatureOptions.from(setting).effective("label",RadioState.WIFI_MERGED));
        System.out.println("Upgrade103Check passed " + checks + " checks");
    }
}
