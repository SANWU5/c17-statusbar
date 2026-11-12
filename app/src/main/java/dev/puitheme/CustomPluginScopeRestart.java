// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit maintenance action. Uses fresh official scope evidence, never force-stops apps. */
public final class CustomPluginScopeRestart {
    private CustomPluginScopeRestart() { }

    static final class Target {
        final String name, process;
        final int uid;
        Target(String name, String process, int uid) {
            if (!CustomPluginSupport.appPackage(name) || uid < 1000)
                throw new IllegalArgumentException("不支持的重启目标");
            this.name = name;
            this.process = safeProcess(process) ? process : name;
            this.uid = uid;
        }
    }

    /** Blocking: the plugin controller owns the worker and prevents repeated taps. */
    public static String restart(Context context, JSONObject descriptor) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Restart requires a worker");
        AppFrameworkStatus.ScopeSnapshot scopes = AppFrameworkStatus.queryScopes();
        if (!scopes.known) throw new IOException("无法确认框架作用域，请连接框架后重试");
        Plan plan = plan(context, descriptor, scopes.packages);
        List<Target> targets = plan.targets;
        if (targets.isEmpty()) return "没有可重启的已授权作用域，请先申请作用域";
        java.lang.Process child = null;
        Output output = new Output();
        try {
            child = new ProcessBuilder("su", "-c", command(targets)).redirectErrorStream(true).start();
            final InputStream stream = child.getInputStream();
            Thread reader = new Thread(() -> output.drain(stream), "C17-scope-restart-output");
            reader.setDaemon(true); reader.start();
            if (!child.waitFor(45, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                throw new IOException("Root 授权或重启等待超时，请检查各作用域状态");
            }
            reader.join(1000);
            return describe(child.exitValue(), output.text(), targets, plan.skipped);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (child != null) child.destroyForcibly();
            throw new IOException("重启等待已中断，请检查各作用域状态", interrupted);
        } finally {
            if (child != null) {
                try { child.getInputStream().close(); } catch (IOException ignored) { }
                try { child.getOutputStream().close(); } catch (IOException ignored) { }
                try { child.getErrorStream().close(); } catch (IOException ignored) { }
            }
        }
    }

    static final class Plan {
        final List<Target> targets; final int skipped;
        Plan(List<Target> targets, int skipped) { this.targets = targets; this.skipped = skipped; }
    }

    static Plan plan(Context context, JSONObject descriptor, Set<String> granted) throws IOException {
        List<Target> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        JSONArray declared = CustomPluginSupport.scopes(descriptor);
        if (declared.length() > 64) throw new IOException("插件声明的作用域过多");
        int skipped = 0;
        for (int i = 0; i < declared.length(); i++) {
            String name = declared.optString(i);
            if (!CustomPluginSupport.appPackage(name) || name.equals(context.getPackageName()))
                throw new IOException("插件包含不支持的重启目标");
            if (!seen.add(name)) continue;
            if (!granted.contains(name)) { skipped++; continue; }
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(name, 0);
                targets.add(new Target(name, info.processName, info.uid));
            } catch (PackageManager.NameNotFoundException absent) { skipped++; }
        }
        return new Plan(targets, skipped);
    }

    private static boolean safeProcess(String name) {
        return name != null && name.length() <= 240 &&
                name.matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+(?::[a-zA-Z0-9_]+)?") &&
                !name.equals(ModuleDiagnostics.PACKAGE_NAME) && !name.startsWith(ModuleDiagnostics.PACKAGE_NAME + ":");
    }

    static String command(List<Target> targets) {
        if (targets.isEmpty() || targets.size() > 64) throw new IllegalArgumentException("Invalid targets");
        // Match BOTH numeric UID and full cmdline. Prefix matching is only allowed after ':';
        // same-UID system_server, similarly named apps, and other Android users cannot match.
        StringBuilder script = new StringBuilder("[ \"$(id -u)\" = 0 ] || { echo C17_ROOT_REQUIRED; exit 21; }; ");
        script.append("find_scope() { ps -A -o UID,PID,NAME | while read owner procid name; do ")
                .append("[ \"$owner\" = \"$1\" ] || continue; ")
                .append("case \"$name\" in \"$2\"|\"$2\":*|\"$3\") printf '%s ' \"$procid\";; esac; done; }; ");
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            script.append("old").append(i).append("=$(find_scope ").append(target.uid).append(" '")
                    .append(target.name).append("' '").append(target.process).append("'); ");
        }
        // Snapshot every target before signalling any: dependency restarts must not be killed twice.
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            script.append("for candidate in $old").append(i).append("; do ")
                    .append("current=$(find_scope ").append(target.uid).append(" '").append(target.name)
                    .append("' '").append(target.process).append("'); ")
                    .append("case \" $current \" in *\" $candidate \"*) kill -TERM \"$candidate\" 2>/dev/null;; esac; done; ");
        }
        script.append("sleep 3; ");
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            script.append("current=$(find_scope ").append(target.uid).append(" '").append(target.name)
                    .append("' '").append(target.process).append("'); ")
                    .append("state=ENDED; [ -n \"$current\" ] && state=RESTARTED; ")
                    .append("[ -z \"$old").append(i).append("\" ] && state=IDLE; ")
                    .append("for candidate in $old").append(i).append("; do case \" $current \" in *\" $candidate \"*) state=FAILED;; esac; done; ")
                    .append("echo C17_SCOPE:").append(target.name).append(":$state; ");
        }
        return script.append("echo C17_SCOPE_DONE").toString();
    }

    static String describe(int exit, String output, List<Target> targets, int skipped) throws IOException {
        if (exit != 0 || !output.contains("C17_SCOPE_DONE"))
            throw new IOException("未完成作用域重启，请检查 Root 授权后重试");
        int restarted = 0, ended = 0, idle = 0, failed = 0;
        for (Target target : targets) {
            String prefix = "C17_SCOPE:" + target.name + ":";
            String state = "";
            for (String line : output.split("\\R")) if (line.startsWith(prefix)) state = line.substring(prefix.length());
            switch (state) {
                case "RESTARTED": restarted++; break;
                case "ENDED": ended++; break;
                case "IDLE": idle++; break;
                default: failed++; break;
            }
        }
        String message = "已重启 " + restarted + " 个作用域";
        if (ended > 0) message += "；已结束 " + ended + " 个，下次打开时载入";
        if (idle > 0) message += "；" + idle + " 个未运行";
        if (skipped > 0) message += "；跳过 " + skipped + " 个未授权或未安装目标";
        if (failed > 0) message += "；" + failed + " 个未确认结束，请重试";
        return message;
    }

    private static final class Output {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        void drain(InputStream stream) {
            byte[] buffer = new byte[1024];
            try {
                int count;
                while ((count = stream.read(buffer)) != -1) synchronized (this) {
                    if (bytes.size() < 32768) bytes.write(buffer, 0, Math.min(count, 32768 - bytes.size()));
                }
            } catch (IOException closed) { }
        }
        synchronized String text() { return new String(bytes.toByteArray(), StandardCharsets.UTF_8); }
    }
}
