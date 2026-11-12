// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.api.XposedInterface;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs the unmodified shipped ZIP through release-host import, assets, Rhino, Java bridge and lifecycle.
 * Usage: LiquidGlassPluginPackageCheck <plugin.zip> <same-build runtime.jar> [split.zip] [unified.zip] [beta011.zip].
 * Android DEX bytes are transferred and hash-checked; the desktop executes the same product Java bytecode
 * from runtime.jar through an isolated package loader. This does not claim Android DEX or GPU validation.
 */
public final class LiquidGlassPluginPackageCheck {
    private static final String ID="dev.c17.liquid-glass",SYS="com.android.systemui",HOME="com.android.launcher",SIDE="com.coloros.smartsidebar";
    private static int checks;
    private static void equal(Object wanted,Object actual,String why){checks++;if(!Objects.equals(wanted,actual))throw new AssertionError(why+": "+wanted+" != "+actual);}
    private static final class Packages extends PackageManager {
        @Override public ActivityInfo getActivityInfo(android.content.ComponentName name,int flags)throws NameNotFoundException{throw new NameNotFoundException();}
        @Override public int getComponentEnabledSetting(android.content.ComponentName name){return 0;}
        @Override public void setComponentEnabledSetting(android.content.ComponentName name,int state,int flags){}
        @Override public String[] getPackagesForUid(int uid){return new String[]{uid==1000?SYS:uid==12001?HOME:uid==12002?SIDE:ModuleDiagnostics.PACKAGE_NAME};}
    }
    private static final class Owner extends Context {
        final String name;final ClassLoader loader;final Packages packages=new Packages();
        final ActivationGuardPreferencesCheck.MemoryPreferences preferences=new ActivationGuardPreferencesCheck.MemoryPreferences();
        ContentResolver resolver=new ContentResolver();
        Owner(File files,String name,ClassLoader loader){super(files);this.name=name;this.loader=loader;}
        @Override public String getPackageName(){return name;}
        @Override public ClassLoader getClassLoader(){return loader;}
        @Override public PackageManager getPackageManager(){return packages;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}
        @Override public boolean deleteSharedPreferences(String name){return true;}
        @Override public ContentResolver getContentResolver(){return resolver;}
    }
    private static final class Queue extends Handler {
        final ConcurrentLinkedQueue<Runnable> ready=new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Runnable> later=new ConcurrentLinkedQueue<>();
        Queue(){super(Looper.getMainLooper());}
        @Override public boolean post(Runnable task){ready.add(task);return true;}
        @Override public boolean postDelayed(Runnable task,long delay){later.add(task);return true;}
        @Override public void removeCallbacks(Runnable task){ready.remove(task);later.remove(task);}
        void drain(){Runnable task;int budget=10000;while((task=ready.poll())!=null){if(--budget==0)throw new AssertionError("Unbounded work");task.run();}Handler.drainForCheck();}
        void until(BooleanSupplier done,CustomPluginRuntime runtime)throws Exception{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            do{drain();if(done.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<end);
            throw new AssertionError("Runtime did not settle: "+runtime.status());
        }
    }
    private static final class Transport extends ContentResolver {
        final SettingsProvider provider;final int uid;int reads;
        Transport(SettingsProvider provider,int uid){this.provider=provider;this.uid=uid;}
        @Override public Bundle call(Uri uri,String method,String target,Bundle extras){Binder.setCallingIdentityForCheck(uid,uid+70);return provider.call(method,target,extras);}
        @Override public InputStream openInputStream(Uri uri)throws java.io.FileNotFoundException{
            Binder.setCallingIdentityForCheck(uid,uid+70);reads++;return new ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri,"r"));
        }
    }
    private static final class Installer implements CustomPluginRuntime.Install {
        final Map<Executable,List<XposedInterface.Hooker>> hooks=new HashMap<>();int added,removed,originalCalls,interceptCalls;
        @Override public XposedInterface.HookHandle hook(Executable target,XposedInterface.Hooker hooker){
            List<XposedInterface.Hooker> callbacks=hooks.computeIfAbsent(target,key->new ArrayList<>());
            if(callbacks.contains(hooker))throw new AssertionError("Duplicate callback "+target);
            callbacks.add(hooker);added++;
            return (XposedInterface.HookHandle)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},(owner,method,args)->{
                if("unhook".equals(method.getName())){if(!callbacks.remove(hooker))throw new AssertionError("Double removal");if(callbacks.isEmpty())hooks.remove(target);removed++;return null;}
                if("getExecutable".equals(method.getName()))return target;throw new AssertionError(method);
            });
        }
        Object draw()throws Throwable{
            Method draw=com.oplus.posteffect.drawable.BaseDrawable.class.getDeclaredMethod("draw",android.graphics.Canvas.class);
            Object receiver=new com.oplus.posteffect.drawable.BaseDrawable();Object[] args={new android.graphics.Canvas()};
            return invoke(draw,receiver,args);
        }
        Object invoke(Method draw,Object receiver,Object[] args)throws Throwable{
            List<XposedInterface.Hooker> callbacks=hooks.get(draw);if(callbacks==null)throw new AssertionError("Captured native entry was not hooked");
            return dispatch(draw,receiver,args,new ArrayList<>(callbacks),0);
        }
        Object dispatch(Method draw,Object receiver,Object[] args,List<XposedInterface.Hooker> callbacks,int index)throws Throwable{
            if(index==callbacks.size()){originalCalls++;return null;}
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(owner,method,input)->{
                switch(method.getName()){
                    case "getExecutable":return draw;
                    case "getThisObject":return receiver;
                    case "getArgs":return Arrays.asList(args);
                    case "getArg":return args[(Integer)input[0]];
                    case "proceed":return dispatch(draw,receiver,input==null||input.length==0?args:(Object[])input[0],callbacks,index+1);
                    case "proceedWith":return dispatch(draw,input[0],input.length==1?args:(Object[])input[1],callbacks,index+1);
                    default:throw new AssertionError(method);
                }
            });
            interceptCalls++;return callbacks.get(index).intercept(chain);
        }
    }
    private static final class Process implements AutoCloseable {
        final String name;final URLClassLoader loader;final Owner owner;final Queue main=new Queue();final Installer installer=new Installer();
        final Transport transport;final CustomPluginRuntime runtime;final CustomPluginAppRuntime.Session session;final CustomPluginRepository repository;
        Process(String name,int uid,File jar,SettingsProvider provider,CustomPluginRepository repository)throws Exception{
            this.name=name;this.repository=repository;
            loader=new URLClassLoader(new java.net.URL[]{jar.toURI().toURL()},LiquidGlassPluginPackageCheck.class.getClassLoader());
            owner=new Owner(Files.createTempDirectory("c17-glass-"+uid+"-").toFile(),name,loader);
            transport=new Transport(provider,uid);owner.resolver=transport;
            if(SYS.equals(name)){session=null;runtime=new CustomPluginRuntime(main);runtime.configure(owner,installer,null);}
            else{session=new CustomPluginAppRuntime.Session(owner,main,installer);runtime=session.runtime;}
            runtime.rememberLoader(loader);
            if(session==null){refresh();runtime.setSafeMode(false);}else session.start();
        }
        void refresh()throws Exception{if(session==null)runtime.accept(repository.compiledPlan(name));else transport.dispatchSettingsChange();}
        int loaded(){Bundle report=new Bundle();runtime.health(report);return report.getInt("custom_plugin_runtime_loaded");}
        String status(){JSONArray statuses=runtime.applicationStatus();return statuses.length()==0?"":statuses.optJSONObject(0).optJSONObject("status").optString("glass_status");}
        String productStatus()throws Exception{return (String)Class.forName("dev.c17.plugins.liquidglass.Entry",false,loader).getMethod("status").invoke(null);}
        void running()throws Exception{main.until(()->loaded()==1&&!status().isEmpty(),runtime);}
        @Override public void close()throws Exception{if(session!=null)session.stop();else runtime.stop();main.drain();loader.close();}
    }
    public static void main(String[] args)throws Throwable{
        if(args.length<2||args.length>5)throw new IllegalArgumentException("Provide shipped ZIP, same-build runtime.jar and optional split/unified/beta011 previous ZIPs");
        File zip=new File(args[0]).getCanonicalFile(),jar=new File(args[1]).getCanonicalFile();
        equal(true,zip.isFile()&&jar.isFile(),"product artifacts exist");
        try(ZipFile contents=new ZipFile(zip);ZipFile java=new ZipFile(jar)){
            equal(true,contents.getEntry("classes.dex")!=null,"package includes Android bytecode");
            equal(true,java.getEntry("dev/c17/plugins/liquidglass/Entry.class")!=null,"desktop uses product Entry bytecode");
            equal(false,java.stream().anyMatch(entry->entry.getName().startsWith("dev/puitheme/")||entry.getName().startsWith("io/github/libxposed/")),"plugin does not bundle host or framework implementation");
        }
        android.os.Process.uidForCheck=20000;
        File files=Files.createTempDirectory("c17-glass-import-").toFile();Owner host=new Owner(files,ModuleDiagnostics.PACKAGE_NAME,LiquidGlassPluginPackageCheck.class.getClassLoader());
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(host,null);
        CustomPluginRepository repository=new CustomPluginRepository(new File(files,"custom_plugins"));
        CustomPluginRepository.Plugin installed;JSONObject descriptor;
        try(InputStream input=Files.newInputStream(zip.toPath());CustomPluginRepository.Preview preview=repository.preview(input,zip.getName())){
            descriptor=new JSONObject(preview.descriptor.toString());
            equal(ID,descriptor.getString("id"),"real ZIP descriptor imports");equal(251,descriptor.getInt("minimumHost"),"state material interoperability host minimum enforced");
            equal(Arrays.asList(SYS,HOME,SIDE),list(descriptor.getJSONArray("scopes")),"descriptor declares exact three packages");
            equal(true,CustomPluginSupport.requires(descriptor,"scope.apps")&&CustomPluginSupport.requires(descriptor,"ui.collapsible-groups"),"multi-app and collapsed groups survive compilation");
            installed=repository.install(preview,false);
        }
        equal(false,installed.enabled,"import alone never starts effects");
        equal(true,installed.state.getBoolean("enabled"),"independent master default is preserved");
        JSONObject reference=referenceValues(descriptor);java.util.Iterator<String> defaults=reference.keys();
        while(defaults.hasNext()) {
            String key=defaults.next();
            equal(reference.get(key),installed.state.get(key),"fresh import stores the editable iOS 27 reference explicitly: "+key);
        }
        equal(true,installed.state.getBoolean("area_search"),"desktop search capsule is independently selected");
        equal(true,installed.state.getBoolean("area_clear_all"),"notification clear button selected");
        equal(false,installed.state.has("qs_background")||installed.state.has("shade_background"),"split panel settings retired");
        equal(true,installed.state.getBoolean("panel_background"),"existing fresh-import panel default remains enabled");
        JSONArray controls=descriptor.getJSONArray("ui");
        for(int i=0;i<controls.length();i++) {
            JSONObject control=controls.getJSONObject(i);if(!CustomPluginSchema.persistentControl(control))continue;
            String key=control.getString("key");
            equal(CustomPluginSchema.defaultValue(control),installed.state.get(key),"runtime receives explicit UI default, not sparse legacy fallback: "+key);
        }
        equal(0,repository.compiledPlan(HOME).getJSONArray("runtimes").length(),"disabled import is absent from launcher plan");
        repository.setEnabled(ID,true);
        try(Process system=new Process(SYS,1000,jar,provider,repository);Process home=new Process(HOME,12001,jar,provider,repository);Process side=new Process(SIDE,12002,jar,provider,repository)){
            List<Process> processes=Arrays.asList(system,home,side);
            for(Process process:processes){
                process.running();equal(true,process.status().contains(process.name),"runtime.js status identifies "+process.name);
                equal(1,process.owner.componentCallbacksForCheck.size(),"one configuration observer for "+process.name);
                equal(true,process.transport.reads>0,"real ZIP assets crossed provider for "+process.name);
                equal(true,process.installer.hooks.size()>0,"Java bridge installs a captured optional native hook for "+process.name);
                equal(true,process.status().contains("已绘制区域：[]"),"missing real draw/material is not claimed as coverage for "+process.name);
                equal(0,process.main.later.size(),"settled process has no timer polling for "+process.name);
                process.installer.draw();equal(1,process.installer.originalCalls,"native draw proceeds exactly once for "+process.name);
            }
            Method sharedDraw=com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable.class.getDeclaredMethod("draw",android.graphics.Canvas.class);
            equal(2,system.installer.hooks.get(sharedDraw).size(),"independent mask and material callbacks share the native entry");
            int nativeBefore=system.installer.originalCalls,callbacksBefore=system.installer.interceptCalls;
            system.installer.invoke(sharedDraw,new com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable(new com.oplusos.systemui.common.blurability.ViewBlurProxy()),new Object[]{new android.graphics.Canvas()});
            equal(nativeBefore+1,system.installer.originalCalls,"shared callbacks proceed to native exactly once");
            equal(callbacksBefore+2,system.installer.interceptCalls,"both callbacks execute through the real host bridge");
            equal(false,system.loader.loadClass("dev.c17.plugins.liquidglass.Entry")==home.loader.loadClass("dev.c17.plugins.liquidglass.Entry"),"each app owns an independent product singleton");
            equal(false,home.loader.loadClass("dev.c17.plugins.liquidglass.Entry")==side.loader.loadClass("dev.c17.plugins.liquidglass.Entry"),"sidebar singleton is independent of launcher");
            int[] originalHooks=processes.stream().mapToInt(p->p.installer.added).toArray();
            repository.updateState(ID,"enabled",false);repository.updateState(ID,"intensity",73);
            for(Process process:processes)process.refresh();
            for(int i=0;i<processes.size();i++){
                Process process=processes.get(i);process.main.until(()->process.status().contains("已关闭"),process.runtime);
                equal(1,process.loaded(),"master off preserves runtime for immediate restore in "+process.name);
                equal(originalHooks[i],process.installer.added,"state changes do not reinstall hooks in "+process.name);
                equal(1,process.owner.componentRegistrationsForCheck,"state changes reuse configuration observer in "+process.name);
            }
            repository.updateState(ID,"enabled",true);for(Process process:processes)process.refresh();
            for(Process process:processes){
                process.main.until(()->process.status().contains("已启用"),process.runtime);
                process.owner.componentCallbacksForCheck.get(0).onConfigurationChanged(new android.content.res.Configuration());process.main.drain();
                equal(1,process.owner.componentCallbacksForCheck.size(),"theme change does not duplicate observer in "+process.name);
            }
            // Actual runtime.js status from both foreign UIDs reaches the management form without overwriting SystemUI.
            String generation=new File(installed.runtimeBaseDir).getName();
            home.main.until(()->combined(descriptor,generation).contains(HOME),home.runtime);side.main.until(()->combined(descriptor,generation).contains(SIDE),side.runtime);
            equal(true,combined(descriptor,generation).contains("system-ui-existing"),"foreign statuses retain original SystemUI form data");
            repository.setEnabled(ID,false);for(Process process:processes)process.refresh();
            for(Process process:processes){
                process.main.until(()->process.loaded()==0&&process.owner.componentCallbacksForCheck.isEmpty(),process.runtime);
                equal("未运行",process.productStatus(),"disable releases product singleton in "+process.name);
                equal(process.installer.added,process.installer.removed,"disable removes every native hook exactly once in "+process.name);
                equal(1,process.owner.componentUnregistrationsForCheck,"disable unregisters theme callback in "+process.name);
            }
            repository.setEnabled(ID,true);for(Process process:processes)process.refresh();
            for(Process process:processes){process.running();equal(2,process.owner.componentRegistrationsForCheck,"reenable installs a fresh product lifecycle in "+process.name);}
            // Safe mode and permanent session shutdown each unwind the actual shipped Entry.
            for(Process process:processes){
                process.runtime.setSafeMode(true);process.main.drain();equal("未运行",process.productStatus(),"safe mode restores product lifecycle in "+process.name);
                equal(0,process.owner.componentCallbacksForCheck.size(),"safe mode unregisters component callbacks in "+process.name);
                process.runtime.setSafeMode(false);process.running();
            }
            for(Process process:processes){process.close();equal(0,process.owner.componentCallbacksForCheck.size(),"shutdown restores observers in "+process.name);equal(process.installer.added,process.installer.removed,"shutdown removes all hooks in "+process.name);equal("未运行",process.productStatus(),"shutdown leaves no active Entry in "+process.name);}
        }
        Handler.drainForCheck();equal(0,Handler.postedForCheck.size(),"no pending plugin status loop after closure");
        activationOnEdit(zip);
        if(args.length>=3)upgradePanels(new File(args[2]),zip);
        if(args.length>=4)upgradeMenusAndPreset(new File(args[3]),zip);
        if(args.length>=5)upgradeAllSettings(new File(args[4]),zip);
        System.out.println("LiquidGlassPluginPackageCheck "+checks+" checks passed (real ZIP/Rhino/product JVM bytecode; Android GPU remains device validation)");
    }
    private static void activationOnEdit(File current)throws Exception {
        File dir=Files.createTempDirectory("glass-edit-activation-").toFile();CustomPluginRepository repo=new CustomPluginRepository(dir);
        try(InputStream input=Files.newInputStream(current.toPath());CustomPluginRepository.Preview p=repo.preview(input,current.getName())){repo.install(p,false);}
        repo.updateState(ID,"scope_desktop",false);repo.updateState(ID,"area_dock",false);repo.updateState(ID,"show_dock",false);
        repo.updateState(ID,"dock_corner",37);JSONObject state=repo.list().get(0).state;
        equal(true,state.getBoolean("scope_desktop"),"editing dock radius enables desktop scope");
        equal(true,state.getBoolean("area_dock"),"editing dock radius enables dock area");
        equal(true,state.getBoolean("show_dock"),"editing dock radius enables dock background");
        equal(37,state.getInt("dock_corner"),"editing radius persists requested value");
        repo.updateState(ID,"scope_desktop",false);state=repo.list().get(0).state;
        equal(false,state.getBoolean("scope_desktop"),"explicit parent off is respected");
        equal(true,state.getBoolean("area_dock"),"disabling parent retains child selection");
        repo.updateState(ID,"area_dock",true);equal(true,repo.list().get(0).state.getBoolean("scope_desktop"),"enabling child enables required scope");
        repo.updateState(ID,"desktop_background",false);repo.updateState(ID,"native_blur_enabled",false);repo.updateState(ID,"panel_background",false);repo.updateState(ID,"panel_tint",false);
        repo.updateState(ID,"desktop_blur",0);state=repo.list().get(0).state;
        equal(true,state.getBoolean("desktop_background"),"zero radius edit enables desktop effect");
        equal(false,state.getBoolean("native_blur_enabled"),"unrelated native blur remains off");
        equal(false,state.getBoolean("panel_background"),"unrelated panel remains off");
        repo.updateState(ID,"native_blur",138);equal(true,repo.list().get(0).state.getBoolean("native_blur_enabled"),"native blur edit enables feature");
        repo.updateState(ID,"panel_light_color","#8055aa77");state=repo.list().get(0).state;
        equal(true,state.getBoolean("panel_background"),"panel color activates panel effect");equal(true,state.getBoolean("panel_tint"),"panel color activates custom tint");
        equal("#8055aa77",state.getString("panel_light_color"),"edited color remains exact");
        repo.updateState(ID,"panel_background",false);repo.updateState(ID,"panel_tint",false);
        equal(false,repo.list().get(0).state.getBoolean("panel_background"),"turning tint off never turns panel back on");
        equal(false,repo.list().get(0).enabled,"configuration edits do not enable the plugin");
    }
    private static void upgradePanels(File previous,File current)throws Exception {
        for(int mode=0;mode<4;mode++) {
            CustomPluginRepository repo=new CustomPluginRepository(Files.createTempDirectory("glass-upgrade-").toFile());
            try(InputStream input=Files.newInputStream(previous.toPath());CustomPluginRepository.Preview p=repo.preview(input,previous.getName())){repo.install(p,false);}
            boolean qs=mode==1||mode==3,shade=mode==2||mode==3;
            repo.updateState(ID,"qs_background",qs);repo.updateState(ID,"shade_background",shade);
            repo.updateState(ID,"qs_blur",37);repo.updateState(ID,"shade_blur",73);
            repo.updateState(ID,"qs_retention",24);repo.updateState(ID,"shade_retention",61);
            repo.updateState(ID,"intensity",81);
            CustomPluginRepository.Plugin next;
            try(InputStream input=Files.newInputStream(current.toPath());CustomPluginRepository.Preview p=repo.preview(input,current.getName())){next=repo.install(p,true);}
            equal(qs||shade,next.state.getBoolean("panel_background"),"upgrade enabled panel mode "+mode);
            equal(!qs&&shade?73:37,next.state.getInt("panel_blur"),"upgrade chooses deterministic native radius "+mode);
            equal(!qs&&shade?61:24,next.state.getInt("panel_retention"),"upgrade keeps corresponding retention "+mode);
            equal(81,next.state.getInt("intensity"),"unrelated optical state survives merge "+mode);
            equal(false,next.state.has("qs_blur")||next.state.has("shade_blur"),"no split runtime values after merge "+mode);
            repo.updateState(ID,"panel_blur",222);
            try(InputStream input=Files.newInputStream(current.toPath());CustomPluginRepository.Preview p=repo.preview(input,current.getName())){next=repo.install(p,true);}
            equal(222,next.state.getInt("panel_blur"),"reimport preserves unified user value "+mode);
            repo.resetState(ID);next=repo.list().get(0);
            equal(referenceValues(next.descriptor).getInt("panel_blur"),next.state.getInt("panel_blur"),"reset discards migrated radius and restores factory default "+mode);
            equal(true,next.state.getBoolean("panel_background"),"reset restores reference shared panel mode "+mode);
        }
    }
    private static void upgradeMenusAndPreset(File previous,File current)throws Exception {
        CustomPluginRepository repo=new CustomPluginRepository(Files.createTempDirectory("glass-menu-upgrade-").toFile());
        try(InputStream input=Files.newInputStream(previous.toPath());CustomPluginRepository.Preview p=repo.preview(input,previous.getName())){repo.install(p,false);}
        repo.updateState(ID,"dock_corner",37.25);repo.updateState(ID,"show_dock",false);
        repo.updateState(ID,"scope_desktop",false);repo.updateState(ID,"area_clear_all",false);
        repo.updateState(ID,"enabled",false);repo.setEnabled(ID,true);
        repo.updateState(ID,"light_color","#8055aa77");repo.updateState(ID,"native_blur",234);repo.updateState(ID,"native_retention",43);
        CustomPluginRepository.Plugin next;
        try(InputStream input=Files.newInputStream(current.toPath());CustomPluginRepository.Preview p=repo.preview(input,current.getName())){next=repo.install(p,true);}
        equal("#8055aa77",next.state.getString("light_color"),"beta004 text color survives explicit color-control upgrade");
        equal(234,next.state.getInt("native_blur"),"beta004 radius is not silently replaced by new preset");
        equal(43,next.state.getInt("native_retention"),"beta004 retention is preserved before explicit preset");
        equal(false,next.enabled,"replacement import stops the plugin until explicitly reenabled");
        equal(false,next.state.getBoolean("enabled"),"replacement import preserves the inner master setting");
        JSONArray ui=next.descriptor.getJSONArray("ui");JSONObject preset=null;int depth=0;
        for(int i=0;i<ui.length();i++) {
            JSONObject control=ui.getJSONObject(i);List<String> path=CustomPluginSchema.groupPath(control);
            depth=Math.max(depth,path.size());
            if(!"management".equals(control.optString("section")))
                equal(true,!path.isEmpty()&&Arrays.asList("作用区域","效果调节").contains(path.get(0)),"settings are under the two requested top menus");
            equal(false,"preset_clear_glass".equals(control.optString("key")),"retired clear preset is absent from current package");
            if("preset_ios27_balanced".equals(control.optString("key")))preset=control;
        }
        equal(2,depth,"real package uses at most two meaningful menu levels");equal(true,preset!=null,"editable iOS 27 preset is present");
        JSONObject beforePreset=new JSONObject(next.state.toString());
        repo.applyPreset(ID,next.versionCode,"preset_ios27_balanced");next=repo.list().get(0);
        presetPreservesOtherSettings(beforePreset,next.state,preset.getJSONObject("values"),"menu upgrade");
        equal(false,next.state.getBoolean("scope_desktop"),"preset keeps desktop scope choice");
        equal(false,next.state.getBoolean("area_clear_all"),"preset keeps clear-button choice");
        equal(false,next.state.getBoolean("enabled"),"preset keeps inner master switch");
        equal(false,next.enabled,"applying a preset does not enable a stopped plugin");
        repo.setEnabled(ID,true);repo.applyPreset(ID,next.versionCode,"preset_ios27_balanced");next=repo.list().get(0);
        equal(true,next.enabled,"applying a preset preserves an explicitly reenabled plugin");
        presetPreservesOtherSettings(beforePreset,next.state,preset.getJSONObject("values"),"reenabled menu upgrade");
        JSONObject values=preset.getJSONObject("values");java.util.Iterator<String> keys=values.keys();
        while(keys.hasNext()){String key=keys.next();equal(values.get(key),next.state.get(key),"iOS 27 reference value actually saved: "+key);}
        equal(false,next.state.getBoolean("custom_tint")||next.state.getBoolean("panel_tint"),"iOS 27 preset starts with system color sources");
        equal(100,next.state.getInt("brightness"),"iOS 27 preset does not force wallpaper brightness");
        String[] editableStrengths={"intensity","refraction","refraction_width","highlight_width","highlight","shadow","light_angle",
                "saturation","brightness","contrast","edge_falloff","native_blur","native_retention","desktop_blur","desktop_retention","panel_blur","panel_retention"};
        for(String key:editableStrengths) {
            JSONObject control=null;
            for(int i=0;i<ui.length();i++)if(key.equals(ui.getJSONObject(i).optString("key")))control=ui.getJSONObject(i);
            equal(true,control!=null&&"slider".equals(control.optString("type")),"preset keeps editable strength control: "+key);
            double value=(control.getDouble("min")+control.getDouble("max"))/2d;
            repo.updateState(ID,key,value);
            equal(value,repo.list().get(0).state.getDouble(key),"real imported preset permits editing: "+key);
        }
        repo.updateState(ID,"refraction",17.25);equal(17.25,repo.list().get(0).state.getDouble("refraction"),"preset remains directly editable");
        for(String prefix:Arrays.asList("","panel_")) {
            String toggle=prefix.isEmpty()?"custom_tint":"panel_tint";
            repo.updateState(ID,toggle,true);
            repo.updateState(ID,prefix+"light_color","#8055aa77");repo.updateState(ID,prefix+"light_opacity",43);
            repo.updateState(ID,prefix+"dark_color","#A0336699");repo.updateState(ID,prefix+"dark_opacity",61);
            next=repo.list().get(0);
            equal(true,next.state.getBoolean(toggle),"custom color source can be enabled after preset: "+prefix);
            equal("#8055aa77",next.state.getString(prefix+"light_color"),"custom light ARGB persists: "+prefix);
            equal("#A0336699",next.state.getString(prefix+"dark_color"),"custom dark ARGB persists: "+prefix);
            repo.updateState(ID,toggle,false);next=repo.list().get(0);
            equal(false,next.state.getBoolean(toggle),"system color source can be restored: "+prefix);
            equal("#8055aa77",next.state.getString(prefix+"light_color"),"system source preserves chosen light color: "+prefix);
            equal(43,next.state.getInt(prefix+"light_opacity"),"system source preserves chosen light strength: "+prefix);
            equal(61,next.state.getInt(prefix+"dark_opacity"),"system source preserves chosen dark strength: "+prefix);
        }
    }
    private static void upgradeAllSettings(File previous,File current)throws Exception {
        equal(true,previous.isFile(),"actual prior settings upgrade artifact exists");
        for(boolean customized:new boolean[]{false,true}) {
            String label=customized?"previous customized":"previous defaults";
            CustomPluginRepository repo=new CustomPluginRepository(Files.createTempDirectory("glass-all-settings-upgrade-").toFile());
            CustomPluginRepository.Plugin installed;
            try(InputStream input=Files.newInputStream(previous.toPath());CustomPluginRepository.Preview p=repo.preview(input,previous.getName())){installed=repo.install(p,false);}
            equal(true,installed.versionCode>=11&&installed.versionCode<=21,"settings fixture is beta.011 through installed beta.021");
            JSONArray ui=installed.descriptor.getJSONArray("ui");
            JSONObject expected=new JSONObject();
            for(int i=0;i<ui.length();i++) {
                JSONObject control=ui.getJSONObject(i);if(!CustomPluginSchema.persistentControl(control))continue;
                String key=control.getString("key");Object value=CustomPluginSchema.defaultValue(control);
                equal(value,installed.state.get(key),label+" starts from the stored UI default: "+key);
                if(customized) {
                    switch(control.getString("type")) {
                        case "switch":value=!((Boolean)value);break;
                        case "slider":
                            double min=control.getDouble("min"),max=control.getDouble("max");
                            value=(min+max)/2d+Math.min(.125d,(max-min)/4d);
                            if("refraction".equals(key))value=-17.25d;
                            if("dock_corner".equals(key))value=37.25d;
                            break;
                        case "color":value=new String[]{"#8055aa77","#A0336699","#004488aa","#ffCc6622"}[i%4];break;
                        default:throw new AssertionError("Add a preservation fixture for persistent control: "+key);
                    }
                }
                expected.put(key,value);
            }
            equal(installed.state.length(),expected.length(),label+" covers every persisted setting");
            if(customized) {
                // Numeric and colour edits may enable parents. Apply true choices first,
                // then every explicit false, so the fixture really contains the requested state.
                for(int pass=0;pass<3;pass++)for(int i=0;i<ui.length();i++) {
                    JSONObject control=ui.getJSONObject(i);if(!CustomPluginSchema.persistentControl(control))continue;
                    String key=control.getString("key");Object value=expected.get(key);
                    int phase=value instanceof Boolean?((Boolean)value?1:2):0;
                    if(pass==phase)repo.updateState(ID,key,value);
                }
                repo.setEnabled(ID,true);
            }
            installed=repo.list().get(0);
            preservedSettings(expected,installed.state,label+" fixture is exact before upgrade");
            JSONObject before=new JSONObject(installed.state.toString());
            CustomPluginRepository.Plugin next;
            try(InputStream input=Files.newInputStream(current.toPath());CustomPluginRepository.Preview p=repo.preview(input,current.getName())){next=repo.install(p,true);}
            equal(true,next.versionCode>installed.versionCode,label+" upgrades to a newer real package");
            equal(false,next.enabled,label+" import retains host stop-until-reenabled policy");
            preservedSettings(before,next.state,label+" upgrade preserves exact saved value");
            if(next.versionCode>=13 && installed.versionCode<=12) {
                equal(before.getDouble("refraction"),next.state.getDouble("clock_refraction"),"clock inherits saved refraction on upgrade");
                equal(before.getDouble("highlight"),next.state.getDouble("clock_highlight"),"clock inherits saved highlight on upgrade");
                equal(before.getDouble("shadow"),next.state.getDouble("clock_shadow"),"clock inherits saved shadow on upgrade");
                equal(100,next.state.getInt("clock_dispersion"),"upgrade preserves native clock dispersion");
                equal(0,next.state.getInt("body_refraction"),"upgrade does not force new body magnification");
                equal(0,next.state.getInt("surface_reflection"),"upgrade does not force new edge reflection");
            }
            if(next.versionCode>=14 && installed.versionCode<=13)
                equal(100,next.state.getInt("surface_transmission"),"upgrade preserves the prior backdrop transmission");
            // Re-import must not silently migrate values that happen to equal an old preset.
            try(InputStream input=Files.newInputStream(current.toPath());CustomPluginRepository.Preview p=repo.preview(input,current.getName())){next=repo.install(p,true);}
            preservedSettings(before,next.state,label+" repeat import preserves exact saved value");
            JSONObject preset=referenceValues(next.descriptor);
            repo.applyPreset(ID,next.versionCode,"preset_ios27_balanced");next=repo.list().get(0);
            preservedSettings(preset,next.state,label+" explicit preset saves requested value");
            presetPreservesOtherSettings(before,next.state,preset,label+" explicit preset");
            equal(false,next.enabled,label+" explicit preset keeps stopped plugin stopped");
        }
    }
    private static void preservedSettings(JSONObject expected,JSONObject actual,String label)throws Exception {
        java.util.Iterator<String> keys=expected.keys();
        while(keys.hasNext()) {
            String key=keys.next();equal(true,actual.has(key),label+" key remains present: "+key);
            Object wanted=expected.get(key),value=actual.get(key);
            if(wanted instanceof Number&&value instanceof Number)
                equal(new java.math.BigDecimal(wanted.toString()),new java.math.BigDecimal(value.toString()),label+": "+key);
            else equal(wanted,value,label+": "+key);
        }
    }
    private static void presetPreservesOtherSettings(JSONObject before,JSONObject after,JSONObject preset,String label)throws Exception {
        JSONObject protectedValues=new JSONObject();java.util.Iterator<String> keys=before.keys();
        while(keys.hasNext()) {
            String key=keys.next();
            if(key.startsWith("scope_")||key.startsWith("area_")||Arrays.asList("enabled","show_dock","dock_corner").contains(key))
                equal(false,preset.has(key),label+" preset does not declare selection or layout setting: "+key);
            if(!preset.has(key))protectedValues.put(key,before.get(key));
        }
        preservedSettings(protectedValues,after,label+" preserves settings outside preset");
    }
    private static JSONObject referenceValues(JSONObject descriptor)throws Exception {
        JSONArray ui=descriptor.getJSONArray("ui");
        for(int i=0;i<ui.length();i++) {
            JSONObject control=ui.getJSONObject(i);
            if("preset_ios27_balanced".equals(control.optString("key")))return control.getJSONObject("values");
        }
        throw new AssertionError("Editable iOS 27 preset missing");
    }
    private static List<String> list(JSONArray array){List<String> values=new ArrayList<>();for(int i=0;i<array.length();i++)values.add(array.optString(i));return values;}
    private static String combined(JSONObject descriptor,String generation){
        try{return CustomPluginAppProvider.withStatus(descriptor,generation,new JSONObject().put("status",new JSONObject().put("glass_status","system-ui-existing"))).toString();}
        catch(Exception failed){throw new IllegalStateException(failed);}
    }
}
