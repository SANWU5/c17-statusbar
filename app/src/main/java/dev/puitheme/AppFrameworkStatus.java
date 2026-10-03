// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Official module-app service connection, independent of a particular injected SystemUI build.
 * A connected service proves framework communication; ModuleRuntimeStatus separately verifies
 * that the current build was loaded in SystemUI. No manager package or stored heartbeat is used.
 */
public final class AppFrameworkStatus {
    private static final int REQUIRED_API = 101;
    private static final long INITIAL_WAIT_MS = 4000L;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<Subscription> OBSERVERS = new LinkedHashSet<>();
    private static final Map<XposedService, Connection> SERVICES = new IdentityHashMap<>();
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "C17-framework-status");
        worker.setDaemon(true);
        return worker;
    });
    private static boolean started;
    private static volatile Snapshot snapshot = waiting();

    public enum State { CHECKING, CONNECTED, NOT_CONNECTED, ERROR }
    public interface Callback { void onStatus(Snapshot status); }

    public static final class Snapshot {
        public final State state;
        /** True only after the official helper delivered a service and its RPCs succeeded. */
        public final boolean connected;
        /** The module requires API 101; framework API 102 is also supported. */
        public final boolean compatible;
        public final int apiVersion;
        public final String frameworkName, frameworkVersion, message;
        public final long frameworkVersionCode;

        private Snapshot(State state, int api, String name, String version, long code, String message) {
            this.state = state;
            this.connected = state == State.CONNECTED;
            this.compatible = connected && api >= REQUIRED_API;
            this.apiVersion = api;
            this.frameworkName = name;
            this.frameworkVersion = version;
            this.frameworkVersionCode = code;
            this.message = message;
        }
    }

    /** Close on Activity destruction; queued callbacks are suppressed and release the Activity. */
    public static final class Subscription implements AutoCloseable {
        private volatile boolean closed;
        private Callback callback;
        private Subscription(Callback callback) { this.callback = callback; }
        @Override public void close() {
            synchronized (LOCK) {
                closed = true;
                callback = null;
                OBSERVERS.remove(this);
            }
        }
        public void cancel() { close(); }
    }

    private static final class Connection {
        volatile boolean live = true;
        Snapshot result;
    }

    private AppFrameworkStatus() { }

    public static Snapshot current() { return snapshot; }

    /** All callbacks, including the initial cached state, are delivered on the main thread. */
    public static Subscription observe(Callback callback) {
        Subscription subscription = new Subscription(Objects.requireNonNull(callback));
        synchronized (LOCK) {
            OBSERVERS.add(subscription);
            deliver(subscription, snapshot);
        }
        start();
        return subscription;
    }

    /** May be called from Application.onCreate; the official listener is registered only once. */
    public static void start(Context context) {
        SettingsFrameworkMirror.attach(context);
        start();
        // An earlier metadata-only subscription may have preceded the app context.
        synchronized(LOCK) {
            for(Map.Entry<XposedService,Connection> entry:SERVICES.entrySet())
                if(entry.getValue().live&&entry.getValue().result!=null&&entry.getValue().result.compatible)
                    SettingsFrameworkMirror.bound(entry.getKey());
        }
        if(context!=null)SettingsSnapshot.frameworkConnected(context);
    }

    public static void start() {
        synchronized (LOCK) {
            if (started) return;
            started = true;
        }
        try {
            // libxposed permits exactly one process-wide listener and provides no unregister API.
            // Keep this listener for process lifetime; UI subscriptions are separately disposable.
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override public void onServiceBind(XposedService service) { bind(service); }
                @Override public void onServiceDied(XposedService service) { died(service); }
            });
            MAIN.postDelayed(() -> {
                synchronized (LOCK) {
                    if (snapshot.state == State.CHECKING) publish(disconnected());
                }
            }, INITIAL_WAIT_MS);
        } catch (RuntimeException | LinkageError unavailable) {
            synchronized (LOCK) { publish(error()); }
            ModuleDiagnostics.error("framework", "Official framework service registration failed", unavailable);
        }
    }

    private static void bind(XposedService service) {
        if (service == null) return;
        Connection connection = new Connection();
        synchronized (LOCK) {
            Connection previous = SERVICES.put(service, connection);
            if (previous != null) previous.live = false;
        }
        // The helper may replay a cached service synchronously during registration. Do not make
        // Binder RPCs on the UI thread or while holding the helper's internal cache lock.
        WORK.execute(() -> {
            if (!connection.live) return;
            Snapshot result;
            try {
                int api = service.getApiVersion();
                String name = text(service.getFrameworkName(), "Xposed");
                String version = text(service.getFrameworkVersion(), "未知版本");
                long code = service.getFrameworkVersionCode();
                String message = api >= REQUIRED_API ? "模块已启用 · " + name + " " + version
                        : "框架服务已连接，但当前 API " + api + " 低于模块要求的 API " + REQUIRED_API;
                result = new Snapshot(State.CONNECTED, api, name, version, code, message);
            } catch (RuntimeException | LinkageError unavailable) {
                result = error();
                ModuleDiagnostics.error("framework", "Official framework service metadata unavailable", unavailable);
            }
            synchronized (LOCK) {
                if (!connection.live || SERVICES.get(service) != connection) return;
                connection.result = result;
                publish(bestConnection());
                if(result.compatible)SettingsFrameworkMirror.bound(service);
            }
        });
    }

    private static void died(XposedService service) {
        synchronized (LOCK) {
            Connection connection = SERVICES.remove(service);
            if (connection == null) return;
            connection.live = false;
            SettingsFrameworkMirror.unbound(service);
            publish(bestConnection());
        }
    }

    /** One dead framework cannot invalidate another live connection. Prefer LSPosed when present. */
    private static Snapshot bestConnection() {
        Snapshot selected = null, failure = null;
        for (Connection connection : SERVICES.values()) {
            Snapshot candidate = connection.result;
            if (!connection.live || candidate == null) continue;
            if (!candidate.connected) { failure = candidate; continue; }
            boolean lsp = candidate.frameworkName.toLowerCase(java.util.Locale.ROOT).contains("lsposed");
            boolean selectedLsp = selected != null
                    && selected.frameworkName.toLowerCase(java.util.Locale.ROOT).contains("lsposed");
            if (selected == null || candidate.compatible && !selected.compatible
                    || candidate.compatible == selected.compatible && lsp && !selectedLsp)
                selected = candidate;
        }
        if (selected != null) return selected;
        if (failure != null) return failure;
        return SERVICES.isEmpty() ? disconnected() : waiting();
    }

    /** Called under LOCK so bind/death notifications retain their actual publication order. */
    private static void publish(Snapshot next) {
        snapshot = next;
        for (Subscription subscription : OBSERVERS) deliver(subscription, next);
    }

    private static void deliver(Subscription subscription, Snapshot next) {
        MAIN.post(() -> {
            Callback callback;
            synchronized (LOCK) { callback = subscription.closed ? null : subscription.callback; }
            if (callback != null) callback.onStatus(next);
        });
    }

    private static Snapshot waiting() {
        return new Snapshot(State.CHECKING, 0, "", "", 0L, "正在连接框架服务…");
    }
    private static Snapshot disconnected() {
        return new Snapshot(State.NOT_CONNECTED, 0, "", "", 0L,
                "尚未连接框架服务。若已启用模块，请完全关闭本应用后重新打开。");
    }
    private static Snapshot error() {
        return new Snapshot(State.ERROR, 0, "", "", 0L, "框架服务状态读取失败，请重新打开本应用。");
    }
    private static String text(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String singleLine = value.trim().replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() > 160 ? singleLine.substring(0, 160) : singleLine;
    }
}
