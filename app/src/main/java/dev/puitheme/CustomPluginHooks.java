// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.TextView;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Owns API 1 declarations and the separate API 2 lifecycle; provider IO stays outside hook callbacks. */
final class CustomPluginHooks {
    static final String METHOD = "read_custom_plugin_plan", PLAN = "plan";
    interface Install { XposedInterface.HookHandle hook(Method method,XposedInterface.Hooker hooker) throws Throwable; }
    private static final int MAX_VIEWS = 256, MAX_TEXT = 4096, MAX_LOADERS = 16;
    private static final long[] BOOT_RETRY_DELAYS = {250L, 500L, 1000L, 2000L, 4000L, 8000L, 16000L};
    private final Handler main;
    private final Runnable retryBootPlan = this::retryUnconfirmedPlan;
    private final Runnable refreshBootSnapshot = () -> refresh(false);
    private final CustomPluginRuntime runtime;
    private final List<WeakReference<ClassLoader>> loaders = new ArrayList<>();
    private final Map<Method,Binding> installed = new LinkedHashMap<>();
    private final List<Binding> cleanup = new ArrayList<>();
    private final Map<View,ViewState> views = new WeakHashMap<>();
    private final ThreadLocal<Boolean> applying = new ThreadLocal<>();
    private List<CustomPluginRules.Rule> rules = Collections.emptyList();
    private JSONObject settings = new JSONObject();
    private String fingerprint = "";
    private Context context;
    private Install install;
    private Runnable changed;
    private CustomPluginFrameworkReader framework;
    private boolean paused = true, hostPaused = true, snapshotPaused;
    private boolean stopped, loading, requested, requestedPrimary, readingPrimary, confirmed;
    private int bootRetries;
    private int errors;
    private int resolvedRules;
    private long generation, requestSerial, revision, hits;
    private long lastPrimaryRevision = -1L;
    private String planSource = "";
    private String lastReport = "";

    private int materialOwnership;
    private final Runnable materialChanged = () -> {
        int next=CustomPluginMaterialIsolation.signature(glassState());
        Runnable notify;
        synchronized(CustomPluginHooks.this) {
            if(stopped||next==materialOwnership)return;
            materialOwnership=next;notify=changed;
        }
        if(notify!=null)notify.run();
    };
    CustomPluginHooks(Handler main) {
        this.main = main; this.runtime = new CustomPluginRuntime(main);
        runtime.observeLifecycle(() -> {
            // Closing a plugin can run inside application of a new safety snapshot. Wait
            // for that transaction to finish before reading the last-applied host settings.
            this.main.removeCallbacks(materialChanged);
            this.main.post(materialChanged);
        });
    }

    private JSONObject glassState() {
        return runtime.runningState(CustomPluginMaterialIsolation.PLUGIN,CustomPluginMaterialIsolation.MIN_VERSION);
    }

    Bundle runtimeSettings(Bundle saved) { return CustomPluginMaterialIsolation.apply(saved,glassState()); }

    void configure(Context context,Install install,Runnable changed) {
        configure(context,install,changed,(executable,hooker) -> {
            if (!(executable instanceof Method)) throw new UnsupportedOperationException("Constructor installer unavailable");
            return install.hook((Method)executable,hooker);
        });
    }

    void configure(Context context,Install install,Runnable changed,CustomPluginRuntime.Install runtimeInstall) {
        synchronized(this) {
            if (stopped || context == null || this.context != null) return;
            this.install = install; this.changed = changed; this.context = context;
            framework = new CustomPluginFrameworkReader(runtimeInstall, ModuleRuntimeStatus.SYSTEM_UI, this::frameworkChanged);
        }
        runtime.configure(context,runtimeInstall,changed);
        synchronized(this) { refresh(false); reconcile(); }
    }

    private synchronized void frameworkChanged() {
        if (stopped) return;
        main.removeCallbacks(refreshBootSnapshot);
        main.post(refreshBootSnapshot);
    }

