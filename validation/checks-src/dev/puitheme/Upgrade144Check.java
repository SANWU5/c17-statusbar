package dev.puitheme;

import android.os.Bundle;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Objects;

/** Real protocol boundaries: legacy storage, partial portable files and complete framework copies. */
public final class Upgrade144Check {
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Upgrade144 " + checks + " " + reason + ": " + expected + " != " + actual);
    }
    private static String document(String fields) {
        return "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{" + fields + "}}";
    }
    private static Map<String, Object> wire(Bundle snapshot, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : snapshot.keySet()) result.put(key, snapshot.get(key));
        if(schema<37)for(String key:Upgrade169.DEFAULTS.keySet())result.remove(key); if(schema<38)for(String key:Upgrade171.DEFAULTS.keySet())result.remove(key); if(schema<39)for(String key:Upgrade172.DEFAULTS.keySet())result.remove(key); if(schema<40)for(String key:Upgrade173.DEFAULTS.keySet())result.remove(key); if(schema<41)for(String key:Upgrade174.DEFAULTS.keySet())result.remove(key); if(schema<42)for(String key:Upgrade175.DEFAULTS.keySet())result.remove(key); if(schema<44)for(String key:Upgrade184.DEFAULTS.keySet())result.remove(key); if(schema<45)for(String key:Upgrade185.DEFAULTS.keySet())result.remove(key); if(schema<46)for(String key:Upgrade186.DEFAULTS.keySet())result.remove(key); if(schema<47)for(String key:Upgrade187.DEFAULTS.keySet())result.remove(key); if(schema<48)for(String key:Upgrade188.DEFAULTS.keySet())result.remove(key); if(schema<49)for(String key:Upgrade195.DEFAULTS.keySet())result.remove(key); if(schema<50)for(String key:Upgrade196.DEFAULTS.keySet())result.remove(key); if(schema<52)for(String key:Upgrade279.DEFAULTS.keySet())result.remove(key); if(schema<51)for(String key:Upgrade209.DEFAULTS.keySet())result.remove(key); if(schema<43)for(String key:Upgrade177.DEFAULTS.keySet())result.remove(key); if(schema<36)for(String key:Upgrade168.DEFAULTS.keySet())result.remove(key); if(schema<35)for(String key:Upgrade166.DEFAULTS.keySet())result.remove(key); if(schema<34)for(String key:Upgrade160.DEFAULTS.keySet())result.remove(key); if(schema<33)for(String key:Upgrade157.DEFAULTS.keySet())result.remove(key); if(schema<32)for(String key:Upgrade154.DEFAULTS.keySet())result.remove(key); if (schema < 31) for (String key : Upgrade146.DEFAULTS.keySet()) result.remove(key);
        if (schema < 30) for (String key : Upgrade144.DEFAULTS.keySet()) result.remove(key);
        result.put(SettingsFrameworkMirror.SCHEMA, schema);
        result.put(SettingsFrameworkMirror.REVISION, 144L);
        return result;
    }
    private static void mirrors() {
        equal(52,SettingsFrameworkMirror.VERSION, "current wire schema");
        equal(36, Upgrade144.DEFAULTS.size(), "one master, eight outer elements and three inner label fields");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(StatusBarSettings.CLOCK_OFFSET_X, -5.25f);
        source.put(StatusBarSettings.SPEED_OFFSET_X, 7f);
        source.put(ClockLayoutSettings.MARGIN_ENABLED, true);
        source.put(ClockLayoutSettings.MARGIN_START, 8f);
        Bundle full = SettingsSnapshot.fromPreferences(source);
        Map<String, Object> old = wire(full, 29), untouched = new LinkedHashMap<>(old);
        Bundle upgraded = SettingsFrameworkMirror.decode(old);
        equal(true, SettingsSnapshot.complete(upgraded), "complete schema29 upgrades");
        equal(untouched, old, "decode leaves historical input untouched");
        for (Map.Entry<String, Object> entry : Upgrade144.DEFAULTS.entrySet()) {
            equal(entry.getValue(), upgraded.get(entry.getKey()), "new native default " + entry.getKey());
            Map<String, Object> mixed = new LinkedHashMap<>(old);
            mixed.put(entry.getKey(), entry.getValue());
            equal(null, SettingsFrameworkMirror.decode(mixed), "old version cannot carry mixed new batch " + entry.getKey());
            Map<String, Object> missing = wire(full, 30);
            missing.remove(entry.getKey());
            equal(null, SettingsFrameworkMirror.decode(missing), "current version requires each field " + entry.getKey());
            Map<String, Object> wrong = wire(full, 30);
            wrong.put(entry.getKey(), entry.getValue() instanceof String ? 1f : "wrong type");
            equal(null, SettingsFrameworkMirror.decode(wrong), "current field type is exact " + entry.getKey());
        }
        equal(-5.25f, upgraded.get(StatusBarSettings.CLOCK_OFFSET_X), "old drawing offset stays stored");
        equal(8f, upgraded.get(ClockLayoutSettings.MARGIN_START), "old double-row margin stays stored");
        old.remove(StatusBarSettings.BATTERY_OFFSET_Y);
        equal(null, SettingsFrameworkMirror.decode(old), "new defaults cannot hide missing old fields");
        equal(true, SettingsSnapshot.complete(SettingsFrameworkMirror.decode(wire(full, 30))), "current complete copy accepted");
    }
    private static void migration() {
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        Upgrade144.migrate(store);
        equal(0, store.writes, "empty locked storage is not seeded");
        store.values.put(StatusBarSettings.CLOCK_OFFSET_X, -12.5f);
        store.values.put(StatusBarSettings.SPEED_OFFSET_X, 23.75f);
        store.values.put(StatusBarLayoutSettings.MASTER, true);
        store.values.put(StatusBarLayoutSettings.key("battery", "margin_left"), 3.5f);
        store.values.put(StatusBarLayoutSettings.key("clock", "side"), StatusBarLayoutSettings.RIGHT);
        store.values.put("local_note", "retained");
        Map<String, Object> expected = new LinkedHashMap<>(store.values);
        for (Map.Entry<String, Object> entry : Upgrade144.DEFAULTS.entrySet()) expected.putIfAbsent(entry.getKey(), entry.getValue());
        Upgrade144.migrate(store);
        equal(1, store.writes, "one durable write");
        equal(expected, store.values, "only absent fields added; no offset to margin conversion");
        Upgrade144.migrate(store);
        equal(1, store.writes, "idempotent");
    }
    private static void portable() throws Exception {
        equal(1, ConfigTransfer.SCHEMA_VERSION, "portable format remains schema1");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(StatusBarLayoutSettings.MASTER, true);
        source.put(StatusBarLayoutSettings.key("clock", "side"), "right");
        source.put(StatusBarLayoutSettings.key("clock", "order"), 2f);
        source.put(StatusBarLayoutSettings.key("clock", "margin_left"), 1.2345f);
        source.put(StatusBarLayoutSettings.key("label", "order"), 2f);
        source.put(StatusBarSettings.CLOCK_OFFSET_X, -9f);
        ConfigTransfer.PreparedImport selected = ConfigTransfer.prepare(ConfigTransfer.exportJson(source, source.keySet()), true);
        equal(source, selected.values(), "selected fields roundtrip exactly");
        ConfigTransfer.PreparedImport legacy = ConfigTransfer.prepare(document("\"speed_offset_x\":7.25"), true);
        equal(Collections.<String, Object>singletonMap(StatusBarSettings.SPEED_OFFSET_X, 7.25f), legacy.values(), "legacy partial does not seed layout");
        ConfigTransfer.PreparedImport newOnly = ConfigTransfer.prepare(document("\"statusbar_layout_battery_margin_left\":2.75"), true);
        equal(1, newOnly.count, "single new field remains partial");
        ActivationGuardPreferencesCheck.MemoryPreferences store = new ActivationGuardPreferencesCheck.MemoryPreferences();
        store.values.put(StatusBarLayoutSettings.MASTER, true);
        store.values.put(StatusBarLayoutSettings.key("clock", "side"), "left");
        equal(true, ConfigTransfer.commit(store, legacy), "legacy partial commits");
        equal(true, store.values.get(StatusBarLayoutSettings.MASTER), "legacy import keeps explicit master");
        equal("left", store.values.get(StatusBarLayoutSettings.key("clock", "side")), "legacy import keeps explicit side");
        equal(true, ConfigTransfer.commit(store, selected), "explicit new selected values commit");
        equal("right", store.values.get(StatusBarLayoutSettings.key("clock", "side")), "new supplied side wins");
        equal(-9f, store.values.get(StatusBarSettings.CLOCK_OFFSET_X), "legacy offset retained alongside new fields");
        for (String fields : new String[]{
                "\"statusbar_layout_clock_side\":\"middle\"", "\"statusbar_layout_label_side\":\"left\"",
                "\"statusbar_layout_clock_order\":1.5", "\"statusbar_layout_clock_order\":-1",
                "\"statusbar_layout_clock_order\":2147483648", "\"statusbar_layout_label_order\":3",
                "\"statusbar_layout_wifi_margin_left\":-1e99", "\"statusbar_layout_clock_order\":\"2\"",
                "\"statusbar_layout_enabled\":1", "\"statusbar_layout_carrier_side\":\"left\",\"statusbar_layout_carrier_side\":\"right\""}) {
            checks++;
            try { ConfigTransfer.prepare(document(fields), true); throw new AssertionError("Accepted invalid layout: " + fields); }
            catch (IOException expected) { }
        }
        ConfigScope.Section layout = ConfigScope.section(StatusBarLayoutSettings.GROUP);
        Set<String> ordering = new LinkedHashSet<>();
        for (String key : Upgrade144.DEFAULTS.keySet()) if (!StatusBarLayoutSettings.marginKey(key)) ordering.add(key);
        // code166 extends this page with per-slot placement; the historical
        // Upgrade144 batch stays immutable and still owns no margin fields here.
        ordering.add(NativeStatusIcons.LAYOUT);
        equal(ordering, layout.keys(), "ordering page owns ordering fields only; margins travel with element pages");
        equal(false, layout.keys().contains(StatusBarSettings.CLOCK_OFFSET_X), "new selected page cannot overwrite legacy clock offset");
    }
    private static void editorAndRuntime() {
        SettingsCatalog.Group group = SettingsCatalog.group(StatusBarLayoutSettings.GROUP);
        equal(SettingsCatalog.STATUSBAR, group.category, "layout is a status-bar destination");
        equal(19, group.items.size(), "ordering page contains region/order plus per-slot placement");
        equal(false, group.items.get(0).defaultValue, "layout opt-in is off");
        for (String token : StatusBarLayoutSettings.TOKENS) {
            SettingsCatalog.Item order = SettingsCatalog.item(StatusBarLayoutSettings.key(token, "order"));
            equal(true, SettingEditor.forItem(order, Collections.emptyMap()).integer, "ordinal integer editor " + token);
            equal(false, SettingEditor.forItem(order, Collections.emptyMap()).signed, "ordinal cannot be negative " + token);
            equal(null, SettingsCatalog.validationError(order, 0f), "zero follows system " + token);
            equal(true, SettingsCatalog.validationError(order, 1.25f) != null, "fractional ordinal rejected " + token);
            SettingsCatalog.Item gap = SettingsCatalog.item(StatusBarLayoutSettings.key(token, "margin_right"));
            equal(1.2345f, SettingsCatalog.customNumber(gap, "1.2345"), "fractional gap preserved " + token);
            equal(null, SettingsCatalog.validationError(gap, -1f), "negative gap accepted " + token);
            equal(true, NumericPolicy.signed(gap.key), "physical gap is signed " + token);
        }
        for (String key : new String[]{StatusBarSettings.CLOCK_OFFSET_X, ClockLayoutSettings.DUAL_OFFSET_X,
                StatusBarSettings.BATTERY_OFFSET_X, StatusBarSettings.SPEED_OFFSET_X,
                StatusBarSettings.DATA_OFFSET_X, StatusBarSettings.LABEL_OFFSET_X, NotificationIconArea.X, NativeStatusIcons.X,
                SpeedPosition.POSITION, SpeedPosition.SAFE_GAP, SpeedPosition.SAFE_GAP_ENABLED, SpeedPosition.EDGE_RESERVE,
                ClockLayoutSettings.MARGIN_START, ClockLayoutSettings.MARGIN_END, ClockLayoutSettings.MARGIN_ENABLED, NetworkIconOrder.SWAP}) {
            equal(null, SettingsCatalog.item(key), "conflicting legacy field absent from editor " + key);
            equal(true, ConfigTransfer.types().containsKey(key), "legacy import/export type retained " + key);
            equal(true, SettingsCatalog.hiddenKeys().contains(key), "legacy uncovered-key check excludes " + key);
        }
        equal("network",SettingsCatalog.item(StatusBarSettings.WIFI_OFFSET_X).groupId,"shared horizontal position has one network editor");
        equal(true,ConfigTransfer.types().containsKey(StatusBarSettings.WIFI_OFFSET_X),"shared position reuses the existing portable key");
        Map<String,Object> sharedPosition=new LinkedHashMap<>();
        sharedPosition.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED,false);
        equal(true,!SettingsCatalog.unavailableReason(StatusBarSettings.WIFI_OFFSET_X,sharedPosition).isEmpty(),"independent mode keeps legacy horizontal drawing retired");
        sharedPosition.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        equal("",SettingsCatalog.unavailableReason(StatusBarSettings.WIFI_OFFSET_X,sharedPosition),"shared mode exposes its planner-owned horizontal position");
        equal(true, SettingsCatalog.item(StatusBarSettings.CLOCK_OFFSET_Y) != null, "vertical adjustment remains reachable");
        equal(true, SettingsCatalog.item(BatteryTextStyle.X) != null, "battery inner text X remains reachable");
        equal(true, SettingsCatalog.item(NativeDataActivity.X) != null, "activity inner X remains reachable");
        equal(null, SettingsCatalog.item("statusbar_layout_label_side"), "inner label cannot cross region");
        Map<String, Object> active = new LinkedHashMap<>();
        active.put(StatusBarLayoutSettings.MASTER, true); active.put(DataBatterySpacing.ENABLED, true);
        equal(true, SettingsCatalog.unavailableReason(DataBatterySpacing.SPACING, active).contains("已接管"), "old boundary suspended in new layout");
        equal(true, active.get(DataBatterySpacing.ENABLED), "suspending legacy boundary keeps its saved switch");
        active.put(StatusBarLayoutSettings.MASTER, false);
        equal("", SettingsCatalog.unavailableReason(DataBatterySpacing.SPACING, active), "old boundary available after leaving new layout");
        Bundle values = new Bundle();
        values.putBoolean(StatusBarLayoutSettings.MASTER, true);
        values.putString(StatusBarLayoutSettings.key("clock", "side"), "right");
        values.putFloat(StatusBarLayoutSettings.key("clock", "order"), 3f);
        values.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 2.125f);
        values.putFloat(StatusBarLayoutSettings.key("clock", "margin_right"), -4f);
        StatusBarLayoutSettings.Snapshot parsed = StatusBarLayoutSettings.Snapshot.parse(values);
        equal(true, parsed.enabled, "enabled snapshot"); equal("right", parsed.side("clock"), "chosen side");
        equal(3, parsed.order("clock"), "chosen ordinal"); equal(2.125f, parsed.left("clock"), "chosen fractional gap");
        equal(-4f, parsed.right("clock"), "negative stored gap preserved for runtime layout");
        values.putString(StatusBarLayoutSettings.key("clock", "side"), "bad");
        equal("right", parsed.side("clock"), "parsed snapshot is immutable");
        equal("native", StatusBarLayoutSettings.Snapshot.parse(values).side("clock"), "malformed side native fallback");
        values.putBoolean(StatusBarSettings.SAFE_MODE, true);
        equal(false, StatusBarLayoutSettings.enabled(values), "safe mode does not enable layout");
        equal(false, StatusBarLayoutSettings.Snapshot.parse(null).enabled, "missing settings native");
    }
    private static void effectiveRuntime() {
        Bundle raw = new Bundle();
        String[] horizontal = {StatusBarSettings.CLOCK_OFFSET_X, ClockLayoutSettings.DUAL_OFFSET_X,
                StatusBarSettings.CARRIER_OFFSET_X, StatusBarSettings.BATTERY_OFFSET_X,
                StatusBarSettings.SPEED_OFFSET_X, StatusBarSettings.WIFI_OFFSET_X, StatusBarSettings.DATA_OFFSET_X,
                StatusBarSettings.LABEL_OFFSET_X, NotificationIconArea.X, NativeStatusIcons.X};
        for (String key : horizontal) raw.putFloat(key, -9.25f);
        String[] retained = {StatusBarSettings.CLOCK_OFFSET_Y, ClockLayoutSettings.DUAL_OFFSET_Y,
                StatusBarSettings.BATTERY_OFFSET_Y, StatusBarSettings.SPEED_OFFSET_Y, StatusBarSettings.WIFI_OFFSET_Y,
                StatusBarSettings.DATA_OFFSET_Y, StatusBarSettings.LABEL_OFFSET_Y, NotificationIconArea.Y, NativeStatusIcons.Y,
                CarrierPanels.key(CarrierPanels.NOTIFICATION, "offset_x"), CarrierPanels.key(CarrierPanels.CONTROL, "offset_x"),
                CarrierPanels.key(CarrierPanels.LOCKSCREEN, "offset_x"), StatusBarSettings.SHADE_CLOCK_OFFSET_X,
                NativeDataActivity.X, BatteryTextStyle.X};
        for (String key : retained) raw.putFloat(key, 3.125f);
        String[] suspended = {ClockLayoutSettings.MARGIN_ENABLED, SpeedPosition.SAFE_GAP_ENABLED,
                NetworkIconOrder.SWAP, DataBatterySpacing.ENABLED};
        for (String key : suspended) raw.putBoolean(key, true);
        raw.putBoolean("speed_position_enabled", true);
        raw.putString(SpeedPosition.POSITION, SpeedPosition.CLOCK_RIGHT);
        raw.putFloat(NativeStatusIcons.SPACING, 4.25f);
        Bundle nativeCopy = StatusBarLayoutSettings.runtime(raw);
        for (String key : raw.keySet()) equal(raw.get(key), nativeCopy.get(key), "off retains native configuration " + key);
        equal(false, raw == nativeCopy, "always a separate copy");
        raw.putBoolean(StatusBarLayoutSettings.MASTER, true);
        Bundle effective = StatusBarLayoutSettings.runtime(raw);
        equal(0f, effective.get(NativeStatusIcons.SPACING), "flow owns actual hint-gap allocation");
        equal(4.25f, raw.get(NativeStatusIcons.SPACING), "hint gap remains stored for the planner and legacy restoration");
        for (String key : horizontal) {
            equal(0f, effective.get(key), "new layout suspends old X " + key);
            equal(-9.25f, raw.get(key), "persisted X intact " + key);
        }
        for (String key : retained) equal(3.125f, effective.get(key), "Y and panel/internal X retained " + key);
        for (String key : suspended) {
            equal(false, effective.get(key), "old boundary/order suspended " + key);
            equal(true, raw.get(key), "saved old switch intact " + key);
        }
        equal(true, effective.get("speed_position_enabled"), "speed vertical switch remains alive");
        equal(SpeedPosition.CLOCK_RIGHT, effective.get(SpeedPosition.POSITION), "anchor is retained; renderer owns gate");
        raw.putBoolean(StatusBarSettings.SAFE_MODE, true);
        Bundle safe = StatusBarLayoutSettings.runtime(raw);
        for (String key : raw.keySet()) equal(raw.get(key), safe.get(key), "safe mode retains source and avoids activation " + key);
        equal(0, StatusBarLayoutSettings.runtime(null).keySet().size(), "absent bundle remains empty");
    }
    public static void main(String[] args) throws Exception {
        mirrors(); migration(); portable(); editorAndRuntime(); effectiveRuntime();
        System.out.println("Upgrade144Check passed " + checks + " checks");
    }
}
