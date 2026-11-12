// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Translation tables a plugin ships: {@code i18n/<language>.json} in its package maps a whole display string, exactly
 * as the plugin writes it, to the text for that language. The host shows the translation for the name, description and
 * usage, and for the titles, descriptions, units, option labels, menu names, confirmations, status values and list
 * items of the form. Keys, values and everything the host compares or stores keep the plugin's own text, so a table
 * can never change what a setting does.
 *
 * Languages are the app's tags (en, ja, zh-Hant, pt, he, id, …). A missing entry falls back to the bare language,
 * then for readers of neither Chinese to English, and finally to the plugin's own text.
 */
public final class CustomPluginLocalization {
    public static final int MAX_FILES = 64, MAX_FILE_BYTES = 2 * 1024 * 1024, MAX_ENTRIES = 20000, MAX_TEXT = 8192;
    private static final Pattern FILE = Pattern.compile("([a-z]{2,3})(?:-([A-Z][a-z]{3}))?(?:-([A-Z]{2}|[0-9]{3}))?\\.json");
    private static final Map<String, String> NONE = Collections.emptyMap();
    private static final Map<String, Map<String, String>> CACHE = new LinkedHashMap<String, Map<String, String>>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) { return size() > 12; }
    };
    private CustomPluginLocalization() { }

    /** Translates one display string of a plugin. */
    public interface Text { String tr(String text); }

    /** Every file in i18n/ of an extracted package must be a valid table; called while a package is admitted. */
    static void validate(File assets) throws IOException {
        File folder = new File(assets, "i18n");
        if (!folder.exists()) return;
        if (!folder.isDirectory()) throw new IOException("插件翻译目录无效");
        File[] files = folder.listFiles();
        if (files == null) throw new IOException("无法读取插件翻译目录");
        if (files.length > MAX_FILES) throw new IOException("插件翻译文件超过 " + MAX_FILES + " 个");
        for (File file : files) {
            if (!file.isFile() || !FILE.matcher(file.getName()).matches())
                throw new IOException("插件翻译文件应为 i18n/<语言>.json：" + file.getName());
            if (file.length() > MAX_FILE_BYTES) throw new IOException("插件翻译文件超过 2 MB：" + file.getName());
            read(file, true);
        }
    }

    /** The translator for [plugin] in the app's current language; the identity when the plugin has no table. */
    public static Text forPlugin(CustomPluginRepository.Plugin plugin) {
        Map<String, String> table = plugin == null ? NONE : table(plugin.runtimeBaseDir, C17Text.language());
        return table.isEmpty() ? IDENTITY : text -> translate(table, text);
    }

    /** The table for [language] from an installed plugin's resource folder (empty when there is none). */
    public static Map<String, String> table(String runtimeBaseDir, String language) {
        if (runtimeBaseDir == null || runtimeBaseDir.isEmpty() || language == null) return NONE;
        String cacheKey = runtimeBaseDir + "|" + language;
        synchronized (CACHE) {
            Map<String, String> cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
        }
        Map<String, String> merged = new HashMap<>();
        // Later files only fill gaps: the exact language wins over its fallbacks.
        for (String tag : chain(language)) {
            File file = new File(new File(runtimeBaseDir, "i18n"), tag + ".json");
            if (!file.isFile() || file.length() > MAX_FILE_BYTES) continue;
            try {
                for (Map.Entry<String, String> entry : read(file, false).entrySet()) merged.putIfAbsent(entry.getKey(), entry.getValue());
            } catch (IOException ignored) {
                // A damaged table only loses its translations; the plugin's own text stays readable.
            }
        }
        Map<String, String> result = merged.isEmpty() ? NONE : Collections.unmodifiableMap(merged);
        synchronized (CACHE) { CACHE.put(cacheKey, result); }
        return result;
    }

    /** zh-Hant falls back to zh-Hans; other languages to their bare tag and then English. */
    static String[] chain(String language) {
        Matcher parts = FILE.matcher(language + ".json");
        String bare = parts.matches() ? parts.group(1) : language;
        if (language.startsWith("zh")) return "zh-Hans".equals(language) ? new String[]{"zh-Hans"} : new String[]{language, "zh-Hans"};
        if ("en".equals(bare)) return bare.equals(language) ? new String[]{"en"} : new String[]{language, "en"};
        return bare.equals(language) ? new String[]{language, "en"} : new String[]{language, bare, "en"};
    }

    static String translate(Map<String, String> table, String text) {
        if (text == null || text.isEmpty()) return text;
        String value = table.get(text);
        return value == null ? text : value;
    }

    private static final Text IDENTITY = text -> text;

    private static Map<String, String> read(File file, boolean strict) throws IOException {
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject object;
        try { object = new JSONObject(json); }
        catch (Exception invalid) { throw new IOException("插件翻译文件不是 JSON 对象：" + file.getName()); }
        if (object.length() > MAX_ENTRIES) throw new IOException("插件翻译条目超过 " + MAX_ENTRIES + " 条：" + file.getName());
        Map<String, String> table = new HashMap<>(Math.max(16, object.length() * 2));
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.opt(key);
            boolean valid = value instanceof String && !key.isEmpty() && key.length() <= MAX_TEXT && ((String) value).length() <= MAX_TEXT
                    && key.indexOf('\0') < 0 && ((String) value).indexOf('\0') < 0 && !((String) value).trim().isEmpty();
            if (!valid) {
                if (strict) throw new IOException("插件翻译条目无效：" + file.getName());
                continue;
            }
            table.put(key, (String) value);
        }
        return table;
    }
}
