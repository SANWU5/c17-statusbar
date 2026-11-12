// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.io.FileNotFoundException;

/** Independent external-app entry. The framework instantiates StatusBarModule directly for SystemUI. */
public final class C17Module extends XposedModule {
    private XposedModuleInterface.ModuleLoadedParam loaded;
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param){loaded=param;}
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param){
        if(!"com.android.systemui".equals(param.getPackageName()))application(param.getPackageName(),param.getDefaultClassLoader());
    }
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param){
        if(!"com.android.systemui".equals(param.getPackageName()))application(param.getPackageName(),param.getClassLoader());
    }
    private void application(String packageName,ClassLoader loader){
        if(ModuleVariant.DABING)return;
        CustomPluginAppRuntime.install(packageName,loaded==null?null:loaded.getProcessName(),loader,new CustomPluginRuntime.Install(){
            @Override public XposedInterface.HookHandle hook(java.lang.reflect.Executable executable,XposedInterface.Hooker callback)throws Throwable{
                C17Module.this.deoptimize(executable);
                return C17Module.this.hook(executable).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(callback);
            }
            @Override public void deoptimize(java.lang.reflect.Executable executable){C17Module.this.deoptimize(executable);}
            @Override public SharedPreferences frameworkPreferences(){return getRemotePreferences(SettingsFrameworkMirror.GROUP);}
            @Override public ParcelFileDescriptor openFrameworkFile(String name)throws FileNotFoundException{return openRemoteFile(name);}
        });
    }
}
