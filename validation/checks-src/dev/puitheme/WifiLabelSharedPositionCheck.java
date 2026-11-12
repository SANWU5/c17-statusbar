package dev.puitheme;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared-slot protocol/editor contracts; actual native shared-slot geometry is checked by its runtime fixture. */
public final class WifiLabelSharedPositionCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void equal(Object expected, Object actual, String why) { check(java.util.Objects.equals(expected, actual), why + ": " + expected + " != " + actual); }
    private static String gap(String token, String side) { return StatusBarLayoutSettings.key(token, "margin_" + side); }
    private static Bundle bundle(Map<String, ?> values) {
        Bundle result = new Bundle();
        for (Map.Entry<String, ?> item : values.entrySet()) {
            Object value = item.getValue();
            if (value instanceof Boolean) result.putBoolean(item.getKey(), (Boolean) value);
            else if (value instanceof Number) result.putFloat(item.getKey(), ((Number) value).floatValue());
            else if (value instanceof String) result.putString(item.getKey(), (String) value);
        }
        return result;
    }
    private static final class Storage extends Context {
        final Map<String, SharedPreferences> files = new LinkedHashMap<>();
        final ContentResolver resolver = new ContentResolver();
        @Override public String getPackageName() { return "validation.shared-slot"; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return files.computeIfAbsent(name, key -> new ActivationGuardPreferencesCheck.MemoryPreferences());
        }
        @Override public ContentResolver getContentResolver() { return resolver; }
    }
    private static void editorsAndTrial() throws Exception {
        ActivationGuardPreferencesCheck.MemoryPreferences raw = new ActivationGuardPreferencesCheck.MemoryPreferences();
        String left = gap("label", "left"), right = gap("label", "right"), wifiLeft = gap("wifi", "left"), wifiRight = gap("wifi", "right");
        raw.values.put(left, 9.25f); raw.values.put(right, 17.75f); raw.values.put(wifiLeft, 2.5f); raw.values.put(wifiRight, 6.125f);
        SettingsCatalog.Item labelLeft = SettingsCatalog.item(left), labelRight = SettingsCatalog.item(right);
        equal(9.25f, SettingEditor.storedValue(labelLeft, raw.getAll()), "off shows independently stored label gap");
        raw.values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true);
        Map<String, ?> before = raw.getAll();
        equal(wifiLeft, StatusBarLayoutSettings.editingKey(left, before), "label left edits canonical Wi-Fi gap");
        equal(wifiRight, StatusBarLayoutSettings.editingKey(right, before), "label right edits canonical Wi-Fi gap");
        equal(2.5f, SettingEditor.storedValue(labelLeft, before), "slider reads the real canonical left value");
        equal(6.125f, SettingEditor.storedValue(labelRight, before), "slider reads the real canonical right value");
        SettingEditor.Info info = SettingEditor.forItem(labelLeft, before);
        equal("2.5", info.initial, "manual number dialog shares the same confirmed value");
        check(info.context.contains("共用") && info.description.contains("共用"), "editor explains the shared value");
        check(SettingsCatalog.displayTitle(labelLeft, before).contains("共用"), "label row visibly shows shared state");
        check(SettingsCatalog.displayTitle(SettingsCatalog.item(wifiLeft), before).contains("共用"), "Wi-Fi row visibly shows the same shared state");
        equal(9.25f, SettingsCatalog.value(before, left), "raw catalog retains the original independent label value");
        for (String key : Arrays.asList(StatusBarLayoutSettings.key("label", "order"), gap("mobile", "left"),
                StatusBarLayoutSettings.key("wifi", "side"), StatusBarLayoutSettings.WIFI_LABEL_SHARED, "unknown"))
            equal(key, StatusBarLayoutSettings.editingKey(key, before), "mapping cannot overwrite an unrelated field");
        Storage storage = new Storage();
        String trialKey = StatusBarLayoutSettings.editingKey(labelLeft.key, raw.getAll());
        check(NumericTrial.begin(storage, raw, raw, trialKey, 63.25f), "shared manual trial starts on the canonical actual preference");
        equal(63.25f, raw.values.get(wifiLeft), "trial writes canonical Wi-Fi value");
        equal(9.25f, raw.values.get(left), "trial never overwrites saved independent label value");
        check(NumericTrial.rollback(), "shared trial reliably rolls back");
        equal(2.5f, raw.values.get(wifiLeft), "rollback restores canonical prior value");
        equal(9.25f, raw.values.get(left), "rollback preserves label independent choice");
        raw.values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, false);
        equal(left, StatusBarLayoutSettings.editingKey(left, raw.getAll()), "closing restores the original editing key");
        equal(9.25f, SettingEditor.storedValue(labelLeft, raw.getAll()), "closing immediately restores the original independent UI value");
        check(!SettingsCatalog.displayTitle(labelLeft, raw.getAll()).contains("共用"), "closing removes the shared row state");
        check(NumericTrial.begin(storage, raw, raw, StatusBarLayoutSettings.editingKey(left, raw.getAll()), 12.75f), "independent trial still works after closing");
        check(NumericTrial.keep(), "independent trial confirms normally");
        equal(12.75f, raw.values.get(left), "independent trial saves only its own gap");
        equal(2.5f, raw.values.get(wifiLeft), "independent trial cannot alter Wi-Fi gap");
        raw.values.put(left, -9.25f); raw.values.put(wifiLeft, -2.5f);
        raw.values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true);
        info = SettingEditor.forItem(labelLeft, raw.getAll());
        equal("-2.5", info.initial, "signed shared editor reads the canonical negative value");
        check(info.signed && info.description.contains("负数收紧"), "shared editor accepts negative numeric input");
        trialKey = StatusBarLayoutSettings.editingKey(left, raw.getAll());
        check(NumericTrial.begin(storage, raw, raw, trialKey, -63.25f), "negative shared trial starts on canonical preference");
        equal(-63.25f, raw.values.get(wifiLeft), "negative trial reaches canonical Wi-Fi setting unchanged");
        equal(-9.25f, raw.values.get(left), "negative trial leaves independent label setting untouched");
        check(NumericTrial.rollback(), "negative shared trial can cancel");
        equal(-2.5f, raw.values.get(wifiLeft), "negative rollback restores canonical signed value exactly");
        check(NumericTrial.begin(storage, raw, raw, trialKey, -1000000f), "shared trial accepts values beyond slider suggestion");
        check(NumericTrial.keep(), "negative shared trial confirms");
        equal(-1000000f, raw.values.get(wifiLeft), "confirmed shared value is not clamped");
        Map<String, Object> imported = ConfigTransfer.prepare(ConfigTransfer.exportJson(raw.getAll()), true).values();
        equal(-1000000f, imported.get(wifiLeft), "canonical negative setting exports and imports unchanged");
        equal(-9.25f, imported.get(left), "independent negative label setting stays distinct during sharing");
        raw.values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED, false);
        equal(-9.25f, SettingEditor.storedValue(labelLeft, raw.getAll()), "closing sharing recovers the prior signed independent setting");
        raw.values.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        raw.values.put(StatusBarSettings.WIFI_OFFSET_Y,-3.25f);raw.values.put(StatusBarSettings.LABEL_OFFSET_Y,12.5f);
        trialKey=StatusBarLayoutSettings.editingKey(StatusBarSettings.LABEL_OFFSET_Y,raw.getAll());
        check(NumericTrial.begin(storage,raw,raw,trialKey,-8.125f),"shared vertical trial uses the same actual preference path");
        equal(-8.125f,raw.values.get(StatusBarSettings.WIFI_OFFSET_Y),"trial changes canonical Y");
        equal(12.5f,raw.values.get(StatusBarSettings.LABEL_OFFSET_Y),"trial preserves independent label Y");
        check(NumericTrial.rollback(),"shared vertical trial can cancel");equal(-3.25f,raw.values.get(StatusBarSettings.WIFI_OFFSET_Y),"trial rollback restores canonical Y");
    }
    private static void sharedVerticalPosition() throws Exception {
        Map<String,Object> saved=new LinkedHashMap<>();saved.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        saved.put(StatusBarSettings.WIFI_OFFSET_Y,-3.25f);saved.put(StatusBarSettings.LABEL_OFFSET_Y,12.5f);
        saved.put("wifi_position_enabled",true);saved.put("label_position_enabled",false);
        saved.put("wifi_size_enabled",false);saved.put("label_size_enabled",true);
        saved.put(StatusBarSettings.WIFI_ICON_SCALE,145f);saved.put(StatusBarSettings.LABEL_SCALE,80f);
        equal(StatusBarSettings.WIFI_OFFSET_Y,StatusBarLayoutSettings.editingKey(StatusBarSettings.LABEL_OFFSET_Y,saved),"shared label vertical editor uses canonical Wi-Fi offset");
        equal("wifi_position_enabled",StatusBarLayoutSettings.editingKey("label_position_enabled",saved),"shared label position toggle edits the same canonical switch");
        equal(-3.25f,SettingEditor.storedValue(SettingsCatalog.item(StatusBarSettings.LABEL_OFFSET_Y),saved),"label slider reads canonical Wi-Fi Y");
        equal(true,SettingEditor.storedValue(SettingsCatalog.item("label_position_enabled"),saved),"label switch reads canonical Wi-Fi intent");
        for(String key:Arrays.asList("wifi_enabled","label_enabled","wifi_size_enabled","label_size_enabled",
                StatusBarSettings.WIFI_ICON_SCALE,StatusBarSettings.LABEL_SCALE,StatusBarSettings.SLOT_WIDTH,StatusBarSettings.DATA_OFFSET_Y))
            equal(key,StatusBarLayoutSettings.editingKey(key,saved),"sharing does not alias independent size/master field "+key);
        equal(StatusBarSettings.WIFI_OFFSET_X,StatusBarLayoutSettings.editingKey(StatusBarSettings.LABEL_OFFSET_X,saved),"shared horizontal editor uses the same canonical position");
        for(String key:Arrays.asList(StatusBarSettings.WIFI_OFFSET_Y,StatusBarSettings.LABEL_OFFSET_Y)){
            SettingsCatalog.Item item=SettingsCatalog.item(key);
            check(SettingsCatalog.displayTitle(item,saved).contains("共用"),"both vertical rows show shared state");
            check(SettingEditor.forItem(item,saved).description.contains("共用"),"both numeric editors explain shared vertical state");
        }
        check(!SettingsCatalog.displayTitle(SettingsCatalog.item(StatusBarSettings.LABEL_SCALE),saved).contains("共用"),"independent label scale is not presented as shared");
        check(SettingsCatalog.item(StatusBarLayoutSettings.WIFI_LABEL_SHARED).description.contains("大小"),"shared switch explains independent size controls");
        for(int masters=0;masters<4;masters++)for(boolean position:new boolean[]{false,true}){
            saved.put("wifi_enabled",(masters&1)!=0);saved.put("label_enabled",(masters&2)!=0);
            saved.put("wifi_position_enabled",position);saved.put("label_position_enabled",!position);
            Bundle raw=bundle(saved),runtime=StatusBarLayoutSettings.runtime(raw);FeatureOptions effective=FeatureOptions.from(runtime);
            equal(-3.25f,runtime.getFloat(StatusBarSettings.LABEL_OFFSET_Y),"runtime label Y uses canonical value independently of either master");
            equal(12.5f,raw.getFloat(StatusBarSettings.LABEL_OFFSET_Y),"runtime never overwrites saved independent label Y");
            equal(!position,raw.getBoolean("label_position_enabled"),"runtime never overwrites saved independent label switch");
            equal((masters&1)!=0&&position,effective.position("wifi"),"Wi-Fi position keeps its own feature master");
            equal((masters&2)!=0&&position,effective.position("label"),"label position keeps its own feature master");
            equal(false,effective.size("wifi"),"Wi-Fi size switch stays independent");
            equal((masters&2)!=0,effective.size("label"),"label size remains independently enabled");
            equal(145f,runtime.getFloat(StatusBarSettings.WIFI_ICON_SCALE),"Wi-Fi scale remains independently stored");
            equal(80f,runtime.getFloat(StatusBarSettings.LABEL_SCALE),"label scale remains independently stored");
            raw.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,false);Bundle restored=StatusBarLayoutSettings.runtime(raw);
            equal(!position,restored.getBoolean("label_position_enabled"),"closing sharing restores either independently saved label switch state");
            equal(12.5f,restored.getFloat(StatusBarSettings.LABEL_OFFSET_Y),"closing sharing restores saved label Y for every independent-master combination");
        }
        Map<String,Object> defaults=new LinkedHashMap<>(saved);defaults.remove("wifi_position_enabled");defaults.remove(StatusBarSettings.WIFI_OFFSET_Y);
        Bundle inherited=StatusBarLayoutSettings.runtime(bundle(defaults));
        equal(true,inherited.getBoolean("label_position_enabled"),"missing canonical switch uses its existing enabled default");
        equal(0f,inherited.getFloat(StatusBarSettings.LABEL_OFFSET_Y),"missing canonical Y uses Wi-Fi default rather than old label Y");
        saved.put(StatusBarSettings.SAFE_MODE,true);Bundle paused=StatusBarLayoutSettings.runtime(bundle(saved));
        equal(12.5f,paused.getFloat(StatusBarSettings.LABEL_OFFSET_Y),"safe mode suspends effective vertical sharing");
        check(!FeatureOptions.from(paused).position("label")&&!FeatureOptions.from(paused).position("wifi"),"safe mode suspends both position effects");
        saved.remove(StatusBarSettings.SAFE_MODE);saved.put(StatusBarLayoutSettings.WIFI_LABEL_SHARED,false);
        Bundle independent=StatusBarLayoutSettings.runtime(bundle(saved));
        equal(12.5f,independent.getFloat(StatusBarSettings.LABEL_OFFSET_Y),"closing sharing restores original independent label Y");
        equal(false,independent.getBoolean("label_position_enabled"),"closing sharing restores original independent label switch");
        equal(StatusBarSettings.LABEL_OFFSET_Y,StatusBarLayoutSettings.editingKey(StatusBarSettings.LABEL_OFFSET_Y,saved),"closing sharing restores independent editor");
        Map<String,Object> imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true).values();
        equal(12.5f,imported.get(StatusBarSettings.LABEL_OFFSET_Y),"export retains independent label Y");
        equal(-3.25f,imported.get(StatusBarSettings.WIFI_OFFSET_Y),"export retains canonical Wi-Fi Y");
    }
    public static void main(String[] ignored) throws Exception {
        sharedVerticalPosition();
        equal(false, StatusBarLayoutSettings.BOOLEANS.get(StatusBarLayoutSettings.WIFI_LABEL_SHARED), "sharing is opt-in");
        check(StatusBarLayoutSettings.owns(StatusBarLayoutSettings.WIFI_LABEL_SHARED), "protocol recognizes its Boolean key");
        check(!StatusBarLayoutSettings.Snapshot.parse(new Bundle()).wifiLabelShared, "empty configuration is independent");
        SettingsCatalog.Item toggle = SettingsCatalog.item(StatusBarLayoutSettings.WIFI_LABEL_SHARED);
        check(toggle != null && SettingsCatalog.BOOLEAN.equals(toggle.type), "visible switch has the Boolean protocol");
        check(SettingsCatalog.items("network").contains(toggle), "shared switch belongs to the single actual network editor");
        equal("common",SettingsCatalog.networkDetailPageId(toggle.key),"shared position is grouped in the common position tab");
        equal("", SettingsCatalog.unavailableReason(toggle.key, Collections.emptyMap()), "sharing requires neither style nor sorting");
        for(String old:new String[]{"wifi","label","native_network_badge"})
            equal("network",SettingsCatalog.canonicalGroupId(old),"old network destinations still reach the unified page");
        equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(toggle.key), "switch is portable as Boolean");
        check(ConfigScope.section("wifi").keys().contains(toggle.key), "shared switch has a stable export owner");
        Map<String, Object> values = new LinkedHashMap<>(); values.put(toggle.key, true);
        values.put(gap("wifi", "left"), 3.25f); values.put(gap("label", "left"), 19.5f);
        values.put(StatusBarLayoutSettings.key("wifi", "side"), "left"); values.put(StatusBarLayoutSettings.key("wifi", "order"), 3f);
        values.put(StatusBarLayoutSettings.key("label", "order"), 2f);
        StatusBarLayoutSettings.Snapshot state = StatusBarLayoutSettings.Snapshot.parse(bundle(values));
        check(state.enabled && state.wifiLabelShared && !state.orderingEnabled, "sharing enables the actual slot with sorting off");
        equal("wifi", state.slotToken("wifi"), "Wi-Fi is the canonical physical slot token");
        equal("wifi", state.slotToken("label"), "label selects that same slot rather than copying coordinates");
        equal("mobile", state.slotToken("mobile"), "cellular signal remains an independent slot");
        equal("native", state.side("wifi"), "shared mode respects the independent sorting switch");
        equal(0, state.order("wifi"), "sorting off leaves saved canonical order inactive");
        equal(19.5f, state.left("label"), "raw inner label value remains available for exact native restore");
        values.put(StatusBarLayoutSettings.MASTER, true); state = StatusBarLayoutSettings.Snapshot.parse(bundle(values));
        equal("left", state.side(state.slotToken("label")), "shared slot resolves actual side through canonical Wi-Fi settings");
        equal(3, state.order(state.slotToken("label")), "shared slot resolves order through canonical Wi-Fi settings");
        equal(3.25f, state.left(state.slotToken("label")), "shared slot resolves margins through canonical Wi-Fi settings");
        check(!SettingsCatalog.unavailableReason(StatusBarLayoutSettings.key("label", "order"), values).isEmpty(), "inactive independent label order cannot appear active while shared");
        values.put(StatusBarSettings.SAFE_MODE, true); state = StatusBarLayoutSettings.Snapshot.parse(bundle(values));
        check(!state.enabled && !state.wifiLabelShared, "safe mode suspends both real sharing and placement");
        equal("label", state.slotToken("label"), "safe mode restores independent native ownership");
        equal(gap("wifi", "left"), StatusBarLayoutSettings.editingKey(gap("label", "left"), values), "safe mode does not rewrite the user's configured shared editing choice");
        values.remove(StatusBarSettings.SAFE_MODE); values.put(toggle.key, false);
        equal("label", StatusBarLayoutSettings.Snapshot.parse(bundle(values)).slotToken("label"), "turning off restores independent runtime slot identity");
        Map<String, Object> imported = ConfigTransfer.prepare(ConfigTransfer.exportJson(values), true).values();
        equal(false, imported.get(toggle.key), "shared opt-in round-trips exactly");
        equal(19.5f, imported.get(gap("label", "left")), "sharing never destroys independently stored export data");
        equal(3.25f, imported.get(gap("wifi", "left")), "canonical export data remains independent");
        editorsAndTrial();
        System.out.println("WifiLabelSharedPositionCheck: " + checks + " checks passed");
    }
}
