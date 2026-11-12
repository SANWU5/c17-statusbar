// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Trace;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * On-demand cost accounting for installed hooks. Off by default: a hook then pays one volatile read.
 * {@code debug.c17.hooktrace=1} adds a trace section per hook and one named "orig" around the native method, so a
 * Perfetto trace shows the module's own share of a frame. {@code 2} only counts calls and time. The property is read
 * when the runtime status is queried, never from a hook, and the counters are bounded by the number of hooks.
 */
final class HookTrace {
    static final String PROPERTY = "debug.c17.hooktrace";
    static final int OFF = 0, SECTIONS = 1, COUNTERS = 2;
    private static final int MAX_SITES = 2048;
    private static volatile int mode;
    private static final Map<String, Site> SITES = new ConcurrentHashMap<>();
    private HookTrace() { }

    static final class Site {
        final String label;
        volatile long calls, nanos, nativeNanos;
        Site(String label) { this.label = label; }
    }

    static boolean on() { return mode != OFF; }

    /** One site per owner and method; the label is what a trace shows. */
    static Site site(String owner, Executable method) {
        String label = label(owner, method);
        Site known = SITES.get(label);
        if (known != null) return known;
        if (SITES.size() >= MAX_SITES) return new Site(label);
        Site created = new Site(label), raced = SITES.putIfAbsent(label, created);
        return raced == null ? created : raced;
    }

    static String label(String owner, Executable method) {
        String type = method.getDeclaringClass().getName();
        int dot = type.lastIndexOf('.');
        String name = owner + " " + (dot < 0 ? type : type.substring(dot + 1)) + "."
                + (method instanceof java.lang.reflect.Constructor ? "<init>" : method.getName());
        // Trace section names are capped at 127 characters by the platform.
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    static Object run(Site site, XposedInterface.Hooker hooker, XposedInterface.Chain chain) throws Throwable {
        boolean sections = mode == SECTIONS;
        Timed timed = new Timed(chain, sections);
        long start = System.nanoTime();
        if (sections) Trace.beginSection(site.label);
        try { return hooker.intercept(timed); }
        finally {
            if (sections) Trace.endSection();
            // Diagnostic counters: a lost update between threads only costs precision.
            site.calls++; site.nanos += System.nanoTime() - start; site.nativeNanos += timed.nativeNanos;
        }
    }

    /** Reads the switch. Returns whether the mode changed; a change starts a fresh count. */
    static boolean refresh() {
        int next = OFF;
        try {
            Object value = Class.forName("android.os.SystemProperties").getMethod("getInt", String.class, int.class)
                    .invoke(null, PROPERTY, OFF);
            if (value instanceof Integer) next = (Integer) value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { }
        if (next != SECTIONS && next != COUNTERS) next = OFF;
        if (next == mode) return false;
        reset(); mode = next; return true;
    }

    static void reset() { for (Site site : SITES.values()) { site.calls = 0; site.nanos = 0; site.nativeNanos = 0; } }

    /** The costliest sites first, by the module's own time (total minus the native method). */
    static JSONObject report(int limit) throws org.json.JSONException {
        List<Site> sites = new ArrayList<>(SITES.values());
        Collections.sort(sites, (a, b) -> Long.compare(b.nanos - b.nativeNanos, a.nanos - a.nativeNanos));
        JSONArray rows = new JSONArray(); long calls = 0, own = 0, total = 0; int used = 0;
        for (Site site : sites) {
            long count = site.calls; if (count == 0) continue;
            long all = site.nanos, self = all - site.nativeNanos;
            calls += count; own += self; total += all; used++;
            if (rows.length() < limit) rows.put(new JSONArray().put(site.label).put(count).put(self / 1000).put(all / 1000));
        }
        return new JSONObject().put("mode", mode).put("sites", SITES.size()).put("used", used).put("calls", calls)
                .put("own_us", own / 1000).put("total_us", total / 1000).put("top", rows);
    }

    /** Times every way of running the native method, and marks it in a trace. */
    private static final class Timed implements XposedInterface.Chain {
        private final XposedInterface.Chain chain;
        private final boolean sections;
        long nativeNanos;
        Timed(XposedInterface.Chain chain, boolean sections) { this.chain = chain; this.sections = sections; }
        @Override public Executable getExecutable() { return chain.getExecutable(); }
        @Override public Object getThisObject() { return chain.getThisObject(); }
        @Override public List<Object> getArgs() { return chain.getArgs(); }
        @Override public Object getArg(int index) { return chain.getArg(index); }
        private long enter() { if (sections) Trace.beginSection("orig"); return System.nanoTime(); }
        private void exit(long start) { nativeNanos += System.nanoTime() - start; if (sections) Trace.endSection(); }
        @Override public Object proceed() throws Throwable {
            long start = enter(); try { return chain.proceed(); } finally { exit(start); }
        }
        @Override public Object proceed(Object[] args) throws Throwable {
            long start = enter(); try { return chain.proceed(args); } finally { exit(start); }
        }
        @Override public Object proceedWith(Object receiver) throws Throwable {
            long start = enter(); try { return chain.proceedWith(receiver); } finally { exit(start); }
        }
        @Override public Object proceedWith(Object receiver, Object[] args) throws Throwable {
            long start = enter(); try { return chain.proceedWith(receiver, args); } finally { exit(start); }
        }
    }
}
