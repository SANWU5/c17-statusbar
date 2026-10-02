// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable settings only. Preparing an import never changes preferences. */
public final class ConfigTransfer {
    public static final String PACKAGE_NAME = "dev.puitheme.iosstatusbar";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 256 * 1024;
    public enum Type { BOOLEAN, NUMBER, COLOR, STRING }
    private static final Map<String, Type> TYPES = registry();

    private ConfigTransfer() {}

    /** Immutable validated values; the UI must obtain confirmation before commit(). */
    public static final class PreparedImport {
        private final Map<String, Object> values;
        public final int count;
        public final boolean fontFallback;
        public final String warning;
        private PreparedImport(Map<String, Object> values, boolean fontFallback) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.count = values.size();
            this.fontFallback = fontFallback;
            this.warning = fontFallback ? "当前设备没有导入的自选字体，将使用系统字体。字体文件不会随配置传输。" : "";
        }
        public int count() { return count; }
        public Map<String, Object> values() { return values; }
        public boolean hasCustomFontFallback() { return fontFallback; }
        public String warning() { return warning; }
    }

    /** Known defaults include clock entry/border and independent clear styles, never arbitrary preferences. */
    private static Map<String, Type> registry() {
        Map<String, Type> result = new LinkedHashMap<>();
        for (String key : StatusBarSettings.BOOLEAN_DEFAULTS.keySet())
            if (!StatusBarSettings.DIAGNOSTICS_ENABLED.equals(key) && !StatusBarSettings.SAFE_MODE.equals(key))
                result.put(key, Type.BOOLEAN);
        for (String key : StatusBarSettings.NUMERIC_DEFAULTS.keySet()) result.put(key, Type.NUMBER);
        for (String key : StatusBarSettings.COLOR_DEFAULTS.keySet()) {
            result.put(key, Type.COLOR);
            result.put(StatusBarSettings.alphaKey(key), Type.BOOLEAN);
        }
        for (String key : StatusBarSettings.STRING_DEFAULTS.keySet())
            if (portableString(key)) result.put(key, Type.STRING);
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Type> types() { return TYPES; }

    private static boolean portableString(String key) {
        if (NotificationIconArea.MODE.equals(key) || NotificationIconArea.TEXT.equals(key)) return true;
        if (key.equals(StatusBarSettings.FONT_MODE) || key.equals(StatusBarSettings.SIGNAL_LAYOUT)
                || key.equals(StatusBarSettings.BATTERY_STYLE) || key.equals(StatusBarSettings.CLOCK_PATTERN)
                || key.equals(StatusBarSettings.SHADE_CLOCK_PATTERN)
                || NotificationBigClockSettings.STRINGS.containsKey(key)
                || key.equals(StatusBarSettings.CARRIER_MODE) || key.equals(StatusBarSettings.CARRIER_PATTERN)
                || key.equals(StatusBarSettings.CARRIER_TEXT)) return true;
        for (String group : CarrierPanels.GROUPS)
            if (key.equals(CarrierPanels.key(group, "mode")) || key.equals(CarrierPanels.key(group, "pattern"))
                    || key.equals(CarrierPanels.key(group, "text"))) return true;
        return false;
    }

    /** Export a complete effective snapshot, resolving old shared values and alpha inheritance. */
    public static String exportJson(Map<String, ?> stored) throws IOException {
        Map<String, ?> source = stored == null ? Collections.emptyMap() : stored;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Type> item : TYPES.entrySet()) {
            String key = item.getKey();
            Object value;
            switch (item.getValue()) {
                case BOOLEAN:
                    value = key.endsWith("_custom_alpha")
                            ? StatusBarSettings.customAlpha(source, key.substring(0, key.length() - "_custom_alpha".length()))
                            : StatusBarSettings.bool(source, key);
                    break;
                case NUMBER:
                    float number = NumericPolicy.finite(StatusBarSettings.settingNumber(source, key,
                            StatusBarSettings.NUMERIC_DEFAULTS.get(key)), StatusBarSettings.NUMERIC_DEFAULTS.get(key));
                    value = number;
                    break;
                case COLOR: value = StatusBarSettings.color(source, key); break;
                default:
                    String text = StatusBarSettings.string(source, key);
                    if (key.endsWith("_text")) text = portableText(text);
                    try { value = validateString(key, text); }
                    catch (IOException invalidStoredValue) { value = StatusBarSettings.STRING_DEFAULTS.get(key); }
            }
            snapshot.put(key, value);
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"package\": "); quote(json, PACKAGE_NAME);
        json.append(",\n  \"schema\": ").append(SCHEMA_VERSION).append(",\n  \"settings\": {\n");
        boolean first = true;
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if (!first) json.append(",\n"); first = false;
            json.append("    "); quote(json, entry.getKey()); json.append(": ");
            if (entry.getValue() instanceof String) quote(json, (String) entry.getValue());
            else json.append(entry.getValue());
        }
        json.append("\n  }\n}\n");
        String result = json.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid("配置文件过大");
        return result;
    }

    public static String exportJson(SharedPreferences preferences) throws IOException {
        if (preferences == null) throw invalid("无法读取当前设置");
        return exportJson(preferences.getAll());
    }

    /** Streams belong to the caller and are not closed by these helpers. */
    public static void write(OutputStream output, Map<String, ?> stored) throws IOException {
        if (output == null) throw invalid("无法打开保存位置");
        byte[] bytes = exportJson(stored).getBytes(StandardCharsets.UTF_8);
        output.write(bytes);
    }

    public static PreparedImport read(InputStream input, boolean customFontAvailable) throws IOException {
        if (input == null) throw invalid("无法读取配置文件");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) {
                int next = input.read();
                if (next == -1) break;
                if (output.size() == MAX_BYTES) throw invalid("配置文件超过 256 KB");
                output.write(next);
                continue;
            }
            if (output.size() > MAX_BYTES - count) throw invalid("配置文件超过 256 KB");
            output.write(buffer, 0, count);
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
            return prepare(json, customFontAvailable);
        } catch (CharacterCodingException malformed) { throw invalid("配置文件需要使用 UTF-8 编码"); }
    }

    /** Strict JSON, exact types and all keys are checked before producing an applicable object. */
    public static PreparedImport prepare(String json, boolean customFontAvailable) throws IOException {
        if (json == null || json.length() > MAX_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw invalid("配置文件为空或超过 256 KB");
        Map<String, Object> document = new JsonReader(json).document();
        for (String key : document.keySet())
            if (!key.equals("package") && !key.equals("schema") && !key.equals("settings"))
                throw invalid("配置文件包含不支持的顶层字段");
        if (!PACKAGE_NAME.equals(document.get("package"))) throw invalid("这不是 C17 状态栏配置文件");
        Object schema = document.get("schema");
        if (!(schema instanceof BigDecimal) || ((BigDecimal) schema).compareTo(BigDecimal.valueOf(SCHEMA_VERSION)) != 0)
            throw invalid("不支持这个配置文件版本");
        Object raw = document.get("settings");
        if (!(raw instanceof Map)) throw invalid("配置文件缺少设置对象");
        Map<?, ?> settings = (Map<?, ?>) raw;
        if (settings.isEmpty()) throw invalid("配置文件没有可导入的设置");
        Map<String, Object> validated = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : settings.entrySet()) {
            String key = (String) entry.getKey();
            if (QsTileCorners.LEGACY_RADIUS.equals(key)) {
                if (settings.containsKey(QsTileCorners.RADIUS)) continue;
                key = QsTileCorners.RADIUS;
            }
            Type type = TYPES.get(key);
            if (type == null) throw invalid("配置文件包含不支持或不可移植的设置");
            Object value = entry.getValue();
            if (StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(key)) {
                // Ignore the former switch's value and type without discarding the user's import.
                validated.put(key, true);
                continue;
            }
            if (NotificationBigClockSettings.STACK_ENABLED.equals(key)) {
                validated.put(key, false);
                continue;
            }
            switch (type) {
                case BOOLEAN:
                    if (!(value instanceof Boolean)) throw invalid("开关设置类型不正确");
                    break;
                case NUMBER:
                    if (!(value instanceof BigDecimal)) throw invalid("数值设置类型不正确");
                    float number = ((BigDecimal) value).floatValue();
                    if (Float.isNaN(number) || Float.isInfinite(number)
                            || number == 0f && ((BigDecimal) value).signum() != 0)
                        throw invalid("数值超出可保存范围");
                    String numericError = SettingsCatalog.numericInputError(key, (BigDecimal) value);
                    if (numericError != null) throw invalid(numericError);
                    if (QsTileCorners.RADIUS.equals(key) && (number < 0f || number > QsTileCorners.MAX_RADIUS))
                        throw invalid("圆角半径应为 0 到 30 dp");
                    if (NotificationIconArea.MAX_COUNT.equals(key) && (number < 0f || number != Math.floor(number)))
                        throw invalid("图标数量应为非负整数，0 表示不显示");
                    // Persist finite user values verbatim; rendering has separate physical guards.
                    value = number;
                    break;
                case COLOR:
                    if (!(value instanceof BigDecimal)) throw invalid("颜色设置类型不正确");
                    BigDecimal color = (BigDecimal) value;
                    // Bound magnitude before intValueExact: very large JSON exponents must never
                    // cause BigDecimal to materialize an enormous integer during conversion.
                    if (color.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
                            || color.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0)
                        throw invalid("颜色需要使用 32 位 ARGB 整数");
                    try { value = color.signum() == 0 ? 0 : color.intValueExact(); }
                    catch (ArithmeticException invalidColor) { throw invalid("颜色需要使用 32 位 ARGB 整数"); }
                    break;
                default:
                    if (!(value instanceof String)) throw invalid("文本设置类型不正确");
                    value = validateString(key, (String) value);
            }
            validated.put(key, value);
        }
        boolean fallback = !customFontAvailable && ("custom".equals(validated.get(StatusBarSettings.FONT_MODE))
                || "custom".equals(validated.get(NotificationBigClockSettings.FONT)));
        if (!customFontAvailable) {
            if ("custom".equals(validated.get(StatusBarSettings.FONT_MODE))) validated.put(StatusBarSettings.FONT_MODE, "system");
            if ("custom".equals(validated.get(NotificationBigClockSettings.FONT))) validated.put(NotificationBigClockSettings.FONT, "system");
        }
        return new PreparedImport(validated, fallback);
    }

    /**
     * One bulk commit, without clearing local diagnostics, font metadata or other private values.
     * A false result means disk persistence failed; Android may already expose the complete new
     * snapshot in memory. Never attempt a second partial write or claim that false is a rollback.
     */
    public static boolean commit(SharedPreferences preferences, PreparedImport prepared) {
        if (preferences == null || prepared == null) throw new IllegalArgumentException("Missing validated configuration");
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> item : prepared.values.entrySet()) {
            Object value = item.getValue(); String key = item.getKey();
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else editor.putString(key, (String) value);
        }
        if (!prepared.values.containsKey(StatusBarSettings.DATA_ACTIVITY_HIDDEN))
            editor.putBoolean(StatusBarSettings.DATA_ACTIVITY_HIDDEN, true);
        return editor.commit();
    }

    /** Imported fonts remain local; a portable configuration never reads or embeds their contents. */
    public static boolean customFontAvailable(Context context) {
        if (context == null || !PACKAGE_NAME.equals(context.getPackageName())) return false;
        try {
            File file = new File(context.createDeviceProtectedStorageContext().getFilesDir(), "fonts/custom.font");
            if (!file.isFile() || file.length() < 4 || file.length() > 96L * 1024 * 1024) return false;
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] magic = new byte[4];
                return input.read(magic) == magic.length && FontRepository.supportedHeader(magic);
            }
        } catch (Exception inaccessible) { return false; }
    }

    private static String portableText(String value) {
        if (value == null) return null;
        String text = value.replace('\n', ' ').replace('\r', ' ');
        int end = Math.min(TimeFormat.MAX_TEXT, text.length());
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static String validateString(String key, String value) throws IOException {
        if (value == null || !validUnicode(value)) throw invalid("文本包含无效字符");
        if (NotificationIconArea.MODE.equals(key)) {
            requireChoice(value, "native", "heart", "text", "image");
        } else if (NotificationIconArea.TEXT.equals(key)) {
            if (value.codePointCount(0,value.length()) > 12) throw invalid("通知图标文字最多 12 个字符");
            for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw invalid("通知图标文字需要单行");
        } else if (NotificationBigClockSettings.FOOTER_PATTERN.equals(key)) {
            if (NotificationBigClockSettings.footerValidationError(value) != null) throw invalid("配置中的底部内容格式无效");
        } else if (key.endsWith("_pattern")) {
            if (TimeFormat.validationError(value) != null) throw invalid("配置中的时间格式无效");
        } else if (key.endsWith("_text")) {
            if (value.length() > TimeFormat.MAX_TEXT || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
                throw invalid("自定义文字需要单行且不超过 120 个字符");
            for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i)))
                throw invalid("自定义文字包含控制字符");
        } else if (NotificationBigClockSettings.FONT.equals(key)) {
            requireChoice(value, "native", "system", "pingfang", "custom");
        } else if (NotificationBigClockSettings.ALIGNMENT.equals(key)||NotificationBigClockSettings.DATE_ALIGNMENT.equals(key)
                ||NotificationBigClockSettings.FOOTER_ALIGNMENT.equals(key)) {
            requireChoice(value, "left", "center", "right");
        } else if (key.equals(StatusBarSettings.FONT_MODE)) {
            requireChoice(value, "system", "pingfang", "custom");
        } else if (key.equals(StatusBarSettings.SIGNAL_LAYOUT)) {
            requireChoice(value, "system", "single");
        } else if (key.equals(StatusBarSettings.BATTERY_STYLE)) {
            requireChoice(value, "pui", "native");
        } else if (key.endsWith("_mode")) requireChoice(value, "original", "text", "time");
        return value;
    }

    private static void requireChoice(String value, String... options) throws IOException {
        for (String option : options) if (option.equals(value)) return;
        throw invalid("配置中的选项值不受支持");
    }

    private static boolean validUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    private static IOException invalid(String reason) { return new IOException(reason); }

    private static void quote(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c); json.append("\\u");
                        for (int n = hex.length(); n < 4; n++) json.append('0'); json.append(hex);
                    } else json.append(c);
            }
        }
        json.append('"');
    }

    /** Android's JSON parser accepts comments and duplicate names; imports use strict JSON instead. */
    private static final class JsonReader {
        private final String text; private int at;
        JsonReader(String text) { this.text = text; if (!text.isEmpty() && text.charAt(0) == '\ufeff') at = 1; }
        Map<String, Object> document() throws IOException {
            Object value = value(0); whitespace();
            if (at != text.length() || !(value instanceof Map)) throw invalid("配置文件需要一个完整 JSON 对象");
            @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) value;
            return result;
        }
        private Object value(int depth) throws IOException {
            whitespace(); if (at >= text.length()) throw invalid("配置 JSON 不完整");
            char c = text.charAt(at);
            if (c == '{') return object(depth + 1);
            if (c == '[') return array(depth + 1);
            if (c == '"') return string();
            if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
            if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
            if (text.startsWith("null", at)) { at += 4; return null; }
            if (c == '-' || c >= '0' && c <= '9') return number();
            throw invalid("配置 JSON 包含不支持的数据");
        }
        private Map<String, Object> object(int depth) throws IOException {
            if (depth > 16) throw invalid("配置 JSON 对象嵌套过深");
            at++; Map<String, Object> result = new LinkedHashMap<>(); whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace(); if (at >= text.length() || text.charAt(at) != '"') throw invalid("JSON 设置名称需要双引号");
                String name = string(); whitespace(); require(':');
                if (result.containsKey(name)) throw invalid("配置 JSON 包含重复设置名称");
                result.put(name, value(depth)); whitespace();
                if (take('}')) return result;
                require(',');
            }
        }
        private List<Object> array(int depth) throws IOException {
            if (depth > 16) throw invalid("配置 JSON 数组嵌套过深");
            at++; List<Object> result = new ArrayList<>(); whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value(depth)); whitespace();
                if (take(']')) return result;
                require(',');
            }
        }
        private String string() throws IOException {
            require('"'); StringBuilder result = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') {
                    String value = result.toString();
                    if (!validUnicode(value)) throw invalid("JSON 文本包含无效 Unicode 字符");
                    return value;
                }
                if (c < 0x20) throw invalid("JSON 文本包含未转义控制字符");
                if (c != '\\') { result.append(c); continue; }
                if (at >= text.length()) throw invalid("JSON 文本转义不完整");
                char escape = text.charAt(at++);
                switch (escape) {
                    case '"': case '\\': case '/': result.append(escape); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (at > text.length() - 4) throw invalid("Unicode 转义不完整");
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            char hex = text.charAt(at++);
                            int digit = hex >= '0' && hex <= '9' ? hex - '0'
                                    : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10
                                    : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
                            if (digit < 0) throw invalid("Unicode 转义无效"); code = code * 16 + digit;
                        }
                        result.append((char) code); break;
                    default: throw invalid("JSON 转义无效");
                }
            }
            throw invalid("JSON 文本未结束");
        }
        private BigDecimal number() throws IOException {
            int start = at; if (take('-') && at == text.length()) throw invalid("JSON 数值不完整");
            if (take('0')) {
                if (at < text.length() && digit(text.charAt(at))) throw invalid("JSON 数值不能使用前导零");
            } else {
                if (at >= text.length() || text.charAt(at) < '1' || text.charAt(at) > '9') throw invalid("JSON 数值无效");
                while (at < text.length() && digit(text.charAt(at))) at++;
            }
            if (take('.')) digits();
            if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                at++; if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++; digits();
            }
            if (at - start > 128) throw invalid("JSON 数值过长");
            try { return new BigDecimal(text.substring(start, at)); }
            catch (NumberFormatException invalidNumber) { throw invalid("JSON 数值无效"); }
        }
        private void digits() throws IOException {
            int start = at; while (at < text.length() && digit(text.charAt(at))) at++;
            if (at == start) throw invalid("JSON 数值不完整");
        }
        private static boolean digit(char c) { return c >= '0' && c <= '9'; }
        private void whitespace() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
        private boolean take(char c) { if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private void require(char c) throws IOException { if (!take(c)) throw invalid("配置 JSON 结构不正确"); }
    }
}
