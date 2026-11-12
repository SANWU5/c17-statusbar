package dev.puitheme;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict schema27/28 framework boundaries and durable/portable big-clock options. */
public final class Upgrade135Check {
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade135 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static void check(boolean value, String reason) { equal(true, value, reason); }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if (schema < 29) result.remove(CapsulePersistence.ENABLED);
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 135L);
        return result;
    }
    private static void mirrors() {
        equal(52,SettingsFrameworkMirror.VERSION, "new framework schema");
        equal(4, Upgrade135.DEFAULTS.size(), "two options, two orientations");
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put(NotificationBigClockSettings.MASTER, true);
        saved.put(NotificationBigClockSettings.LANDSCAPE_MASTER, true);
        saved.put(NotificationBigClockSettings.FONT, NotificationBigClockSettings.FONT_NATIVE_DIGIT);
        saved.put(NotificationBigClockSettings.LETTER_SPACING, -.08f);
        saved.put(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.LETTER_SPACING), .12f);
        saved.put(StatusBarSettings.DATA_OFFSET_X, -7.25f);
        Bundle full = SettingsSnapshot.fromPreferences(saved);
        check(SettingsSnapshot.complete(full), "fresh current snapshot complete");
        Map<String, Object> old = wire(full, 27);
        for (String key : Upgrade135.DEFAULTS.keySet()) old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        Map<String, Object> untouched = new LinkedHashMap<>(old);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(SettingsSnapshot.complete(upgraded), "complete schema27 upgrades");
        equal(untouched, old, "historical input unmodified");
        for (String key : full.keySet()) equal(full.get(key), upgraded.get(key), "old choice preserved " + key);
        String[] keys = Upgrade135.DEFAULTS.keySet().toArray(new String[0]);
        for (String key : keys) {
            equal(false, StatusBarSettings.BOOLEAN_DEFAULTS.get(key), "option remains off until chosen " + key);
            equal(false, upgraded.get(key), "historical default " + key);
            Map<String, Object> missing = wire(full, 28);
            missing.remove(key);
            equal(null, SettingsFrameworkMirror.decode(missing), "current schema must contain every option " + key);
            for (Object invalid : new Object[]{null, 0, 1f, "false"}) {
                Map<String, Object> malformed = wire(full, 28);
                malformed.put(key, invalid);
                equal(null, SettingsFrameworkMirror.decode(malformed), "boolean type is strict " + key + "/" + invalid);
            }
            Map<String, Object> mixedNull = new LinkedHashMap<>(old);
            mixedNull.put(key, null);
            equal(null, SettingsFrameworkMirror.decode(mixedNull), "present null is not a historical absence " + key);
        }
        for (int mask = 1; mask < 16; mask++) {
            Map<String, Object> mixed = new LinkedHashMap<>(old);
            for (int index = 0; index < keys.length; index++) if ((mask & (1 << index)) != 0) mixed.put(keys[index], false);
            equal(null, SettingsFrameworkMirror.decode(mixed), "schema27 containing any new batch rejected " + mask);
        }
        Map<String, Object> missingOld = new LinkedHashMap<>(old);
        missingOld.remove(NotificationBigClockSettings.LETTER_SPACING);
        equal(null, SettingsFrameworkMirror.decode(missingOld), "new defaults cannot hide a missing old choice");
        for (int mask = 0; mask < 16; mask++) {
            Map<String, Object> custom = wire(full, 28);
            for (int index = 0; index < keys.length; index++) custom.put(keys[index], (mask & (1 << index)) != 0);
            Bundle retained = SettingsFrameworkMirror.decode(custom);
            check(SettingsSnapshot.complete(retained), "every current independent combination decodes " + mask);
            for (String key : keys) equal(custom.get(key), retained.get(key), "independent opt-in retained " + key + "/" + mask);
        }
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade135.migrate(store);
        equal(0, store.writes, "empty locked storage stays empty");
        store.values.put(NotificationBigClockSettings.MASTER, true);
        store.values.put(NotificationBigClockSettings.LETTER_SPACING, -.08f);
        store.values.put("local_note", "retained");
        store.values.put(NotificationBigClockSettings.PROPORTIONAL_DIGITS, true);
        Upgrade135.migrate(store);
        equal(1, store.writes, "one durable batch");
        equal(true, store.values.get(NotificationBigClockSettings.PROPORTIONAL_DIGITS), "explicit portrait opt-in retained");
        equal(false, store.values.get(NotificationBigClockSettings.TIME_ANIMATION_DISABLED), "missing animation setting keeps native behaviour");
        equal(false, store.values.get(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.PROPORTIONAL_DIGITS)), "landscape remains independent");
        equal(false, store.values.get(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.TIME_ANIMATION_DISABLED)), "landscape animation remains native");
        equal(true, store.values.get(NotificationBigClockSettings.MASTER), "existing clock kept");
        equal(-.08f, store.values.get(NotificationBigClockSettings.LETTER_SPACING), "existing spacing kept");
        equal("retained", store.values.get("local_note"), "local metadata kept");
        Upgrade135.migrate(store);
        equal(1, store.writes, "idempotent");
        String landscapeAnimation = NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.TIME_ANIMATION_DISABLED);
        store.values.put(landscapeAnimation, true);
        store.values.remove(NotificationBigClockSettings.TIME_ANIMATION_DISABLED);
        Upgrade135.migrate(store);
        equal(2, store.writes, "missing counterpart repaired together");
        equal(true, store.values.get(landscapeAnimation), "landscape opt-in never overwritten");
        equal(false, store.values.get(NotificationBigClockSettings.TIME_ANIMATION_DISABLED), "portrait remains default");
    }
    private static void portable() throws Exception {
        equal(1, ConfigTransfer.SCHEMA_VERSION, "portable format unchanged");
        Map<String, Object> original = new LinkedHashMap<>();
        original.put(NotificationBigClockSettings.MASTER, true);
        original.put(NotificationBigClockSettings.LETTER_SPACING, -.06f);
        Map<String, Object> exported = ConfigTransfer.prepare(ConfigTransfer.exportJson(original), true).values();
        for (String key : Upgrade135.DEFAULTS.keySet()) {
            equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(key), "portable boolean " + key);
            equal(false, exported.get(key), "old export defaults off " + key);
        }
        String legacy = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{\"notification_big_clock_enabled\":true,\"notification_big_clock_letter_spacing\":-0.06}}";
        ConfigTransfer.PreparedImport imported = ConfigTransfer.prepare(legacy, true);
        for (String key : Upgrade135.DEFAULTS.keySet()) {
            check(!imported.values().containsKey(key), "partial old import does not invent an opt-in " + key);
            equal(false, StatusBarSettings.bool(imported.values(), key), "absent old option resolves off " + key);
        }
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        store.values.put(NotificationBigClockSettings.PROPORTIONAL_DIGITS, true);
        check(ConfigTransfer.commit(store, imported), "old import commits");
        Upgrade135.migrate(store);
        equal(true, store.values.get(NotificationBigClockSettings.PROPORTIONAL_DIGITS), "partial import retains an existing new choice");
        equal(false, store.values.get(NotificationBigClockSettings.TIME_ANIMATION_DISABLED), "untouched missing option defaults off");
        equal(-.06f, store.values.get(NotificationBigClockSettings.LETTER_SPACING), "imported old spacing retained");
        Map<String, Object> current = new LinkedHashMap<>();
        for (String key : Upgrade135.DEFAULTS.keySet()) current.put(key, true);
        Map<String, Object> roundtrip = ConfigTransfer.prepare(ConfigTransfer.exportJson(current, current.keySet()), true).values();
        for (String key : Upgrade135.DEFAULTS.keySet()) equal(true, roundtrip.get(key), "portable opt-in survives " + key);
    }
    public static void main(String[] args) throws Exception {
        mirrors(); migration(); portable();
        System.out.println("Upgrade135Check passed " + checks + " checks");
    }
}
