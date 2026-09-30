// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Opt-in, bounded diagnostics. Callers must supply fixed stage descriptions, never settings values. */
public final class ModuleDiagnostics {
    public static final String KEY_ENABLED = "diagnostics_enabled";
    public static final String METHOD_RECORD = "record_statusbar_diagnostic";
    public static final String EVENT_JSON = "event_json";
    public static final String PACKAGE_NAME = "dev.puitheme.iosstatusbar";
    static final int MAX_FILE_BYTES = 512 * 1024;
    static final int MAX_EVENT_BYTES = 16 * 1024;
    static final int MAX_PENDING = 32;
    static final long SNAPSHOT_LIFETIME_MS = 30 * 60 * 1000L;
    private static final Object STATE_LOCK = new Object();
    private static final Object FILE_LOCK = new Object();
    private static final Object SHARE_LOCK = new Object();
    private static final ArrayDeque<String> PENDING = new ArrayDeque<>();
    private static final RateGate RATE = new RateGate();
    private static final RateGate WRITE_RATE = new RateGate();
    private static Context boundContext;
    private static boolean configured, enabled, draining;
    private static long clearedBefore;
    private static final Pattern PRIVATE_WORDS = Pattern.compile(
            "(?i)\\b(?:pin|password|passwd|imei|imsi|ssid|bssid|serial|phone|carrier_text|clock_pattern|font_uri)\\b"
            + "|密码|手机号|用户文本|自定义文本值");
    private static final Pattern URLS = Pattern.compile("(?i)\\b(?:https?|content|file|intent)://[^\\s]+");
    private static final Pattern PATHS = Pattern.compile("(?i)[A-Z]:[\\\\/][^\\s]+|/(?:data|storage|sdcard|mnt)/[^\\s]+");
    private static final Pattern QUOTED = Pattern.compile("\"[^\"\\n]*\"|'[^'\\n]*'|“[^”]*”|‘[^’]*’");
    private static final Pattern IDENTIFIERS = Pattern.compile("(?<![A-Za-z0-9])[A-Fa-f0-9]{8,}(?![A-Za-z0-9])");
    private static final Pattern LONG_NUMBERS = Pattern.compile("[0-9]{4,}");
    private static final Pattern ASSIGNMENTS = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*\\s*[=:]\\s*[^\\s,;]+|[\\w.]+\\.(?:ttf|otf|ttc)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]{0,199}");
    private static final Pattern METHOD_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,99}|<init>|<clinit>");
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");

    public static void bind(Context context) {
        if (context == null) return;
        try {
            Context application = context.getApplicationContext();
            synchronized (STATE_LOCK) { boundContext = application == null ? context : application; }
            startDrain();
        } catch (Throwable ignored) { }
    }

    public static void configure(Context context, Bundle settings) { configure(settings); bind(context); }

    /** The first disabled configuration discards startup messages without opening a file. */
    public static void configure(Bundle settings) {
        boolean active = settings != null && Boolean.TRUE.equals(settings.get(KEY_ENABLED));
        synchronized (STATE_LOCK) {
            configured = true;
            enabled = active;
            if (!active) { PENDING.clear(); RATE.clear(); }
        }
        if (active) startDrain();
    }

    public static void info(String source, String message) { emit(source, message, null, false); }
    public static void error(String source, String message, Throwable error) { emit(source, message, error, true); }

    /** App-only convenience entry: reads the current opt-in before emitting a fixed stage message. */
    public static void app(Context context, String source, String message) {
        if (context == null || !PACKAGE_NAME.equals(context.getPackageName())) return;
        try {
            Bundle options = new Bundle();
            options.putBoolean(KEY_ENABLED, StatusBarSettings.bool(StatusBarSettings.preferences(context).getAll(), KEY_ENABLED));
            configure(context, options);
            info(source, message);
        } catch (Throwable ignored) { }
    }

    private static void emit(String source, String message, Throwable error, boolean failure) {
        try {
            synchronized (STATE_LOCK) { if (configured && !enabled) return; }
            JSONObject payload = event(source, message, error);
            payload.put("level", failure ? "error" : "info");
            String value = payload.toString();
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_EVENT_BYTES) return;
            synchronized (STATE_LOCK) {
                if (configured && !enabled) return;
                if (!RATE.accept((failure ? "error|" : "info|") + valueKey(source, message, error), System.currentTimeMillis())) return;
                if (PENDING.size() >= MAX_PENDING) PENDING.removeFirst();
                PENDING.addLast(value);
            }
            startDrain();
        } catch (Throwable ignored) { }
    }

    private static String valueKey(String source, String message, Throwable error) {
        return safeSource(source) + "|" + sanitizeMessage(message) + "|" + (error == null ? "info" : error.getClass().getName());
    }

    private static void startDrain() {
        synchronized (STATE_LOCK) {
            if (!configured || !enabled || boundContext == null || draining || PENDING.isEmpty()) return;
            draining = true;
        }
        try {
            Thread worker = new Thread(ModuleDiagnostics::drain, "C17-diagnostics");
            worker.setDaemon(true);
            worker.start();
        } catch (Throwable ignored) {
            synchronized (STATE_LOCK) { draining = false; }
        }
    }

    private static void drain() {
        try {
            while (true) {
                String value;
                Context context;
                synchronized (STATE_LOCK) {
                    if (!enabled || boundContext == null || PENDING.isEmpty()) return;
                    value = PENDING.removeFirst(); context = boundContext;
                }
                try {
                    Bundle request = new Bundle();
                    request.putString(EVENT_JSON, value);
                    request.putString("process", "com.android.systemui".equals(context.getPackageName()) ? "systemui" : "app");
                    Bundle response = context.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI), METHOD_RECORD, null, request);
                    if (response != null && Boolean.FALSE.equals(response.get("enabled"))) {
                        synchronized (STATE_LOCK) { enabled = false; PENDING.clear(); RATE.clear(); }
                        return;
                    }
                } catch (Throwable ignored) { /* Never recursively report a diagnostic transport failure. */ }
            }
        } finally {
            synchronized (STATE_LOCK) { draining = false; }
            startDrain();
        }
    }

    /** Provider append endpoint; the provider must first enforce own-app/SystemUI callers. */
    public static Bundle record(Context context, Bundle request) {
        Bundle result = new Bundle();
        boolean active = loggingEnabled(context);
        result.putBoolean("enabled", active);
        result.putBoolean("written", false);
        if (!active || request == null) return result;
        try {
            Object raw = request.get(EVENT_JSON);
            if (!(raw instanceof String) || ((String) raw).getBytes(StandardCharsets.UTF_8).length > MAX_EVENT_BYTES) return result;
            JSONObject received = new JSONObject((String) raw);
            JSONObject safe = validateEvent(received);
            safe.put("version", installedVersion(context));
            safe.put("sdk", Build.VERSION.SDK_INT);
            safe.put("os", safeVersion(Build.VERSION.RELEASE));
            safe.put("process", "systemui".equals(request.get("process")) ? "systemui" : "app");
            synchronized (FILE_LOCK) {
                // Recheck after waiting for another writer: disabled logging must reject queued events.
                if (!loggingEnabled(context)) { result.putBoolean("enabled", false); return result; }
                if (System.currentTimeMillis() < clearedBefore) clearedBefore = 0;
                if (eventMillis(safe.optString("timestamp")) <= clearedBefore) return result;
                if (!WRITE_RATE.accept(safe.optString("source") + "|" + safe.optString("level")
                        + "|" + safe.optString("message") + "|" + rootCauseClass(safe), System.currentTimeMillis())) return result;
                append(logDirectory(context), safe.toString());
            }
            result.putBoolean("written", true);
        } catch (Throwable ignored) { }
        return result;
    }

    private static boolean loggingEnabled(Context context) {
        try {
            return context != null && PACKAGE_NAME.equals(context.getPackageName())
                    && StatusBarSettings.bool(StatusBarSettings.preferences(context).getAll(), KEY_ENABLED);
        } catch (Throwable ignored) { return false; }
    }

    private static String rootCauseClass(JSONObject event) {
        JSONObject exception = event.optJSONObject("exception");
        JSONArray causes = exception == null ? null : exception.optJSONArray("causes");
        JSONObject last = causes == null ? null : causes.optJSONObject(causes.length() - 1);
        return last == null ? "" : last.optString("class", "unknown");
    }

    static JSONObject event(String source, String message, Throwable error) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("timestamp", timestamp());
        value.put("source", safeSource(source));
        value.put("level", error == null ? "info" : "error");
        value.put("message", sanitizeMessage(message));
        if (error != null) value.put("exception", serializeThrowable(error));
        return value;
    }

    /** Never reads exception messages, filenames or raw stacktrace headers. */
    public static JSONObject serializeThrowable(Throwable error) {
        JSONObject result = new JSONObject();
        JSONArray causes = new JSONArray();
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable current = error;
        boolean truncated = false;
        try {
            while (current != null && causes.length() < 4) {
                if (seen.put(current, Boolean.TRUE) != null) { truncated = true; break; }
                JSONObject cause = new JSONObject();
                cause.put("class", safeClass(current.getClass().getName()));
                JSONArray frames = new JSONArray();
                StackTraceElement[] trace = current.getStackTrace();
                if (trace != null && trace.length > 8) truncated = true;
                if (trace != null) for (int i = 0; i < Math.min(8, trace.length); i++) {
                    StackTraceElement item = trace[i];
                    if (item == null) continue;
                    JSONObject frame = new JSONObject();
                    frame.put("class", safeClass(item.getClassName()));
                    frame.put("method", safeMethod(item.getMethodName()));
                    frame.put("line", Math.max(-1, item.getLineNumber()));
                    frames.put(frame);
                }
                cause.put("stack", frames); causes.put(cause);
                current = current.getCause();
            }
            if (current != null) truncated = true;
            result.put("causes", causes); result.put("truncated", truncated);
        } catch (Throwable ignored) {
            try { result.put("causes", causes); result.put("truncated", true); } catch (JSONException impossible) { }
        }
        return result;
    }

    static JSONObject validateEvent(JSONObject input) throws JSONException {
        JSONObject safe = new JSONObject();
        String at = input.optString("timestamp", "");
        safe.put("timestamp", TIMESTAMP.matcher(at).matches() ? at : timestamp());
        safe.put("source", safeSource(input.optString("source", "app")));
        safe.put("level", "error".equals(input.optString("level")) ? "error" : "info");
        safe.put("message", sanitizeMessage(input.optString("message", "")));
        JSONObject exception = input.optJSONObject("exception");
        if (exception != null) {
            JSONObject cleaned = new JSONObject();
            JSONArray values = exception.optJSONArray("causes"), causes = new JSONArray();
            boolean truncated = Boolean.TRUE.equals(exception.opt("truncated")) || values != null && values.length() > 4;
            if (values != null) for (int i = 0; i < Math.min(4, values.length()); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                JSONObject cause = new JSONObject();
                cause.put("class", safeClass(value.optString("class", "unknown")));
                JSONArray rawFrames = value.optJSONArray("stack"), frames = new JSONArray();
                if (rawFrames != null && rawFrames.length() > 8) truncated = true;
                if (rawFrames != null) for (int j = 0; j < Math.min(8, rawFrames.length()); j++) {
                    JSONObject rawFrame = rawFrames.optJSONObject(j);
                    if (rawFrame == null) continue;
                    JSONObject frame = new JSONObject();
                    frame.put("class", safeClass(rawFrame.optString("class", "unknown")));
                    frame.put("method", safeMethod(rawFrame.optString("method", "unknown")));
                    Object line = rawFrame.opt("line");
                    frame.put("line", line instanceof Number ? Math.max(-1, ((Number) line).intValue()) : -1);
                    frames.put(frame);
                }
                cause.put("stack", frames); causes.put(cause);
            }
            cleaned.put("causes", causes);
            cleaned.put("truncated", truncated);
            safe.put("exception", cleaned);
        }
        return safe;
    }

    public static String sanitizeMessage(String value) {
        if (value == null) return "";
        String limited = value.substring(0, Math.min(value.length(), 2048));
        if (PRIVATE_WORDS.matcher(limited).find()) return "敏感内容已省略";
        limited = URLS.matcher(limited).replaceAll("[地址]");
        limited = PATHS.matcher(limited).replaceAll("[路径]");
        limited = QUOTED.matcher(limited).replaceAll("[内容]");
        limited = ASSIGNMENTS.matcher(limited).replaceAll("[参数]");
        limited = LONG_NUMBERS.matcher(limited).replaceAll("[数字]");
        limited = IDENTIFIERS.matcher(limited).replaceAll("[标识]");
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < limited.length() && safe.length() < 240; i++) {
            char valueAt = limited.charAt(i);
            if (!Character.isISOControl(valueAt)) safe.append(valueAt);
        }
        if (safe.length() > 0 && Character.isHighSurrogate(safe.charAt(safe.length() - 1))) safe.setLength(safe.length() - 1);
        return safe.toString();
    }

    private static String safeSource(String value) {
        if ("systemui".equals(value) || "hooks".equals(value)) return "hook";
        if ("settings".equals(value)) return "setting";
        if ("tiles".equals(value)) return "tile";
        if ("qs_style".equals(value)) return "qs_style";
        return "hook".equals(value) || "setting".equals(value) || "update".equals(value)
                || "tile".equals(value) || "app".equals(value) || "radio".equals(value)
                || "font".equals(value) || "export".equals(value) || "config".equals(value) ? value : "app";
    }
    private static String safeClass(String value) { return value != null && CLASS_NAME.matcher(value).matches() ? value : "unknown"; }
    private static String safeMethod(String value) { return value != null && METHOD_NAME.matcher(value).matches() ? value : "unknown"; }
    private static String safeVersion(String value) { return value != null && value.matches("[A-Za-z0-9.+_-]{1,64}") ? value : "unknown"; }
    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); return format.format(new Date());
    }
    private static long eventMillis(String timestamp) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));format.setLenient(false);
            return format.parse(timestamp).getTime();
        } catch (Exception ignored) { return System.currentTimeMillis(); }
    }
    private static String installedVersion(Context context) {
        try { return safeVersion(context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName); }
        catch (Exception ignored) { return "unknown"; }
    }
    private static File logDirectory(Context context) { return new File(context.createDeviceProtectedStorageContext().getFilesDir(), "diagnostics"); }

    /** One active 512 KiB JSONL file plus at most one rotated 512 KiB file. */
    static void append(File directory, String event) throws IOException {
        byte[] bytes = (event + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_EVENT_BYTES) throw new IOException("Diagnostic event is too large");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create diagnostic directory");
        File current = new File(directory, "c17.log"), previous = new File(directory, "c17.log.1");
        if (current.length() + bytes.length > MAX_FILE_BYTES) {
            if (previous.exists() && !previous.delete()) throw new IOException("Cannot rotate diagnostic log");
            if (current.exists() && !current.renameTo(previous)) throw new IOException("Cannot rotate diagnostic log");
        }
        try (FileOutputStream output = new FileOutputStream(current, true)) { output.write(bytes); }
    }

    static final class RateGate {
        private final LinkedHashMap<String, Long> recent = new LinkedHashMap<>();
        private long second = -1; private int count;
        boolean accept(String key, long now) {
            Long previous = recent.get(key);
            if (previous != null && now >= previous && now - previous < 5000L) return false;
            long current = now / 1000L;
            if (second != current) { second = current; count = 0; }
            if (count >= 40) return false;
            count++;
            recent.remove(key);recent.put(key, now);
            if (recent.size() > 128) recent.remove(recent.keySet().iterator().next());
            return true;
        }
        void clear() { recent.clear(); second = -1; count = 0; }
    }

    public static void share(Activity activity) {
        requireOwnApp(activity);
        Thread worker = new Thread(() -> {
            try {
                Uri uri = snapshotUri(activity);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain"); send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        send.setClipData(ClipData.newRawUri("C17 调试日志", uri));
                        activity.startActivity(Intent.createChooser(send, "分享调试日志"));
                    } catch (Exception unavailable) {
                        error("export", "日志分享界面启动失败", unavailable);
                        Toast.makeText(activity, "无法打开分享界面，请尝试下载日志", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception unavailable) {
                error("export", "日志分享快照准备失败", unavailable);
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                        Toast.makeText(activity, "暂时没有可分享的日志，请开启日志后复现问题", Toast.LENGTH_LONG).show();
                });
            }
        }, "C17-log-share");
        worker.setDaemon(true);worker.start();
    }

    public static void beginExport(Activity activity, int requestCode) {
        requireOwnApp(activity);
        if (!hasLog(activity)) { Toast.makeText(activity, "暂时没有日志，请开启日志后复现问题", Toast.LENGTH_LONG).show(); return; }
        try {
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);create.setType("text/plain");
            create.putExtra(Intent.EXTRA_TITLE, "C17-诊断日志.txt");
            activity.startActivityForResult(create, requestCode);
        } catch (Exception ignored) { Toast.makeText(activity, "无法打开文件保存界面", Toast.LENGTH_LONG).show(); }
    }

    public static void writeExport(Context context, Uri uri) throws IOException {
        requireOwnApp(context);
        if (uri == null || !"content".equals(uri.getScheme())) throw new IOException("Use a document destination");
        if (!hasLog(context)) throw new FileNotFoundException("No diagnostic log");
        byte[] snapshot = snapshotBytes(context);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Cannot open document destination");
            // External SAF providers may be slow; never hold the log lock during their I/O.
            output.write(snapshot);
        }
    }

    /** UI preview: at most the newest 64 KiB; sharing/exporting still includes the bounded full log. */
    public static String readText(Context context) {
        requireOwnApp(context);
        synchronized (FILE_LOCK) {
            try {
                File directory = logDirectory(context);
                byte[] current = readTail(new File(directory, "c17.log"), 64 * 1024);
                byte[] previous = readTail(new File(directory, "c17.log.1"), 64 * 1024 - current.length);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(previous.length + current.length);
                bytes.write(previous);bytes.write(current);
                if (bytes.size() == 0) return "暂无日志。请开启日志后复现问题。";
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException ignored) { return "无法读取日志，请稍后重试。"; }
        }
    }

    public static void clear(Context context) throws IOException {
        requireOwnApp(context);
        synchronized (STATE_LOCK) { PENDING.clear(); RATE.clear(); }
        synchronized (SHARE_LOCK) { synchronized (FILE_LOCK) {
            clearedBefore = System.currentTimeMillis();
            WRITE_RATE.clear();
            File directory = logDirectory(context);
            for (String name : new String[]{"c17.log", "c17.log.1"}) {
                File file = new File(directory, name);
                if (file.exists() && !file.delete()) throw new IOException("Cannot clear diagnostic log");
            }
            File[] snapshots = new File(context.getCacheDir(), "diagnostic-shares").listFiles();
            if (snapshots != null) for (File file : snapshots)
                if (file.isFile() && file.getName().matches("[0-9a-f]{32}\\.log")) removeSnapshot(context, file);
        } }
    }

    static byte[] readTail(File file, int limit) throws IOException {
        if (!file.isFile() || limit <= 0) return new byte[0];
        long skipped = Math.max(0, file.length() - limit);
        try (FileInputStream input = new FileInputStream(file);ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            long remainingSkip = skipped;
            while (remainingSkip > 0) {
                long amount = input.skip(remainingSkip);
                if (amount == 0) { if (input.read() == -1) break; amount = 1; }
                remainingSkip -= amount;
            }
            byte[] buffer = new byte[4096];int remaining = limit, count;
            while (remaining > 0 && (count = input.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                output.write(buffer, 0, count);remaining -= count;
            }
            byte[] bytes = output.toByteArray();
            if (skipped > 0) {
                int first = 0;
                while (first < bytes.length && bytes[first] != '\n') first++;
                bytes = first < bytes.length ? Arrays.copyOfRange(bytes, first + 1, bytes.length) : new byte[0];
            }
            return bytes;
        }
    }

    private static void requireOwnApp(Context context) {
        if (context == null || !PACKAGE_NAME.equals(context.getPackageName()))
            throw new SecurityException("Only the settings app can read or export diagnostics");
    }

    private static boolean hasLog(Context context) {
        File directory = logDirectory(context);
        return new File(directory, "c17.log").length() > 0 || new File(directory, "c17.log.1").length() > 0;
    }

    private static byte[] snapshotBytes(Context context) throws IOException {
        synchronized (FILE_LOCK) { return snapshotBytes(logDirectory(context), installedVersion(context)); }
    }

    static byte[] snapshotBytes(File directory, String version) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("C17 状态栏诊断日志\n版本：" + safeVersion(version)
                + "\n只记录固定阶段说明和异常类型／调用位置，不记录用户配置内容。\n\n").getBytes(StandardCharsets.UTF_8));
        byte[] buffer = new byte[4096];
        boolean content = false;
        for (String name : new String[]{"c17.log.1", "c17.log"}) {
            File file = new File(directory, name);
            if (!file.isFile()) continue;
            try (FileInputStream input = new FileInputStream(file)) {
                int remaining = MAX_FILE_BYTES, read;
                while (remaining > 0 && (read = input.read(buffer, 0, Math.min(remaining, buffer.length))) != -1) {
                    output.write(buffer, 0, read); remaining -= read;content = true;
                }
            }
        }
        if (!content) throw new FileNotFoundException("No diagnostic log");
        return output.toByteArray();
    }

    private static Uri snapshotUri(Context context) throws IOException {
        synchronized (SHARE_LOCK) { return prepareSnapshot(context); }
    }

    private static Uri prepareSnapshot(Context context) throws IOException {
        requireOwnApp(context);
        if (!hasLog(context)) throw new FileNotFoundException("No diagnostic log");
        File directory = new File(context.getCacheDir(), "diagnostic-shares");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create share snapshot");
        long now = System.currentTimeMillis();
        File[] old = directory.listFiles();
        if (old != null) for (File file : old) {
            if (file.isFile() && file.getName().matches("[0-9a-f]{32}\\.log") && now - file.lastModified() > SNAPSHOT_LIFETIME_MS) {
                removeSnapshot(context, file);
            }
        }
        File[] retained = directory.listFiles((folder, name) -> name.matches("[0-9a-f]{32}\\.log"));
        if (retained != null) {
            Arrays.sort(retained, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
            for (int i = 0; i < retained.length - 3; i++) removeSnapshot(context, retained[i]);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        File snapshot = new File(directory, token + ".log");
        byte[] bytes = snapshotBytes(context);
        try (FileOutputStream output = new FileOutputStream(snapshot)) { output.write(bytes); }
        return shareUri(token);
    }

    private static void removeSnapshot(Context context, File file) {
        String token = file.getName().substring(0, 32);
        context.revokeUriPermission(shareUri(token), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        file.delete();
    }

    private static Uri shareUri(String token) {
        return Uri.parse("content://" + DiagnosticFileProvider.AUTHORITY + "/snapshot/" + token + "/C17-diagnostics.txt");
    }

    static File sharedFile(Context context, Uri uri) throws FileNotFoundException {
        if (uri == null || !"content".equals(uri.getScheme()) || !DiagnosticFileProvider.AUTHORITY.equals(uri.getAuthority())
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPathSegments().size() != 3
                || !"snapshot".equals(uri.getPathSegments().get(0)) || !"C17-diagnostics.txt".equals(uri.getPathSegments().get(2)))
            throw new FileNotFoundException("Unknown diagnostic snapshot");
        String token = uri.getPathSegments().get(1);
        if (!token.matches("[0-9a-f]{32}")) throw new FileNotFoundException("Unknown diagnostic snapshot");
        File file = new File(new File(context.getCacheDir(), "diagnostic-shares"), token + ".log");
        long age = System.currentTimeMillis() - file.lastModified();
        if (!file.isFile() || age > SNAPSHOT_LIFETIME_MS || file.length() > MAX_FILE_BYTES * 2L + 4096)
            throw new FileNotFoundException("Diagnostic snapshot expired");
        return file;
    }

    private ModuleDiagnostics() {}
}
