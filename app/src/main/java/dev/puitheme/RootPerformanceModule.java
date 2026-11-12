// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The optional root-manager module reuses valid ART code and only pre-compiles SystemUI and the
 * control-centre plugin when needed. It cannot replace code loaded by a live process or remove hook
 * overhead. A bounded native resource service is independent of one-shot ART checks; no mount is required.
 * It replaces the former cache-backup module, which it removes on installation.
 */
public final class RootPerformanceModule {
    public static final String ID = "c17_performance";
    public static final String DISPLAY_NAME = "Status C17 附属模块";
    public static final long VERSION_CODE = 14L;
    static final String LEGACY_ID = "c17_resident_cache";
    private static final String[] ASSETS = {"module.prop", "skip_mount", "customize.sh", "service.sh", "action.sh", "uninstall.sh", "common.sh", "webui.sh",
            "cache-service.sh", "bin/c17cached-arm64-v8a", "bin/c17cached-armeabi-v7a", "bin/c17cached-x86_64", "bin/c17cached-x86",
            "webroot/index.html", "webroot/style.css", "webroot/app.js", "post-fs-data.sh", "boot-rescue.sh",
            "bin/c17bootrescue-arm64-v8a", "bin/c17bootrescue-armeabi-v7a", "bin/c17bootrescue-x86_64", "bin/c17bootrescue-x86"};
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Action activeAction;
    private static volatile Result latest;
    private static volatile String pendingSchedule;
    private static final CopyOnWriteArrayList<Callback> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final long TIMEOUT_SECONDS = 45L;
    public enum Action { CHECK, INSTALL, REMOVE, ENABLE, DISABLE, RUN, AUTOMATIC, RESCUE_ENABLE, RESCUE_DISABLE }
    public interface Callback { void completed(Result result); }
    public interface Subscription extends AutoCloseable { @Override void close(); }
    public static final class Request {
        private volatile boolean detached;
        /** Detach an Activity, never cancel an operation already authorized and submitted. */
        public void cancel() { detached = true; }
    }
    public static final class Snapshot {
        public final boolean root, installed, pending, disabled, remove, legacy, currentBoot, outdated;
        public final String manager, lastState, error, moduleVersion, trigger, verification, priority;
        public final long time, compiled, packages, moduleVersionCode, recordVersionCode, reused, verified, deferred, ready;
        public final String bootUUID, recordBoot, busyState, activeJobState, diagnostic, scheduleKind, scheduleState;
        public final long idleRemaining, scheduleNextAfter;
        public final boolean recordPresent, taskActive, taskBlocked, lockAvailable;
        /** Cumulative benefit ledger and the last SystemUI runtime sample; all zero when the module has not recorded them yet. */
        public final long ledgerSince, ledgerCompiles, ledgerWallMs, ledgerCpuMs, ledgerSizeKb, ledgerReuses, ledgerAvoidedMs;
        public final long runtimeSampled, runtimeJitMs, runtimeProcSeconds;
        public final boolean runtimeAot;
        public final String executionLog, failureLog;
        public final boolean cacheRunning;
        public final boolean cacheProtected, cacheCoreProtected;
        public final long cacheBytes, cacheLimit, cacheHits, cacheMisses, cacheDiskHits, cacheEntries;
        public final boolean rescueAvailable, rescueEnabled;
        public final String rescueState, rescueError;
        public final long rescueTime;
        public String rescueMessage() {
            if (!rescueAvailable) return "更新附属模块后可用";
            String state;
            switch (rescueState) {
                case "watching": state="正在监听按键"; break;
                case "triggered": state="正在重置系统界面"; break;
                case "waiting_unlock": state="等待首次解锁后完成清理"; break;
                case "cleared": state="重置完成"; break;
                case "failed": state="恢复未完成"; break;
                case "cancelled": state="已取消"; break;
                default: state="尚无记录";
            }
            if (!"none".equals(rescueError)) state += "：" + rescueErrorMessage(rescueError);
            if (remove) return "模块待卸载 · " + state;
            if (disabled) return "模块已停用 · " + state;
            return (rescueEnabled ? "已开启 · " : "已关闭 · ") + state;
        }
        public String cacheMessage() {
            if (!cacheRunning) return "常驻缓存尚未连接，资源读取使用本地回退。";
            return "常驻缓存运行中：" + cacheEntries + " 项，" + (cacheBytes / 1048576L) + "/" + (cacheLimit / 1048576L)
                    + " MB；内存命中 " + Math.max(0L,cacheHits-cacheDiskHits) + " 次、磁盘恢复 " + cacheDiskHits + " 次。"
                    + (cacheProtected ? "已提高缓存保留优先级。" : "使用系统默认回收优先级。")
                    + (cacheCoreProtected ? "内核已启用 16 MB 核心缓存保护。" : "当前未启用内核核心缓存保护。");
        }
        /** Activation confirms this installed script ran this boot; optimization can still wait or fail. */
        public String homeStatus() {
            if (!root) return "待查询";
            if (!installed && !pending) return "未安装";
            boolean active = installed && !pending && !disabled && !remove && !outdated && currentBoot
                    && recordVersionCode == moduleVersionCode && recordVersionCode >= VERSION_CODE
                    && ("boot".equals(trigger) || "auto".equals(trigger) || "run".equals(trigger))
                    && !"unknown".equals(lastState);
            if (active && "running".equals(lastState)) return "优化中";
            return active ? "已激活" : "已安装";
        }
        public String verificationMessage() {
            if (recordVersionCode < VERSION_CODE) return "旧记录未核对当前系统编译状态";
            if (!currentBoot) return "历史核对记录：" + verified + "/" + packages + " 个组件，本次开机尚未确认";
            if (recordVersionCode != moduleVersionCode) return "旧模块的核对记录，新版本尚未确认";
            if ("verified".equals(verification)) return "已核对系统当前编译状态：" + verified + "/" + packages + " 个组件";
            if ("partial".equals(verification)) return "已核对 " + verified + "/" + packages + " 个组件，其余尚未确认";
            return "系统当前编译状态尚未确认";
        }
        public String errorMessage() {
            switch (error) {
                case "none": return "";
                case "compile_failed": return "系统没有完成预编译（存储不足或系统正忙），下次开机会重试";
                case "no_package": return "没有找到系统界面";
                case "boot_timeout": return "等待系统启动完成超时，下次开机会重试";
                case "boot_incomplete": return "系统启动尚未完成，自动任务已延期";
                case "busy": return "上一次预编译仍在进行";
                case "lock_unavailable": return "系统缺少可靠的任务互斥能力，优化未启动，原运行记录已保留";
                case "lock_incomplete": return "本次开机存在无法确认归属的旧锁，重启后可恢复";
                case "device_interactive": return "自动优化等待息屏或连续无操作 10 分钟，当前条件未满足";
                case "idle_unknown": return "无法可靠确认无操作时长，等待息屏后再优化";
                case "device_hot": return "该次优化温度条件未满足；当前自动规则为电池温度低于 55℃（55℃及以上延期），手动不受温度限制";
                case "battery_low": return "该次优化电量条件未满足；当前自动规则为电量高于 15%（15%及以下延期），充电与否不影响，手动不受电量限制";
                case "power_unknown": return "该次优化无法确认电池电量；当前仅自动优化要求电量高于 15%，手动不检查电量";
                case "thermal_unknown": return "该次优化无法确认温度；当前自动只检查电池温度，不检查 Thermal Service，手动不检查温度";
                case "screen_unknown": return "自动优化暂时无法确认息屏状态，已延期";
                case "timeout_unavailable": return "系统不支持可靠的编译超时控制，本次未开始执行";
                case "art_cancel_unavailable": return "系统不支持可靠取消编译任务，本次未开始执行";
                case "art_unverified": return "无法确认当前编译结果是否有效，请刷新运行记录";
                case "compile_timeout": return "编译等待超时，任务已请求取消，下次开机会重试";
                case "compile_cancel_failed": return "未能确认上次系统编译任务已结束，本次开机暂停新任务；重启后重新检查";
                case "interrupted": return "操作已中断，已尝试取消系统任务，请查看运行记录";
                case "restore_failed": return "恢复系统编译方式失败，失败记录已保留，请查看运行记录";
                case "restore_timeout": return "恢复等待超时，失败记录已保留，请查看运行记录";
                case "restore_cancel_failed": return "未能确认系统恢复任务已取消，失败记录已保留";
                case "fingerprint_write_failed": case "status_write_failed": return "无法写入运行记录";
                case "path_link_rejected": case "data_path_rejected": return "模块目录存在异常链接，操作已停止";
                case "migration_failed": return "旧模块记录迁移未完成，已有记录已保留";
                case "module_id_rejected": return "模块身份不符，操作已停止";
                case "module_path_rejected": return "模块安装位置不符，操作已停止";
                case "module_version_rejected": return "模块版本记录异常，操作已停止";
                case "mode_rejected": return "运行模式不受支持，操作已停止";
                case "root_required": return "未获得 Root 权限，操作未执行";
                case "module_inactive": return "附属模块已停用或待卸载，优化未执行";
                case "data_create_failed": return "无法创建模块运行记录目录";
                case "unexpected_directory": return "卸载发现异常目录，记录已保留，请查看运行记录";
                case "uninstall_failed": return "卸载清理未完成，记录已保留，请查看运行记录";
                case "uninstall_incomplete": return "卸载仍有残留记录，请查看运行记录";
                case "cache_cleanup_failed": return "缓存服务尚未安全停止或清理，记录已保留，请重试";
                default: return "部分步骤未完成，请查看运行记录后重试";
            }
        }
        Snapshot(Map<String, String> fields, boolean root) {
            this.root = root;
            String found = fields.get("manager");
            manager = "KernelSU".equals(found) || "Magisk".equals(found) || "APatch".equals(found) ? found : "未识别";
            installed = "1".equals(fields.get("installed")); pending = "1".equals(fields.get("pending"));
            disabled = "1".equals(fields.get("disabled")); remove = "1".equals(fields.get("remove"));
            legacy = "1".equals(fields.get("legacy"));
            String state = fields.get("last_state");
            lastState = "ok".equals(state) || "current".equals(state) || "waiting".equals(state) || "running".equals(state)
                    || "deferred".equals(state) || "failed".equals(state) ? state : "unknown";
            String problem = fields.get("last_error");
            error = problem != null && problem.matches("[a-z0-9_]{1,64}") ? problem : "none";
            time = number(fields.get("last_time"), Long.MAX_VALUE / 1000L);
            compiled = number(fields.get("last_compiled"), 64L); packages = number(fields.get("last_packages"), 64L);
            moduleVersionCode = number(fields.get("module_version_code"), 99999999L);
            recordVersionCode = number(fields.get("last_module_version_code"), 99999999L);
            reused = number(fields.get("last_reused"), packages);
            verified = number(fields.get("last_verified"), packages);
            deferred = number(fields.get("last_deferred"), packages);
            ready = Math.min(packages, compiled + (recordVersionCode >= VERSION_CODE ? reused : 0L));
            String proof = fields.get("last_verification");
            verification = "verified".equals(proof) && packages > 0L && verified == packages ? "verified"
                    : ("verified".equals(proof) || "partial".equals(proof)) && verified > 0L ? "partial" : "unverified";
            String scheduling = fields.get("last_priority");
            priority = "background".equals(scheduling) || "compatibility".equals(scheduling) ? scheduling : "unknown";
            String version = fields.get("module_version");
            moduleVersion = version != null && version.matches("[a-zA-Z0-9._+-]{1,64}") ? version : "未知";
            outdated = (installed || pending) && moduleVersionCode < VERSION_CODE;
            bootUUID = uuid(fields.get("boot")); recordBoot = uuid(fields.get("last_boot"));
            currentBoot = !bootUUID.isEmpty() && bootUUID.equals(recordBoot);
            String mode = fields.get("last_mode");
            trigger = "boot".equals(mode) || "auto".equals(mode) || "run".equals(mode) || "uninstall".equals(mode) ? mode : "unknown";
            busyState = taskFlag(fields.get("busy")); activeJobState = taskFlag(fields.get("active_job"));
            recordPresent = "1".equals(fields.get("record_present"));
            lockAvailable = !fields.containsKey("lock_available") || "1".equals(fields.get("lock_available"));
            taskActive = "1".equals(busyState);
            taskBlocked = !lockAvailable || "unknown".equals(busyState) || "unknown".equals(activeJobState)
                    || ("1".equals(activeJobState) && !taskActive)
                    || (currentBoot && !"0".equals(activeJobState) && ("compile_cancel_failed".equals(error) || "restore_cancel_failed".equals(error)));
            idleRemaining = number(fields.get("last_idle_remaining"), 600L);
            String detail = fields.get("last_diagnostic");
            diagnostic = "uuid_missing".equals(detail) || "cancel_pending".equals(detail)
                    || "cancel_unrecognized".equals(detail) || "final_unrecognized".equals(detail)
                    || "active_unknown".equals(detail) || "client_failed".equals(detail) ? detail : "none";
            scheduleKind = "on_demand".equals(fields.get("schedule_kind")) ? "on_demand" : "unknown";
            String planned = fields.get("schedule_state");
            scheduleState = scheduleStateValue(planned) ? planned : "unknown";
            scheduleNextAfter = number(fields.get("schedule_next_after"), 9999999999L);
            ledgerSince = number(fields.get("total_since"), 9999999999L); ledgerCompiles = number(fields.get("total_compiles"), 9999999L);
            ledgerWallMs = number(fields.get("total_wall_ms"), 99999999999999L); ledgerCpuMs = number(fields.get("total_cpu_ms"), 99999999999999L);
            ledgerSizeKb = number(fields.get("total_size_kb"), 99999999999L); ledgerReuses = number(fields.get("total_reuses"), 9999999L);
            ledgerAvoidedMs = number(fields.get("total_avoided_ms"), 99999999999999L);
            runtimeSampled = number(fields.get("runtime_sampled"), 9999999999L); runtimeJitMs = number(fields.get("runtime_jit_ms"), 99999999999999L);
            runtimeProcSeconds = number(fields.get("runtime_proc_s"), 99999999L); runtimeAot = "1".equals(fields.get("runtime_aot"));
            executionLog = decodeLog(fields.get("trace_hex"), 2048);
            failureLog = decodeLog(fields.get("failure_hex"), 512);
            cacheRunning = "1".equals(fields.get("cache_running")) && "1".equals(fields.get("cache_protocol")) && !disabled && !remove;
            cacheProtected = cacheRunning && "-600".equals(fields.get("cache_oom_adj"));
            cacheCoreProtected = cacheRunning && "16777216".equals(fields.get("cache_memory_low"));
            cacheBytes = number(fields.get("cache_bytes"), 67108864L); cacheLimit = number(fields.get("cache_limit"), 67108864L);
            cacheEntries = number(fields.get("cache_entries"),256L); cacheHits=number(fields.get("cache_hits"),Long.MAX_VALUE);
            cacheMisses=number(fields.get("cache_misses"),Long.MAX_VALUE); cacheDiskHits=number(fields.get("cache_disk_hits"),cacheHits);
            String rescue=fields.get("rescue_state"),rescueProblem=fields.get("rescue_error"),rescueFlag=fields.get("rescue_enabled"),rescueStamp=fields.get("rescue_time");
            rescueAvailable = moduleVersionCode >= 12 && ("0".equals(rescueFlag)||"1".equals(rescueFlag))
                    && rescueStateValue(rescue) && rescueErrorValue(rescueProblem)
                    && rescueStamp != null && rescueStamp.matches("[0-9]{1,10}") && number(rescueStamp,9999999999L) >= 0;
            rescueEnabled=rescueAvailable&&"1".equals(rescueFlag);
            rescueState=rescueAvailable?rescue:"unknown";rescueError=rescueAvailable?rescueProblem:"unsupported";
            rescueTime=rescueAvailable?number(rescueStamp,9999999999L):0;
        }
        public String diagnosticMessage() {
            switch (diagnostic) {
                case "uuid_missing": return "系统没有提供可跟踪的任务编号";
                case "cancel_pending": return "系统收到取消请求，但尚未确认任务退出";
                case "cancel_unrecognized": return "系统取消返回不符合已核验协议";
                case "final_unrecognized": return "系统最终结果缺失或无法识别";
                case "active_unknown": return "存在未确认任务，已暂停新任务";
                case "client_failed": return "系统包管理器暂时没有应答，已自动重试，未留下需要等待的任务";
                default: return "";
            }
        }
        /** Same fixed, framed data contract as the root-manager WebUI. All values are already validated. */
        public String webStatus() {
            StringBuilder out = new StringBuilder("C17_WEB:begin\n");
            webField(out, "installed", installed ? "1" : "0"); webField(out, "pending", pending ? "1" : "0");
            webField(out, "disabled", disabled ? "1" : "0"); webField(out, "remove", remove ? "1" : "0");
            webField(out, "boot", bootUUID); webField(out, "busy", busyState); webField(out, "active_job", activeJobState);
            webField(out, "lock_available", lockAvailable ? "1" : "0");
            webField(out, "current_boot", currentBoot ? "1" : "0"); webField(out, "record_present", recordPresent ? "1" : "0");
            webField(out, "module_version", moduleVersion); webField(out, "module_version_code", moduleVersionCode);
            webField(out, "last_state", lastState); webField(out, "last_time", time); webField(out, "last_compiled", compiled);
            webField(out, "last_packages", packages); webField(out, "last_error", error); webField(out, "last_boot", recordBoot);
            webField(out, "last_mode", trigger); webField(out, "last_module_version_code", recordVersionCode);
            webField(out, "last_reused", reused); webField(out, "last_verified", verified); webField(out, "last_deferred", deferred);
            webField(out, "last_verification", verification); webField(out, "last_priority", priority);
            webField(out, "last_idle_remaining", idleRemaining); webField(out, "last_diagnostic", diagnostic);
            webField(out, "schedule_kind", scheduleKind); webField(out, "schedule_state", scheduleState);
            webField(out, "schedule_next_after", scheduleNextAfter);
            webField(out, "total_since", ledgerSince); webField(out, "total_compiles", ledgerCompiles); webField(out, "total_wall_ms", ledgerWallMs);
            webField(out, "total_cpu_ms", ledgerCpuMs); webField(out, "total_size_kb", ledgerSizeKb); webField(out, "total_reuses", ledgerReuses);
            webField(out, "total_avoided_ms", ledgerAvoidedMs); webField(out, "runtime_sampled", runtimeSampled);
            webField(out, "runtime_jit_ms", runtimeJitMs); webField(out, "runtime_proc_s", runtimeProcSeconds); webField(out, "runtime_aot", runtimeAot ? "1" : "0");
            webField(out, "trace_hex", encodeLog(executionLog)); webField(out, "failure_hex", encodeLog(failureLog));
            webField(out,"cache_running",cacheRunning?1:0); webField(out,"cache_protocol",1);
            webField(out,"cache_bytes",cacheBytes);webField(out,"cache_limit",cacheLimit);webField(out,"cache_entries",cacheEntries);
            webField(out,"cache_hits",cacheHits);webField(out,"cache_misses",cacheMisses);webField(out,"cache_disk_hits",cacheDiskHits);
            webField(out,"cache_oom_adj",cacheProtected ? "-600" : "0");
            webField(out,"cache_memory_low",cacheCoreProtected ? "16777216" : "unsupported");
            if (rescueAvailable) {
                webField(out,"rescue_enabled",rescueEnabled?1:0);webField(out,"rescue_state",rescueState);
                webField(out,"rescue_error",rescueError);webField(out,"rescue_time",rescueTime);
            }
            return out.append("C17_WEB:end\n").toString();
        }
    }
    public static final class Result {
        public final boolean success;
        public final String message;
        public final Snapshot snapshot;
        Result(boolean success, String message, Snapshot snapshot) { this.success = success; this.message = message; this.snapshot = snapshot; }
    }
    public static Result latest() { return latest; }
    public static boolean busy() { return BUSY.get(); }
    /** A recreated Activity can recover an accepted task without treating status reads as writes. */
    public static Action activeAction() { return BUSY.get() ? activeAction : null; }
    /** Pure home presentation policy; a busy install/remove is never mistaken for compilation. */
    public static String homeStatus(Snapshot snapshot, boolean writeBusy, boolean error, Action action) {
        if (writeBusy && action != Action.RUN && action != Action.AUTOMATIC) return "处理中";
        if (error || snapshot == null || !snapshot.root) return "待查询";
        if (snapshot.remove) return "待卸载";
        if (snapshot.disabled) return "已停用";
        if (snapshot.pending) return "待重启";
        if (snapshot.outdated) return "待更新";
        if (writeBusy && (action == Action.RUN || action == Action.AUTOMATIC) && snapshot.installed) return "优化中";
        return snapshot.homeStatus();
    }
    public static Subscription observe(Callback callback) {
        OBSERVERS.add(callback);
        if (latest != null) new Handler(Looper.getMainLooper()).post(() -> {
            Result value = latest; if (value != null && OBSERVERS.contains(callback)) callback.completed(value);
        });
        return () -> OBSERVERS.remove(callback);
    }
    private RootPerformanceModule() { }

