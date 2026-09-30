package dev.puitheme;

import android.content.SharedPreferences;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** No device, disk preferences or user configuration is used by these checks. */
public final class ConfigTransferCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Config transfer check " + checks + " failed");
    }
    private static void truth(boolean actual) { equal(true, actual); }
    private static String document(String settings) {
        return "{\"package\":\"" + ConfigTransfer.PACKAGE_NAME + "\",\"schema\":1,\"settings\":{" + settings + "}}";
    }
    private static Map<String, Object> values(String settings) throws IOException {
        return ConfigTransfer.prepare(document(settings), true).values();
    }
    private static void invalid(String json) throws Exception {
        checks++;
        try { ConfigTransfer.prepare(json, true); }
        catch (IOException expected) { return; }
        throw new AssertionError("Invalid configuration accepted at check " + checks);
    }
    private static void invalidSetting(String setting) throws Exception { invalid(document(setting)); }
    private static String panel(String group, String suffix) { return CarrierPanels.key(group, suffix); }

    public static void main(String[] args) throws Exception {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put(StatusBarSettings.DIAGNOSTICS_ENABLED, true);
        stored.put(StatusBarSettings.FONT_REVISION, "local-font-revision-marker");
        stored.put(StatusBarSettings.FONT_NAME, "local-font-name-marker");
        stored.put("font_path", "private-file-marker");
        stored.put("font_uri", "private-uri-marker");
        stored.put("device_id", "private-device-marker");
        stored.put("diagnostic_log", "private-log-marker");
        stored.put(StatusBarSettings.CARRIER_TEXT, "共享文字");
        stored.put(StatusBarSettings.CARRIER_MODE, "time");
        stored.put(StatusBarSettings.CARRIER_PATTERN, "yyyy年M月d日 {周} {时段} HH:mm:ss");
        stored.put(StatusBarSettings.CARRIER_OFFSET_X, -1234.56f);
        stored.put("carrier_color_light", 0x66123456);
        stored.put("carrier_color_dark", 0xff334455);
        stored.put("carrier_color_dark_custom_alpha", true);
        stored.put("carrier_position_enabled", false);
        stored.put("offset_x", 600.25f);
        stored.put("offset_y", -400.75f);
        stored.put("icon_scale", 400.25f);
        stored.put(StatusBarSettings.TILES_FADE_RANGE, 350.25f);
        String exported = ConfigTransfer.exportJson(stored);
        ConfigTransfer.PreparedImport full = ConfigTransfer.prepare(exported, true);
        equal(ConfigTransfer.types().size(), full.count);
        equal(full.count, full.count());
        equal(false, full.fontFallback);
        equal("", full.warning);
        equal("", full.warning());
        for (String marker : new String[]{"local-font-revision-marker", "local-font-name-marker",
                "private-file-marker", "private-uri-marker", "private-device-marker", "private-log-marker",
                "diagnostics_enabled", "font_revision", "font_name", "font_path", "font_uri", "device_id", "diagnostic_log"})
            equal(false, exported.contains(marker));
        for (String group : new String[]{CarrierPanels.NOTIFICATION, CarrierPanels.CONTROL}) {
            equal("共享文字", full.values().get(panel(group, "text")));
            equal("time", full.values().get(panel(group, "mode")));
            equal(stored.get(StatusBarSettings.CARRIER_PATTERN), full.values().get(panel(group, "pattern")));
            equal(-1234.56f, full.values().get(panel(group, "offset_x")));
            equal(false, full.values().get(panel(group, "position_enabled")));
            equal(0x66123456, full.values().get(panel(group, "color_light")));
            equal(true, full.values().get(panel(group, "color_light_custom_alpha")));
            equal(0xff334455, full.values().get(panel(group, "color_dark")));
            equal(true, full.values().get(panel(group, "color_dark_custom_alpha")));
        }
        equal("original", full.values().get(panel(CarrierPanels.LOCKSCREEN, "mode")));
        equal(false, full.values().get(panel(CarrierPanels.LOCKSCREEN, "enabled")));
        equal(0f, full.values().get(panel(CarrierPanels.LOCKSCREEN, "offset_x")));
        equal(0xff000000, full.values().get(panel(CarrierPanels.LOCKSCREEN, "color_light")));
        equal(false, full.values().get(panel(CarrierPanels.LOCKSCREEN, "color_light_custom_alpha")));
        for (String group : new String[]{"wifi", "data"}) {
            equal(600.25f, full.values().get(group + "_offset_x"));
            equal(-400.75f, full.values().get(group + "_offset_y"));
            equal(400.25f, full.values().get(group + "_icon_scale"));
        }
        equal(350.25f, full.values().get(StatusBarSettings.TILES_LEFT_RANGE));
        equal(350.25f, full.values().get(StatusBarSettings.TILES_RIGHT_RANGE));
        equal(ConfigTransfer.types().size(), ConfigTransfer.prepare(ConfigTransfer.exportJson(nullMap()), true).count);
        equal(ConfigTransfer.types().size(), ConfigTransfer.prepare(ConfigTransfer.exportJson(Collections.emptyMap()), true).count);
        checks++;
        try { full.values().put("wifi_offset_x", 7f); throw new AssertionError("Prepared import is mutable"); }
        catch (UnsupportedOperationException expected) { }
        checks++;
        try { ConfigTransfer.types().clear(); throw new AssertionError("Registry is mutable"); }
        catch (UnsupportedOperationException expected) { }

        // Explicit panel values, including opaque custom colors, are portable and take precedence.
        stored.put(panel(CarrierPanels.NOTIFICATION, "text"), "通知独立");
        stored.put(panel(CarrierPanels.NOTIFICATION, "color_light"), 0xff778899);
        stored.put(panel(CarrierPanels.NOTIFICATION, "color_light_custom_alpha"), true);
        stored.put(panel(CarrierPanels.CONTROL, "color_dark_custom_alpha"), false);
        full = ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true);
        equal("通知独立", full.values().get(panel(CarrierPanels.NOTIFICATION, "text")));
        equal("共享文字", full.values().get(panel(CarrierPanels.CONTROL, "text")));
        equal(0xff778899, full.values().get(panel(CarrierPanels.NOTIFICATION, "color_light")));
        equal(true, full.values().get(panel(CarrierPanels.NOTIFICATION, "color_light_custom_alpha")));
        equal(false, full.values().get(panel(CarrierPanels.CONTROL, "color_dark_custom_alpha")));

        Map<String, Object> numbers = values("\"wifi_offset_x\":-1500.25,\"data_offset_y\":1750.75,"
                + "\"wifi_icon_scale\":400.5,\"clock_scale\":0,\"clock_spacing\":-900.12,"
                + "\"speed_line_gap\":-123.25,\"font_weight\":100,\"carrier_weight\":900,"
                + "\"battery_width_scale\":12000.5,\"tiles_blur_radius\":8000.25");
        equal(-1500.25f, numbers.get("wifi_offset_x"));
        equal(1750.75f, numbers.get("data_offset_y"));
        equal(400.5f, numbers.get("wifi_icon_scale"));
        equal(0f, numbers.get("clock_scale"));
        equal(-900.12f, numbers.get("clock_spacing"));
        equal(-123.25f, numbers.get("speed_line_gap"));
        equal(100f, numbers.get("font_weight"));
        equal(900f, numbers.get("carrier_weight"));
        equal(12000.5f, numbers.get("battery_width_scale"));
        equal(8000.25f, numbers.get("tiles_blur_radius"));
        equal(Float.MAX_VALUE, values("\"wifi_offset_x\":3.4028235e38").get("wifi_offset_x"));
        equal(Float.MIN_VALUE, values("\"wifi_offset_x\":1.4e-45").get("wifi_offset_x"));
        equal(0f, values("\"wifi_icon_scale\":-0").get("wifi_icon_scale"));
        for (String setting : new String[]{"\"font_weight\":99", "\"carrier_weight\":901",
                "\"wifi_icon_scale\":-0.01", "\"battery_scale\":-1", "\"slot_width\":-1",
                "\"wifi_offset_x\":1e100", "\"wifi_offset_x\":1e2147483647",
                "\"wifi_offset_x\":1e-2147483647", "\"wifi_icon_scale\":-1e-100",
                "\"wifi_offset_x\":NaN", "\"wifi_offset_x\":Infinity", "\"wifi_offset_x\":\"3.25\"",
                "\"wifi_offset_x\":null", "\"wifi_offset_x\":true", "\"wifi_offset_x\":[]",
                "\"wifi_offset_x\":{}", "\"wifi_offset_x\":01", "\"wifi_offset_x\":+1",
                "\"wifi_offset_x\":1.", "\"wifi_offset_x\":.1", "\"wifi_offset_x\":1e",
                "\"wifi_offset_x\":1e+", "\"wifi_offset_x\":0x11", "\"wifi_offset_x\":1_000",
                "\"wifi_color_light\":1.5", "\"wifi_color_light\":2147483648",
                "\"wifi_color_light\":-2147483649", "\"wifi_color_light\":1e2147483647",
                "\"wifi_color_light\":1e-2147483647", "\"wifi_color_light\":\"#FFFFFFFF\"",
                "\"wifi_enabled\":1", "\"wifi_enabled\":\"true\"", "\"wifi_enabled\":null"}) invalidSetting(setting);
        equal(Integer.MIN_VALUE, values("\"wifi_color_light\":-2147483648").get("wifi_color_light"));
        equal(Integer.MAX_VALUE, values("\"wifi_color_light\":2147483647").get("wifi_color_light"));
        equal(0, values("\"wifi_color_light\":0e2147483647").get("wifi_color_light"));
        equal(0, values("\"wifi_color_light\":0e2147483646").get("wifi_color_light"));
        equal(false, values("\"wifi_enabled\":false").get("wifi_enabled"));
        equal(true, values("\"wifi_enabled\":true").get("wifi_enabled"));

        for (String setting : new String[]{"\"diagnostics_enabled\":true", "\"font_name\":\"x\"",
                "\"font_revision\":\"x\"", "\"font_path\":\"x\"", "\"font_uri\":\"x\"",
                "\"device_id\":\"x\"", "\"diagnostic_log\":\"x\"", "\"unknown\":1",
                "\"font_mode\":\"uri\"", "\"signal_layout\":\"double\"", "\"battery_style\":\"other\"",
                "\"carrier_mode\":\"device\"", "\"carrier_text\":12", "\"clock_pattern\":true",
                "\"clock_pattern\":\"{未知}\"", "\"clock_pattern\":\"HH:mm\\n\"",
                "\"clock_pattern\":\"\"", "\"clock_pattern\":\"'unfinished\"",
                "\"carrier_text\":\"line\\nline\"", "\"carrier_text\":\"x\\u0000\"",
                "\"carrier_text\":\"\\uD800\"", "\"carrier_text\":\"\\uDC00\"",
                "\"carrier_text\":\"\\u１２３４\"", "\"carrier_text\":\"bad\\x00\""}) invalidSetting(setting);
        equal("文字 🌟 \"A\" \\ /", values("\"carrier_text\":\"文字 \\uD83C\\uDF1F \\\"A\\\" \\\\ \\/\"").get("carrier_text"));
        equal("{年}年{月}月{日}日 {星期} {时段} {12时}:{分}:{秒}", values("\"clock_pattern\":\"{年}年{月}月{日}日 {星期} {时段} {12时}:{分}:{秒}\"").get("clock_pattern"));
        invalidSetting("\"carrier_text\":\"" + "x".repeat(121) + "\"");
        invalidSetting("\"clock_pattern\":\"'" + "x".repeat(200) + "'\"");
        for (String choice : new String[]{"system", "pingfang", "custom"})
            equal(choice, values("\"font_mode\":\"" + choice + "\"").get("font_mode"));
        for (String choice : new String[]{"system", "single"})
            equal(choice, values("\"signal_layout\":\"" + choice + "\"").get("signal_layout"));
        for (String choice : new String[]{"native", "pui"})
            equal(choice, values("\"battery_style\":\"" + choice + "\"").get("battery_style"));
        for (String group : CarrierPanels.GROUPS)
            for (String choice : new String[]{"original", "text", "time"})
                equal(choice, values("\"" + panel(group, "mode") + "\":\"" + choice + "\"").get(panel(group, "mode")));

        ConfigTransfer.PreparedImport fallback = ConfigTransfer.prepare(document("\"font_mode\":\"custom\""), false);
        equal("system", fallback.values().get("font_mode"));
        equal(true, fallback.fontFallback);
        equal(true, fallback.hasCustomFontFallback());
        truth(fallback.warning.contains("系统字体"));
        equal(false, ConfigTransfer.prepare(document("\"font_mode\":\"pingfang\""), false).fontFallback);
        equal(false, ConfigTransfer.prepare(document("\"wifi_offset_x\":1"), false).fontFallback);
        equal("custom", ConfigTransfer.prepare(document("\"font_mode\":\"custom\""), true).values().get("font_mode"));

        String valid = document("\"wifi_offset_x\":1");
        equal(1, ConfigTransfer.prepare("\ufeff" + valid, true).count);
        equal(1, ConfigTransfer.prepare(valid.replace("\"schema\":1", "\"schema\":1.0"), true).count);
        for (String bad : new String[]{"", " ", "[]", "null", valid + " {}", "/*x*/" + valid,
                valid.replace("\"package\":", "package:"), valid.replace("\"settings\"", "'settings'"),
                valid.replace("\"schema\":1", "\"schema\":2"), valid.replace("\"schema\":1", "\"schema\":\"1\""),
                valid.replace(ConfigTransfer.PACKAGE_NAME, "dev.other"), valid.replace("\"schema\":1,", ""),
                valid.replace("\"schema\":1,", "\"schema\":1,\"unknown\":1,"),
                valid.replace("\"schema\":1,", "\"schema\":1,\"schema\":1,"),
                document(""), document("\"wifi_offset_x\":1,\"wifi_offset_x\":2"),
                document("\"wifi_offset_x\":1,\"wifi_offset_\\u0078\":2"), document("\"wifi_offset_x\":1,"),
                document("\"wifi_offset_x\":1//x\n"), document("\"wifi_offset_x\":1/*x*/"),
                document("\"wifi_offset_x\":{\"deep\":{}}"), document("\"wifi_offset_x\":" + "1".repeat(129))}) invalid(bad);

        stored.put(StatusBarSettings.CARRIER_TEXT, "第一行\n第二行\r尾");
        equal("第一行 第二行 尾", ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true).values().get(StatusBarSettings.CARRIER_TEXT));
        stored.put(StatusBarSettings.CARRIER_TEXT, "x".repeat(119) + "🌟suffix");
        equal("x".repeat(119), ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true).values().get(StatusBarSettings.CARRIER_TEXT));
        stored.put(StatusBarSettings.CARRIER_TEXT, "x".repeat(150));
        equal(120, ((String) ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true).values().get(StatusBarSettings.CARRIER_TEXT)).length());
        stored.put("wifi_icon_scale", -5f);
        stored.put("clock_weight", 5000f);
        stored.put("clock_pattern", "{未知}");
        stored.put("battery_style", "invalid");
        full = ConfigTransfer.prepare(ConfigTransfer.exportJson(stored), true);
        equal(0f, full.values().get("wifi_icon_scale"));
        equal(900f, full.values().get("clock_weight"));
        equal(TimeFormat.CLOCK_DEFAULT, full.values().get("clock_pattern"));
        equal("pui", full.values().get("battery_style"));

        FakePreferences preferences = new FakePreferences(stored);
        equal(0, preferences.editCount);
        invalidSetting("\"wifi_offset_x\":3,\"battery_scale\":-1");
        equal(0, preferences.editCount);
        ConfigTransfer.PreparedImport prepared = ConfigTransfer.prepare(document("\"wifi_offset_x\":3.25,\"data_enabled\":false,\"carrier_text\":\"导入\",\"wifi_color_light\":-1"), true);
        equal(0, preferences.editCount);
        equal(true, ConfigTransfer.commit(preferences, prepared));
        equal(1, preferences.editCount);
        equal(1, preferences.commitCount);
        equal(4, preferences.lastWriteCount);
        equal(false, preferences.clearCalled);
        equal(3.25f, preferences.values.get("wifi_offset_x"));
        equal(false, preferences.values.get("data_enabled"));
        equal("导入", preferences.values.get("carrier_text"));
        equal(-1, preferences.values.get("wifi_color_light"));
        equal(true, preferences.values.get(StatusBarSettings.DIAGNOSTICS_ENABLED));
        equal("local-font-name-marker", preferences.values.get(StatusBarSettings.FONT_NAME));
        equal("local-font-revision-marker", preferences.values.get(StatusBarSettings.FONT_REVISION));
        equal("private-uri-marker", preferences.values.get("font_uri"));
        preferences.persist = false;
        equal(false, ConfigTransfer.commit(preferences, prepared));
        equal(2, preferences.commitCount);
        equal(2, preferences.editCount);
        equal(3.25f, preferences.values.get("wifi_offset_x"));
        equal(false, preferences.clearCalled);
        FakePreferences clean = new FakePreferences(Collections.emptyMap());
        equal(true, ConfigTransfer.commit(clean, full));
        equal(ConfigTransfer.types().size(), clean.lastWriteCount);
        equal(ConfigTransfer.types().size(), clean.values.size());
        equal(ConfigTransfer.exportJson(clean), ConfigTransfer.exportJson(clean.getAll()));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConfigTransfer.write(bytes, stored);
        equal(ConfigTransfer.exportJson(stored), bytes.toString(StandardCharsets.UTF_8));
        equal(ConfigTransfer.types().size(), ConfigTransfer.read(new ByteArrayInputStream(bytes.toByteArray()), true).count);
        ZeroReadStream intermittentlyZero = new ZeroReadStream(valid.getBytes(StandardCharsets.UTF_8));
        equal(1, ConfigTransfer.read(intermittentlyZero, true).count);
        equal(false, intermittentlyZero.closed);
        checks++;
        try { ConfigTransfer.read(new ByteArrayInputStream(new byte[]{(byte)0xc3, (byte)0x28}), true); throw new AssertionError("Malformed UTF-8 accepted"); }
        catch (IOException expected) { }
        checks++;
        try { ConfigTransfer.read(new ByteArrayInputStream(new byte[ConfigTransfer.MAX_BYTES + 1]), true); throw new AssertionError("Oversize stream accepted"); }
        catch (IOException expected) { }
        invalid("x".repeat(ConfigTransfer.MAX_BYTES + 1));
        invalid("中".repeat(ConfigTransfer.MAX_BYTES / 2));

        // The global QS appearance schema has two scene palettes and no category settings.
        equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get("qs_appearance_enabled"));
        equal(false, ConfigTransfer.prepare(ConfigTransfer.exportJson(Collections.emptyMap()), true).values().get("qs_appearance_enabled"));
        equal(true, values("\"qs_appearance_enabled\":true").get("qs_appearance_enabled"));
        for (String key : ConfigTransfer.types().keySet()) equal(false, key.startsWith("qs_style_"));
        for (String scene : new String[]{"light", "dark"}) {
                    String prefix = "qs_global_" + scene;
                    equal(ConfigTransfer.Type.COLOR, ConfigTransfer.types().get(prefix + "_color"));
                    equal(ConfigTransfer.Type.COLOR, ConfigTransfer.types().get(prefix + "_gradient_color"));
                    equal(ConfigTransfer.Type.NUMBER, ConfigTransfer.types().get(prefix + "_opacity"));
                    equal(ConfigTransfer.Type.NUMBER, ConfigTransfer.types().get(prefix + "_gradient_angle"));
                    equal(ConfigTransfer.Type.BOOLEAN, ConfigTransfer.types().get(prefix + "_gradient_enabled"));
                    equal(0xff102030, values("\"" + prefix + "_color\":" + 0xff102030).get(prefix + "_color"));
                    equal(0f, values("\"" + prefix + "_opacity\":0").get(prefix + "_opacity"));
                    equal(100f, values("\"" + prefix + "_opacity\":100").get(prefix + "_opacity"));
                    equal(200f, values("\"" + prefix + "_opacity\":200").get(prefix + "_opacity"));
                    equal(Float.MAX_VALUE, values("\"" + prefix + "_opacity\":3.4028235e38").get(prefix + "_opacity"));
                    Map<String, Object> customOpacity = new HashMap<>();
                    customOpacity.put(prefix + "_opacity", 200f);
                    equal(200f, ConfigTransfer.prepare(ConfigTransfer.exportJson(customOpacity), true).values().get(prefix + "_opacity"));
                    customOpacity.put(prefix + "_opacity", Float.MAX_VALUE);
                    equal(Float.MAX_VALUE, ConfigTransfer.prepare(ConfigTransfer.exportJson(customOpacity), true).values().get(prefix + "_opacity"));
                    equal(1080.25f, values("\"" + prefix + "_gradient_angle\":1080.25").get(prefix + "_gradient_angle"));
                    equal(false, values("\"" + prefix + "_gradient_enabled\":false").get(prefix + "_gradient_enabled"));
                    equal(0xff223344, values("\"" + prefix + "_gradient_color\":" + 0xff223344).get(prefix + "_gradient_color"));
                    equal(100.01f, values("\"" + prefix + "_opacity\":100.01").get(prefix + "_opacity"));
                    invalidSetting("\"" + prefix + "_opacity\":-1");
                    invalidSetting("\"" + prefix + "_gradient_angle\":-1");
        }
        invalidSetting("\"qs_style_wifi_enabled\":true");
        invalidSetting("\"qs_style_wifi_light_opacity\":50");
        System.out.println(checks + " checks passed (portable snapshots, strict bounded import, legacy migration, numeric rules, font fallback, one bulk commit)");
    }

    private static Map<String, Object> nullMap() { return null; }
    private static final class ZeroReadStream extends ByteArrayInputStream {
        boolean zero = true, closed;
        ZeroReadStream(byte[] bytes) { super(bytes); }
        @Override public synchronized int read(byte[] b, int off, int len) {
            if (zero) { zero = false; return 0; } zero = true;
            return super.read(b, off, len);
        }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
    private static final class FakePreferences implements SharedPreferences {
        final Map<String, Object> values = new LinkedHashMap<>();
        int editCount, commitCount, lastWriteCount;
        boolean clearCalled, persist = true;
        FakePreferences(Map<String, ?> source) { values.putAll(source); }
        public Map<String, ?> getAll() { return new LinkedHashMap<>(values); }
        public String getString(String key, String fallback) { return values.containsKey(key) ? (String)values.get(key) : fallback; }
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key, Set<String> fallback) { return values.containsKey(key) ? (Set<String>) values.get(key) : fallback; }
        public int getInt(String key, int fallback) { return values.containsKey(key) ? (Integer) values.get(key) : fallback; }
        public long getLong(String key, long fallback) { return values.containsKey(key) ? (Long) values.get(key) : fallback; }
        public float getFloat(String key, float fallback) { return values.containsKey(key) ? (Float) values.get(key) : fallback; }
        public boolean getBoolean(String key, boolean fallback) { return values.containsKey(key) ? (Boolean) values.get(key) : fallback; }
        public boolean contains(String key) { return values.containsKey(key); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener ignored) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener ignored) { }
        public Editor edit() { editCount++; return new FakeEditor(); }
        private final class FakeEditor implements Editor {
            final Map<String, Object> pending = new HashMap<>();
            public Editor putString(String key, String value) { pending.put(key, value); return this; }
            public Editor putStringSet(String key, Set<String> value) { pending.put(key, new HashSet<>(value)); return this; }
            public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            public Editor remove(String key) { throw new AssertionError("Import must not remove preferences"); }
            public Editor clear() { clearCalled = true; throw new AssertionError("Import must preserve local settings"); }
            public boolean commit() { commitCount++; lastWriteCount = pending.size(); values.putAll(pending); return persist; }
            public void apply() { throw new AssertionError("Import requires one checked commit"); }
        }
    }
}
