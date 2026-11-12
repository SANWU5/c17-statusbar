// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * App-only record that the welcome flow (language, introduction, statements) was completed. Never exported.
 * From 2.1 on (App292) the record also holds the app version code that completed it: every update, that is every
 * other version code, shows the welcome flow once more. The typed free-notice phrase is not asked again; it has its
 * own record.
 */
public final class WelcomeState {
    /** Raise when the statements change materially; users then read and confirm them once more. */
    public static final int VERSION = 1;
    private static final String PREFERENCES = "app_welcome", ACCEPTED = "accepted_version", APP_VERSION = "accepted_app_version";
    private WelcomeState() { }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** This package's version code, read at run time (desktop checks have no generated BuildConfig); -1 if unreadable. */
    @SuppressWarnings("deprecation")
    private static int appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? (int) info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return -1;
        }
    }

    public static boolean completed(Context context) {
        Map<String, Object> values = PreferenceWrites.values(preferences(context));
        return completed(values.get(ACCEPTED), values.get(APP_VERSION), appVersion(context));
    }

    /**
     * Read and confirmed for these statements by this very version; a record from another version code, or from before
     * App292 without one, shows it again. An unreadable current version keeps the rule from before App292.
     */
    static boolean completed(Object accepted, Object appVersion, int currentAppVersion) {
        if (!(accepted instanceof Integer) || (Integer) accepted < VERSION) return false;
        return currentAppVersion < 0 || appVersion instanceof Integer && (Integer) appVersion == currentAppVersion;
    }

    public static boolean complete(Context context) {
        SharedPreferences raw = preferences(context);
        int version = appVersion(context);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(ACCEPTED, VERSION);
        SharedPreferences.Editor editor = raw.edit().putInt(ACCEPTED, VERSION);
        if (version >= 0) {
            changes.put(APP_VERSION, version);
            editor.putInt(APP_VERSION, version);
        }
        return PreferenceWrites.commit(raw, editor, changes, false);
    }
}
