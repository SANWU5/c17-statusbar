package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Schema 31 preserves independent positions and strictly upgrades historical framework snapshots. */
public final class Upgrade146Check {
    private static int checks;
    private static final String SHARED = StatusBarLayoutSettings.WIFI_LABEL_SHARED;

    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual))
            throw new AssertionError("Upgrade146 " + checks + " " + reason + ": " + expected + " != " + actual);
    }

    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 146L);
        return result;
    }

    private static void mirrors() {
        equal(52,SettingsFrameworkMirror.VERSION, "current framework schema");
        equal(Collections.<String, Object>singletonMap(SHARED, false), Upgrade146.DEFAULTS, "one neutral opt-in");
        equal(36, Upgrade144.DEFAULTS.size(), "historical layout batch stays frozen");
        equal(false, Upgrade144.DEFAULTS.containsKey(SHARED), "schema30 cannot claim a schema31 option");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(SHARED, true);
        values.put(StatusBarLayoutSettings.MASTER, true);
        values.put(StatusBarLayoutSettings.key("wifi", "side"), "left");
        values.put(StatusBarLayoutSettings.key("wifi", "margin_left"), 3.25f);
        values.put(StatusBarLayoutSettings.key("label", "margin_left"), 19.75f);
        values.put(StatusBarSettings.CLOCK_OFFSET_X, -8.25f);
        Bundle full = SettingsSnapshot.fromPreferences(values);
        equal(true, SettingsSnapshot.complete(full), "fresh complete current snapshot");
        for (int schema : new int[]{29, 30}) {
            Map<String, Object> historical = wire(full, schema);
            Map<String, Object> untouched = new LinkedHashMap<>(historical);
            Bundle decoded = SettingsFrameworkMirror.decode(historical);
            equal(true, SettingsSnapshot.complete(decoded), "historical copy upgrades " + schema);
            equal(untouched, historical, "decoding never edits historical input " + schema);
            equal(false, decoded.get(SHARED), "historical copy never opts in " + schema);
            for (String key : full.keySet()) {
                Object expected = Upgrade146.DEFAULTS.containsKey(key) ? false
                        : schema < 30 && Upgrade144.DEFAULTS.containsKey(key) ? Upgrade144.DEFAULTS.get(key) : full.get(key);
                equal(expected, decoded.get(key), "old choice or later neutral default " + schema + "/" + key);
            }
            for (Object mixed : new Object[]{false, true, null, 0, 0f, "false"}) {
                Map<String, Object> invalid = new LinkedHashMap<>(historical);
                invalid.put(SHARED, mixed);
                equal(null, SettingsFrameworkMirror.decode(invalid), "historical schema rejects any present later field " + schema + "/" + mixed);
            }
            Map<String, Object> incomplete = new LinkedHashMap<>(historical);
            incomplete.remove(StatusBarSettings.CLOCK_OFFSET_X);
            equal(null, SettingsFrameworkMirror.decode(incomplete), "upgrade cannot mask a missing older field " + schema);
        }
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String, Object> current = wire(full, 31);
            current.put(SHARED, enabled);
            Bundle decoded = SettingsFrameworkMirror.decode(current);
            equal(true, SettingsSnapshot.complete(decoded), "current valid copy decodes " + enabled);
            equal(enabled, decoded.get(SHARED), "current opt-in kept exactly " + enabled);
            equal(19.75f, decoded.get(StatusBarLayoutSettings.key("label", "margin_left")), "independent label gap retained " + enabled);
            equal(3.25f, decoded.get(StatusBarLayoutSettings.key("wifi", "margin_left")), "canonical Wi-Fi gap retained " + enabled);
        }
        Map<String, Object> missing = wire(full, 31);
        missing.remove(SHARED);
        equal(null, SettingsFrameworkMirror.decode(missing), "current schema requires its one new field");
        for (Object invalid : new Object[]{null, 0, 1L, 0f, "true", new Object()}) {
            Map<String, Object> malformed = wire(full, 31);
            malformed.put(SHARED, invalid);
            equal(null, SettingsFrameworkMirror.decode(malformed), "current field is strictly Boolean " + invalid);
        }
        ActivationGuardPreferencesCheck.MemoryPreferences remote = new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true, SettingsFrameworkMirror.write(remote, full, 146L), "current snapshot publishes durably");
        equal(52, remote.values.get(SettingsFrameworkMirror.SCHEMA), "publication advertises schema51");
        equal(true, SettingsFrameworkMirror.decode(remote.getAll()).get(SHARED), "published opt-in reads back");
    }

    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade146.migrate(store);
        equal(0, store.writes, "locked empty storage is not seeded");
        store.values.put(StatusBarSettings.CLOCK_OFFSET_X, -9.5f);
        store.values.put(StatusBarLayoutSettings.key("wifi", "margin_left"), 4.5f);
        store.values.put(StatusBarLayoutSettings.key("label", "margin_left"), 15.125f);
        store.values.put("local_note", "kept");
        Map<String, Object> expected = new LinkedHashMap<>(store.values);
        expected.put(SHARED, false);
        Upgrade146.migrate(store);
        equal(1, store.writes, "one durable initialization");
        equal(expected, store.values, "only the absent flag is added");
        Upgrade146.migrate(store);
        equal(1, store.writes, "idempotent initialization");
        for (Object supplied : new Object[]{true, false, null, "legacy-invalid"}) {
            store.values.put(SHARED, supplied);
            Map<String, Object> before = new LinkedHashMap<>(store.values);
            Upgrade146.migrate(store);
            equal(before, store.values, "an explicitly present value is never rewritten " + supplied);
            equal(1, store.writes, "present value performs no write " + supplied);
        }
        store.values.remove(SHARED);
        Upgrade144.migrate(store);
        equal(false, store.values.containsKey(SHARED), "historical migration never seeds a later flag");
        equal(36, Upgrade144.DEFAULTS.size(), "historical migration keeps exactly its original batch");
        Upgrade146.migrate(store);
        equal(false, store.values.get(SHARED), "current migration follows the historical batch with a neutral flag");
        equal(15.125f, store.values.get(StatusBarLayoutSettings.key("label", "margin_left")), "saved independent margins survive both migrations");
    }

    private static void portable() throws Exception {
        equal(1, ConfigTransfer.SCHEMA_VERSION, "portable schema stays compatible");
        equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(SHARED), "portable Boolean type");
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String, Object> values = Collections.<String, Object>singletonMap(SHARED, enabled);
            equal(values, ConfigTransfer.prepare(ConfigTransfer.exportJson(values, values.keySet()), true).values(), "selected opt-in roundtrip " + enabled);
        }
        String prefix = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{";
        ConfigTransfer.PreparedImport old = ConfigTransfer.prepare(prefix + "\"speed_offset_x\":7.25}}", true);
        equal(false, old.values().containsKey(SHARED), "legacy partial import does not synthesize the opt-in");
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        store.values.put(SHARED, true);
        equal(true, ConfigTransfer.commit(store, old), "legacy partial import commits");
        equal(true, store.values.get(SHARED), "legacy partial import keeps the explicit opt-in");
        for (String invalid : new String[]{"null", "0", "1", "\"true\""}) {
            checks++;
            try {
                ConfigTransfer.prepare(prefix + "\"" + SHARED + "\":" + invalid + "}}", true);
                throw new AssertionError("Accepted non-Boolean shared position: " + invalid);
            } catch (IOException expected) { }
        }
    }

    public static void main(String[] args) throws Exception {
        mirrors(); migration(); portable();
        System.out.println("Upgrade146Check passed " + checks + " checks");
    }
}
