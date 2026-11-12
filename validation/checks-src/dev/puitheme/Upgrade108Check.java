package dev.puitheme;

import android.os.Bundle;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared network spacing: strict historical/current mirrors, durable migration and one common editor. */
public final class Upgrade108Check {
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade108 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static void check(boolean value, String reason) { equal(true, value, reason); }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 108L);
        return result;
    }
    private static Map<String, Object> wire25(Bundle snapshot) {
        Map<String, Object> result = wire(snapshot, 25);
        for (String key : Upgrade117.DEFAULTS.keySet()) result.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key);
        return result;
    }
    private static void mirrors() {
        check(SettingsFrameworkMirror.VERSION >= 25, "schema");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put(NetworkSlotGeometry.MARGIN_LEFT, 0f);
        expected.put(NetworkSlotGeometry.MARGIN_RIGHT, 0f);
        equal(expected, Upgrade108.DEFAULTS, "only two neutral additions");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(NetworkSlotGeometry.POSITION, true);
        values.put(NetworkSlotGeometry.OFFSET_X, -3.5f);
        values.put(NetworkSlotGeometry.OFFSET_Y, 2.25f);
        values.put(StatusBarSettings.SLOT_WIDTH, 29f);
        values.put(NotificationMiniStack.ENABLED, true);
        Bundle full = SettingsSnapshot.fromPreferences(values);
        check(SettingsSnapshot.complete(full), "fresh current snapshot is complete");
        Map<String, Object> old = wire(full, 24);
        for (String key : Upgrade108.DEFAULTS.keySet()) old.remove(key); for(String key:Upgrade117.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade118.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade135.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade136.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade144.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade146.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade154.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade157.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade160.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade168.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade169.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade171.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade172.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade173.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade174.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade175.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade177.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade184.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade185.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade186.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade187.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade188.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade195.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade196.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade209.DEFAULTS.keySet())old.remove(key);for(String key:Upgrade279.DEFAULTS.keySet())old.remove(key); for(String key:Upgrade166.DEFAULTS.keySet())old.remove(key);
        Map<String, Object> untouched = new LinkedHashMap<>(old);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        check(upgraded != null && SettingsSnapshot.complete(upgraded), "complete schema24 upgrades");
        equal(untouched, old, "decoder does not edit the old copy");
        for (String key : full.keySet()) equal(full.get(key), upgraded.get(key), "old options preserved " + key);
        String[] keys = Upgrade108.DEFAULTS.keySet().toArray(new String[0]);
        for (int mask = 1; mask < 4; mask++) {
            Map<String, Object> mixed = new LinkedHashMap<>(old);
            for (int index = 0; index < keys.length; index++) if ((mask & (1 << index)) != 0) mixed.put(keys[index], 0f);
            equal(null, SettingsFrameworkMirror.decode(mixed), "old schema containing any new batch is rejected " + mask);
        }
        for (String key : keys) {
            Map<String, Object> partial = new LinkedHashMap<>(old);
            partial.put(key, null);
            equal(null, SettingsFrameworkMirror.decode(partial), "a present null is still a mixed batch " + key);
            Map<String, Object> missing = wire25(full);
            missing.remove(key);
            equal(null, SettingsFrameworkMirror.decode(missing), "current schema needs both margins " + key);
            for (Object invalid : new Object[]{null, 1, 1L, true, "0", Float.NaN, Float.POSITIVE_INFINITY}) {
                Map<String, Object> wrong = wire25(full);
                wrong.put(key, invalid);
                equal(null, SettingsFrameworkMirror.decode(wrong), "current margin is a finite Float " + key + "/" + invalid);
            }
        }
        Map<String, Object> incomplete = new LinkedHashMap<>(old);
        incomplete.remove(StatusBarSettings.SLOT_WIDTH);
        equal(null, SettingsFrameworkMirror.decode(incomplete), "new defaults cannot mask an absent old field");
        values.put(NetworkSlotGeometry.MARGIN_LEFT, 4.5f);
        values.put(NetworkSlotGeometry.MARGIN_RIGHT, 7.25f);
        Bundle custom = SettingsFrameworkMirror.decode(wire25(SettingsSnapshot.fromPreferences(values)));
        equal(4.5f, custom.get(NetworkSlotGeometry.MARGIN_LEFT), "custom left kept");
        equal(7.25f, custom.get(NetworkSlotGeometry.MARGIN_RIGHT), "custom right kept independently");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade108.migrate(store);
        equal(0, store.writes, "an empty store stays empty");
        store.values.put(NetworkSlotGeometry.OFFSET_X, -4f);
        store.values.put(NetworkSlotGeometry.POSITION, true);
        store.values.put(NotificationMiniStack.ENABLED, true);
        store.values.put("local_font_note", "keep");
        Upgrade108.migrate(store);
        equal(1, store.writes, "both defaults committed together");
        equal(0f, store.values.get(NetworkSlotGeometry.MARGIN_LEFT), "left initialized");
        equal(0f, store.values.get(NetworkSlotGeometry.MARGIN_RIGHT), "right initialized");
        equal(-4f, store.values.get(NetworkSlotGeometry.OFFSET_X), "position retained");
        equal(true, store.values.get(NotificationMiniStack.ENABLED), "mini stack opt-in retained");
        equal("keep", store.values.get("local_font_note"), "unrelated local metadata retained");
        store.values.put(NetworkSlotGeometry.MARGIN_LEFT, 6f);
        Upgrade108.migrate(store);
        equal(1, store.writes, "migration is idempotent");
        equal(6f, store.values.get(NetworkSlotGeometry.MARGIN_LEFT), "explicit custom left never overwritten");
        store.values.remove(NetworkSlotGeometry.MARGIN_RIGHT);
        Upgrade108.migrate(store);
        equal(2, store.writes, "only a missing counterpart is initialized");
        equal(6f, store.values.get(NetworkSlotGeometry.MARGIN_LEFT), "independent custom left retained");
        equal(0f, store.values.get(NetworkSlotGeometry.MARGIN_RIGHT), "missing right neutral");
    }
    private static void editor() throws Exception {
        check(SettingsCatalog.group("network")!=null,"merged editor reuses published independent keys");
        Map<String,Object> values=new LinkedHashMap<>();
        values.put(NetworkSlotGeometry.POSITION,true);values.put(RadioState.BOTH_VISIBLE,true);
        values.put(NetworkSlotGeometry.MARGIN_LEFT,3.75f);values.put(NetworkSlotGeometry.MARGIN_RIGHT,8.5f);
        ConfigTransfer.PreparedImport imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(values),true);
        for (String key:Upgrade108.DEFAULTS.keySet()) {
            equal(null,SettingsCatalog.item(key),"retired shared editor absent "+key);
            check(SettingsCatalog.hiddenKeys().contains(key),"retired shared setting hidden "+key);
            equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(key),"historical numeric type "+key);
            equal(values.get(key),imported.values().get(key),"saved margin remains portable "+key);
            check(ConfigScope.keys(Arrays.asList(ConfigScope.OTHER_ID)).contains(key),"unpaged history still exported "+key);
        }
        check(SettingsCatalog.applicable(SettingsCatalog.item("wifi_position_enabled"),values),"public Wi-Fi position independent of retired settings");
        check(SettingsCatalog.applicable(SettingsCatalog.item("label_position_enabled"),values),"public label position independent of retired settings");
        equal(true,values.get(RadioState.BOTH_VISIBLE),"viewing public pages does not erase saved history");
    }
    public static void main(String[] args) throws Exception {
        mirrors(); migration(); editor();
        System.out.println("Upgrade108Check passed " + checks + " checks");
    }
}
