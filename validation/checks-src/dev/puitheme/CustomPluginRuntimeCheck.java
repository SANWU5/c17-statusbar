// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.api.XposedInterface;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs the actual unrestricted Rhino engine, removable hooks, Java proxies and isolated lifecycles. */
public final class CustomPluginRuntimeCheck {
    private static int checks;
    private static Class<?> observedPackagedEntry;
    private static final String HOST=Host.class.getName();
    private static final List<String> events=new ArrayList<>();
    /** Receives a class from the actual C17.loadDex loader; the host classpath has no plugin classes. */
    public static void observePackagedEntry(Class<?> entry){observedPackagedEntry=entry;}
    public interface Listener { int update(int value); }
    public interface Bridge { Object hook(Executable executable,XposedInterface.Hooker callback); void deoptimize(Executable executable);void log(String message); }
    public static class Host {
        static volatile CountDownLatch callbackEntered,callbackResume;
        public Host() { }
        public Host(int value) { }
        public int sum(int a,int b){return a+b;}
        public int one(){return 1;}
        public String echo(String value){return value;}
        public Object object(){return new Object();}
        public static void record(String value){events.add(value);}
        public static XposedInterface.Hooker callback(){return chain->((Number)chain.proceed()).intValue()+30;}
        public static void install(Context context,Bridge bridge,String state)throws Exception{
            bridge.hook(Host.class.getDeclaredMethod("one"),callback());bridge.log(state);
        }
        public static void release(){events.add("released");}
        public static void blocking()throws InterruptedException {
            callbackEntered.countDown();if(!callbackResume.await(3,TimeUnit.SECONDS))throw new AssertionError("raw Java fixture was not resumed");
        }
    }
    private interface Original {Object run(Object receiver,Object[] args)throws Throwable;}
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(why+": "+expected+" != "+actual);}
    private static final class Owner extends Context {
        final File root;
        final Map<String,byte[]> assets=new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger failedReads=new AtomicInteger();
        volatile Thread assetThread;
        volatile CountDownLatch opened,gate;
        Owner(File root){this.root=root;}
        @Override public File getFilesDir(){return root;}
        @Override public android.content.ContentResolver getContentResolver(){return new android.content.ContentResolver(){
            @Override public java.io.InputStream openInputStream(android.net.Uri uri)throws java.io.FileNotFoundException{
                assetThread=Thread.currentThread();if(opened!=null)opened.countDown();
                if(gate!=null)try{gate.await(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.FileNotFoundException("Interrupted fixture");}
                if(failedReads.getAndDecrement()>0)throw new java.io.FileNotFoundException("Transient provider fixture");
                byte[] bytes=assets.get(uri.toString());if(bytes==null)throw new java.io.FileNotFoundException(uri.toString());return new java.io.ByteArrayInputStream(bytes);
            }
        };}
    }
    private static final class Dispatcher extends Handler {
        final ConcurrentLinkedQueue<Runnable> ready=new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Delayed> delayed=new ConcurrentLinkedQueue<>();
        static final class Delayed {final Runnable task;final long at;Delayed(Runnable task,long delay){this.task=task;at=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(delay);}}
        Dispatcher(){super(Looper.getMainLooper());}
        @Override public boolean post(Runnable task){ready.add(task);return true;}
        @Override public boolean postDelayed(Runnable task,long millis){delayed.add(new Delayed(task,millis));return true;}
        @Override public void removeCallbacks(Runnable task){ready.remove(task);delayed.removeIf(item->item.task==task);}
        void drain(){
            for(Delayed item:new ArrayList<>(delayed))if(item.at<=System.nanoTime() && delayed.remove(item))ready.add(item.task);
            Runnable task;int budget=10000;while((task=ready.poll())!=null){if(--budget==0)throw new AssertionError("Unbounded main dispatch");task.run();}
        }
        void until(BooleanSupplier condition)throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
            do{drain();if(condition.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<end);
            throw new AssertionError("Timed out waiting for asynchronous runtime");
        }
    }
    private static final class Site {
        final Executable executable; final XposedInterface.Hooker callback;
        Site(Executable executable,XposedInterface.Hooker callback){this.executable=executable;this.callback=callback;}
    }
    private static final class Framework {
        final List<Site> hooks=new ArrayList<>();
        final Dispatcher dispatcher=new Dispatcher();
        final CustomPluginRuntime runtime=new CustomPluginRuntime(dispatcher);
        final File root;
        final Owner owner;
        final Map<String,File> frameworkFiles=new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger frameworkReads=new AtomicInteger();
        volatile Thread frameworkThread;
        int removed,deoptimized,originalCalls;
        boolean refuseRemoval,refuseNextHook;
        Framework()throws Exception{this(true);}
        Framework(boolean rememberHost)throws Exception{
            root=Files.createTempDirectory("c17-runtime-v2-").toFile();
            owner=new Owner(root);
            runtime.configure(owner,new CustomPluginRuntime.Install(){
                public XposedInterface.HookHandle hook(Executable executable,XposedInterface.Hooker callback){
                    if(refuseNextHook){refuseNextHook=false;throw new IllegalStateException("Framework refused optional hook");}
                    Site site=new Site(executable,callback);hooks.add(site);
                    return (XposedInterface.HookHandle)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},(owner,method,args)->{
                        if(method.getName().equals("unhook")){if(refuseRemoval)throw new IllegalStateException("test removal failure");removed++;hooks.remove(site);return null;}
                        if(method.getName().equals("getExecutable"))return executable;throw new AssertionError(method.getName());
                    });
                }
                public void deoptimize(Executable executable){deoptimized++;}
                @Override public ParcelFileDescriptor openFrameworkFile(String name)throws FileNotFoundException{
                    frameworkThread=Thread.currentThread();
                    frameworkReads.incrementAndGet();
                    File file=frameworkFiles.get(name);
                    if(file==null)throw new FileNotFoundException(name);
                    return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                }
            },null);
            if(rememberHost)runtime.rememberLoader(Host.class.getClassLoader());runtime.setSafeMode(false);
        }
        JSONObject entry(String id,String source)throws Exception{return new JSONObject().put("id",id).put("versionCode",1).put("source",source).put("state",new JSONObject().put("answer",23)).put("baseDir",root.getPath());}
        void plan(JSONObject...entries)throws Exception{JSONArray array=new JSONArray();for(JSONObject entry:entries)array.put(entry);runtime.accept(new JSONObject().put("revision",1).put("runtimes",array));}
        Object call(String name,Class<?>[] types,Object[] args,Original original)throws Throwable{
            Method method=Host.class.getDeclaredMethod(name,types);return call(method,new Host(),args,original);
        }
        Object call(Executable executable,Object receiver,Object[] args,Original original)throws Throwable{
            List<Site> sites=new ArrayList<>();for(Site site:hooks)if(site.executable.equals(executable))sites.add(site);
            return next(sites,0,executable,receiver,args,original);
        }
        Object next(List<Site> sites,int index,Executable executable,Object receiver,Object[] args,Original original)throws Throwable{
            if(index==sites.size()){originalCalls++;return original.run(receiver,args);}
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(owner,method,input)->{
                switch(method.getName()){
                    case "getExecutable":return executable;
                    case "getThisObject":return receiver;
                    case "getArgs":return Arrays.asList(args);
                    case "getArg":return args[(Integer)input[0]];
                    case "proceed":return next(sites,index+1,executable,receiver,input==null||input.length==0?args:(Object[])input[0],original);
                    case "proceedWith":return next(sites,index+1,executable,input[0],input.length==1?args:(Object[])input[1],original);
                    default:throw new AssertionError(method.getName());
                }
            });
            return sites.get(index).callback.intercept(chain);
        }
        String eval(String id,String source)throws Exception{return runtime.evaluateCommand(id,source);}
        int loaded(){Bundle out=new Bundle();runtime.health(out);return out.getInt("custom_plugin_runtime_loaded");}
    }
    private static String hook(String name,String types,String body){return "C17.hook('"+HOST+"','"+name+"',"+types+",function(c){"+body+"});";}
    public static void main(String[] ignored)throws Throwable{
        if(ignored.length>0 && "--native-budget".equals(ignored[0])) { portableAllocationBudget(); return; }
        Framework f=new Framework();
        equal(null,f.runtime.runningState("all",1),"missing plugin cannot claim host materials");
        f.plan(f.entry("all","importClass(java.lang.StringBuilder);var live = new StringBuilder('java'); var value=C17.state.answer; var clean=[];"));
        equal(23,f.runtime.runningState("all",1).getInt("answer"),"only running plugin exposes an ownership snapshot");
        f.runtime.runningState("all",1).put("answer",99);
        equal(23,f.runtime.runningState("all",1).getInt("answer"),"ownership snapshot cannot mutate plugin configuration");
        equal(null,f.runtime.runningState("all",2),"older glass version keeps its compatible host material behavior");
        equal("java23",f.eval("all","String(live)+value"),"unrestricted Java class creation and structured plugin state");
        equal("true",f.eval("all","typeof Packages !== 'undefined' && typeof JavaAdapter !== 'undefined' && typeof importClass === 'function'"),"LiveConnect aliases available");
        equal("true",f.eval("all","C17.moduleClassLoader != null && C17.context != null && C17.pluginDir != null"),"Android context and module loader exposed");
        equal("2",f.eval("all","String(C17.host.apiVersion)"),"runtime exposes its actual API contract");
        equal(Integer.toString(CustomPluginSupport.versionCode()),f.eval("all","String(C17.host.versionCode)"),"runtime capability probe uses the current host identity");
        equal("true",f.eval("all","C17.hasFeature('runtime.dex') && C17.hasFeature('ui.native-settings') && !C17.hasFeature('future.unavailable')"),"capability detection distinguishes available generic contracts from future contracts");
        equal("true",f.eval("all","C17.host.runtimePackage === String(C17.context.getPackageName())"),"runtime reports the actual executing package instead of a declared scope");
        equal("java.lang.String",f.eval("all","String(C17.findClass('java.lang.String').getName())"),"arbitrary class namespace");
        equal("[I",f.eval("all","String(C17.findClass('int[]').getName())"),"primitive array class resolution");
        f.eval("all","var listener=C17.proxy(C17.findClass('"+Listener.class.getName()+"'),{update:function(v){return v+4;}});var proxyValue=listener.update(6);");
        equal("10",f.eval("all","proxyValue"),"Android-safe Java interface proxy callback");
        f.eval("all","var runs=0;C17.runOnMain(function(){runs++});");
        equal("0",f.eval("all","runs"),"main dispatch is asynchronous");f.dispatcher.drain();equal("1",f.eval("all","runs"),"main callback delivered");
        f.eval("all","var cancelled=C17.runOnMain(function(){runs+=10});cancelled();");f.dispatcher.drain();
        equal("1",f.eval("all","runs"),"cancelled main callback stays inert even if already queued");
        equal(1,f.loaded(),"cancel does not stop plugin");
        f.eval("all","C17.save('config',{nested:[1,true,'x']});");
        equal("{\"nested\":[1,true,\"x\"]}",f.eval("all","JSON.stringify(C17.load('config'))"),"own JSON storage round trip");
        Files.write(new File(f.root,"helper.js").toPath(),"exports.value=17;module.exports.count=1;".getBytes(StandardCharsets.UTF_8));
        equal("17",f.eval("all","C17.require('helper.js').value"),"file-backed module execution");
        equal("true",f.eval("all","C17.require('helper.js') === C17.require('helper.js')"),"module cache shared only within plugin");
        File moduleDir=new File(f.root,"modules");moduleDir.mkdir();
        Files.write(new File(moduleDir,"one.js").toPath(),"module.exports=require('./two');".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(moduleDir,"two.js").toPath(),"module.exports={value:18};".getBytes(StandardCharsets.UTF_8));
        equal("18",f.eval("all","C17.require('modules/one').value"),"nested CommonJS relative paths and omitted extension");
        f.plan(f.entry("parameters",hook("sum","['int','int']","c.args[0]=7;return c.proceed()+5;")));
        equal(15,f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,3},(receiver,args)->(Integer)args[0]+(Integer)args[1]),"change native args and transform result");
        equal(1,f.originalCalls,"native method once");
        f.plan(f.entry("once",hook("one","[]","var a=c.proceed();var b=c.proceed();return a+b;")));
        equal(14,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->7),"repeat proceed returns cached value");equal(2,f.originalCalls,"repeat proceed never repeats native side effects");
        f.plan(f.entry("fault",hook("one","[]","var v=c.proceed();throw new Error('after proceed');")));
        equal(9,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->9),"plugin error falls back to already computed native result");equal(3,f.originalCalls,"fault fallback does not repeat native");equal(0,f.loaded(),"faulty plugin isolated");
        f.plan(f.entry("before",hook("one","[]","throw new Error('before proceed');")),f.entry("healthy",hook("echo","['java.lang.String']","return 'independent';")));
        equal(8,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->8),"fault before proceed resumes native");equal(4,f.originalCalls,"fault before proceeds once");equal(1,f.loaded(),"healthy plugin survives sibling failure");
        equal("independent",f.call("echo",new Class<?>[]{String.class},new Object[]{"native"},(receiver,args)->args[0]),"sibling hook remains live");equal(4,f.originalCalls,"return replacement skips native");
        f.plan(f.entry("nativeThrow",hook("one","[]","return c.proceed();")));
        IllegalStateException originalFailure=new IllegalStateException("native identity");
        try{f.call("one",new Class<?>[0],new Object[0],(receiver,args)->{throw originalFailure;});throw new AssertionError("native failure swallowed");}
        catch(IllegalStateException failure){equal(originalFailure,failure,"native exception identity preserved");}
        equal(5,f.originalCalls,"native throw once");
        f.plan(f.entry("object",hook("object","[]","return new java.util.ArrayList();")));
        equal(true,f.call("object",new Class<?>[0],new Object[0],(receiver,args)->null) instanceof ArrayList,"arbitrary Java object result");
        events.clear();
        f.plan(f.entry("cleanup","var Host=C17.findClass('"+HOST+"');C17.onCleanup(function(){Host.getMethod('record',java.lang.String).invoke(null,'first')});C17.onCleanup(function(){Host.getMethod('record',java.lang.String).invoke(null,'second')});"));
        f.runtime.setSafeMode(true);equal(Arrays.asList("second","first"),events,"cleanup LIFO in safety mode");equal(0,f.loaded(),"safe mode unloads scope");
        equal(null,f.runtime.runningState("cleanup",1),"safe mode immediately releases host material ownership");
        try{f.eval("cleanup","1");throw new AssertionError("paused command accepted");}catch(IllegalStateException expected){checks++;}
        f.runtime.setSafeMode(false);equal(1,f.loaded(),"leave safety mode starts fresh scope");
        equal(true,f.runtime.runningState("cleanup",1)!=null,"restarted scope can claim materials again");
        f.plan(f.entry("constructors","C17.hookAllConstructors('"+HOST+"',function(c){return c.proceed();});"));
        equal(2,f.hooks.size(),"all constructor overloads supported");
        Object made=new Host();equal(made,f.call(Host.class.getConstructor(),null,new Object[0],(receiver,args)->made),"constructor native object passes through");
        f.plan(f.entry("exact","var method=C17.findClass('"+HOST+"').getDeclaredMethod('one');C17.deoptimize(method);C17.hook(method,function(c){return 42});"));
        equal(1,f.deoptimized,"native deoptimization exposed");equal(42,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->1),"exact reflected method hook");
        f.plan(f.entry("bridge","var Host=C17.findClass('"+HOST+"');var bridge=C17.proxy(C17.findClass('"+Bridge.class.getName()+"'),{hook:function(site,callback){return C17.hook(site,callback)},deoptimize:function(site){C17.deoptimize(site)},log:function(text){Host.getDeclaredMethod('record',java.lang.String).invoke(null,[text]);}});var methods=Host.getDeclaredMethods();var install,release;for(var i=0;i<methods.length;i++){if(String(methods[i].getName())==='install')install=methods[i];if(String(methods[i].getName())==='release')release=methods[i];}install.invoke(null,[C17.context,bridge,'bridge-ready']);C17.onCleanup(function(){release.invoke(null,[])});"));
        equal("bridge-ready",events.get(events.size()-1),"Method.invoke null receiver plus JS argument array matches packaged entry");
        equal(37,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->7),"Java Hooker receives genuine cached libxposed Chain through proxy Bridge");
        f.plan();equal("released",events.get(events.size()-1),"packaged entry release reflection on cleanup");
        Framework lateLoader=new Framework();
        ClassLoader child=new ClassLoader(Listener.class.getClassLoader()){};
        Object generated=Proxy.newProxyInstance(child,new Class<?>[]{Listener.class},(object,method,args)->null);
        String generatedName=generated.getClass().getName();
        lateLoader.plan(lateLoader.entry("pending","var loaderEvents=0;C17.onClassLoader(function(loader){loaderEvents++});C17.hookAll('"+generatedName+"','update',function(c){return 5});"));
        equal(0,lateLoader.hooks.size(),"unknown loader hook waits without failing plugin");
        lateLoader.runtime.rememberLoader(child);equal(1,lateLoader.hooks.size(),"new class loader installs pending hook");
        equal("2",lateLoader.eval("pending","loaderEvents"),"loader listener sees existing and later loader exactly once");
        lateLoader.runtime.rememberLoader(child);equal(1,lateLoader.hooks.size(),"same loader never duplicates hook");lateLoader.runtime.stop();
        Framework assets=new Framework();byte[] assetBytes="exports.value=73;".getBytes(StandardCharsets.UTF_8);
        String assetHash=hex(java.security.MessageDigest.getInstance("SHA-256").digest(assetBytes));
        assets.owner.assets.put("content://dev.puitheme.iosstatusbar.settings/custom-plugins/assets/assets/1/nested/lib.js",assetBytes);
        JSONObject assetEntry=assets.entry("assets","var assetValue=C17.require('nested/lib.js').value;")
                .put("baseDir","/data/user_de/0/app/private/unreadable")
                .put("assets",new JSONArray().put(new JSONObject().put("path","nested/lib.js").put("bytes",assetBytes.length).put("sha256",assetHash)));
        assets.plan(assetEntry);assets.dispatcher.until(()->assets.loaded()==1);
        equal("73",assets.eval("assets","assetValue"),"cross-UID provider assets copied and verified before execution");
        equal(false,Thread.currentThread()==assets.owner.assetThread,"provider IO and asset verification run off the main thread");
        File copied=new File(assets.root,"c17_plugins/assets/1/nested/lib.js");equal(true,copied.isFile(),"pluginDir uses SystemUI-owned versioned directory");
        assets.plan(new JSONObject(assetEntry.toString()).put("versionCode",2).put("source",""));equal(0,assets.loaded(),"missing asset disables updated plugin before execution");assets.runtime.stop();
        frameworkAssetChecks(assetBytes,assetHash);
        Framework retained=new Framework();
        retained.plan(retained.entry("guard","var Host=C17.findClass('"+HOST+"');C17.hookNative(Host.getDeclaredMethod('one'),Host.getDeclaredMethod('callback').invoke(null,[]),true);"));
        Bundle nativeHealth=new Bundle();retained.runtime.health(nativeHealth);equal(1,nativeHealth.getInt("custom_plugin_runtime_hooks"),"native hook handles count toward runtime hook health");
        retained.plan();equal(1,retained.hooks.size(),"explicit process-lifetime native guard survives scope unload");
        equal(37,retained.call("one",new Class<?>[0],new Object[0],(receiver,args)->7),"retained guard runs Java without closed JS callback");retained.runtime.stop();
        JSONObject same=f.entry("same",hook("one","[]","return 44;"));f.plan(same);int count=f.hooks.size();f.plan(new JSONObject(same.toString()));equal(count,f.hooks.size(),"unchanged plan retains registration");
        f.runtime.accept(new JSONObject().put("runtimes",new JSONArray().put(new JSONObject().put("id","bad"))));equal(count,f.hooks.size(),"invalid complete plan retains previous plugin");
        f.plan(f.entry("scopeA","var privateValue=1;"),f.entry("scopeB","var privateValue=2;"));equal("1",f.eval("scopeA","privateValue"),"independent scope A");equal("2",f.eval("scopeB","privateValue"),"independent scope B");
        f.plan(f.entry("late",hook("one","[]","return 55;")));Site late=f.hooks.get(0);f.refuseRemoval=true;f.plan();equal(1,f.hooks.size(),"removal failure retains inert framework callback");
        final int[] calls={0};
        XposedInterface.Chain nativeChain=(XposedInterface.Chain)Proxy.newProxyInstance(Host.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(owner,method,args)->{
            if(method.getName().equals("proceed")){calls[0]++;return 7;}throw new AssertionError("inactive callback accessed "+method.getName());
        });
        equal(7,late.callback.intercept(nativeChain),"late callback bypasses closed JS scope");equal(1,calls[0],"late fallback native once");
        f.runtime.stop();equal(0,f.loaded(),"stop releases scopes");
        stateAndLoaderChecks();
        coalescedSafeModeChecks();
        closedPluginReplacementChecks();
        dexParentChecks();
        assetLifecycleChecks();
        nativeContinuationChecks();
        portableJavaChecks();
        if(Arrays.asList(ignored).contains("--free-edit"))packagedEntryChecks();
        startupFailureChecks();
        formActionChecks();
        standardTemplateChecks();
        installedStorageChecks();
        System.out.println("CustomPluginRuntimeCheck "+checks+" checks passed");
    }

    private static void frameworkAssetChecks(byte[] bytes,String hash)throws Exception {
        String fileName="c17-plugin-asset-"+hash;
        Framework cold=new Framework();
        JSONObject primary=cold.entry("cold_asset","var value=C17.require('lib.js').value;"+hook("one","[]","return value;"))
                .put("assets",new JSONArray().put(new JSONObject().put("path","lib.js").put("bytes",bytes.length).put("sha256",hash)));
        JSONObject shared=new JSONObject(primary.toString());
        shared.getJSONArray("assets").getJSONObject(0).put("frameworkFile",fileName);
        File remote=new File(cold.root,"framework-blob");
        Files.write(remote.toPath(),bytes);
        cold.frameworkFiles.put(fileName,remote);
        cold.plan(shared);
        cold.dispatcher.until(()->cold.loaded()==1);
        equal("73",cold.eval("cold_asset","value"),"cold boot runs the plugin with only the shared asset channel");
        equal(null,cold.owner.assetThread,"shared asset never contacts the unavailable settings application");
        equal(false,Thread.currentThread()==cold.frameworkThread,"shared asset preparation stays off the main thread");
        Site installed=cold.hooks.get(0);
        cold.plan(primary);
        equal(1,cold.hooks.size(),"switch to the live provider keeps a single plugin hook");
        equal(installed,cold.hooks.get(0),"asset transport change preserves the original hook");
        cold.runtime.stop();

        Framework damaged=new Framework();
        File bad=new File(damaged.root,"damaged-blob");
        byte[] wrong=bytes.clone();wrong[0]^=1;
        Files.write(bad.toPath(),wrong);
        damaged.frameworkFiles.put(fileName,bad);
        damaged.plan(shared);
        damaged.dispatcher.until(()->damaged.runtime.status().optJSONObject(0).optInt("assetFailures")>0);
        equal(0,damaged.loaded(),"wrong shared asset digest cannot start plugin code");
        equal(false,new File(damaged.root,"c17_plugins/cold_asset/1/lib.js").exists(),"wrong digest never reaches the executable cache");
        damaged.owner.assets.put("content://dev.puitheme.iosstatusbar.settings/custom-plugins/assets/cold_asset/1/lib.js",bytes);
        damaged.dispatcher.until(()->damaged.loaded()==1);
        equal("73",damaged.eval("cold_asset","value"),"valid live provider recovers a broken shared blob");
        damaged.runtime.stop();

        Framework mismatched=new Framework();
        JSONObject invalid=new JSONObject(shared.toString());
        invalid.getJSONArray("assets").getJSONObject(0).put("frameworkFile","arbitrary-framework-file");
        mismatched.frameworkFiles.put("arbitrary-framework-file",remote);
        mismatched.plan(invalid);
        mismatched.dispatcher.until(()->mismatched.runtime.status().optJSONObject(0).optInt("assetFailures")>0);
        equal(0,mismatched.frameworkReads.get(),"a recipe cannot read a file outside its content-addressed asset");
        mismatched.runtime.stop();
    }

    private static void standardTemplateChecks()throws Exception {
        File directory=new File("tools/custom-plugins/templates/standard-runtime");
        String main=new String(Files.readAllBytes(new File(directory,"main.js").toPath()),StandardCharsets.UTF_8);
        String source=new String(Files.readAllBytes(new File(directory,"runtime.js").toPath()),StandardCharsets.UTF_8);
        JSONObject descriptor=CustomPluginEngine.evaluate(main,new JSONObject());String id=descriptor.getString("id");
        Framework f=new Framework();JSONObject entry=f.entry(id,source).put("form",CustomPluginSchema.form(descriptor))
                .put("state",new JSONObject().put("record_changes",false).put("note","保存的备注"));
        try {
            f.plan(entry);JSONObject initial=new JSONObject(f.runtime.formStatusCommand(id));String instance=initial.getString("instanceId");
            equal("尚无记录",initial.getJSONObject("status").getString("journal_status"),"template does not save on startup");
            JSONObject saved=new JSONObject(f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"save_record"));
            equal(true,saved.getBoolean("ok"),"template action runs in actual host engine");
            equal("保存的备注",new JSONObject(saved.getJSONObject("status").getString("journal_status")).getString("note"),"manual save uses actual selected state");
            entry.put("state",new JSONObject().put("record_changes",true).put("note",String.join("",Collections.nCopies(4096,"\""))));f.plan(entry);
            JSONObject changed=new JSONObject(f.runtime.formStatusCommand(id));
            equal(instance,changed.getString("instanceId"),"state update reuses template runtime");
            equal(true,changed.getJSONObject("status").getString("journal_status").length()<=4096,"long escaped note fits status limit");
            equal("4096",f.eval(id,"String(JSON.parse(C17.load('last-record','{}')).note.length)"),"status truncation preserves the entire saved record");
            f.plan();equal(0,f.loaded(),"disable removes runtime callbacks");f.plan(entry);
            JSONObject resumed=new JSONObject(f.runtime.formStatusCommand(id));
            equal(changed.getJSONObject("status").getString("journal_status"),resumed.getJSONObject("status").getString("journal_status"),"reenable retains recorded data");
            JSONObject cleared=new JSONObject(f.runtime.actionCommand(id,resumed.getString("instanceId"),java.util.UUID.randomUUID().toString(),"clear_record"));
            equal(true,cleared.getBoolean("ok"),"clear action succeeds");equal("尚无记录",cleared.getJSONObject("status").getString("journal_status"),"clear updates visible status");
        } finally { f.runtime.stop(); }
    }

    private static void installedStorageChecks()throws Exception{
        Framework f=new Framework();JSONObject entry=f.entry("test.storage","var previous=C17.load('proof','empty');");
        JSONObject installed=new JSONObject().put("id","test.storage").put("installedAt",1).put("preferences",new JSONArray());
        JSONArray inventory=new JSONArray().put(installed);
        f.runtime.accept(new JSONObject().put("runtimes",new JSONArray().put(entry)).put("installedPlugins",inventory));f.dispatcher.drain();
        equal(1,f.loaded(),"complete installed inventory starts its matching runtime");
        f.eval("test.storage","C17.save('proof','persisted')");
        File data=new File(f.root,"c17_plugins/data/"+hex(java.security.MessageDigest.getInstance("SHA-256").digest("test.storage".getBytes(StandardCharsets.UTF_8))));
        equal(true,data.isDirectory()&&data.list().length>0,"runtime stores its values under its owned identity");
        f.runtime.accept(new JSONObject().put("runtimes",new JSONArray()).put("installedPlugins",inventory));f.dispatcher.drain();
        equal(0,f.loaded(),"disable unloads every executable callback");equal(true,data.isDirectory(),"disable preserves dormant configuration for deliberate reenable");
        f.runtime.accept(new JSONObject().put("runtimes",new JSONArray()).put("installedPlugins",new JSONArray()));f.dispatcher.drain();
        equal(false,data.exists(),"deleting an already disabled plugin removes its persisted runtime values");
        installed.put("installedAt",2);f.runtime.accept(new JSONObject().put("runtimes",new JSONArray().put(entry)).put("installedPlugins",inventory));f.dispatcher.drain();
        equal("empty",f.eval("test.storage","previous"),"fresh installation cannot revive the removed plugin's configuration");
        f.runtime.accept(new JSONObject().put("runtimes",new JSONArray().put(entry)).put("installedPlugins",new JSONArray()));f.dispatcher.drain();
        equal(1,f.loaded(),"incomplete inventory cannot clear a running plugin or its data");f.runtime.stop();
    }

    private static void formActionChecks()throws Exception{
        Framework f=new Framework();
        JSONObject form=new JSONObject().put("actions",new JSONArray().put("capture").put("fail")).put("statuses",new JSONArray().put("saved_at"));
        String source="var captures=0,failures=0,glyphBytes=0;C17.publishStatus({saved_at:'not saved'});"
                +"C17.onAction('capture',function(request){if(glyphBytes)return {icon:new Array(glyphBytes+1).join('a')};captures++;C17.save('capture-proof',{n:captures});C17.publishStatus({saved_at:'saved-'+captures});return {message:'captured-'+captures,request:request.requestId};});"
                +"C17.onAction('fail',function(){failures++;throw new Error('capture refused');});";
        JSONObject entry=f.entry("form",source).put("form",form);f.plan(entry);
        JSONObject initial=new JSONObject(f.runtime.formStatusCommand("form"));String instance=initial.getString("instanceId");
        equal(true,instance.matches("[a-f0-9-]{36}"),"each loaded form has a random running-instance identity");
        equal("not saved",initial.getJSONObject("status").getString("saved_at"),"plugin publishes initial read-only status independently from settings");
        equal(2,initial.getJSONArray("actions").length(),"only registered declared actions are advertised to the form");
        String first=java.util.UUID.randomUUID().toString();String response=f.runtime.actionCommand("form",instance,first,"capture");JSONObject captured=new JSONObject(response);
        equal(true,captured.getBoolean("ok"),"an explicit form action invokes its runtime callback");
        equal(first,captured.getJSONObject("value").getString("request"),"callback receives the exact once-only request identity");
        equal("saved-1",captured.getJSONObject("status").getString("saved_at"),"callback result includes the newly published read-only timestamp value");
        equal(response,f.runtime.actionCommand("form",instance,first,"capture"),"same request returns the recorded result rather than invoking the callback again");
        equal("1",f.eval("form","captures"),"report retries cannot repeat the action side effect");
        JSONObject second=new JSONObject(f.runtime.actionCommand("form",instance,java.util.UUID.randomUUID().toString(),"capture"));
        equal("saved-2",second.getJSONObject("status").getString("saved_at"),"a repeated user click has a new request and can overwrite an earlier snapshot");
        equal("23",f.eval("form","C17.state.answer"),"actions never become persistent settings or state counters");
        String failed=java.util.UUID.randomUUID().toString();String failedResult=f.runtime.actionCommand("form",instance,failed,"fail");
        equal(false,new JSONObject(failedResult).getBoolean("ok"),"callback failure is returned to the form");
        equal(failedResult,f.runtime.actionCommand("form",instance,failed,"fail"),"failed action requests also remain consumed");equal("1",f.eval("form","failures"),"a failed callback is never replayed by a result retry");
        boolean denied=false;try{f.runtime.actionCommand("form",instance,first,"fail");}catch(IllegalArgumentException expected){denied=true;}equal(true,denied,"a request identity cannot be rebound to another action");
        denied=false;try{f.runtime.actionCommand("form",java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString(),"capture");}catch(IllegalStateException expected){denied=true;}equal(true,denied,"an instance mismatch is refused before callback execution");
        denied=false;try{f.runtime.actionCommand("form",instance,"persistent-setting-value","capture");}catch(IllegalArgumentException expected){denied=true;}equal(true,denied,"a static button setting cannot masquerade as an action request");
        JSONObject stateOnly=new JSONObject(entry.toString()).put("state",new JSONObject().put("answer",55));f.plan(stateOnly);
        equal(instance,new JSONObject(f.runtime.formStatusCommand("form")).getString("instanceId"),"ordinary configuration updates retain the same live instance");equal("2",f.eval("form","captures"),"configuration update does not replay a previous action");
        JSONObject replacement=new JSONObject(entry.toString()).put("source",source+"\n// replacement");f.plan(replacement);
        String next=new JSONObject(f.runtime.formStatusCommand("form")).getString("instanceId");equal(false,instance.equals(next),"plugin replacement always changes the running instance identity");equal("0",f.eval("form","captures"),"a replacement does not inherit queued form clicks");
        denied=false;try{f.runtime.actionCommand("form",instance,first,"capture");}catch(IllegalStateException expected){denied=true;}equal(true,denied,"a command for the previous load never runs in its replacement");
        equal("2",f.eval("form","C17.load('capture-proof',{}).n"),"reloading the runtime does not overwrite the last action's stored data");
        f.eval("form","glyphBytes=96000");
        JSONObject glyphResult=new JSONObject(f.runtime.actionCommand("form",next,java.util.UUID.randomUUID().toString(),"capture"));
        equal(true,glyphResult.getBoolean("ok"),"native icon catalogs above the former text-only limit remain readable");
        equal(96000,glyphResult.getJSONObject("value").getString("icon").length(),"glyph catalog transport retains its complete payload");
        f.eval("form","glyphBytes=262144");
        equal(false,new JSONObject(f.runtime.actionCommand("form",next,java.util.UUID.randomUUID().toString(),"capture")).getBoolean("ok"),"oversized catalogs remain bounded before cross-process delivery");
        f.runtime.setSafeMode(true);equal(0,f.loaded(),"safety mode still closes runtime instances");
        denied=false;try{f.runtime.formStatusCommand("form");}catch(IllegalStateException expected){denied=true;}equal(true,denied,"paused plugins have no executing form channel");
        denied=false;try{f.runtime.actionCommand("form",next,java.util.UUID.randomUUID().toString(),"capture");}catch(IllegalStateException expected){denied=true;}equal(true,denied,"form actions cannot bypass safety mode");
        f.runtime.setSafeMode(false);String resumed=new JSONObject(f.runtime.formStatusCommand("form")).getString("instanceId");equal(false,next.equals(resumed),"leaving safety mode starts a new identity rather than reviving an old command target");equal("0",f.eval("form","captures"),"safety resume never replays action side effects");f.runtime.stop();
    }

    /** This callback's signature and transport refer only to JDK types. */
    public static final class PortableCallback {
        static String mode="twice";
        static Object replacement;
        static InvocationHandler escaped;
        static Throwable offThread;
        public static InvocationHandler create(){return (receiver,method,frame)->{
            Object[] arguments=(Object[])frame[1];InvocationHandler next=(InvocationHandler)frame[2];escaped=next;
            if(!Objects.equals(receiver,frame[0])||method!=frame[3])throw new AssertionError("Portable frame metadata");
            if(org.mozilla.javascript.Context.getCurrentContext()!=null)throw new AssertionError("Native Java hook reentered Rhino");
            if(mode.equals("twice"))return (Integer)next.invoke(receiver,null,null)+(Integer)next.invoke(receiver,null,null);
            if(mode.equals("args"))return next.invoke(receiver,null,new Object[]{7,8});
            if(mode.equals("receiver"))return next.invoke(replacement,null,null);
            if(mode.equals("invalid")){
                try{next.invoke(receiver,null,new Object[0]);throw new AssertionError("Invalid arguments accepted");}
                catch(IllegalArgumentException expected){}
            }
            if(mode.equals("thread")){
                Thread other=new Thread(()->{try{next.invoke(receiver,null,null);}catch(Throwable expected){offThread=expected;}});
                other.start();other.join(1500);if(other.isAlive())throw new AssertionError("Off-thread callback hung");
            }
            return next.invoke(receiver,null,null);
        };}
    }
    /** Fixed native callback, excluding fixture construction and JavaScript startup from the allocation sample. */
    private static void portableAllocationBudget()throws Throwable {
        Framework f=new Framework();
        try {
            PortableCallback.mode="normal";
            f.plan(f.entry("budget","var h=C17.findClass('"+HOST+"');var c=C17.findClass('"+PortableCallback.class.getName()
                    +"').getMethod('create').invoke(null,[]);C17.hookJava(h.getDeclaredMethod('sum',java.lang.Integer.TYPE,java.lang.Integer.TYPE),c);"));
            Executable site=Host.class.getDeclaredMethod("sum",int.class,int.class);
            Object receiver=new Host();List<Object> arguments=Arrays.asList(1,2);
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(Host.class.getClassLoader(),
                    new Class<?>[]{XposedInterface.Chain.class},(self,method,args)->{
                        switch(method.getName()) {
                            case "getThisObject":return receiver;
                            case "getArgs":return arguments;
                            case "getExecutable":return site;
                            case "proceed":return 3;
                            default:throw new AssertionError(method.getName());
                        }
                    });
            XposedInterface.Hooker callback=f.hooks.get(0).callback;
            for(int i=0;i<20000;i++)callback.intercept(chain);
            com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
            if(!bean.isThreadAllocatedMemorySupported())throw new AssertionError("Allocation accounting unavailable");
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);
            for(int i=0;i<10000;i++)if(!Integer.valueOf(3).equals(callback.intercept(chain)))throw new AssertionError("Native result changed");
            long bytes=bean.getThreadAllocatedBytes(thread)-before;
            System.out.println("Native callback bytes/call="+(bytes/10000.0));
            if(bytes>120L*10000L)throw new AssertionError("Stable native bridge exceeds 120 bytes/callback: "+bytes);
            try{PortableCallback.escaped.invoke(receiver,null,null);throw new AssertionError("Expired callback accepted");}
            catch(IllegalStateException expected){}
        }finally{f.runtime.stop();PortableCallback.escaped=null;}
    }
    private static void portableJavaChecks()throws Throwable {
        Framework f=new Framework();String callback=PortableCallback.class.getName();
        String sum="var host=C17.findClass('"+HOST+"');var callback=C17.findClass('"+callback+"').getMethod('create').invoke(null,[]);"
                +"var portableHandle=C17.hookJava(host.getDeclaredMethod('sum',java.lang.Integer.TYPE,java.lang.Integer.TYPE),callback);";
        try{
            PortableCallback.mode="twice";f.plan(f.entry("portable",sum));
            equal(10,f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{2,3},(owner,args)->(Integer)args[0]+(Integer)args[1]),"portable callback can repeat proceed without repeating native work");
            equal(1,f.originalCalls,"portable native execution occurs once");
            equal("true",f.eval("portable","String(portableHandle instanceof java.lang.Runnable)"),"portable handle exposes only the JDK Runnable contract");
            try{PortableCallback.escaped.invoke(null,null,null);throw new AssertionError("Escaped continuation accepted");}
            catch(IllegalStateException expected){checks++;}
            PortableCallback.mode="args";
            equal(15,f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(owner,args)->(Integer)args[0]+(Integer)args[1]),"portable callback changes native arguments");
            PortableCallback.mode="invalid";
            equal(3,f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(owner,args)->(Integer)args[0]+(Integer)args[1]),"invalid replacement does not poison valid native continuation");
            PortableCallback.mode="thread";PortableCallback.offThread=null;
            equal(3,f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(owner,args)->(Integer)args[0]+(Integer)args[1]),"portable callback remains usable after an off-thread attempt");
            equal(true,PortableCallback.offThread instanceof IllegalStateException,"portable continuation rejects another thread before native work");
            PortableCallback.mode="receiver";PortableCallback.replacement=new Host();
            equal(19,f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(owner,args)->owner==PortableCallback.replacement?19:0),"portable continuation supports a changed receiver");
            PortableCallback.mode="normal";IllegalStateException original=new IllegalStateException("portable native identity");
            try{f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(owner,args)->{throw original;});throw new AssertionError("Native exception swallowed");}
            catch(IllegalStateException failure){equal(original,failure,"portable continuation preserves native exception identity");}
            f.eval("portable","portableHandle.run();portableHandle.run();");equal(0,f.hooks.size(),"portable unhook handle is idempotent");
            f.plan();f.plan(f.entry("portable",sum));equal(1,f.hooks.size(),"portable scope can install a new generation");
            f.runtime.setSafeMode(true);equal(0,f.hooks.size(),"portable ordinary hooks clean up in safe mode");
        }finally{f.runtime.stop();}
        Framework retained=new Framework();
        try{
            retained.plan(retained.entry("portable-guard",sum.replace("),callback);","),callback,true);")));
            retained.plan();equal(1,retained.hooks.size(),"portable process guard retains its Java callback after JS unload");
            PortableCallback.mode="normal";
            equal(3,retained.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(owner,args)->(Integer)args[0]+(Integer)args[1]),"retained portable guard needs no live JS scope");
        }finally{retained.runtime.stop();PortableCallback.escaped=null;PortableCallback.replacement=null;}
    }

    private static void packagedEntryChecks()throws Throwable {
        boolean independent=false;
        try{Class.forName("dev.c17.plugins.freeedit.Entry",false,Host.class.getClassLoader());}
        catch(ClassNotFoundException expected){independent=true;}
        equal(true,independent,"plugin product classes are absent from the host parent classpath");
        Framework f=new Framework();
        try {
            Files.copy(new File(freeEditBuild(),"runtime.jar").toPath(),new File(f.root,"classes.dex").toPath());
            ClassLoader nativeLoader=packagedOemLoader();f.runtime.rememberLoader(nativeLoader);
            JSONObject entry=packagedEntry(f,"packaged",packagedSettings(true));
            f.plan(entry);
            Bundle health=new Bundle();f.runtime.health(health);
            equal(1,f.loaded(),"actual runtime.js and independent plugin entry start through Rhino: "+health.getString("custom_plugin_runtime_last_error"));
            equal("true",f.eval("packaged","var type=C17.findClass('dev.c17.plugins.freeedit.Entry');var installed=type.getDeclaredField('installed');installed.setAccessible(true);String(installed.getBoolean(null))"),"packaged DEX entry is installed");
            equal("dalvik.system.DexClassLoader",f.eval("packaged","String(type.getClassLoader().getClass().getName())"),"plugin implementation is loaded by its independent DEX loader");
            PackagedPipeline pipeline=packagedPipeline(f,nativeLoader,entry,true);
            int installed=f.hooks.size();
            f.plan(new JSONObject(entry.toString()).put("state",packagedSettings(true).put("qs_card_grid_rows","7")));
            equal(installed,f.hooks.size(),"packaged settings preserve native hook registrations");
            f.runtime.setSafeMode(true);
            equal(0,f.loaded(),"packaged scope stops in safety mode");
            equal(true,f.hooks.size()>0 && f.hooks.size()<installed,"only packaged late-object guards survive cleanup");
            pipeline.afterCleanup(f);
            boolean refused=false;try{f.runtime.actionCommand("packaged",pipeline.instance,java.util.UUID.randomUUID().toString(),"qs_card_capture_safety");}
            catch(IllegalStateException expected){refused=true;}
            equal(true,refused,"actual packaged action cannot bypass host safety pause");
            f.runtime.health(health);equal(0L,health.getLong("custom_plugin_runtime_errors"),"packaged startup and cleanup have no runtime faults");
        } finally {f.runtime.stop();}
        Framework optional=new Framework();
        try {
            Files.copy(new File(freeEditBuild(),"runtime.jar").toPath(),new File(optional.root,"classes.dex").toPath());
            ClassLoader optionalLoader=packagedOemLoader();optional.runtime.rememberLoader(optionalLoader);optional.refuseNextHook=true;
            optional.plan(packagedEntry(optional,"optional",packagedSettings(false)));
            Bundle health=new Bundle();optional.runtime.health(health);
            equal(1,optional.loaded(),"DEX handles an optional framework refusal without a secondary inactive error: "+health.getString("custom_plugin_runtime_last_error"));
            equal(0L,health.getLong("custom_plugin_runtime_errors"),"handled DEX bridge errors do not fault the plugin scope");
            String diagnostics=optional.eval("optional","String(C17.findClass('dev.c17.plugins.freeedit.Entry').getDeclaredMethod('diagnosticStatus').invoke(null,[]))");
            equal(true,diagnostics.contains("Framework refused optional hook"),"real proxy propagation preserves the original group installation failure");
            optional.eval("optional","var entry=C17.findClass('dev.c17.plugins.freeedit.Entry');var mainField=entry.getDeclaredField('main');mainField.setAccessible(true);var handler=mainField.get(null);if(handler.delayed!=null)handler.delayed.run();");
            diagnostics=optional.eval("optional","String(entry.getDeclaredMethod('diagnosticStatus').invoke(null,[]))");
            JSONObject recovered=new JSONObject(diagnostics);boolean retryRecovered=false;
            JSONArray attempts=recovered.getJSONArray("loaders");
            for(int i=0;i<attempts.length();i++)for(String group:new String[]{"geometry","navigation","media"}){
                JSONObject one=attempts.getJSONObject(i).getJSONObject(group);
                if(one.getString("lastError").contains("Framework refused optional hook"))retryRecovered="ready".equals(one.getString("stage"))||"different_owner".equals(one.getString("stage"));
            }
            equal(true,retryRecovered,"bounded plugin-owned retry recovers the refused group through the real C17 proxy: "+diagnostics);
            equal(true,diagnostics.contains("Framework refused optional hook"),"successful proxy retry retains its original diagnostic history");
            equal(optionalLoader,optionalLoader.loadClass("com.oplus.systemui.plugins.qs.customize.component.OplusQSPersonalComponent").getClassLoader(),"bounded refusal fixture keeps the actual native owner alive throughout retry");
        } finally {optional.runtime.stop();}
        Framework noNavigation=new Framework();
        try{
            Files.copy(new File(freeEditBuild(),"runtime.jar").toPath(),new File(noNavigation.root,"classes.dex").toPath());
            ClassLoader nativeLoader=packagedOemLoader(false);noNavigation.runtime.rememberLoader(nativeLoader);
            JSONObject entry=packagedEntry(noNavigation,"no-navigation",packagedSettings(true));noNavigation.plan(entry);
            PackagedPipeline pipeline=packagedPipeline(noNavigation,nativeLoader,entry,false);
            noNavigation.runtime.setSafeMode(true);pipeline.afterCleanup(noNavigation);
        }finally{noNavigation.runtime.stop();}
    }

    private static JSONObject packagedSettings(boolean enabled)throws Exception{
        return new JSONObject().put("qs_card_grid_rows","6").put("qs_card_small_tiles_mode","more")
                .put("qs_card_free_edit_enabled",enabled).put("qs_card_more_tiles_enabled",enabled)
                .put("qs_card_swipe_more_enabled",enabled).put("qs_card_wallpaper_enabled",false)
                .put("qs_card_media_thin_highlight_enabled",false).put("qs_card_hide_idle_controls_enabled",false)
                .put("qs_card_verified_baseline_ready",true);
    }
    private static JSONObject packagedEntry(Framework framework,String id,JSONObject state)throws Exception{
        String main=new String(Files.readAllBytes(new File("plugins/control-center-free-edit/main.js").toPath()),StandardCharsets.UTF_8);
        JSONObject descriptor=CustomPluginEngine.evaluate(main,state),form=CustomPluginSchema.form(descriptor);
        equal(2,descriptor.getInt("apiVersion"),"actual package main.js evaluates through the host descriptor engine");
        equal(Arrays.asList("qs_card_more_sort_catalog","qs_card_more_refresh_catalog","qs_card_capture_safety","qs_card_skip_safety","qs_card_refresh_status"),form.getJSONArray("actions").toList(),"actual main.js declares catalog, safety and status actions");
        equal(Arrays.asList("qs_card_safety_status","qs_card_runtime_status"),form.getJSONArray("statuses").toList(),"actual main.js declares safety and runtime status");
        String source=new String(Files.readAllBytes(new File("plugins/control-center-free-edit/runtime.js").toPath()),StandardCharsets.UTF_8);
        return framework.entry(id,source).put("versionCode",descriptor.getInt("versionCode")).put("state",state).put("form",form);
    }
    private static PackagedPipeline packagedPipeline(Framework f,ClassLoader loader,JSONObject entry,boolean navigation)throws Throwable{
        String id=entry.getString("id");
        Class<?> calls=Class.forName("qsfixture.NativeCalls",true,loader);
        Method original=calls.getMethod("callOriginal",Method.class,Object.class,Object[].class);
        calls.getField("handler").set(null,(java.lang.reflect.InvocationHandler)(owner,method,args)->f.call(method,owner,args,(receiver,input)->{
            try{return original.invoke(null,method,receiver,input);}
            catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
        }));
        Object fixture=Class.forName("qsfixture.FullFixture",true,loader).getConstructor().newInstance();
        f.eval(id,"var profile=C17.findClass('dev.c17.plugins.freeedit.QsCardOemProfile').getDeclaredField('CHECK');profile.setAccessible(true);var gate=profile.get(null);['started','complete','approved'].forEach(function(name){var field=gate.getClass().getDeclaredField(name);field.setAccessible(true);field.setBoolean(gate,true)});");
        f.eval(id,"C17.findClass('"+CustomPluginRuntimeCheck.class.getName()+"').getMethod('observePackagedEntry',java.lang.Class).invoke(null,[C17.findClass('dev.c17.plugins.freeedit.Entry')]);");
        Class<?> actualEntry=observedPackagedEntry,actualRuntime=actualEntry.getClassLoader().loadClass("dev.c17.plugins.freeedit.QsCardProjectionRuntime");
        equal(false,actualEntry.getClassLoader()==Host.class.getClassLoader(),"observed Entry belongs only to the independent plugin loader");
        Object snapshot=privateField(actualEntry,"safetySnapshot").get(null);
        PackagedEvidence evidence=new PackagedEvidence(actualEntry,actualRuntime);
        Class<?> sourceType=actualEntry.getClassLoader().loadClass("dev.c17.plugins.freeedit.QsSafetySnapshot$Source");
        // Only the audited OEM/SQLite read boundary is substituted. The real snapshot validator,
        // runtime.js SafetyStorage proxy, C17.save atomic replace, form action and status stay intact.
        privateField(snapshot.getClass(),"source").set(snapshot,Proxy.newProxyInstance(actualEntry.getClassLoader(),new Class<?>[]{sourceType},(owner,method,input)->method.getName().equals("packageName")?"com.android.systemui":evidence.read()));
        Object cards=getPublic(fixture,"cards"),component=getPublic(cards,"component"),manager=getPublic(component,"manager"),vm=getPublic(component,"editViewModel");
        Object nativeConfig=getPublic(manager,"layoutConfig"),state=getPublic(cards,"state"),flow=getPublic(vm,"uiStateFlow"),adapter=getPublic(component,"adapter");
        fixture.getClass().getMethod("attach").invoke(fixture);
        fixture.getClass().getMethod("expand",float.class).invoke(fixture,1f);
        f.eval(id,"var selectedEntry=C17.findClass('dev.c17.plugins.freeedit.Entry');var handlerField=selectedEntry.getDeclaredField('main');handlerField.setAccessible(true);var retryHandler=handlerField.get(null);for(var attempt=0;attempt<2;attempt++){var retry=retryHandler.delayed;if(retry==null)break;retryHandler.delayed=null;retry.run()}");
        Handler.drainForCheck();
        Object captured=actualRuntime.getMethod("nativeSafetyViewModel").invoke(null);
        String captureFailure=captured==vm?"":"; Entry="+actualEntry.getMethod("diagnosticStatus").invoke(null)+"; Host="+f.runtime.status();
        equal(vm,captured,"actual registered native collector captures the exact VM with all business gated"+captureFailure);
        equal(true,new JSONObject(evidence.read()).getJSONObject("activity").getBoolean("nativeReady"),"substituted OEM evidence is derived from the actual captured native state");
        equal(nativeConfig,getPublic(manager,"layoutConfig"),"missing snapshot leaves the original native layout config untouched");
        equal(false,privateField(actualRuntime,"verifiedBaseline").getBoolean(null),"forged UI verified marker cannot authorize the actual packaged Runtime");
        Method effective=actualEntry.getDeclaredMethod("effectiveSettings");effective.setAccessible(true);
        Bundle gated=(Bundle)effective.invoke(null);
        Class<?> settingType=actualEntry.getClassLoader().loadClass("dev.c17.plugins.freeedit.QsCardSettings");
        for(Object key:((Map<?,?>)settingType.getField("BOOLEANS").get(null)).keySet())
            equal(false,gated.getBoolean(key.toString()),"missing receipt gates actual effective feature "+key);
        Bundle raw=(Bundle)privateField(actualEntry,"settings").get(null);
        equal(true,raw.getBoolean("qs_card_free_edit_enabled"),"safety gate preserves the user's requested free edit value");
        equal(true,raw.getBoolean("qs_card_hide_lower_tiles_enabled"),"packaged runtime always normalizes forced lower hiding");
        JSONObject initial=new JSONObject(f.runtime.formStatusCommand(id));String instance=initial.getString("instanceId");
        equal(Arrays.asList("qs_card_refresh_status","qs_card_capture_safety","qs_card_skip_safety","qs_card_more_sort_catalog","qs_card_more_refresh_catalog"),initial.getJSONArray("actions").toList(),"actual runtime.js registers all declared callbacks");
        equal("已启动",initial.getJSONObject("status").getString("qs_card_runtime_status"),"startup publishes engine status");
        int hookCount=f.hooks.size();
        JSONObject refreshed=new JSONObject(f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"qs_card_refresh_status"));
        equal(true,refreshed.getBoolean("ok"),"refresh status executes through actual host bridge");
        equal(instance,new JSONObject(f.runtime.formStatusCommand(id)).getString("instanceId"),"refresh reuses same runtime instance");
        equal(hookCount,f.hooks.size(),"status refresh does not reinstall hooks");
        JSONObject catalogReply=new JSONObject(f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"qs_card_more_sort_catalog"));
        equal(true,catalogReply.getBoolean("ok"),"actual sort callback executes through the host action bridge while safety-gated");
        JSONObject catalogValue=catalogReply.getJSONObject("value");
        equal(false,catalogValue.getBoolean("available"),"safety gate refuses a live ordering catalog");
        equal(Collections.emptyList(),catalogValue.getJSONArray("items").toList(),"gated catalog cannot invent editable members");
        equal(Collections.emptyList(),catalogValue.getJSONArray("selected").toList(),"gated catalog cannot invent a selected order");
        equal("展开控制中心后重试",catalogValue.getString("message"),"actual callback explains the unavailable catalog");
        equal(nativeConfig,getPublic(manager,"layoutConfig"),"sort catalog action preserves the native layout configuration");
        equal("",f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"sort catalog action does not create or replace the safety receipt");
        equal(false,initial.getJSONObject("status").getString("qs_card_safety_status").isEmpty(),"actual runtime.js publishes safety status through the form channel");
        equal("",f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"startup and native capture cannot invent saved recovery bytes");
        JSONObject refused=new JSONObject(f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"qs_card_capture_safety"));
        equal(true,refused.getBoolean("ok"),"actual form action reports a requested-features refusal without faulting the plugin");
        equal(true,refused.getJSONObject("value").getString("message").contains("保存"),"actual callback force-saves without requiring disabled switches");
        equal(true,!f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))").isEmpty(),"force save creates a real receipt even with requested switches enabled");

        f.plan(new JSONObject(entry.toString()).put("state",packagedSettings(false)));
        equal(instance,new JSONObject(f.runtime.formStatusCommand(id)).getString("instanceId"),"switches-off update keeps the same executing action instance");
        String request=java.util.UUID.randomUUID().toString();
        String savedResult=f.runtime.actionCommand(id,instance,request,"qs_card_capture_safety");
        JSONObject saved=new JSONObject((String)actualEntry.getMethod("safetyStatus").invoke(null));
        equal(true,new JSONObject(savedResult).getBoolean("ok")&&saved.getBoolean("ready"),"host actionCommand invokes actual C17.onAction and real snapshot capture validates the baseline: "+savedResult+" / "+saved);
        String baseline=f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))");
        JSONObject document=new JSONObject(baseline);
        File data=new File(new File(new File(f.root,"c17_plugins"),"data"),hex(java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8))));
        File savedFile=new File(data,java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("native-safety-snapshot-v1".getBytes(StandardCharsets.UTF_8))+".json");
        equal(baseline,new org.json.JSONTokener(new String(Files.readAllBytes(savedFile.toPath()),StandardCharsets.UTF_8)).nextValue(),"actual atomic host storage contains the exact native receipt string");
        equal(saved.getLong("savedAtMillis"),document.getLong("savedAtMillis"),"shown save timestamp comes from the actual C17.save committed document");
        String shown=new JSONObject(f.runtime.formStatusCommand(id)).getJSONObject("status").getString("qs_card_safety_status");
        equal(true,shown.contains(saved.getString("savedAtText")),"actual runtime.js status includes the committed save time");
        equal(64,document.getString("checksum").length(),"stored snapshot is a real fingerprinted receipt rather than a synthetic ready bit");
        int reads=evidence.reads;
        equal(savedResult,f.runtime.actionCommand(id,instance,request,"qs_card_capture_safety"),"host action nonce retry returns the exact packaged result");
        equal(reads,evidence.reads,"a result retry does not recollect or overwrite the native snapshot");
        equal(baseline,f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"once-only request retains the exact atomic saved bytes");
        String replacement=f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"qs_card_capture_safety");
        equal(true,new JSONObject(replacement).getBoolean("ok")&&evidence.reads>reads,"a second user click can recollect and overwrite through actual C17.save");
        baseline=f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))");
        equal(false,new File(savedFile.getPath()+".tmp").exists(),"successful repeated overwrite leaves no pending storage file");
        equal(true,new JSONObject((String)actualEntry.getMethod("safetyStatus").invoke(null)).getBoolean("ready"),"atomic overwrite can immediately be independently revalidated");
        java.nio.file.Path failedPending=new File(savedFile.getPath()+".tmp").toPath();
        Files.createDirectory(failedPending);
        JSONObject failedSave;
        try{failedSave=new JSONObject(f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"qs_card_capture_safety"));}
        finally{Files.delete(failedPending);}
        equal(true,failedSave.getBoolean("ok"),"an actual generic storage write failure is handled by the business snapshot callback");
        equal("保存未完成，旧数据已保留",failedSave.getJSONObject("value").getString("message"),"failed actual C17.save reports the retained old snapshot");
        equal(baseline,f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"failed overwrite preserves exact previously committed atomic storage bytes");
        equal(1,f.loaded(),"handled safety storage failure leaves the actual packaged runtime alive");
        equal(true,new JSONObject((String)actualEntry.getMethod("safetyStatus").invoke(null)).getBoolean("ready"),"old saved baseline remains usable after an actual storage failure");
        equal(nativeConfig,getPublic(manager,"layoutConfig"),"saving a baseline does not enable disabled feature preferences");
        f.plan(new JSONObject(entry.toString()).put("state",packagedSettings(true)));
        Handler.drainForCheck();

        String inspect="(function(){function type(name){return C17.findClass('dev.c17.plugins.freeedit.'+name)}"
                +"function field(owner,name,stat){var f=(stat?owner:owner.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(stat?null:owner)}"
                +"function flag(v){return String(v)==='true'}function num(v){return Number(String(v))}"
                +"var e=type('Entry'),c=type('QsCardProjectionRuntime'),n=type('QsSmallTilesVisibility'),all=field(c,'sessions',true).values().toArray(),sessions=[];"
                +"for(var i=0;i<all.length;i++){var s=all[i],manager=field(s,'manager').get(),config=field(manager,'layoutConfig'),plan=field(s,'plan');sessions.push({projected:flag(field(s,'projected')),entryCommitted:flag(field(s,'entryCommitted')),failed:flag(field(s,'failed')),pages:num(field(plan,'pages')),currentConfig:{rows:num(field(config,'rowCount')),coarseColumns:num(field(config,'colCount'))}})}"
                +"return JSON.stringify({installed:flag(field(e,'installed',true)),owners:{geometry:num(field(e,'cardOwners',true).size()),navigation:num(field(e,'navigationOwners',true).size())},navigation:{bound:field(n,'binding',true)!==null,sessionCount:num(field(n,'sessions',true).size())},geometry:{ready:flag(field(c,'ready',true)),sessionCount:all.length,sessions:sessions}})})()";
        JSONObject status=new JSONObject(f.eval(id,inspect));
        equal(true,status.getBoolean("installed"),"actual packaged entry remains installed after native callbacks");
        equal(1,status.getJSONObject("owners").getInt("geometry"),"actual packaged entry installs the geometry owner");
        equal(navigation?1:0,status.getJSONObject("owners").getInt("navigation"),"actual packaged entry reports navigation owner eligibility");
        equal(navigation,status.getJSONObject("navigation").getBoolean("bound"),"native navigation binding reflects its complete contract");
        equal(navigation?1:0,status.getJSONObject("navigation").getInt("sessionCount"),"registered native lifecycle captures only eligible navigation owners");
        JSONObject geometry=status.getJSONObject("geometry");
        equal(true,geometry.getBoolean("ready"),"full geometry reflection preflight succeeds");
        equal(1,geometry.getInt("sessionCount"),"reflected bootstrap emit passes through the registered native hook");
        JSONObject session=geometry.getJSONArray("sessions").getJSONObject(0);
        equal(true,session.getBoolean("projected"),"verified explicit free edit projects the real native personal consumer");
        equal(false,session.getBoolean("entryCommitted"),"More stays an unselected candidate until the user explicitly adds it");
        equal(false,session.getBoolean("failed"),"full native pipeline remains usable");
        equal(1,session.getInt("pages"),"actual packaged projection keeps every displayed component on the single top page");
        equal(4,session.getJSONObject("currentConfig").getInt("rows"),"a fresh private canvas starts at four rows and ignores retired global defaults");
        equal(4,session.getJSONObject("currentConfig").getInt("coarseColumns"),"native layout exposes four independently draggable base columns");
        equal(state,flow.getClass().getField("value").get(flow),"full pipeline never publishes its projected state to native StateFlow");
        Class<?> holderType=Class.forName("com.oplus.systemui.plugins.qs.customize.view.viewholder.BaseEditableViewHolder",true,loader);
        Object holder=holderType.getConstructor(Object.class).newInstance(vm);
        List<?> grids=(List<?>)adapter.getClass().getMethod("getDataList").invoke(adapter);Object projectedGrid=grids.get(0);
        holderType.getMethod("bindData",projectedGrid.getClass()).invoke(holder,projectedGrid);
        Object realSize=holderType.getMethod("getCurrRealSize").invoke(holder);
        equal(2,realSize.getClass().getField("colSpan").getInt(realSize),"registered holder real-size hook keeps projected 2x2 in base columns");
        Class<?> spanType=realSize.getClass();List<Object> choices=new ArrayList<>();
        for(int columns:new int[]{1,2,4})choices.add(spanType.getConstructor(int.class,int.class).newInstance(columns,2));
        Class<?> callbackType=Class.forName(holderType.getName()+"$onViewCreated$3",true,loader);
        Object callback=callbackType.getConstructor(holderType).newInstance(holder);
        List<?> accepted=(List<?>)callbackType.getMethod("getActualSupportSize",List.class).invoke(callback,choices);
        equal(2,accepted.size(),"native support callback cannot mistake projected 2x2 for 4x2");
        Object nativeGrid=cards.getClass().getField("cachedMedia").get(cards);
        holderType.getMethod("bindData",nativeGrid.getClass()).invoke(holder,nativeGrid);
        Object nativeSize=holderType.getMethod("getCurrRealSize").invoke(holder);
        equal(2,nativeSize.getClass().getField("colSpan").getInt(nativeSize),"holder reused for native coarse 1x2 retains native 2x2 conversion");
        int beforeFrames=evidence.reads;
        Object collector=getPublic(cards,"collector");Method emit=collector.getClass().getMethod("emit",Object.class,loader.loadClass("kotlin.coroutines.Continuation"));
        for(int i=0;i<20;i++)emit.invoke(collector,state,null);Handler.drainForCheck();
        equal(beforeFrames,evidence.reads,"repeated actual native emissions do not reopen the snapshot source per frame");
        equal(baseline,f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"projection frames preserve the committed native snapshot");
        Object userFlow=getPublic(getPublic(vm,"tileConfigRepoSwitcher"),"userId");
        int beforeUser=evidence.reads;userFlow.getClass().getField("value").set(userFlow,10);emit.invoke(collector,state,null);
        equal(beforeUser,evidence.reads,"same-VM user boundary performs no synchronous SQL/source work in the actual hook");
        equal(true,privateField(actualRuntime,"verifiedBaseline").getBoolean(null),"same-VM user boundary retains package-scoped explicit decision");
        equal(true,getPublic(manager,"layoutConfig")!=null,"native user switch retains an available layout configuration");
        Handler.drainForCheck();
        equal(true,((Bundle)effective.invoke(null)).getBoolean("qs_card_verified_baseline_ready"),"user switch does not demand a new safety receipt");
        equal(baseline,f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"switching users preserves the committed previous-user snapshot");
        userFlow.getClass().getField("value").set(userFlow,0);emit.invoke(collector,state,null);Handler.drainForCheck();
        equal(true,privateField(actualRuntime,"verifiedBaseline").getBoolean(null),"switching back revalidates original-user evidence through the actual packaged lifecycle");
        List<?> locations=(List<?>)getPublic(state,"allPersonalTilesLoc");List<Object> changedLoc=new ArrayList<>(locations);
        changedLoc.set(0,locations.get(0).getClass().getConstructor(int.class,int.class,int.class).newInstance(0,0,2));
        Object changed=state.getClass().getConstructor(List.class,List.class,List.class,boolean.class,boolean.class)
                .newInstance(getPublic(state,"allPersonalTiles"),changedLoc,getPublic(state,"otherAddedTiles"),false,false);
        int beforeLayout=evidence.reads;flow.getClass().getField("value").set(flow,changed);cards.getClass().getField("state").set(cards,changed);emit.invoke(collector,changed,null);
        equal(beforeLayout,evidence.reads,"changed raw native geometry is detected before deferred source validation");
        equal(true,privateField(actualRuntime,"verifiedBaseline").getBoolean(null),"native cache geometry cannot revoke explicit decision");
        Handler.drainForCheck();
        JSONObject mismatch=new JSONObject((String)actualEntry.getMethod("safetyStatus").invoke(null));
        equal("ready",mismatch.getString("state"),"saved record remains ready after native layout changes");
        equal(true,mismatch.getBoolean("retained")&&mismatch.getBoolean("ready"),"layout mismatch retains the old record and explicit decision");
        equal(baseline,f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))"),"native fallback never overwrites the last successful safety data");
        equal(true,((Bundle)privateField(actualEntry,"settings").get(null)).getBoolean("qs_card_free_edit_enabled"),"native fallback preserves the user's requested configuration");
        int readsBeforeRestore=evidence.reads;
        flow.getClass().getField("value").set(flow,state);cards.getClass().getField("state").set(cards,state);emit.invoke(collector,state,null);Handler.drainForCheck();
        equal(readsBeforeRestore,evidence.reads,"restoring native geometry requires no SQL evidence read");
        equal(true,privateField(actualRuntime,"verifiedBaseline").getBoolean(null),"original raw geometry must freshly match before projection recovers");
        equal(4,integer(getPublic(manager,"layoutConfig"),"colCount"),"freshly revalidated original geometry recovers the requested four-column display");
        JSONObject skipReply=new JSONObject(f.runtime.actionCommand(id,instance,java.util.UUID.randomUUID().toString(),"qs_card_skip_safety"));
        equal(true,skipReply.getBoolean("ok"),"declared skip action executes through the actual host and runtime.js bridge");
        JSONObject skipStatus=new JSONObject((String)actualEntry.getMethod("safetyStatus").invoke(null));
        equal(true,skipStatus.getBoolean("ready")&&skipStatus.getBoolean("skipped")&&!skipStatus.getBoolean("saved"),"skip is usable without falsely claiming a new complete backup");
        equal(baseline,f.eval(id,"String(C17.load('native-safety-last-complete-v1',''))"),"actual skip retains the last complete receipt in separate atomic storage");
        equal(true,new JSONObject(f.runtime.formStatusCommand(id)).getJSONObject("status").getString("qs_card_safety_status").contains("跳过"),"actual form visibly distinguishes skip from saved backup");
        baseline=f.eval(id,"String(C17.load('native-safety-snapshot-v1',''))");
        return new PackagedPipeline(actualEntry,actualRuntime,cards,component,manager,nativeConfig,state,flow,adapter,holder,holderType,projectedGrid,instance,baseline,savedFile);
    }

    private static Field privateField(Class<?> owner,String name)throws Exception{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
    private static Object getPublic(Object owner,String name)throws Exception{return owner.getClass().getField(name).get(owner);}
    private static int integer(Object owner,String name)throws Exception{return owner.getClass().getField(name).getInt(owner);}

    private static final class PackagedPipeline {
        final Class<?> entry,runtime,holderType;
        final Object cards,component,manager,nativeConfig,state,flow,adapter,holder,projectedGrid;
        final String instance,baseline;
        final File savedFile;
        PackagedPipeline(Class<?> entry,Class<?> runtime,Object cards,Object component,Object manager,Object nativeConfig,Object state,Object flow,Object adapter,Object holder,Class<?> holderType,Object projectedGrid,String instance,String baseline,File savedFile){
            this.entry=entry;this.runtime=runtime;this.cards=cards;this.component=component;this.manager=manager;this.nativeConfig=nativeConfig;this.state=state;this.flow=flow;this.adapter=adapter;this.holder=holder;this.holderType=holderType;this.projectedGrid=projectedGrid;this.instance=instance;this.baseline=baseline;this.savedFile=savedFile;
        }
        void afterCleanup(Framework framework)throws Throwable{
            Handler.drainForCheck();
            equal(false,privateField(entry,"installed").getBoolean(null),"actual runtime.js cleanup releases its independent Java Entry");
            equal(null,privateField(entry,"safetySnapshot").get(null),"cleanup revokes the live native snapshot authorization");
            equal(baseline,new org.json.JSONTokener(new String(Files.readAllBytes(savedFile.toPath()),StandardCharsets.UTF_8)).nextValue(),"host safety pause preserves the last committed plugin snapshot file");
            equal(nativeConfig,getPublic(manager,"layoutConfig"),"cleanup restores the exact original native layout config");
            equal(state,getPublic(flow,"value"),"cleanup preserves the original native StateFlow identity");
            equal(getPublic(state,"allPersonalTiles"),adapter.getClass().getMethod("getDataList").invoke(adapter),"cleanup restores original native personal components");
            Object vm=getPublic(component,"editViewModel");Method support=vm.getClass().getMethod("getActualSupportSize",int.class,List.class);
            List<Object> fallback=Collections.singletonList(getPublic(getPublic(cards,"cachedMedia"),"size"));
            // A callback queued before unload can still carry the old display GridData. Exercise
            // its registered holder-origin guard, followed by the real VM support-query guard.
            holderType.getField("grid").set(holder,projectedGrid);
            Class<?> callbackType=holderType.getClassLoader().loadClass(holderType.getName()+"$onViewCreated$3");
            Object callback=callbackType.getConstructor(holderType).newInstance(holder);
            Method query=callbackType.getMethod("getActualSupportSize",List.class);
            int before=framework.originalCalls;
            Object blocked=framework.call(query,callback,new Object[]{fallback},(owner,args)->framework.call(support,vm,new Object[]{999,fallback},(target,input)->input[1]));
            equal(Collections.emptyList(),blocked,"retained late-object guard blocks a stale display index after actual JS unload");
            equal(before+1,framework.originalCalls,"late holder callback cannot reach the native location helper");
            Object foreign=vm.getClass().getConstructor().newInstance();
            Object transparent=framework.call(support,foreign,new Object[]{0,fallback},(owner,args)->args[1]);
            equal(fallback,transparent,"retained guard stays transparent to a fresh unrelated native VM");
            equal(before+2,framework.originalCalls,"unrelated native support query executes exactly once after cleanup");
        }
    }

    /** Primitive evidence from the captured native VM; only the unavailable on-device SQL boundary is doubled. */
    private static final class PackagedEvidence {
        final Class<?> entry,runtime;int reads;
        PackagedEvidence(Class<?> entry,Class<?> runtime){this.entry=entry;this.runtime=runtime;}
        String read()throws Exception{
            reads++;Object vm=runtime.getMethod("nativeSafetyViewModel").invoke(null);if(vm==null)throw new IllegalStateException("Native VM unavailable");
            Object normal=getPublic(getPublic(vm,"uiStateFlow"),"value");int user=(Integer)getPublic(getPublic(getPublic(vm,"tileConfigRepoSwitcher"),"userId"),"value");
            JSONArray personal=new JSONArray(),other=new JSONArray(),persisted=new JSONArray();
            List<?> grids=(List<?>)getPublic(normal,"allPersonalTiles"),locations=(List<?>)getPublic(normal,"allPersonalTilesLoc");
            for(int i=0;i<grids.size();i++){
                JSONObject tile=tile(grids.get(i));Object location=locations.get(i);
                tile.put("page",integer(location,"pageIndex")).put("col",integer(location,"startCol")).put("row",integer(location,"startRow"));personal.put(tile);
                persisted.put(row(user,tile,tile.getInt("page")+","+tile.getInt("col")*2+","+tile.getInt("row"),2));
            }
            for(Object grid:(List<?>)getPublic(normal,"otherAddedTiles")){JSONObject tile=tile(grid);other.put(tile);persisted.put(row(user,tile,"-1,-1,-1",1));}
            Class<?> profile=entry.getClassLoader().loadClass("dev.c17.plugins.freeedit.QsCardOemProfile"),snapshot=entry.getClassLoader().loadClass("dev.c17.plugins.freeedit.QsSafetySnapshot");
            Method requested=entry.getDeclaredMethod("requestedFeaturesActive");requested.setAccessible(true);
            JSONObject environment=new JSONObject().put("profile",snapshot.getField("PROFILE").get(null)).put("manufacturer","fixture").put("model","fixture-model").put("device","fixture-device")
                    .put("buildFingerprint","fixture-rom").put("sdkInt",36).put("systemUiPackage",privateField(profile,"PACKAGE").get(null)).put("systemUiVersionCode",987654L)
                    .put("systemUiVersionName","fixture-any-rom").put("systemUiApkSha256",JSONObject.NULL).put("userId",user).put("userSerial",user+100);
            return new JSONObject().put("environment",environment).put("activity",new JSONObject().put("featuresActive",requested.invoke(null)).put("editing",getPublic(normal,"isEditMode"))
                    .put("restoring",runtime.getMethod("nativeSafetyRestoring").invoke(null)).put("nativeReady",true))
                    .put("native",new JSONObject().put("grid",new JSONObject().put("columns",2).put("rows",4)).put("personal",personal).put("other",other)
                            .put("persisted",new JSONObject().put("schema","sep_edit_info-v1").put("grid",new JSONObject().put("userId",user).put("columns",4).put("rows",4)).put("tiles",persisted))).toString();
        }
        private JSONObject tile(Object grid)throws Exception{
            Object data=getPublic(grid,"data"),span=getPublic(grid,"size");String name=data.getClass().getSimpleName(),spec;
            if(name.equals("MediaViewModel"))spec="innerFake_media";
            else if(name.equals("BrightnessVolumeViewModel"))spec="innerFake_brightness_volume";
            else spec=(String)data.getClass().getMethod("getSpec").invoke(data);
            return new JSONObject().put("id",getPublic(grid,"uniqueId").toString()).put("spec",spec).put("cols",integer(span,"colSpan")).put("rows",integer(span,"rowSpan"));
        }
        private JSONObject row(int user,JSONObject tile,String location,int scale)throws Exception{
            return new JSONObject().put("userId",user).put("spec",tile.getString("spec")).put("loc",location).put("size",tile.getInt("cols")*scale+","+tile.getInt("rows"))
                    .put("expandInfo",JSONObject.NULL).put("expandInfoTwo","").put("expandInfoThree","");
        }
    }

    private static ClassLoader packagedOemLoader()throws Exception {
        return packagedOemLoader(true);
    }
    private static ClassLoader packagedOemLoader(boolean navigation)throws Exception {
        File root=freeEditBuild(),classes=new File(root,"classes"),checks=new File(root,"checks");
        String previous=System.getProperty("java.class.path"),previousGeometry=System.getProperty("c17.qsCardFixtureRoot"),previousEntry=System.getProperty("c17.entryFixtureRoot");
        java.nio.file.Path isolated=Files.createTempDirectory("c17-packaged-native-");
        // Compile the plugin's OEM doubles without exposing its implementation to the runtime's
        // parent loader. Startup must load its own implementation from the packaged JAR/DEX.
        try(java.net.URLClassLoader factory=new java.net.URLClassLoader(new java.net.URL[]{checks.toURI().toURL(),classes.toURI().toURL()},Host.class.getClassLoader())) {
            System.setProperty("java.class.path",previous+File.pathSeparator+classes.getAbsolutePath());
            System.setProperty("c17.qsCardFixtureRoot",isolated.resolve("geometry").toString());
            System.setProperty("c17.entryFixtureRoot",isolated.resolve("entry").toString());
            Method fixture=Class.forName("dev.c17.plugins.freeedit.EntryFullPipelineFixture",true,factory).getDeclaredMethod("fixtures",boolean.class,boolean.class);fixture.setAccessible(true);
            try(java.net.URLClassLoader generated=(java.net.URLClassLoader)fixture.invoke(null,navigation,true)) {
                return new java.net.URLClassLoader(generated.getURLs(),Host.class.getClassLoader()) {
                    @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                        // Use the generated full OEM contract when it exists. Parent host
                        // checks also contain deliberately smaller QSTile/material stubs.
                        boolean nativeFixture=((name.startsWith("com.oplus.")||name.startsWith("com.oplusos.")
                                ||name.startsWith("com.android.systemui.")||name.startsWith("com.coui."))
                                &&findResource(name.replace('.','/')+".class")!=null)
                                ||name.equals("kotlin.Lazy")||name.equals("kotlin.Unit")||name.equals("kotlinx.coroutines.flow.StateFlow")
                                ||name.equals("kotlin.coroutines.Continuation")||name.startsWith("qsfixture.");
                        if(!nativeFixture)return super.loadClass(name,resolve);
                        synchronized(getClassLoadingLock(name)) {
                            Class<?> found=findLoadedClass(name);if(found==null)found=findClass(name);if(resolve)resolveClass(found);return found;
                        }
                    }
                };
            }
        } finally {
            System.setProperty("java.class.path",previous);
            if(previousGeometry==null)System.clearProperty("c17.qsCardFixtureRoot");else System.setProperty("c17.qsCardFixtureRoot",previousGeometry);
            if(previousEntry==null)System.clearProperty("c17.entryFixtureRoot");else System.setProperty("c17.entryFixtureRoot",previousEntry);
        }
    }
    private static File freeEditBuild(){return new File(System.getProperty("c17.freeedit.buildDir","plugins/control-center-free-edit/build"));}

    private static void startupFailureChecks()throws Throwable {
        Framework f=new Framework();events.clear();
        try {
            f.plan(f.entry("startup","var Host=C17.findClass('"+HOST+"');C17.onCleanup(function(){Host.getDeclaredMethod('record',java.lang.String).invoke(null,['cleanup'])});var callback=C17.proxy(C17.findClass('"+Listener.class.getName()+"'),{update:function(){throw new Error('primary-startup-failure')}});callback.update(1);C17.log('after callback');"));
            Bundle health=new Bundle();f.runtime.health(health);
            equal(0,f.loaded(),"failed synchronous proxy callback closes startup");
            equal(1L,health.getLong("custom_plugin_runtime_errors"),"secondary inactive bridge call cannot overwrite or double-count the first startup failure");
            equal(true,health.getString("custom_plugin_runtime_last_error").contains("primary-startup-failure"),"original synchronous startup failure remains available without a live JS scope");
            JSONObject status=f.runtime.status().getJSONObject(0);
            equal("failed",status.getString("stage"),"native lifecycle status identifies failed startup");
            equal(true,status.getString("stack").contains("primary-startup-failure"),"native status retains the original startup exception stack");
            f.dispatcher.drain();equal(Arrays.asList("cleanup"),events,"faulted startup cleanup runs exactly once");
            f.plan(f.entry("recoverable","var handled=false;var callback=C17.proxy(C17.findClass('"+Listener.class.getName()+"'),{update:function(){throw new Error('recoverable-bridge-failure')}},true);try{callback.update(1)}catch(expected){handled=true}C17.log('recovered');"));
            equal("true",f.eval("recoverable","handled"),"opt-in Java proxy propagation lets the caller recover before the scope is faulted");
            equal(1,f.loaded(),"handled bridge failure leaves its plugin running");
        } finally {f.runtime.stop();}
    }

    private static void coalescedSafeModeChecks()throws Throwable {
        Framework f=new Framework();events.clear();
        try {
            f.plan(f.entry("coalesced",hook("one","[]","return 42;")
                    +"C17.onCleanup(function(){C17.findClass('"+HOST+"').getDeclaredMethod('record',java.lang.String).invoke(null,['coalesced-cleanup'])});"));
            equal(1,f.loaded(),"safe-mode transition fixture starts live");
            background(() -> {f.runtime.setSafeMode(true);f.runtime.setSafeMode(false);});
            equal(0,f.loaded(),"background safety signal makes old callbacks inert immediately");
            f.dispatcher.drain();
            equal(1,f.loaded(),"coalesced safety enter and exit restart a live plugin after cleanup");
            equal(Arrays.asList("coalesced-cleanup"),events,"coalesced safety transition cleans the old instance exactly once");
            equal(1,f.removed,"coalesced safety transition removes the old ordinary hook");
            equal(1,f.hooks.size(),"coalesced safety transition keeps one fresh ordinary hook");
            equal("23",f.eval("coalesced","String(C17.state.answer)"),"root command can use the restarted scope");
            equal(42,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->1),"restarted plugin hook remains live");
        } finally {f.runtime.stop();}
        Framework busy=new Framework();events.clear();
        Host.callbackEntered=new CountDownLatch(1);Host.callbackResume=new CountDownLatch(1);
        AtomicReference<Object> answer=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread running=null;
        try {
            busy.plan(busy.entry("coalesced-busy",hook("one","[]","C17.findClass('"+HOST+"').getDeclaredMethod('blocking').invoke(null,[]);return 9;")
                    +"C17.onCleanup(function(){C17.findClass('"+HOST+"').getDeclaredMethod('record',java.lang.String).invoke(null,['busy-cleanup'])});"));
            running=thread(()->{try{answer.set(busy.call("one",new Class<?>[0],new Object[0],(receiver,args)->7));}catch(Throwable problem){failure.set(problem);}});
            running.start();equal(true,Host.callbackEntered.await(2,TimeUnit.SECONDS),"old scope enters a busy callback before safety transition");
            busy.runtime.setSafeMode(true);busy.runtime.setSafeMode(false);
            equal(0,busy.loaded(),"new instance waits for busy old scope cleanup after safety exit");
            try{busy.eval("coalesced-busy","1");throw new AssertionError("replacement scope ran before cleanup");}
            catch(CustomPluginRuntime.CommandPendingException expected){checks++;}
            Host.callbackResume.countDown();running.join(2000);
            equal(false,running.isAlive(),"retired callback completes without blocking main");if(failure.get()!=null)throw failure.get();
            equal(7,answer.get(),"retired callback falls back to native once after safety exit");
            busy.dispatcher.until(()->busy.loaded()==1);
            equal(Arrays.asList("busy-cleanup"),events,"busy old scope cleans exactly once before replacement starts");
            equal(1,busy.hooks.size(),"busy retirement leaves one fresh ordinary hook");
        } finally {
            Host.callbackResume.countDown();if(running!=null)running.join(2000);busy.runtime.stop();
        }
    }

    private static void closedPluginReplacementChecks()throws Throwable {
        Framework f=new Framework();
        try {
            f.plan(f.entry("replaced-fault","throw new Error('replacement-startup-fixture');"));
            f.dispatcher.drain();
            equal("failed",f.runtime.status().getJSONObject(0).getString("stage"),"failed entry retains native diagnostics after cleanup");
            f.plan(f.entry("replaced-fault","var recovered=73;"));
            equal(1,f.loaded(),"already closed failed instance cannot block its same-id replacement");
            equal("73",f.eval("replaced-fault","String(recovered)"),"replacement root command uses a fresh live scope");
        } finally {f.runtime.stop();}
    }

    private static void stateAndLoaderChecks()throws Throwable {
        Framework f=new Framework();
        JSONObject initial=f.entry("state",hook("one","[]","return Number(C17.state.answer);")
                +"var updates=[];var identity={};C17.onStateChanged(function(next,previous){updates.push(previous.answer+'>'+next.answer)});");
        f.plan(initial);Site installed=f.hooks.get(0);
        f.plan(new JSONObject(initial.toString()).put("state",new JSONObject().put("answer",31)));
        equal(1,f.hooks.size(),"state-only change keeps one live hook");equal(installed,f.hooks.get(0),"state-only change preserves hook and DEX scope identity");
        equal("23>31",f.eval("state","updates.join(',')"),"state listener receives old and new state");
        equal(31,f.call("one",new Class<?>[0],new Object[0],(receiver,args)->1),"existing hook observes updated C17.state");
        f.plan(new JSONObject(initial.toString()).put("state",new JSONObject().put("answer",31)));
        equal("1",f.eval("state","updates.length"),"same state does not notify twice");
        f.eval("state","var handle=C17.hookNative(C17.findClass('"+HOST+"').getDeclaredMethod('one'),C17.findClass('"+HOST+"').getDeclaredMethod('callback').invoke(null,[]),false);handle.unhook();handle.unhook();");
        int removed=f.removed;f.plan();equal(removed+1,f.removed,"explicit rollback and automatic cleanup share idempotent handle");f.runtime.stop();

        Framework lifecycle=new Framework();
        lifecycle.plan(lifecycle.entry("thread","var loaderCount=0,loaderThread='';C17.onClassLoader(function(){loaderCount++;loaderThread=String(java.lang.Thread.currentThread().getName())});C17.onCleanup(function(){C17.save('last',{answer:73});C17.log('cleanup');java.lang.System.setProperty('c17-runtime-cleanup-thread',String(java.lang.Thread.currentThread().getName()));});"));
        ClassLoader later=new ClassLoader(Host.class.getClassLoader()){};
        background(() -> lifecycle.runtime.rememberLoader(later));
        equal("1",lifecycle.eval("thread","loaderCount"),"background class discovery does not execute JS listener on that thread");
        lifecycle.dispatcher.drain();equal("2",lifecycle.eval("thread","loaderCount"),"main receives deferred loader callback");
        equal(Thread.currentThread().getName(),lifecycle.eval("thread","loaderThread"),"loader listener runs on main");
        background(() -> lifecycle.runtime.setSafeMode(true));
        equal(null,System.getProperty("c17-runtime-cleanup-thread"),"background safe-mode signal defers cleanup to main");
        lifecycle.dispatcher.drain();equal(Thread.currentThread().getName(),System.getProperty("c17-runtime-cleanup-thread"),"cleanup runs on main");
        lifecycle.runtime.setSafeMode(false);equal("73",lifecycle.eval("thread","C17.load('last').answer"),"cleanup can persist state before scope is released");
        lifecycle.runtime.stop();System.clearProperty("c17-runtime-cleanup-thread");
    }

    private static void dexParentChecks()throws Throwable {
        Framework f=new Framework(false);File[] jars=dexFixtures(f.root);
        ClassLoader blind=new java.net.URLClassLoader(new java.net.URL[]{jars[1].toURI().toURL()},ClassLoader.getSystemClassLoader().getParent());
        try{Class.forName(XposedInterface.Hooker.class.getName(),false,blind);throw new AssertionError("OEM fixture sees module contract");}
        catch(ClassNotFoundException expected){checks++;}
        f.runtime.rememberLoader(blind);
        f.plan(f.entry("dex","var loader=C17.loadDex('fixture.jar');var entry=C17.findClass('fixture.DexEntry',loader);"));
        equal("true",f.eval("dex","entry.getDeclaredMethod('contract').invoke(null,[]).equals(C17.findClass('io.github.libxposed.api.XposedInterface$Hooker'))"),"DEX shares host libxposed contract with a blind OEM loader present first");
        equal("oem-visible",f.eval("dex","String(entry.getDeclaredMethod('oem').invoke(null,[]))"),"DEX falls back to native OEM class loader");
        f.eval("dex","C17.onStateChanged(function(){});");JSONObject updated=f.entry("dex","var loader=C17.loadDex('fixture.jar');var entry=C17.findClass('fixture.DexEntry',loader);").put("state",new JSONObject().put("answer",2));
        f.plan(updated);equal("true",f.eval("dex","loader === entry.getClassLoader()"),"state update preserves fixture class loader");f.runtime.stop();
        // Framework API classes can be injected by a defining loader that is not
        // visible through the module loader's ordinary parent delegation on ART.
        ClassLoader moduleWithoutApi=new ClassLoader(CustomPluginRuntime.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.startsWith("io.github.libxposed.api."))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        ClassLoader parent=CustomPluginRuntime.dexParent(moduleWithoutApi,XposedInterface.class.getClassLoader(),()->Collections.singletonList(blind));
        try(java.net.URLClassLoader dex=new java.net.URLClassLoader(new java.net.URL[]{jars[0].toURI().toURL()},parent)) {
            Class<?> contract=Class.forName("fixture.DexBridge",false,dex);
            AtomicReference<Object> received=new AtomicReference<>();
            Object proxy=Proxy.newProxyInstance(dex,new Class<?>[]{contract},(self,method,args)->{received.set(args[0]);return null;});
            Method hook=contract.getDeclaredMethod("hook",XposedInterface.Hooker.class);
            XposedInterface.Hooker callback=chain->chain.proceed();hook.invoke(proxy,callback);
            equal(callback,received.get(),"real DEX interface proxy uses the framework defining loader when module delegation cannot see API");
            equal(XposedInterface.Hooker.class,hook.getParameterTypes()[0],"DEX bridge parameter retains the host API class identity");
            equal(XposedInterface.Chain.class,Class.forName(XposedInterface.Chain.class.getName(),false,parent),"all nested public API contracts use the defining loader");
            equal("oem-visible",Class.forName("fixture.oem.OnlyOem",true,dex).getMethod("value").invoke(null),"native fallback remains available with a split API loader");
        }
    }

    private static File[] dexFixtures(File root)throws Exception {
        File source=new File(root,"fixture-sources"),out=new File(root,"fixture-classes");source.mkdir();out.mkdir();
        File dex=new File(source,"DexEntry.java"),oem=new File(source,"OnlyOem.java"),bridge=new File(source,"DexBridge.java");
        Files.write(dex.toPath(),("package fixture;public class DexEntry { public static Class<?> contract(){return io.github.libxposed.api.XposedInterface.Hooker.class;} public static String oem(){return fixture.oem.OnlyOem.value();}}").getBytes(StandardCharsets.UTF_8));
        Files.write(oem.toPath(),"package fixture.oem;public class OnlyOem {public static String value(){return \"oem-visible\";}}".getBytes(StandardCharsets.UTF_8));
        Files.write(bridge.toPath(),"package fixture;public interface DexBridge {void hook(io.github.libxposed.api.XposedInterface.Hooker callback);}".getBytes(StandardCharsets.UTF_8));
        javax.tools.JavaCompiler compiler=javax.tools.ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new AssertionError("JDK compiler unavailable");
        int status=compiler.run(null,null,null,"-source","8","-target","8","-Xlint:-options","-classpath",System.getProperty("java.class.path"),"-d",out.getPath(),dex.getPath(),oem.getPath(),bridge.getPath());
        if(status!=0)throw new AssertionError("DEX fixture JAR compilation failed");
        File dexJar=new File(root,"fixture.jar"),oemJar=new File(root,"oem.jar");
        writeJar(dexJar,out,"fixture/DexEntry.class","fixture/DexBridge.class");writeJar(oemJar,out,"fixture/oem/OnlyOem.class");return new File[]{dexJar,oemJar};
    }
    private static void writeJar(File jar,File root,String... paths)throws Exception {
        try(java.util.jar.JarOutputStream output=new java.util.jar.JarOutputStream(new java.io.FileOutputStream(jar))){for(String path:paths){output.putNextEntry(new java.util.jar.JarEntry(path));output.write(Files.readAllBytes(new File(root,path).toPath()));output.closeEntry();}}
    }

    private static void assetLifecycleChecks()throws Throwable {
        Framework cancelled=new Framework();byte[] bytes="exports.value=91;".getBytes(StandardCharsets.UTF_8);
        String sha=hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        JSONObject entry=cancelled.entry("cancelled","java.lang.System.setProperty('c17-stale-asset-start','started');")
                .put("assets",new JSONArray().put(new JSONObject().put("path","lib.js").put("bytes",bytes.length).put("sha256",sha)));
        cancelled.owner.assets.put("content://dev.puitheme.iosstatusbar.settings/custom-plugins/assets/cancelled/1/lib.js",bytes);
        cancelled.owner.opened=new CountDownLatch(1);cancelled.owner.gate=new CountDownLatch(1);
        cancelled.plan(entry);equal(true,cancelled.owner.opened.await(2,TimeUnit.SECONDS),"resource worker starts without blocking main");
        cancelled.plan();cancelled.owner.gate.countDown();
        cancelled.dispatcher.until(()->new File(cancelled.root,"c17_plugins/cancelled/1/lib.js").isFile());
        cancelled.dispatcher.drain();equal(0,cancelled.loaded(),"disabled in-flight plan cannot activate after copy completes");
        equal(null,System.getProperty("c17-stale-asset-start"),"stale generation never executes plugin source");cancelled.runtime.stop();

        Framework retry=new Framework();JSONObject transientEntry=retry.entry("retry","var result=C17.require('lib.js').value;")
                .put("assets",new JSONArray().put(new JSONObject().put("path","lib.js").put("bytes",bytes.length).put("sha256",sha)));
        retry.owner.assets.put("content://dev.puitheme.iosstatusbar.settings/custom-plugins/assets/retry/1/lib.js",bytes);retry.owner.failedReads.set(1);
        retry.plan(transientEntry);retry.dispatcher.until(()->retry.loaded()==1);
        equal("91",retry.eval("retry","result"),"transient provider failure retries and activates the same plan");retry.runtime.stop();

        Framework refreshed=new Framework();refreshed.owner.assets.put("content://dev.puitheme.iosstatusbar.settings/custom-plugins/assets/retry/1/lib.js",bytes);refreshed.owner.failedReads.set(3);
        refreshed.plan(transientEntry);
        refreshed.dispatcher.until(()->"failed".equals(refreshed.runtime.status().getJSONObject(0).optString("stage")));
        Bundle failedHealth=new Bundle();refreshed.runtime.health(failedHealth);
        equal(1,failedHealth.getInt("custom_plugin_runtime_failed"),"exhausted asset attempts report a failed startup");
        refreshed.plan(new JSONObject(transientEntry.toString()));refreshed.dispatcher.until(()->refreshed.loaded()==1);
        equal("91",refreshed.eval("retry","result"),"same plan refresh retries after automatic retry budget");
        equal("running",refreshed.runtime.status().getJSONObject(0).getString("stage"),"recovered asset startup reports running in SystemUI");
        equal("running",refreshed.runtime.applicationStatus().getJSONObject(0).getString("stage"),"recovered asset startup reports running in application scopes");
        Bundle recoveredHealth=new Bundle();refreshed.runtime.health(recoveredHealth);
        equal(0,recoveredHealth.getInt("custom_plugin_runtime_failed"),"recovered startup no longer counts as failed");
        equal(failedHealth.getLong("custom_plugin_runtime_errors"),recoveredHealth.getLong("custom_plugin_runtime_errors"),"recovery retains the cumulative error history");
        refreshed.runtime.stop();
    }

    private static void nativeContinuationChecks()throws Throwable {
        Framework f=new Framework();f.plan(f.entry("concurrent",hook("sum","['int','int']","return c.proceed();")+hook("one","[]","return 43;")));
        AtomicReference<Object> result=new AtomicReference<>();AtomicReference<Throwable> problem=new AtomicReference<>();
        Thread outer=thread(() -> {try{result.set(f.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(receiver,args)->{
            AtomicReference<Object> nested=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread other=thread(()->{try{nested.set(f.call("one",new Class<?>[0],new Object[0],(self,input)->1));}catch(Throwable error){failure.set(error);}});other.start();other.join(1500);
            if(other.isAlive())throw new AssertionError("native proceed held the plugin lock across another hooked thread");
            if(failure.get()!=null)throw failure.get();return nested.get();}));}catch(Throwable error){problem.set(error);}});
        outer.start();outer.join(2500);equal(false,outer.isAlive(),"native inter-thread call completes");if(problem.get()!=null)throw problem.get();equal(43,result.get(),"another thread may enter the same plugin while native is executing");f.runtime.stop();

        Framework off=new Framework();CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);
        off.plan(off.entry("off",hook("one","[]","return c.proceed()+100;")));
        AtomicReference<Object> answer=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread running=thread(()->{try{answer.set(off.call("one",new Class<?>[0],new Object[0],(receiver,args)->{entered.countDown();if(!resume.await(3,TimeUnit.SECONDS))throw new AssertionError("continuation not resumed");return 7;}));}catch(Throwable error){failure.set(error);}});
        running.start();equal(true,entered.await(2,TimeUnit.SECONDS),"native continuation entered");
        off.runtime.setSafeMode(true);equal(0,off.loaded(),"main disables plugin while native continuation is suspended");
        resume.countDown();running.join(2000);equal(false,running.isAlive(),"disabled native continuation resumes");if(failure.get()!=null)throw failure.get();
        equal(7,answer.get(),"late callback uses already computed native result");equal(1,off.originalCalls,"native continuation remains single-proceed during disable");off.runtime.stop();

        Framework invalid=new Framework();invalid.plan(invalid.entry("invalid",hook("sum","['int','int']","return c.proceed([]);")));
        equal(3,invalid.call("sum",new Class<?>[]{int.class,int.class},new Object[]{1,2},(receiver,args)->(Integer)args[0]+(Integer)args[1]),"invalid bridge args fault plugin and still invoke original once");
        equal(1,invalid.originalCalls,"invalid conversion never poisons the native fallback");invalid.runtime.stop();

        Framework busy=new Framework();Host.callbackEntered=new CountDownLatch(1);Host.callbackResume=new CountDownLatch(1);
        busy.plan(busy.entry("busy",hook("one","[]","C17.findClass('"+HOST+"').getDeclaredMethod('blocking').invoke(null,[]);return 99;")));
        AtomicReference<Throwable> backgroundFailure=new AtomicReference<>();
        Thread callback=thread(()->{try{busy.call("one",new Class<?>[0],new Object[0],(receiver,args)->7);}catch(Throwable callbackFailure){backgroundFailure.set(callbackFailure);}});callback.start();
        equal(true,Host.callbackEntered.await(2,TimeUnit.SECONDS),"raw Java callback holds its JS scope lock");
        AtomicReference<String> command=new AtomicReference<>();AtomicReference<Throwable> managerFailure=new AtomicReference<>();
        Thread manager=new Thread(()->{
            Looper.setCurrentForCheck(Looper.getMainLooper());
            try {
                try{busy.eval("busy","1");command.set("unexpected success");}catch(IllegalStateException expected){command.set(expected.getMessage());}
                busy.runtime.setSafeMode(true);Bundle health=new Bundle();busy.runtime.health(health);
            } catch(Throwable managementFailure){managerFailure.set(managementFailure);}
            finally{Looper.resetCurrentForCheck();}
        },"C17-check-main-management");manager.setDaemon(true);manager.start();manager.join(1500);
        try {
            equal(false,manager.isAlive(),"main management never waits for a busy JS callback");
            if(managerFailure.get()!=null)throw managerFailure.get();
            equal(true,command.get()!=null && command.get().contains("busy"),"root evaluation reports busy instead of blocking main");
        } finally {Host.callbackResume.countDown();}
        callback.join(2000);if(backgroundFailure.get()!=null)throw backgroundFailure.get();
        busy.dispatcher.until(()->busy.hooks.isEmpty());equal(0,busy.loaded(),"delayed cleanup finishes when the busy scope is released");busy.runtime.stop();
    }

    private interface Background {void run()throws Throwable;}
    private static Thread thread(Background task) {
        Thread thread=new Thread(()->{Looper.setCurrentForCheck(null);try{task.run();}catch(Throwable failure){throw new AssertionError(failure);}finally{Looper.resetCurrentForCheck();}},"C17-check-worker");thread.setDaemon(true);return thread;
    }
    private static void background(Background task)throws Throwable {
        AtomicReference<Throwable> failure=new AtomicReference<>();Thread thread=thread(()->{try{task.run();}catch(Throwable problem){failure.set(problem);}});thread.start();thread.join(2000);
        equal(false,thread.isAlive(),"background runtime signal returns promptly");if(failure.get()!=null)throw failure.get();
    }
    private static String hex(byte[] digest){StringBuilder value=new StringBuilder();for(byte b:digest)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString();}
}
