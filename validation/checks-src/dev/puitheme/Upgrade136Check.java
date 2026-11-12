package dev.puitheme;

import android.os.Bundle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistence survives configuration boundaries without enabling itself or changing older choices. */
public final class Upgrade136Check {
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade136 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 136L);
        return result;
    }
    private static void mirrors() {
        equal(52,SettingsFrameworkMirror.VERSION, "framework schema");
        equal(Collections.<String, Object>singletonMap(CapsulePersistence.ENABLED, false), Upgrade136.DEFAULTS, "one opt-in");
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put(FluidCloudCapsule.MASTER, false);
        saved.put(FluidCloudCapsule.BLUR_ENABLED, true);
        saved.put(FluidCloudCapsule.FILL_COLOR, 0xff459ae2);
        saved.put(NotificationBigClockSettings.PROPORTIONAL_DIGITS, true);
        saved.put(NotificationBigClockSettings.TIME_ANIMATION_DISABLED, true);
        saved.put(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE, 13.7f);
        saved.put(StatusBarSettings.DATA_OFFSET_X, -3.2f);
        saved.put(StatusBarSettings.SPEED_OFFSET_X, 17.25f);
        Bundle full = SettingsSnapshot.fromPreferences(saved);
        equal(true, SettingsSnapshot.complete(full), "current snapshot complete");
        Map<String, Object> old = wire(full, 28);
        old.remove(CapsulePersistence.ENABLED);
        Map<String, Object> unchanged = new LinkedHashMap<>(old);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        equal(true, SettingsSnapshot.complete(upgraded), "complete beta52 wire upgrades");
        equal(unchanged, old, "input unchanged");
        for (String key : full.keySet()) equal(full.get(key), upgraded.get(key), "old choice preserved " + key);
        equal(false, upgraded.get(CapsulePersistence.ENABLED), "old settings stay native");
        for (Object value : new Object[]{false, true, null, 0, "false"}) {
            Map<String, Object> mixed = new LinkedHashMap<>(old);
            mixed.put(CapsulePersistence.ENABLED, value);
            equal(null, SettingsFrameworkMirror.decode(mixed), "schema28 cannot contain a later field " + value);
        }
        Map<String, Object> missingOld = new LinkedHashMap<>(old);
        missingOld.remove(StatusBarSettings.SPEED_OFFSET_X);
        equal(null, SettingsFrameworkMirror.decode(missingOld), "new default cannot cover missing old settings");
        Map<String, Object> missingNew = wire(full, 29);
        missingNew.remove(CapsulePersistence.ENABLED);
        equal(null, SettingsFrameworkMirror.decode(missingNew), "schema29 requires new field");
        for (Object invalid : new Object[]{null, 0, 1f, "true"}) {
            Map<String, Object> wrong = wire(full, 29);
            wrong.put(CapsulePersistence.ENABLED, invalid);
            equal(null, SettingsFrameworkMirror.decode(wrong), "strict boolean type " + invalid);
        }
        for (boolean persistent : new boolean[]{false, true}) {
            Map<String, Object> current = wire(full, 29);
            current.put(CapsulePersistence.ENABLED, persistent);
            Bundle kept = SettingsFrameworkMirror.decode(current);
            equal(true, SettingsSnapshot.complete(kept), "each current state decodes " + persistent);
            equal(persistent, kept.get(CapsulePersistence.ENABLED), "opt-in retained " + persistent);
            equal(false, kept.get(FluidCloudCapsule.MASTER), "appearance remains independent");
        }
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade136.migrate(store);
        equal(0, store.writes, "locked empty storage stays empty");
        store.values.put(FluidCloudCapsule.MASTER, false);
        store.values.put(StatusBarSettings.SPEED_OFFSET_X, -9.5f);
        store.values.put("local_note", "kept");
        Map<String, Object> expected = new LinkedHashMap<>(store.values);
        expected.put(CapsulePersistence.ENABLED, false);
        Upgrade136.migrate(store);
        equal(1, store.writes, "one durable migration");
        equal(expected, store.values, "only missing opt-in added");
        Upgrade136.migrate(store);
        equal(1, store.writes, "idempotent");
        store.values.put(CapsulePersistence.ENABLED, true);
        Upgrade136.migrate(store);
        equal(1, store.writes, "explicit opt-in never overwritten");
        equal(true, store.values.get(CapsulePersistence.ENABLED), "explicit opt-in kept");
    }
    private static void portable() throws Exception {
        equal(1, ConfigTransfer.SCHEMA_VERSION, "portable format unchanged");
        equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(CapsulePersistence.ENABLED), "registered portable type");
        Map<String, Object> defaults = ConfigTransfer.prepare(ConfigTransfer.exportJson(Collections.emptyMap()), true).values();
        equal(false, defaults.get(CapsulePersistence.ENABLED), "fresh export off");
        Map<String, Object> current = Collections.<String, Object>singletonMap(CapsulePersistence.ENABLED, true);
        equal(true, ConfigTransfer.prepare(ConfigTransfer.exportJson(current, current.keySet()), true).values().get(CapsulePersistence.ENABLED), "selected-page roundtrip");
        String legacy = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{\"speed_offset_x\":7.25}}";
        ConfigTransfer.PreparedImport imported = ConfigTransfer.prepare(legacy, true);
        equal(false, imported.values().containsKey(CapsulePersistence.ENABLED), "partial old import leaves new switch alone");
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        store.values.put(CapsulePersistence.ENABLED, true);
        equal(true, ConfigTransfer.commit(store, imported), "old partial import commits");
        Upgrade136.migrate(store);
        equal(true, store.values.get(CapsulePersistence.ENABLED), "partial import keeps chosen persistence");
        equal(7.25f, store.values.get(StatusBarSettings.SPEED_OFFSET_X), "old selected setting still imported");
    }
    private static void editor() {
        SettingsCatalog.Item item = SettingsCatalog.item(CapsulePersistence.ENABLED);
        equal("fluid_cloud_capsule", item.groupId, "camera page");
        equal("常驻流体云", item.title, "requested label");
        equal(SettingsCatalog.BOOLEAN, item.type, "switch type");
        equal(false, item.master, "independent switch");
        equal(false, item.defaultValue, "editor off by default");
        equal(Collections.singletonList(item), SettingsCatalog.commonDetailItems(SettingsCatalog.group(item.groupId)), "reachable from every appearance tab");
        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put(FluidCloudCapsule.MASTER, false);
        appearance.put(FluidCloudCapsule.BLUR_ENABLED, true);
        appearance.put(FluidCloudAccent.ENABLED, false);
        equal("", SettingsCatalog.unavailableReason(item.key, appearance), "appearance switches cannot block behaviour");
        equal(true, item.description.contains("手动收起"), "manual control explained");
    }
    public static void main(String[] args) throws Exception {
        mirrors(); migration(); portable(); editor();
        System.out.println("Upgrade136Check passed " + checks + " checks");
    }
}
