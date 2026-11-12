// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.service.XposedService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Executable;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real import, provider authentication, exact asset transfer and event-driven multi-process runtime. */
public final class CustomPluginAppsCheck {
    private static final String A="com.example.launcher",B="com.example.sidebar",SYS="com.android.systemui";
    private static int checks;
    private static final List<String> events=Collections.synchronizedList(new ArrayList<>());
    public static void record(String event){events.add(event);}
    public static final class Target {public int value(){return 3;}}
    private interface Task {void run()throws Exception;}
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(why+": "+expected+" != "+actual);}
    private static void reject(Task task,Class<? extends Throwable> expected,String why)throws Exception{
        checks++;try{task.run();}catch(Throwable failure){if(expected.isInstance(failure))return;throw new AssertionError(why,failure);}throw new AssertionError("Accepted "+why);
    }
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static JSONObject descriptor(String id,String source,String...scopes)throws Exception{
        JSONObject result=new JSONObject().put("id",id).put("name","跨应用检查").put("version","1.0").put("versionCode",1).put("apiVersion",2)
                .put("capabilities",new JSONArray().put("runtime")).put("features",new JSONArray().put("scope.apps")).put("scopes",new JSONArray(Arrays.asList(scopes)))
                .put("runtime",new JSONObject().put("entry","runtime.js")).put("settings",new JSONObject()).put("hooks",new JSONArray())
                .put("ui",new JSONArray().put(new JSONObject().put("type","slider").put("key","strength").put("title","强度").put("min",0).put("max",100).put("default",45)));
        return result;
    }
    private static void install(CustomPluginRepository repository,JSONObject descriptor,String source)throws Exception{
        JSONObject manifest=new JSONObject(descriptor.toString());manifest.remove("settings");manifest.remove("hooks");manifest.remove("ui");manifest.put("entry","main.js");
        Map<String,String> files=new java.util.LinkedHashMap<>();files.put("plugin.json",manifest.toString());files.put("main.js","C17.define("+descriptor+");");files.put("runtime.js",source);files.put("assets/material.txt","private:"+descriptor.getString("id"));
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(buffer)){
            for(Map.Entry<String,String> file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(bytes(file.getValue()));zip.closeEntry();}
        }
        try(CustomPluginRepository.Preview preview=repository.preview(new ByteArrayInputStream(buffer.toByteArray()),"apps.zip")){repository.install(preview,false);}
    }
    private static final class Packages extends PackageManager {
        @Override public ActivityInfo getActivityInfo(android.content.ComponentName name,int flags)throws NameNotFoundException{throw new NameNotFoundException();}
        @Override public int getComponentEnabledSetting(android.content.ComponentName name){return 0;}
        @Override public void setComponentEnabledSetting(android.content.ComponentName name,int state,int flags){}
        @Override public String[] getPackagesForUid(int uid){return new String[]{uid==1000?SYS:uid==12001?A:uid==12002?B:uid==20000?ModuleDiagnostics.PACKAGE_NAME:"untrusted.app"};}
        @Override public ApplicationInfo getApplicationInfo(String name,int flags)throws NameNotFoundException{if(ModuleDiagnostics.PACKAGE_NAME.equals(name))return new ApplicationInfo();throw new NameNotFoundException();}
    }
    private static final class Owner extends Context {
        final String name;final Packages packages=new Packages();final ActivationGuardPreferencesCheck.MemoryPreferences preferences=new ActivationGuardPreferencesCheck.MemoryPreferences();
        final List<Thread> fileReaders=Collections.synchronizedList(new ArrayList<>());
        final Map<BroadcastReceiver,IntentFilter> receivers=new HashMap<>();
        ContentResolver resolver=new ContentResolver();
        Owner(File files,String name){super(files);this.name=name;}
        @Override public String getPackageName(){return name;}
        @Override public File getFilesDir(){fileReaders.add(Thread.currentThread());return super.getFilesDir();}
        @Override public PackageManager getPackageManager(){return packages;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}
        @Override public boolean deleteSharedPreferences(String name){return true;}
        @Override public ContentResolver getContentResolver(){return resolver;}
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){return registerReceiver(receiver,filter);}
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter){receivers.put(receiver,filter);return null;}
        @Override public void unregisterReceiver(BroadcastReceiver receiver){receivers.remove(receiver);}
        void broadcast(String action){
            for(Map.Entry<BroadcastReceiver,IntentFilter> entry:new HashMap<>(receivers).entrySet())
                if(entry.getValue().actions.contains(action)&&entry.getValue().schemes.isEmpty())entry.getKey().onReceive(this,new Intent(action));
        }
    }
    private static JSONObject plan(SettingsProvider provider,int uid,String target)throws Exception{
        Binder.setCallingIdentityForCheck(uid,uid+101);
        JSONObject value=CustomPluginAppRuntime.Session.decode(provider.call(CustomPluginAppProvider.PLAN,target,null));
        equal(uid,Binder.getCallingUid(),"plan restores calling UID");equal(uid+101,Binder.getCallingPid(),"plan restores calling PID");return value;
    }
    private static List<String> ids(JSONObject plan,String field)throws Exception{
        List<String> values=new ArrayList<>();JSONArray entries=plan.getJSONArray(field);for(int i=0;i<entries.length();i++)values.add(entries.getJSONObject(i).getString("id"));return values;
    }
    private static String asset(SettingsProvider provider,int uid,String target,String id)throws Exception{
        Binder.setCallingIdentityForCheck(uid,uid+101);Uri uri=Uri.parse("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/apps/"+target+"/"+id+"/1/assets/material.txt");
        try(java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri,"r"))){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}
    }
    private static final class Queue extends Handler {
        final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>(),later=new ConcurrentLinkedQueue<>();
        Queue(){super(Looper.getMainLooper());}
        @Override public boolean post(Runnable runnable){tasks.add(runnable);return true;}
        @Override public boolean postDelayed(Runnable runnable,long delay){later.add(runnable);return true;}
        @Override public void removeCallbacks(Runnable runnable){tasks.removeIf(task->task==runnable);later.removeIf(task->task==runnable);}
        void drain(){int count=0;Runnable task;while((task=tasks.poll())!=null){if(++count>10000)throw new AssertionError("Unbounded runtime work");task.run();}}
        void until(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);do{drain();if(condition.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<end);throw new AssertionError("Runtime did not settle: "+events);}
    }
    private static final class Transport extends ContentResolver {
        final SettingsProvider provider;final int uid;final AtomicInteger reads=new AtomicInteger(),planRequests=new AtomicInteger(),assetRequests=new AtomicInteger();
        volatile boolean fail,failObservation;volatile CountDownLatch captured,release;
        Transport(SettingsProvider provider,int uid){this.provider=provider;this.uid=uid;}
        @Override public void registerContentObserver(Uri uri,boolean descendants,android.database.ContentObserver observer){
            if(failObservation)throw new SecurityException("Fixture observation unavailable");super.registerContentObserver(uri,descendants,observer);
        }
        @Override public Bundle call(Uri uri,String method,String target,Bundle extras){
            if(CustomPluginAppProvider.PLAN.equals(method))planRequests.incrementAndGet();
            Binder.setCallingIdentityForCheck(uid,uid+101);if(fail)throw new IllegalStateException("Provider unavailable");
            Bundle answer=provider.call(method,target,extras);
            if(CustomPluginAppProvider.PLAN.equals(method)){reads.incrementAndGet();CountDownLatch capture=captured,gate=release;if(capture!=null){captured=null;capture.countDown();try{if(!gate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Fixture gate timeout");}catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}}}
            return answer;
        }
        @Override public java.io.InputStream openInputStream(Uri uri)throws java.io.FileNotFoundException{
            assetRequests.incrementAndGet();
            if(fail)throw new java.io.FileNotFoundException("Provider unavailable");
            Binder.setCallingIdentityForCheck(uid,uid+101);return new ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri,"r"));
        }
    }
    private static class Installer implements CustomPluginRuntime.Install {
        final AtomicInteger added=new AtomicInteger(),removed=new AtomicInteger();
        final Map<Executable,XposedInterface.Hooker> callbacks=new HashMap<>();
        @Override public XposedInterface.HookHandle hook(Executable target,XposedInterface.Hooker hooker){
            callbacks.put(target,hooker);
            added.incrementAndGet();return (XposedInterface.HookHandle)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},(proxy,method,args)->{
                if("unhook".equals(method.getName())){removed.incrementAndGet();return null;}if("getExecutable".equals(method.getName()))return target;throw new AssertionError(method);
            });
        }
    }
    private static final class FrameworkStore {
        volatile Map<String,?> values=Collections.emptyMap();
        final List<SharedPreferences.OnSharedPreferenceChangeListener> listeners=new CopyOnWriteArrayList<>();
        final SharedPreferences preferences=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
                    switch(method.getName()){
                        case "getAll":return values;
                        case "registerOnSharedPreferenceChangeListener":listeners.add((SharedPreferences.OnSharedPreferenceChangeListener)args[0]);return null;
                        case "unregisterOnSharedPreferenceChangeListener":listeners.remove(args[0]);return null;
                        default:throw new AssertionError(method);
                    }
                });
        final XposedService service=new XposedService(preferences);
        long revision;
        void publish(CustomPluginRepository repository,boolean safeMode)throws Exception{
            String metadata=CustomPluginFrameworkPublisher.stage(service,repository.frameworkPlans(safeMode),Collections.emptySet()).metadata;
            Bundle main=SettingsSnapshot.fromPreferences(Collections.singletonMap(StatusBarSettings.SAFE_MODE,safeMode));
            Map<String,Object> next=new HashMap<>();for(String key:main.keySet())next.put(key,main.get(key));
            next.put(SettingsFrameworkMirror.SCHEMA,SettingsFrameworkMirror.VERSION);next.put(SettingsFrameworkMirror.REVISION,++revision);
            next.put(CustomPluginFrameworkPublisher.KEY,metadata);values=next;
            for(SharedPreferences.OnSharedPreferenceChangeListener listener:listeners)listener.onSharedPreferenceChanged(preferences,SettingsFrameworkMirror.REVISION);
        }
    }
    private static int running(CustomPluginRuntime runtime){Bundle report=new Bundle();runtime.health(report);return report.getInt("custom_plugin_runtime_loaded");}
    public static void main(String[] args)throws Exception{
        android.os.Process.uidForCheck=20000;android.os.Process.pidForCheck=91;
        File files=Files.createTempDirectory("c17-apps-provider-").toFile();Owner host=new Owner(files,ModuleDiagnostics.PACKAGE_NAME);
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(host,null);CustomPluginRepository repository=new CustomPluginRepository(new File(files,"custom_plugins"));
        JSONObject multi=descriptor("apps.shared","",A,B,SYS);
        multi.getJSONArray("ui").put(new JSONObject().put("type","status").put("key","glass_status").put("title","运行状态"));CustomPluginSupport.requireExecutable(multi);
        equal(true,CustomPluginSupport.FEATURES.contains("scope.apps"),"cross-process engine is advertised");
        JSONObject missing=new JSONObject(multi.toString()).put("features",new JSONArray());reject(()->CustomPluginSupport.requireExecutable(missing),IllegalArgumentException.class,"foreign scopes without capability");
        for(String invalid:new String[]{"android",ModuleDiagnostics.PACKAGE_NAME,"../app"}){JSONObject one=new JSONObject(multi.toString()).put("scopes",new JSONArray().put(invalid));reject(()->CustomPluginSupport.requireExecutable(one),IllegalArgumentException.class,"non-application target "+invalid);}
        JSONObject rules=new JSONObject(multi.toString()).put("settings",new JSONObject().put("clock_enabled",true));reject(()->CustomPluginSupport.requireExecutable(rules),IllegalArgumentException.class,"SystemUI overlays cannot leak across apps");
        admission(multi);
        String runtime="var p=String(C17.host.runtimePackage);Packages.dev.puitheme.CustomPluginAppsCheck.record('start:'+p);"
                +"C17.hook('dev.puitheme.CustomPluginAppsCheck$Target','value',[],function(c){return c.proceed()+C17.state.strength;});"
                +"C17.publishStatus({glass_status:'glass '+p});"
                +"C17.onStateChanged(function(next){Packages.dev.puitheme.CustomPluginAppsCheck.record('state:'+p+':'+next.strength);C17.publishStatus({glass_status:'strength '+next.strength+' '+p});});"
                +"C17.onCleanup(function(){Packages.dev.puitheme.CustomPluginAppsCheck.record('stop:'+p);});";
        install(repository,multi,runtime);install(repository,descriptor("apps.only-a","",A),"/*"+"x".repeat(70000)+"*/ // 完整结尾");
        install(repository,descriptor("apps.only-b","",B),"void 0;");
        JSONObject legacy=descriptor("apps.legacy","",SYS);legacy.remove("scopes");legacy.remove("features");install(repository,legacy,"void 0;");
        equal(Collections.emptyList(),ids(plan(provider,12001,A),"runtimes"),"imported plugins stay disabled in other apps");
        for(CustomPluginRepository.Plugin plugin:repository.list())repository.setEnabled(plugin.id,true);
        JSONObject a=plan(provider,12001,A),b=plan(provider,12002,B);
        equal(Arrays.asList("apps.only-a","apps.shared"),ids(a,"runtimes"),"launcher sees only its two runtimes");
        equal(Arrays.asList("apps.only-b","apps.shared"),ids(b,"runtimes"),"sidebar sees only its two runtimes");
        equal(ids(a,"runtimes"),ids(a,"installedPlugins"),"launcher inventory contains no unrelated plugins");
        equal(false,a.has("settings")||a.has("hooks")||a.has("adminCommands"),"foreign plan excludes host settings and command channels");
        equal(A,a.getString("targetPackage"),"destination is bound in the plan");
        equal("",a.getJSONArray("runtimes").getJSONObject(0).getString("baseDir"),"host private filesystem paths are redacted");
        equal(A,a.getJSONArray("runtimes").getJSONObject(0).getString("assetPackage"),"assets are bound to runtime package");
        equal(true,a.getJSONArray("runtimes").getJSONObject(0).getString("source").endsWith("完整结尾"),"large UTF-8 plans survive streamed transport");
        equal(Arrays.asList("apps.legacy","apps.shared"),ids(CustomPluginRepository.plan(host),"runtimes"),"legacy SystemUI plan excludes foreign-only runtimes");
        equal(false,a.getBoolean("safeMode"),"default app safety follows host");
        host.preferences.values.put(StatusBarSettings.CLOCK_ENABLED,false);
        host.preferences.values.put(StatusBarSettings.SHADE_CLOCK_ENABLED,false);
        host.preferences.values.put(StatusBarSettings.TILES_BLUR_RADIUS,0f);
        SettingsProvider coldProvider=new SettingsProvider();coldProvider.attachInfo(host,null);
        JSONObject cold=plan(coldProvider,12001,A);
        equal(ids(a,"runtimes"),ids(cold,"runtimes"),"provider cold start restores persisted enabled plugins without opening C17");
        equal(false,cold.getBoolean("safeMode"),"disabled host styling does not pause a scoped plugin at startup");
        host.preferences.values.clear();host.preferences.values.put(StatusBarSettings.SAFE_MODE,true);
        equal(true,plan(provider,12001,A).getBoolean("safeMode"),"emergency safety reaches every package");host.preferences.values.clear();
        Binder.setCallingIdentityForCheck(12001,12102);
        reject(()->provider.call(CustomPluginAppProvider.PLAN,B,null),SecurityException.class,"cannot claim another package");
        reject(()->provider.call(CustomPluginAppProvider.PLAN,SYS,null),SecurityException.class,"cannot use external transport for SystemUI");
        for(String method:new String[]{CustomPluginHooks.METHOD,"read_statusbar_settings",CustomPluginAdmin.METHOD,CustomPluginAdmin.REPORT,CustomPluginAppProvider.STATUS})
            reject(()->provider.call(method,null,null),SecurityException.class,"foreign caller cannot use "+method);
        equal("private:apps.shared",asset(provider,12001,A,"apps.shared"),"shared scoped assets are readable");
        equal("private:apps.only-b",asset(provider,12002,B,"apps.only-b"),"sidebar receives its exact asset");
        reject(()->asset(provider,12001,A,"apps.only-b"),java.io.FileNotFoundException.class,"foreign package cannot read another plugin asset");
        reject(()->asset(provider,12001,B,"apps.only-b"),SecurityException.class,"asset path cannot impersonate another package");
        Binder.setCallingIdentityForCheck(12001,12102);Uri root=Uri.parse("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/apps/"+A+"/apps.shared/1/");
        reject(()->provider.openFile(Uri.parse(root+"../library.json"),"r"),java.io.FileNotFoundException.class,"asset traversal rejected");
        reject(()->provider.openFile(Uri.parse(root+"assets/material.txt"),"w"),java.io.FileNotFoundException.class,"external assets cannot be written");
        reject(()->provider.openFile(Uri.parse("content://"+StatusBarSettings.AUTHORITY+"/font/current"),"r"),SecurityException.class,"font endpoint remains private");
        repository.setEnabled("apps.only-a",false);
        reject(()->asset(provider,12001,A,"apps.only-a"),java.io.FileNotFoundException.class,"disabled foreign asset is revoked");
        JSONObject disabled=plan(provider,12001,A);equal(Arrays.asList("apps.shared"),ids(disabled,"runtimes"),"disable removes executable only");
        equal(Arrays.asList("apps.only-a","apps.shared"),ids(disabled,"installedPlugins"),"disable retains owned data inventory");
        lifecycle(provider,repository,host,multi);
        bootRecovery(provider);
        frameworkBoot();
        failureStatus();
        bootstrap();
        processStorage();
        System.out.println("CustomPluginAppsCheck "+checks+" checks passed");
    }
    private static void failureStatus()throws Exception{
        File files=Files.createTempDirectory("c17-app-failure-provider-").toFile();Owner host=new Owner(files,ModuleDiagnostics.PACKAGE_NAME);
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(host,null);CustomPluginRepository repository=new CustomPluginRepository(new File(files,"custom_plugins"));
        String id="apps.fault";JSONObject descriptor=descriptor(id,"",A);
        descriptor.getJSONArray("ui").put(new JSONObject().put("type","status").put("key","glass_status").put("title","运行状态"));
        install(repository,descriptor,"C17.publishStatus({glass_status:'stale running message'});C17.hook('dev.puitheme.CustomPluginAppsCheck$Target','value',[],function(c){throw new Error('fixture callback failure');});");repository.setEnabled(id,true);
        Owner owner=new Owner(Files.createTempDirectory("c17-app-failure-runtime-").toFile(),A);owner.resolver=new Transport(provider,12001);
        Queue queue=new Queue();Installer installer=new Installer();CustomPluginAppRuntime.Session session=new CustomPluginAppRuntime.Session(owner,queue,installer);
        try{
            session.runtime.rememberLoader(CustomPluginAppsCheck.class.getClassLoader());session.start();queue.until(()->running(session.runtime)==1);
            String generation=plan(provider,12001,A).getJSONArray("runtimes").getJSONObject(0).getString("generation");
            queue.until(()->combined(descriptor,generation).contains("stale running message"));
            java.lang.reflect.Method target=Target.class.getDeclaredMethod("value");AtomicInteger proceeded=new AtomicInteger();
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(CustomPluginAppsCheck.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(proxy,method,args)->{
                if(method.getName().equals("getExecutable"))return target;if(method.getName().equals("getThisObject"))return new Target();
                if(method.getName().equals("getArgs"))return Collections.emptyList();if(method.getName().equals("proceed")){proceeded.incrementAndGet();return 3;}
                throw new AssertionError(method);
            });
            try{equal(3,installer.callbacks.get(target).intercept(chain),"failed plugin callback preserves native work");}catch(Throwable failure){throw new AssertionError(failure);}
            queue.until(()->running(session.runtime)==0&&installer.removed.get()==1);
            equal(1,proceeded.get(),"native callback proceeds exactly once after failure");
            equal("failed",session.runtime.applicationStatus().getJSONObject(0).getString("stage"),"external reports preserve a failed entry instead of permanently stopping");
            queue.until(()->combined(descriptor,generation).contains("加载失败"));
            equal(false,combined(descriptor,generation).contains("stale running message"),"failed stage overrides previously published success text");
        }finally{session.stop();queue.drain();}
    }
    private static Object bootstrap(String packageName)throws Exception{
        java.lang.reflect.Field processes=CustomPluginAppRuntime.class.getDeclaredField("processes");processes.setAccessible(true);
        return ((Map<?,?>)processes.get(null)).get(packageName);
    }
    private static CustomPluginAppRuntime.Session session(Object bootstrap)throws Exception{
        java.lang.reflect.Field field=bootstrap.getClass().getDeclaredField("session");field.setAccessible(true);return (CustomPluginAppRuntime.Session)field.get(bootstrap);
    }
    private static void queue(Object bootstrap,Queue queue)throws Exception{
        java.lang.reflect.Field main=bootstrap.getClass().getDeclaredField("main");main.setAccessible(true);main.set(bootstrap,queue);
    }
    private static void attach(Installer installer,android.app.Application app,Owner context)throws Exception{
        java.lang.reflect.Method attach=android.app.Application.class.getDeclaredMethod("attach",Context.class);attach.setAccessible(true);
        XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(CustomPluginAppsCheck.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(proxy,method,args)->{
            if(method.getName().equals("getThisObject"))return app;
            if(method.getName().equals("proceed")){attach.invoke(app,context);return null;}
            throw new AssertionError(method);
        });
        try{installer.callbacks.get(attach).intercept(chain);}catch(Throwable failure){throw new AssertionError("actual application attach hook failed",failure);}
    }
    private static void bootstrap()throws Exception{
        File files=Files.createTempDirectory("c17-bootstrap-provider-").toFile();Owner host=new Owner(files,ModuleDiagnostics.PACKAGE_NAME);
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(host,null);CustomPluginRepository repository=new CustomPluginRepository(new File(files,"custom_plugins"));
        String id="apps.bootstrap";install(repository,descriptor(id,"",A,B),"C17.hook('dev.puitheme.CustomPluginAppsCheck$Target','value',[],function(c){return c.proceed();});");repository.setEnabled(id,true);
        Owner owner=new Owner(Files.createTempDirectory("c17-bootstrap-runtime-").toFile(),A);Transport transport=new Transport(provider,12001);owner.resolver=transport;
        Installer installer=new Installer();ClassLoader loader=CustomPluginAppsCheck.class.getClassLoader();
        CustomPluginAppRuntime.install(A,A,loader,installer);Object bootstrap=bootstrap(A);Queue queue=new Queue();queue(bootstrap,queue);
        CustomPluginAppRuntime.install(A,A,loader,installer);equal(1,installer.added.get(),"package callbacks install Application.attach once");
        attach(installer,new android.app.Application(),host);queue.drain();equal(null,session(bootstrap),"unrelated applications cannot claim the bootstrap");
        android.app.Application app=new android.app.Application();attach(installer,app,owner);
        equal(owner,app.getBaseContext(),"native attach executes before the application package is inspected");
        equal(null,session(bootstrap),"application attachment schedules runtime work after the native callback");
        queue.until(()->{try{return session(bootstrap)!=null&&running(session(bootstrap).runtime)==1;}catch(Exception invalid){throw new AssertionError(invalid);}});
        CustomPluginAppRuntime.Session session=session(bootstrap);
        try{
            equal(2,installer.added.get(),"actual attach-to-provider chain reaches the plugin hook");equal(1,installer.removed.get(),"one-shot application hook is removed after connection");
            equal(app,session.owner,"plugin runtime retains its actual application context");
            repository.updateState(id,"strength",71);transport.dispatchSettingsChange();queue.until(()->"71".equals(evaluate(session,id,"C17.state.strength")));
            equal(2,installer.added.get(),"observed configuration changes reuse the attached plugin");
            CustomPluginAppRuntime.install(A,A,loader,installer);equal(2,installer.added.get(),"late package-ready callbacks do not duplicate active hooks");
            repository.setEnabled(id,false);transport.dispatchSettingsChange();queue.until(()->running(session.runtime)==0&&installer.removed.get()==2);
            equal(1,transport.observers.size(),"live runtime keeps the disable observer until shutdown");
        }finally{session.stop();queue.drain();}
        equal(0,transport.observers.size(),"bootstrap shutdown releases its observer");
        // onPackageReady may arrive after Application.attach. It must use the existing
        // application without installing a second runtime or waiting for another attach.
        Owner other=new Owner(Files.createTempDirectory("c17-bootstrap-late-").toFile(),B);other.resolver=new Transport(provider,12002);
        Installer late=new Installer();CustomPluginAppRuntime.install(B,B+":panel",loader,late);Object lateBootstrap=bootstrap(B);Queue lateQueue=new Queue();queue(lateBootstrap,lateQueue);
        android.app.Application existing=new android.app.Application();java.lang.reflect.Method attach=android.app.Application.class.getDeclaredMethod("attach",Context.class);attach.setAccessible(true);attach.invoke(existing,other);
        // Some framework callbacks precede mInitialApplication, while optimized default
        // Application.attach bypasses our hook. The next main-loop turn must still connect.
        android.app.ActivityThread.applicationForCheck=null;repository.setEnabled(id,true);
        CustomPluginAppRuntime.install(B,B+":panel",loader,late);
        equal(null,session(lateBootstrap),"early package-ready callback has no application yet");
        android.app.ActivityThread.applicationForCheck=existing;
        try{
            lateQueue.until(()->{try{return session(lateBootstrap)!=null&&running(session(lateBootstrap).runtime)==1;}catch(Exception invalid){throw new AssertionError(invalid);}});
            equal(2,late.added.get(),"existing application fallback reaches its scoped plugin once");equal(1,late.removed.get(),"fallback also removes the unused application hook");
        }finally{android.app.ActivityThread.applicationForCheck=null;CustomPluginAppRuntime.Session started=session(lateBootstrap);if(started!=null)started.stop();lateQueue.drain();}
    }
    private static String evaluate(CustomPluginAppRuntime.Session session,String id,String source){try{return session.runtime.evaluateCommand(id,source);}catch(Exception unavailable){return "";}}
    private static void bootRecovery(SettingsProvider provider)throws Exception{
        Owner owner=new Owner(Files.createTempDirectory("c17-locked-app-runtime-").toFile(),A);
        Transport transport=new Transport(provider,12001);owner.resolver=transport;transport.failObservation=true;
        Queue queue=new Queue();Installer hooks=new Installer();CustomPluginAppRuntime.Session session=new CustomPluginAppRuntime.Session(owner,queue,hooks);
        try{
            session.runtime.rememberLoader(CustomPluginAppsCheck.class.getClassLoader());session.start();queue.until(()->!queue.later.isEmpty());
            int attempts=0;
            for(Runnable retry;(retry=queue.later.poll())!=null;){
                if(++attempts>8)throw new AssertionError("Unbounded boot provider retries");
                queue.post(retry);queue.until(()->!session.loading);
            }
            equal(0,transport.reads.get(),"unavailable boot observer prevents reading any plan");
            equal(0,hooks.added.get(),"external boot keeps the native state during the retry window");
            transport.failObservation=false;
            owner.broadcast(Intent.ACTION_USER_UNLOCKED);
            equal(false,queue.tasks.isEmpty(),"first unlock wakes a parked external plugin with no settings edit or process restart");
            queue.until(()->running(session.runtime)==1);
            equal(1,hooks.added.get(),"unlock loads the enabled scoped plugin exactly once");
            equal(0,queue.later.size(),"a healthy boot plan leaves no retry timers");
        }finally{session.stop();queue.drain();}
        equal(0,owner.receivers.size(),"closing a boot session unregisters readiness and removal receivers");
        equal(1,hooks.removed.get(),"closing the recovered session removes its hook exactly once");
    }
    private static void frameworkBoot()throws Exception{
        Owner host=new Owner(Files.createTempDirectory("c17-shared-boot-host-").toFile(),ModuleDiagnostics.PACKAGE_NAME);
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(host,null);
        CustomPluginRepository repository=new CustomPluginRepository(new File(host.getFilesDir(),"custom_plugins"));
        String id="apps.shared-boot";
        install(repository,descriptor(id,"",A),"C17.hook('dev.puitheme.CustomPluginAppsCheck$Target','value',[],function(c){return c.proceed();});");
        repository.setEnabled(id,true);
        FrameworkStore store=new FrameworkStore();store.publish(repository,false);
        Owner owner=new Owner(Files.createTempDirectory("c17-shared-boot-owner-").toFile(),A);
        Transport transport=new Transport(provider,12001);transport.fail=true;transport.failObservation=true;owner.resolver=transport;
        Installer hooks=new Installer(){
            @Override public SharedPreferences frameworkPreferences(){return store.preferences;}
            @Override public ParcelFileDescriptor openFrameworkFile(String name)throws java.io.FileNotFoundException{return store.service.openRemoteFileForCheck(name);}
        };
        Queue queue=new Queue();CustomPluginAppRuntime.Session session=new CustomPluginAppRuntime.Session(owner,queue,hooks);
        try{
            session.runtime.rememberLoader(CustomPluginAppsCheck.class.getClassLoader());session.start();queue.until(()->running(session.runtime)==1);
            equal(1,hooks.added.get(),"external hooks start from the shared host snapshot without opening C17");
            equal(0,transport.planRequests.get()+transport.assetRequests.get(),"provider blocking does not affect shared plan or resource loading");
            equal(0,queue.later.size(),"an observed boot snapshot does not poll for provider readiness");
            File assets=new File(evaluate(session,id,"String(C17.pluginDir)"),"assets/material.txt");
            equal("private:"+id,Files.readString(assets.toPath()),"boot runtime consumes the exact scoped resource");
            repository.updateState(id,"strength",74);store.publish(repository,false);
            queue.until(()->"74".equals(evaluate(session,id,"C17.state.strength")));
            equal(1,hooks.added.get(),"shared revision changes update the running plugin without reinstalling hooks");
            transport.fail=false;transport.failObservation=false;session.request();
            queue.until(()->transport.reads.get()>0&&!session.loading);
            equal(1,hooks.added.get(),"primary confirmation of the same configuration reuses the boot runtime");
            host.preferences.values.put(StatusBarSettings.SAFE_MODE,true);transport.dispatchSettingsChange();
            queue.until(()->running(session.runtime)==0);
            transport.fail=true;transport.dispatchSettingsChange();queue.until(()->!queue.later.isEmpty());
            equal(1,hooks.added.get(),"the older shared snapshot cannot undo a newer primary safety switch");
            host.preferences.values.put(StatusBarSettings.SAFE_MODE,false);store.publish(repository,false);
            queue.until(()->running(session.runtime)==1);
            equal(2,hooks.added.get(),"a newer confirmed shared revision can resume a paused runtime");
            transport.fail=false;repository.setEnabled(id,false);transport.dispatchSettingsChange();
            queue.until(()->running(session.runtime)==0);
            transport.fail=true;transport.dispatchSettingsChange();queue.until(()->!queue.later.isEmpty());
            equal(2,hooks.added.get(),"provider loss cannot restore a plugin disabled at a newer library revision");
            repository.delete(id);store.publish(repository,false);
            queue.until(()->!assets.exists()&&queue.later.isEmpty());
            equal(0,running(session.runtime),"a removed scope clears owned boot resources");
        }finally{session.stop();queue.drain();}
        equal(0,store.listeners.size(),"external shutdown unregisters the shared boot observer");
        equal(0,owner.receivers.size(),"shared boot retains and removes the host lifecycle receiver");
    }
    private static void admission(JSONObject multi)throws Exception{
        JSONObject appOnly=new JSONObject(multi.toString()).put("scopes",new JSONArray().put(A));
        appOnly.getJSONArray("features").put("runtime.actions");
        equal("",CustomPluginSupport.executionProblem(appOnly),"declaring action capability alone remains compatible");
        appOnly.getJSONArray("ui").put(new JSONObject().put("type","action").put("key","refresh").put("title","刷新"));
        reject(()->CustomPluginSupport.requireExecutable(appOnly),IllegalArgumentException.class,"app-only form actions have no SystemUI recipient");
        JSONObject mixed=new JSONObject(appOnly.toString()).put("scopes",new JSONArray().put(A).put(SYS));
        equal("",CustomPluginSupport.executionProblem(mixed),"mixed SystemUI and app scopes preserve working actions");
        JSONObject sorted=new JSONObject(multi.toString()).put("scopes",new JSONArray().put(A));
        sorted.getJSONArray("ui").put(new JSONObject().put("type","sort").put("key","order").put("optionsAction","choices"));
        reject(()->CustomPluginSupport.requireExecutable(sorted),IllegalArgumentException.class,"dynamic sort options also require SystemUI command routing");
        JSONObject sharedPrefs=new JSONObject(multi.toString());sharedPrefs.getJSONObject("runtime").put("preferences",new JSONArray().put("native_settings"));
        reject(()->CustomPluginSupport.requireExecutable(sharedPrefs),IllegalArgumentException.class,"foreign plugins cannot own globally named app preferences");
        sharedPrefs.put("scopes",new JSONArray().put(SYS));
        equal("",CustomPluginSupport.executionProblem(sharedPrefs),"legacy SystemUI preference ownership remains supported");
        CustomPluginRepository review=new CustomPluginRepository(Files.createTempDirectory("c17-apps-review-").toFile());
        install(review,appOnly,"void 0;");equal(1,review.list().size(),"unsupported action package can still be imported for review");
        reject(()->review.setEnabled(appOnly.getString("id"),true),IllegalArgumentException.class,"unsupported action package cannot become a broken enabled UI");
        equal(false,review.list().get(0).enabled,"rejected enable leaves package safely disabled");
    }
    private static void processStorage()throws Exception{
        File hostFiles=Files.createTempDirectory("c17-same-app-provider-").toFile();Owner host=new Owner(hostFiles,ModuleDiagnostics.PACKAGE_NAME);
        SettingsProvider provider=new SettingsProvider();provider.attachInfo(host,null);CustomPluginRepository repository=new CustomPluginRepository(new File(hostFiles,"custom_plugins"));
        String id="apps.process-storage";install(repository,descriptor(id,"",A),"C17.onCleanup(function(){C17.load('marker','missing');});");repository.setEnabled(id,true);
        File sharedFiles=Files.createTempDirectory("c17-same-app-files-").toFile();Owner owner=new Owner(sharedFiles,A);
        Transport transport=new Transport(provider,12001);owner.resolver=transport;Thread mainThread=Thread.currentThread();
        Queue firstQueue=new Queue(),secondQueue=new Queue(),restartQueue=new Queue();Installer firstHooks=new Installer(),secondHooks=new Installer(),restartHooks=new Installer();
        CustomPluginAppRuntime.Session first=new CustomPluginAppRuntime.Session(owner,firstQueue,firstHooks,A),
                second=new CustomPluginAppRuntime.Session(owner,secondQueue,secondHooks,A+":overlay"),
                restart=new CustomPluginAppRuntime.Session(owner,restartQueue,restartHooks,A);
        try{
            first.start();second.start();firstQueue.until(()->running(first.runtime)==1);secondQueue.until(()->running(second.runtime)==1);
            equal(false,owner.fileReaders.contains(mainThread),"foreign ownership and asset filesystem work never runs on the application main thread");
            String dir1=first.runtime.evaluateCommand(id,"String(C17.pluginDir)"),dir2=second.runtime.evaluateCommand(id,"String(C17.pluginDir)");
            String data1=first.runtime.evaluateCommand(id,"String(C17.dataDir())"),data2=second.runtime.evaluateCommand(id,"String(C17.dataDir())");
            equal(false,dir1.equals(dir2),"same-UID processes have separate asset roots");equal(false,data1.equals(data2),"same-UID processes have separate persistent plugin data");
            File root1=new File(dir1).getParentFile().getParentFile(),root2=new File(dir2).getParentFile().getParentFile();
            equal(true,new File(root1,"owners").isDirectory()&&new File(root2,"owners").isDirectory(),"ownership ledgers are isolated with process resources");
            first.runtime.evaluateCommand(id,"C17.save('marker','main')");second.runtime.evaluateCommand(id,"C17.save('marker','overlay')");
            equal("main",first.runtime.evaluateCommand(id,"C17.load('marker')"),"first process retains its own saved value");
            equal("overlay",second.runtime.evaluateCommand(id,"C17.load('marker')"),"child process cannot overwrite first process storage");
            first.stop();firstQueue.drain();restart.start();restartQueue.until(()->running(restart.runtime)==1);
            equal(dir1,restart.runtime.evaluateCommand(id,"String(C17.pluginDir)"),"stable process name reuses asset namespace across restarts");
            equal("main",restart.runtime.evaluateCommand(id,"C17.load('marker')"),"stable process name preserves saved state across restarts");
            repository.delete(id);restart.request();restartQueue.until(()->running(restart.runtime)==0&&!new File(dir1).exists());
            equal(true,new File(dir2).isDirectory(),"one process removal never deletes still-running sibling assets");
            equal(1,running(second.runtime),"sibling continues until its own settings notification");
            equal("overlay",second.runtime.evaluateCommand(id,"C17.load('marker')"),"sibling restoration data survives another process removal");
            second.request();secondQueue.until(()->running(second.runtime)==0&&!new File(dir2).exists());
            equal(false,new File(data1).exists()||new File(data2).exists(),"each stopped process removes only its own deleted-plugin data");
        }finally{first.stop();second.stop();restart.stop();}
    }
    private static void lifecycle(SettingsProvider provider,CustomPluginRepository repository,Owner host,JSONObject multi)throws Exception{
        repository.setEnabled("apps.only-b",false);events.clear();Queue qa=new Queue(),qb=new Queue();Installer ia=new Installer(),ib=new Installer();
        Owner a=new Owner(Files.createTempDirectory("c17-launcher-runtime-").toFile(),A),b=new Owner(Files.createTempDirectory("c17-sidebar-runtime-").toFile(),B);
        Transport ta=new Transport(provider,12001),tb=new Transport(provider,12002);a.resolver=ta;b.resolver=tb;
        CustomPluginAppRuntime.Session sa=new CustomPluginAppRuntime.Session(a,qa,ia),sb=new CustomPluginAppRuntime.Session(b,qb,ib);
        try{
            sa.runtime.rememberLoader(CustomPluginAppsCheck.class.getClassLoader());sb.runtime.rememberLoader(CustomPluginAppsCheck.class.getClassLoader());sa.start();sb.start();
            qa.until(()->running(sa.runtime)==1);qb.until(()->running(sb.runtime)==1);
            equal(1,ia.added.get(),"launcher installs plugin once");equal(1,ib.added.get(),"sidebar independently installs plugin once");
            equal(true,events.contains("start:"+A)&&events.contains("start:"+B),"same imported code runs in both actual package contexts");
            equal(0,qa.later.size()+qb.later.size(),"settled runtimes have no polling timers");
            repository.updateState("apps.shared","strength",72);ta.dispatchSettingsChange();tb.dispatchSettingsChange();
            qa.until(()->events.contains("state:"+A+":72"));qb.until(()->events.contains("state:"+B+":72"));
            equal(1,ia.added.get(),"state update reuses launcher hook");equal(1,ib.added.get(),"state update reuses sidebar hook");
            equal(0,ia.removed.get()+ib.removed.get(),"ordinary settings do not tear down the glass instance");
            String generation=plan(provider,12001,A).getJSONArray("runtimes").getJSONObject(0).getString("generation");
            qa.until(()->combined(multi,generation).contains("strength 72 "+A));qb.until(()->combined(multi,generation).contains("strength 72 "+B));
            equal(true,combined(multi,generation).contains("系统界面：system fixture"),"external status retains SystemUI text");
            equal(true,combined(multi,generation).contains("最近报告"),"external runtime reports are identified as last observations");
            equal(false,combined(multi,"old-installation").contains("strength 72"),"a new installation never inherits old process reports");
            // A stale read captured before disable must not re-enable effects after the notification.
            CountDownLatch captured=new CountDownLatch(1);ta.captured=captured;ta.release=new CountDownLatch(1);sa.request();qa.drain();
            equal(true,captured.await(3,TimeUnit.SECONDS),"captured old plan in flight");
            repository.setEnabled("apps.shared",false);ta.dispatchSettingsChange();tb.dispatchSettingsChange();ta.release.countDown();
            qa.until(()->running(sa.runtime)==0&&ia.removed.get()==1);qb.until(()->running(sb.runtime)==0&&ib.removed.get()==1);
            equal(1,ia.added.get(),"superseded plan never reinstalls the launcher hook");
            equal(true,events.contains("stop:"+A)&&events.contains("stop:"+B),"disable executes restoration in both applications");
            repository.setEnabled("apps.shared",true);ta.dispatchSettingsChange();tb.dispatchSettingsChange();
            qa.until(()->running(sa.runtime)==1);qb.until(()->running(sb.runtime)==1);
            equal(2,ia.added.get(),"re-enable creates a new launcher instance");
            host.preferences.values.put(StatusBarSettings.SAFE_MODE,true);ta.dispatchSettingsChange();tb.dispatchSettingsChange();
            qa.until(()->running(sa.runtime)==0);qb.until(()->running(sb.runtime)==0);
            equal(2,ia.removed.get(),"emergency safe mode unhooks launcher");equal(2,ib.removed.get(),"emergency safe mode unhooks sidebar");
            host.preferences.values.clear();ta.dispatchSettingsChange();qa.until(()->running(sa.runtime)==1);
            ta.fail=true;ta.dispatchSettingsChange();qa.until(()->running(sa.runtime)==0);
            equal(3,ia.removed.get(),"provider loss restores native app state");equal(1,qa.later.size(),"failure has one bounded retry rather than a timer loop");
            ta.fail=false;ta.dispatchSettingsChange();qa.until(()->running(sa.runtime)==1);
            qa.drain();Binder.setCallingIdentityForCheck(20000,91);
            JSONArray reports=new JSONArray(provider.call(CustomPluginAppProvider.STATUS,null,null).getString("reports"));
            equal(true,reports.length()>=2,"per-package reports are visible only to the C17 owner");
            for(int i=0;i<reports.length();i++){
                JSONObject report=reports.getJSONObject(i);equal(true,report.getInt("uid")==12001||report.getInt("uid")==12002,"report UID comes from Binder");
                JSONArray plugins=report.getJSONArray("plugins");for(int j=0;j<plugins.length();j++)equal(false,plugins.getJSONObject(j).has("stack")||plugins.getJSONObject(j).has("error"),"report stores counters rather than arbitrary exception payloads");
            }
            sa.stop();sb.stop();qa.drain();qb.drain();
            equal(0,ta.observers.size()+tb.observers.size(),"shutdown unregisters both observers");equal(0,qa.later.size()+qb.later.size(),"shutdown cancels retries");
            equal(ia.added.get(),ia.removed.get(),"all launcher hooks are removed exactly once");equal(ib.added.get(),ib.removed.get(),"all sidebar hooks are removed exactly once");
            Owner unobserved=new Owner(Files.createTempDirectory("c17-unobserved-runtime-").toFile(),A);Transport blocked=new Transport(provider,12001);blocked.failObservation=true;unobserved.resolver=blocked;
            Queue qc=new Queue();Installer ic=new Installer();CustomPluginAppRuntime.Session sc=new CustomPluginAppRuntime.Session(unobserved,qc,ic);
            try{
                sc.start();qc.until(()->!qc.later.isEmpty());equal(0,blocked.reads.get(),"missing disable observer prevents even fetching an enabled plan");equal(0,ic.added.get(),"missing disable observer cannot leave permanent effects");
                equal(1,qc.later.size(),"observation failures use one bounded retry");blocked.failObservation=false;sc.request();qc.until(()->running(sc.runtime)==1);
                equal(1,ic.added.get(),"observer recovery permits a normal start");
            }finally{sc.stop();qc.drain();}equal(ic.added.get(),ic.removed.get(),"recovered observation session cleans up normally");
        }finally{sa.stop();sb.stop();}
    }
    private static String combined(JSONObject descriptor,String generation){try{return CustomPluginAppProvider.withStatus(descriptor,generation,new JSONObject()
            .put("status",new JSONObject().put("glass_status","system fixture"))).getJSONObject("status").getString("glass_status");}
        catch(Exception invalid){throw new AssertionError(invalid);}}
}
