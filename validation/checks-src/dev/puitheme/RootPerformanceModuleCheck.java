package dev.puitheme;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

/** Framed manager results, bounded output and exact module-only command targets. No root process is started. */
public final class RootPerformanceModuleCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++; if (!Objects.equals(expected, actual)) throw new AssertionError("Root performance " + checks + ": " + expected + " != " + actual);
    }
    private static final String HEADER = "C17_PERF:begin\nC17_PERF:manager=KernelSU\nC17_PERF:installed=1\n"
            + "C17_PERF:pending=0\nC17_PERF:disabled=0\nC17_PERF:remove=0\nC17_PERF:legacy=0\n";
    private static final String RECORD = "C17_PERF:last_state=ok\nC17_PERF:last_time=1791500000\nC17_PERF:last_compiled=2\n"
            + "C17_PERF:last_packages=2\nC17_PERF:last_error=none\n";
    private static final String END = "C17_PERF:end\nC17_DONE:0\n";
    private static RootPerformanceModule.Result parse(int exit, String text) { return RootPerformanceModule.parse(exit, text, RootPerformanceModule.Action.CHECK); }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--web-status-file".equals(args[0])) {
            RootPerformanceModule.Result result = parse(0, new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8));
            equal(true, result.success); System.out.print(result.snapshot.webStatus()); return;
        }
        // Replay actual generated-command output, including status files without a final newline.
        if (args.length == 2 && "--parse-file".equals(args[0])) {
            RootPerformanceModule.Result result = parse(0, new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8));
            equal(true, result.success);
            System.out.println("Root performance real command protocol parsed: home=" + result.snapshot.homeStatus()
                    + ", compiled=" + result.snapshot.compiled + ", reused=" + result.snapshot.reused
                    + ", verified=" + result.snapshot.verified + ", deferred=" + result.snapshot.deferred
                    + ", verification=" + result.snapshot.verification + ", state=" + result.snapshot.lastState);
            return;
        }
        // The shell fixture uses the same generated commands, rewriting only the fixed absolute sandbox paths.
        if (args.length == 1) {
            for (RootPerformanceModule.Action action : RootPerformanceModule.Action.values())
                Files.write(Paths.get(args[0], action.name() + ".sh"), RootPerformanceModule.command(action, "/data/local/tmp/c17-test's-module.zip").getBytes(StandardCharsets.UTF_8));
        }
        RootPerformanceModule.Result good = parse(0, "manager prose\n" + HEADER + RECORD + END);
        equal(true, good.success); equal(true, good.snapshot.root); equal("KernelSU", good.snapshot.manager);
        equal(true, good.snapshot.installed); equal(false, good.snapshot.pending); equal(false, good.snapshot.disabled); equal(false, good.snapshot.remove);
        equal(false, good.snapshot.legacy); equal("ok", good.snapshot.lastState); equal(1791500000L, good.snapshot.time);
        equal(2L, good.snapshot.compiled); equal(2L, good.snapshot.packages); equal(true, good.message.contains("已预编译 2/2"));
        equal(true, parse(0, HEADER + RECORD.replace("state=ok", "state=current") + END).message.contains("版本没有变化"));
        equal(true, parse(0, HEADER + RECORD.replace("state=ok", "state=running") + END).message.contains("正在检查或预编译"));
        RootPerformanceModule.Result failed = parse(0, HEADER + RECORD.replace("state=ok", "state=failed").replace("error=none", "error=compile_failed") + END);
        equal("failed", failed.snapshot.lastState); equal(true, failed.message.contains("下次开机会重试"));
        equal("unknown", parse(0, HEADER + RECORD.replace("state=ok", "state=$(boom)") + END).snapshot.lastState);
        equal(true, parse(0, HEADER + END).message.contains("尚无运行记录"));
        // Framing: only a complete block followed by the exit marker counts.
        equal(false, parse(22, HEADER + RECORD + END).success);
        equal(false, parse(0, "C17_DONE:0\n" + HEADER + RECORD + "C17_PERF:end\n").success);
        equal(false, parse(0, HEADER + RECORD).success);
        equal(null, parse(0, "C17_PERF:installed=1\nC17_PERF:end\nC17_DONE:0\n").snapshot);
        equal(false, parse(0, HEADER.replace("C17_PERF:legacy=0\n", "") + END).success);
        equal(false, parse(0, HEADER.replace("installed=1", "installed=yes") + END).success);
        equal(false, parse(0, HEADER + "C17_PERF:installed=0\n" + END).success);
        equal(false, parse(0, HEADER + END + "C17_PERF:begin\n").success);
        equal(false, parse(0, HEADER.replace("manager=KernelSU", "manager=$(bad)") + END).success);
        equal("未识别", parse(0, HEADER.replace("manager=KernelSU", "manager=unknown") + END).snapshot.manager);
        equal("Magisk", parse(0, HEADER.replace("manager=KernelSU", "manager=Magisk") + END).snapshot.manager);
        equal(true, parse(0, HEADER.replace("disabled=0", "disabled=1") + END).message.contains("已暂停"));
        equal(true, parse(0, HEADER.replace("remove=0", "remove=1") + END).message.contains("标记卸载"));
        equal(true, parse(0, HEADER.replace("installed=1", "installed=0").replace("pending=0", "pending=1") + END).message.contains("开机注册待更新"));
        equal(true, parse(0, HEADER.replace("installed=1", "installed=0") + END).message.contains("尚未安装"));
        equal(true, parse(0, HEADER.replace("installed=1", "installed=0").replace("legacy=0", "legacy=1") + END).message.contains("自动移除"));
        String broken = RECORD.replace("1791500000", "9223372036854775807").replace("compiled=2", "compiled=-1")
                .replace("packages=2", "packages=65").replace("error=none", "error=$(execute)");
        RootPerformanceModule.Snapshot bounded = parse(0, HEADER + broken + END).snapshot;
        equal(0L, bounded.time); equal(0L, bounded.compiled); equal(0L, bounded.packages); equal("none", bounded.error);
        equal(0L, RootPerformanceModule.number("9223372036854775808", Long.MAX_VALUE));
        // Action messages and exit codes.
        equal(true, RootPerformanceModule.parse(0, HEADER + END, RootPerformanceModule.Action.RUN).message.contains("已提交优化"));
        equal(true, RootPerformanceModule.parse(0, HEADER + END, RootPerformanceModule.Action.INSTALL).message.contains("安装已提交"));
        equal(true, RootPerformanceModule.parse(27, HEADER + END.replace("C17_DONE:0", "C17_DONE:27"), RootPerformanceModule.Action.RUN).message.contains("已停用或待卸载"));
        equal(true, RootPerformanceModule.parse(25, "", RootPerformanceModule.Action.INSTALL).message.contains("未找到支持的 Root 管理器"));
        // Command targets: only this module, the old cache-backup id only for its removal, quoted zip, no newline injection.
        String install = RootPerformanceModule.command(RootPerformanceModule.Action.INSTALL, "/data/local/tmp/a'b.zip");
        equal(true, install.contains("module install '/data/local/tmp/a'\\''b.zip'"));
        equal(true, install.contains("drop_legacy"));
        equal(true, install.contains("launch boot"));
        equal(false, install.contains("launch run"));
        String check = RootPerformanceModule.command(RootPerformanceModule.Action.CHECK, null);
        equal(false, check.contains("operate() { drop_legacy"));
        equal(true, check.contains("valid \"$candidate\" || return 20")); // A suspicious installed directory cannot be reported as absent.
        equal(true, RootPerformanceModule.command(RootPerformanceModule.Action.REMOVE, null).contains("module uninstall c17_performance"));
        equal(true, RootPerformanceModule.command(RootPerformanceModule.Action.RUN, null).contains("choose; launch run"));
        // Existing V1 modules remain readable; V2 records identify this boot instead of presenting old success as current.
        RootPerformanceModule.Snapshot oldModule = parse(0, HEADER + "C17_PERF:module_version=1.0\nC17_PERF:module_version_code=1\n" + RECORD + END).snapshot;
        equal("1.0", oldModule.moduleVersion); equal(1L, oldModule.moduleVersionCode); equal(true, oldModule.outdated); equal(false, oldModule.currentBoot);
        String boot = "00000000-0000-4000-8000-000000000107";
        String metadata = "C17_PERF:module_version=2.3\nC17_PERF:module_version_code=14\nC17_PERF:boot=" + boot + "\n";
        String bootRecord = "C17_PERF:last_boot=" + boot + "\nC17_PERF:last_mode=boot\n";
        RootPerformanceModule.Result thisBoot = parse(0, HEADER + metadata + RECORD + bootRecord + END);
        equal(false, thisBoot.snapshot.outdated); equal(true, thisBoot.snapshot.currentBoot); equal("boot", thisBoot.snapshot.trigger);
        equal(true, thisBoot.message.contains("本次开机：")); equal(true, thisBoot.message.contains("自然重新加载"));
        equal(false, parse(0, HEADER + metadata + RECORD + bootRecord.replace("000000000107", "000000000106") + END).snapshot.currentBoot);
        equal(true, parse(0, HEADER + metadata + RECORD.replace("state=ok", "state=waiting") + bootRecord + END).message.contains("等待启动完成"));
        equal("unknown", parse(0, HEADER + metadata + RECORD + bootRecord.replace("mode=boot", "mode=$(boom)") + END).snapshot.trigger);
        equal(0L, parse(0, HEADER + metadata.replace("module_version_code=14", "module_version_code=1299999999") + RECORD + END).snapshot.moduleVersionCode);
        equal("未知", parse(0, HEADER + metadata.replace("module_version=2.3", "module_version=$(boom)") + RECORD + END).snapshot.moduleVersion);
        String pending = HEADER.replace("installed=1", "installed=0").replace("pending=0", "pending=1");
        equal(true, parse(0, pending + metadata + RECORD + bootRecord + END).message.contains("已预编译 2/2"));
        // The home card reports activation independently from optimization completion.
        equal("Status C17 附属模块", RootPerformanceModule.DISPLAY_NAME);
        equal(14L, RootPerformanceModule.VERSION_CODE);
        equal("已安装", oldModule.homeStatus());
        equal("已安装", thisBoot.snapshot.homeStatus()); // V2 success from this boot cannot prove the new script ran.
        String proof = "C17_PERF:last_module_version_code=14\nC17_PERF:last_reused=1\nC17_PERF:last_verified=2\n"
                + "C17_PERF:last_deferred=0\nC17_PERF:last_verification=verified\nC17_PERF:last_priority=background\n";
        String modernRecord = RECORD.replace("compiled=2", "compiled=1");
        String modern = HEADER + metadata + modernRecord + bootRecord + proof;
        RootPerformanceModule.Result optimized = parse(0, modern + END);
        equal("已激活", optimized.snapshot.homeStatus()); equal(1L, optimized.snapshot.compiled);
        equal(1L, optimized.snapshot.reused); equal(2L, optimized.snapshot.verified); equal(2L, optimized.snapshot.ready);
        equal(0L, optimized.snapshot.deferred); equal(14L, optimized.snapshot.recordVersionCode);
        equal("verified", optimized.snapshot.verification); equal("background", optimized.snapshot.priority);
        equal(true, optimized.message.contains("本轮编译 1 个、复用 1 个"));
        equal(true, optimized.message.contains("核对系统当前编译状态：2/2"));
        equal("未安装", parse(0, HEADER.replace("installed=1", "installed=0") + END).snapshot.homeStatus());
        equal("待查询", new RootPerformanceModule.Snapshot(new java.util.LinkedHashMap<>(), false).homeStatus());
        for (String flag : new String[]{"disabled", "remove", "pending"})
            equal("已安装", parse(0, modern.replace(flag + "=0", flag + "=1") + END).snapshot.homeStatus());
        equal("已安装", parse(0, modern.replace("last_module_version_code=14", "last_module_version_code=2") + END).snapshot.homeStatus());
        equal("已安装", parse(0, modern.replace("last_boot=" + boot, "last_boot=00000000-0000-4000-8000-000000000106") + END).snapshot.homeStatus());
        equal(true, parse(0, modern.replace("last_boot=" + boot, "last_boot=00000000-0000-4000-8000-000000000106") + END).snapshot.verificationMessage().contains("本次开机尚未确认"));
        equal(true, parse(0, modern.replace("C17_PERF:module_version_code=14", "C17_PERF:module_version_code=15") + END).snapshot.verificationMessage().contains("新版本尚未确认"));
        for (String state : new String[]{"waiting", "deferred", "failed", "current"})
            equal("已激活", parse(0, modern.replace("state=ok", "state=" + state) + END).snapshot.homeStatus());
        String runningRecord = modern.replace("state=ok", "state=running");
        RootPerformanceModule.Snapshot running = parse(0, runningRecord + END).snapshot;
        equal("优化中", running.homeStatus());
        equal("优化中", parse(0, runningRecord.replace("mode=boot", "mode=run") + END).snapshot.homeStatus());
        // Historical, old-version, malformed-mode or absent-module records cannot claim a current optimization.
        equal("已安装", parse(0, runningRecord.replace("last_boot=" + boot,
                "last_boot=00000000-0000-4000-8000-000000000106") + END).snapshot.homeStatus());
        equal("已安装", parse(0, runningRecord.replace("last_module_version_code=14", "last_module_version_code=2") + END).snapshot.homeStatus());
        equal("已安装", parse(0, runningRecord.replace("C17_PERF:module_version_code=14", "C17_PERF:module_version_code=15") + END).snapshot.homeStatus());
        equal("已安装", parse(0, runningRecord.replace("mode=boot", "mode=uninstall") + END).snapshot.homeStatus());
        equal("已安装", parse(0, runningRecord.replace("mode=boot", "mode=future_mode") + END).snapshot.homeStatus());
        equal("未安装", parse(0, runningRecord.replace("installed=1", "installed=0") + END).snapshot.homeStatus());
        // Home presentation distinguishes in-flight RUN from installation/management, with warnings above RUN.
        equal("优化中", RootPerformanceModule.homeStatus(running, false, false, null));
        equal("优化中", RootPerformanceModule.homeStatus(optimized.snapshot, true, false, RootPerformanceModule.Action.RUN));
        equal("待查询", RootPerformanceModule.homeStatus(running, false, true, null));
        equal("待查询", RootPerformanceModule.homeStatus(running, true, true, RootPerformanceModule.Action.RUN));
        equal("待查询", RootPerformanceModule.homeStatus(null, true, false, RootPerformanceModule.Action.RUN));
        equal("待查询", RootPerformanceModule.homeStatus(new RootPerformanceModule.Snapshot(new java.util.LinkedHashMap<>(), false),
                true, false, RootPerformanceModule.Action.RUN));
        for (RootPerformanceModule.Action action : RootPerformanceModule.Action.values()) {
            if (action != RootPerformanceModule.Action.RUN && action != RootPerformanceModule.Action.AUTOMATIC) {
                equal("处理中", RootPerformanceModule.homeStatus(running, true, false, action));
                equal("处理中", RootPerformanceModule.homeStatus(running, true, true, action));
            }
        }
        String[] priorityFlags = {"remove", "disabled", "pending"};
        String[] priorityLabels = {"待卸载", "已停用", "待重启"};
        for (int index = 0; index < priorityFlags.length; index++) {
            RootPerformanceModule.Snapshot flagged = parse(0, runningRecord.replace(priorityFlags[index] + "=0",
                    priorityFlags[index] + "=1") + END).snapshot;
            equal(priorityLabels[index], RootPerformanceModule.homeStatus(flagged, false, false, null));
            equal(priorityLabels[index], RootPerformanceModule.homeStatus(flagged, true, false, RootPerformanceModule.Action.RUN));
            equal("待查询", RootPerformanceModule.homeStatus(flagged, true, true, RootPerformanceModule.Action.RUN));
            equal("处理中", RootPerformanceModule.homeStatus(flagged, true, false, RootPerformanceModule.Action.INSTALL));
        }
        RootPerformanceModule.Snapshot oldRunning = parse(0, runningRecord.replace("C17_PERF:module_version_code=14",
                "C17_PERF:module_version_code=3") + END).snapshot;
        equal("待更新", RootPerformanceModule.homeStatus(oldRunning, false, false, null));
        equal("待更新", RootPerformanceModule.homeStatus(oldRunning, true, false, RootPerformanceModule.Action.RUN));
        RootPerformanceModule.Snapshot competingFlags = parse(0, runningRecord.replace("remove=0", "remove=1")
                .replace("disabled=0", "disabled=1").replace("pending=0", "pending=1") + END).snapshot;
        equal("待卸载", RootPerformanceModule.homeStatus(competingFlags, true, false, RootPerformanceModule.Action.RUN));
        equal("未安装", RootPerformanceModule.homeStatus(parse(0, runningRecord.replace("installed=1", "installed=0")
                + END).snapshot, true, false, RootPerformanceModule.Action.RUN));
        equal("已安装", parse(0, modern.replace("state=ok", "state=future_state") + END).snapshot.homeStatus());
        equal("已安装", parse(0, modern.replace("mode=boot", "mode=uninstall") + END).snapshot.homeStatus());
        equal("已安装", parse(0, modern.replace("mode=boot", "mode=future_mode") + END).snapshot.homeStatus());
        RootPerformanceModule.Result postponed = parse(0, modern.replace("state=ok", "state=deferred")
                .replace("error=none", "error=device_hot").replace("deferred=0", "deferred=1") + END);
        equal(1L, postponed.snapshot.deferred); equal(true, postponed.message.contains("55℃"));
        equal(true, postponed.message.contains("待处理 1 个"));
        equal(true, parse(0, modern.replace("C17_PERF:module_version_code=14", "C17_PERF:module_version_code=3") + END).snapshot.outdated);
        RootPerformanceModule.Result manualUnsupported = parse(0, modern.replace("state=ok", "state=deferred")
                .replace("mode=boot", "mode=run").replace("error=none", "error=art_cancel_unavailable") + END);
        equal(true, manualUnsupported.message.contains("手动优化本次未执行"));
        equal(false, manualUnsupported.message.contains("手动优化已延期"));
        equal(true, postponed.snapshot.errorMessage().contains("55℃"));
        equal(true, parse(0, modern.replace("error=none", "error=battery_low") + END).snapshot.errorMessage().contains("15%"));
        // V3 manual records could fail environmental checks; do not relabel them as automatic runs.
        for (String problem : new String[]{"device_hot", "battery_low", "thermal_unknown"}) {
            String legacyManual = modern.replace("module_version=2.3", "module_version=1.2")
                    .replace("version_code=14", "version_code=3").replace("mode=boot", "mode=run")
                    .replace("state=ok", "state=deferred").replace("error=none", "error=" + problem);
            String detail = parse(0, legacyManual + END).snapshot.errorMessage();
            equal(true, detail.startsWith("该次优化") && detail.contains("当前自动") && detail.contains("手动"));
        }
        for (String problem : new String[]{"device_interactive", "device_hot", "battery_low", "power_unknown", "thermal_unknown",
                "screen_unknown", "timeout_unavailable", "art_cancel_unavailable", "art_unverified", "compile_timeout",
                "compile_cancel_failed", "restore_failed", "restore_timeout", "restore_cancel_failed"})
            equal(false, parse(0, modern.replace("error=none", "error=" + problem) + END).snapshot.errorMessage().contains("部分步骤"));
        equal(true, parse(0, modern.replace("error=none", "error=restore_failed") + END).snapshot.errorMessage().contains("失败记录已保留"));
        RootPerformanceModule.Result restoreFailure = parse(0, modern.replace("installed=1", "installed=0")
                .replace("state=ok", "state=failed").replace("error=none", "error=restore_failed")
                .replace("mode=boot", "mode=uninstall") + END);
        equal("未安装", restoreFailure.snapshot.homeStatus()); equal("uninstall", restoreFailure.snapshot.trigger);
        equal(true, restoreFailure.message.contains("恢复系统编译方式失败"));
        equal(false, restoreFailure.message.contains("重试恢复"));
        equal(true, restoreFailure.message.contains("重新安装附属模块后"));
        equal(true, parse(0, modern.replace("installed=1", "installed=0").replace("state=ok", "state=failed")
                .replace("error=none", "error=uninstall_failed").replace("mode=boot", "mode=uninstall") + END).message.contains("卸载清理未完成"));
        equal("partial", parse(0, modern.replace("verified=2", "verified=1") + END).snapshot.verification);
        equal("unverified", parse(0, modern.replace("verified=2", "verified=65") + END).snapshot.verification);
        equal("unverified", parse(0, modern.replace("verification=verified", "verification=$(boom)") + END).snapshot.verification);
        equal("unknown", parse(0, modern.replace("priority=background", "priority=$(boom)") + END).snapshot.priority);
        equal(0L, parse(0, modern.replace("reused=1", "reused=3") + END).snapshot.reused);
        equal(0L, parse(0, modern.replace("deferred=0", "deferred=-1") + END).snapshot.deferred);
        equal(true, parse(0, modern.replace("state=ok", "state=current") + END).message.contains("复用 1"));
        // New or malformed optional records preserve trustworthy root/module installation facts.
        equal(true, parse(0, modern + "C17_PERF:future_field=a\nC17_PERF:future_field=b\nC17_PERF:future_record\n" + END).success);
        RootPerformanceModule.Result ambiguous = parse(0, modern + "C17_PERF:last_state=current\n" + END);
        equal(true, ambiguous.success); equal("unknown", ambiguous.snapshot.lastState); equal("已安装", ambiguous.snapshot.homeStatus());
        equal(false, parse(0, modern + "C17_PERF:installed=1\n" + END).success);
        String invalidBoot = "------------------------------------";
        equal(false, parse(0, modern.replace(boot, invalidBoot) + END).snapshot.currentBoot);
        equal(false, RootPerformanceModule.parse(0, modern + END, RootPerformanceModule.Action.REMOVE).message.contains("并恢复系统默认"));
        // Minimized on-device V1 capture: no final LF is legal data when the reporter inserts its delimiter.
        String legacyStatus = "C17_PERF:last_state=ok\nC17_PERF:last_time=1791500000\nC17_PERF:last_compiled=2\nC17_PERF:last_packages=2\nC17_PERF:last_error=none";
        equal(false, parse(0, HEADER + legacyStatus + END).success);
        equal(true, parse(0, HEADER + legacyStatus + "\n" + END).success);
        for (String bad : new String[]{null, "/x.apk", "/x\n.zip", "/x\r.zip"}) {
            try { RootPerformanceModule.command(RootPerformanceModule.Action.INSTALL, bad); equal("rejected", "accepted"); }
            catch (IllegalArgumentException expected) { equal(true, true); }
        }
        for (RootPerformanceModule.Action action : RootPerformanceModule.Action.values()) {
            String command = RootPerformanceModule.command(action, "/data/local/tmp/m.zip");
            equal(false, command.contains("c17-resident-cache"));
            equal(true, command.endsWith("exit $result\n"));
        }
        RootPerformanceModule.OutputTail tail = new RootPerformanceModule.OutputTail();
        byte[] noise = new byte[10000]; java.util.Arrays.fill(noise, (byte) 'x'); noise[noise.length - 1] = '\n'; tail.append(noise, noise.length);
        byte[] protocol = (HEADER + RECORD + END).getBytes(StandardCharsets.UTF_8); tail.append(protocol, protocol.length);
        equal(true, parse(0, tail.text()).success);

        equal(true, RootPerformanceModule.command(RootPerformanceModule.Action.AUTOMATIC, null).contains("launch auto"));
        equal("auto", parse(0, modern.replace("mode=boot", "mode=auto") + END).snapshot.trigger);
        String tasks = modern + "C17_PERF:busy=0\nC17_PERF:active_job=0\nC17_PERF:record_present=1\n"
                + "C17_PERF:schedule_kind=on_demand\nC17_PERF:schedule_state=scheduled\nC17_PERF:schedule_next_after=1791858000\n"
                + "C17_PERF:last_idle_remaining=600\nC17_PERF:last_diagnostic=uuid_missing\n";
        RootPerformanceModule.Snapshot web = parse(0, tasks + END).snapshot;
        equal(false, web.taskActive); equal(false, web.taskBlocked); equal(true, web.recordPresent);
        equal(600L, web.idleRemaining); equal("uuid_missing", web.diagnostic); equal("scheduled", web.scheduleState);
        equal(true, web.webStatus().startsWith("C17_WEB:begin\n")); equal(true, web.webStatus().endsWith("C17_WEB:end\n"));
        equal(true, web.webStatus().contains("record_present=1\n")); equal(true, web.webStatus().contains("last_diagnostic=uuid_missing\n"));
        // Benefit ledger and the last SystemUI sample are optional framed data; bounds apply and nothing else is trusted.
        String ledger = "C17_PERF:total_since=1792257000\nC17_PERF:total_compiles=3\nC17_PERF:total_wall_ms=12500\nC17_PERF:total_cpu_ms=900\n"
                + "C17_PERF:total_size_kb=6144\nC17_PERF:total_reuses=8\nC17_PERF:total_avoided_ms=30000\n"
                + "C17_PERF:runtime_sampled=1792257900\nC17_PERF:runtime_jit_ms=6000\nC17_PERF:runtime_proc_s=600\nC17_PERF:runtime_aot=1\n";
        RootPerformanceModule.Snapshot booked = parse(0, tasks + ledger + END).snapshot;
        equal(3L, booked.ledgerCompiles); equal(12500L, booked.ledgerWallMs); equal(900L, booked.ledgerCpuMs); equal(6144L, booked.ledgerSizeKb);
        equal(8L, booked.ledgerReuses); equal(30000L, booked.ledgerAvoidedMs); equal(1792257000L, booked.ledgerSince);
        equal(6000L, booked.runtimeJitMs); equal(600L, booked.runtimeProcSeconds); equal(true, booked.runtimeAot);
        equal(true, booked.webStatus().contains("total_reuses=8\n")); equal(true, booked.webStatus().contains("runtime_aot=1\n"));
        RootPerformanceModule.Snapshot unbooked = parse(0, tasks + END).snapshot;
        equal(0L, unbooked.ledgerCompiles); equal(0L, unbooked.ledgerSince); equal(false, unbooked.runtimeAot);
        equal(0L, parse(0, tasks + ledger.replace("total_compiles=3", "total_compiles=-1") + END).snapshot.ledgerCompiles);
        equal(0L, parse(0, tasks + ledger.replace("total_wall_ms=12500", "total_wall_ms=1e9") + END).snapshot.ledgerWallMs);
        equal(0L, parse(0, tasks + ledger.replace("total_reuses=8", "total_reuses=99999999999") + END).snapshot.ledgerReuses);
        equal(false, parse(0, tasks + ledger.replace("runtime_aot=1", "runtime_aot=yes") + END).snapshot.runtimeAot);
        // A repeated optional ledger key is ambiguous and is discarded; it must not invalidate the report.
        equal(0L, parse(0, tasks + ledger + "C17_PERF:total_compiles=4\n" + END).snapshot.ledgerCompiles);
        equal("client_failed", parse(0, tasks.replace("uuid_missing", "client_failed") + END).snapshot.diagnostic);
        equal(true, parse(0, tasks.replace("uuid_missing", "client_failed") + END).snapshot.diagnosticMessage().length() > 0);
        // An unknown task marker that the report shows as expired (active_job=0) no longer blocks a recorded cancel failure.
        String cancelled = tasks.replace("last_error=none", "last_error=compile_cancel_failed");
        equal(false, parse(0, cancelled + END).snapshot.taskBlocked);
        equal(true, parse(0, cancelled.replace("active_job=0", "active_job=unknown") + END).snapshot.taskBlocked);
        equal(true, parse(0, tasks.replace("busy=0", "busy=1") + END).snapshot.taskActive);
        equal(true, parse(0, tasks.replace("active_job=0", "active_job=1") + END).snapshot.taskBlocked);
        equal(true, parse(0, tasks.replace("busy=0", "busy=unknown") + END).snapshot.taskBlocked);
        equal(0L, parse(0, tasks.replace("idle_remaining=600", "idle_remaining=601") + END).snapshot.idleRemaining);
        for (String badState : new String[]{"a;id", "daily", "running\n"}) {
            try { RootPerformanceModule.recordSchedule(badState, 0); equal("rejected", "accepted"); }
            catch (IllegalArgumentException expected) { equal(true, true); }
        }
        for (long badTime : new long[]{-1L, 10000000000L}) {
            try { RootPerformanceModule.recordSchedule("scheduled", badTime); equal("rejected", "accepted"); }
            catch (IllegalArgumentException expected) { equal(true, true); }
        }
        RootPerformanceModule.recordSchedule("scheduled", 1791858000L);
        String planned = RootPerformanceModule.command(RootPerformanceModule.Action.CHECK, null);
        equal(true, planned.contains("state=scheduled\nnext_after=1791858000\n"));
        equal(true, planned.contains("plan >/dev/null 2>&1 || :"));
        equal(false, RootPerformanceModule.command(RootPerformanceModule.Action.CHECK, null).contains("plan()"));
        String diagnosticText="编译失败\nFinal Status: FAILED\nC17_PERF:installed=0";
        equal(diagnosticText, RootPerformanceModule.decodeLog(RootPerformanceModule.encodeLog(diagnosticText),2048));
        equal("",RootPerformanceModule.decodeLog("0g",2048));equal("",RootPerformanceModule.decodeLog("f",2048));
        equal("",RootPerformanceModule.decodeLog("4142",1));
        RootPerformanceModule.Snapshot logged=parse(0,modern+"C17_PERF:trace_hex="+RootPerformanceModule.encodeLog(diagnosticText)+"\n"+END).snapshot;
        equal(diagnosticText,logged.executionLog);equal(true,logged.installed);
        for (int size : new int[] {512, 513, 2048}) {
            char[] chars = new char[size]; java.util.Arrays.fill(chars, 'x');
            String value = new String(chars);
            RootPerformanceModule.Result large = parse(0, modern + "C17_PERF:trace_hex=" + RootPerformanceModule.encodeLog(value) + "\n" + END);
            equal(true, large.success); equal(value, large.snapshot.executionLog);
        }
        equal(true, RootPerformanceModule.decodeLog("e4b8", 2048).contains("日志编码不完整"));
        for (String hex : new String[] {"g0", "0", "<script>", new String(new char[4098]).replace('\0', 'a')}) {
            RootPerformanceModule.Result optional = parse(0, modern + "C17_PERF:trace_hex=" + hex + "\n" + END);
            equal(true, optional.success); equal("", optional.snapshot.executionLog);
        }
        RootPerformanceModule.Snapshot unsupported = parse(0, tasks + "C17_PERF:lock_available=0\n" + END).snapshot;
        equal(false, unsupported.lockAvailable); equal(true, unsupported.taskBlocked);
        equal(true, unsupported.webStatus().contains("lock_available=0\n"));
        equal(true, parse(0, tasks + END).snapshot.lockAvailable);
        equal(true, RootPerformanceModule.parse(28, "", RootPerformanceModule.Action.RUN).message.contains("暂不支持启动优化"));
        equal(true, RootPerformanceModule.parse(0, HEADER + END, RootPerformanceModule.Action.DISABLE).message.contains("当前优化继续"));
        String cache = "C17_PERF:cache_running=1\nC17_PERF:cache_protocol=1\nC17_PERF:cache_bytes=2097152\n"
                + "C17_PERF:cache_limit=67108864\nC17_PERF:cache_entries=8\nC17_PERF:cache_hits=20\n"
                + "C17_PERF:cache_misses=4\nC17_PERF:cache_disk_hits=3\nC17_PERF:cache_oom_adj=-600\n"
                + "C17_PERF:cache_memory_low=16777216\n";
        RootPerformanceModule.Snapshot cached = parse(0, HEADER + RECORD + cache + END).snapshot;
        equal(true,cached.cacheRunning); equal(true,cached.cacheProtected); equal(true,cached.cacheCoreProtected);
        equal(2097152L,cached.cacheBytes); equal(8L,cached.cacheEntries); equal(20L,cached.cacheHits);
        equal(true,cached.cacheMessage().contains("内存命中 17 次"));
        equal(true,cached.webStatus().contains("cache_memory_low=16777216\n"));
        equal(false,parse(0, HEADER.replace("disabled=0","disabled=1") + RECORD + cache + END).snapshot.cacheProtected);
        equal(false,parse(0, HEADER + RECORD + cache.replace("cache_protocol=1","cache_protocol=2") + END).snapshot.cacheRunning);
        equal(false,parse(0, HEADER + RECORD + cache.replace("cache_memory_low=16777216","cache_memory_low=unsupported") + END).snapshot.cacheCoreProtected);
        equal(0L,parse(0, HEADER + RECORD + cache.replace("cache_bytes=2097152","cache_bytes=999999999999") + END).snapshot.cacheBytes);
        equal(0L,parse(0, HEADER + RECORD + cache + "C17_PERF:cache_hits=99\n" + END).snapshot.cacheHits);
        equal(false,parse(0, HEADER + RECORD + END).snapshot.cacheRunning);
        String rescue="C17_PERF:rescue_enabled=1\nC17_PERF:rescue_state=waiting_unlock\nC17_PERF:rescue_error=user_locked\nC17_PERF:rescue_time=1793850000\n";
        RootPerformanceModule.Snapshot rescueReport=parse(0,modern+rescue+END).snapshot;
        equal(true,rescueReport.rescueAvailable);equal(true,rescueReport.rescueEnabled);
        equal("waiting_unlock",rescueReport.rescueState);equal(1793850000L,rescueReport.rescueTime);
        equal(true,rescueReport.rescueMessage().contains("首次解锁"));
        equal(true,rescueReport.webStatus().contains("rescue_enabled=1\n"));
        equal("ok",rescueReport.lastState);equal("none",rescueReport.error); // Recovery never replaces the ART ledger.
        equal(false,parse(0,modern+END).snapshot.rescueAvailable);
        equal(false,parse(0,modern.replace("module_version_code=14","module_version_code=11")+rescue+END).snapshot.rescueAvailable);
        RootPerformanceModule.Snapshot previousRescue=parse(0,modern.replace("module_version_code=14","module_version_code=12")+rescue+END).snapshot;
        equal(true,previousRescue.outdated);equal(true,previousRescue.rescueAvailable); // Rescue status protocol starts at 12 independently of the current module UI version.
        equal(true,parse(0,modern+END).snapshot.rescueMessage().contains("更新附属模块"));
        for(String rescueBroken:new String[]{rescue.replace("enabled=1","enabled=yes"),rescue.replace("waiting_unlock","$(bad)"),
                rescue.replace("user_locked","arbitrary_shell_text"),rescue.replace("1793850000","-1"),rescue+"C17_PERF:rescue_enabled=0\n"}) {
            RootPerformanceModule.Result parsed=parse(0,modern+rescueBroken+END);
            equal(true,parsed.success);equal(false,parsed.snapshot.rescueAvailable);equal(false,parsed.snapshot.rescueEnabled);
            equal(false,parsed.snapshot.webStatus().contains("rescue_enabled="));
        }
        equal(false,parse(0,modern+rescue.replace("enabled=1","enabled=0")+END).snapshot.rescueEnabled);
        equal(true,parse(0,modern.replace("disabled=0","disabled=1")+rescue+END).snapshot.rescueMessage().contains("模块已停用"));
        for(RootPerformanceModule.Action action:new RootPerformanceModule.Action[]{RootPerformanceModule.Action.RESCUE_ENABLE,RootPerformanceModule.Action.RESCUE_DISABLE}) {
            String operation=RootPerformanceModule.command(action,null).split("operate\\(\\) \\{ ",2)[1].split(" \\}; operate;",2)[0];
            equal(true,operation.contains("boot-rescue.sh"));equal(true,operation.contains(action==RootPerformanceModule.Action.RESCUE_ENABLE?"--enable":"--disable"));
            equal(false,operation.contains("launch "));equal(false,operation.contains("--trigger"));equal(false,operation.contains("pm clear"));
            equal(false,operation.contains("lockcheck"));
        }
        equal(true,RootPerformanceModule.parse(0,modern+rescue+END,RootPerformanceModule.Action.RESCUE_ENABLE).message.contains("没有清理数据"));
        System.out.println("RootPerformanceModuleCheck passed " + checks + " checks");
    }
}
