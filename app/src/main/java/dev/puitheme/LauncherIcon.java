// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

/** App entry visibility is PackageManager state, independent of runtime/exported settings. */
public final class LauncherIcon {
    public static final String LAUNCHER_ALIAS="dev.puitheme.MainActivity";
    public static final String SETTINGS_ALIAS="dev.puitheme.SettingsActivity";
    public static final String SETTINGS_CATEGORY="de.robv.android.xposed.category.MODULE_SETTINGS";
    private static final String ACTIVITY="dev.puitheme.ModernMainActivity";
    private LauncherIcon(){}
    public static final class Result {
        public final boolean success,hidden;
        public final String message;
        private Result(boolean success,boolean hidden,String message){this.success=success;this.hidden=hidden;this.message=message;}
    }
    public static Result read(Context context) {
        try {
            requireOwn(context);PackageManager manager=context.getPackageManager();
            ComponentName launcher=new ComponentName(ModuleDiagnostics.PACKAGE_NAME,LAUNCHER_ALIAS);
            ActivityInfo info=manager.getActivityInfo(launcher,PackageManager.MATCH_DISABLED_COMPONENTS);
            if(!ACTIVITY.equals(info.targetActivity))throw new IllegalStateException("Unexpected launcher alias");
            return new Result(true,!enabled(manager,launcher,info),"");
        }catch(RuntimeException | PackageManager.NameNotFoundException unavailable){return new Result(false,false,"无法读取桌面图标状态，请重试");}
    }
    public static boolean isHidden(Context context) {
        Result result=read(context);if(!result.success)throw new IllegalStateException(result.message);return result.hidden;
    }
    /** Hiding is called only after the UI confirmation. Never kills/finishes the current settings page. */
    public static synchronized Result setHidden(Context context,boolean hidden) {
        Result before=read(context);if(!before.success)return before;
        try {
            PackageManager manager=context.getPackageManager();
            if(hidden) {
                ComponentName settings=new ComponentName(ModuleDiagnostics.PACKAGE_NAME,SETTINGS_ALIAS);
                ActivityInfo info=manager.getActivityInfo(settings,PackageManager.MATCH_DISABLED_COMPONENTS);
                if(!info.exported||!ACTIVITY.equals(info.targetActivity)||!enabled(manager,settings,info))
                    return new Result(false,before.hidden,"模块设置入口不可用，未更改桌面图标");
            }
            if(before.hidden==hidden)return new Result(true,hidden,hidden?"桌面图标已隐藏":"桌面图标已显示");
            manager.setComponentEnabledSetting(new ComponentName(ModuleDiagnostics.PACKAGE_NAME,LAUNCHER_ALIAS),
                    hidden?PackageManager.COMPONENT_ENABLED_STATE_DISABLED:PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Result after=read(context);
            if(!after.success||after.hidden!=hidden)return new Result(false,after.success?after.hidden:before.hidden,"桌面图标状态尚未确认，请重试");
            return new Result(true,hidden,hidden?"桌面图标已隐藏，可从 LSPosed 的模块设置恢复":"桌面图标已恢复，启动器可能稍后刷新");
        }catch(RuntimeException | PackageManager.NameNotFoundException unavailable){
            Result after=read(context);return new Result(false,after.success?after.hidden:before.hidden,"无法更改桌面图标，请重试");
        }
    }
    private static boolean enabled(PackageManager manager,ComponentName component,ActivityInfo info) {
        switch(manager.getComponentEnabledSetting(component)) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:return info.enabled;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:return false;
            default:throw new IllegalStateException("Unknown component state");
        }
    }
    private static void requireOwn(Context context) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName()))throw new SecurityException("Own app context required");
    }
}
