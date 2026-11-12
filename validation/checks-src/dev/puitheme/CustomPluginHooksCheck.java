// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import com.android.systemui.plugins.C17PluginHost;
import io.github.libxposed.api.XposedInterface;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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

/** Exercises real plan parsing, API101 callbacks, removal, native restoration and isolation. */
public final class CustomPluginHooksCheck {
    private static int checks;
    private static final String HOST = C17PluginHost.class.getName();
    interface Original { Object run() throws Throwable; }
    interface Rejected { void run() throws Exception; }
    private static void equal(Object expected,Object actual,String why) {
        checks++; if (!Objects.equals(expected,actual)) throw new AssertionError(why + ": " + expected + " != " + actual);
    }
    private static void rejected(Rejected operation,String why) throws Exception {
        try { operation.run(); } catch (IllegalArgumentException | ReflectiveOperationException expected) { checks++; return; }
        throw new AssertionError("Expected rejection: " + why);
    }
    private static JSONObject rule(String id,String method,String kind) throws Exception {
        return new JSONObject().put("id",id).put("className",HOST).put("method",method).put("argCount",0).put("kind",kind);
    }
    private static JSONObject property(String id,String name,double value) throws Exception {
        return rule(id,"update","property").put("property",name).put("value",value);
    }
    private static JSONObject plan(JSONObject... rules) throws Exception {
        JSONArray hooks = new JSONArray(); for (JSONObject rule : rules) hooks.put(new JSONObject(rule.toString()).put("pluginId","sample"));
        return new JSONObject().put("revision",1).put("settings",new JSONObject()).put("hooks",hooks);
    }
    private static void validate(JSONObject rule) throws Exception {
        CustomPluginRules.validate(new JSONObject().put("hooks",new JSONArray().put(rule)));
    }
    private static CustomPluginRules.Rule parsed(JSONObject rule) throws Exception {
        validate(rule); return CustomPluginRules.parse(new JSONArray().put(rule),false).get(0);
    }
    private static final class Framework {
        final Map<Method,XposedInterface.Hooker> hooks = new LinkedHashMap<>();
        int installed,removed; boolean refuseRemoval;
        final CustomPluginHooks runtime = new CustomPluginHooks(new Handler(Looper.getMainLooper()));
        Framework() {
            runtime.configure(new Context(),(method,hooker) -> {
                equal(false,hooks.containsKey(method),"no duplicate method install"); installed++; hooks.put(method,hooker);
                return (XposedInterface.HookHandle) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},
                        (owner,event,args) -> {
                            if (event.getName().equals("unhook")) {
                                if (refuseRemoval) throw new IllegalStateException("transient test refusal");
                                removed++; hooks.remove(method); return null;
                            }
                            if (event.getName().equals("getExecutable")) return method;
                            throw new AssertionError(event.getName());
                        });
            },null);
            runtime.rememberLoader(C17PluginHost.class.getClassLoader());
            runtime.setSafeMode(false);
        }
        Object call(String name,C17PluginHost view,Original original) throws Throwable {
            Method executable = C17PluginHost.class.getDeclaredMethod(name);
            XposedInterface.Hooker hook = hooks.get(executable);
            if (hook == null) return original.run();
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},
                    (owner,event,args) -> {
                        switch (event.getName()) {
                            case "getThisObject": return view;
                            case "getExecutable": return executable;
                            case "proceed": return original.run();
                            default: throw new AssertionError(event.getName());
                        }
                    });
            return hook.intercept(chain);
        }
    }
    private static final class CommandDispatcher extends Handler {
        final ConcurrentLinkedQueue<Runnable> ready=new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Delayed> later=new ConcurrentLinkedQueue<>();
        long now;
        static final class Delayed {
            final Runnable task;final long at;
            Delayed(Runnable task,long at){this.task=task;this.at=at;}
        }
        CommandDispatcher(){super(Looper.getMainLooper());}
        @Override public boolean post(Runnable task){ready.add(task);return true;}
        @Override public boolean postDelayed(Runnable task,long delay){later.add(new Delayed(task,now+delay));return true;}
        @Override public void removeCallbacks(Runnable task){ready.removeIf(item->item==task);later.removeIf(item->item.task==task);}
        void drain(){
            int budget=1000;
            while(true){
                for(Delayed item:new ArrayList<>(later))if(item.at<=now && later.remove(item))ready.add(item.task);
                Runnable task=ready.poll();if(task==null)return;
                if(--budget==0)throw new AssertionError("Unbounded command dispatch");task.run();
            }
        }
        void advance(long millis){now+=millis;drain();}
        void until(BooleanSupplier condition)throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            do{drain();if(condition.getAsBoolean())return;Thread.sleep(5);}while(System.nanoTime()<end);
            throw new AssertionError("Timed out waiting for command resource completion");
        }
    }
    public static final class CommandOwner extends Context {
        final File root;
        final byte[] resource="exports.answer=73;".getBytes(StandardCharsets.UTF_8);
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final List<Bundle> reports=new ArrayList<>();
        final List<String> executions=new ArrayList<>();
        final Thread mainThread=Thread.currentThread();
        int refusedReports;
        final AtomicInteger resourceReads=new AtomicInteger();
        volatile boolean failResources;
        Runnable onRecord;
        final ContentResolver resolver=new ContentResolver(){
            @Override public Bundle call(Uri uri,String method,String argument,Bundle extras){
                if(CustomPluginAdmin.REPORT.equals(method)){
                    reports.add(new Bundle(extras));
                    if(refusedReports-->0)throw new IllegalStateException("Command report unavailable");
                    return new Bundle();
                }
                return null;
            }
            @Override public java.io.InputStream openInputStream(Uri uri)throws java.io.FileNotFoundException {
                resourceReads.incrementAndGet();
                entered.countDown();
                try{if(!release.await(3,TimeUnit.SECONDS))throw new java.io.FileNotFoundException("Resource gate timed out");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.FileNotFoundException("Resource gate interrupted");}
                if(failResources)throw new java.io.FileNotFoundException("Permanent command resource fixture failure");
                return new java.io.ByteArrayInputStream(resource);
            }
        };
        CommandOwner(File root){this.root=root;}
        @Override public File getFilesDir(){return root;}
        @Override public ContentResolver getContentResolver(){return resolver;}
        public void record(String label){
            if(Thread.currentThread()!=mainThread)throw new AssertionError("Command did not run on main");
            executions.add(label);
            Runnable callback=onRecord;onRecord=null;if(callback!=null)callback.run();
        }
    }
    private static final class CommandSession implements AutoCloseable {
        final CommandDispatcher dispatcher=new CommandDispatcher();
        final CommandOwner owner;
        final CustomPluginHooks hooks=new CustomPluginHooks(dispatcher);
        final JSONObject entry;
        CommandSession()throws Exception {
            owner=new CommandOwner(Files.createTempDirectory("c17-hooks-command-").toFile());
            StringBuilder hash=new StringBuilder();
            for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(owner.resource))hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            entry=new JSONObject().put("id","command-runtime").put("versionCode",1).put("baseDir",owner.root.getPath())
                    .put("source","var answer=C17.require('lib.js').answer;").put("state",new JSONObject())
                    .put("assets",new JSONArray().put(new JSONObject().put("path","lib.js").put("bytes",owner.resource.length).put("sha256",hash.toString())));
            hooks.configure(owner,(method,hooker)->{throw new AssertionError("Command fixture must not install hooks");},null);
            hooks.setSafeMode(false);
        }
        JSONObject command(String nonce)throws Exception {
            return new JSONObject().put("action","eval").put("nonce",nonce).put("id","command-runtime")
                    .put("source","C17.context.record('"+nonce+"'); String(answer);");
        }
        JSONObject planWith(JSONObject...commands)throws Exception {
            JSONArray array=new JSONArray();for(JSONObject command:commands)array.put(command);
            return plan().put("runtimes",new JSONArray().put(entry)).put("adminCommands",array);
        }
        int loaded(){Bundle report=new Bundle();hooks.health(report);return report.getInt("custom_plugin_runtime_loaded");}
        @Override public void close(){owner.release.countDown();hooks.stop();}
    }
    private static void coldStartCommand()throws Exception {
        try(CommandSession session=new CommandSession()){
            session.hooks.accept(session.planWith(session.command("cold-once")));
            equal(true,session.owner.entered.await(2,TimeUnit.SECONDS),"resource copy starts while command waits");
            equal(0,session.owner.reports.size(),"cold-start eval stays pending without reporting a terminal error");
            equal(1,session.dispatcher.later.size(),"one delayed command retry is queued");
            session.dispatcher.advance(99);
            equal(0,session.owner.executions.size(),"pending retry is throttled to 100 ms");
            session.owner.release.countDown();session.dispatcher.until(()->session.loaded()==1);
            session.owner.refusedReports=1;session.dispatcher.advance(1);
            equal(java.util.Arrays.asList("cold-once"),session.owner.executions,"same cold-start nonce eventually executes exactly once");
            equal(1,session.owner.reports.size(),"ready command reports its result without another plan delivery");
            equal("73",session.owner.reports.get(0).getString("result"),"cold command evaluates the prepared plugin scope");
            session.dispatcher.advance(100);
            equal(2,session.owner.reports.size(),"completed command retries its failed report on the timer");
            equal(java.util.Arrays.asList("cold-once"),session.owner.executions,"report retry never executes eval again");
            session.hooks.accept(session.planWith(session.command("cold-once")));session.dispatcher.advance(1000);
            equal(2,session.owner.reports.size(),"acknowledged nonce stays complete across later plans and timers");
            equal(0,session.dispatcher.later.size(),"no retry timer remains after acknowledgement");
            session.hooks.accept(session.planWith(new JSONObject().put("action","runtime_status").put("nonce","ready-diagnostics")));
            JSONObject plugin=new JSONObject(session.owner.reports.get(2).getString("result")).getJSONArray("plugins").getJSONObject(0);
            equal("command-runtime",plugin.getString("id"),"native status includes the resident plugin identity");
            equal(false,plugin.getString("stage").isEmpty(),"native status forwards plugin lifecycle stage");
            equal(true,plugin.has("error")&&plugin.has("stack"),"native status forwards per-plugin error and stack fields");
            equal(java.util.Arrays.asList("cold-once"),session.owner.executions,"native diagnostics never execute plugin command source");
        }
    }
    private static void replacePendingCommands()throws Exception {
        try(CommandSession session=new CommandSession()){
            session.hooks.accept(session.planWith(session.command("removed")));
            equal(true,session.owner.entered.await(2,TimeUnit.SECONDS),"removed command reaches the resource gate");
            Runnable stale=session.dispatcher.later.element().task;
            JSONObject cleared=session.planWith();cleared.remove("adminCommands");session.hooks.accept(cleared);
            equal(0,session.dispatcher.later.size(),"plan without commands cancels pending command timer");
            session.owner.release.countDown();session.dispatcher.until(()->session.loaded()==1);
            stale.run();session.dispatcher.advance(1000);
            equal(0,session.owner.executions.size(),"cleared command cannot run after assets become ready or stale timer fires");
            equal(0,session.owner.reports.size(),"cleared pending command never reports a stale terminal result");
            equal(0,session.dispatcher.later.size(),"stale callback cannot restore the cleared retry plan");
        }
        try(CommandSession session=new CommandSession()){
            session.hooks.accept(session.planWith(session.command("old-pending")));
            equal(true,session.owner.entered.await(2,TimeUnit.SECONDS),"replaced command reaches the resource gate");
            Runnable stale=session.dispatcher.later.element().task;
            session.hooks.accept(session.planWith(session.command("new-pending")));
            equal(1,session.dispatcher.later.size(),"new nonce replaces the old pending timer");
            session.owner.release.countDown();session.dispatcher.until(()->session.loaded()==1);
            stale.run();session.dispatcher.advance(100);
            equal(java.util.Arrays.asList("new-pending"),session.owner.executions,"stale retry only consumes latest plan's nonce");
            equal(1,session.owner.reports.size(),"replaced pending nonce never reports");
            equal("new-pending",session.owner.reports.get(0).getString("nonce"),"report belongs to replacement nonce");
        }
        try(CommandSession session=new CommandSession()){
            JSONObject command=session.command("disabled-pending");session.hooks.accept(session.planWith(command));
            equal(true,session.owner.entered.await(2,TimeUnit.SECONDS),"disabled command reaches the resource gate");
            Runnable stale=session.dispatcher.later.element().task;
            session.hooks.accept(plan().put("runtimes",new JSONArray()).put("adminCommands",new JSONArray().put(command)));
            equal(1,session.owner.reports.size(),"disabled plugin ends pending command with ordinary failure");
            equal(true,session.owner.reports.get(0).containsKey("error"),"disabled command reports terminal error");
            stale.run();session.dispatcher.advance(1000);
            equal(0,session.owner.executions.size(),"disabled pending command cannot execute JavaScript");
            equal(1,session.owner.reports.size(),"terminal failure is cached and acknowledged once");
            equal(0,session.dispatcher.later.size(),"ordinary failure does not keep a command retry alive");
        }
        try(CommandSession session=new CommandSession()){
            session.hooks.accept(session.planWith(session.command("stopped-pending")));
            equal(true,session.owner.entered.await(2,TimeUnit.SECONDS),"stopped command reaches the resource gate");
            Runnable stale=session.dispatcher.later.element().task;session.hooks.stop();
            equal(0,session.dispatcher.later.size(),"stop removes queued command retry");
            stale.run();session.dispatcher.advance(1000);
            equal(0,session.owner.executions.size(),"stopped command cannot execute from a previously dequeued callback");
            equal(0,session.owner.reports.size(),"stop suppresses stale command reports");
        }
    }
    private static void completedCommandResults()throws Exception {
        try(CommandSession session=new CommandSession()){
            session.owner.release.countDown();session.hooks.accept(session.planWith());session.dispatcher.until(()->session.loaded()==1);
            JSONObject command=session.command("reentrant-once");JSONObject repeated=session.planWith(command);
            session.owner.onRecord=()->session.hooks.accept(repeated);
            session.hooks.accept(session.planWith(command));session.dispatcher.advance(100);
            equal(java.util.Arrays.asList("reentrant-once"),session.owner.executions,"same nonce cannot reenter eval while its first execution is in progress");
            equal(1,session.owner.reports.size(),"reentrant plan delivery reports one completed result");
            equal(0,session.dispatcher.later.size(),"reentrant execution leaves no completed command timer");

            JSONObject failing=session.command("failed-once").put("source","C17.context.record('failed-once');throw new Error('command-terminal-fixture');");
            session.owner.refusedReports=1;session.hooks.accept(session.planWith(failing));
            equal(2,session.owner.reports.size(),"ordinary JS exception reports terminal failure immediately");
            String failure=session.owner.reports.get(1).getString("error");equal(true,failure!=null,"terminal command stores an error result");
            session.dispatcher.advance(100);
            equal(3,session.owner.reports.size(),"terminal error report retries after transport failure");
            equal(failure,session.owner.reports.get(2).getString("error"),"terminal retry preserves the original error result");
            equal(java.util.Arrays.asList("reentrant-once","failed-once"),session.owner.executions,"ordinary failed command executes once despite report retry");
            session.hooks.accept(session.planWith(failing));session.dispatcher.advance(1000);
            equal(3,session.owner.reports.size(),"acknowledged error is never executed or reported again");
        }
    }
    private static void exhaustedCommandResources()throws Exception {
        try(CommandSession session=new CommandSession()){
            session.owner.failResources=true;session.owner.release.countDown();
            session.hooks.accept(session.planWith(session.command("resource-failed")));
            session.dispatcher.until(()->runtimeErrors(session.hooks)>=1);
            equal(0,session.owner.reports.size(),"first resource failure keeps command pending");
            session.dispatcher.advance(250);session.dispatcher.until(()->runtimeErrors(session.hooks)>=2);
            equal(0,session.owner.reports.size(),"second resource failure remains retryable");
            session.dispatcher.advance(500);session.dispatcher.until(()->runtimeErrors(session.hooks)>=3);
            session.dispatcher.advance(100);
            equal(3,session.owner.resourceReads.get(),"runtime resource failure budget is three attempts");
            equal(1,session.owner.reports.size(),"exhausted startup ends the pending command with an ordinary error");
            equal(true,session.owner.reports.get(0).containsKey("error"),"exhausted command reports terminal startup failure");
            session.dispatcher.advance(1000);
            equal(0,session.owner.executions.size(),"failed startup never executes eval source");
            equal(0,session.dispatcher.later.size(),"exhausted startup stops command polling");
            session.hooks.accept(session.planWith(new JSONObject().put("action","runtime_status").put("nonce","failed-diagnostics")));
            JSONObject plugin=new JSONObject(session.owner.reports.get(1).getString("result")).getJSONArray("plugins").getJSONObject(0);
            equal("command-runtime",plugin.getString("id"),"failed plugin diagnostics need no live JS scope");
            equal(true,plugin.getString("error").contains("Permanent command resource fixture failure"),"native status retains primary startup error");
            equal(true,plugin.getString("stack").contains("FileNotFoundException"),"native status forwards native startup exception stack");
        }
    }
    private static long runtimeErrors(CustomPluginHooks hooks){Bundle health=new Bundle();hooks.health(health);return health.getLong("custom_plugin_runtime_errors");}

    private static int runtimeCount(CustomPluginHooks hooks,String key) {
        Bundle report=new Bundle();hooks.health(report);return report.getInt(key);
    }

    /** The main settings can already be available when the cold plugin provider refuses its first read. */
    private static void bootPlanRecovery()throws Exception {
        CommandDispatcher dispatcher=new CommandDispatcher();
        File root=Files.createTempDirectory("c17-plugin-boot-").toFile();
        JSONObject entry=new JSONObject().put("id","boot-runtime").put("versionCode",1)
                .put("baseDir",root.getPath()).put("state",new JSONObject()).put("source","var ready=true;");
        String json=plan().put("runtimes",new JSONArray().put(entry)).toString();
        AtomicInteger calls=new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean available=new java.util.concurrent.atomic.AtomicBoolean();
        ContentResolver resolver=new ContentResolver(){
            @Override public Bundle call(Uri uri,String method,String argument,Bundle extras){
                if(!CustomPluginHooks.METHOD.equals(method))throw new AssertionError("Unexpected boot transport: "+method);
                calls.incrementAndGet();
                if(!available.get())return null;
                Bundle answer=new Bundle();answer.putString(CustomPluginHooks.PLAN,json);return answer;
            }
        };
        Context owner=new Context(){
            @Override public File getFilesDir(){return root;}
            @Override public ContentResolver getContentResolver(){return resolver;}
        };
        CustomPluginHooks hooks=new CustomPluginHooks(dispatcher);
        try {
            hooks.configure(owner,(method,hooker)->{throw new AssertionError("Unexpected native hook");},null);
            dispatcher.until(()->calls.get()==1&&!dispatcher.later.isEmpty());
            equal(0,runtimeCount(hooks,"custom_plugin_runtime_requested"),"unavailable cold provider supplies no plan");
            available.set(true);
            dispatcher.advance(500);
            dispatcher.until(()->runtimeCount(hooks,"custom_plugin_runtime_requested")==1);
            equal(2,calls.get(),"provider becomes available without another settings edit");
            equal(0,runtimeCount(hooks,"custom_plugin_runtime_loaded"),"boot plan waits for confirmed host safety mode");
            hooks.setSafeMode(false);dispatcher.drain();
            equal(1,runtimeCount(hooks,"custom_plugin_runtime_loaded"),"ready provider starts the plugin after safety is known");
            dispatcher.advance(60000);
            equal(2,calls.get(),"successful boot plan cancels further retries");
        } finally {hooks.stop();}
    }

    private static void sharedBootPlan() throws Exception {
        CommandDispatcher dispatcher = new CommandDispatcher();
        File directory = Files.createTempDirectory("c17-shared-plan-").toFile();
        AtomicReference<Map<String, ?>> data = new AtomicReference<>();
        AtomicReference<SharedPreferences.OnSharedPreferenceChangeListener> changes = new AtomicReference<>();
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (owner, method, args) -> {
            switch (method.getName()) {
                case "getAll": return data.get();
                case "registerOnSharedPreferenceChangeListener": changes.set((SharedPreferences.OnSharedPreferenceChangeListener) args[0]); return null;
                case "unregisterOnSharedPreferenceChangeListener": changes.set(null); return null;
                default: throw new AssertionError(method.getName());
            }
        });
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicReference<JSONObject> live = new AtomicReference<>();
        Context context = new Context() {
            @Override public File getFilesDir() { return directory; }
            @Override public ContentResolver getContentResolver() { return new ContentResolver() {
                @Override public Bundle call(Uri uri, String method, String argument, Bundle extras) {
                    providerCalls.incrementAndGet();
                    JSONObject plan = live.get();
                    if (plan == null) return null;
                    Bundle result = new Bundle();
                    result.putString(CustomPluginHooks.PLAN, plan.toString());
                    return result;
                }
            }; }
        };
        CustomPluginRuntime.Install installation = new CustomPluginRuntime.Install() {
            @Override public XposedInterface.HookHandle hook(java.lang.reflect.Executable member, XposedInterface.Hooker callback) {
                throw new AssertionError("Shared boot fixture does not install native hooks");
            }
            @Override public SharedPreferences frameworkPreferences() { return preferences; }
            @Override public ParcelFileDescriptor openFrameworkFile(String name) throws java.io.FileNotFoundException {
                return ParcelFileDescriptor.open(new File(directory, name), ParcelFileDescriptor.MODE_READ_ONLY);
            }
        };
        JSONObject runtime = new JSONObject().put("id","shared-runtime").put("versionCode",1)
                .put("baseDir",directory.getPath()).put("state",new JSONObject()).put("source","var ready=true;");
        JSONObject boot = plan().put("revision",2L).put("runtimes",new JSONArray().put(runtime))
                .put("targetPackage",ModuleRuntimeStatus.SYSTEM_UI).put("safeMode",false)
                .put("installedPlugins",new JSONArray().put(new JSONObject().put("id","shared-runtime").put("installedAt",1L)
                        .put("preferences",new JSONArray())));
        data.set(sharedWire(directory, boot, 10L));
        CustomPluginHooks hooks = new CustomPluginHooks(dispatcher);
        try {
            hooks.configure(context, (method, callback) -> { throw new AssertionError("Unexpected native rule"); }, null, installation);
            dispatcher.until(() -> runtimeCount(hooks,"custom_plugin_runtime_requested") == 1);
            equal(0, providerCalls.get(), "SystemUI obtains boot plugins without contacting the blocked app");
            equal(0, runtimeCount(hooks,"custom_plugin_runtime_loaded"), "Plugins wait for host startup settings");
            hooks.setSafeMode(false); dispatcher.drain();
            equal(1, runtimeCount(hooks,"custom_plugin_runtime_loaded"), "Host startup settings release the shared plan");

            data.set(sharedWire(directory, new JSONObject(boot.toString()).put("safeMode",true), 11L));
            changes.get().onSharedPreferenceChanged(preferences, SettingsFrameworkMirror.REVISION);
            dispatcher.until(() -> runtimeCount(hooks,"custom_plugin_runtime_loaded") == 0);
            equal(0, providerCalls.get(), "Shared safety applies with the provider unavailable");
            data.set(sharedWire(directory, boot, 12L));
            changes.get().onSharedPreferenceChanged(preferences, SettingsFrameworkMirror.REVISION);
            dispatcher.until(() -> runtimeCount(hooks,"custom_plugin_runtime_loaded") == 1);

            Map<String, Object> withdrawn = new LinkedHashMap<>(data.get());
            withdrawn.put(SettingsFrameworkMirror.REVISION, 13L);
            withdrawn.put(CustomPluginFrameworkPublisher.KEY, CustomPluginFrameworkPublisher.WITHDRAWN);
            data.set(withdrawn);
            changes.get().onSharedPreferenceChanged(preferences, SettingsFrameworkMirror.REVISION);
            dispatcher.until(() -> runtimeCount(hooks,"custom_plugin_runtime_loaded") == 0);
            equal(1, providerCalls.get(), "Withdrawn boot plan pauses existing effects when the provider cannot replace it");
            data.set(sharedWire(directory, boot, 14L));
            changes.get().onSharedPreferenceChanged(preferences, SettingsFrameworkMirror.REVISION);
            dispatcher.until(() -> runtimeCount(hooks,"custom_plugin_runtime_loaded") == 1);

            live.set(plan().put("revision",3L).put("runtimes",new JSONArray()));
            hooks.refresh();
            dispatcher.until(() -> runtimeCount(hooks,"custom_plugin_runtime_requested") == 0);
            live.set(null);
            changes.get().onSharedPreferenceChanged(preferences, SettingsFrameworkMirror.REVISION);
            dispatcher.until(() -> providerCalls.get() == 3);
            dispatcher.drain();
            equal(0, runtimeCount(hooks,"custom_plugin_runtime_loaded"), "A stale shared plan cannot revive a plugin disabled live");
        } finally { hooks.stop(); }
        equal(null, changes.get(), "Shared revision observer released with SystemUI hooks");
    }

    private static Map<String, ?> sharedWire(File directory, JSONObject plan, long revision) throws Exception {
        byte[] bytes = plan.toString().getBytes(StandardCharsets.UTF_8);
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hash = new StringBuilder();
        for (byte value : digest) hash.append(Character.forDigit((value & 0xf0) >>> 4, 16)).append(Character.forDigit(value & 0xf, 16));
        String name = CustomPluginFrameworkPublisher.PLAN_PREFIX + hash;
        Files.write(new File(directory,name).toPath(),bytes);
        JSONObject ref = new JSONObject().put("file",name).put("bytes",bytes.length).put("sha256",hash.toString());
        JSONObject meta = new JSONObject().put("format",CustomPluginFrameworkPublisher.FORMAT)
                .put("revision",plan.getLong("revision")).put("safeMode",plan.getBoolean("safeMode"))
                .put("plans",new JSONObject().put(ModuleRuntimeStatus.SYSTEM_UI,ref));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SettingsFrameworkMirror.REVISION, revision);
        result.put(SettingsFrameworkMirror.SCHEMA, SettingsFrameworkMirror.VERSION);
        result.put(StatusBarSettings.SAFE_MODE, plan.getBoolean("safeMode"));
        result.put(CustomPluginFrameworkPublisher.KEY, meta.toString());
        return result;
    }

    /** The host callback rereads its last successful snapshot, just like StatusBarModule. */
    private static void glassMaterialLifecycle()throws Exception {
        CommandDispatcher dispatcher=new CommandDispatcher();
        CommandOwner owner=new CommandOwner(Files.createTempDirectory("c17-glass-ownership-").toFile());
        CustomPluginHooks hooks=new CustomPluginHooks(dispatcher);
        Bundle initial=new Bundle();initial.putBoolean("qs_appearance_enabled",true);
        Bundle[] lastApplied={initial},rendered={new Bundle(initial)};
        boolean[] applyingSafety={false};int[] nestedRebinds={0},notifications={0};
        hooks.configure(owner,(method,hooker)->{throw new AssertionError("Material lifecycle fixture needs no native hooks");},()->{
            notifications[0]++;if(applyingSafety[0])nestedRebinds[0]++;
            hooks.setSafeMode(lastApplied[0].getBoolean("safe_mode",false));
            rendered[0]=hooks.runtimeSettings(lastApplied[0]);
        });
        JSONObject state=new JSONObject().put("system_glass",true).put("cc_glass",true);
        JSONObject entry=new JSONObject().put("id",CustomPluginMaterialIsolation.PLUGIN).put("versionCode",CustomPluginMaterialIsolation.MIN_VERSION)
                .put("baseDir",owner.root.getPath()).put("state",state)
                .put("source","C17.context.record('start');C17.onCleanup(function(){C17.context.record('stop');});"
                        +"C17.onStateChanged(function(next){if(next.fail)throw new Error('glass-lifecycle-failure');});");
        try {
            hooks.setSafeMode(false);hooks.accept(plan().put("runtimes",new JSONArray().put(entry)));
            dispatcher.drain();
            equal(false,rendered[0].getBoolean("qs_appearance_enabled"),"started glass takes its host material after main dispatch");
            equal(true,lastApplied[0].getBoolean("qs_appearance_enabled"),"material ownership never modifies the last successful user snapshot");
            equal(java.util.Arrays.asList("start"),owner.executions,"first glass scope starts once");

            // The new safety snapshot is not committed until setSafeMode returns. A synchronous
            // ownership callback here would reread old safe=false and immediately start glass again.
            applyingSafety[0]=true;int before=notifications[0];hooks.setSafeMode(true);
            equal(before,notifications[0],"safety shutdown never synchronously rebinds the previous snapshot");
            equal(0,nestedRebinds[0],"material lifecycle cannot reenter the safety application transaction");
            Bundle safe=new Bundle(initial);safe.putBoolean("safe_mode",true);lastApplied[0]=safe;applyingSafety[0]=false;
            dispatcher.drain();
            Bundle health=new Bundle();hooks.health(health);
            equal(false,health.getBoolean("custom_plugin_runtime_active"),"queued material restoration observes the newly committed safety snapshot");
            equal(0,health.getInt("custom_plugin_runtime_loaded"),"safety mode keeps executable glass stopped");
            equal(java.util.Arrays.asList("start","stop"),owner.executions,"old safe=false cannot resurrect a scope during shutdown");
            equal(true,rendered[0].getBoolean("qs_appearance_enabled"),"safety shutdown restores host material ownership");

            lastApplied[0]=new Bundle(initial);hooks.setSafeMode(false);dispatcher.drain();
            equal(false,rendered[0].getBoolean("qs_appearance_enabled"),"leaving safety mode lets a fresh glass scope own its material");
            hooks.accept(plan().put("runtimes",new JSONArray().put(new JSONObject(entry.toString()).put("state",new JSONObject(state.toString()).put("fail",true)))));
            dispatcher.drain();hooks.health(health);
            equal(0,health.getInt("custom_plugin_runtime_loaded"),"a failed state callback stops the glass scope");
            equal(true,rendered[0].getBoolean("qs_appearance_enabled"),"a failed glass scope yields host material again");
            equal(true,runtimeErrors(hooks)>0,"failed material ownership transition retains its runtime error evidence");

            hooks.accept(plan().put("runtimes",new JSONArray()));dispatcher.drain();
            hooks.accept(plan().put("runtimes",new JSONArray().put(entry)));
            before=notifications[0];hooks.stop();dispatcher.drain();
            equal(before,notifications[0],"stopping the host removes queued material callbacks");
        } finally {applyingSafety[0]=false;hooks.stop();}
    }
    private static void nativeRuntimeStatus() throws Exception {
        List<Bundle> reports=new ArrayList<>();
        ContentResolver resolver=new ContentResolver(){
            @Override public Bundle call(Uri uri,String method,String argument,Bundle extras){
                if(CustomPluginAdmin.REPORT.equals(method)){
                    reports.add(new Bundle(extras));
                    if(reports.size()==1)throw new IllegalStateException("Transient report transport failure");
                    if(reports.size()==2)return null;
                    if(reports.size()==3){Bundle error=new Bundle();error.putString("error","Report not saved");return error;}
                    return new Bundle();
                }
                return null;
            }
        };
        Context context=new Context(){@Override public ContentResolver getContentResolver(){return resolver;}};
        CustomPluginHooks hooks=new CustomPluginHooks(new Handler(Looper.getMainLooper()));
        hooks.configure(context,(method,hooker)->{throw new AssertionError("Empty native status must not install hooks");},null);
        try {
            JSONObject command=new JSONObject().put("action","runtime_status").put("nonce","native-status");
            hooks.accept(plan().put("revision",7).put("runtimes",new JSONArray()).put("adminCommands",new JSONArray().put(command)));
            equal(1,reports.size(),"paused runtime with no plugins reports native status");
            Bundle report=reports.get(0);
            equal("native-status",report.getString("nonce"),"native status report retains command nonce");
            equal(false,report.containsKey("error"),"native status succeeds without any plugin identity, scope or JavaScript");
            JSONObject status=new JSONObject(report.getString("result"));
            equal(ModuleRuntimeStatus.BUILD_TOKEN,status.getString("build_token"),"status identifies the sampling runtime build");
            equal(android.os.Process.myPid(),status.getInt("pid"),"status uses sampling process PID");
            equal(0,status.getJSONArray("plugins").length(),"native status includes empty per-plugin diagnostic array");
            JSONObject health=status.getJSONObject("health");
            Bundle expected=new Bundle();hooks.health(expected);
            equal(expected.keySet().size(),health.length(),"status carries all native health fields");
            for(String key:expected.keySet())equal(String.valueOf(expected.get(key)),String.valueOf(health.get(key)),"native health field "+key);
            equal(0,health.getInt("custom_plugin_runtime_loaded"),"no enabled plugin is needed for a sample");
            equal(false,health.getBoolean("custom_plugin_runtime_active"),"native sample exposes paused runtime");
            equal(7L,health.getLong("custom_plugin_runtime_revision"),"native sample reflects the accepted plan revision");

            hooks.setSafeMode(false);
            hooks.accept(plan().put("revision",8).put("adminCommands",new JSONArray().put(command)));
            equal(2,reports.size(),"failed native report retries on the next delivered plan");
            equal(report.getString("result"),reports.get(1).getString("result"),"retry keeps first sample despite runtime state and revision changes");
            hooks.accept(plan().put("revision",9).put("adminCommands",new JSONArray().put(command)));
            equal(3,reports.size(),"null report acknowledgement remains retryable");
            hooks.accept(plan().put("revision",10).put("adminCommands",new JSONArray().put(command)));
            equal(4,reports.size(),"error report acknowledgement remains retryable");
            for(Bundle retry:reports)equal(report.getString("result"),retry.getString("result"),"every report retry reuses the original sample");
            hooks.accept(plan().put("revision",11).put("adminCommands",new JSONArray().put(command)));
            equal(4,reports.size(),"successfully acknowledged nonce is never sampled or reported again");
            hooks.accept(plan().put("revision",12).put("adminCommands",new JSONArray().put(new JSONObject().put("action","runtime_status").put("nonce","fresh-native-status"))));
            equal(5,reports.size(),"new nonce samples the current runtime");
            JSONObject fresh=new JSONObject(reports.get(4).getString("result")).getJSONObject("health");
            equal(12L,fresh.getLong("custom_plugin_runtime_revision"),"new status includes latest accepted revision");
            equal(true,fresh.getBoolean("custom_plugin_runtime_active"),"new status includes current active state");
        } finally {hooks.stop();}
    }
    public static void main(String[] ignored) throws Throwable {
        glassMaterialLifecycle();
        coldStartCommand();
        replacePendingCommands();
        completedCommandResults();
        exhaustedCommandResources();
        nativeRuntimeStatus();
        CustomPluginRules.validate(new JSONObject()); checks++;
        for (String prefix : new String[]{"java.lang.","android.view.","dev.puitheme.","com.oplus.systemuiAttack.","com.android.systemui.evil;"})
            rejected(() -> validate(property("x","alpha",.5).put("className",prefix+"View")),"unapproved namespace");
        rejected(() -> validate(property("x","alpha",.5).put("method","<init>")),"constructor");
        rejected(() -> validate(property("x","alpha",.5).put("method","wait")),"Object lifecycle");
        rejected(() -> validate(property("x","alpha",.5).put("argCount",1.5)),"fractional count");
        rejected(() -> validate(property("x","alpha",.5).put("argCount",9)),"large count");
        rejected(() -> validate(property("x","alpha",.5).put("argCount","0")),"coerced count");
        rejected(() -> validate(property("x","alpha",1.01)),"alpha upper bound");
        rejected(() -> validate(property("x","alpha",-.01)),"alpha lower bound");
        rejected(() -> validate(property("x","translationX",200.1)),"translation bound");
        rejected(() -> validate(property("x","scaleX",.09)),"scale lower bound");
        rejected(() -> validate(property("x","scaleY",4.01)),"scale upper bound");
        rejected(() -> validate(property("x","rotation",2)),"unsupported property");
        rejected(() -> validate(property("x","alpha",.5).put("code","evil()")),"unknown executable field");
        rejected(() -> validate(rule("x","update","text").put("find","").put("replace","a")),"empty literal");
        rejected(() -> validate(rule("x","update","text").put("find","a")),"incomplete pair");
        rejected(() -> validate(rule("x","update","text")),"empty action");
        rejected(() -> validate(rule("x","update","text").put("prefix",new String(new char[257]).replace('\0','x'))),"text bound");
        rejected(() -> validate(rule("x","count","result").put("value",JSONObject.NULL)),"null result");
        rejected(() -> validate(rule("x","count","result").put("value","long text")),"reference string result");
        rejected(() -> validate(rule("x","update","text").put("prefix","a\0b")),"NUL text");
        rejected(() -> parsed(rule("x","object","result").put("value",1)).resolve(C17PluginHost.class.getClassLoader()),"reference result");
        rejected(() -> parsed(rule("x","count","result").put("value",1.2)).resolve(C17PluginHost.class.getClassLoader()),"integer result");
        rejected(() -> parsed(rule("x","count","result").put("value",2147483648L)).resolve(C17PluginHost.class.getClassLoader()),"integer overflow");
        rejected(() -> parsed(property("x","alpha",.5).put("method","staticUpdate")).resolve(C17PluginHost.class.getClassLoader()),"static view receiver");
        rejected(() -> parsed(rule("x","nativeValue","result").put("value",1)).resolve(C17PluginHost.class.getClassLoader()),"native body");
        rejected(() -> parsed(rule("x","ambiguous","result").put("argCount",1).put("value",1)).resolve(C17PluginHost.class.getClassLoader()),"ambiguous overload");
        rejected(() -> parsed(rule("x","abstractValue","result").put("className",C17PluginHost.Abstract.class.getName()).put("value",1)).resolve(C17PluginHost.class.getClassLoader()),"abstract body");
        rejected(() -> parsed(rule("x","ratio","result").put("value",Double.MAX_VALUE)).resolve(C17PluginHost.class.getClassLoader()),"float overflow");
        rejected(() -> parsed(rule("x","enabled","result").put("value",1)).resolve(C17PluginHost.class.getClassLoader()),"boolean literal mismatch");
        rejected(() -> parsed(rule("x","large","result").put("value",new java.math.BigDecimal("9223372036854775808"))).resolve(C17PluginHost.class.getClassLoader()),"long overflow");
        JSONArray duplicate = new JSONArray().put(property("x","alpha",.5)).put(property("x","scaleX",1));
        rejected(() -> CustomPluginRules.validate(new JSONObject().put("hooks",duplicate)),"duplicate id");
        JSONArray many = new JSONArray(); for (int i=0;i<33;i++) many.put(property("p"+i,"alpha",.5));
        rejected(() -> CustomPluginRules.validate(new JSONObject().put("hooks",many)),"plugin hook budget");
        equal(Long.MAX_VALUE,parsed(rule("x","large","result").put("value",Long.MAX_VALUE)).resultFor(long.class),"exact large long");
        equal('Q',parsed(rule("x","letter","result").put("value","Q")).resultFor(char.class),"char literal");
        equal(1.25f,parsed(rule("x","ratio","result").put("value",1.25)).resultFor(float.class),"float literal");

        Framework f = new Framework(); C17PluginHost view = new C17PluginHost();
        view.setText("native [a]"); view.setAlpha(.8f); view.setTranslationY(7);
        view.getResources().getDisplayMetrics().density = 2;
        JSONObject text = rule("text","update","text").put("find","[a]").put("replace","[b]").put("prefix","(").put("suffix",")");
        JSONObject alpha = property("alpha","alpha",.4), y = property("y","translationY",3);
        f.runtime.accept(plan(text,alpha,y)); equal(1,f.installed,"shared method uses one handle");
        android.os.Bundle health = new android.os.Bundle(); f.runtime.health(health);
        equal(3,health.getInt("custom_plugin_requested"),"report requested rules"); equal(3,health.getInt("custom_plugin_resolved"),"report resolved rules");
        equal(0,health.getInt("custom_plugin_unavailable"),"multiple rules on one method are ready");
        f.call("update",view,() -> null);
        equal("(native [b])",view.getText().toString(),"literal text transform"); equal(.4f,view.getAlpha(),"alpha"); equal(6f,view.getTranslationY(),"dp conversion");
        f.call("update",view,() -> null); equal("(native [b])",view.getText().toString(),"no repeated prefix");
        int handles = f.installed; f.runtime.accept(plan(text,alpha,y)); equal(handles,f.installed,"unchanged plan retains handles");
        f.call("update",view,() -> { view.setText("new [a]"); view.setAlpha(.7f); return null; });
        equal("(new [b])",view.getText().toString(),"fresh native text");
        f.runtime.setSafeMode(true); equal(0,f.hooks.size(),"safe mode cancels hooks");
        equal("new [a]",view.getText().toString(),"safe mode restores native text"); equal(.7f,view.getAlpha(),"latest native alpha"); equal(7f,view.getTranslationY(),"native translation");
        f.runtime.setSafeMode(false); f.call("update",view,() -> null);
        view.setAlpha(.9f); view.setText("external"); f.runtime.accept(plan());
        equal(.9f,view.getAlpha(),"removal preserves newer external alpha"); equal("external",view.getText().toString(),"removal preserves newer native text");
        f.runtime.accept(plan(text,rule("nested","nested","text").put("prefix","(" ).put("suffix",")")));
        f.call("update",view,() -> f.call("nested",view,() -> { view.setText("nested [a]"); return null; }));
        equal("(nested [b])",view.getText().toString(),"nested callbacks compose once");
        f.runtime.accept(new JSONObject().put("revision",2).put("settings",new JSONObject()).put("hooks",new JSONArray().put(new JSONObject())));
        equal(2,f.hooks.size(),"malformed plan retains last good registrations");
        f.runtime.accept(plan().put("error","library invalid")); equal(0,f.hooks.size(),"repository corruption disables previous plan");
        equal("nested [a]",view.getText().toString(),"repository failure restores previous view");
        f.runtime.accept(plan(rule("result","count","result").put("value",23),rule("bool","enabled","result").put("value",true)));
        int[] nativeCalls = {0}; equal(23,f.call("count",view,() -> { nativeCalls[0]++; return 7; }),"primitive replacement");
        equal(1,nativeCalls[0],"native runs exactly once"); equal(true,f.call("enabled",view,() -> false),"boolean replacement");
        IllegalStateException nativeFailure = new IllegalStateException("native callback failure");
        try { f.call("count",view,() -> { throw nativeFailure; }); throw new AssertionError("native throw was swallowed"); }
        catch (IllegalStateException propagated) { equal(nativeFailure,propagated,"native exception identity survives result hook"); }
        f.runtime.accept(plan(alpha,property("other-y","translationY",2).put("method","nested")));
        f.call("update",view,() -> null); f.call("nested",view,() -> null);
        equal(.4f,view.getAlpha(),"independent callback keeps the other method's property");
        equal(4f,view.getTranslationY(),"second method applies its own dp property");
        f.runtime.accept(plan()); equal(.9f,view.getAlpha(),"independent property restores its actual native value");
        f.runtime.accept(plan(text));
        Looper.setCurrentForCheck(null); f.call("update",view,() -> { view.setText("worker"); return null; }); Looper.resetCurrentForCheck();
        equal("worker",view.getText().toString(),"worker callback leaves Views alone");
        f.call("update",view,() -> null); f.refuseRemoval = true; f.runtime.setSafeMode(true);
        equal("worker",view.getText().toString(),"failed unhook still restores view");
        int before = f.installed; f.runtime.setSafeMode(false); equal(before,f.installed,"failed removal cannot duplicate hook");
        equal(null,f.call("update",view,() -> null),"leftover registration remains bypassed"); equal("worker",view.getText().toString(),"failed handle cannot reapply");
        f.refuseRemoval = false; f.runtime.rememberLoader(C17PluginHost.class.getClassLoader()); f.call("update",view,() -> null);
        equal("(worker)",view.getText().toString(),"later event retries removal then install");
        f.runtime.stop(); equal("worker",view.getText().toString(),"uninstall restores view"); equal(0,f.hooks.size(),"uninstall removes handles");
        f.runtime.accept(plan(text)); equal(0,f.hooks.size(),"stopped runtime cannot reactivate");
        equal(f.installed,f.removed,"every installed handle eventually removed");

        Framework bounded = new Framework(); java.util.List<C17PluginHost> retained = new java.util.ArrayList<>();
        bounded.runtime.accept(plan(property("a","alpha",.5)));
        for (int i=0;i<258;i++) { C17PluginHost item = new C17PluginHost(); retained.add(item); bounded.call("update",item,() -> null); }
        equal(.5f,retained.get(255).getAlpha(),"bounded tracker includes its final allowed View");
        equal(1f,retained.get(256).getAlpha(),"bounded tracker preserves extra native Views");
        bounded.runtime.stop(); for (C17PluginHost item : retained) equal(1f,item.getAlpha(),"all owned Views restored");
        Framework unavailable = new Framework();
        unavailable.runtime.accept(plan(property("x","alpha",.5).put("className","com.android.systemui.plugins.AbsentOnThisRom")));
        health = new android.os.Bundle(); unavailable.runtime.health(health);
        equal(1,health.getInt("custom_plugin_unavailable"),"missing ROM class is reported");
        equal(0,health.getInt("custom_plugin_installed"),"no broad fallback hook");
        equal(true,HookHealth.findings(HookHealth.copy(health)).stream().anyMatch(item -> item.id.equals("custom_plugins_missing")),"diagnostic shows missing plugin rules");
        unavailable.runtime.stop();
        Framework textBudget = new Framework(); C17PluginHost longText = new C17PluginHost();
        textBudget.runtime.accept(plan(rule("expanded","update","text").put("find","a").put("replace",new String(new char[256]).replace('\0','b'))));
        String original = new String(new char[4096]).replace('\0','a'); longText.setText(original);
        textBudget.call("update",longText,() -> null); equal(original,longText.getText(),"literal expansion is preflighted before allocation");
        longText.setText(original + "a"); textBudget.call("update",longText,() -> null); equal(4097,longText.getText().length(),"long native text remains untouched");
        textBudget.runtime.stop();
        bootPlanRecovery();
        sharedBootPlan();
        System.out.println("CustomPluginHooksCheck passed " + checks + " checks");
    }
}
