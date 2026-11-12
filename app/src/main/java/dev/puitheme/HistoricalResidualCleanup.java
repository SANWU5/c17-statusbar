// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Explicit, destructive maintenance. Never called by startup, migration or diagnostics. */
final class HistoricalResidualCleanup {
    private HistoricalResidualCleanup() { }
    static String clear(Context context) throws Exception {
        if (!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName())) throw new SecurityException("Own app required");
        int user = android.os.Process.myUid() / 100000;
        String root = "/data/user_de/" + user + "/com.android.systemui/files/c17_plugins/owners";
        // Inventory must remain readable before plugins are disabled/reset. Bound
        // all root output, validate every record; never source a manifest as shell.
        String inventory = run(inventoryCommand(root), 30, 262144);
        Set<String> names = preferenceNames(inventory);
        MaintenanceReset.Result reset = MaintenanceReset.reset(context);
        if (!reset.success || !reset.frameworkSynced) return "清理尚未继续：" + reset.message + "。需连接框架并确认默认配置已同步，避免旧配置重新恢复。";
        File script = File.createTempFile("c17-history-cleanup-", ".sh", context.getCacheDir());
        try {
            try (java.io.InputStream input = context.getAssets().open("history-cleanup.sh")) {
                Files.copy(input, script.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            StringBuilder command = new StringBuilder("sh ").append(RootPerformanceModule.quote(script.getAbsolutePath())).append(' ').append(user);
            for (String name : names) command.append(' ').append(RootPerformanceModule.quote(name));
            run(command.toString(), 600, 65536);
            return "清理已完成，请重新打开应用；附属模块保持停用。";
        } finally { script.delete(); }
    }
    /** Read-only inventory: fail closed on oversized or unreadable records. */
    static String inventoryCommand(String root) {
        return "d=" + RootPerformanceModule.quote(root) + "; "
                + "[ ! -L \"$d\" ] || exit 20; if [ -d \"$d\" ]; then for f in \"$d\"/*.json; do "
                + "[ -e \"$f\" ] || continue; [ -f \"$f\" ] && [ ! -L \"$f\" ] || exit 20; "
                + "bytes=$(wc -c < \"$f\") || exit 20; [ \"$bytes\" -le 32768 ] || exit 20; "
                + "head -c 32769 \"$f\" || exit 20; printf '\\n'; done; fi";
    }
    static Set<String> preferenceNames(String inventory) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        JSONArray records = new JSONArray();
        if (inventory.isEmpty()) return names;
        String[] lines = inventory.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            // The shell adds one terminating separator. A blank record before
            // it is invalid, not evidence that an owner has no preferences.
            if (line.isEmpty() && index == lines.length - 1) continue;
            if (line.trim().isEmpty()) throw new IllegalArgumentException("Empty plugin inventory record");
            if (line.getBytes(StandardCharsets.UTF_8).length > 32768)
                throw new IllegalArgumentException("Plugin inventory too large");
            JSONTokener reader = new JSONTokener(line);
            Object value = reader.nextValue();
            if (!(value instanceof JSONObject) || reader.nextClean() != 0)
                throw new IllegalArgumentException("Invalid plugin inventory record");
            JSONObject record = (JSONObject) value;
            records.put(record);
            JSONArray declared = record.getJSONArray("preferences");
            for (int i = 0; i < declared.length(); i++) names.add(declared.getString(i));
            if (names.size() > 256) throw new IllegalArgumentException("Too many plugin preferences");
        }
        // Validate identities and ownership across the full inventory, not just
        // independently per line: two plugins must not claim the same file.
        CustomPluginStorage.historicalInventory(records);
        return names;
    }
    private static String run(String command, int seconds, int bound) throws Exception {
        java.lang.Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        java.util.concurrent.atomic.AtomicBoolean overflow = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean readFailed = new java.util.concurrent.atomic.AtomicBoolean();
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[2048];
            try (java.io.InputStream input = process.getInputStream()) {
                for (int count; (count = input.read(buffer)) != -1;) {
                    synchronized (output) { if (output.size() + count > bound) overflow.set(true); else output.write(buffer, 0, count); }
                }
            } catch (java.io.IOException failure) { readFailed.set(true); }
        }, "C17HistoryCleanupOutput"); reader.setDaemon(true); reader.start();
        if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
            process.destroy(); throw new java.io.IOException("清理等待超时，部分步骤可能已完成，请先重启并查看附属模块记录");
        }
        reader.join(1000);
        if (reader.isAlive() || readFailed.get())
            throw new java.io.IOException("清理记录读取不完整，已停止后续操作，部分步骤可能已完成");
        String text; synchronized (output) { text = new String(output.toByteArray(), StandardCharsets.UTF_8); }
        if (overflow.get()) throw new java.io.IOException("清理记录过大，已停止后续操作");
        if (process.exitValue() != 0) throw new java.io.IOException("清理未完成，部分设置可能已恢复默认：" + text.substring(Math.max(0, text.length() - 1600)));
        return text;
    }
}
