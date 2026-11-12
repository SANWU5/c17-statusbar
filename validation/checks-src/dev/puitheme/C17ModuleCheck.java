// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;
import java.util.Objects;

/** Framework-owned entry points must not call wrapper attachment methods missing in protected API102 runtimes. */
public final class C17ModuleCheck {
    private static int checks;
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(why+": "+expected+" != "+actual);}
    private static final class LightLoader extends ClassLoader {
        int systemUiLoads;
        LightLoader(){super(C17ModuleCheck.class.getClassLoader());}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(name.equals("dev.puitheme.StatusBarModule"))systemUiLoads++;
            if(name.startsWith("dev.puitheme.")){
                Class<?> type=findLoadedClass(name);if(type==null)try(java.io.InputStream input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();type=defineClass(name,bytes,0,bytes.length);
                }catch(java.io.IOException unavailable){throw new ClassNotFoundException(name,unavailable);}if(resolve)resolveClass(type);return type;
            }
            return super.loadClass(name,resolve);
        }
    }
    @SuppressWarnings("unchecked") private static <T>T param(Class<T> type,String packageName){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if(method.getName().equals("getPackageName")||method.getName().equals("getProcessName"))return packageName;
            if(method.getReturnType()==ClassLoader.class)return C17ModuleCheck.class.getClassLoader();
            if(method.getReturnType()==Boolean.TYPE)return false;if(method.getReturnType()==Integer.TYPE)return 0;return null;
        });
    }
    public static void main(String[] args)throws Exception{
        java.util.List<String> entries=java.nio.file.Files.readAllLines(java.nio.file.Paths.get("app/src/main/resources/META-INF/xposed/java_init.list"));
        equal(java.util.Arrays.asList("dev.puitheme.StatusBarModule","dev.puitheme.C17Module"),entries,"framework directly owns both entry instances");
        LightLoader loader=new LightLoader();XposedModule entry=(XposedModule)loader.loadClass("dev.puitheme.C17Module").getDeclaredConstructor().newInstance();
        equal(0,loader.systemUiLoads,"external entry itself has no dependency on SystemUI implementation");
        entry.onModuleLoaded(param(XposedModuleInterface.ModuleLoadedParam.class,"com.example.launcher:overlay"));
        entry.onPackageLoaded(param(XposedModuleInterface.PackageLoadedParam.class,"com.example.launcher"));
        entry.onPackageReady(param(XposedModuleInterface.PackageReadyParam.class,"com.example.sidebar"));
        entry.onPackageLoaded(param(XposedModuleInterface.PackageLoadedParam.class,"android"));
        equal(0,loader.systemUiLoads,"application and unsupported server routes do not load heavy SystemUI state");
        Class<?> appRuntime=loader.loadClass("dev.puitheme.CustomPluginAppRuntime");java.lang.reflect.Field routes=appRuntime.getDeclaredField("processes");routes.setAccessible(true);
        Object bootstrap=((java.util.Map<?,?>)routes.get(null)).get("com.example.launcher");
        java.lang.reflect.Field process=bootstrap.getClass().getDeclaredField("processName");process.setAccessible(true);
        equal("com.example.launcher:overlay",process.get(bootstrap),"framework child-process identity reaches the isolated runtime storage router");
        entry.onModuleLoaded(param(XposedModuleInterface.ModuleLoadedParam.class,"com.android.systemui"));
        entry.onPackageLoaded(param(XposedModuleInterface.PackageLoadedParam.class,"com.android.systemui"));
        entry.onPackageReady(param(XposedModuleInterface.PackageReadyParam.class,"com.android.systemui"));
        equal(0,loader.systemUiLoads,"SystemUI callback never creates or manually attaches a delegate");
        equal(false,((java.util.Map<?,?>)routes.get(null)).containsKey("com.android.systemui"),"external entry cannot start a second SystemUI plugin runtime");
        // The old entry is instantiated by the framework, never by C17Module. Its original
        // initialization remains valid even if framework identity is temporarily unavailable.
        StatusBarModule primary=new StatusBarModule();primary.onModuleLoaded(param(XposedModuleInterface.ModuleLoadedParam.class,"com.android.systemui"));
        java.lang.reflect.Field instance=StatusBarModule.class.getDeclaredField("instance");instance.setAccessible(true);
        equal(primary,instance.get(null),"framework-owned original onModuleLoaded initializes SystemUI identity");
        equal(XposedModule.class,StatusBarModule.class.getSuperclass(),"SystemUI keeps its original direct framework entry superclass");
        for(Class<?> type:new Class<?>[]{C17Module.class,StatusBarModule.class}){
            try(java.io.InputStream input=type.getResourceAsStream(type.getSimpleName()+".class")){
                String bytecode=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
                equal(false,bytecode.contains("attachFramework"),"compiled "+type.getSimpleName()+" has no reference to the missing API102 attachment method");
            }
        }
        System.out.println("C17ModuleCheck "+checks+" checks passed");
    }
}