    synchronized JSONObject settings() {
        if (paused) return new JSONObject();
        try { return new JSONObject(settings.toString()); }
        catch (org.json.JSONException impossible) { return new JSONObject(); }
    }

    void health(Bundle report) {
        runtime.health(report);
        synchronized(this) {
        java.util.Set<CustomPluginRules.Rule> ready = new java.util.HashSet<>();
        for (Binding binding : installed.values()) if (binding.active) ready.addAll(binding.rules);
        report.putInt("custom_plugin_requested",rules.size());
        report.putInt("custom_plugin_resolved",resolvedRules);
        report.putInt("custom_plugin_installed",installed.size());
        report.putInt("custom_plugin_unavailable",Math.max(0,rules.size()-ready.size()));
        report.putLong("custom_plugin_revision",revision);
        report.putLong("custom_plugin_hits",hits);
        report.putBoolean("custom_plugin_active",!stopped && !paused);
        report.putString("custom_plugin_plan_source",planSource);
        }
    }

    /** Main package and the already audited OEM plugin entry points supply their own loaders. */
    void rememberLoader(ClassLoader loader) {
        runtime.rememberLoader(loader);
        synchronized(this) {
        if (stopped || loader == null) return;
        for (java.util.Iterator<WeakReference<ClassLoader>> it = loaders.iterator(); it.hasNext();) {
            ClassLoader known = it.next().get();
            if (known == null) it.remove(); else if (known == loader) {
                if (!cleanup.isEmpty()) reconcile();
                return;
            }
        }
        if (loaders.size() >= MAX_LOADERS) return;
        loaders.add(new WeakReference<>(loader)); reconcile();
        }
    }

    synchronized void retryReadyLoader() { reconcile(); }

    /** A boot or unlock broadcast may make the provider available after the first retry window. */
    synchronized void bootReady() {
        if (stopped || context == null || confirmed) return;
        resetBootRetries();
        if (!loading) readPlan(false);
    }

    private synchronized void retryUnconfirmedPlan() {
        if (!stopped && context != null && !confirmed && !loading) readPlan(false);
    }

    private void resetBootRetries() {
        main.removeCallbacks(retryBootPlan);
        bootRetries = 0;
    }

    void refresh() { refresh(true); }

    private synchronized void refresh(boolean primary) {
        if (stopped || context == null) return;
        resetBootRetries();
        requestSerial++;
        if (loading) {
            requested = true;
            requestedPrimary |= primary || readingPrimary;
            return;
        }
        readPlan(primary);
    }

    private void readPlan(boolean primary) {
        loading = true; final Context owner = context; final long requestGeneration = generation, serial = requestSerial;
        readingPrimary = primary;
        final CustomPluginFrameworkReader source = framework;
        final long primaryRevision = lastPrimaryRevision;
        Thread worker = new Thread(() -> {
            JSONObject shared = source == null ? null : source.read();
            if (shared != null && shared.optLong("revision",-1L) < primaryRevision) shared = null;
            final JSONObject live = primary || shared == null ? readProvider(owner) : null;
            final JSONObject loaded = live != null ? live : shared;
            final boolean withdrawn = loaded == null && source != null && source.snapshotWithdrawn();
            main.post(() -> {
                boolean current;
                synchronized (CustomPluginHooks.this) {
                    if (stopped || requestGeneration != generation) return;
                    loading = readingPrimary = false;
                    // A notification during IPC supersedes that response. In particular an older
                    // enabled plan must not briefly return after a plugin has been disabled.
                    current = serial == requestSerial;
                    if (current && withdrawn) confirmed = false;
                }
                if (current && loaded != null) accept(loaded, live == null ? "framework" : "provider");
                else if (current && withdrawn) updateSafety(true, true);
                synchronized (CustomPluginHooks.this) {
                    if (stopped || loading) return;
                    if (requested) {
                        boolean nextPrimary = requestedPrimary;
                        requested = requestedPrimary = false;
                        readPlan(nextPrimary);
                    }
                    else if (!confirmed && bootRetries < BOOT_RETRY_DELAYS.length)
                        main.postDelayed(retryBootPlan, BOOT_RETRY_DELAYS[bootRetries++]);
                }
            });
        },"C17-plugin-plan");
        worker.setDaemon(true); worker.start();
    }

