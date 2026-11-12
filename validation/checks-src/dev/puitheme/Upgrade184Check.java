package dev.puitheme;

import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The four fluid-cloud gradient numbers must survive every persistence boundary and leave older schemas untouched. */
public final class Upgrade184Check {
    private static int checks;
    private static final String START = FluidCloudStyle.BACKGROUND_GRADIENT_START, MID = FluidCloudStyle.BACKGROUND_GRADIENT_MID,
            STRENGTH = FluidCloudStyle.BACKGROUND_GRADIENT_STRENGTH, OPACITY = FluidCloudStyle.BACKGROUND_GRADIENT_OPACITY;
    private static final String[] KEYS = {START, MID, STRENGTH, OPACITY};
    private static final float[] DEFAULT = {0f, 50f, 100f, 100f};
    private static void equal(Object expected, Object actual, String why) {
        checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade184 " + checks + " " + why + ": " + expected + " != " + actual);
    }
    private static Map<String, Object> additions() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < KEYS.length; i++) values.put(KEYS[i], DEFAULT[i]);
        return values;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> wire(Bundle full, int schema) throws Exception {
        Method method = Upgrade173Check.class.getDeclaredMethod("wire", Bundle.class, Integer.TYPE); method.setAccessible(true);
        return (Map<String, Object>) method.invoke(null, full, schema);
    }
    private static String document(String key, String raw) {
        return "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{\"" + key + "\":" + raw + "}}";
    }
    private static void registration(Bundle full) {
        equal(52, SettingsFrameworkMirror.VERSION, "current mirror schema"); equal(1, ConfigTransfer.SCHEMA_VERSION, "portable JSON unchanged");
        equal(1000, full.keySet().size(), "complete settings count"); equal(987, ConfigTransfer.types().size(), "portable count");
        equal(additions(), Upgrade184.DEFAULTS, "exact four Float additions"); equal(4, Upgrade184.DEFAULTS.size(), "184 batch frozen");
        equal(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE, "fluid_cloud_background_gradient_range", "the end is the pre-existing range key");
        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            equal(DEFAULT[i], full.get(key), "fresh effective default"); equal(DEFAULT[i], StatusBarSettings.NUMERIC_DEFAULTS.get(key), "registered Float default");
            equal(ConfigTransfer.Type.NUMBER, ConfigTransfer.types().get(key), "portable number");
            for (Map<String, Object> older : new Map[]{Upgrade177.DEFAULTS, Upgrade175.DEFAULTS, Upgrade174.DEFAULTS, Upgrade173.DEFAULTS, Upgrade171.DEFAULTS, Upgrade169.DEFAULTS})
                equal(false, older.containsKey(key), "not retroactively added to an older batch");
            equal(true, SettingsCatalog.item(key) != null, "has an editor entry");
        }
    }
    private static void mirrors(Bundle full) throws Exception {
        for (int schema = 1; schema < 44; schema++) {
            Map<String, Object> old = wire(full, schema), before = new LinkedHashMap<>(old); Bundle decoded = SettingsFrameworkMirror.decode(old);
            equal(true, SettingsSnapshot.complete(decoded), "complete old mirror upgrades: " + schema); equal(before, old, "decoding never mutates wire");
            for (int i = 0; i < KEYS.length; i++) {
                equal(false, old.containsKey(KEYS[i]), "historical wire omits the future number"); equal(DEFAULT[i], decoded.get(KEYS[i]), "historical mirror gains the default");
                for (Object forged : new Object[]{0f, 33f, 100f, 7, 7L, 7d, true, "7", null, Float.NaN}) {
                    Map<String, Object> mixed = new LinkedHashMap<>(old); mixed.put(KEYS[i], forged);
                    equal(null, SettingsFrameworkMirror.decode(mixed), "historical schema rejects even a pre-existing future key");
                }
            }
            if (schema == 43) equal(961, old.size() - 0, "published177 wire frozen");
        }
        Map<String, Object> current = wire(full, 44); equal(965, current.size(), "schema44 wire and two metadata fields stay frozen");
        equal(true, SettingsSnapshot.complete(SettingsFrameworkMirror.decode(current)), "complete44 accepted");
        for (float start : new float[]{0f, 12.5f, 98f}) for (float strength : new float[]{0f, 100f, 200f}) {
            Map<String, Object> saved = new LinkedHashMap<>(current); saved.put(START, start); saved.put(STRENGTH, strength);
            Bundle decoded = SettingsFrameworkMirror.decode(saved);
            equal(true, SettingsSnapshot.complete(decoded), "independent current numbers complete"); equal(start, decoded.get(START), "start preserved"); equal(strength, decoded.get(STRENGTH), "strength preserved");
        }
        for (String key : KEYS) {
            Map<String, Object> missing = new LinkedHashMap<>(current); missing.remove(key); equal(null, SettingsFrameworkMirror.decode(missing), "44 cannot omit a number");
            for (Object invalid : new Object[]{null, 50, 50L, 50d, true, "50", Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                Map<String, Object> wrong = new LinkedHashMap<>(current); wrong.put(key, invalid); equal(null, SettingsFrameworkMirror.decode(wrong), "44 rejects a mistyped number");
            }
        }
        current.put(SettingsFrameworkMirror.SCHEMA, SettingsFrameworkMirror.VERSION+1); equal(null, SettingsFrameworkMirror.decode(current), "future mirror rejected");
    }
    private static Map<String, Object> siblings() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE, 62f); values.put(FluidCloudStyle.BACKGROUND_OPACITY, 80f); values.put(FluidCloudStyle.MASTER, true); values.put("private_note", "keep");
        return values;
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade184.migrate(store); equal(0, store.writes, "empty XML stays empty"); equal(Collections.emptyMap(), store.values, "no defaults written on reset");
        store.values.putAll(siblings()); Map<String, Object> before = new LinkedHashMap<>(store.values);
        Upgrade184.migrate(store); equal(1, store.writes, "all four missing numbers use one durable write");
        for (int i = 0; i < KEYS.length; i++) equal(DEFAULT[i], store.values.get(KEYS[i]), "missing XML number gets its default");
        for (String key : before.keySet()) equal(before.get(key), store.values.get(key), "every saved sibling preserved, including the range");
        Upgrade184.migrate(store); equal(1, store.writes, "migration idempotent");
        for (Object present : new Object[]{12f, 99.5f, 200f, null, 50, Float.NaN, "invalid"}) {
            for (String key : KEYS) store.values.put(key, present); before = new LinkedHashMap<>(store.values);
            Upgrade184.migrate(store); equal(before, store.values, "any present XML entry is not rewritten"); equal(1, store.writes, "present entries produce no write");
        }
        store.values.clear(); store.values.putAll(siblings()); store.values.put(MID, 33f); before = new LinkedHashMap<>(store.values);
        Upgrade184.migrate(store); equal(2, store.writes, "partial XML adds only the missing numbers"); equal(33f, store.values.get(MID), "explicit number retained");
        equal(0f, store.values.get(START), "absent start default"); for (String key : before.keySet()) equal(before.get(key), store.values.get(key), "partial migration preserves siblings");
    }
    private static void imports(Bundle full) throws Exception {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        store.values.putAll(siblings()); store.values.put(START, 20f); store.values.put(MID, 40f);
        for (String raw : new String[]{"0", "35.5", "70"}) {
            Map<String, Object> before = new LinkedHashMap<>(store.values);
            ConfigTransfer.PreparedImport prepared = ConfigTransfer.prepare(document(STRENGTH, raw), true);
            equal(1, prepared.count, "partial number import stays partial"); equal(true, ConfigTransfer.commit(store, prepared), "partial number import commits");
            equal(Float.valueOf(raw), store.values.get(STRENGTH), "numeric value retained");
            for (String other : before.keySet()) if (!STRENGTH.equals(other)) equal(before.get(other), store.values.get(other), "number import leaves siblings unchanged");
        }
        Map<String, Object> saved = new LinkedHashMap<>();
        for (String key : full.keySet()) saved.put(key, full.get(key));
        saved.put(START, 15.5f); saved.put(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE, 72.25f); saved.put(MID, 30f); saved.put(STRENGTH, 120f); saved.put(OPACITY, 65f);
        Map<String, Object> sourceBefore = new LinkedHashMap<>(saved);
        ConfigTransfer.PreparedImport prepared = ConfigTransfer.prepare(ConfigTransfer.exportJson(saved), true);
        equal(987, prepared.count, "full portable count"); equal(sourceBefore, saved, "export leaves source unchanged");
        equal(15.5f, prepared.values().get(START), "start exact"); equal(72.25f, prepared.values().get(FluidCloudStyle.BACKGROUND_GRADIENT_RANGE), "end exact");
        equal(30f, prepared.values().get(MID), "mid exact"); equal(120f, prepared.values().get(STRENGTH), "strength exact"); equal(65f, prepared.values().get(OPACITY), "opacity exact");
    }
    public static void main(String[] args) throws Exception {
        Bundle full = SettingsSnapshot.fromPreferences(Collections.emptyMap());
        registration(full); mirrors(full); migration(); imports(full);
        System.out.println("Upgrade184Check: " + checks + " checks passed");
    }
}
