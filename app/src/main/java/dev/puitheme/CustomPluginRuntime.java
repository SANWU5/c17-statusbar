// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import dalvik.system.DexClassLoader;
import io.github.libxposed.api.XposedInterface;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

/** API 2 executes trusted, unrestricted JavaScript in the hooked process. It is not a sandbox. */
final class CustomPluginRuntime {
    interface Install {
        XposedInterface.HookHandle hook(Executable method,XposedInterface.Hooker callback) throws Throwable;
        default void deoptimize(Executable method) throws Throwable { }
        default SharedPreferences frameworkPreferences() { return null; }
        default ParcelFileDescriptor openFrameworkFile(String name) throws FileNotFoundException {
            throw new FileNotFoundException(name);
        }
    }
    private final Object stateLock = new Object();
    private final Handler main;
    private final String runtimePackage,storageProcess;
    private final Map<String,Plugin> plugins = new LinkedHashMap<>();
    private final List<Plugin> retiring = new ArrayList<>();
    private final List<WeakReference<ClassLoader>> loaders = new ArrayList<>();
    private final List<XposedInterface.HookHandle> retainedNativeHandles = new ArrayList<>();
    private final ExecutorService assets = Executors.newSingleThreadExecutor(task -> {
        Thread worker=new Thread(task,"C17-plugin-assets"); worker.setDaemon(true); return worker;
    });
    private Context context;
    private Install install;
    private Runnable changed;
    private Runnable lifecycleChanged;
    private JSONObject savedPlan = new JSONObject();
    private boolean paused = true, stopped;
    private long revision, errors,storageRevision;
    private String lastError="";

    CustomPluginRuntime(Handler main) { this(main,ModuleRuntimeStatus.SYSTEM_UI); }
    CustomPluginRuntime(Handler main,String runtimePackage) {this(main,runtimePackage,runtimePackage);}
    CustomPluginRuntime(Handler main,String runtimePackage,String processName) {
        if(!CustomPluginSupport.appPackage(runtimePackage))throw new IllegalArgumentException("Invalid runtime application package");
        this.main = main;this.runtimePackage=runtimePackage;
        if(processName==null||processName.isEmpty())throw new IllegalArgumentException("Runtime process name is required");
        try{storageProcess=ModuleRuntimeStatus.SYSTEM_UI.equals(runtimePackage)?null:hex(java.security.MessageDigest.getInstance("SHA-256").digest(processName.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    void observeLifecycle(Runnable listener){synchronized(stateLock){lifecycleChanged=listener;}}
    /** Only a successfully started scope may suppress a host material. Failed/paused scopes yield. */
    JSONObject runningState(String id,int minimumVersion) {
        synchronized(stateLock) {
            Plugin plugin=plugins.get(id);
            return paused||stopped||plugin==null||!plugin.active||!plugin.ready||plugin.closeRequested||plugin.closed
                    ||plugin.entry.optInt("versionCode",0)<minimumVersion ? null : copyState(plugin.state);
        }
    }
    private void lifecycleChanged(){Runnable listener;synchronized(stateLock){listener=lifecycleChanged;}if(listener!=null)listener.run();}

    static final class CommandPendingException extends IllegalStateException {
        CommandPendingException(String message) { super(message); }
    }

    static ClassLoader dexParent(ClassLoader moduleLoader,ClassLoader apiLoader,
            java.util.function.Supplier<List<ClassLoader>> nativeLoaders) {
        return new ClassLoader(moduleLoader) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                // libxposed may inject its contract through a separate defining
                // loader. The module's ordinary parent delegation need not see it.
                // Keep the framework's exact Class identity, including nested API types.
                if(name.startsWith("io.github.libxposed.api."))return Class.forName(name,false,apiLoader);
                return super.loadClass(name,resolve);
            }
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
                for(ClassLoader candidate:nativeLoaders.get()) {
                    if(candidate==moduleLoader)continue;
                    try{return Class.forName(name,false,candidate);}catch(ClassNotFoundException absent){}
                }
                throw new ClassNotFoundException(name);
            }
        };
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper()==main.getLooper()) action.run(); else main.post(action);
    }

    /** Called only by the provider's root-authenticated command pipeline, on the main thread. */
    public String evaluateCommand(String pluginId,String source) throws Exception {
        Plugin plugin;
        synchronized(stateLock){
            if(paused||stopped)throw new IllegalStateException("Executable plugins are paused");
            plugin=plugins.get(pluginId);
        }
        if(plugin==null)throw new IllegalArgumentException("Plugin is not enabled: "+pluginId);
        if(!plugin.lock.tryLock())throw new CommandPendingException("Plugin callback is busy");
        try {
            if(!plugin.closeRequested && !plugin.closed && (plugin.preparing || !plugin.started && plugin.assetFailures<3 || plugin.active && !plugin.ready))
                throw new CommandPendingException("Plugin is still starting");
            if(!plugin.active)throw new IllegalStateException("Plugin is not running: "+plugin.id+"; "+plugin.failure);
            org.mozilla.javascript.Context cx=enter();
            try{return org.mozilla.javascript.Context.toString(cx.evaluateString(plugin.scope,source,pluginId+"/root-command.js",1,null));}
            catch(RuntimeException failure){throw new Exception("Plugin command failed",failure);}
            finally{org.mozilla.javascript.Context.exit();}
        } finally { plugin.lock.unlock(); }
    }

