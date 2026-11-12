/* Local assets only. Native exec signature is verified against KernelSU's js/index.js
 * and APatch's WebViewInterface.kt. Shell output is data, never evaluated.
 * https://raw.githubusercontent.com/tiann/KernelSU/main/js/index.js
 * https://raw.githubusercontent.com/bmax121/APatch/main/app/src/main/java/me/bmax/apatch/ui/webui/WebViewInterface.kt
 */
(function () {
  "use strict";
  const COMMANDS = Object.freeze({
    read: "sh /data/adb/modules/c17_performance/webui.sh status",
    run: "sh /data/adb/modules/c17_performance/webui.sh run",
    enable: "sh /data/adb/modules/c17_performance/webui.sh enable",
    disable: "sh /data/adb/modules/c17_performance/webui.sh disable",
    "rescue-enable": "sh /data/adb/modules/c17_performance/webui.sh rescue-enable",
    "rescue-disable": "sh /data/adb/modules/c17_performance/webui.sh rescue-disable"
  });
  const EXPECTED_VERSION = 14;
  const REFRESH_MS = 4000;
  const c17 = window.c17 && typeof window.c17.readStatus === "function" && typeof window.c17.run === "function" &&
    typeof window.c17.setEnabled === "function" ? window.c17 : null;
  const ksu = window.ksu && typeof window.ksu.exec === "function" ? window.ksu : null;
  const bridge = c17 || ksu;
  const demo = !bridge && document.documentElement.dataset.readonlyDemo === "true" &&
    typeof window.C17_WEBUI_DEMO === "string";
  const fields = new Set(["installed", "pending", "disabled", "remove", "boot", "busy", "active_job", "lock_available",
    "module_version", "module_version_code", "record_present", "last_state", "last_time", "last_compiled",
    "last_packages", "last_error", "last_boot", "last_mode", "last_module_version_code", "last_reused",
    "last_verified", "last_deferred", "last_verification", "last_priority", "schedule_present",
    "schedule_scheduler", "schedule_enabled", "schedule_state", "schedule_interval_hours", "schedule_next_after",
    "schedule_kind", "last_idle_remaining", "last_diagnostic", "current_boot",
    "total_since", "total_compiles", "total_wall_ms", "total_cpu_ms", "total_size_kb", "total_reuses", "total_avoided_ms",
    "runtime_sampled", "runtime_jit_ms", "runtime_proc_s", "runtime_aot", "trace_hex", "failure_hex",
    "cache_running", "cache_protocol", "cache_bytes", "cache_limit", "cache_entries", "cache_hits", "cache_disk_hits", "cache_misses", "cache_oom_adj", "cache_memory_low",
    "rescue_enabled", "rescue_state", "rescue_error", "rescue_time"]);
  const rescueErrors = Object.freeze({none: "", path_rejected: "数据路径校验未通过，已停止。", input_unavailable: "无法读取按键设备。",
    status_write_failed: "无法保存恢复记录。", reset_failed: "系统数据未全部清理，请核对结果。", package_unavailable: "系统包管理器尚未就绪。",
    clear_unknown: "包管理器完成状态未知，未并发重复清理。", user_locked: "用户数据尚未解锁。", external_unavailable: "外部存储尚不可访问。",
    boot_gate_rejected: "已离开允许触发的开机阶段。", authorization_rejected: "没有本次按键授权。", pending_expired: "待恢复请求已过期。", unsupported: "当前系统尚不支持。"});
  const errors = Object.freeze({
    none: "", busy: "已有任务正在执行，请稍后刷新。", root_required: "未获得 Root 权限，操作未执行。",
    lock_unavailable: "系统缺少可靠的任务互斥能力，优化未启动，原运行记录已保留。",
    lock_incomplete: "本次开机存在无法确认归属的旧锁，暂不启动任务；重启后可恢复。",
    module_inactive: "模块已停用或待卸载，优化未执行。", device_hot: "电池温度达到 55℃，自动任务已延期。",
    battery_low: "电量为 15% 或更低，自动任务已延期。", device_interactive: "等待息屏或连续 10 分钟无操作。",
    power_unknown: "无法读取电池电量，自动任务已延期。", thermal_unknown: "无法读取电池温度，自动任务已延期。",
    screen_unknown: "无法确认空闲状态，自动任务已延期。", boot_timeout: "等待系统启动超时，下次启动再试。",
    boot_incomplete: "系统启动尚未完成，自动任务已延期。", idle_unknown: "无法确认无操作时长，等待息屏后再处理。",
    compile_failed: "系统未完成编译，运行记录已保留。", compile_timeout: "编译等待超时，已请求取消；请核对运行记录。",
    compile_cancel_failed: "未能确认 ART 编译任务已取消。暂不启动新任务，请在 C17 查看运行记录。",
    restore_cancel_failed: "未能确认 ART 恢复任务已取消。失败记录已保留，请在 C17 查看。",
    art_cancel_unavailable: "系统缺少可靠的 ART 取消能力，本次未开始。", timeout_unavailable: "系统缺少可靠的操作超时能力，本次未开始。",
    art_unverified: "当前 ART 结果尚未核验。", interrupted: "任务中断；已尝试取消，是否结束请查看记录。",
    restore_failed: "恢复系统编译方式未完成，失败记录已保留。", restore_timeout: "恢复等待超时，失败记录已保留。",
    no_package: "未识别到系统界面组件。", package_query_failed: "系统组件查询失败，本次未完成。",
    fingerprint_write_failed: "无法保存组件记录。", status_write_failed: "无法保存运行记录。",
    path_link_rejected: "模块路径校验失败，操作已停止。", data_path_rejected: "运行记录目录校验失败。",
    module_path_rejected: "模块安装路径校验失败。", module_id_rejected: "模块身份校验失败。",
    module_version_rejected: "模块版本记录异常。", migration_failed: "旧记录迁移未完成，记录已保留。",
    mode_rejected: "不支持此运行模式。", data_create_failed: "无法创建运行记录目录。",
    uninstall_incomplete: "卸载仍有残留记录，请在 C17 查看。", uninstall_failed: "卸载清理未完成，记录已保留。",
    cache_cleanup_failed: "缓存服务尚未安全停止或清理，记录已保留，请重试。"
  });
  let callbackCounter = 0;
  let snapshot = null;
  let reading = false;
  let acting = false;
  let unconfirmed = false;
  let timer = null;
  let readError = "";
  let actionNotice = "";
  let holdUntil = 0;
  let actionKind = "";
  const el = (id) => document.getElementById(id);
  const hidden = () => document.hidden || window.c17HostPaused === true;
  function text(id, value) { el(id).textContent = value; }
  function logText(hex, limit) {
    if (!hex || hex.length > limit * 2 || hex.length % 2 || !/^[0-9a-fA-F]+$/.test(hex)) return "";
    try { return decodeURIComponent(hex.replace(/../g, b => "%" + b)).replace(/[\x00-\x08\x0b-\x1f\x7f]/g, ""); }
    catch (_) { return "日志编码不完整，请查看后续记录"; }
  }
  function count(value, maximum) {
    if (!/^[0-9]{1,10}$/.test(value || "")) return null;
    const result = Number(value);
    return Number.isSafeInteger(result) && result <= maximum ? result : null;
  }
  function parse(raw) {
    if (typeof raw !== "string" || raw.length > 16384) throw new Error("运行记录大小异常");
    const data = Object.create(null);
    let frame = false;
    let ended = false;
    for (const original of raw.replace(/\r/g, "").split("\n")) {
      const line = original.trim();
      if (!line) continue;
      if (line === "C17_WEB:begin" || line === "C17_PERF:begin") {
        if (frame || ended) throw new Error("运行记录重复");
        frame = true;
      } else if (line === "C17_WEB:end" || line === "C17_PERF:end") {
        if (!frame || ended) throw new Error("运行记录不完整");
        frame = false; ended = true;
      } else if (frame) {
        const match = /^(?:C17_WEB:|C17_PERF:)?([a-z_]+)=(.*)$/.exec(line);
        if (!match) throw new Error("运行记录格式异常");
        if (!fields.has(match[1])) continue;
        // Logs are optional evidence: their byte limit is encoded as twice as many hex characters.
        // A broken/duplicate log must not discard the validated installation and task state.
        const logLimit = match[1] === "trace_hex" ? 4096 : match[1] === "failure_hex" ? 1024 : 0;
        if (logLimit) {
          data[match[1]] = Object.prototype.hasOwnProperty.call(data, match[1]) || match[2].length > logLimit ||
            match[2].length % 2 || !/^[0-9a-fA-F]*$/.test(match[2]) ? "" : match[2];
          continue;
        }
        if (Object.prototype.hasOwnProperty.call(data, match[1]) || match[2].length > 1024) throw new Error("运行记录字段异常");
        data[match[1]] = match[2];
      }
    }
    if (!ended || frame) throw new Error("运行记录不完整");
    for (const key of ["installed", "pending", "disabled", "remove", "record_present"]) {
      if (!/^[01]$/.test(data[key] || "")) throw new Error("模块状态无法确认");
    }
    if (!/^[0-9a-f-]{36}$/i.test(data.boot || "")) throw new Error("当前开机记录无法确认");
    if (!/^(0|1|unknown)$/.test(data.busy || "") || !/^(0|1|unknown)$/.test(data.active_job || "")) throw new Error("任务状态无法确认");
    if (data.lock_available !== undefined && !/^[01]$/.test(data.lock_available)) throw new Error("任务互斥能力无法确认");
    if (data.installed === "1" && count(data.module_version_code, 9999) === null) throw new Error("模块版本无法确认");
    if (data.record_present === "1") {
      if (!/^(ok|current|waiting|running|deferred|failed)$/.test(data.last_state || "")) throw new Error("运行状态无法确认");
      if (count(data.last_time, 9999999999) === null || count(data.last_module_version_code, 9999) === null ||
        !/^[0-9a-f-]{36}$/i.test(data.last_boot || "") || !/^(boot|run|auto|idle|scheduled|uninstall)$/.test(data.last_mode || "")) throw new Error("记录来源无法确认");
      for (const key of ["compiled", "packages", "reused", "verified", "deferred"]) {
        if (count(data["last_" + key], 9999) === null) throw new Error("运行计数无法确认");
      }
      if (!/^[a-z0-9_:.\-]{1,120}$/i.test(data.last_error || "")) throw new Error("错误记录无法确认");
    }
    return data;
  }
  function currentRecord(data) {
    return data.record_present === "1" && data.last_boot === data.boot &&
      data.last_module_version_code === data.module_version_code && Number(data.module_version_code) >= EXPECTED_VERSION;
  }
  function recoveryView(data) {
    const available = data && Number(data.module_version_code) >= 12 && /^[01]$/.test(data.rescue_enabled || "") &&
      /^(idle|watching|triggered|waiting_unlock|cleared|failed|cancelled)$/.test(data.rescue_state || "") &&
      Object.prototype.hasOwnProperty.call(rescueErrors, data.rescue_error) && count(data.rescue_time, 9999999999) !== null;
    if (!available) return {available:false, enabled:false, canToggle:false, title:"待确认", message:"系统UI防炸状态尚未确认，请更新模块后刷新。", time:"—"};
    const labels={idle:"尚无恢复记录。", watching:"正在监听按键。", triggered:"正在重置 SystemUI。",
      waiting_unlock:"等待首次解锁后补清。", cleared:"最近一次重置已完成。", failed:"最近一次恢复未完成。", cancelled:"最近一次恢复已取消。"};
    const active=data.disabled !== "1" && data.remove !== "1";
    const title=data.remove === "1" ? "待卸载" : !active ? "模块已停用" : data.pending === "1" ? "待重启" : data.rescue_enabled === "1" ? "已开启" : "已关闭";
    return {available:true, enabled:data.rescue_enabled === "1", canToggle:data.installed === "1" && data.pending !== "1" && data.remove !== "1",
      title, message:labels[data.rescue_state]+rescueErrors[data.rescue_error],time:Number(data.rescue_time)>0?"最近恢复："+timestamp(data.rescue_time):"尚无恢复时间"};
  }
  function errorMessage(code) {
    return Object.prototype.hasOwnProperty.call(errors, code) ? errors[code] : "操作未完成，请在 C17 查看详细记录（" + code + "）。";
  }
  function present(data, localBusy) {
    const current = currentRecord(data);
    const result = { title: "已安装", message: "按需优化，有效结果直接复用。", tone: "blue", canRun: true, canToggle: true, running: false };
    if (data.installed !== "1") return { title: data.pending === "1" ? "待重启" : "未安装", message: data.pending === "1" ? "重启后加载附属模块。" : "请在 C17 安装附属模块。", tone: "neutral", canRun: false, canToggle: false, running: false };
    if (data.remove === "1") return { title: "待卸载", message: "重启后处理卸载及恢复。", tone: "red", canRun: false, canToggle: false, running: false };
    if (data.lock_available === "0") return { title: "系统暂不支持", message: "暂时无法启动优化，详见记录。", tone: "amber", canRun: false, canToggle: false, running: false };
    const uncertain = data.busy === "unknown" || data.active_job === "unknown";
    const live = localBusy || data.busy === "1";
    const unresolved = current && /^(waiting|running)$/.test(data.last_state) && !live;
    // A recorded cancel failure only holds while its task marker still exists; the module drops an unknown one after 15 minutes.
    const cancelled = current && data.active_job !== "0" && /^(compile_cancel_failed|restore_cancel_failed)$/.test(data.last_error || "");
    if (uncertain) return { title: "无法确认", message: "请刷新后重试。", tone: "amber", canRun: false, canToggle: false, running: false };
    if (cancelled || data.active_job === "1" && !live) return { title: "取消未确认", message: "未能确认系统任务已经结束。", tone: "red", canRun: false, canToggle: false, running: false };
    if (data.disabled === "1") return { title: "已停用", message: live ? "当前任务仍需确认结束；停用阻止后续自动处理。" : "自动处理已停用。", tone: "amber", canRun: false,
      canToggle: !live && data.pending !== "1" && Number(data.module_version_code) >= EXPECTED_VERSION, running: live };
    if (data.pending === "1") return { title: "待重启", message: "重启后加载新版本。", tone: "cyan", canRun: false, canToggle: false, running: false };
    if (Number(data.module_version_code) < EXPECTED_VERSION) return { title: "待更新", message: "请在 C17 更新附属模块。", tone: "amber", canRun: false, canToggle: false, running: false };
    if (live) return { title: current && data.last_state === "waiting" && data.last_mode !== "run" ? "等待空闲" : "优化中", message: "稍后自动更新。", tone: "cyan", canRun: false, canToggle: false, running: true };
    if (unresolved) return { title: "结果待确认", message: "请刷新后查看结果。", tone: "amber", canRun: false, canToggle: false, running: false };
    if (!current && data.record_present === "1") result.message = "上次记录供参考，当前尚未检查。";
    if (current && data.last_state === "ok") { result.title = "优化完成"; result.message = "无需重启系统界面。"; result.tone = "green"; }
    if (current && data.last_state === "current") { result.title = "无需编译"; result.message = "有效结果已复用。"; result.tone = "green"; }
    if (current && data.last_state === "deferred") { result.title = "已延期"; result.message = data.last_mode === "run" ? "本次未执行，请查看原因。" : "空闲条件满足后再检查。"; result.tone = "amber"; }
    if (current && data.last_state === "deferred" && /^(art_cancel_unavailable|timeout_unavailable)$/.test(data.last_error || "")) {
      result.title = "编译未开始"; result.message = errorMessage(data.last_error);
    }
    if (current && data.last_state === "failed") { result.title = "未完成"; result.message = "运行记录已保留，请查看原因。"; result.tone = "red"; }
    return result;
  }
  function nativeExec(kind) {
    return new Promise((resolve, reject) => {
      const name = "C17WebCallback" + (++callbackCounter);
      const timeout = window.setTimeout(() => {
        delete window[name];
        reject(new Error("管理器响应超时；这不代表系统任务已经取消"));
      }, c17 ? 50000 : 12000);
      window[name] = (errno, stdout, stderr) => {
        window.clearTimeout(timeout); delete window[name];
        const code = Number(errno);
        if (!Number.isInteger(code) || typeof stdout !== "string" || typeof stderr !== "string") reject(new Error("管理器返回格式异常"));
        else resolve({ code, stdout, stderr });
      };
      try {
        if (c17) {
          if (kind === "read") c17.readStatus(name);
          else if (kind === "run") c17.run(name);
          else if (kind === "rescue-enable" || kind === "rescue-disable") c17.setRecoveryEnabled(kind === "rescue-enable", name);
          else c17.setEnabled(kind === "enable", name);
        } else ksu.exec(COMMANDS[kind], "{}", name);
      }
      catch (_) { window.clearTimeout(timeout); delete window[name]; reject(new Error("管理器执行接口不可用")); }
    });
  }
  function timestamp(value) {
    const seconds = count(value, 9999999999);
    if (!seconds) return "暂无时间";
    return new Date(seconds * 1000).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false });
  }
  function scheduleText(data) {
    if (data.schedule_kind === "on_demand") {
      if (data.schedule_state === "scheduled") return "已登记检查计划 · 系统可能延后";
      if (data.schedule_state === "inactive") return "暂无待处理任务";
      if (data.schedule_state === "unavailable") return "自动检查不可用，请在 C17 查看";
      if (data.schedule_state === "running") return "正在确认自动任务";
      if (data.schedule_state === "deferred") return "本轮已延期，等待下次空闲事件";
      if (data.schedule_state === "blocked") return "任务结果未确认，本次开机暂停自动检查";
      return "检查计划尚未确认";
    }
    if (data.schedule_present !== "1") return "未确认自动检查，请在 C17 查看";
    if (data.schedule_enabled === "0" || data.schedule_state === "disabled") return "自动检查已停用";
    if (data.schedule_state === "registered") return "已安排 · 等待空闲";
    if (data.schedule_state === "unavailable") return "自动检查不可用，请在 C17 查看";
    return "自动检查状态待确认";
  }
  const RING_LENGTH = 213.6;
  // Pure views over a parsed snapshot, exported for the local fixtures.
  function ringView(data, current) {
    const verified = count(data.last_verified, 9999), packages = count(data.last_packages, 9999);
    const known = data.record_present === "1" && verified !== null && packages !== null && packages > 0;
    const share = known ? Math.min(1, verified / packages) : 0;
    return { dash: (share * RING_LENGTH).toFixed(1) + " " + RING_LENGTH, opacity: known && !current ? "0.55" : "1", label: known ? verified + "/" + packages : "—" };
  }
  function seconds(ms) { return (ms / 1000).toFixed(ms >= 10000 ? 0 : 1) + " 秒"; }
  // Costs are what ART reported for each compile; "avoided" is an estimate; the runtime lines are one sample, not a comparison.
  function benefitView(data) {
    const compiles = count(data.total_compiles, 9999999), reuses = count(data.total_reuses, 9999999);
    const wall = count(data.total_wall_ms, 99999999999999), saved = count(data.total_avoided_ms, 99999999999999);
    const sizeKb = count(data.total_size_kb, 99999999999), since = count(data.total_since, 9999999999);
    const ledger = since !== null && since > 0;
    const sampled = count(data.runtime_sampled, 9999999999), age = count(data.runtime_proc_s, 99999999);
    const jit = count(data.runtime_jit_ms, 99999999999999);
    const haveSample = sampled !== null && sampled > 0 && age !== null && age > 0;
    const aot = data.runtime_aot === "1";
    const longEnough = haveSample && jit !== null && age >= 60;
    return {
      since: ledger ? "自 " + timestamp(data.total_since) + " 起" : "尚无记录",
      compiles: ledger ? (compiles || 0) + " 次" : "—",
      compilesNote: ledger ? "耗时 " + seconds(wall || 0) + (sizeKb ? " · 新增产物 " + Math.round(sizeKb / 1024) + " MB" : "") : "ART 实测耗时",
      saved: ledger ? (reuses || 0) + " 次" : "—",
      savedNote: ledger ? "约省编译 " + seconds(saved || 0) + "（估算）" : "相比每次都重编",
      aot: haveSample ? (aot ? "已使用" : "未使用") : "—",
      aotNote: haveSample ? (aot ? "系统界面运行的是预编译产物" : "系统界面尚在解释执行") : "是否使用预编译产物",
      jit: longEnough ? Math.round(jit / (age / 60)) + " ms/分" : "—",
      jitNote: longEnough ? "JIT 线程占用 · 进程已运行 " + Math.round(age / 60) + " 分钟" : haveSample ? "进程刚启动，样本太短" : "JIT 线程占用"
    };
  }
  function renderRing(current) {
    const view = ringView(snapshot, current);
    el("ring-arc").setAttribute("stroke-dasharray", view.dash);
    el("ring-arc").setAttribute("opacity", view.opacity);
    text("ring-text", view.label);
  }
  function renderBenefits() {
    const view = benefitView(snapshot);
    text("benefit-since", view.since);
    text("benefit-compiles", view.compiles); text("benefit-compiles-note", view.compilesNote);
    text("benefit-saved", view.saved); text("benefit-saved-note", view.savedNote);
    text("benefit-aot", view.aot); text("benefit-aot-note", view.aotNote);
    text("benefit-jit", view.jit); text("benefit-jit-note", view.jitNote);
  }
  function render() {
    const rescue=recoveryView(snapshot);
    const rescueBridge=!!ksu || c17 && typeof c17.setRecoveryEnabled === "function";
    text("rescue-toggle-title", readError ? "待确认" : rescue.title);
    text("rescue-toggle-note", rescue.available ? (rescue.enabled ? "系统UI防炸已开启。" : "系统UI防炸已关闭。") : "请更新模块后刷新。");
    text("rescue-result", rescue.message);text("rescue-time",rescue.time);
    el("rescue-toggle").setAttribute("aria-checked", String(!readError && rescue.enabled));
    el("rescue-toggle").setAttribute("aria-label", "系统UI防炸，" + (readError ? "待确认" : rescue.title));
    el("rescue-toggle").disabled = !rescueBridge || !rescue.canToggle || !!readError || reading || acting || unconfirmed || Date.now()<holdUntil;
    const cacheReady = snapshot && !readError && snapshot.cache_running === "1" && snapshot.cache_protocol === "1" && snapshot.disabled !== "1" && snapshot.remove !== "1";
    text("cache-title", readError || !snapshot ? "待确认" : snapshot.disabled === "1" || snapshot.remove === "1" ? "已停用" : cacheReady ? "运行中" : "未连接");
    text("cache-detail", cacheReady ? (count(snapshot.cache_entries, 256) || 0) + " 项 · " +
      ((count(snapshot.cache_bytes, 67108864) || 0) / 1048576).toFixed(1) + " / 64 MB · 命中 " +
      (count(snapshot.cache_hits, Number.MAX_SAFE_INTEGER) || 0) + " 次，其中磁盘恢复 " +
      (count(snapshot.cache_disk_hits, Number.MAX_SAFE_INTEGER) || 0) + " 次。" +
      (snapshot.cache_oom_adj === "-600" ? "已提高缓存保留优先级。" : "使用系统默认回收优先级。") +
      (snapshot.cache_memory_low === "16777216" ? "内核已启用 16 MB 核心缓存保护。" : "当前未启用内核核心缓存保护。")
      : "使用本地资源回退；缓存状态与编译状态分别核对。");
    const connected = !!bridge || demo;
    const holding = Date.now() < holdUntil;
    let view = snapshot ? present(snapshot, false) : { title: "正在读取", message: "正在连接管理器…", tone: "neutral", canRun: false, canToggle: false, running: false };
    if (snapshot && !view.running && view.canToggle && (acting || unconfirmed || holding)) {
      view = { title: unconfirmed ? "等待确认" : acting ? actionKind === "run" ? "提交中" : "处理中" : "已提交",
        message: unconfirmed ? "尚未确认任务结果，请刷新后查看。" : "正在确认新的运行记录。", tone: "cyan", canRun: false, canToggle: false, running: false };
    }
    if (!connected) view = { title: "请在 C17 查看", message: "从 C17 或 Root 管理器打开。", tone: "neutral", canRun: false, canToggle: false, running: false };
    else if (readError) view = { title: "无法确认", message: "读取失败，请刷新重试。", tone: "amber", canRun: false, canToggle: false, running: false };
    text("status-title", view.title); text("status-message", view.message);
    el("status-card").dataset.tone = view.tone;
    el("status-card").dataset.running = String(view.running && !hidden());
    el("optimize").disabled = !bridge || !view.canRun || reading || acting || unconfirmed || holding;
    el("module-toggle").disabled = !bridge || !view.canToggle || reading || acting || unconfirmed || holding;
    el("refresh").disabled = !connected || reading;
    text("optimize-title", acting || unconfirmed ? "正在确认…" : view.running ? "优化中" : "立即优化");
    el("demo-notice").hidden = !demo;
    const notice = actionNotice || (reading ? "正在刷新…" : "");
    text("action-hint", notice); el("action-hint").hidden = !notice;
    const diagnoses = { uuid_missing: "系统未提供可跟踪的任务编号。", cancel_pending: "已请求取消，系统仍未确认退出。", cancel_unrecognized: "系统取消返回无法核验。", final_unrecognized: "系统最终返回无法核验。", active_unknown: "存在未确认的任务记录。", client_failed: "系统包管理器暂时没有应答，已自动重试。" };
    const reason = readError || (snapshot && snapshot.record_present === "1" && snapshot.last_error !== "none" ? errorMessage(snapshot.last_error) + " " + (diagnoses[snapshot.last_diagnostic] || "") : "");
    text("reason", reason); el("reason").hidden = !reason;
    text("execution-log", snapshot ? logText(snapshot.trace_hex, 2048) || "尚无记录" : "尚无记录");
    text("failure-log", snapshot ? logText(snapshot.failure_hex, 512) || "尚无失败记录" : "尚无失败记录");
    if (!snapshot) return;
    text("version", snapshot.module_version ? "v" + snapshot.module_version : "—");
    const hasRecord = snapshot.record_present === "1";
    const current = currentRecord(snapshot);
    text("source", hasRecord ? (snapshot.last_mode === "run" ? "手动优化" : snapshot.last_mode === "boot" ? "开机自动" : snapshot.last_mode === "uninstall" ? "卸载恢复" : "空闲自动") + (current ? "" : " · 历史记录") : "尚无运行记录");
    text("time", hasRecord ? timestamp(snapshot.last_time) : "—");
    for (const key of ["compiled", "reused", "deferred"]) text(key, hasRecord ? snapshot["last_" + key] : "—");
    text("verified", hasRecord ? snapshot.last_verified + "/" + snapshot.last_packages : "—");
    renderRing(current);
    renderBenefits();
    text("module-toggle-title", snapshot.disabled === "1" ? "启用模块" : "停用模块");
    text("module-toggle-note", snapshot.disabled === "1" ? "恢复后续处理" : "暂停后续处理");
    const plans = { scheduled: "计划待空闲", registered: "计划待空闲", inactive: "暂无待处理", disabled: "已停用", unavailable: "暂不可用", deferred: "等待下次空闲", blocked: "暂停新任务", running: "确认中" };
    text("auto-title", snapshot.disabled === "1" ? "已停用" : plans[snapshot.schedule_state] || "状态待确认");
    text("schedule", scheduleText(snapshot));
    text("auto-tag", snapshot.disabled === "1" ? "已停用" : plans[snapshot.schedule_state] || "待确认");
    text("record-validity", !hasRecord ? "尚无记录" : current ? "当前开机 · 当前模块版本" : "历史记录，不能证明当前任务");
    const verified = Number(snapshot.last_verified || 0);
    const packages = Number(snapshot.last_packages || 0);
    text("verification", !hasRecord || verified === 0 ? "尚未核验" : verified === packages && packages > 0 && snapshot.last_verification === "verified" ? "记录中全部组件已核验" : "仅部分组件已核验");
    text("priority", snapshot.last_priority === "background" ? "系统 ART 后台优先级" : hasRecord ? "系统兼容模式" : "—");
  }
  function scheduleRefresh() {
    if (timer) window.clearTimeout(timer);
    timer = null;
    const changing = acting || unconfirmed || snapshot && snapshot.busy === "1";
    if (bridge && !hidden()) timer = window.setTimeout(() => refresh(false), changing ? REFRESH_MS : 15000);
  }
  async function refresh(manual) {
    if (reading || hidden()) return;
    reading = true; render();
    try {
      if (demo) snapshot = parse(window.C17_WEBUI_DEMO);
      else if (bridge) {
        const result = await nativeExec("read");
        if (result.code !== 0) throw new Error(result.code === 21 ? "未获得 Root 权限" : "状态读取失败（" + result.code + "），请在 C17 查看");
        snapshot = parse(result.stdout);
      }
      readError = "";
      if (manual && snapshot && unconfirmed && snapshot.busy === "0" && snapshot.active_job === "0" && Date.now() >= holdUntil) unconfirmed = false;
      if (manual && !acting) actionNotice = "状态已刷新";
    text("connection", demo ? "只读示例 · 未连接手机" : bridge ? "刚刚更新 · 离开页面停止刷新" : "当前管理器未提供执行接口");
    } catch (error) { readError = error.message; }
    finally { reading = false; render(); scheduleRefresh(); }
  }
  async function act(kind) {
    if (!bridge || !snapshot || reading || acting || unconfirmed || Date.now() < holdUntil || hidden()) return;
    const view = present(snapshot, false);
    const rescueAction = kind === "rescue-enable" || kind === "rescue-disable";
    if (rescueAction ? !recoveryView(snapshot).canToggle || c17 && typeof c17.setRecoveryEnabled !== "function" : kind === "run" ? !view.canRun : !view.canToggle) return;
    acting = true; actionKind = kind; actionNotice = kind === "run" ? "正在提交优化任务…" : "正在更新模块状态…"; render();
    try {
      holdUntil = Date.now() + 8000;
      const result = await nativeExec(kind);
      if (result.code !== 0) throw new Error(result.code === 28 ? errorMessage("lock_unavailable") : result.code === 29 ? errorMessage("lock_incomplete") : "操作未完成（" + result.code + "），请查看运行记录");
      actionNotice = rescueAction ? "系统UI防炸开关已提交" : kind === "run" ? "任务已提交，等待结果" : "操作已提交，等待确认";
    } catch (error) {
      actionNotice = error.message; unconfirmed = true;
    } finally { acting = false; await refresh(false); render(); }
  }
  el("refresh").addEventListener("click", () => refresh(true));
  el("optimize").addEventListener("click", () => act("run"));
  el("module-toggle").addEventListener("click", () => act(snapshot && snapshot.disabled === "1" ? "enable" : "disable"));
  el("rescue-toggle").addEventListener("click", () => act(snapshot && snapshot.rescue_enabled === "1" ? "rescue-disable" : "rescue-enable"));
  document.addEventListener("visibilitychange", () => {
    document.querySelector(".page").classList.toggle("paused", hidden());
    if (hidden()) { if (timer) window.clearTimeout(timer); timer = null; render(); }
    else refresh(false);
  });
  window.addEventListener("pagehide", () => { if (timer) window.clearTimeout(timer); timer = null; });
  window.addEventListener("pageshow", () => { if (!hidden() && bridge) refresh(false); });
  // Pure, read-only helpers allow deterministic local fixtures without a Root bridge.
  window.C17WebUI = Object.freeze({ parse, present, currentRecord, ringView, benefitView, logText, recoveryView });
  if (bridge || demo) refresh(false); else render();
})();
