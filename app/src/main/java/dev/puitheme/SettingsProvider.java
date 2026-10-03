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
import java.util.Collections;

/* JADX INFO: loaded from: classes.dex */
public final class SettingsProvider extends ContentProvider {
    private static final String METHOD_READ = "read_statusbar_settings";
    private Bundle lastGood;

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public Bundle call(String str, String str2, Bundle bundle) {
        if (PanelMode.METHOD.equals(str)) {
            enforceAllowedReader(); return PanelMode.query(getContext());
        }
        if (ModuleRuntimeStatus.METHOD_BEGIN.equals(str)) return ModuleRuntimeStatus.beginProbe(getContext(), bundle);
        if (ModuleRuntimeStatus.METHOD_QUERY.equals(str)) return ModuleRuntimeStatus.queryStatus(getContext(), bundle);
        if (ModuleRuntimeStatus.METHOD_REPORT.equals(str)) return ModuleRuntimeStatus.recordSystemUiReport(getContext(), bundle);
        if ("read_framework_settings_status".equals(str)) {
            enforceAllowedReader();return SettingsFrameworkMirror.status();
        }
        if (ModuleDiagnostics.METHOD_RECORD.equals(str)) {
            enforceAllowedReader();
            return ModuleDiagnostics.record(getContext(), bundle);
        }
        if (!METHOD_READ.equals(str)) {
            return super.call(str, str2, bundle);
        }
        enforceAllowedReader();
        Boolean requestedSafety = null;
        try {
            SharedPreferences preferences=StatusBarSettings.preferences(getContext());
            if(!SettingsSnapshot.notificationAllowed(preferences))
                throw new IllegalStateException("Settings storage has an unconfirmed failed write");
            Map<String,?> values=SettingsSnapshot.readableValues(preferences);
            Object safety=values.get(StatusBarSettings.SAFE_MODE);
            if(safety instanceof Boolean)requestedSafety=(Boolean)safety;
            if(values.isEmpty())synchronized(this) {
                if(lastGood!=null)return new Bundle(lastGood);
            }
            Bundle snapshot=SettingsSnapshot.fromPreferences(values);
            if(snapshot!=null) {
                synchronized(this) { lastGood=new Bundle(snapshot); }
                SettingsSnapshot.scheduleSave(getContext(),preferences,null);
                return snapshot;
            }
            ModuleDiagnostics.info("settings","Malformed stored settings; retaining last complete snapshot");
        } catch(RuntimeException unavailable) {
            ModuleDiagnostics.error("settings","Settings provider read failed; retaining last complete snapshot",unavailable);
        }
        synchronized(this) {
            if(lastGood==null)lastGood=SettingsSnapshot.durableSnapshot(getContext());
            // A damaged styling preference must never prevent the emergency runtime switch.
            // This modifies only the delivered fallback, preserving the saved user configuration.
            if(lastGood==null&&Boolean.TRUE.equals(requestedSafety))
                lastGood=SettingsSnapshot.fromPreferences(Collections.emptyMap());
            if(lastGood!=null&&requestedSafety!=null)
                lastGood.putBoolean(StatusBarSettings.SAFE_MODE,requestedSafety);
            return lastGood==null?null:new Bundle(lastGood);
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceAllowedReader();
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only endpoint");
        File file;
        if ("/font/current".equals(uri.getPath())) file = new File(getContext().createDeviceProtectedStorageContext().getFilesDir(), "fonts/custom.font");
        else if ("/notification-icon/current".equals(uri.getPath())) file = NotificationIconRepository.file(getContext());
        else if (uri.getPath() != null && uri.getPath().startsWith("/shade-wallpaper/")) {
            try { file = ShadeWallpaperRepository.providerFile(getContext(), uri); }
            catch (java.io.IOException invalid) { throw new FileNotFoundException(invalid.getMessage()); }
        }
        else if (uri.getPath() != null && uri.getPath().startsWith("/icon-pack/")) {
            try { file = IconPackRepository.providerFile(getContext(), uri); }
            catch (java.io.IOException invalid) { throw new java.io.FileNotFoundException(invalid.getMessage()); }
        }
        else throw new FileNotFoundException("Unknown private asset");
        if (!file.isFile()) throw new FileNotFoundException("No imported asset");
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
