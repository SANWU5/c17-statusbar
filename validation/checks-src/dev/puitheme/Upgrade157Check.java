package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict schema 33 migration and an independently portable, default-off 2x1 fill choice. */
public final class Upgrade157Check {
    private static int checks;
    private static final String FILL = QsTileFullFill.ENABLED;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade157 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if (schema < 33) for (String key : Upgrade157.DEFAULTS.keySet()) result.remove(key);
        if (schema < 32) for (String key : Upgrade154.DEFAULTS.keySet()) result.remove(key);
        if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 157L);
        return result;
    }
    private static void mirrors() {
        equal(52,SettingsFrameworkMirror.VERSION, "current framework schema");
        equal(Collections.<String, Object>singletonMap(FILL, false), Upgrade157.DEFAULTS, "one independent neutral option");
        equal(Collections.singletonMap(FILL, false), QsTileFullFill.BOOLEANS, "runtime registers only the new Boolean");
        equal(false, Upgrade154.DEFAULTS.containsKey(FILL), "schema32 batch stays frozen");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FILL, true);
        values.put(AodNotificationBackground.ENABLED, true);
        values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true);
        values.put(StatusBarSettings.CLOCK_OFFSET_X, -8.25f);
        Bundle full = SettingsSnapshot.fromPreferences(values);
        equal(true, SettingsSnapshot.complete(full), "current snapshot is complete");
        equal(877 + Upgrade160.DEFAULTS.size() + Upgrade166.DEFAULTS.size() + Upgrade168.DEFAULTS.size() + Upgrade169.DEFAULTS.size() + Upgrade171.DEFAULTS.size() + Upgrade172.DEFAULTS.size() + Upgrade173.DEFAULTS.size() + Upgrade174.DEFAULTS.size() + Upgrade175.DEFAULTS.size() + Upgrade177.DEFAULTS.size() + Upgrade184.DEFAULTS.size() + Upgrade185.DEFAULTS.size() + Upgrade186.DEFAULTS.size() + Upgrade187.DEFAULTS.size() + Upgrade188.DEFAULTS.size() + Upgrade195.DEFAULTS.size() + Upgrade196.DEFAULTS.size() + Upgrade209.DEFAULTS.size() + Upgrade279.DEFAULTS.size(), full.keySet().size(), "frozen 877-field snapshot plus each subsequent independent batch");
        for (int schema : new int[]{29, 30, 31, 32}) {
            Map<String, Object> historical = wire(full, schema);
            for (Object mixed : new Object[]{false, true, null, 0, 0f, "false"}) {
                Map<String, Object> invalid = new LinkedHashMap<>(historical); invalid.put(FILL, mixed);
                equal(null, SettingsFrameworkMirror.decode(invalid), "old schema cannot carry a later key " + schema + "/" + mixed);
            }
            Map<String, Object> untouched = new LinkedHashMap<>(historical);
            Bundle decoded = SettingsFrameworkMirror.decode(historical);
            equal(true, SettingsSnapshot.complete(decoded), "complete historical snapshot upgrades " + schema);
            equal(untouched, historical, "decode never edits its historical input " + schema);
            for (String key : full.keySet()) {
                Object expected = Upgrade157.DEFAULTS.containsKey(key) ? false
                        : schema < 32 && Upgrade154.DEFAULTS.containsKey(key) ? Upgrade154.DEFAULTS.get(key)
                        : schema < 31 && Upgrade146.DEFAULTS.containsKey(key) ? Upgrade146.DEFAULTS.get(key)
                        : schema < 30 && Upgrade144.DEFAULTS.containsKey(key) ? Upgrade144.DEFAULTS.get(key) : full.get(key);
                equal(expected, decoded.get(key), "prior choice or neutral later default " + schema + "/" + key);
            }
            Map<String, Object> missing = new LinkedHashMap<>(historical); missing.remove(StatusBarSettings.CLOCK_OFFSET_X);
            equal(null, SettingsFrameworkMirror.decode(missing), "new default cannot mask an older missing field " + schema);
        }
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String, Object> current = wire(full, 33); current.put(FILL, enabled);
            Bundle decoded = SettingsFrameworkMirror.decode(current);
            equal(true, SettingsSnapshot.complete(decoded), "current Boolean accepted " + enabled);
            equal(enabled, decoded.get(FILL), "explicit fill choice preserved " + enabled);
            equal(true, decoded.get(AodNotificationBackground.ENABLED), "AOD choice remains independent " + enabled);
            equal(true, decoded.get(StatusBarLayoutSettings.WIFI_LABEL_SHARED), "status layout choice remains independent " + enabled);
        }
        Map<String, Object> missing = wire(full, 33); missing.remove(FILL);
        equal(null, SettingsFrameworkMirror.decode(missing), "schema33 requires the new Boolean");
        for (Object malformed : new Object[]{null, 0, 1L, 0f, "true", new Object()}) {
            Map<String, Object> wrong = wire(full, 33); wrong.put(FILL, malformed);
            equal(null, SettingsFrameworkMirror.decode(wrong), "current fill value stays strictly Boolean " + malformed);
        }
        ActivationGuardPreferencesCheck.MemoryPreferences remote = new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true, SettingsFrameworkMirror.write(remote, full, 157L), "complete current snapshot publishes");
        equal(52, remote.values.get(SettingsFrameworkMirror.SCHEMA), "publication advertises current schema51");
        equal(true, SettingsFrameworkMirror.decode(remote.getAll()).get(FILL), "published fill choice reads back");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade157.migrate(store); equal(0, store.writes, "empty locked storage stays empty");
        store.values.put(StatusBarSettings.CLOCK_OFFSET_X, -9.5f);
        store.values.put(AodNotificationBackground.ENABLED, true);
        store.values.put("local_note", "kept");
        Map<String, Object> expected = new LinkedHashMap<>(store.values); expected.put(FILL, false);
        Upgrade157.migrate(store); equal(1, store.writes, "one durable initialization");
        equal(expected, store.values, "only the absent neutral fill key is added");
        Upgrade157.migrate(store); equal(1, store.writes, "migration is idempotent");
        for (Object supplied : new Object[]{true, false, null, "legacy-invalid"}) {
            store.values.put(FILL, supplied); Map<String, Object> before = new LinkedHashMap<>(store.values);
            Upgrade157.migrate(store); equal(before, store.values, "explicit values are never overwritten " + supplied);
            equal(1, store.writes, "present values perform no write " + supplied);
        }
        store.values.remove(FILL); Upgrade154.migrate(store);
        equal(false, store.values.containsKey(FILL), "earlier migration cannot introduce the future option");
        Upgrade157.migrate(store); equal(false, store.values.get(FILL), "later initialization remains default-off");
        equal(true, store.values.get(AodNotificationBackground.ENABLED), "saved AOD selection survives");
    }
    private static void portable() throws Exception {
        equal(1, ConfigTransfer.SCHEMA_VERSION, "portable JSON schema does not change");
        equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(FILL), "portable Boolean is whitelisted");
        SettingsCatalog.Group group = SettingsCatalog.group("tile_2x1_fill");
        equal(true, group != null, "independent page exists");
        equal(SettingsCatalog.CONTROL_CENTER, group.category, "page belongs to control center");
        equal(FILL, group.masterKey, "page switch edits the new option");
        equal(1, group.items.size(), "one page switch does not duplicate the Boolean");
        int destinations = 0;
        for (SettingsCatalog.NavigationSection section : SettingsCatalog.navigationSections(SettingsCatalog.CONTROL_CENTER))
            for (SettingsCatalog.Group destination : section.groups) if (group.id.equals(destination.id)) destinations++;
        equal(1, destinations, "new page is reachable exactly once");
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String, Object> values = Collections.<String, Object>singletonMap(FILL, enabled);
            equal(values, ConfigTransfer.prepare(ConfigTransfer.exportJson(values, values.keySet()), true).values(), "explicit fill choice roundtrip " + enabled);
        }
        String prefix = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{";
        ConfigTransfer.PreparedImport old = ConfigTransfer.prepare(prefix + "\"speed_offset_x\":7.25}}", true);
        equal(false, old.values().containsKey(FILL), "old partial file never manufactures a fill selection");
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences(); store.values.put(FILL, true);
        equal(true, ConfigTransfer.commit(store, old), "old partial file commits");
        equal(true, store.values.get(FILL), "old partial import preserves saved fill selection");
        for (String invalid : new String[]{"null", "0", "1", "\"true\""}) {
            checks++;
            try { ConfigTransfer.prepare(prefix + "\"" + FILL + "\":" + invalid + "}}", true); throw new AssertionError("Accepted non-Boolean fill option"); }
            catch (IOException expected) { }
        }
    }
    public static void main(String[] arguments) throws Exception {
        mirrors(); migration(); portable();
        System.out.println("Upgrade157Check passed " + checks + " checks");
    }
}
