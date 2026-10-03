// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedInterface;

/** A new challenge must be answered by the current injected SystemUI process. */
public final class ModuleRuntimeStatus {
    public static final String SYSTEM_UI = "com.android.systemui";
    public static final String ACTION_PING = "dev.puitheme.iosstatusbar.PING_RUNTIME_STATUS";
    public static final String METHOD_BEGIN = "begin_runtime_probe";
    public static final String METHOD_QUERY = "query_runtime_status";
    public static final String METHOD_REPORT = "report_runtime_status";
    // Change this whenever distributing a build whose runtime gate must replace an earlier build.
    public static final String BUILD_TOKEN = "c17-runtime-20261003-single-network-shade-position-code64";
    public static final String KEY_NONCE = "nonce";
    public static final String KEY_PID = "pid";
    public static final String KEY_UPTIME = "uptime_ms";
    public static final String KEY_BUILD = "build_token";
    public static final String KEY_API = "api_version";
    public static final String KEY_FRAMEWORK = "framework_name";
    public static final String KEY_FRAMEWORK_VERSION = "framework_version";
    public static final String KEY_ACTIVE = "active";
    public static final long PROBE_TIMEOUT_MS = 2500L;
    private static final String URI = "content://dev.puitheme.iosstatusbar.settings/values";
    public static final String RUNTIME_URI = "content://dev.puitheme.iosstatusbar.settings/runtime_status";
    private static final Object LOCK = new Object();
    private static final ScheduledThreadPoolExecutor WORK = new ScheduledThreadPoolExecutor(1);
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static Challenge challenge;
    private static BroadcastReceiver systemUiReceiver;
    private static HandlerThread receiverThread;
    private static ProbeHandle currentProbe;

    static { WORK.setRemoveOnCancelPolicy(true); }

    private ModuleRuntimeStatus() { }

    public enum State { CHECKING, ACTIVE, NOT_DETECTED, ERROR }
    public interface Callback { void onStatus(Snapshot result); }

    public static final class Snapshot {
        public final State state;
        /** Only a verified response to the present challenge can make this true. */
        public final boolean active;
        public final String message, summary, framework, detail, frameworkName, frameworkVersion;
        public final int pid, apiVersion;
        public final long uptimeMillis;
        private Snapshot(State state, String message, int pid, long uptime, int api,
                String framework, String version) {
            this.state = state;
            this.active = state == State.ACTIVE;
            this.message = message;
            this.summary = message;
            this.pid = pid;
            this.uptimeMillis = uptime;
            this.apiVersion = api;
            this.frameworkName = framework;
            this.frameworkVersion = version;
            this.framework = framework.isEmpty() ? "" : framework + " " + version;
            this.detail = state == State.ACTIVE ? "API " + api + " · SystemUI PID " + pid : "";
        }
    }

    static final class Challenge {
        final String nonce;
        final long startedElapsed, startedUptime, deadlineElapsed;
        Snapshot result;
        Challenge(String nonce, long elapsed, long uptime) {
            this.nonce = nonce;
            this.startedElapsed = elapsed;
            this.startedUptime = uptime;
            this.deadlineElapsed = elapsed + PROBE_TIMEOUT_MS;
        }
    }

    static boolean allowedReporter(int callerUid, int ownUid, int systemUiUid) {
        return systemUiUid > 0 && callerUid == systemUiUid && callerUid != ownUid;
    }

    static boolean accepts(Challenge request, String nonce, int claimedPid, int callerPid,
            long uptime, long nowElapsed, long nowUptime, String token, int api,
            String framework, String version) {
        return request != null && validNonce(nonce) && request.nonce.equals(nonce)
                && nowElapsed >= request.startedElapsed && nowElapsed < request.deadlineElapsed
                && claimedPid > 0 && claimedPid == callerPid
                && uptime >= request.startedUptime && uptime <= nowUptime
                && BUILD_TOKEN.equals(token) && api >= 101
                && validLspName(framework) && validFramework(version);
    }

    private static boolean validNonce(String nonce) {
        return nonce != null && nonce.matches("[a-f0-9]{32}");
    }

    private static boolean validFramework(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 160;
    }

    private static boolean validLspName(String value) {
        return validFramework(value) && value.toLowerCase(Locale.ROOT).contains("lsposed");
    }