    private JSONObject readProvider(Context owner) {
        try {
            Bundle answer = owner.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI),METHOD,null,null);
            String json = answer == null ? null : answer.getString(PLAN);
            ParcelFileDescriptor descriptor = answer == null ? null : answer.getParcelable("plan_fd");
            if (descriptor != null) try (java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384]; int count;
                while ((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                json = new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            }
            return json == null ? null : new JSONObject(json);
        } catch (Exception | LinkageError unavailable) {
            report("Plugin plan provider is temporarily unavailable",unavailable);
            return null;
        }
    }

    /** Existing effects leave immediately; the saved plan remains available when safety mode ends. */
    void setSafeMode(boolean safeMode) {
        updateSafety(safeMode, false);
    }

    private void updateSafety(boolean safeMode, boolean fromSnapshot) {
        boolean next;
        Runnable notify;
        synchronized(this) {
            if (stopped) return;
            if (fromSnapshot) snapshotPaused = safeMode; else hostPaused = safeMode;
            next = hostPaused || snapshotPaused;
            if (paused == next) return;
            paused = next;
        if (paused) clearEffects(); else reconcile();
            notify = fromSnapshot ? changed : null;
        }
        runtime.setSafeMode(next);
        if (notify != null) notify.run();
    }

    void stop() {
        main.removeCallbacks(materialChanged);
        CustomPluginFrameworkReader reader;
        synchronized(this) {
            if (stopped) return;
            stopped = true; generation++; main.removeCallbacks(retryBootPlan); clearEffects(); loaders.clear();
            main.removeCallbacks(refreshBootSnapshot);
            reader = framework; framework = null;
            settings = new JSONObject(); rules = Collections.emptyList(); resolvedRules = 0; changed = null; install = null; context = null;
        }
        if (reader != null) reader.close();
        synchronized(adminResults) {
            adminCommands=null;main.removeCallbacks(retryAdminCommands);adminRetryQueued=false;adminResults.clear();
        }
        runtime.stop();
    }

    /** Package access also permits checks to drive the exact production parser/transition. */
    void accept(JSONObject plan) { accept(plan, ""); }

    private void accept(JSONObject plan, String source) {
        try {
            if (!(plan.opt("settings") instanceof JSONObject)
                    || !(plan.opt("hooks") instanceof JSONArray) || !(plan.opt("revision") instanceof Number)
                    || ((Number) plan.opt("revision")).longValue() < 0
                    || ((Number) plan.opt("revision")).doubleValue() != ((Number) plan.opt("revision")).longValue())
                throw new IllegalArgumentException("Incomplete plugin plan");
            JSONObject nextSettings = new JSONObject(plan.getJSONObject("settings").toString());
            if (nextSettings.length() > 512) throw new IllegalArgumentException("Too many aggregate plugin settings");
            for (java.util.Iterator<String> keys = nextSettings.keys(); keys.hasNext();) {
                String key = keys.next(); JSONObject one = new JSONObject();
                one.put(key,nextSettings.get(key)); CustomPluginSettings.validate(one);
            }
            List<CustomPluginRules.Rule> nextRules = CustomPluginRules.parse(plan.getJSONArray("hooks"),true);
            String nextFingerprint = nextSettings.toString() + "\n" + plan.getJSONArray("hooks").toString();
            boolean shared = "framework".equals(source);
            if (shared && plan.optBoolean("safeMode",false)) updateSafety(true, true);
            Runnable notify = null;
            synchronized(this) {
                if (stopped) return;
                revision = ((Number) plan.opt("revision")).longValue();
                if (!fingerprint.equals(nextFingerprint)) {
                    clearEffects(); rules = nextRules; settings = nextSettings; fingerprint = nextFingerprint; errors = 0; hits = 0; resolvedRules = 0;
                    notify = changed;
                }
                reconcile();
            }
            if (!shared) synchronized(adminResults) {
                adminCommands=plan.optJSONArray("adminCommands");
                java.util.Set<String> present=new java.util.HashSet<>();
                if(adminCommands!=null)for(int i=0;i<adminCommands.length();i++){
                    JSONObject command=adminCommands.optJSONObject(i);if(command!=null)present.add(command.optString("nonce"));
                }
                // Ephemeral commands are revalidated with the App before execution. Withdrawn
                // forms need no retained response, even if reporting previously failed.
                adminResults.entrySet().removeIf(entry -> entry.getValue().ephemeral
                        && !entry.getValue().executing && !present.contains(entry.getKey()));
                main.removeCallbacks(retryAdminCommands);adminRetryQueued=false;
            }
            // API 2 can call through native methods that are also wrapped by API 1.
            // Never hold this monitor while invoking a plugin or waiting on its scope.
            boolean accepted = runtime.accept(plan);
            if (accepted) synchronized(this) {
                if (!stopped) {
                    confirmed = !plan.has("error");
                    if ("provider".equals(source) && confirmed) lastPrimaryRevision = revision;
                    if (confirmed && !source.isEmpty() && !source.equals(planSource)) {
                        planSource = source;
                        android.util.Log.i("C17-PluginBoot", "SystemUI accepted plugin plan from " + source);
                    }
                    if (confirmed) main.removeCallbacks(retryBootPlan);
                }
            }
            if (accepted && (!shared || !plan.optBoolean("safeMode",false)) && !source.isEmpty()) updateSafety(false, true);
            executeAdminCommands();
            if (notify != null) notify.run();
        } catch (Exception | LinkageError invalid) { report("Invalid plugin plan; retaining the previous plan",invalid); }
    }

    private static final class AdminResult { Bundle values; boolean reported,executing,ephemeral; }
    private final Map<String,AdminResult> adminResults = new LinkedHashMap<>();
    private JSONArray adminCommands;
    private boolean adminRetryQueued;
    private final Runnable retryAdminCommands=this::executeAdminCommands;
    private void executeAdminCommands() {
        Context owner;
        synchronized(this) { owner = stopped ? null : context; }
        if(owner==null)return;
        JSONArray commands;
        synchronized(adminResults) {
            commands=adminCommands;if(commands==null)return;
            if(Looper.myLooper()!=main.getLooper()){
                if(!adminRetryQueued)adminRetryQueued=main.post(retryAdminCommands);
                return;
            }
            main.removeCallbacks(retryAdminCommands);adminRetryQueued=false;
        }
        for(int i=0;i<commands.length();i++)try {
            JSONObject command=commands.getJSONObject(i);String nonce=command.getString("nonce");
            AdminResult cached; boolean execute;
            synchronized(adminResults) {
                if(commands!=adminCommands)return;
                cached=adminResults.get(nonce);
                if(cached==null){cached=new AdminResult();adminResults.put(nonce,cached);}
                execute=cached.values==null&&!cached.executing;
                if(execute)cached.executing=true;
            }
            if(execute) {
                Bundle result=new Bundle();result.putString("nonce",nonce);
                boolean formCommand="form_status".equals(command.optString("action"))||"form_action".equals(command.optString("action"));
                synchronized(adminResults){cached.ephemeral=formCommand;}
                if(formCommand){
                    try{
                    Bundle check=new Bundle();check.putString("nonce",nonce);check.putBoolean("ephemeral_form",true);check.putBoolean("form_check",true);
                    Bundle present=owner.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI),CustomPluginAdmin.REPORT,null,check);
                    if(present==null)throw new IllegalStateException("Form request could not be confirmed");
                    if(!present.getBoolean("present",false)){
                        synchronized(adminResults){cached.executing=false;cached.reported=true;cached.values=result;}continue;
                    }
                    result.putBoolean("ephemeral_form",true);
                    }catch(Exception|LinkageError unavailable){synchronized(adminResults){cached.executing=false;}throw unavailable;}
                }
                try {
                    // Previously persisted eval commands have no action field.
                    switch(command.optString("action","eval")){
                        case "runtime_status": {
                            Bundle health=new Bundle();health(health);JSONObject fields=new JSONObject();
                            for(String key:health.keySet())fields.put(key,health.get(key));
                            // The hook cost switch is read here, on request, never from a hook.
                            boolean traceChanged=HookTrace.refresh();
                            JSONObject status=new JSONObject().put("build_token",ModuleRuntimeStatus.BUILD_TOKEN)
                                    .put("pid",android.os.Process.myPid()).put("health",fields).put("plugins",runtime.status());
                            if(HookTrace.on()||traceChanged){
                                status.put("hook_trace",HookTrace.report(command.optInt("hook_trace_limit",60)));
                                status.put("mini_stack",StatusBarModule.miniStackSummary());
                                if(command.optBoolean("hook_trace_reset",false))HookTrace.reset();
                            }
                            result.putString("result",status.toString());
                            break;
                        }
                        case "eval": result.putString("result",runtime.evaluateCommand(command.getString("id"),command.getString("source")));break;
                        case "form_status":result.putString("result",runtime.formStatusCommand(command.getString("id")));break;
                        case "form_action":result.putString("result",runtime.actionCommand(command.getString("id"),command.getString("instanceId"),nonce,command.getString("key")));break;
                        default: throw new IllegalArgumentException("Unknown plugin admin command");
                    }
                }
                // Pending is raised before JS execution; this nonce remains eligible for retry.
                catch(CustomPluginRuntime.CommandPendingException pending){result=null;}
                catch(Throwable failure){result.putString("error",failure.toString());}
                synchronized(adminResults) { cached.values=result;cached.executing=false; }
            }
            Bundle report;
            synchronized(adminResults) {
                if(commands!=adminCommands)return;
                report=cached.reported||cached.values==null?null:new Bundle(cached.values);
            }
            if(report==null)continue;
            Bundle acknowledgement=owner.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI),CustomPluginAdmin.REPORT,null,report);
            if(acknowledgement==null||acknowledgement.containsKey("error"))throw new IllegalStateException("Plugin admin command report was not acknowledged");
            synchronized(adminResults) { cached.reported=true;if(cached.ephemeral)cached.values=new Bundle(); }
        }catch(Exception|LinkageError failure){report("Plugin admin command unavailable",failure);}
        synchronized(adminResults) {
            if(commands!=adminCommands)return;
            boolean pending=false;
            for(int i=0;i<commands.length();i++){
                JSONObject command=commands.optJSONObject(i);
                AdminResult cached=command==null?null:adminResults.get(command.optString("nonce"));
                if(cached!=null&&!cached.reported)pending=true;
            }
            if(!pending){main.removeCallbacks(retryAdminCommands);adminRetryQueued=false;}
            else if(!adminRetryQueued)adminRetryQueued=main.postDelayed(retryAdminCommands,100);
        }
    }

    private void reconcile() {
        retryCleanup();
        if (stopped || paused || install == null || rules.isEmpty()) return;
        Map<Method,List<CustomPluginRules.Rule>> desired = new LinkedHashMap<>();
        java.util.Set<CustomPluginRules.Rule> resolved = new java.util.HashSet<>();
        for (WeakReference<ClassLoader> ref : loaders) {
            ClassLoader loader = ref.get(); if (loader == null) continue;
            for (CustomPluginRules.Rule rule : rules) try {
                Method method = rule.resolve(loader);
                resolved.add(rule);
                List<CustomPluginRules.Rule> group = desired.get(method);
                if (group == null) {
                    if (desired.size() >= CustomPluginRules.MAX_PLAN_HOOKS) continue;
                    group = new ArrayList<>(); desired.put(method,group);
                }
                // Parent-first loaders can resolve the same Method. Apply each declaration once.
                if (!group.contains(rule)) group.add(rule);
            } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError unsupported) {
                // Optional OEM classes commonly arrive in a later loader; no class-wide fallback.
            }
        }
        resolvedRules = resolved.size();
        for (Map.Entry<Method,List<CustomPluginRules.Rule>> site : desired.entrySet()) {
            Binding known = installed.get(site.getKey());
            if (known != null) { known.rules = site.getValue(); continue; }
            if (installed.size() + cleanup.size() >= CustomPluginRules.MAX_PLAN_HOOKS) break;
            boolean pending = false;
            for (Binding orphan : cleanup) if (orphan.method.equals(site.getKey())) pending = true;
            if (pending) continue;
            Binding binding = new Binding(site.getKey(),site.getValue());
            try {
                binding.handle = install.hook(binding.method,chain -> intercept(binding,chain));
                if (binding.handle == null) throw new IllegalStateException("Framework returned no removable hook handle");
                binding.active = true; installed.put(binding.method,binding);
            } catch (Throwable refused) { binding.active = false; report("Plugin method is unavailable on this system",refused); }
        }
        String summary = "Plugin rules requested="+rules.size()+", resolved="+resolvedRules+", installed="+installed.size();
        if (!summary.equals(lastReport)) {
            lastReport = summary; ModuleDiagnostics.info("custom_plugins",summary);
        }
    }

    private Object intercept(Binding binding,XposedInterface.Chain chain) throws Throwable {
        List<CustomPluginRules.Rule> current;
        View view = null;
        boolean bypass;
        synchronized (this) {
            bypass = stopped || paused || !binding.active || Boolean.TRUE.equals(applying.get());
            current = binding.rules;
            Object receiver = chain.getThisObject();
            if (!bypass && receiver instanceof View && Looper.myLooper() == main.getLooper()) {
                view = (View) receiver;
                applying.set(true);
                try { ViewState old = views.get(view); if (old != null) old.restoreFor(view,current); }
                finally { applying.remove(); }
            }
        }
        if (bypass) return chain.proceed();
        Object result = chain.proceed();
        synchronized (this) {
            if (stopped || paused || !binding.active) return result;
            if (hits < Long.MAX_VALUE) hits++;
            applying.set(true);
            try {
                // A nested native update may already have applied this plan. Remove its own
                // output before composing the outer callback, so prefixes never accumulate.
                if (view != null) { ViewState old = views.get(view); if (old != null) old.restoreFor(view,current); }
                for (CustomPluginRules.Rule rule : current) {
                    if (rule.kind.equals("result")) result = rule.resultFor(binding.method.getReturnType());
                    else if (view != null) apply(view,rule);
                }
            } catch (RuntimeException | LinkageError failed) { report("Plugin view update was skipped",failed); }
            finally { applying.remove(); }
        }
        return result;
    }

    private void apply(View view,CustomPluginRules.Rule rule) {
        ViewState state = views.get(view);
        if (state == null) {
            if (views.size() >= MAX_VIEWS) return;
            state = new ViewState(); views.put(view,state);
        }
        if (rule.kind.equals("property")) {
            float value = ((Number) rule.value).floatValue();
            if (rule.property.startsWith("translation")) value *= view.getResources().getDisplayMetrics().density;
            FloatState property = state.properties.get(rule.property);
            if (property == null) { property = new FloatState(); state.properties.put(rule.property,property); }
            float existing = read(view,rule.property);
            if (!property.owned || Float.compare(existing,property.applied) != 0) property.nativeValue = existing;
            property.applied = value; property.owned = true; write(view,rule.property,value);
        } else if (view instanceof TextView) {
            TextView text = (TextView) view; CharSequence existing = text.getText();
            if (existing == null || existing.length() > MAX_TEXT) return;
            if (!state.textOwned || !same(existing,state.appliedText)) state.nativeText = existing;
            String value = existing.toString();
            if (!rule.find.isEmpty()) {
                // String.replace can expand a long string dramatically; preflight the exact literal size.
                long length = value.length(); int start = 0, index;
                while ((index = value.indexOf(rule.find,start)) >= 0) {
                    length += rule.replace.length() - rule.find.length(); start = index + rule.find.length();
                }
                if (length + rule.prefix.length() + rule.suffix.length() > MAX_TEXT) return;
                value = value.replace(rule.find,rule.replace);
            }
            if ((long) value.length() + rule.prefix.length() + rule.suffix.length() > MAX_TEXT) return;
            value = rule.prefix + value + rule.suffix;
            state.appliedText = value; state.textOwned = true;
            if (!same(existing,value)) text.setText(value);
        }
    }

    private void clearEffects() {
        for (Binding binding : installed.values()) {
            binding.active = false;
            cleanup.add(binding);
        }
        installed.clear(); retryCleanup(); applying.set(true);
        try {
            for (Map.Entry<View,ViewState> effect : views.entrySet()) if (effect.getKey() != null) {
                View view = effect.getKey(); ViewState state = effect.getValue();
                if (Looper.myLooper() == main.getLooper()) try { state.restore(view); }
                catch (RuntimeException unavailable) { report("Native plugin view is no longer available",unavailable); }
                else { WeakReference<View> weak = new WeakReference<>(view); main.post(() -> {
                    View remaining = weak.get(); if (remaining != null) try { state.restore(remaining); }
                    catch (RuntimeException unavailable) { report("Native plugin view is no longer available",unavailable); }
                }); }
            }
        } finally { applying.remove(); views.clear(); }
    }

    private void retryCleanup() {
        for (java.util.Iterator<Binding> it = cleanup.iterator(); it.hasNext();) {
            Binding binding = it.next();
            try { if (binding.handle != null) binding.handle.unhook(); it.remove(); }
            catch (Throwable refused) { report("Plugin hook removal is bypassed pending a later cleanup",refused); }
        }
    }

    private synchronized void report(String message,Throwable cause) {
        if (errors++ < 6) ModuleDiagnostics.error("custom_plugins",message,cause);
    }
    private static boolean same(CharSequence left,CharSequence right) { return left == right || left != null && right != null && left.toString().equals(right.toString()); }
    private static float read(View view,String name) {
        switch (name) {
            case "alpha": return view.getAlpha(); case "translationX": return view.getTranslationX();
            case "translationY": return view.getTranslationY(); case "scaleX": return view.getScaleX();
            case "scaleY": return view.getScaleY(); default: throw new IllegalArgumentException("Unknown View property");
        }
    }
    private static void write(View view,String name,float value) {
        switch (name) {
            case "alpha": view.setAlpha(value); break; case "translationX": view.setTranslationX(value); break;
            case "translationY": view.setTranslationY(value); break; case "scaleX": view.setScaleX(value); break;
            case "scaleY": view.setScaleY(value); break; default: throw new IllegalArgumentException("Unknown View property");
        }
    }
    private static final class FloatState { float nativeValue,applied; boolean owned; }
    private static final class ViewState {
        final Map<String,FloatState> properties = new LinkedHashMap<>();
        CharSequence nativeText,appliedText; boolean textOwned;
        void restoreFor(View view,List<CustomPluginRules.Rule> rules) {
            for (CustomPluginRules.Rule rule : rules) {
                if (rule.kind.equals("property")) restoreProperty(view,rule.property,properties.get(rule.property));
                else if (rule.kind.equals("text")) restoreText(view);
            }
        }
        void restore(View view) {
            for (Map.Entry<String,FloatState> property : properties.entrySet()) {
                restoreProperty(view,property.getKey(),property.getValue());
            }
            restoreText(view);
        }
        private void restoreProperty(View view,String name,FloatState state) {
            if (state == null) return;
            if (state.owned && Float.compare(read(view,name),state.applied) == 0) write(view,name,state.nativeValue);
            state.owned = false;
        }
        private void restoreText(View view) {
            if (textOwned && view instanceof TextView && same(((TextView) view).getText(),appliedText)) ((TextView) view).setText(nativeText);
            textOwned = false;
        }
    }
    private static final class Binding {
        final Method method; volatile List<CustomPluginRules.Rule> rules;
        XposedInterface.HookHandle handle; boolean active;
        Binding(Method method,List<CustomPluginRules.Rule> rules) { this.method = method; this.rules = rules; }
    }
}