    /** The confirmation button is the authorization boundary. Use application context so a finished result survives Activity changes. */
    public static Request submit(Context host, Action action, Callback callback) {
        Request request = new Request();
        Context application = host.getApplicationContext();
        final Context context = application == null ? host : application;
        Handler main = new Handler(Looper.getMainLooper());
        if (!BUSY.compareAndSet(false, true)) {
            main.post(() -> { if (!request.detached) callback.completed(new Result(false, "已有模块操作正在执行，请稍后刷新状态。", latest == null ? null : latest.snapshot)); });
            return request;
        }
        activeAction = action;
        WORK.execute(() -> {
            Result result;
            File zip = null;
            try {
                if (action == Action.INSTALL) zip = packageAssets(context);
                result = run(command(action, zip == null ? null : zip.getCanonicalPath()), action);
            } catch (IOException | RuntimeException failure) {
                result = new Result(false, "无法完成模块操作，请检查 Root 授权后刷新真实状态。", latest == null ? null : latest.snapshot);
            } finally {
                if (zip != null && !zip.delete()) zip.deleteOnExit();
            }
            latest = result; activeAction = null; BUSY.set(false);
            final Result completed = result;
            main.post(() -> {
                if (!request.detached) callback.completed(completed);
                for (Callback observer : OBSERVERS) observer.completed(completed);
            });
        });
        return request;
    }