    private static void enforceOwnUid() {
        if (Binder.getCallingUid() != Process.myUid())
            throw new SecurityException("Runtime probes are only available to this app");
    }

    /** SettingsProvider routes METHOD_BEGIN here. No saved heartbeat is reused. */
    public static Bundle beginProbe(Context context, Bundle extras) {
        enforceOwnUid();
        String nonce = extras == null ? null : extras.getString(KEY_NONCE);
        if (!validNonce(nonce)) throw new IllegalArgumentException("Invalid runtime probe nonce");
        Challenge started = new Challenge(nonce, SystemClock.elapsedRealtime(), SystemClock.uptimeMillis());
        synchronized (LOCK) { challenge = started; }
        Bundle response = new Bundle();
        response.putBoolean("started", true);
        response.putLong("deadline_elapsed", started.deadlineElapsed);
        return response;
    }

    /** SettingsProvider routes METHOD_REPORT here, before its regular reader permission check. */
    public static Bundle recordSystemUiReport(Context context, Bundle extras) {
        int systemUiUid;
        try {
            systemUiUid = context.getPackageManager().getApplicationInfo(SYSTEM_UI, 0).uid;
        } catch (Exception unavailable) {
            throw new SecurityException("SystemUI identity is unavailable", unavailable);
        }
        if (!allowedReporter(Binder.getCallingUid(), Process.myUid(), systemUiUid))
            throw new SecurityException("Only the actual SystemUI UID may report module activation");
        boolean accepted = false;
        if (extras != null) synchronized (LOCK) {
            String nonce = extras.getString(KEY_NONCE);
            int pid = extras.getInt(KEY_PID, -1), api = extras.getInt(KEY_API, 0);
            long uptime = extras.getLong(KEY_UPTIME, -1L);
            String framework = extras.getString(KEY_FRAMEWORK), version = extras.getString(KEY_FRAMEWORK_VERSION);
            accepted = accepts(challenge, nonce, pid, Binder.getCallingPid(), uptime,
                    SystemClock.elapsedRealtime(), SystemClock.uptimeMillis(), extras.getString(KEY_BUILD),
                    api, framework, version);
            if (accepted) challenge.result = new Snapshot(State.ACTIVE, "模块已在 SystemUI 中激活", pid,
                    uptime, api, framework, version);
        }
        Bundle response = new Bundle();
        response.putBoolean("accepted", accepted);
        if (accepted) context.getContentResolver().notifyChange(runtimeUri(extras.getString(KEY_NONCE)), null, 0);
        return response;
    }

