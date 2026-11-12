// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Interface text in the chosen app language.
 *
 * The source text is Simplified Chinese and stays in the code. {@code assets/i18n/<language>.json} maps a whole source
 * string to its translation (tools/i18n builds and checks these tables). Text is looked up only where it is shown,
 * never where the program compares, stores or exports it, so the logic keeps working on the source text. A missing
 * entry falls back to English for languages that do not read Chinese, then to the source.
 *
 * Without {@link #use} (desktop checks, other processes) every lookup returns the source unchanged.
 */
public final class C17Text {
    private static final Map<String, String> NONE = Collections.emptyMap();
    private static volatile Map<String, String> table = NONE, english = NONE;
    private static volatile String language = C17Language.DEFAULT_TAG;
    private C17Text() { }

    /** Loads the tables for the language of [context]'s configuration; cheap when it did not change. */
    public static void use(Context context) {
        if (context == null) return;
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        String tag = C17Language.tagFor(locale);
        // A language that is not offered keeps the source text, as the Android resources do.
        if (tag == null || tag.isEmpty()) tag = C17Language.DEFAULT_TAG;
        synchronized (C17Text.class) {
            if (tag.equals(language) && (table != NONE || C17Language.DEFAULT_TAG.equals(tag))) return;
            AssetManager assets = context.getAssets();
            Map<String, String> next = C17Language.DEFAULT_TAG.equals(tag) ? NONE : load(assets, tag);
            Map<String, String> fallback = tag.startsWith("zh") || "en".equals(tag) ? NONE : load(assets, "en");
            // A language whose table is not there yet reads English, like its missing entries do.
            if (next == NONE && fallback != NONE) next = fallback;
            table = next;
            english = fallback;
            language = tag;
        }
    }

    /** The shown form of [source]; null and empty text pass through. */
    public static String tr(String source) {
        if (source == null || source.isEmpty()) return source;
        Map<String, String> current = table;
        if (current == NONE) return source;
        String value = current.get(source);
        if (value == null) value = english.get(source);
        return value == null ? source : value;
    }

    /**
     * {@link String#format} with a translated template. Text arguments are looked up too: a value the program inserts
     * is often itself interface text ("导入", a region name); numbers and names that are no table entry stay as they are.
     */
    public static String format(String template, Object... args) {
        Object[] shown = args == null ? new Object[0] : args.clone();
        for (int i = 0; i < shown.length; i++) if (shown[i] instanceof String) shown[i] = tr((String) shown[i]);
        return String.format(Locale.getDefault(), tr(template), shown);
    }

    /** The table language, an offered tag such as "en" or "zh-Hant"; "zh-Hans" shows the source. */
    public static String language() { return language; }

    /** True when the shown text is the source itself. */
    public static boolean source() { return table == NONE; }

    private static Map<String, String> load(AssetManager assets, String tag) {
        try (InputStream input = assets.open("i18n/" + tag + ".json");
             JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Map<String, String> result = new HashMap<>(8192);
            reader.beginObject();
            while (reader.hasNext()) result.put(reader.nextName(), reader.nextString());
            reader.endObject();
            return Collections.unmodifiableMap(result);
        } catch (Exception missing) {
            // No table for this language yet: use() falls back to English, or to the source text.
            return NONE;
        }
    }
}
