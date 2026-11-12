// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Collections;
import java.util.Locale;

/**
 * Per-app language. Android 13+ uses the system per-app locale (also listed in the phone's own app language
 * settings through res/xml/locales_config.xml); older versions apply the stored tag to each activity context.
 * The welcome flow uses string resources; the rest of the interface is translated where it is shown, by
 * {@link C17Text} and the tables in assets/i18n (App291).
 */
public final class C17Language {
    public static final class Option {
        public final String tag, nativeName;
        Option(String tag, String nativeName) { this.tag = tag; this.nativeName = nativeName; }
    }
    /** The author's default, used before any choice when the phone's own language is not offered. */
    public static final String DEFAULT_TAG = "zh-Hans";
    /** Tag "" follows the system. Native names are never translated, so everyone can find their own language. */
    public static final Option[] OPTIONS = {
            new Option("", null),
            new Option("zh-Hans", "简体中文"), new Option("zh-Hant", "繁體中文"), new Option("en", "English"),
            new Option("ja", "日本語"), new Option("ko", "한국어"), new Option("es", "Español"),
            new Option("pt", "Português"), new Option("fr", "Français"), new Option("de", "Deutsch"),
            new Option("it", "Italiano"), new Option("nl", "Nederlands"), new Option("pl", "Polski"),
            new Option("ru", "Русский"), new Option("uk", "Українська"), new Option("tr", "Türkçe"),
            new Option("el", "Ελληνικά"), new Option("cs", "Čeština"), new Option("ro", "Română"),
            new Option("sv", "Svenska"), new Option("ar", "العربية"), new Option("fa", "فارسی"),
            new Option("he", "עברית"), new Option("ur", "اردو"), new Option("hi", "हिन्दी"),
            new Option("bn", "বাংলা"), new Option("th", "ไทย"), new Option("vi", "Tiếng Việt"),
            new Option("id", "Bahasa Indonesia"), new Option("ms", "Bahasa Melayu"), new Option("sw", "Kiswahili"),
    };
    private static final String PREFERENCES = "app_language", TAG = "tag";
    private C17Language() { }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String storedTag(Context context) {
        Object value = PreferenceWrites.values(preferences(context)).get(TAG);
        return value instanceof String ? (String) value : null;
    }

    /** True once the user picked a language in the app or in Android's app settings. */
    public static boolean chosen(Context context) {
        LocaleList locales = applicationLocales(context);
        return storedTag(context) != null || locales != null && !locales.isEmpty();
    }

    /** An empty tag follows the system. Android 13+ settings are the source of truth after a choice. */
    public static String current(Context context) {
        String stored = storedTag(context);
        LocaleList locales = applicationLocales(context);
        if (locales != null) {
            if (!locales.isEmpty()) {
                String tag = tagFor(locales.get(0));
                return tag == null ? "" : tag;
            }
            return stored == null ? firstLaunchTag() : "";
        }
        return stored == null ? firstLaunchTag() : known(stored) ? stored : "";
    }

    /** The offered language closest to [locale], or null when none is offered. */
    static String tagFor(Locale locale) {
        if (locale == null) return null;
        String tag = locale.toLanguageTag();
        if (known(tag)) return tag;
        if ("zh".equals(locale.getLanguage())) {
            String region = locale.getCountry();
            return "Hant".equals(locale.getScript()) || "TW".equals(region) || "HK".equals(region)
                    || "MO".equals(region) ? "zh-Hant" : DEFAULT_TAG;
        }
        // toLanguageTag maps the legacy codes ("iw", "in") to the offered "he" and "id".
        String language = Locale.forLanguageTag(locale.getLanguage()).toLanguageTag();
        return known(language) ? language : null;
    }

    /**
     * Before any choice: the phone's own language when it is offered, so a reader who does not know Chinese starts in a
     * language they can read (App285); otherwise Simplified Chinese as the author asked.
     */
    public static String firstLaunchTag() {
        String tag = tagFor(Resources.getSystem().getConfiguration().getLocales().get(0));
        return tag == null || tag.isEmpty() ? DEFAULT_TAG : tag;
    }

    private static LocaleList applicationLocales(Context context) {
        if (Build.VERSION.SDK_INT < 33) return null;
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        return manager == null ? null : manager.getApplicationLocales();
    }

    public static boolean known(String tag) {
        for (Option option : OPTIONS) if (option.tag.equals(tag)) return true;
        return false;
    }

    /** Stores and applies a choice. Android 13+ recreates the activity itself; older versions recreate it here. */
    public static boolean apply(Activity activity, String tag) {
        String next = known(tag) ? tag : "";
        SharedPreferences raw = preferences(activity);
        String previous = storedTag(activity);
        boolean changed = !next.equals(previous);
        if (changed && !PreferenceWrites.commit(raw, raw.edit().putString(TAG, next),
                Collections.<String, Object>singletonMap(TAG, next), false)) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = activity.getSystemService(LocaleManager.class);
            if (manager != null) {
                LocaleList wanted = next.isEmpty() ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(next);
                if (!wanted.equals(manager.getApplicationLocales())) {
                    manager.setApplicationLocales(wanted);
                    return true;
                }
                if (previous == null && next.isEmpty()) activity.recreate();
                return true;
            }
        }
        if (changed) activity.recreate();
        return true;
    }

    /** Applies the first-launch default, or the local choice on versions without per-app locales. */
    public static Context wrap(Context base) {
        if (base == null) return null;
        String tag = storedTag(base);
        LocaleList locales = applicationLocales(base);
        if (locales != null && (!locales.isEmpty() || tag != null)) return base;
        if (tag == null) tag = firstLaunchTag();
        if (tag.isEmpty() || !known(tag)) return base;
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(Locale.forLanguageTag(tag)));
        return base.createConfigurationContext(configuration);
    }
}