    /** SettingsProvider routes METHOD_QUERY here. An old or expired nonce is always inactive. */
    public static Bundle queryStatus(Context context, Bundle extras) {
        enforceOwnUid();
        String nonce = extras == null ? null : extras.getString(KEY_NONCE);
        Bundle response = new Bundle();
        response.putBoolean(KEY_ACTIVE, false);
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            if (challenge == null || !validNonce(nonce) || !challenge.nonce.equals(nonce)
                    || now < challenge.startedElapsed || now >= challenge.deadlineElapsed) return response;
            Snapshot result = challenge.result;
            if (result != null) {
                response.putBoolean(KEY_ACTIVE, true);
                response.putString(KEY_NONCE, nonce);
                response.putString(KEY_BUILD, BUILD_TOKEN);
                response.putInt(KEY_PID, result.pid);
                response.putLong(KEY_UPTIME, result.uptimeMillis);
                response.putInt(KEY_API, result.apiVersion);
                response.putString(KEY_FRAMEWORK, result.frameworkName);
                response.putString(KEY_FRAMEWORK_VERSION, result.frameworkVersion);
            }
        }
        return response;
    }

    public static void registerSystemUiReceiver(Context context, XposedInterface framework) {
        registerSystemUiReceiver(context, framework.getFrameworkName(), framework.getFrameworkVersion(), framework.getApiVersion());
    }

    public interface VerifiedProbeListener { void onVerifiedProbe(String nonce); }
    private static volatile VerifiedProbeListener verifiedProbeListener;
    public static void setVerifiedProbeListener(VerifiedProbeListener listener) { verifiedProbeListener = listener; }

    /** Call once from injected SystemUI after its application context becomes available. */
    public static void registerSystemUiReceiver(Context supplied, String framework, String version, int api) {
        if (!SYSTEM_UI.equals(supplied.getPackageName()))
            throw new IllegalArgumentException("Runtime receiver requires the SystemUI context");
        if (!validLspName(framework) || !validFramework(version) || api < 101)
            throw new IllegalArgumentException("Runtime receiver requires verified framework details");
        synchronized (LOCK) {
            if (systemUiReceiver != null) return;
            Context application = supplied.getApplicationContext();
            final Context context = application == null ? supplied : application;
            HandlerThread thread = new HandlerThread("C17RuntimeReporter");
            thread.start();
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context received, Intent intent) {
                    if (intent == null || !ACTION_PING.equals(intent.getAction())) return;
                    String nonce = intent.getStringExtra(KEY_NONCE);
                    if (!validNonce(nonce)) return;
                    Bundle report = new Bundle();
                    report.putString(KEY_NONCE, nonce);
                    report.putInt(KEY_PID, Process.myPid());
                    report.putLong(KEY_UPTIME, SystemClock.uptimeMillis());
                    report.putString(KEY_BUILD, BUILD_TOKEN);
                    report.putInt(KEY_API, api);
                    report.putString(KEY_FRAMEWORK, framework);
                    report.putString(KEY_FRAMEWORK_VERSION, version);
                    try {
                        Bundle response = context.getContentResolver().call(Uri.parse(URI), METHOD_REPORT, null, report);
                        VerifiedProbeListener listener = verifiedProbeListener;
                        if (response != null && response.getBoolean("accepted", false) && listener != null)
                            listener.onVerifiedProbe(nonce);
                    } catch (RuntimeException unavailable) {
                        // No report means inactive. Provider failure must not crash SystemUI.
                    }
                }
            };
            try {
                IntentFilter filter = new IntentFilter(ACTION_PING);
                Handler handler = new Handler(thread.getLooper());
                if (Build.VERSION.SDK_INT >= 33)
                    context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED);
                else context.registerReceiver(receiver, filter, null, handler);
                systemUiReceiver = receiver;
                receiverThread = thread;
            } catch (RuntimeException error) {
                thread.quitSafely();
                throw error;
            }
        }
    }

    /** Pure completion latch: duplicate notifications and cancellation cannot complete twice. */
    static final class ProbeState {
        private boolean done, queryStarted;
        synchronized boolean beginQuery() {
            if (done || queryStarted) return false;
            queryStarted = true;
            return true;
        }
        synchronized boolean finish() {
            if (done) return false;
            done = true;
            return true;
        }
        synchronized boolean open() { return !done; }
    }

    private static Uri runtimeUri(String nonce) { return Uri.parse(RUNTIME_URI).buildUpon().appendPath(nonce).build(); }

    public static final class ProbeHandle {
        private volatile boolean cancelled;
        private volatile long deadlineElapsed = Long.MAX_VALUE;
        private final int generation;
        private final Context context;
        private final Handler main;
        private final Callback callback;
        private final String nonce = UUID.randomUUID().toString().replace("-", "");
        private final ProbeState state = new ProbeState();
        private ContentObserver observer;
        private volatile ScheduledFuture<?> timeout;

        private ProbeHandle(int generation, Context context, Callback callback) {
            this.generation = generation;
            this.context = context;
            this.callback = callback;
            this.main = new Handler(Looper.getMainLooper());
        }

        /** Cancel on pause; queued callbacks cannot unlock an Activity after cancellation. */
        public synchronized void cancel() {
            if (cancelled) return;
            cancelled = true;
            state.finish();
            ScheduledFuture<?> scheduled = timeout;
            if (scheduled != null) scheduled.cancel(false);
            WORK.execute(this::cleanup);
        }

        private boolean current() { return !cancelled && GENERATION.get() == generation; }
        private boolean live() { return current() && state.open(); }
        private Bundle request() {
            Bundle request = new Bundle();
            request.putString(KEY_NONCE, nonce);
            return request;
        }

        private void start() {
            if (!live()) return;
            try {
                observer = new ContentObserver(null) {
                    @Override public void onChange(boolean selfChange) {
                        if (live() && state.beginQuery()) WORK.execute(ProbeHandle.this::queryReply);
                    }
                };
                // Register first: even an immediate SystemUI response cannot be missed.
                context.getContentResolver().registerContentObserver(runtimeUri(nonce), false, observer);
                if (!live()) { cleanup(); return; }
                Bundle began = context.getContentResolver().call(Uri.parse(URI), METHOD_BEGIN, null, request());
                if (began == null || !began.getBoolean("started", false))
                    throw new IllegalStateException("Probe not started");
                deadlineElapsed = began.getLong("deadline_elapsed", 0L);
                long remaining = deadlineElapsed - SystemClock.elapsedRealtime();
                if (!live()) { cleanup(); return; }
                if (remaining <= 0L || remaining > PROBE_TIMEOUT_MS) { finish(notDetected()); return; }
                timeout = WORK.schedule(() -> finish(notDetected()), remaining, TimeUnit.MILLISECONDS);
                if (!live()) { cleanup(); return; }
                context.sendBroadcast(new Intent(ACTION_PING).setPackage(SYSTEM_UI).putExtra(KEY_NONCE, nonce));
            } catch (RuntimeException error) {
                finish(unavailable(State.ERROR, "无法检测模块激活状态，请重新检测。"));
            }
        }

        private void queryReply() {
            if (!live()) return;
            try {
                if (SystemClock.elapsedRealtime() >= deadlineElapsed) { finish(notDetected()); return; }
                Bundle reply = context.getContentResolver().call(Uri.parse(URI), METHOD_QUERY, null, request());
                if (reply != null && reply.getBoolean(KEY_ACTIVE, false)
                        && nonce.equals(reply.getString(KEY_NONCE)) && BUILD_TOKEN.equals(reply.getString(KEY_BUILD))) {
                    int api = reply.getInt(KEY_API, 0), pid = reply.getInt(KEY_PID, -1);
                    String framework = reply.getString(KEY_FRAMEWORK), version = reply.getString(KEY_FRAMEWORK_VERSION);
                    if (api >= 101 && pid > 0 && validLspName(framework) && validFramework(version)) {
                        finish(new Snapshot(State.ACTIVE, "模块已在 SystemUI 中激活", pid,
                                reply.getLong(KEY_UPTIME), api, framework, version));
                        return;
                    }
                }
                finish(notDetected());
            } catch (RuntimeException error) {
                finish(unavailable(State.ERROR, "无法检测模块激活状态，请重新检测。"));
            }
        }

        private void finish(Snapshot result) {
            if (!state.finish()) return;
            cleanup();
            deliver(main, this, callback, result);
        }

        // All observer registration and removal run on the one shared worker.
        private void cleanup() {
            ScheduledFuture<?> scheduled = timeout;
            timeout = null;
            if (scheduled != null) scheduled.cancel(false);
            ContentObserver registered = observer;
            observer = null;
            if (registered != null) {
                try { context.getContentResolver().unregisterContentObserver(registered); }
                catch (RuntimeException ignored) { }
            }
            synchronized (LOCK) { if (currentProbe == this) currentProbe = null; }
        }
    }

    /** One event-driven challenge, with main-thread callbacks and no periodic work. */
    public static ProbeHandle probe(Context supplied, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Runtime probe callback is required");
        Context application = supplied.getApplicationContext();
        Context context = application == null ? supplied : application;
        ProbeHandle handle = new ProbeHandle(GENERATION.incrementAndGet(), context, callback);
        synchronized (LOCK) {
            if (currentProbe != null) currentProbe.cancel();
            currentProbe = handle;
        }
        deliver(handle.main, handle, callback, unavailable(State.CHECKING, "正在检测模块激活状态…"));
        WORK.execute(handle::start);
        return handle;
    }

    private static Snapshot notDetected() {
        return unavailable(State.NOT_DETECTED,
                "未检测到模块激活。请在 LSP 中启用本模块并勾选 SystemUI，再重启系统 UI 后重新检测。");
    }

    private static Snapshot unavailable(State state, String message) {
        return new Snapshot(state, message, 0, 0L, 0, "", "");
    }

    private static void deliver(Handler main, ProbeHandle handle, Callback callback, Snapshot result) {
        main.post(() -> {
            if (!handle.current()) return;
            if (result.active && SystemClock.elapsedRealtime() >= handle.deadlineElapsed)
                callback.onStatus(unavailable(State.NOT_DETECTED, "本次检测已过期，请重新检测模块激活状态。"));
            else callback.onStatus(result);
        });
    }

}
