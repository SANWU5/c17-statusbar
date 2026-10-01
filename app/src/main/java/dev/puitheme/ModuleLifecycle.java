// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/** The loaded SystemUI code must release its in-memory styles when the app is removed. */
public final class ModuleLifecycle {
    private static volatile boolean removed;
    private static boolean registered;
    private static Runnable release;
    private ModuleLifecycle() { }

    public static synchronized void bind(Context context, Runnable callback) {
        if (context == null || !"com.android.systemui".equals(context.getPackageName())) return;
        release = callback;
        if (registered || removed) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context owner, Intent intent) {
                if (isRemoval(intent)) markRemoved();
            }
        };
        try {
            // Both package-removal actions are system-protected broadcasts.
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter);
            registered = true;
        } catch (RuntimeException unavailable) { /* Provider failure also verifies package presence. */ }
    }

    static boolean isRemoval(Intent intent) {
        return intent != null && intent.getData() != null && isRemoval(intent.getAction(),
                intent.getData().getScheme(), intent.getData().getSchemeSpecificPart(),
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false));
    }

    static boolean isRemoval(String action, String scheme, String packageName, boolean replacing) {
        return (Intent.ACTION_PACKAGE_REMOVED.equals(action) || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action))
                && "package".equals(scheme) && ModuleDiagnostics.PACKAGE_NAME.equals(packageName) && !replacing;
    }

    public static boolean removed() { return removed; }

    /** Only called after failed IPC; ordinary rendering never asks PackageManager. */
    static boolean verifyAfterFailure(Context context) {
        if (removed) return true;
        try { context.getPackageManager().getApplicationInfo(ModuleDiagnostics.PACKAGE_NAME, 0); }
        catch (PackageManager.NameNotFoundException missing) { markRemoved(); }
        catch (RuntimeException unavailable) { /* Temporary boot/user/provider failures retain settings. */ }
        return removed;
    }

    static void markRemoved() {
        Runnable callback;
        synchronized (ModuleLifecycle.class) {
            if (removed) return;
            removed = true; callback = release; release = null;
        }
        SettingsSnapshot.forgetApplied();
        ModuleDiagnostics.release();
        if (callback != null) callback.run();
    }

    public static Bundle nativeSettings() {
        Bundle result = SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap());
        result.putBoolean(StatusBarSettings.SAFE_MODE, true);
        return result;
    }
}
