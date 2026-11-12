package dev.puitheme;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Schema 27: the lock-screen notification options and the pull-down wallpaper's blur, pull and clock-zone settings: what the batch holds, how
 * an older configuration and an older framework copy are brought up to it, and that the wallpaper's first-release keys are not in it.
 */
public final class Upgrade118Check {
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade118 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static void check(boolean value, String reason) { equal(true, value, reason); }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 118L);
        return result;
    }
    public static void main(String[] args) {
        equal(52,SettingsFrameworkMirror.VERSION, "schema");
        // the batch: two lock options, the pull switch, 6 blurs, 2 x (zone switch, follow, blur, height, feather)
        // the music card: per scene two switches, a colour and its transparency switch, a tint colour and its switch, a strength
        // + the two page switches, the landscape pull-blur switch and its picture row; the speed's reserve; AOD's own look (6 settings, the mask's transparency switch, the notification strength)
        equal(2 + 2 + 2 + 6 + 2 * 5 + 2 * 7 + 1 + 1 + 8 + 2 - 1 + 10 - 1 + 2, Upgrade118.DEFAULTS.size(), "lock options, pull, blurs, zones, music card");
        for (String scene : new String[]{"light", "dark"}) {
            String card = QsMediaAppearance.prefix(scene);
            equal(false, Upgrade118.DEFAULTS.get(card + "_background_solid_enabled"), "the main colour is opt-in " + scene);
            equal(false, Upgrade118.DEFAULTS.get(card + "_background_custom_enabled"), "the custom colour is opt-in " + scene);
            equal(0f, Upgrade118.DEFAULTS.get(card + "_background_tint_opacity"), "no tint " + scene);
            equal(false, Upgrade118.DEFAULTS.get(StatusBarSettings.alphaKey(card + "_background_color")), "an opaque custom colour " + scene);
            equal(QsMediaAppearance.COLORS.get(card + "_background_color"), Upgrade118.DEFAULTS.get(card + "_background_color"), "its colour " + scene);
        }
        equal(false, Upgrade118.DEFAULTS.get(LockNotificationSettings.UNIFY_AOD), "same colour in AOD is opt-in");
        equal(true, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.BLUR_WITH_PULL), "blur with the pull is on");
        for (String scene : ShadeWallpaperSettings.SCENES) {
            equal(0f, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.blurKey(scene)), "blur " + scene);
            equal(null, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.enabledKey(scene)), "first-release switch is not in this batch " + scene);
            equal(null, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.brightnessKey(scene)), "first-release brightness is not in this batch " + scene);
            equal(null, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.revisionKey(scene)), "first-release picture is not in this batch " + scene);
            if (ShadeWallpaperSettings.hasZone(scene)) {
                equal(false, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.zoneEnabledKey(scene)), "zone off " + scene);
                equal(true, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.zoneFollowKey(scene)), "zone follows the clock " + scene);
            }
        }
        equal(null, Upgrade118.DEFAULTS.get(ShadeWallpaperSettings.MASTER), "the master is the first release's");
        // every key of the batch is a real setting with the same default
        for (Map.Entry<String, Object> item : Upgrade118.DEFAULTS.entrySet()) {
            String name = item.getKey();
            Object known = name.endsWith("_custom_alpha") ? (Object) StatusBarSettings.customAlpha(new LinkedHashMap<String, Object>(), name.substring(0, name.length() - "_custom_alpha".length()))
                    : item.getValue() instanceof Boolean ? StatusBarSettings.BOOLEAN_DEFAULTS.get(name)
                    : item.getValue() instanceof Float ? StatusBarSettings.NUMERIC_DEFAULTS.get(name)
                    : item.getValue() instanceof Integer ? StatusBarSettings.COLOR_DEFAULTS.get(name) : StatusBarSettings.STRING_DEFAULTS.get(name);
            equal(item.getValue(), known, "default of " + item.getKey());
        }
        // an older framework copy upgrades; one that already holds a new key is not a copy of that schema
        Bundle full = SettingsSnapshot.fromPreferences(new LinkedHashMap<>());
        check(SettingsSnapshot.complete(full), "a fresh current snapshot is complete");
        Map<String, Object> old = wire(full, 26);
        for (String key : Upgrade118.DEFAULTS.keySet()) old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        Map<String, Object> untouched = new LinkedHashMap<>(old);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "a schema 26 copy upgrades");
        equal(untouched, old, "the decoder does not edit the old copy");
        for (String key : Upgrade118.DEFAULTS.keySet()) equal(Upgrade118.DEFAULTS.get(key), upgraded.get(key), "default supplied " + key);
        for (String key : Upgrade118.DEFAULTS.keySet()) {
            Map<String, Object> mixed = new LinkedHashMap<>(old);
            mixed.put(key, Upgrade118.DEFAULTS.get(key));
            equal(null, SettingsFrameworkMirror.decode(mixed), "an older schema holding a new key is rejected " + key);
            Map<String, Object> missing = wire(full, SettingsFrameworkMirror.VERSION);
            missing.remove(key);
            equal(null, SettingsFrameworkMirror.decode(missing), "the current schema needs every key " + key);
        }
        // a configuration saved before: the defaults are added once, nothing else is touched
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade118.migrate(store);
        equal(0, store.writes, "an empty store stays empty");
        store.values.put(NotificationBigClockSettings.MASTER, true);
        store.values.put(ShadeWallpaperSettings.MASTER, true);
        Upgrade118.migrate(store);
        equal(1, store.writes, "all defaults are committed together");
        for (Map.Entry<String, Object> item : Upgrade118.DEFAULTS.entrySet()) {
            // the two page switches take the old master's value
            if (ShadeWallpaperSettings.PORTRAIT_MASTER.equals(item.getKey()) || ShadeWallpaperSettings.LANDSCAPE_MASTER.equals(item.getKey())) continue;
            equal(item.getValue(), store.values.get(item.getKey()), "initialised " + item.getKey());
        }
        equal(true, store.values.get(ShadeWallpaperSettings.MASTER), "the existing wallpaper switch is kept");
        equal(true, store.values.get(ShadeWallpaperSettings.PORTRAIT_MASTER), "an old master on carries over to the portrait page");
        equal(true, store.values.get(ShadeWallpaperSettings.LANDSCAPE_MASTER), "and to the landscape page");
        store.values.put(ShadeWallpaperSettings.blurKey(ShadeWallpaperSettings.NOTIFICATION_PORTRAIT), 18f);
        Upgrade118.migrate(store);
        equal(1, store.writes, "idempotent");
        equal(18f, store.values.get(ShadeWallpaperSettings.blurKey(ShadeWallpaperSettings.NOTIFICATION_PORTRAIT)), "a chosen blur is never overwritten");
        System.out.println("Upgrade118Check passed " + checks + " checks");
    }
}
