// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public final class SettingsProvider extends ContentProvider {
    private static final String METHOD_READ = "read_statusbar_settings";

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public Bundle call(String str, String str2, Bundle bundle) {
        if (ModuleDiagnostics.METHOD_RECORD.equals(str)) {
            enforceAllowedReader();
            return ModuleDiagnostics.record(getContext(), bundle);
        }
        if (!METHOD_READ.equals(str)) {
            return super.call(str, str2, bundle);
        }
        enforceAllowedReader();
        SharedPreferences sharedPreferences = StatusBarSettings.preferences(getContext());
        Bundle bundle2 = new Bundle();
        Map<String, ?> values = sharedPreferences.getAll();
        for (Map.Entry<String, Float> setting : StatusBarSettings.NUMERIC_DEFAULTS.entrySet()) {
            bundle2.putFloat(setting.getKey(), StatusBarSettings.settingNumber(values, setting.getKey(), setting.getValue()));
        }
        for (Map.Entry<String, Integer> color : StatusBarSettings.COLOR_DEFAULTS.entrySet()) {
            bundle2.putInt(color.getKey(), StatusBarSettings.color(values, color.getKey()));
            bundle2.putBoolean(StatusBarSettings.alphaKey(color.getKey()), StatusBarSettings.customAlpha(values, color.getKey()));
        }
        for (String key : StatusBarSettings.STRING_DEFAULTS.keySet())
            bundle2.putString(key, StatusBarSettings.string(values, key));
        for (String key : StatusBarSettings.BOOLEAN_DEFAULTS.keySet())
            bundle2.putBoolean(key, StatusBarSettings.bool(values, key));
        return bundle2;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceAllowedReader();
        if (!"r".equals(mode) || !"/font/current".equals(uri.getPath()))
            throw new FileNotFoundException("Read-only font endpoint");
        File file = new File(getContext().createDeviceProtectedStorageContext().getFilesDir(), "fonts/custom.font");
        if (!file.isFile()) throw new FileNotFoundException("No imported font");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void enforceAllowedReader() {
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return;
        }
        String[] packagesForUid = getContext().getPackageManager().getPackagesForUid(callingUid);
        if (packagesForUid != null) {
            for (String str : packagesForUid) {
                if ("com.android.systemui".equals(str)) {
                    return;
                }
            }
        }
        throw new SecurityException("Status-bar settings are only readable by this app and SystemUI");
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        throw new UnsupportedOperationException("Settings are available through call()");
    }

    @Override // android.content.ContentProvider
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.puitheme.settings";
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri uri, ContentValues contentValues) {
        throw new UnsupportedOperationException("read only");
    }

    @Override // android.content.ContentProvider
    public int delete(Uri uri, String str, String[] strArr) {
        throw new UnsupportedOperationException("read only");
    }

    @Override // android.content.ContentProvider
    public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        throw new UnsupportedOperationException("read only");
    }
}
