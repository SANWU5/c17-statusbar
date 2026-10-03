// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import java.io.File;
import java.io.IOException;

/** Explicit app-private maintenance. The caller blocks edits/imports until this worker returns. */
public final class MaintenanceReset {
    private MaintenanceReset() { }

    public static final class Result {
        public final boolean success,frameworkSynced;
        public final int removedAssets;
        public final String message;
        Result(boolean success,boolean synced,int removed,String message) {
            this.success=success;frameworkSynced=synced;removedAssets=removed;this.message=message;
        }
    }

    /** Run on a worker after closing prepared font/image imports and disabling new submissions. */
    public static synchronized Result reset(Context context) {
        requireOwn(context);
        SettingsSnapshot.ResetResult settings=SettingsSnapshot.resetForMaintenance(context);
        if(!settings.primaryReset)return new Result(false,settings.frameworkSynced,0,settings.message);
        int removed=0;
        try {
            File files=context.createDeviceProtectedStorageContext().getFilesDir();
            removed+=clearImportedAssets(files,"fonts","custom.font","(?:import|previous)-[A-Za-z0-9_-]+\\.font");
            removed+=clearImportedAssets(files,"notification-icons","custom.png","(?:pending|previous)-[A-Za-z0-9_-]+\\.png");
        } catch(IOException unavailable) {
            return new Result(false,settings.frameworkSynced,removed,"配置已恢复默认，但导入资源未全部删除，请重试");
        }
        return new Result(settings.localSuccess(),settings.frameworkSynced,removed,settings.message);
    }

    public static void clearLogs(Context context) throws IOException {
        requireOwn(context);ModuleDiagnostics.clear(context);
    }

    /** No recursion, arbitrary names or links outside the two exact private asset directories. */
    static int clearImportedAssets(File privateFiles,String name,String current,String temporaryPattern) throws IOException {
        File root=privateFiles.getCanonicalFile(),directory=new File(root,name);
        if(!directory.exists())return 0;
        if(!directory.getCanonicalFile().equals(directory.getAbsoluteFile())
                ||!directory.getCanonicalFile().getParentFile().equals(root))throw new IOException("Unexpected private asset directory");
        File[] files=directory.listFiles();
        if(files==null)throw new IOException("Cannot read private asset directory");
        int removed=0;
        for(File file:files) {
            if(!file.getName().equals(current)&&!file.getName().matches(temporaryPattern))continue;
            if(!file.getCanonicalFile().equals(file.getAbsoluteFile())
                    ||!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())
                    ||!file.isFile())throw new IOException("Unexpected imported asset");
            if(!file.delete())throw new IOException("Cannot delete imported asset");
            removed++;
        }
        return removed;
    }
    private static void requireOwn(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))
            throw new SecurityException("Own application context required");
    }
}
