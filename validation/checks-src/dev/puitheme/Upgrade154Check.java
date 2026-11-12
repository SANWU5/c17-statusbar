package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict historical framework snapshots, neutral durable migration and partial portable files. */
public final class Upgrade154Check {
    private static int checks;
    private static final String WHITE = AodNotificationBackground.ENABLED;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade154 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if (schema < 32) for (String key : Upgrade154.DEFAULTS.keySet()) result.remove(key);
        if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 154L);
        return result;
    }
    private static void mirrors() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(WHITE, true);
        values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true);
        values.put(StatusBarSettings.CLOCK_OFFSET_X, -8.25f);
        values.put(StatusBarLayoutSettings.key("wifi", "margin_left"), 3.25f);
        values.put(StatusBarLayoutSettings.key("label", "margin_left"), 19.75f);
        Bundle full = SettingsSnapshot.fromPreferences(values);
        equal(true, SettingsSnapshot.complete(full), "new current snapshot is complete");
        for (int schema : new int[]{29, 30, 31}) {
            Map<String, Object> historical = wire(full, schema);
            for (Object mixed : new Object[]{false, true, null, 0, 0f, "false"}) {
                Map<String, Object> invalid = new LinkedHashMap<>(historical); invalid.put(WHITE, mixed);
                equal(null, SettingsFrameworkMirror.decode(invalid), "historical schema rejects any present later AOD field " + schema + "/" + mixed);
            }
            Map<String, Object> untouched = new LinkedHashMap<>(historical);
            Bundle upgraded = SettingsFrameworkMirror.decode(historical);
            equal(true, SettingsSnapshot.complete(upgraded), "historical complete snapshot upgrades " + schema);
            equal(untouched, historical, "decode never changes its input " + schema);
            for (String key : full.keySet()) {
                Object expected = Upgrade154.DEFAULTS.containsKey(key) ? false
                        : schema < 31 && Upgrade146.DEFAULTS.containsKey(key) ? Upgrade146.DEFAULTS.get(key)
                        : schema < 30 && Upgrade144.DEFAULTS.containsKey(key) ? Upgrade144.DEFAULTS.get(key) : full.get(key);
                equal(expected, upgraded.get(key), "older choice or later neutral default " + schema + "/" + key);
            }
            Map<String, Object> missing = new LinkedHashMap<>(historical); missing.remove(StatusBarSettings.CLOCK_OFFSET_X);
            equal(null, SettingsFrameworkMirror.decode(missing), "new neutral flag cannot hide a missing older field " + schema);
        }
        equal(52,SettingsFrameworkMirror.VERSION, "current framework schema");
        equal(Collections.<String, Object>singletonMap(WHITE, false), Upgrade154.DEFAULTS, "exactly one independent opt-in");
        equal(false, Upgrade118.DEFAULTS.containsKey(WHITE), "historical lock batch stays frozen");
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String, Object> current = wire(full, 32); current.put(WHITE, enabled);
            Bundle decoded = SettingsFrameworkMirror.decode(current);
            equal(true, SettingsSnapshot.complete(decoded), "current schema accepts either Boolean " + enabled);
            equal(enabled, decoded.get(WHITE), "current explicit choice retained " + enabled);
            equal(true, decoded.get(StatusBarLayoutSettings.WIFI_LABEL_SHARED), "network shared slot stays independent " + enabled);
        }
        Map<String, Object> missing = wire(full, 32); missing.remove(WHITE);
        equal(null, SettingsFrameworkMirror.decode(missing), "schema32 requires its field");
        for (Object malformed : new Object[]{null, 0, 1L, 0f, "true", new Object()}) {
            Map<String, Object> wrong = wire(full, 32); wrong.put(WHITE, malformed);
            equal(null, SettingsFrameworkMirror.decode(wrong), "current field remains strictly Boolean " + malformed);
        }
        ActivationGuardPreferencesCheck.MemoryPreferences remote = new ActivationGuardPreferencesCheck.MemoryPreferences();
        equal(true, SettingsFrameworkMirror.write(remote, full, 154L), "complete new snapshot publishes durably");
        equal(52, remote.values.get(SettingsFrameworkMirror.SCHEMA), "publication advertises schema51");
        equal(true, SettingsFrameworkMirror.decode(remote.getAll()).get(WHITE), "published AOD opt-in reads back");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade154.migrate(store); equal(0, store.writes, "empty locked storage stays empty");
        store.values.put(StatusBarSettings.CLOCK_OFFSET_X, -9.5f);
        store.values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true);
        store.values.put("local_note", "kept");
        Map<String, Object> expected = new LinkedHashMap<>(store.values); expected.put(WHITE, false);
        Upgrade154.migrate(store); equal(1, store.writes, "one durable initialization");
        equal(expected, store.values, "only the absent neutral flag is added");
        Upgrade154.migrate(store); equal(1, store.writes, "migration is idempotent");
        for (Object supplied : new Object[]{true, false, null, "legacy-invalid"}) {
            store.values.put(WHITE, supplied); Map<String, Object> before = new LinkedHashMap<>(store.values);
            Upgrade154.migrate(store); equal(before, store.values, "an explicitly present value is never rewritten " + supplied);
            equal(1, store.writes, "present values perform no write " + supplied);
        }
        store.values.remove(WHITE); Upgrade146.migrate(store);
        equal(false, store.values.containsKey(WHITE), "the earlier migration never claims the later AOD flag");
        Upgrade154.migrate(store); equal(false, store.values.get(WHITE), "later initialization remains neutral");
        equal(true, store.values.get(StatusBarLayoutSettings.WIFI_LABEL_SHARED), "existing network settings survive");
    }
    private static void portable() throws Exception {
        equal(1, ConfigTransfer.SCHEMA_VERSION, "portable format stays compatible");
        equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(WHITE), "registered portable Boolean");
        for (boolean enabled : new boolean[]{false, true}) {
            Map<String, Object> values = Collections.<String, Object>singletonMap(WHITE, enabled);
            equal(values, ConfigTransfer.prepare(ConfigTransfer.exportJson(values, values.keySet()), true).values(), "explicit AOD choice roundtrip " + enabled);
        }
        String prefix = "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{";
        ConfigTransfer.PreparedImport old = ConfigTransfer.prepare(prefix + "\"speed_offset_x\":7.25}}", true);
        equal(false, old.values().containsKey(WHITE), "an older partial file never manufactures the opt-in");
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences(); store.values.put(WHITE, true);
        equal(true, ConfigTransfer.commit(store, old), "an older partial file commits");
        equal(true, store.values.get(WHITE), "an older partial file preserves the saved AOD choice");
        for (String invalid : new String[]{"null", "0", "1", "\"true\""}) {
            checks++;
            try { ConfigTransfer.prepare(prefix + "\"" + WHITE + "\":" + invalid + "}}", true); throw new AssertionError("Accepted non-Boolean AOD flag"); }
            catch (IOException expected) { }
        }
    }
    public static void main(String[] arguments) throws Exception {
        mirrors(); migration(); portable();
        System.out.println("Upgrade154Check passed " + checks + " checks");
    }
}