    static File packageAssets(Context context) throws IOException {
        File zip = File.createTempFile("c17-performance-module-", ".zip", context.getCacheDir());
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String name : ASSETS) {
                ZipEntry entry = new ZipEntry(name); entry.setTime(0L); output.putNextEntry(entry);
                try (InputStream input = context.getAssets().open("performance-module/" + name)) {
                    byte[] buffer = new byte[4096]; int total = 0;
                    for (int read; (read = input.read(buffer)) != -1;) {
                        total += read; if (total > (name.startsWith("bin/") ? 4194304 : 262144)) throw new IOException("module asset too large");
                        output.write(buffer, 0, read);
                    }
                }
                output.closeEntry();
            }
        } catch (IOException failure) { zip.delete(); throw failure; }
        return zip;
    }

    private static final String HEADER =
            "[ \"$(id -u)\" = 0 ] || { echo C17_ROOT_REQUIRED; exit 21; };\n"
            + "m=/data/adb/modules/c17_performance; u=/data/adb/modules_update/c17_performance; b=/data/adb/c17-performance; "
            + "lm=/data/adb/modules/c17_resident_cache; manager=unknown; cli=;\n"
            + "if [ -x /data/adb/ksu/bin/ksud ]; then manager=KernelSU; cli=/data/adb/ksu/bin/ksud; elif [ -x /data/adb/ksud ]; then manager=KernelSU; cli=/data/adb/ksud; "
            + "elif [ -x /data/adb/apd ]; then manager=APatch; cli=/data/adb/apd; elif command -v magisk >/dev/null 2>&1; then manager=Magisk; cli=$(command -v magisk); fi;\n"
            + "no_links() { p=$1; while [ \"$p\" != / ] && [ -n \"$p\" ]; do [ ! -L \"$p\" ] || return 1; p=${p%/*}; [ -n \"$p\" ] || p=/; done; };\n"
            + "for p in \"$m\" \"$u\" \"$b\" \"$lm\"; do no_links \"$p\" || { echo C17_PATH_REJECTED; exit 20; }; done;\n"
            + "owned() { f=$1; [ ! -L \"$f\" ] && [ \"$(stat -c %u \"$f\")\" = 0 ] || return 20; "
            + "perm=$(stat -c %a \"$f\") || return 20; case \"$perm\" in ''|*[!0-7]*|?????*) return 20;; esac; "
            // Android mksh arithmetic differs from Bash for leading-zero numbers.
            // The two low digits 2/3/6/7 grant group/other write access.
            + "case \"$perm\" in *[2367][0-7]|*[2367]) return 20;; esac; return 0; };\n"
            + "trusted() { [ -f \"$1\" ] && owned \"$1\"; };\n"
            + "valid() { [ -d \"$1\" ] && owned \"$1\" && trusted \"$1/module.prop\" && grep -qx id=c17_performance \"$1/module.prop\"; };\n"
            + "if [ -e \"$b\" ]; then [ -d \"$b\" ] && owned \"$b\" || { echo C17_PATH_REJECTED; exit 20; }; fi;\n"
            + "legacy() { [ -d \"$lm\" ] && owned \"$lm\" && trusted \"$lm/module.prop\" && grep -qx id=c17_resident_cache \"$lm/module.prop\"; };\n"
            + "choose() { if valid \"$u\"; then d=$u; elif valid \"$m\"; then d=$m; else d=; fi; };\n"
            + "launch() { [ -n \"$d\" ] || return 26; [ ! -e \"$d/disable\" ] && [ ! -e \"$d/remove\" ] || return 27; "
            + "trusted \"$d/common.sh\" || return 20; code=$(sed -n 's/^versionCode=//p' \"$d/module.prop\" | head -n 1); "
            + "case \"$code\" in ''|*[!0-9]*) return 20;; esac; if [ \"$code\" -ge 10 ]; then sh \"$d/common.sh\" lockcheck >/dev/null 2>&1 || return $?; fi; "
            + "if command -v setsid >/dev/null 2>&1; then setsid sh \"$d/common.sh\" \"$1\" </dev/null >/dev/null 2>&1 & "
            + "else sh \"$d/common.sh\" \"$1\" </dev/null >/dev/null 2>&1 & fi; sleep 1; };\n"
            // The replaced cache-backup module runs its own uninstall (removing only its backup) at the next reboot.
            + "drop_legacy() { legacy || return 0; [ ! -L \"$lm/remove\" ] || return 20; [ ! -e \"$lm/remove\" ] || return 0; "
            + "if [ \"$manager\" = KernelSU ] || [ \"$manager\" = APatch ]; then \"$cli\" module uninstall c17_resident_cache >/dev/null 2>&1 && return 0; fi; touch \"$lm/remove\"; };\n";

    private static final String TASK_REPORT =
            "busy=0; active_job=0; current_boot=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null); "
            + "if [ -e \"$b/.lock\" ] || [ -L \"$b/.lock\" ]; then busy=unknown; "
            + "if [ -d \"$b/.lock\" ] && owned \"$b/.lock\" && trusted \"$b/.lock/pid\" && trusted \"$b/.lock/boot\"; then "
            + "pid=$(cat \"$b/.lock/pid\"); previous_boot=$(cat \"$b/.lock/boot\"); "
            + "case \"$pid\" in ''|*[!0-9]*|??????????*) ;; *) if [ \"$previous_boot\" = \"$current_boot\" ] && kill -0 \"$pid\" 2>/dev/null; then busy=1; else busy=0; fi;; esac; fi; fi; "
            + "lock_available=1; if [ -n \"$d\" ] && trusted \"$d/common.sh\"; then code=$(sed -n 's/^versionCode=//p' \"$d/module.prop\" | head -n 1); "
            + "case \"$code\" in ''|*[!0-9]*) ;; *) if [ \"$code\" -ge 10 ]; then lock_report=$(sh \"$d/common.sh\" lockstatus); "
            + "case \"$lock_report\" in unavailable) lock_available=0; busy=unknown;; 0|1|unknown) busy=$lock_report;; *) busy=unknown;; esac; fi;; esac; fi; "
            + "echo C17_PERF:lock_available=$lock_available; "
            + "if [ -e \"$b/active-job\" ] || [ -L \"$b/active-job\" ]; then active_job=unknown; "
            + "if trusted \"$b/active-job\"; then previous_boot=$(sed -n '1p' \"$b/active-job\"); job=$(sed -n '2p' \"$b/active-job\"); "
            + "if [ \"$previous_boot\" != \"$current_boot\" ]; then active_job=0; "
            + "elif printf '%s\\n' \"$job\" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'; then active_job=1; "
            + "elif [ \"$job\" = unknown ] && [ -n \"$(find \"$b/active-job\" -mmin +15 2>/dev/null)\" ]; then active_job=0; fi; fi; fi; "
            + "echo C17_PERF:busy=$busy; echo C17_PERF:active_job=$active_job;\n";
    private static final String REPORT =
            "for candidate in \"$m\" \"$u\"; do if [ -e \"$candidate\" ] || [ -L \"$candidate\" ]; then valid \"$candidate\" || return 20; fi; done;\n"
            + "echo C17_PERF:begin; echo C17_PERF:manager=$manager; installed=0; pending=0; disabled=0; remove=0; old=0;\n"
            + "valid \"$m\" && installed=1; valid \"$u\" && pending=1;\n"
            + "for d in \"$m\" \"$u\"; do [ -d \"$d\" ] || continue; for flag in disable remove update; do [ ! -L \"$d/$flag\" ] || return 20; done; "
            + "[ ! -e \"$d/disable\" ] || disabled=1; [ ! -e \"$d/remove\" ] || remove=1; [ ! -e \"$d/update\" ] || pending=1; done;\n"
            + "if legacy && [ ! -e \"$lm/remove\" ]; then old=1; fi;\n"
            + "echo C17_PERF:installed=$installed; echo C17_PERF:pending=$pending; echo C17_PERF:disabled=$disabled; echo C17_PERF:remove=$remove; echo C17_PERF:legacy=$old;\n"
            + "choose; if [ -n \"$d\" ]; then echo C17_PERF:module_version=$(sed -n 's/^version=//p' \"$d/module.prop\" | head -n 1); "
            + "echo C17_PERF:module_version_code=$(sed -n 's/^versionCode=//p' \"$d/module.prop\" | head -n 1); fi; "
            + "echo C17_PERF:boot=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null);\n"
            // Version 1 status files lack a final newline. Always delimit the protocol, including those existing installations.
            + TASK_REPORT
            + "if [ -n \"$d\" ] && trusted \"$d/cache-service.sh\"; then sh \"$d/cache-service.sh\" status | sed 's/^cache_/C17_PERF:cache_/'; fi; "
            + "if [ -n \"$d\" ] && trusted \"$d/boot-rescue.sh\"; then sh \"$d/boot-rescue.sh\" --status | sed -n 's/^rescue_/C17_PERF:rescue_/p'; fi; "
            + "if trusted \"$b/status\"; then echo C17_PERF:record_present=1; head -c 2048 \"$b/status\" | sed 's/^/C17_PERF:last_/'; printf '\\n'; else echo C17_PERF:record_present=0; fi; "
            + "if trusted \"$b/schedule\"; then head -c 512 \"$b/schedule\" | sed 's/^/C17_PERF:schedule_/'; printf '\\n'; fi; "
            + "if trusted \"$b/ledger\"; then head -c 512 \"$b/ledger\" | sed 's/^/C17_PERF:total_/'; printf '\\n'; fi; "
            + "if trusted \"$b/runtime\"; then head -c 256 \"$b/runtime\" | sed 's/^/C17_PERF:runtime_/'; printf '\\n'; fi; "
            + "if trusted \"$b/trace\"; then printf C17_PERF:trace_hex=; tail -c 2048 \"$b/trace\" | od -An -v -tx1 | tr -d ' \\n'; printf '\\n'; fi; "
            + "if trusted \"$b/operation-last\"; then printf C17_PERF:failure_hex=; tail -c 512 \"$b/operation-last\" | od -An -v -tx1 | tr -d ' \\n'; printf '\\n'; fi; echo C17_PERF:end;\n";

    static String command(Action action, String zip) {
        String operation;
        switch (action) {
            case RESCUE_ENABLE: case RESCUE_DISABLE:
                operation = "choose; [ -n \"$d\" ] && [ \"$d\" = \"$m\" ] || return 26; "
                        + "[ ! -e \"$d/remove\" ] || return 27; trusted \"$d/boot-rescue.sh\" || return 26; "
                        + "sh \"$d/boot-rescue.sh\" " + (action==Action.RESCUE_ENABLE?"--enable":"--disable") + " || return $?;";
                break;
            case INSTALL:
                if (zip == null || !zip.endsWith(".zip") || zip.indexOf('\n') >= 0 || zip.indexOf('\r') >= 0) throw new IllegalArgumentException("module zip path");
                operation = "[ -n \"$cli\" ] || return 25; if [ \"$manager\" = Magisk ]; then \"$cli\" --install-module " + quote(zip)
                        + "; else \"$cli\" module install " + quote(zip) + "; fi || return $?; choose; [ -n \"$d\" ] || return 26; drop_legacy || return $?; launch boot || return $?;";
                break;
            case REMOVE:
                operation = "choose; [ -n \"$d\" ] || return 26; if [ \"$manager\" = KernelSU ] || [ \"$manager\" = APatch ]; then "
                        + "\"$cli\" module uninstall c17_performance || return $?; else for d in \"$m\" \"$u\"; do valid \"$d\" || continue; "
                        + "[ ! -L \"$d/remove\" ] || return 20; touch \"$d/remove\" || return $?; done; fi;";
                break;
            case ENABLE: case DISABLE:
                operation = "choose; [ -n \"$d\" ] || return 26; for d in \"$m\" \"$u\"; do valid \"$d\" || continue; [ ! -L \"$d/disable\" ] || return 20; "
                        + (action == Action.DISABLE ? "touch \"$d/disable\"" : "rm -f \"$d/disable\"") + " || return $?; done;"
                        + (action == Action.ENABLE ? " choose; launch boot || return $?;" : "");
                break;
            case RUN:
                // A full compile takes about a minute; it runs detached and records its own result.
                operation = "choose; launch run || return $?;";
                break;
            case AUTOMATIC:
                // One-shot system jobs submit a detached worker; environmental checks stay inside the root script.
                operation = "choose; [ -n \"$d\" ] || return 26; "
                        + "code=$(sed -n 's/^versionCode=//p' \"$d/module.prop\" | head -n 1); "
                        + "case \"$code\" in ''|*[!0-9]*|?????????*) return 26;; esac; "
                        + "[ \"$code\" -ge " + VERSION_CODE + " ] || return 26; launch auto || return $?;";
                break;
            default: operation = ":;"; // A status check never changes either module.
        }
        // Root operation output may contain manager prose; only framed protocol fields are parsed.
        String plan = takeSchedule();
        String metadata = plan == null ? "" : "plan() { " + scheduleOperation(plan) + " }; plan >/dev/null 2>&1 || :;\n";
        return HEADER + "operate() { " + operation + " }; operate; result=$?; " + metadata
                + "report() { " + REPORT + " }; report || result=20; echo C17_DONE:$result; exit $result\n";
    }
    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private static Result run(String command, Action action) {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final InputStream stream = process.getInputStream(); final OutputTail output = new OutputTail();
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[1024];
                try { for (int count; (count = stream.read(buffer)) != -1;) output.append(buffer, count); }
                catch (IOException closed) { }
            }, "C17ModuleOutput"); reader.setDaemon(true); reader.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, "Root 操作等待超时，完成状态尚未确认；请在授权后刷新状态。", null);
            }
            reader.join(1000L);
            return parse(process.exitValue(), output.text(), action);
        } catch (IOException missingRoot) {
            return new Result(false, "未获得 Root 权限，请在 Root 管理器中允许本应用。", null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new Result(false, "操作结果尚未确认，请刷新状态。", null);
        } finally {
            if (process != null) {
                try { process.getInputStream().close(); } catch (IOException ignored) { }
                try { process.getOutputStream().close(); } catch (IOException ignored) { }
                try { process.getErrorStream().close(); } catch (IOException ignored) { }
            }
        }
    }
    /** Drain all manager output, retaining its tail because our protocol follows installation prose. */
    static final class OutputTail {
        // Both bounded logs plus prefixed status/ledger fields must fit in a complete frame.
        private final byte[] bytes = new byte[16384];
        private int size;
        synchronized void append(byte[] input, int count) {
            int keep = Math.min(count, bytes.length), existing = Math.min(size, bytes.length - keep);
            System.arraycopy(bytes, size - existing, bytes, 0, existing);
            System.arraycopy(input, count - keep, bytes, existing, keep);
            size = existing + keep;
        }
        synchronized String text() { return new String(bytes, 0, size, StandardCharsets.UTF_8); }
    }
    static Result parse(int exit, String text, Action action) {
        Map<String, String> fields = new LinkedHashMap<>(); boolean inside = false, complete = false, done = false, invalid = false;
        if (text != null && text.length() <= 16384) for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if ("C17_PERF:begin".equals(line)) { inside = true; complete = false; done = false; invalid = false; fields.clear(); }
            else if ("C17_PERF:end".equals(line) && inside) { inside = false; complete = !invalid && validFields(fields); }
            else if (inside && line.startsWith("C17_PERF:")) {
                int split = line.indexOf('=');
                if (split > 9) {
                    String key = line.substring(9, split);
                    if (knownField(key)) {
                        if (fields.containsKey(key)) {
                            if (requiredField(key)) invalid = true;
                            else fields.put(key, ""); // Keep reliable installation facts, discard ambiguous optional records.
                        } else fields.put(key, line.substring(split + 1));
                    }
                } else if (requiredField(line.substring(9))) invalid = true;
            } else if ("C17_DONE:0".equals(line) && complete && !inside) done = true;
        }
        Snapshot snapshot = complete ? new Snapshot(fields, true) : null;
        boolean success = exit == 0 && complete && done;
        String message = success ? action == Action.CHECK ? checkMessage(snapshot)
                : action == Action.REMOVE ? "已请求卸载，重启后完成。"
                : action == Action.INSTALL ? "安装已提交。"
                : action == Action.RUN ? "已提交优化。"
                : action == Action.AUTOMATIC ? "已提交自动检查。"
                : action == Action.RESCUE_ENABLE ? "系统UI防炸已开启，本次没有清理数据。"
                : action == Action.RESCUE_DISABLE ? "系统UI防炸已关闭。"
                : action == Action.ENABLE ? "模块已启用。" : "模块已停用，当前优化继续。"
                : exit == 21 ? "请授予 Root 权限。" : exit == 20 ? "模块路径异常，已停止。"
                : exit == 25 ? "未找到支持的 Root 管理器。"
                : exit == 26 ? "请先安装附属模块。" : exit == 27 ? "模块已停用或待卸载。"
                : exit == 28 ? "暂不支持启动优化。"
                : exit == 29 ? "任务状态未确认，重启后再试。"
                : "操作未完成，请刷新。";
        return new Result(success, message, snapshot);
    }
    private static String checkMessage(Snapshot snapshot) {
        return (snapshot.root && snapshot.installed ? snapshot.cacheMessage() + "\n" : "") + compilationMessage(snapshot);
    }
    private static String compilationMessage(Snapshot snapshot) {
        if ("failed".equals(snapshot.lastState) && (snapshot.error.startsWith("restore_") || "uninstall".equals(snapshot.trigger)))
            return (!snapshot.installed && !snapshot.pending ? "附属模块未安装。" : "") + snapshot.errorMessage() + "。"
                    + (!snapshot.installed && !snapshot.pending && snapshot.error.startsWith("restore_")
                    ? "可重新安装附属模块后再次请求卸载恢复。" : "");
        if (snapshot.remove) return "模块已标记卸载，重启后尝试恢复系统编译方式并移除；恢复失败会保留运行记录。";
        if (!snapshot.lockAvailable) return "系统缺少可靠的任务互斥能力，优化未启动，原运行记录已保留。";
        if (snapshot.disabled) return "后续自动处理已暂停；不会取消正在执行的系统任务。";
        if (snapshot.pending && "unknown".equals(snapshot.lastState)) return "管理器已接收模块，开机注册待更新；安装后会自动检查，无需手动重启系统界面。";
        if (!snapshot.installed && !snapshot.pending) return snapshot.legacy ? "尚未安装附属模块。旧的缓存备份模块仍在，安装附属模块时会自动移除它。" : "尚未安装附属模块。";
        String record = snapshot.currentBoot ? "本次开机：" : "历史记录（尚未确认本次开机执行）：";
        String update = snapshot.outdated ? " 旧版模块可读取，请更新 Status C17 附属模块以使用新的自动与手动规则。" : "";
        boolean modern = snapshot.recordVersionCode >= VERSION_CODE;
        String counts = "本轮编译 " + snapshot.compiled + " 个、复用 " + snapshot.reused + " 个。";
        String proof = " " + snapshot.verificationMessage() + "。";
        switch (snapshot.lastState) {
            case "ok": return record + (modern ? counts + proof : "已预编译 " + snapshot.compiled + "/" + snapshot.packages + " 个组件。")
                    + " 无需手动重启系统界面，已有进程在自然重新加载时使用新结果。" + update;
            case "current": return record + (modern ? counts + proof : "版本没有变化，继续使用上次的预编译；当前实际编译状态尚未核对。") + update;
            case "waiting": return record + ("run".equals(snapshot.trigger) ? "手动优化已提交，不等待环境条件；正在准备任务。" : "自动优化等待启动完成、息屏或连续无操作 10 分钟、电量高于 15% 和电池温度低于 55℃；充电与否不影响。") + update;
            case "running": return record + "正在检查或预编译，稍后刷新查看。" + (modern ? " " + counts : "") + update;
            case "deferred": return record + ("run".equals(snapshot.trigger) ? "手动优化本次未执行：" : "自动优化已延期：") + snapshot.errorMessage() + "。待处理 " + snapshot.deferred + " 个组件，下次开机或手动优化时重新检查。" + proof + update;
            case "failed": return record + "优化未完成：" + snapshot.errorMessage() + (modern ? "。" + counts + proof : "") + update;
            default: return "已安装，尚无运行记录；后续开机会自动检查，也可点“立即优化”。" + update;
        }
    }
    private static boolean requiredField(String key) {
        return "manager".equals(key) || "installed".equals(key) || "pending".equals(key)
                || "disabled".equals(key) || "remove".equals(key) || "legacy".equals(key);
    }
    private static boolean knownField(String key) {
        if (requiredField(key)) return true;
        switch (key) {
            case "module_version": case "module_version_code": case "boot":
            case "last_state": case "last_time": case "last_compiled": case "last_packages":
            case "last_error": case "last_boot": case "last_mode": case "last_module_version_code":
            case "last_reused": case "last_verified": case "last_deferred": case "last_verification":
            case "last_priority": case "last_idle_remaining": case "last_diagnostic":
            case "busy": case "active_job": case "record_present": case "lock_available":
            case "schedule_kind": case "schedule_state": case "schedule_next_after":
            case "total_since": case "total_compiles": case "total_wall_ms": case "total_cpu_ms": case "total_size_kb":
            case "total_reuses": case "total_avoided_ms":
            case "runtime_sampled": case "runtime_jit_ms": case "runtime_proc_s": case "runtime_aot":
            case "trace_hex": case "failure_hex":
            case "cache_running": case "cache_protocol": case "cache_bytes": case "cache_limit": case "cache_entries":
            case "cache_hits": case "cache_misses": case "cache_disk_hits": case "cache_oom_adj": case "cache_memory_low": return true;
            case "rescue_enabled": case "rescue_state": case "rescue_error": case "rescue_time": return true;
            default: return false;
        }
    }
    private static boolean validFields(Map<String, String> fields) {
        String manager = fields.get("manager");
        if (!("KernelSU".equals(manager) || "Magisk".equals(manager) || "APatch".equals(manager) || "unknown".equals(manager))) return false;
        for (String flag : new String[] {"installed", "pending", "disabled", "remove", "legacy"}) {
            String value = fields.get(flag); if (!("0".equals(value) || "1".equals(value))) return false;
        }
        return true;
    }
    static String decodeLog(String hex, int limit) {
        if (hex == null || hex.length() > limit * 2 || (hex.length() & 1) != 0 || !hex.matches("[0-9a-fA-F]*")) return "";
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    .replaceAll("[\\x00-\\x08\\x0b-\\x1f\\x7f]", "");
        } catch (java.nio.charset.CharacterCodingException malformed) {
            return "日志编码不完整，请查看后续记录";
        }
    }
    static String encodeLog(String value) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) { int b = bytes[i] & 255; result[i * 2] = alphabet[b >>> 4]; result[i * 2 + 1] = alphabet[b & 15]; }
        return new String(result);
    }
    static long number(String value, long maximum) {
        if (value == null || !value.matches("[0-9]{1,19}")) return 0;
        try { long found = Long.parseLong(value); return found >= 0 && found <= maximum ? found : 0; } catch (NumberFormatException overflow) { return 0; }
    }
    private static String uuid(String value) {
        return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") ? value : "";
    }
    private static String taskFlag(String value) { return "0".equals(value) || "1".equals(value) ? value : "unknown"; }
    private static boolean rescueStateValue(String value) {
        return "idle".equals(value)||"watching".equals(value)||"triggered".equals(value)||"waiting_unlock".equals(value)
                ||"cleared".equals(value)||"failed".equals(value)||"cancelled".equals(value);
    }
    private static boolean rescueErrorValue(String value) {
        return value != null && java.util.Arrays.asList("none","path_rejected","input_unavailable","status_write_failed","reset_failed",
                "package_unavailable","clear_unknown","user_locked","external_unavailable","boot_gate_rejected",
                "authorization_rejected","pending_expired","unsupported").contains(value);
    }
    private static String rescueErrorMessage(String value) {
        switch(value) {
            case "path_rejected": return "数据路径校验未通过，已停止";
            case "input_unavailable": return "无法读取按键设备";
            case "status_write_failed": return "无法保存恢复记录";
            case "reset_failed": return "系统数据未全部清理，请核对结果";
            case "package_unavailable": return "系统包管理器尚未就绪";
            case "clear_unknown": return "包管理器完成状态未知，未并发重复清理";
            case "user_locked": return "用户数据尚未解锁";
            case "external_unavailable": return "外部存储尚不可访问";
            case "boot_gate_rejected": return "已离开允许触发的开机阶段";
            case "authorization_rejected": return "没有本次按键授权";
            case "pending_expired": return "待恢复请求已过期";
            default: return "当前系统尚不支持";
        }
    }
    private static void webField(StringBuilder out, String key, Object value) { out.append(key).append('=').append(value).append('\n'); }
    private static boolean scheduleStateValue(String value) {
        return "scheduled".equals(value) || "inactive".equals(value) || "unavailable".equals(value)
                || "deferred".equals(value) || "blocked".equals(value) || "running".equals(value);
    }
    /** Stage metadata for the next authorized root call; scheduling never launches an extra su process. */
    public static synchronized void recordSchedule(String state, long nextAfter) {
        if (!scheduleStateValue(state) || nextAfter < 0L || nextAfter > 9999999999L) throw new IllegalArgumentException("schedule data");
        pendingSchedule = "kind=on_demand\nstate=" + state + "\nnext_after=" + nextAfter + "\n";
    }
    private static synchronized String takeSchedule() {
        String plan = pendingSchedule; pendingSchedule = null; return plan;
    }
    private static String scheduleOperation(String data) {
        return "choose; [ -n \"$d\" ] || return 26; [ -d \"$b\" ] && owned \"$b\" || return 20; "
                + "[ ! -L \"$b/schedule\" ] || return 20; temp=$(mktemp \"$b/.schedule.XXXXXX\") || return 22; "
                + "printf '%s' " + quote(data) + " > \"$temp\" && chmod 0600 \"$temp\" && mv -f \"$temp\" \"$b/schedule\" || { rm -f \"$temp\"; return 22; };";
    }
}