    /** Read-only form queries share the existing main-thread command dispatcher. */
    public String formStatusCommand(String pluginId)throws Exception{
        Plugin plugin=commandPlugin(pluginId,null);
        if(!plugin.lock.tryLock())throw new CommandPendingException("Plugin callback is busy");
        try{commandReady(plugin);return plugin.formSnapshot().toString();}finally{plugin.lock.unlock();}
    }
    /** An action belongs to one running instance. A new load can never inherit an old request. */
    public String actionCommand(String pluginId,String instanceId,String requestId,String action)throws Exception{
        if(requestId==null||!requestId.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid action request identity");
        Plugin plugin=commandPlugin(pluginId,instanceId);
        if(!plugin.lock.tryLock())throw new CommandPendingException("Plugin callback is busy");
        try{
            commandReady(plugin);plugin.trimActionResults();JSONObject previous=plugin.actionResults.get(requestId);
            if(previous!=null){if(!action.equals(previous.optString("action")))throw new IllegalArgumentException("Reused action request identity");return previous.toString();}
            if(plugin.consumedActions.containsKey(requestId))throw new IllegalStateException("操作已执行，返回结果已过期；请刷新状态。该请求不会重复执行");
            Function callback=plugin.actionCallbacks.get(action);
            if(!plugin.declared("actions",action)||callback==null)throw new IllegalArgumentException("Plugin action is unavailable: "+action);
            if(plugin.consumedActions.size()>=8192)throw new IllegalStateException("插件操作记录已达到安全上限，请重新加载插件后继续");
            JSONObject result=new JSONObject().put("requestId",requestId).put("instanceId",plugin.instanceId).put("action",action);
            // Enter the journal before invoking JS. Even a failed callback is an already consumed click.
            plugin.actionResults.put(requestId,result);
            plugin.consumedActions.put(requestId,action);
            plugin.actionResultTimes.put(requestId,System.nanoTime());
            org.mozilla.javascript.Context cx=enter();
            try{
                JSONObject request=new JSONObject().put("requestId",requestId).put("instanceId",plugin.instanceId).put("action",action);
                Object answer=callback.call(cx,plugin.scope,plugin.scope,new Object[]{toScript(cx,plugin.scope,request)});
                if(answer!=Undefined.instance&&answer!=null){
                    Object json=cx.evaluateString(plugin.scope,"JSON.stringify",plugin.id+"/action-result",1,null);
                    Object encoded=((Function)json).call(cx,plugin.scope,plugin.scope,new Object[]{answer});
                    if(encoded!=Undefined.instance){String text=org.mozilla.javascript.Context.toString(encoded);if(text.getBytes(StandardCharsets.UTF_8).length>262144)throw new IllegalArgumentException("Action result is too large");result.put("value",new org.json.JSONTokener(text).nextValue());}
                }
                result.put("ok",true);
            }catch(Throwable failure){result.put("ok",false).put("error",String.valueOf(failure).substring(0,Math.min(4096,String.valueOf(failure).length())));}
            finally{org.mozilla.javascript.Context.exit();}
            result.put("status",copyState(plugin.formStatus));String encoded=result.toString();plugin.trimActionResults();return encoded;
        }finally{plugin.lock.unlock();}
    }
    private Plugin commandPlugin(String pluginId,String expectedInstance){
        synchronized(stateLock){
            if(paused||stopped)throw new IllegalStateException("Executable plugins are paused");
            Plugin plugin=plugins.get(pluginId);if(plugin==null)throw new IllegalStateException("Plugin is not running: "+pluginId);
            if(expectedInstance!=null&&!plugin.instanceId.equals(expectedInstance))throw new IllegalStateException("Plugin instance changed; refresh before trying again");
            return plugin;
        }
    }
    private void commandReady(Plugin plugin){
        if(!current(plugin)||plugin.closeRequested||plugin.closed)throw new IllegalStateException("Plugin instance is no longer active");
        if(plugin.preparing||!plugin.started&&plugin.assetFailures<3||plugin.active&&!plugin.ready)throw new CommandPendingException("Plugin is still starting");
        if(!plugin.active||!plugin.ready)throw new IllegalStateException("Plugin is not running: "+plugin.id);
    }

    void configure(Context context,Install install,Runnable changed) {
        synchronized (stateLock) {
            if (stopped || this.context != null || context == null) return;
            this.context = context; this.install = install; this.changed = changed;
        }
        onMain(this::reconcile);
    }

    void rememberLoader(ClassLoader loader) {
        if (loader == null) return;
        List<Plugin> current;
        synchronized (stateLock) {
            if (stopped) return;
            for (java.util.Iterator<WeakReference<ClassLoader>> it = loaders.iterator(); it.hasNext();) {
                ClassLoader known = it.next().get();
                if (known == null) it.remove(); else if (known == loader) return;
            }
            loaders.add(new WeakReference<>(loader)); current = new ArrayList<>(plugins.values());
        }
        onMain(() -> { for (Plugin plugin : current) plugin.loaderReady(loader); });
    }

    boolean accept(JSONObject plan) {
        // Validate the complete transition before removing any running plugin.
        try {
            JSONArray runtimes = plan.optJSONArray("runtimes");
            if (runtimes == null) runtimes = new JSONArray();
            Map<String,JSONObject> entries = new LinkedHashMap<>();
            for (int i=0;i<runtimes.length();i++) {
                JSONObject entry = runtimes.getJSONObject(i);
                String id = entry.getString("id"), source = entry.getString("source");
                if (id.isEmpty() || entries.containsKey(id) || !(entry.opt("state") instanceof JSONObject)
                        || !(entry.opt("versionCode") instanceof Number) || !(entry.opt("baseDir") instanceof String))
                    throw new IllegalArgumentException("Incomplete runtime plugin entry");
                JSONObject form=entry.optJSONObject("form");
                JSONArray scopes=entry.optJSONArray("scopes");
                if(entry.has("scopes")&&scopes==null)throw new IllegalArgumentException("Invalid runtime scopes");
                if(scopes!=null)for(int j=0;j<scopes.length();j++)if(!CustomPluginSupport.appPackage(scopes.optString(j)))throw new IllegalArgumentException("Invalid executable plugin scope");
                if(!CustomPluginSupport.includes(scopes,runtimePackage))throw new IllegalArgumentException("Plugin does not target this runtime process");
                if(entry.has("assetPackage")&&!runtimePackage.equals(entry.getString("assetPackage")))throw new IllegalArgumentException("Plugin asset package does not match its runtime");
                if(entry.has("form")&&form==null)throw new IllegalArgumentException("Invalid runtime form");
                if(form!=null){
                    CustomPluginSchema.fields(form,"actions","statuses");java.util.Set<String> keys=new java.util.HashSet<>();
                    for(String kind:new String[]{"actions","statuses"}){
                        JSONArray names=form.getJSONArray(kind);for(int j=0;j<names.length();j++){
                            Object name=names.get(j);if(!(name instanceof String)||!CustomPluginSchema.validStateKey((String)name)||!keys.add((String)name))throw new IllegalArgumentException("Invalid runtime form key");
                        }
                    }
                }
                entries.put(id,new JSONObject(entry.toString()));
            }
            if(plan.has("installedPlugins")){
                Map<String,JSONObject> installed=CustomPluginStorage.inventory(plan.getJSONArray("installedPlugins"));
                if(!installed.keySet().containsAll(entries.keySet()))throw new IllegalArgumentException("Running plugin is absent from installed inventory");
            }
            JSONObject accepted = new JSONObject(plan.toString()); accepted.put("runtimes",new JSONArray(runtimes.toString()));
            synchronized (stateLock) {
                if (stopped) return false;
                savedPlan = accepted; revision = plan.optLong("revision",revision);
            }
            onMain(this::reconcile);
            return true;
        } catch (Exception invalid) { report("Invalid executable plugin plan; previous plan retained",invalid);return false; }
    }

    void setSafeMode(boolean safe) {
        List<Plugin> removed=new ArrayList<>();
        synchronized (stateLock) {
            if (stopped || paused == safe) return;
            paused = safe;
            // Remember this transition even if safety mode ends before main can reconcile.
            // Old callbacks become inert now; a replacement waits for their main-thread cleanup.
            if(safe) {
                removed.addAll(plugins.values());plugins.clear();
                for(Plugin plugin:removed){plugin.active=false;if(!plugin.closed)retiring.add(plugin);}
            }
        }
        Collections.reverse(removed);for(Plugin plugin:removed)plugin.close();
        onMain(this::reconcile);
    }

    void stop() {
        List<Plugin> old;
        synchronized (stateLock) {
            if (stopped) return;
            stopped = true; old = new ArrayList<>(plugins.values()); old.addAll(retiring); plugins.clear(); loaders.clear();
            savedPlan = new JSONObject(); changed = null;lifecycleChanged=null;
        }
        Collections.reverse(old); for (Plugin plugin : old) plugin.close();
        assets.shutdownNow();
        synchronized (stateLock) { context = null; install = null; }
    }

    void health(Bundle report) {
        List<Plugin> current;
        synchronized (stateLock) {
            current = new ArrayList<>(plugins.values());
            JSONArray desired = savedPlan.optJSONArray("runtimes");
            report.putInt("custom_plugin_runtime_requested",desired == null ? 0 : desired.length());
            report.putLong("custom_plugin_runtime_revision",revision);
            report.putLong("custom_plugin_runtime_errors",errors);
            report.putString("custom_plugin_runtime_last_error",lastError);
            report.putBoolean("custom_plugin_runtime_active",!paused && !stopped);
            int retained=0;for(XposedInterface.HookHandle handle:retainedNativeHandles)
                if(!(handle instanceof ManagedHandle) || !((ManagedHandle)handle).removed)retained++;
            report.putInt("custom_plugin_runtime_retained_guards",retained);
        }
        int active=0,hooks=0,pending=0,preparing=0,failed=0; long hits=0;
        for (Plugin plugin : current) {
            if (plugin.active && plugin.ready) active++;
            if (plugin.preparing || !plugin.started && !plugin.closeRequested && plugin.assetFailures<3) preparing++;
            if (plugin.closeRequested && !plugin.failure.isEmpty() || plugin.assetFailures>=3 && !plugin.preparing) failed++;
            hooks+=plugin.hookCount+plugin.nativeHooks.get(); pending+=plugin.pendingCount;
            hits+=plugin.hits;
        }
        report.putInt("custom_plugin_runtime_loaded",active); report.putInt("custom_plugin_runtime_hooks",hooks);
        report.putInt("custom_plugin_runtime_pending",pending); report.putLong("custom_plugin_runtime_hits",hits);
        report.putInt("custom_plugin_runtime_preparing",preparing); report.putInt("custom_plugin_runtime_failed",failed);
    }

    /** Root diagnostics remain available after a plugin has lost its JavaScript scope. */
    JSONArray status() {
        List<Plugin> current;
        synchronized(stateLock){current=new ArrayList<>(plugins.values());}
        JSONArray result=new JSONArray();
        for(Plugin plugin:current)try {
            String stage=plugin.closeRequested && !plugin.failure.isEmpty()?"failed":plugin.closeRequested?"stopping":plugin.preparing?"preparing"
                    :plugin.assetFailures>=3?"failed":plugin.active && plugin.ready?"running":plugin.started?"starting":"waiting";
            result.put(new JSONObject().put("id",plugin.id).put("versionCode",plugin.entry.optLong("versionCode"))
                    .put("stage",stage).put("error",plugin.failure).put("stack",plugin.failureStack)
                    .put("assetFailures",plugin.assetFailures).put("hooks",plugin.hookCount+plugin.nativeHooks.get()));
        } catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
        return result;
    }
    /** Declared status strings are copied under their plugin lock; reports never carry arbitrary stack traces. */
    JSONArray applicationStatus(){
        List<Plugin> current;synchronized(stateLock){current=new ArrayList<>(plugins.values());}
        JSONArray result=new JSONArray();
        for(Plugin plugin:current)try{
            JSONObject item=new JSONObject().put("id",plugin.id).put("versionCode",plugin.entry.optLong("versionCode"))
                    .put("generation",plugin.entry.optString("generation"))
                    .put("stage",plugin.closeRequested&&!plugin.failure.isEmpty()?"failed":plugin.closeRequested?"stopping":plugin.assetFailures>=3?"failed":plugin.active&&plugin.ready?"running":plugin.preparing?"preparing":"starting")
                    .put("hooks",plugin.hookCount+plugin.nativeHooks.get()).put("assetFailures",plugin.assetFailures);
            if(plugin.lock.tryLock())try{item.put("status",copyState(plugin.formStatus));}finally{plugin.lock.unlock();}
            result.put(item);
        }catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
        return result;
    }

    private void reconcile() {
        List<Plugin> removed = new ArrayList<>(), current;
        boolean transition=false;
        synchronized (stateLock) {
            if (context == null || install == null) return;
            Map<String,JSONObject> desired = new LinkedHashMap<>();
            JSONArray array = savedPlan.optJSONArray("runtimes");
            if (!paused && !stopped && array != null) for (int i=0;i<array.length();i++) {
                JSONObject entry=array.optJSONObject(i); if (entry != null) desired.put(entry.optString("id"),entry);
            }
            for (java.util.Iterator<Map.Entry<String,Plugin>> it=plugins.entrySet().iterator();it.hasNext();) {
                Map.Entry<String,Plugin> old=it.next(); JSONObject next=desired.get(old.getKey());
                if (next == null || !old.getValue().fingerprint.equals(codeFingerprint(next))) {
                    Plugin previous=old.getValue(); previous.active=false;
                    removed.add(previous); if(!previous.closed)retiring.add(previous); it.remove();
                    transition=true;
                } else {
                    old.getValue().assetSpecs=next.optJSONArray("assets");
                    JSONObject state=copyState(next.optJSONObject("state"));
                    if(!old.getValue().state.toString().equals(state.toString()))transition=true;
                    old.getValue().state=state;
                }
            }
            for (Map.Entry<String,JSONObject> entry : desired.entrySet()) if (!plugins.containsKey(entry.getKey())) {
                Plugin plugin=new Plugin(entry.getValue(),context,install); plugins.put(entry.getKey(),plugin);
                transition=true;
            }
            current=new ArrayList<>(plugins.values());
        }
        // Never run JavaScript, native proceed, cleanup or framework unhook under the manager lock.
        Collections.reverse(removed); for (Plugin plugin : removed) plugin.close();
        JSONArray inventory;java.util.Set<String> busy=new java.util.HashSet<>();
        synchronized(stateLock){inventory=savedPlan.optJSONArray("installedPlugins");
            for(Plugin plugin:plugins.values())if(plugin.started||plugin.preparing)busy.add(plugin.id);
            for(Plugin plugin:retiring)busy.add(plugin.id);
        }
        if(storageProcess!=null&&inventory!=null){
            // The per-process resource queue serializes ownership cleanup with asset preparation.
            // Launcher/sidebar main threads never read ledgers, delete trees, or wait for a file lock.
            final long expected;final Context owner;final JSONArray owned;
            synchronized(stateLock){expected=++storageRevision;owner=context;}
            try{owned=new JSONArray(inventory.toString());}catch(org.json.JSONException invalid){report("Invalid plugin storage inventory",invalid);return;}
            final boolean notifyTransition=transition;
            try{assets.execute(()->{
                synchronized(stateLock){if(stopped||storageRevision!=expected)return;}
                Throwable problem=null;
                try{CustomPluginStorage.reconcile(owner,storageRoot(owner),owned,busy);}catch(Exception unavailable){problem=unavailable;}
                final Throwable failure=problem;
                main.post(()->{
                    synchronized(stateLock){if(stopped||storageRevision!=expected)return;}
                    if(failure!=null){report("Plugin storage removal incomplete",failure);return;}
                    finishReconcile(current,notifyTransition);
                });
            });}catch(RejectedExecutionException stopped){}
            return;
        }
        if(inventory!=null)try{CustomPluginStorage.reconcile(context,inventory,busy);}
        catch(Exception failure){report("Plugin storage removal incomplete",failure);return;}
        finishReconcile(current,transition);
    }

    private void finishReconcile(List<Plugin> current,boolean transition){
        for (Plugin plugin : current) {
            boolean eligible;
            synchronized (stateLock) {
                eligible=!paused && !stopped && plugins.get(plugin.id)==plugin;
                for(Plugin previous:retiring)if(previous.id.equals(plugin.id))eligible=false;
            }
            if (eligible) { plugin.start(); plugin.applyState(); }
        }
        Runnable notify;
        synchronized (stateLock) { notify=changed; }
        if (transition && notify != null) notify.run();
        if(transition)lifecycleChanged();
    }

    private File storageRoot(Context owner){
        File root=new File(owner.createDeviceProtectedStorageContext().getFilesDir(),"c17_plugins");
        return storageProcess==null?root:new File(new File(root,"processes"),storageProcess);
    }

    private boolean current(Plugin plugin) {
        synchronized(stateLock){ return !paused && !stopped && plugins.get(plugin.id)==plugin; }
    }

    private static JSONObject copyState(JSONObject state) {
        try{return state==null?new JSONObject():new JSONObject(state.toString());}
        catch(org.json.JSONException impossible){throw new IllegalArgumentException(impossible);}
    }

    private static String codeFingerprint(JSONObject entry) {
        try {
            JSONObject copy=new JSONObject(entry.toString());
            copy.remove("state");
            JSONArray assets=copy.optJSONArray("assets");
            if(assets!=null)for(int i=0;i<assets.length();i++) {
                JSONObject asset=assets.optJSONObject(i);
                if(asset!=null)asset.remove("frameworkFile");
            }
            return copy.toString();
        }
        catch(org.json.JSONException impossible){throw new IllegalArgumentException(impossible);}
    }

    private List<ClassLoader> currentLoaders() {
        List<ClassLoader> out=new ArrayList<>();
        synchronized (stateLock) {
            for (WeakReference<ClassLoader> weak : loaders) { ClassLoader loader=weak.get(); if (loader != null) out.add(loader); }
        }
        if (!out.contains(CustomPluginRuntime.class.getClassLoader())) out.add(CustomPluginRuntime.class.getClassLoader());
        return out;
    }

    private void report(String message,Throwable problem) {
        synchronized (stateLock) { errors++; lastError=message+": "+String.valueOf(problem); }
        ModuleDiagnostics.error("custom_plugin_runtime",message,problem);
    }

    private interface Call { Object run(org.mozilla.javascript.Context cx,Object[] args) throws Throwable; }
    private interface Undo { void run() throws Throwable; }
    private static final class ValueObserver { final Function callback; volatile boolean active=true; ValueObserver(Function callback){this.callback=callback;} }

    /** Short owner for trace labels: the last id segment, e.g. "liquid-glass". */
    private static String traceOwner(String id){int dot=id==null?-1:id.lastIndexOf('.');return "plugin:"+(dot<0?String.valueOf(id):id.substring(dot+1));}
    private final class Plugin {
        final ReentrantLock lock=new ReentrantLock();
        final String id,source,fingerprint;
        final String instanceId=java.util.UUID.randomUUID().toString();
        final Map<String,Function> actionCallbacks=new LinkedHashMap<>();
        final Map<String,JSONObject> actionResults=new LinkedHashMap<>();
        final Map<String,String> consumedActions=new LinkedHashMap<>();
        final Map<String,Long> actionResultTimes=new LinkedHashMap<>();
        void trimActionResults(){
            long now=System.nanoTime();long bytes=0;
            for(JSONObject value:actionResults.values())bytes+=value.toString().getBytes(StandardCharsets.UTF_8).length;
            java.util.Iterator<Map.Entry<String,JSONObject>> iterator=actionResults.entrySet().iterator();
            while(iterator.hasNext()){
                Map.Entry<String,JSONObject> entry=iterator.next();Long created=actionResultTimes.get(entry.getKey());
                if(actionResults.size()<=8&&bytes<=1048576&&created!=null&&now-created<300000000000L)break;
                bytes-=entry.getValue().toString().getBytes(StandardCharsets.UTF_8).length;
                actionResultTimes.remove(entry.getKey());iterator.remove();
            }
        }
        final JSONObject formStatus=new JSONObject();
        final Context owner;
        final Install installer;
        final JSONObject entry;
        File directory;
        final List<Undo> cleanups=new ArrayList<>();
        final List<Registration> registrations=new ArrayList<>();
        final List<LoaderListener> loaderListeners=new ArrayList<>();
        final List<StateListener> stateListeners=new ArrayList<>();
        final Map<String,Object> modules=new LinkedHashMap<>();
        final List<ClassLoader> dexLoaders=new ArrayList<>();
        final AtomicInteger nativeHooks=new AtomicInteger();
        ScriptableObject scope;
        ScriptableObject api;
        File dataDirectory;
        volatile JSONObject state;
        volatile JSONArray assetSpecs;
        volatile boolean active,ready,preparing;
        volatile String failure="",failureStack="";
        volatile int hookCount,pendingCount;
        volatile long hits;
        volatile boolean closeRequested,closed;
        boolean started,closing,stateQueued,closeQueued;
        volatile int assetFailures;
        String appliedState="";

        Plugin(JSONObject entry,Context owner,Install installer) {
            this.entry=entry; this.owner=owner; this.installer=installer;
            id=entry.optString("id"); source=entry.optString("source"); fingerprint=codeFingerprint(entry);
            state=copyState(entry.optJSONObject("state"));
            assetSpecs=entry.optJSONArray("assets");
            directory=new File(entry.optString("baseDir"));
        }

        void start() {
            if(closeRequested || closed || started || preparing || !current(this))return;
            if(assetSpecs!=null) {
                preparing=true;
                try { assets.execute(() -> {
                    Throwable failure=null;
                    try { prepareAssets(); } catch(Throwable unavailable) { failure=unavailable; }
                    final Throwable problem=failure;
                    main.post(() -> {
                        preparing=false;
                        if(closed){synchronized(stateLock){retiring.remove(this);}main.post(CustomPluginRuntime.this::reconcile);return;}
                        if(closeRequested || !current(this))return;
                         if(problem!=null) {
                             Plugin.this.failure=problem.toString();failureStack=stack(problem);
                             report("Plugin assets are temporarily unavailable: "+id,problem); assetFailures++;
                             lifecycleChanged();
                             if(assetFailures<3)main.postDelayed(this::start,250L*assetFailures);
                         } else {
                             assetFailures=0;Plugin.this.failure="";failureStack="";startPrepared();
                         }
                    });
                }); } catch(RejectedExecutionException stopped) { preparing=false; }
            } else startPrepared();
        }

        private void startPrepared() {
            if(closeRequested || closed || started || !current(this))return;
            if(!lock.tryLock()){main.postDelayed(this::startPrepared,16);return;}
            try {
                if(closeRequested || closed || started || !current(this))return;
                started=true; active=true;
                org.mozilla.javascript.Context cx=enter();
                try {
                    scope=new ImporterTopLevel(cx);
                    api=(ScriptableObject)cx.newObject(scope);
                    put(api,"context",org.mozilla.javascript.Context.javaToJS(owner,scope));
                    put(api,"moduleClassLoader",org.mozilla.javascript.Context.javaToJS(CustomPluginRuntime.class.getClassLoader(),scope));
                    put(api,"pluginDir",org.mozilla.javascript.Context.javaToJS(directory,scope));
                    appliedState=state.toString(); put(api,"state",toScript(cx,scope,state));
                    put(api,"host",toScript(cx,scope,CustomPluginSupport.describe(owner.getPackageName())));
                    function(api,"hasFeature",(context,args) -> CustomPluginSupport.FEATURES.contains(org.mozilla.javascript.Context.toString(arg(args,0))));
                    function(api,"define",(context,args) -> args.length == 0 ? Undefined.instance : args[0]);
                    function(api,"findClass",(context,args) -> wrapped(resolve(typeName(arg(args,0)),args.length>1 ? classLoader(args[1]) : null)));
                    function(api,"hook",(context,args) -> register(args,false,false));
                    function(api,"hookAll",(context,args) -> register(args,true,false));
                    function(api,"hookAllConstructors",(context,args) -> register(args,true,true));
                    function(api,"hookJava",(context,args) -> {
                        Object executable=unwrap(arg(args,0)), callback=unwrap(arg(args,1));
                        if(!(executable instanceof Executable)||!(callback instanceof InvocationHandler))
                            throw new IllegalArgumentException("hookJava requires an Executable and Java InvocationHandler");
                        return wrapped(hookJava((Executable)executable,(InvocationHandler)callback,
                                args.length>2&&org.mozilla.javascript.Context.toBoolean(args[2])));
                    });
                    function(api,"hookNative",(context,args) -> {
                        Object executable=unwrap(arg(args,0)), hooker=unwrap(arg(args,1));
                        if (!(executable instanceof Executable) || !(hooker instanceof XposedInterface.Hooker))
                            throw new IllegalArgumentException("hookNative requires an Executable and Java Hooker");
                        XposedInterface.HookHandle original=installer.hook((Executable)executable,(XposedInterface.Hooker)hooker);
                        if (original==null)throw new IllegalStateException("Framework returned no removable hook handle");
                        XposedInterface.HookHandle handle=new ManagedHandle(original,removed -> {
                            nativeHooks.decrementAndGet();synchronized(stateLock){retainedNativeHandles.remove(removed);}
                        });nativeHooks.incrementAndGet();
                        boolean retain=args.length>2 && org.mozilla.javascript.Context.toBoolean(args[2]);
                        if(retain)synchronized(stateLock){retainedNativeHandles.add(handle);}
                        else cleanups.add(handle::unhook);
                        return wrapped(handle);
                    });
                    function(api,"deoptimize",(context,args) -> {
                        Object executable=unwrap(arg(args,0));
                        if (!(executable instanceof Executable)) throw new IllegalArgumentException("Expected method or constructor");
                        installer.deoptimize((Executable)executable); return Undefined.instance;
                    });
                    function(api,"onCleanup",(context,args) -> {
                        Function callback=callback(arg(args,0)); ensureActive();
                        cleanups.add(() -> invoke(callback,new Object[0])); return Undefined.instance;
                    });
                    function(api,"runOnMain",(context,args) -> queue(callback(arg(args,0))));
                    function(api,"onClassLoader",(context,args) -> listen(callback(arg(args,0))));
                    function(api,"onStateChanged",(context,args) -> {
                        StateListener listener=new StateListener(callback(arg(args,0)));stateListeners.add(listener);
                        cleanups.add(() -> listener.active=false);return cancel(() -> listener.active=false);
                    });
                    function(api,"onAction",(context,args) -> {
                        String key=org.mozilla.javascript.Context.toString(arg(args,0));Function listener=callback(arg(args,1));
                        if(!declared("actions",key)||actionCallbacks.containsKey(key))throw new IllegalArgumentException("Undeclared or duplicate form action: "+key);
                        actionCallbacks.put(key,listener);Undo remove=()->{if(actionCallbacks.get(key)==listener)actionCallbacks.remove(key);};cleanups.add(remove);return cancel(remove);
                    });
                    function(api,"publishStatus",(context,args) -> {
                        Object json=context.evaluateString(scope,"JSON.stringify",id+"/form-status",1,null);
                        Object encoded=((Function)json).call(context,scope,scope,new Object[]{arg(args,0)});
                        if(encoded==Undefined.instance)throw new IllegalArgumentException("Status must be an object");
                        String text=org.mozilla.javascript.Context.toString(encoded);if(text.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Status is too large");
                        JSONObject update=new JSONObject(text);java.util.Iterator<String> keys=update.keys();
                        while(keys.hasNext()){String key=keys.next();Object value=update.get(key);if(!declared("statuses",key)||!(value instanceof String)||((String)value).length()>4096)throw new IllegalArgumentException("Undeclared or invalid read-only status: "+key);}
                        JSONObject merged=copyState(formStatus);keys=update.keys();while(keys.hasNext()){String key=keys.next();merged.put(key,update.get(key));}
                        if(merged.toString().getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Status is too large");
                        keys=update.keys();while(keys.hasNext()){String key=keys.next();formStatus.put(key,update.get(key));}
                        lifecycleChanged();
                        return Undefined.instance;
                    });
                    // App289 runtime.values: one-process values shared with the host and other plugins.
                    cleanups.add(() -> CustomPluginValues.clearOwner(id));
                    function(api,"publishValue",(context,args) -> {
                        String name=org.mozilla.javascript.Context.toString(arg(args,0));
                        Object raw=args.length>1?unwrap(args[1]):null;
                        CustomPluginValues.publish(id,name,raw==Undefined.instance?null:raw);return Undefined.instance;
                    });
                    function(api,"readValue",(context,args) -> {
                        String key=org.mozilla.javascript.Context.toString(arg(args,0));
                        if(!CustomPluginValues.validKey(key))throw new IllegalArgumentException("Invalid shared value key: "+key);
                        Object value=CustomPluginValues.get(key);return value==null?null:wrapped(value);
                    });
                    function(api,"observeValue",(context,args) -> {
                        String key=org.mozilla.javascript.Context.toString(arg(args,0));Function callback=callback(arg(args,1));
                        if(!CustomPluginValues.validKey(key))throw new IllegalArgumentException("Invalid shared value key: "+key);
                        ValueObserver observer=new ValueObserver(callback);
                        Runnable stop=CustomPluginValues.observe(key,value -> main.post(() -> fireValue(observer,value)));
                        Undo undo=() -> {observer.active=false;stop.run();};
                        cleanups.add(undo);
                        Object current=CustomPluginValues.get(key);
                        main.post(() -> fireValue(observer,current));
                        return cancel(undo);
                    });
                    function(api,"log",(context,args) -> { ModuleDiagnostics.info("plugin:"+id,args.length==0 ? "" : org.mozilla.javascript.Context.toString(args[0])); return Undefined.instance; });
                    function(api,"require",(context,args) -> require(context,org.mozilla.javascript.Context.toString(arg(args,0))));
                    function(api,"loadDex",(context,args) -> wrapped(loadDex(org.mozilla.javascript.Context.toString(arg(args,0)))));
                    function(api,"proxy",(context,args) -> proxy(arg(args,0),arg(args,1),args.length>2 && org.mozilla.javascript.Context.toBoolean(args[2])));
                    function(api,"save",(context,args) -> { save(context,org.mozilla.javascript.Context.toString(arg(args,0)),arg(args,1)); return Undefined.instance; });
                    function(api,"load",(context,args) -> load(context,org.mozilla.javascript.Context.toString(arg(args,0)),args.length>1?args[1]:null));
                    function(api,"dataDir",(context,args) -> wrapped(dataDir()));
                    put(scope,"C17",api);
                    cx.evaluateString(scope,"Object.freeze(C17.host.features);Object.freeze(C17.host);Object.defineProperty(C17,'host',{value:C17.host,writable:false,enumerable:true,configurable:false});",id+"/host-contract",1,null);
                    cx.evaluateString(scope,source,id+"/main.js",1,null);
                    ready=active && !closeRequested;
                } catch (Throwable failure) { fault(failure); }
                finally { org.mozilla.javascript.Context.exit(); }
            } finally { lock.unlock();lifecycleChanged(); }
        }

        private boolean declared(String kind,String key){
            JSONObject form=entry.optJSONObject("form");JSONArray names=form==null?null:form.optJSONArray(kind);
            if(names!=null)for(int i=0;i<names.length();i++)if(key!=null&&key.equals(names.optString(i)))return true;return false;
        }
        private JSONObject formSnapshot()throws org.json.JSONException{
            JSONArray actions=new JSONArray();for(String key:actionCallbacks.keySet())actions.put(key);
            return new JSONObject().put("id",id).put("instanceId",instanceId).put("status",copyState(formStatus)).put("actions",actions);
        }

        void applyState() {
            if(!active || closeRequested || stateQueued || appliedState.equals(state.toString()))return;
            if(!lock.tryLock()) {
                stateQueued=true;
                main.postDelayed(() -> {stateQueued=false;applyState();},16);return;
            }
            try {
                if(!active || closeRequested || appliedState.equals(state.toString()))return;
                org.mozilla.javascript.Context cx=enter();
                try {
                    Object previous=ScriptableObject.getProperty(api,"state"),next=toScript(cx,scope,state);
                    appliedState=state.toString();put(api,"state",next);
                    for(StateListener listener:new ArrayList<>(stateListeners))if(listener.active && active)
                        listener.callback.call(cx,scope,scope,new Object[]{next,previous});
                } catch(Throwable failure) { fault(failure); }
                finally {org.mozilla.javascript.Context.exit();}
            } finally {lock.unlock();}
        }

        private void function(ScriptableObject api,String name,Call operation) {
            BaseFunction function=new BaseFunction(scope,ScriptableObject.getFunctionPrototype(scope)) {
                @Override public Object call(org.mozilla.javascript.Context cx,Scriptable parent,Scriptable self,Object[] args) {
                    lock.lock(); try {
                        if(!(closing && (name.equals("save")||name.equals("load")||name.equals("dataDir")||name.equals("log")||name.equals("findClass"))))ensureActive();
                        try { return operation.run(cx,args); }
                        catch (Throwable failure) { throw org.mozilla.javascript.Context.throwAsScriptRuntimeEx(failure); }
                    } finally {lock.unlock();}
                }
            };
            put(api,name,function);
        }

        private Object wrapped(Object value) { return org.mozilla.javascript.Context.javaToJS(value,scope); }
        private void ensureActive() { if (!active) throw new IllegalStateException("Plugin is no longer active"); }

        private Object invoke(Function function,Object[] arguments) {
            org.mozilla.javascript.Context cx=enter();
            try { return function.call(cx,scope,scope,arguments); }
            finally { org.mozilla.javascript.Context.exit(); }
        }

        private Object register(Object[] args,boolean all,boolean constructors) throws Throwable {
            ensureActive(); Registration registration;
            Object first=unwrap(arg(args,0));
            if (first instanceof Executable && !all) {
                registration=new Registration((Executable)first,hookCallback(arg(args,1)));
            } else {
                String className=typeName(arg(args,0));
                if (constructors) registration=new Registration(className,"<init>",null,true,hookCallback(arg(args,1)));
                else if (all) registration=new Registration(className,org.mozilla.javascript.Context.toString(arg(args,1)),null,true,hookCallback(arg(args,2)));
                else registration=new Registration(className,org.mozilla.javascript.Context.toString(arg(args,1)),strings(arg(args,2)),false,hookCallback(arg(args,3)));
            }
            registrations.add(registration); cleanups.add(registration::close);
            for (ClassLoader loader : allLoaders()) registration.resolve(loader);
            counters();
            return cancel(() -> registration.close());
        }

        private Object cancel(Undo undo) {
            return new BaseFunction(scope,ScriptableObject.getFunctionPrototype(scope)) {
                @Override public Object call(org.mozilla.javascript.Context cx,Scriptable parent,Scriptable self,Object[] args) {
                    lock.lock(); try {
                        try { undo.run(); } catch (Throwable failure) { report("Plugin cancellation failed: "+id,failure); }
                    } finally {lock.unlock();}
                    return Undefined.instance;
                }
            };
        }

        private Object queue(Function callback) {
            final boolean[] scheduled={true};
            Runnable task=new Runnable(){public void run(){
                if(!active || !scheduled[0])return;
                if(!lock.tryLock()){main.postDelayed(this,16);return;}
                try {
                    if (!active || !scheduled[0]) return;
                    scheduled[0]=false;
                    try { invoke(callback,new Object[0]); } catch (Throwable failure) { fault(failure); }
                } finally {lock.unlock();}
            }};
            Undo cancel=() -> {scheduled[0]=false;main.removeCallbacks(task);};
            main.post(task); cleanups.add(cancel);
            return cancel(cancel);
        }

        /** Main thread: one shared value delivered under the plugin lock, never after its scope ended. */
        private void fireValue(ValueObserver observer,Object value) {
            if(!active||!observer.active)return;
            if(!lock.tryLock()){main.postDelayed(() -> fireValue(observer,value),16);return;}
            try {
                if(!active||!observer.active)return;
                try { invoke(observer.callback,new Object[]{value==null?null:wrapped(value)}); } catch (Throwable failure) { fault(failure); }
            } finally {lock.unlock();}
        }

        private Object listen(Function callback) {
            LoaderListener listener=new LoaderListener(callback); loaderListeners.add(listener);
            cleanups.add(() -> listener.active=false);
            onMain(() -> fireListener(listener));
            return cancel(() -> listener.active=false);
        }

        private void fireListener(LoaderListener listener) {
            if(!active || closeRequested || !listener.active)return;
            if(!lock.tryLock()){main.postDelayed(() -> fireListener(listener),16);return;}
            try {for(ClassLoader loader:currentLoaders())listener.fire(loader);}
            finally{lock.unlock();}
        }

        void loaderReady(ClassLoader loader) {
            if(!active || closeRequested)return;
            if(!lock.tryLock()){main.postDelayed(() -> loaderReady(loader),16);return;}
            try {
                if (!active) return;
                for (Registration registration : new ArrayList<>(registrations)) registration.resolve(loader);
                for (LoaderListener listener : new ArrayList<>(loaderListeners)) listener.fire(loader);
                counters();
            } finally {lock.unlock();}
        }

        private List<ClassLoader> allLoaders() {
            List<ClassLoader> out=currentLoaders(); out.addAll(dexLoaders); return out;
        }

        private Class<?> resolve(String name,ClassLoader explicit) throws ClassNotFoundException {
            Class<?> primitive=primitive(name); if (primitive != null) return primitive;
            if (name.endsWith("[]")) return Array.newInstance(resolve(name.substring(0,name.length()-2),explicit),0).getClass();
            if (explicit != null) return Class.forName(name,false,explicit);
            ClassNotFoundException last=null;
            try{return Class.forName(name,false,CustomPluginRuntime.class.getClassLoader());}
            catch(ClassNotFoundException missing){last=missing;}
            for (ClassLoader loader : allLoaders()) try { return Class.forName(name,false,loader); }
            catch (ClassNotFoundException missing) { last=missing; }
            throw last == null ? new ClassNotFoundException(name) : last;
        }

        private Object require(org.mozilla.javascript.Context cx,String relative) throws IOException {
            return require(cx,relative,directory);
        }

        private Object require(org.mozilla.javascript.Context cx,String relative,File from) throws IOException {
            File file=new File(relative);if(!file.isAbsolute())file=new File(from,relative);
            if(!file.isFile() && !relative.endsWith(".js"))file=new File(file.getPath()+".js");
            file=file.getCanonicalFile();String key=file.getPath();final File parentDirectory=file.getParentFile();
            if (modules.containsKey(key)) return modules.get(key);
            Scriptable module=cx.newObject(scope), exports=cx.newObject(scope); put(module,"exports",exports);
            modules.put(key,exports); // CommonJS cycles observe the initial exports object.
            try {
                Function function=cx.compileFunction(scope,"function(exports,module,require,__filename,__dirname){\n"+read(file)+"\n}",key,1,null);
                BaseFunction require=new BaseFunction(scope,ScriptableObject.getFunctionPrototype(scope)) {
                    @Override public Object call(org.mozilla.javascript.Context context,Scriptable parent,Scriptable self,Object[] args){
                        lock.lock();try{ensureActive();try{return require(context,org.mozilla.javascript.Context.toString(arg(args,0)),parentDirectory);}
                            catch(IOException failure){throw org.mozilla.javascript.Context.throwAsScriptRuntimeEx(failure);}}
                        finally{lock.unlock();}
                    }
                };
                function.call(cx,scope,scope,new Object[]{exports,module,require,key,file.getParent()});
                Object answer=ScriptableObject.getProperty(module,"exports"); modules.put(key,answer); return answer;
            } catch (RuntimeException failure) { modules.remove(key); throw failure; }
        }

        private File resolveFile(String path) throws IOException {
            File file=new File(path); if (!file.isAbsolute()) file=new File(directory,path);
            return file.getCanonicalFile();
        }

        private ClassLoader loadDex(String path) throws Exception {
            File file=resolveFile(path), cache=new File(dataDir(),"dex-cache");
            if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("Unable to create plugin dex cache");
            // Android 14+ requires dynamically loaded bytecode to be read-only before loading.
            if (!file.setReadOnly() && file.canWrite()) throw new IOException("Plugin bytecode is not read-only");
            // Framework and module contracts must share the host's Class identity. A raw
            // SystemUI/OEM loader cannot resolve libxposed API classes supplied to this module.
            ClassLoader parent=dexParent(CustomPluginRuntime.class.getClassLoader(),
                    XposedInterface.class.getClassLoader(),CustomPluginRuntime.this::currentLoaders);
            ClassLoader loader=new DexClassLoader(file.getPath(),cache.getPath(),file.getParent(),parent);
            dexLoaders.add(loader); onMain(() -> loaderReady(loader)); return loader;
        }

        /** JDK-only boundary for frameworks that prohibit dynamic access to their API classes. */
        private Runnable hookJava(Executable executable,InvocationHandler callback,boolean retain)throws Throwable {
            final Class<?>[] parameters=executable.getParameterTypes();
            XposedInterface.HookHandle original=installer.hook(executable,chain->{
                Once once=new Once(chain,parameters,lock);
                Object receiver=chain.getThisObject();
                once.nativeReceiver=receiver;
                Object[] frame={receiver,chain.getArgs().toArray(),once,executable};
                try{return callback.invoke(receiver,executable instanceof Method?(Method)executable:null,frame);}
                finally{once.closeNative();}
            });
            if(original==null)throw new IllegalStateException("Framework returned no removable hook handle");
            XposedInterface.HookHandle handle=new ManagedHandle(original,removed->{
                nativeHooks.decrementAndGet();synchronized(stateLock){retainedNativeHandles.remove(removed);}
            });
            nativeHooks.incrementAndGet();
            if(retain)synchronized(stateLock){retainedNativeHandles.add(handle);}
            else cleanups.add(handle::unhook);
            // Expose only Runnable. Reflecting a framework HookHandle may itself be prohibited.
            return handle::unhook;
        }

        private File dataDir() throws Exception {
            if (dataDirectory == null) {
                byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
                StringBuilder key=new StringBuilder(); for (byte value:digest) key.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                dataDirectory=new File(new File(storageRoot(owner),"data"),key.toString());
                if (!dataDirectory.isDirectory() && !dataDirectory.mkdirs()) throw new IOException("Unable to create plugin storage");
            }
            return dataDirectory;
        }

        private void prepareAssets() throws Exception {
            JSONArray assets=assetSpecs;
            if (assets==null) return; // Desktop callers may provide an already readable temporary directory.
            File root=storageRoot(owner);
            File version=new File(new File(root,id),String.valueOf(entry.getLong("versionCode")));
            // From here on this process may read, map or execute files of this copy.
            CustomPluginStorage.opened(version);
            String base=version.getCanonicalPath()+File.separator;
            for (int i=0;i<assets.length();i++) {
                JSONObject asset=assets.getJSONObject(i);String relative=asset.getString("path");
                File target=new File(version,relative).getCanonicalFile();
                if (!target.getPath().startsWith(base)) throw new IOException("Plugin asset escapes its directory");
                long bytes=asset.getLong("bytes");String expected=asset.getString("sha256");
                String frameworkFile=asset.optString("frameworkFile","");
                if(asset.has("frameworkFile")&&(!expected.matches("[0-9a-f]{64}")
                        || !frameworkFile.equals("c17-plugin-asset-"+expected)))
                    throw new IOException("Invalid shared plugin asset reference");
                String cacheKey=CompanionCacheClient.key("plugin-resource-v1",id+":"+entry.getLong("versionCode")+":"+relative+":"+expected);
                if (target.isFile() && target.length()==bytes && hash(target).equalsIgnoreCase(expected)) {
                    CompanionCacheClient.seedFile(cacheKey,target,bytes);continue;
                }
                byte[] prepared=bytes>0&&bytes<=4*1024*1024?CompanionCacheClient.get(cacheKey,(int)bytes):null;
                if(prepared!=null&&(prepared.length!=bytes||!hex(java.security.MessageDigest.getInstance("SHA-256").digest(prepared)).equalsIgnoreCase(expected)))prepared=null;
                File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Unable to create plugin asset directory");
                File temp=File.createTempFile("asset-",".tmp",parent);boolean committed=false;
                try {
                    if(prepared!=null)writeVerifiedAsset(temp,new ByteArrayInputStream(prepared),bytes,expected);
                    else copyAsset(temp,relative,frameworkFile,bytes,expected);
                    if(!temp.setReadOnly() && temp.canWrite())throw new IOException("Unable to protect plugin asset");
                    if(!temp.renameTo(target))throw new IOException("Unable to commit plugin asset");committed=true;
                    if(prepared==null)CompanionCacheClient.seedFile(cacheKey,target,bytes);
                } finally {if(!committed)temp.delete();}
            }
            directory=version;
            retireCopies(root,version);
        }

        /**
         * Runs on the resource queue while this instance still counts as preparing, so ownership removal of
         * the same plugin waits for it. Only a complete copy that is still wanted retires its siblings, and
         * a sweep that could not finish never fails the start.
         */
        private void retireCopies(File root,File version) {
            if(closeRequested || closed || !current(this))return;
            try {
                int removed=CustomPluginStorage.pruneVersions(root,id,version.getName());
                if(removed>0)ModuleDiagnostics.info("custom_plugin_runtime","Removed "+removed+" unused plugin resource copies");
            } catch(Exception retained) {
                ModuleDiagnostics.error("custom_plugin_runtime","Unused plugin resource copies were not fully removed",retained);
            }
        }

        private void copyAsset(File temp,String relative,String frameworkFile,long bytes,String expected) throws Exception {
            Throwable frameworkFailure=null;
            if(!frameworkFile.isEmpty())try {
                ParcelFileDescriptor descriptor=installer.openFrameworkFile(frameworkFile);
                if(descriptor==null)throw new FileNotFoundException(frameworkFile);
                writeVerifiedAsset(temp,new ParcelFileDescriptor.AutoCloseInputStream(descriptor),bytes,expected);
                return;
            } catch(Exception | LinkageError | io.github.libxposed.api.error.XposedFrameworkError unavailable) {
                frameworkFailure=unavailable;
            }
            StringBuilder uri=new StringBuilder("content://"+StatusBarSettings.AUTHORITY+"/custom-plugins/");
            if(entry.has("assetPackage"))uri.append("apps/").append(encoded(entry.getString("assetPackage"))).append('/');
            else uri.append("assets/");
            uri.append(encoded(id)).append('/').append(entry.getLong("versionCode"));
            for(String segment:relative.split("/"))uri.append('/').append(encoded(segment));
            try {
                writeVerifiedAsset(temp,owner.getContentResolver().openInputStream(android.net.Uri.parse(uri.toString())),bytes,expected);
            } catch(IOException | RuntimeException unavailable) {
                if(frameworkFailure!=null&&frameworkFailure!=unavailable)unavailable.addSuppressed(frameworkFailure);
                throw unavailable;
            }
        }

        private void writeVerifiedAsset(File temp,InputStream source,long bytes,String expected) throws Exception {
            if(source==null)throw new IOException("Plugin asset provider returned no stream");
            try(InputStream input=source;FileOutputStream output=new FileOutputStream(temp)) {
                java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
                long count=0;
                byte[] buffer=new byte[8192];int read;
                while((read=input.read(buffer))!=-1) {
                    count+=read;
                    if(count>bytes)throw new IOException("Plugin asset exceeds declared size");
                    digest.update(buffer,0,read);
                    output.write(buffer,0,read);
                }
                if(count!=bytes || !hex(digest.digest()).equalsIgnoreCase(expected))throw new IOException("Plugin asset integrity mismatch");
                output.getFD().sync();
            }
        }

        private String hash(File file) throws Exception {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(FileInputStream input=new FileInputStream(file)){byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);}return hex(digest.digest());
        }

        private File storage(String name) throws Exception {
            String key=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
            return new File(dataDir(),key+".json");
        }

        private void save(org.mozilla.javascript.Context cx,String name,Object value) throws Exception {
            File file=storage(name), temp=new File(file.getPath()+".tmp");
            Object json=cx.evaluateString(scope,"JSON.stringify",id+"/storage",1,null);
            Object text=((Function)json).call(cx,scope,scope,new Object[]{value});
            if (text == Undefined.instance) throw new IllegalArgumentException("Stored value must be JSON");
            try (FileOutputStream out=new FileOutputStream(temp)) { out.write(org.mozilla.javascript.Context.toString(text).getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
            try{java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            finally{if(temp.exists())temp.delete();}
        }

        private Object load(org.mozilla.javascript.Context cx,String name,Object fallback) throws Exception {
            File file=storage(name); if (!file.isFile()) return fallback;
            Function parse=(Function)cx.evaluateString(scope,"JSON.parse",id+"/storage",1,null);
            return parse.call(cx,scope,scope,new Object[]{read(file)});
        }

        private Object proxy(Object requested,Object callbacks,boolean propagateErrors) throws Exception {
            Object raw=unwrap(requested); Class<?> type=raw instanceof Class ? (Class<?>)raw : resolve(typeName(requested),null);
            if (!type.isInterface()) throw new IllegalArgumentException("C17.proxy requires a Java interface; use loadDex for subclasses");
            InvocationHandler handler=(object,method,args) -> {
                if (method.getDeclaringClass()==Object.class) {
                    if (method.getName().equals("toString")) return "C17 proxy "+id+":"+type.getName();
                    if (method.getName().equals("hashCode")) return System.identityHashCode(object);
                    if (method.getName().equals("equals")) return object==args[0];
                }
                lock.lock();try {
                    if (!active) return defaultValue(method.getReturnType());
                    Object callback=callbacks instanceof Function ? callbacks : callbacks instanceof Scriptable ? ScriptableObject.getProperty((Scriptable)callbacks,method.getName()) : null;
                    if (!(callback instanceof Function)) return defaultValue(method.getReturnType());
                    org.mozilla.javascript.Context cx=enter();
                    try {
                        Object[] input=args==null?new Object[0]:args.clone();
                        for (int i=0;i<input.length;i++) input[i]=wrapped(input[i]);
                        Object answer=((Function)callback).call(cx,scope,scope,input);
                        return converted(answer,method.getReturnType());
                    } catch (Throwable failure) {
                        // A DEX bridge can own optional-contract rollback. Let that Java caller
                        // handle its exception before deciding whether the whole plugin failed.
                        if(propagateErrors)throw org.mozilla.javascript.Context.throwAsScriptRuntimeEx(failure);
                        fault(failure);return defaultValue(method.getReturnType());
                    }
                    finally { org.mozilla.javascript.Context.exit(); }
                } finally {lock.unlock();}
            };
            return wrapped(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
        }

        void close() {
            active=false;ready=false;closeRequested=true;
            // A synchronous proxy can fail inside entry evaluation. Keep its scope until
            // the outer script unwinds, so the original failure is not replaced by a null scope.
            if(lock.isHeldByCurrentThread())main.post(this::closeMain);else onMain(this::closeMain);
        }

        private void closeMain() {
            if(closed)return;
            if(!lock.tryLock()) {
                if(!closeQueued) {closeQueued=true;main.postDelayed(() -> {closeQueued=false;closeMain();},16);}
                return;
            }
            try {
                if(closed)return;
                closing=true;
                for (int i=cleanups.size()-1;i>=0;i--) try { cleanups.get(i).run(); }
                catch (Throwable failure) { report("Plugin cleanup failed: "+id,failure); }
                cleanups.clear(); loaderListeners.clear(); stateListeners.clear(); modules.clear(); dexLoaders.clear();
                actionResults.clear();actionResultTimes.clear();consumedActions.clear();actionCallbacks.clear();scope=null;api=null;
                closing=false;closed=true;hookCount=0;pendingCount=0;
            } finally {lock.unlock();}
            synchronized(stateLock){if(!preparing)retiring.remove(this);}
            lifecycleChanged();
            main.post(CustomPluginRuntime.this::reconcile);
        }

        private void fault(Throwable failure) {
            if(closeRequested || closed)return;
            this.failure=failure.toString();failureStack=stack(failure);
            report("Plugin stopped after an error: "+id,failure); close();
        }

        private void counters() {
            int hooks=0,pending=0;
            for(Registration registration:registrations) {
                hooks+=registration.handles.size();
                if(registration.active && registration.handles.isEmpty())pending++;
            }
            hookCount=hooks;pendingCount=pending;
        }

        private final class StateListener {
            final Function callback; boolean active=true;
            StateListener(Function callback){this.callback=callback;}
        }

        private final class LoaderListener {
            final Function function;
            final Map<ClassLoader,Boolean> delivered=new java.util.WeakHashMap<>();
            boolean active=true;
            LoaderListener(Function function) { this.function=function; }
            void fire(ClassLoader loader) {
                if (!active || !Plugin.this.active || delivered.containsKey(loader)) return;
                delivered.put(loader,true);
                org.mozilla.javascript.Context cx=enter();
                try { function.call(cx,scope,scope,new Object[]{org.mozilla.javascript.Context.javaToJS(loader,scope)}); }
                catch (Throwable failure) { fault(failure); }
                finally { org.mozilla.javascript.Context.exit(); }
            }
        }

        private final class Registration {
            final Executable exact;
            final String className,name;
            final String[] parameterNames;
            final boolean all;
            final Object function;
            final Map<Executable,XposedInterface.HookHandle> handles=new LinkedHashMap<>();
            boolean active=true;
            Registration(Executable exact,Object function) { this.exact=exact; this.function=function; className=name=null;parameterNames=null;all=false; }
            Registration(String className,String name,String[] parameters,boolean all,Object function) {
                exact=null;this.className=className;this.name=name;parameterNames=parameters;this.all=all;this.function=function;
            }
            void resolve(ClassLoader loader) {
                if (!active || !Plugin.this.active) return;
                try {
                    if (exact != null) { install(exact); return; }
                    Class<?> type=Class.forName(className,false,loader);
                    if (all) {
                        if (name.equals("<init>")) for (Constructor<?> constructor:type.getDeclaredConstructors()) install(constructor);
                        else for (Method method:type.getDeclaredMethods()) if (method.getName().equals(name)) install(method);
                    } else {
                        Class<?>[] parameters=new Class<?>[parameterNames.length];
                        for (int i=0;i<parameters.length;i++) parameters[i]=Plugin.this.resolve(parameterNames[i],loader);
                        if (name.equals("<init>")) install(type.getDeclaredConstructor(parameters));
                        else {
                            Method found=null;
                            for (Class<?> cursor=type;cursor!=null && found==null;cursor=cursor.getSuperclass())
                                try { found=cursor.getDeclaredMethod(name,parameters); } catch (NoSuchMethodException missing) { }
                            if (found == null) throw new NoSuchMethodException(className+"."+name);
                            install(found);
                        }
                    }
                } catch (ClassNotFoundException | NoSuchMethodException unavailable) {
                    // OEM classes may be supplied later; rememberLoader retries this exact request.
                } catch (Throwable failure) { report("Plugin hook could not be installed: "+id,failure); }
            }
            private void install(Executable executable) throws Throwable {
                if (handles.containsKey(executable)) return;
                // Framework hooking does not require reflective invocation permission. On desktop
                // checks JDK modules can refuse setAccessible even for an otherwise hookable method.
                try { executable.setAccessible(true); } catch (RuntimeException inaccessible) { }
                final HookTrace.Site site=HookTrace.site(traceOwner(id),executable);
                final XposedInterface.Hooker body=chain -> intercept(executable,chain);
                XposedInterface.HookHandle handle=installer.hook(executable,
                        chain -> HookTrace.on()?HookTrace.run(site,body,chain):intercept(executable,chain));
                if (handle == null) throw new IllegalStateException("Framework returned no removable hook handle");
                handles.put(executable,removable(handle));counters();
            }
            private Object intercept(Executable executable,XposedInterface.Chain original) throws Throwable {
                if (!active || !Plugin.this.active) return original.proceed();
                lock.lock();try {
                    // Once releases the execution lock around native work, including fallback.
                    Once once=new Once(original,executable.getParameterTypes(),lock);
                    if (!active || !Plugin.this.active)return once.proceed(null);
                    NativeChain chain=new NativeChain(original,once);
                    org.mozilla.javascript.Context cx=enter();
                    try {
                        hits++;
                        Object answer=function instanceof XposedInterface.Hooker
                                ? ((XposedInterface.Hooker)function).intercept(chain)
                                : ((Function)function).call(cx,scope,scope,new Object[]{wrapped(chain)});
                        if(!Plugin.this.active || !active)return once.proceed(null);
                        Class<?> result=executable instanceof Method?((Method)executable).getReturnType():Object.class;
                        return converted(answer,result);
                    } catch (Throwable failure) {
                        // Native exceptions retain identity and native work is never run a second time.
                         if (once.failure != null) throw once.failure;
                         if(!Plugin.this.active || !active)return once.proceed(null);
                         fault(failure);
                        return once.proceed(null);
                    } finally { once.valid=false; org.mozilla.javascript.Context.exit(); }
                } finally {lock.unlock();}
            }
            void close() {
                active=false;
                List<XposedInterface.HookHandle> reversed=new ArrayList<>(handles.values()); Collections.reverse(reversed);
                for (XposedInterface.HookHandle handle:reversed) try { handle.unhook(); }
                catch (Throwable failure) { report("Plugin hook removal failed; retained callback is inert: "+id,failure); }
                handles.clear();counters();
            }
        }
    }

    /** A callback may call proceed repeatedly, or throw after proceeding: native still executes once. */
    private static final class Once implements InvocationHandler {
        XposedInterface.Chain original;
        Class<?>[] parameters;
        ReentrantLock executionLock;
        Thread owner=Thread.currentThread();
        boolean called;
        Object value;
        Throwable failure;
        volatile boolean valid=true;
        Object nativeReceiver;
        Once(XposedInterface.Chain original,Class<?>[] parameters,ReentrantLock executionLock) {
            this.original=original;this.parameters=parameters;this.executionLock=executionLock;
        }
        // The frame already owns a one-shot continuation. Reuse it as the JDK
        // callback instead of allocating an AtomicReference, holder array and lambda.
        @Override public Object invoke(Object receiver,Method ignored,Object[] arguments)throws Throwable {
            return proceedWith(receiver,arguments,receiver!=nativeReceiver);
        }
        void closeNative() {
            valid=false;
            original=null;nativeReceiver=null;value=null;failure=null;
            parameters=null;executionLock=null;owner=null;
        }
        Object proceed(Object[] arguments) throws Throwable {
            return proceedWith(null,arguments,false);
        }
        Object proceedWith(Object receiver,Object[] arguments,boolean replaced) throws Throwable {
            if (!valid) throw new IllegalStateException("Hook chain is only valid during its callback");
            if(Thread.currentThread()!=owner)throw new IllegalStateException("Hook chain belongs to its callback thread");
            if (!called) {
                // Bridge conversion failures are plugin failures, not a native exception. They
                // must leave a valid original fallback instead of poisoning the single native call.
                Object[] converted=null;
                if(arguments!=null) {
                    if(arguments.length!=parameters.length)throw new IllegalArgumentException("Hook argument count mismatch");
                    converted=new Object[arguments.length];
                    for(int i=0;i<converted.length;i++)converted[i]=converted(arguments[i],parameters[i]);
                }
                called=true;
                int held=executionLock.getHoldCount();
                for(int i=0;i<held;i++)executionLock.unlock();
                try {
                    if (arguments==null) value=replaced?original.proceedWith(receiver):original.proceed();
                    else value=replaced?original.proceedWith(receiver,converted):original.proceed(converted);
                } catch (Throwable problem) { failure=problem; }
                finally {for(int i=0;i<held;i++)executionLock.lock();}
            }
            if (failure != null) throw failure;
            return value;
        }
    }

    private static XposedInterface.HookHandle removable(XposedInterface.HookHandle original) {
        return new ManagedHandle(original,null);
    }

    /** Shared by explicit plugin rollback and automatic cleanup; a successful unhook happens once. */
    public static final class ManagedHandle implements XposedInterface.HookHandle {
        private XposedInterface.HookHandle original;
        private java.util.function.Consumer<ManagedHandle> onRemoved;
        private final Executable executable;
        private volatile boolean removed;
        ManagedHandle(XposedInterface.HookHandle original,java.util.function.Consumer<ManagedHandle> onRemoved){this.original=original;this.executable=original.getExecutable();this.onRemoved=onRemoved;}
        @Override public Executable getExecutable(){return executable;}
        @Override public synchronized void unhook(){
            if(removed)return;original.unhook();removed=true;original=null;
            java.util.function.Consumer<ManagedHandle> callback=onRemoved;onRemoved=null;if(callback!=null)callback.accept(this);
        }
    }

    /** Public for Rhino/Java reflection; keeps the full libxposed Chain contract for DEX plugins. */
    public static final class NativeChain implements XposedInterface.Chain {
        private final XposedInterface.Chain original;
        private final Once once;
        public final Object thisObject,receiver;
        public final Executable method;
        public final Object[] args;
        NativeChain(XposedInterface.Chain original,Once once) {
            this.original=original;this.once=once;thisObject=receiver=original.getThisObject();method=original.getExecutable();args=original.getArgs().toArray();
        }
        public Executable getExecutable(){return method;}
        public Object getThisObject(){return thisObject;}
        public List<Object> getArgs(){return java.util.Arrays.asList(args);}
        public Object getArg(int index){return args[index];}
        public Object proceed()throws Throwable{return once.proceed(args);}
        public Object proceed(Object[] changed)throws Throwable{return once.proceed(changed);}
        public Object proceedWith(Object changed)throws Throwable{return once.proceedWith(changed,args,true);}
        public Object proceedWith(Object changed,Object[] arguments)throws Throwable{return once.proceedWith(changed,arguments,true);}
    }

    private static org.mozilla.javascript.Context enter() {
        org.mozilla.javascript.Context cx=org.mozilla.javascript.Context.enter();
        cx.setOptimizationLevel(-1); cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
        cx.setApplicationClassLoader(CustomPluginRuntime.class.getClassLoader()); return cx;
    }
    private static String stack(Throwable failure) {
        java.io.StringWriter text=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(text));return text.toString();
    }
    private static Object unwrap(Object value) { while (value instanceof Wrapper) value=((Wrapper)value).unwrap(); return value; }
    private static Object converted(Object value,Class<?> type) {
        if (type==void.class) return null;
        value=unwrap(value); if (value==Undefined.instance) value=null;
        if (type==Object.class) return value;
        return org.mozilla.javascript.Context.jsToJava(value,type);
    }
    private static Function callback(Object value) { if (!(value instanceof Function)) throw new IllegalArgumentException("Expected JavaScript callback"); return (Function)value; }
    private static Object hookCallback(Object value) { Object raw=unwrap(value);if(raw instanceof XposedInterface.Hooker)return raw;return callback(value); }
    private static Object arg(Object[] args,int index) { if (index>=args.length) throw new IllegalArgumentException("Missing C17 bridge argument"); return args[index]; }
    private static ClassLoader classLoader(Object value) { Object raw=unwrap(value); if (!(raw instanceof ClassLoader)) throw new IllegalArgumentException("Expected ClassLoader");return (ClassLoader)raw; }
    private static String typeName(Object value) { Object raw=unwrap(value); return raw instanceof Class ? ((Class<?>)raw).getName() : org.mozilla.javascript.Context.toString(value); }
    private static String[] strings(Object value) { Object[] array=elements(value);String[] out=new String[array.length];for(int i=0;i<out.length;i++)out[i]=typeName(array[i]);return out; }
    private static Object[] elements(Object value) {
        value=unwrap(value);
        if (value instanceof NativeArray) { NativeArray array=(NativeArray)value;Object[] out=new Object[(int)array.getLength()];for(int i=0;i<out.length;i++)out[i]=array.get(i,array);return out; }
        if (value instanceof List) return ((List<?>)value).toArray();
        if (value!=null && value.getClass().isArray()) { int length=Array.getLength(value);Object[] out=new Object[length];for(int i=0;i<length;i++)out[i]=Array.get(value,i);return out; }
        throw new IllegalArgumentException("Expected argument array");
    }
    private static void put(Scriptable object,String name,Object value) { ScriptableObject.putProperty(object,name,value); }
    private static Object toScript(org.mozilla.javascript.Context cx,Scriptable scope,Object value) {
        if (value==null || value==JSONObject.NULL) return null;
        if (value instanceof JSONObject) {
            Scriptable out=cx.newObject(scope);JSONObject object=(JSONObject)value;
            for (java.util.Iterator<String> keys=object.keys();keys.hasNext();) { String key=keys.next();put(out,key,toScript(cx,scope,object.opt(key))); } return out;
        }
        if (value instanceof JSONArray) { JSONArray array=(JSONArray)value;Object[] out=new Object[array.length()];for(int i=0;i<out.length;i++)out[i]=toScript(cx,scope,array.opt(i));return cx.newArray(scope,out); }
        return value;
    }
    private static String read(File file) throws IOException {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try (FileInputStream input=new FileInputStream(file)) { byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count); }
        return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
    }
    private static Class<?> primitive(String name) {
        switch(name) { case "boolean":return boolean.class;case "byte":return byte.class;case "char":return char.class;case "short":return short.class;case "int":return int.class;case "long":return long.class;case "float":return float.class;case "double":return double.class;case "void":return void.class;default:return null; }
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type==void.class) return null;
        if (type==boolean.class) return false;if(type==char.class)return '\0';if(type==byte.class)return (byte)0;if(type==short.class)return(short)0;
        if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0f;return 0d;
    }
    private static String hex(byte[] digest){StringBuilder text=new StringBuilder();for(byte value:digest)text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();}
    private static String encoded(String text)throws java.io.UnsupportedEncodingException{return java.net.URLEncoder.encode(text,"UTF-8").replace("+","%20");}
}
