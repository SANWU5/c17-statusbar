#!/system/bin/sh
# Never source status or any app-owned file. All paths are fixed; package names come only from the package manager.
umask 077
MODDIR=${0%/*}
DATA=/data/adb/c17-performance
MODE=$1
STATE=ok
ERROR=none
COMPILED=0
PACKAGES=0
REUSED=0
VERIFIED=0
DEFERRED=0
VERIFICATION=unverified
PRIORITY=compatibility
IDLE_REMAINING=0
DIAGNOSTIC=none
LOCKED=0
GUARDED=0
FLOCK_KIND=
FLOCK_BIN=
ACTIVE_PID=
ACTIVE_JOB=
ACTIVE_LOG=
TIMED=0
COMPILE_CAPABILITY_ERROR=
BOOT=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
MODVERSION=
MODCODE=
BUILD_ID=
LAST_STATUS=

# Bounded plain-text evidence. No device identifiers, notifications or user data.
trace() {
  [ "$LOCKED" = 1 ] || return 0
  trace_text="$(date +%s) $MODE $*"
  if ordinary "$DATA/trace" && owned "$DATA/trace"; then
    trace_text="$(tail -n 23 "$DATA/trace")
$trace_text"
  fi
  write_file trace "$(printf '%s\n' "$trace_text" | tail -c 2048)"
}

fail() { STATE=failed; ERROR=$1; }
no_links() {
  path=$1
  while [ "$path" != / ] && [ -n "$path" ]; do
    [ ! -L "$path" ] || return 1
    path=${path%/*}
    [ -n "$path" ] || path=/
  done
}
safe_dir() { [ -d "$1" ] && no_links "$1"; }
ordinary() { [ -f "$1" ] && [ ! -L "$1" ]; }
owned() {
  [ ! -L "$1" ] && [ "$(stat -c %u "$1")" = 0 ] || return 1
  permission=$(stat -c %a "$1") || return 1
  case "$permission" in ''|*[!0-7]*|?????*) return 1;; esac
  # Portable on Android mksh and manager shells: group/other digits 2/3/6/7 allow writes.
  case "$permission" in *[2367][0-7]|*[2367]) return 1;; esac
  return 0
}
unlock() {
  if [ "$LOCKED" = 1 ]; then
    rm -f "$DATA/.lock/pid" "$DATA/.lock/boot" "$DATA/.lock/start" "$DATA/.lock/generation"
    rmdir "$DATA/.lock" 2>/dev/null
    LOCKED=0
  fi
}
cleanup() {
  # A Binder client dying does NOT cancel dex2oat in system_server. Cancel only our own ART job.
  if [ -n "$ACTIVE_JOB" ]; then
    cancel_active && rm -f "$DATA/active-job"
  elif [ -n "$ACTIVE_PID" ] && kill -0 "$ACTIVE_PID" 2>/dev/null; then
    write_file active-job "$(printf '%s\nunknown\n' "$BOOT")"
  fi
  if [ -n "$ACTIVE_PID" ]; then
    terminate_client
    kill "$ACTIVE_PID" 2>/dev/null
  fi
  [ -z "$ACTIVE_LOG" ] || rm -f "$ACTIVE_LOG"
  ACTIVE_PID=; ACTIVE_JOB=; ACTIVE_LOG=
  unlock
  if [ "$GUARDED" = 1 ]; then flock_call -u 9 2>/dev/null; exec 9<&-; GUARDED=0; fi
}
terminate_client() {
  [ -n "$ACTIVE_LOG" ] && ordinary "$ACTIVE_LOG" && owned "$ACTIVE_LOG" || return 0
  identity=$(sed -n 's/^C17_ART_CLIENT=\([0-9][0-9]*:[0-9][0-9]*\)$/\1/p' "$ACTIVE_LOG" | head -n 1)
  case "$identity" in *:*) client=${identity%:*}; birth=${identity#*:};; *) return 0;; esac
  [ "$client" -gt 1 ] && [ "$birth" != 0 ] || return 0
  # This is our exec'd Binder client, not system_server/dex2oat. Guard against PID reuse.
  for signal in TERM KILL; do
    [ "$(awk '{print $22}' "/proc/$client/stat" 2>/dev/null)" = "$birth" ] || break
    kill -"$signal" "$client" 2>/dev/null || :
  done
}
flock_call() {
  # Android mksh closes persistent shell descriptors on exec unless explicitly
  # inherited. This applies to probes, acquisition and release alike.
  case "$FLOCK_KIND" in
    command) flock "$@" 9<&9;;
    toybox) /system/bin/toybox flock "$@" 9<&9;;
    busybox) "$FLOCK_BIN" flock "$@" 9<&9;;
    *) return 2;;
  esac
}
flock_probe() { (exec 9<"$MODDIR"; flock_call -s -n 9) 2>/dev/null; }
select_flock() {
  FLOCK_KIND=command
  command -v flock >/dev/null 2>&1 && flock_probe && return 0
  FLOCK_KIND=toybox
  [ -x /system/bin/toybox ] && flock_probe && return 0
  FLOCK_KIND=busybox
  for FLOCK_BIN in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox; do
    [ -x "$FLOCK_BIN" ] && no_links "$FLOCK_BIN" && owned "$FLOCK_BIN" && flock_probe && return 0
  done
  FLOCK_KIND=; return 1
}
process_start() {
  case "$1" in ''|*[!0-9]*) return 1;; esac
  process_stat=$(cat "/proc/$1/stat" 2>/dev/null) || return 1
  process_stat=${process_stat##*) }
  set -- $process_stat
  [ "$#" -ge 20 ] || return 1
  shift 19
  case "$1" in ''|*[!0-9]*) return 1;; esac
  printf '%s\n' "$1"
}
old_lock_boot() {
  # Legacy empty locks have no identity. Reclaim only evidence predating this boot,
  # with a margin for second-resolution timestamps; current-boot ambiguity stays blocked.
  read -r uptime_seconds ignored < /proc/uptime || return 1
  uptime_seconds=${uptime_seconds%%.*}
  lock_modified=$(stat -c %Y "$DATA/.lock") || return 1
  lock_now=$(date +%s) || return 1
  case "$uptime_seconds:$lock_modified:$lock_now" in *[!0-9:]*|:*|*::*) return 1;; esac
  [ "$lock_modified" -lt "$((lock_now - uptime_seconds - 2))" ]
}
lock_state() {
  # Called only while holding the kernel directory lock. A competing v10 initializer
  # cannot publish/remove any marker here. No active-job record is changed by recovery.
  LOCK_STATE=free
  [ -e "$DATA/.lock" ] || [ -L "$DATA/.lock" ] || return
  LOCK_STATE=unknown
  safe_dir "$DATA/.lock" && owned "$DATA/.lock" || return
  previous=; previous_boot=; previous_start=; generation=
  ordinary "$DATA/.lock/boot" && owned "$DATA/.lock/boot" && previous_boot=$(cat "$DATA/.lock/boot")
  if [ -n "$previous_boot" ]; then
    printf '%s\n' "$previous_boot" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' || return
  fi
  if [ -n "$previous_boot" ] && [ "$previous_boot" != "$BOOT" ]; then LOCK_STATE=stale; return; fi
  if [ -z "$previous_boot" ]; then old_lock_boot && { LOCK_STATE=stale; return; }; fi
  ordinary "$DATA/.lock/pid" && owned "$DATA/.lock/pid" && previous=$(cat "$DATA/.lock/pid")
  ordinary "$DATA/.lock/start" && owned "$DATA/.lock/start" && previous_start=$(cat "$DATA/.lock/start")
  case "$previous_start" in *[!0-9]*) return;; esac
  ordinary "$DATA/.lock/generation" && owned "$DATA/.lock/generation" && generation=$(cat "$DATA/.lock/generation")
  case "$previous" in
    ''|*[!0-9]*) [ "$generation" != 10 ] || LOCK_STATE=stale;;
    *)
      if ! kill -0 "$previous" 2>/dev/null; then LOCK_STATE=stale
      else
        current_start=$(process_start "$previous")
        if [ -n "$previous_start" ] && [ -n "$current_start" ] && [ "$previous_start" != "$current_start" ]; then LOCK_STATE=stale
        else LOCK_STATE=live; fi
      fi;;
  esac
}
guard_lock() {
  exec 9<"$DATA" || return 1
  flock_call -n 9 || { exec 9<&-; return 1; }
  # An uninstall/recreate may have happened while this descriptor was being opened.
  [ "$DATA" -ef "/proc/$$/fd/9" ] || { flock_call -u 9; exec 9<&-; return 1; }
  GUARDED=1
}
take_lock() {
  attempt=0
  while ! mkdir "$DATA/.lock" 2>/dev/null; do
    lock_state
    if [ "$LOCK_STATE" = stale ]; then
      rm -f "$DATA/.lock/pid" "$DATA/.lock/boot" "$DATA/.lock/start" "$DATA/.lock/generation" || return 1
      rmdir "$DATA/.lock" 2>/dev/null || return 1
    elif [ "$LOCK_STATE" = unknown ]; then
      ERROR=lock_incomplete; return 1
    else
      attempt=$((attempt + 1))
      [ "$attempt" -lt 20 ] || return 1
      sleep 0.5
    fi
  done
  LOCKED=1
  printf '10\n' > "$DATA/.lock/generation" || return 1
  printf '%s\n' "$$" > "$DATA/.lock/pid" || return 1
  printf '%s\n' "$BOOT" > "$DATA/.lock/boot" || return 1
  process_start "$$" > "$DATA/.lock/start" || return 1
}
valid_package() { case "$1" in ''|.*|*[!a-zA-Z0-9._]*) return 1;; esac; }
# SystemUI and the OEM control-centre plugin, found by its install path; nothing else is ever compiled.
# This app is deliberately absent: LSPosed hands SystemUI the module dex from shared memory, so an odex compiled for
# the APK is never mapped there (checked on device), while the app's own start-up gained only ~14% for 3x the storage.
packages() {
  listing=$(timeout -k 2 12 cmd package list packages -f 2>/dev/null) || return 1
  printf '%s\n' "$listing" | grep -q '^package:' || return 1
  printf 'com.android.systemui\n'
  printf '%s\n' "$listing" | while IFS= read -r line; do
    case "$line" in package:*/SystemUIPlugin/*=*) package=${line##*=}; valid_package "$package" && printf '%s\n' "$package";; esac
  done
}
# Per-package identities prevent retrying a successful package when its neighbour failed.
# The APK containing LSPosed callbacks is not compiled here; updating it does not invalidate native ART code.
fingerprint() {
  fp_build=$BUILD_ID
  [ -n "$fp_build" ] || return 1
  fp_paths=$(timeout -k 2 12 cmd package path "$1" 2>/dev/null) || return 1
  [ -n "$fp_paths" ] || return 1
  fp_info=$(timeout -k 2 12 dumpsys package "$1" 2>/dev/null) || return 1
  printf '%s\n' "$fp_info" | grep -q 'versionCode=' || return 1
  printf 'build=%s\n' "$fp_build"
  # Script upgrades do not invalidate unchanged native APKs.
  printf '%s %s\n' "$1" "$(printf '%s\n' "$fp_paths" | tr '\n' ' ')"
  printf '%s\n' "$fp_info" | grep -m2 -E 'versionCode=|lastUpdateTime=' | tr -s ' \n' '  '
  printf '\n'
}
write_file() {
  # Data only, never shell source. A new file prevents following a planted link.
  temp=$(mktemp "$DATA/.write.XXXXXX") || return 1
  printf '%s\n' "$2" > "$temp" || { rm -f "$temp"; return 1; }
  [ ! -L "$DATA/$1" ] || { rm -f "$temp"; return 1; }
  mv -f "$temp" "$DATA/$1"
}
# Benefit ledger: plain counters this script writes and the app reads as data, never sources.
# Cost is what ART itself reports for each compile; "avoided" is what a naive module that recompiled every
# check would have spent again, estimated from that package's last measured compile time.
LEDGER_DIRTY=0; OP_WALL=0; OP_CPU=0; OP_KB=0
L_SINCE=0; L_COMPILES=0; L_WALL=0; L_CPU=0; L_KB=0; L_REUSES=0; L_AVOIDED=0
digits() { case "$1" in ''|*[!0-9]*|???????????????*) echo 0;; *) echo "$1";; esac; }
ledger_value() { ordinary "$DATA/ledger" && digits "$(sed -n "s/^$1=//p" "$DATA/ledger" | head -n 1)" || echo 0; }
ledger_load() {
  L_SINCE=$(ledger_value since); L_COMPILES=$(ledger_value compiles); L_WALL=$(ledger_value wall_ms); L_CPU=$(ledger_value cpu_ms)
  L_KB=$(ledger_value size_kb); L_REUSES=$(ledger_value reuses); L_AVOIDED=$(ledger_value avoided_ms)
}
ledger_save() {
  [ "$L_SINCE" != 0 ] || L_SINCE=$(date +%s)
  write_file ledger "$(printf 'since=%s\ncompiles=%s\nwall_ms=%s\ncpu_ms=%s\nsize_kb=%s\nreuses=%s\navoided_ms=%s\n' \
    "$L_SINCE" "$L_COMPILES" "$L_WALL" "$L_CPU" "$L_KB" "$L_REUSES" "$L_AVOIDED")"
}
ledger_reuse() {
  L_REUSES=$((L_REUSES + 1)); LEDGER_DIRTY=1
  ordinary "$DATA/cost.$1" && L_AVOIDED=$((L_AVOIDED + $(digits "$(head -c 14 "$DATA/cost.$1")")))
  return 0
}
ledger_compile() {
  L_COMPILES=$((L_COMPILES + 1)); L_WALL=$((L_WALL + OP_WALL)); L_CPU=$((L_CPU + OP_CPU)); L_KB=$((L_KB + OP_KB)); LEDGER_DIRTY=1
  [ "$OP_WALL" -gt 0 ] && write_file "cost.$1" "$OP_WALL"
  return 0
}
# ART's per-file result line carries the compile cost; sum every dex container of the package.
read_cost() {
  OP_WALL=0; OP_CPU=0; OP_KB=0
  for v in $(sed -n 's/.*dex2oatWallTimeMillis=\([0-9]*\).*/\1/p' "$1"); do OP_WALL=$((OP_WALL + $(digits "$v"))); done
  for v in $(sed -n 's/.*dex2oatCpuTimeMillis=\([0-9]*\).*/\1/p' "$1"); do OP_CPU=$((OP_CPU + $(digits "$v"))); done
  size=0; before=0
  for v in $(sed -n 's/.*[ ,{]sizeBytes=\([0-9]*\).*/\1/p' "$1"); do size=$((size + $(digits "$v"))); done
  for v in $(sed -n 's/.*sizeBeforeBytes=\([0-9]*\).*/\1/p' "$1"); do before=$((before + $(digits "$v"))); done
  [ "$size" -le "$before" ] || OP_KB=$(((size - before) / 1024))
}
# Last runtime sample of SystemUI: JIT thread CPU, process age and whether it runs from its compiled artifact.
sample_runtime() {
  pid=$(pidof com.android.systemui 2>/dev/null | cut -d' ' -f1)
  case "$pid" in ''|*[!0-9]*) return 0;; esac
  [ -r "/proc/$pid/stat" ] || return 0
  sample_now=$(date +%s)
  if ordinary "$DATA/runtime" && owned "$DATA/runtime"; then
    old_sample=$(sed -n 's/^sampled=//p' "$DATA/runtime")
    old_pid=$(sed -n 's/^pid=//p' "$DATA/runtime")
    old_boot=$(sed -n 's/^boot=//p' "$DATA/runtime")
    old_sample=$(digits "$old_sample")
    if [ "$old_pid" = "$pid" ] && [ "$old_boot" = "$BOOT" ] && [ "$sample_now" -ge "$old_sample" ] && [ $((sample_now - old_sample)) -lt 60 ]; then
      trace runtime_sample=reused; return 0
    fi
  fi
  ticks=0
  for task in /proc/$pid/task/*; do
    name=$(cat "$task/comm" 2>/dev/null) || continue
    case "$name" in 'Jit thread pool'*)
      line=$(cat "$task/stat" 2>/dev/null) || continue
      rest=${line##*) }; set -- $rest; ticks=$((ticks + $(digits "${12}") + $(digits "${13}")));;
    esac
  done
  line=$(cat "/proc/$pid/stat" 2>/dev/null) || return 0
  rest=${line##*) }; set -- $rest; started=$(digits "${20}")
  up=$(cut -d' ' -f1 /proc/uptime 2>/dev/null); up=$(digits "${up%%.*}")
  [ "$up" -gt 0 ] || return 0
  age=$((up - started / 100)); [ "$age" -gt 0 ] || return 0
  aot=0; grep -Eq 'SystemUI.*(@classes\.dex|\.odex|\.oat)' "/proc/$pid/maps" 2>/dev/null && aot=1
  write_file runtime "$(printf 'sampled=%s\njit_ms=%s\nproc_s=%s\naot=%s\npid=%s\nboot=%s\n' "$sample_now" "$((ticks * 10))" "$age" "$aot" "$pid" "$BOOT")"
  return 0
}
status() {
  if [ "$LEDGER_DIRTY" = 1 ]; then ledger_save && LEDGER_DIRTY=0; fi
  status_key="$STATE/$ERROR/$COMPILED/$PACKAGES/$REUSED/$VERIFIED/$DEFERRED/$IDLE_REMAINING/$DIAGNOSTIC/$PRIORITY"
  if [ "$LAST_STATUS" = "$status_key" ]; then cat "$DATA/status"; return; fi
  LAST_STATUS=$status_key
  trace "state=$STATE error=$ERROR compiled=$COMPILED reused=$REUSED verified=$VERIFIED deferred=$DEFERRED diagnostic=$DIAGNOSTIC"
  stamp=$(date +%s)
  if [ "$PACKAGES" -gt 0 ] && [ "$VERIFIED" = "$PACKAGES" ]; then VERIFICATION=verified
  elif [ "$VERIFIED" -gt 0 ]; then VERIFICATION=partial
  else VERIFICATION=unverified; fi
  write_file status "$(printf 'state=%s\ntime=%s\ncompiled=%s\npackages=%s\nerror=%s\nboot=%s\nmode=%s\nmodule_version_code=%s\nreused=%s\nverified=%s\ndeferred=%s\nverification=%s\npriority=%s\nidle_remaining=%s\ndiagnostic=%s\n' "$STATE" "$stamp" "$COMPILED" "$PACKAGES" "$ERROR" "$BOOT" "$MODE" "$MODCODE" "$REUSED" "$VERIFIED" "$DEFERRED" "$VERIFICATION" "$PRIORITY" "$IDLE_REMAINING" "$DIAGNOSTIC")" || return 1
  cat "$DATA/status"
  case "$STATE" in
    ok|current) summary='已就绪';;
    waiting) summary='待空闲优化';;
    running) summary='优化中';;
    deferred)
      case "$ERROR" in
        device_hot) detail='电池温度达到 55℃ 或以上';;
        battery_low) detail='电量为 15% 或更低';;
        device_interactive|idle_unknown) detail='待息屏或空闲优化';;
        power_unknown) detail='无法读取电池信息';;
        thermal_unknown) detail='无法读取电池温度';;
        screen_unknown) detail='无法确认息屏状态';;
        timeout_unavailable) detail='系统缺少有界执行能力';;
        art_cancel_unavailable) detail='系统未提供可靠的 ART 取消能力';;
        *) detail='执行异常';;
      esac
      case "$ERROR:$MODE" in
        art_cancel_unavailable:*|timeout_unavailable:*) summary='系统暂不支持优化';;
        device_interactive:*|idle_unknown:*) summary='待息屏或空闲优化';;
        device_hot:*) summary='待电池降温';;
        battery_low:*) summary='待电量恢复';;
        *) summary='优化已延期';;
      esac;;
    *) summary='执行异常';;
  esac
  # Rebuild fixed metadata rather than evaluating the old description.
  if ordinary "$MODDIR/module.prop"; then
    [ "$(sed -n 's/^description=//p' "$MODDIR/module.prop")" != "$summary" ] || return 0
    temp=$(mktemp "$MODDIR/.prop.XXXXXX") || return 1
    printf 'id=c17_performance\nname=Status C17 附属模块\nversion=%s\nversionCode=%s\nauthor=aiingjie\ndescription=%s\n' \
      "$MODVERSION" "$MODCODE" "$summary" > "$temp"
    chmod 0644 "$temp" && mv -f "$temp" "$MODDIR/module.prop" || return 1
  fi
}
capabilities() {
  command -v timeout >/dev/null 2>&1 && timeout -k 1 2 sh -c ':' >/dev/null 2>&1 || { ERROR=timeout_unavailable; return 1; }
  TIMED=1
  help=$(timeout -k 2 12 cmd package help 2>/dev/null)
  # Require service-side cancellation and a primary-only scope. No all-app background job is started.
  printf '%s\n' "$help" | grep -q 'cancel JOB_ID' && printf '%s\n' "$help" | grep -q -- '-v Verbose' &&
    printf '%s\n' "$help" | grep -q -- '--primary-dex' || { ERROR=art_cancel_unavailable; return 1; }
  if printf '%s\n' "$help" | grep -q 'PRIORITY_BACKGROUND'; then PRIORITY=background; fi
}
art_state() {
  # AOSP ART resolves dependency mismatches as reason=vdex, even if an old OAT file exists.
  # Unknown OEM formats remain unverified; they are never counted as current ART evidence.
  ART_FILTER=native
  art=$(timeout -k 2 12 cmd package art dump "$1" 2>/dev/null) || { ART=unknown; return; }
  printf '%s\n' "$art" | grep -Fqx "[$1]" || { ART=unknown; return; }
  primary=$(printf '%s\n' "$art" | sed '/known secondary dex files:/,$d')
  statuses=$(printf '%s\n' "$primary" | grep -E '\[status=[^]]+\].*\[reason=[^]]+\].*\[primary-abi\]')
  path_count=$(printf '%s\n' "$primary" | grep -c '^[[:space:]]*path: ')
  status_count=$(printf '%s\n' "$statuses" | grep -c '\[primary-abi\]')
  filters=$(printf '%s\n' "$statuses" | sed -n 's/.*\[status=\([^]]*\)\].*/\1/p' | sort -u)
  if [ "$path_count" -gt 0 ] && [ "$path_count" = "$status_count" ]; then
    case "$filters" in verify|speed-profile|speed) ART_FILTER=$filters;; esac
  fi
  if [ -z "$statuses" ] || [ "$path_count" != "$status_count" ]; then ART=unknown
  elif printf '%s\n' "$statuses" | grep -Eq '\[reason=vdex\]|\[status=(verify|run-from-apk|run-from-vdex|extract|error|unknown)\]'; then ART=stale
  # Valid profile-guided OEM code is compiled code too. Forcing every prebuilt
  # speed-profile package through speed repeatedly can discard useful profiles.
  elif [ "$(printf '%s\n' "$statuses" | grep -c '\[status=')" = "$(printf '%s\n' "$statuses" | grep -Ec '\[status=(speed|speed-profile)\]')" ]; then ART=ready
  else ART=unknown; fi
}
device_ready() {
  # Manual optimization is explicit: never query or wait for any device condition.
  ERROR=none
  IDLE_REMAINING=0
  [ "$MODE" = boot ] || [ "$MODE" = auto ] || return 0
  [ "$(getprop sys.boot_completed)" = 1 ] || { ERROR=boot_incomplete; return 1; }
  battery=$(timeout -k 2 8 dumpsys battery 2>/dev/null) || { ERROR=power_unknown; return 1; }
  level=$(printf '%s\n' "$battery" | sed -n 's/^[[:space:]]*level: \([0-9][0-9]*\)[[:space:]]*$/\1/p' | head -n 1)
  temperature=$(printf '%s\n' "$battery" | sed -n 's/^[[:space:]]*temperature: \([0-9][0-9]*\)[[:space:]]*$/\1/p' | head -n 1)
  case "$level" in ''|*[!0-9]*|????*) ERROR=power_unknown; return 1;; esac
  case "$temperature" in ''|*[!0-9]*|?????*) ERROR=thermal_unknown; return 1;; esac
  [ "$level" -le 100 ] && [ "$temperature" -le 1000 ] || { ERROR=power_unknown; return 1; }
  [ "$temperature" -lt 550 ] || { ERROR=device_hot; return 1; }
  [ "$level" -gt 15 ] || { ERROR=battery_low; return 1; }
  # Charging and framework thermal severity do not participate in the author's automatic policy.
  power=$(timeout -k 2 8 dumpsys power 2>/dev/null) || { ERROR=screen_unknown; return 1; }
  if printf '%s\n' "$power" | grep -Eq 'mInteractive=true|mWakefulness=Awake'; then
    # These ages are calculated by PowerManager using uptimeMillis, not /proc/uptime (which includes sleep).
    # Accept the modern global display groups or the old global dump; never use per-profile timestamps.
    activity=$(printf '%s\n' "$power" | grep -E '^[[:space:]]*(mLast|last)UserActivityTime(NoChangeLights)?=')
    normal_count=$(printf '%s\n' "$activity" | grep -Ec 'UserActivityTime=')
    light_count=$(printf '%s\n' "$activity" | grep -Ec 'UserActivityTimeNoChangeLights=')
    [ "$normal_count" -gt 0 ] && [ "$normal_count" = "$light_count" ] || { ERROR=idle_unknown; return 1; }
    ages=$(printf '%s\n' "$activity" | sed -n -e 's/^[[:space:]]*[^=]*=[0-9][0-9]* (\([0-9][0-9]*\) ms ago)[[:space:]]*$/\1/p' -e 's/^[[:space:]]*[^=]*=[0-9][0-9]* (now)[[:space:]]*$/0/p')
    [ "$(printf '%s\n' "$ages" | grep -c .)" = $((normal_count + light_count)) ] || { ERROR=idle_unknown; return 1; }
    min_age=600000
    for age in $ages; do
      case "$age" in ''|*[!0-9]*|??????????????*) ERROR=idle_unknown; return 1;; esac
      [ "$age" -ge "$min_age" ] || min_age=$age
    done
    if [ "$min_age" -lt 600000 ]; then
      IDLE_REMAINING=$(( (600000 - min_age + 999) / 1000 )); ERROR=device_interactive; return 1
    fi
  else
    printf '%s\n' "$power" | grep -Eq 'mInteractive=false|mWakefulness=(Asleep|Dozing)' || { ERROR=screen_unknown; return 1; }
  fi
  ERROR=none
}
cancel_active() {
  # Do not infer service completion from a dead Binder client.
  [ -n "$ACTIVE_JOB" ] || return 1
  cancel_attempt=0
  while [ "$cancel_attempt" -lt 5 ]; do
    cancel=$(timeout -k 2 4 cmd package art cancel "$ACTIVE_JOB" 2>&1)
    cancel_result=$?
    # 'Job cancelled' acknowledges the signal only. 'Job not found' proves its registry entry closed.
    if { [ "$cancel_result" = 0 ] || [ "$cancel_result" = 1 ]; } && printf '%s\n' "$cancel" | grep -qx 'Job not found'; then return 0; fi
    [ "$cancel_result" = 0 ] && printf '%s\n' "$cancel" | grep -qx 'Job cancelled' || { DIAGNOSTIC=cancel_unrecognized; return 1; }
    sleep 1; cancel_attempt=$((cancel_attempt + 1))
  done
  DIAGNOSTIC=cancel_pending; return 1
}
# A package-manager call that fails before any ART job exists (system_server busy right after boot, a lost Binder
# call) leaves nothing to cancel or to overlap with, so it is retried and never blocks later tasks.
operation() {
  retry=0
  while :; do
    TRANSIENT=0
    operation_once "$@"; result=$?
    [ "$result" != 0 ] || return 0
    [ "$TRANSIENT" = 1 ] && [ "$retry" -lt 2 ] || return "$result"
    retry=$((retry + 1))
    sleep $((retry * 5 + (retry - 1) * 5))
    ERROR=none; DIAGNOSTIC=none
  done
}
operation_once() {
  kind=$1; target=$2
  trace "package=$target stage=$kind attempt=$((retry + 1))"
  ACTIVE_LOG=$(mktemp "$DATA/.operation.XXXXXX") || { ERROR=status_write_failed; return 1; }
  ACTIVE_JOB=
  OP_PRIORITY=
  [ "$PRIORITY" != background ] || OP_PRIORITY='-p PRIORITY_BACKGROUND'
  # Binder transfers stdout/stderr FDs to system_server. An adb_data_file FD
  # opened below /data/adb is rejected by enforcing ROMs before ART even starts.
  # Pass anonymous pipes to cmd; only our reader opens the private evidence file.
  # Preserve the compiler exit separately from the pipe reader's exit status.
  (
  compile_client() {
  if [ "$kind" = restore ]; then
    # --reset would erase current/reference profiles and can downgrade SystemUI to verify.
    # Restore the known prior filter, or ask the ROM's install policy when no trustworthy prior filter exists.
    if [ "$RESTORE_FILTER" = native ]; then
      exec cmd package compile -v $OP_PRIORITY -r install -f --primary-dex "$target"
    else
      exec cmd package compile -v $OP_PRIORITY -m "$RESTORE_FILTER" -f --primary-dex "$target"
    fi
  elif [ "$PRIORITY" = background ]; then
    exec cmd package compile -v -m speed -p PRIORITY_BACKGROUND --primary-dex "$target"
  else
    exec cmd package compile -v -m speed --primary-dex "$target"
  fi
  }
  compile_client &
  client=$!
  birth=$(awk '{print $22}' "/proc/$client/stat" 2>/dev/null)
  case "$birth" in ''|*[!0-9]*) ;; *) printf 'C17_ART_CLIENT=%s:%s\n' "$client" "$birth";; esac
  wait "$client"
  compiler_exit=$?
  printf '\nC17_ART_CLIENT_EXIT=%s\n' "$compiler_exit"
  ) 9<&- 2>&1 | cat > "$ACTIVE_LOG" 9<&- 2>/dev/null &
  ACTIVE_PID=$!
  start=$(date +%s); polls=0; stop_reason=
  OPERATION_SECONDS=180
  while kill -0 "$ACTIVE_PID" 2>/dev/null; do
    if [ -z "$ACTIVE_JOB" ]; then
      job=$(sed -n "s/.*'pm art cancel \([0-9a-fA-F-]*\)'.*/\1/p" "$ACTIVE_LOG" | head -n 1)
      if [ "${#job}" = 36 ]; then
        case "$job" in ????????-????-????-????-????????????)
          ACTIVE_JOB=$job
          write_file active-job "$(printf '%s\n%s\n' "$BOOT" "$ACTIVE_JOB")" || { stop_reason=${kind}_cancel_failed; break; };;
        esac
      fi
    fi
    elapsed=$(( $(date +%s) - start ))
    if [ "$elapsed" -ge "$OPERATION_SECONDS" ]; then stop_reason=${kind}_timeout; break; fi
    # Detect an incompatible verbose implementation before leaving an untracked ART operation running.
    if [ "$elapsed" -ge 10 ] && [ -z "$ACTIVE_JOB" ]; then DIAGNOSTIC=uuid_missing; stop_reason=${kind}_cancel_failed; break; fi
    if { [ "$MODE" = boot ] || [ "$MODE" = auto ]; } && [ "$kind" != restore ] && [ "$polls" -gt 0 ] && [ $((polls % 15)) = 0 ]; then
      if ! device_ready; then stop_reason=$ERROR; break; fi
    fi
    sleep 1; polls=$((polls + 1))
  done
  result=0
  if [ -n "$stop_reason" ]; then
    if cancel_active; then rm -f "$DATA/active-job"
    else
      stop_reason=${kind}_cancel_failed
      [ -n "$ACTIVE_JOB" ] || write_file active-job "$(printf '%s\nunknown\n' "$BOOT")"
    fi
    # Give the cancellable ART operation a short grace period; the client also has a hard bound.
    grace=0
    while kill -0 "$ACTIVE_PID" 2>/dev/null && [ "$grace" -lt 10 ]; do sleep 1; grace=$((grace + 1)); done
    terminate_client
    kill "$ACTIVE_PID" 2>/dev/null
    kill -KILL "$ACTIVE_PID" 2>/dev/null
    result=1; ERROR=$stop_reason
  else
    wait "$ACTIVE_PID"; result=$?
    client_exit=$(tr -d '\r' < "$ACTIVE_LOG" | sed -n 's/^C17_ART_CLIENT_EXIT=\([0-9][0-9]*\)$/\1/p')
    [ "$client_exit" = 0 ] || result=1
    final_count=$(tr -d '\r' < "$ACTIVE_LOG" | grep -Ec '^Final Status: (PERFORMED|SKIPPED|FAILED|CANCELLED)$')
    if [ "$result" != 0 ] || [ "$(tr -d '\r' < "$ACTIVE_LOG" | grep -Ec '^Final Status: (PERFORMED|SKIPPED)$')" != 1 ]; then
      result=1; ERROR=${kind}_failed
    elif [ "$kind" = compile ]; then read_cost "$ACTIVE_LOG"
    fi
    if [ "$final_count" = 1 ]; then rm -f "$DATA/active-job"
    else
      DIAGNOSTIC=final_unrecognized
      # The command may have lost its Binder connection. Recover/cancel its UUID before any later task.
      job=$(sed -n "s/.*'pm art cancel \([0-9a-fA-F-]*\)'.*/\1/p" "$ACTIVE_LOG" | head -n 1)
      case "$job" in ????????-????-????-????-????????????) ACTIVE_JOB=$job;; *) ACTIVE_JOB=;; esac
      if [ -n "$ACTIVE_JOB" ] && cancel_active; then rm -f "$DATA/active-job"
      elif [ -z "$ACTIVE_JOB" ] && tr -d '\r' < "$ACTIVE_LOG" | grep -qE '^cmd: Failure calling service|Failed transaction'; then
        # The call never reached a running job: nothing to cancel, nothing that can overlap a later task.
        TRANSIENT=1; DIAGNOSTIC=client_failed; rm -f "$DATA/active-job"
      else
        write_file active-job "$(printf '%s\n%s\n' "$BOOT" "${ACTIVE_JOB:-unknown}")"
        ERROR=${kind}_cancel_failed
      fi
    fi
  fi
  ACTIVE_PID=; ACTIVE_JOB=
  # Preserve bounded failure evidence as data for the H5 details; never evaluate or source it.
  if [ "$result" != 0 ] || [ "$DIAGNOSTIC" != none ]; then
    write_file operation-last "$(printf 'time=%s package=%s stage=%s error=%s diagnostic=%s\n' "$(date +%s)" "$target" "$kind" "$ERROR" "$DIAGNOSTIC"; tail -c 1400 "$ACTIVE_LOG")" || ERROR=status_write_failed
  fi
  trace "package=$target stage=$kind result=$result error=$ERROR diagnostic=$DIAGNOSTIC wall_ms=$OP_WALL"
  rm -f "$ACTIVE_LOG"; ACTIVE_LOG=
  return "$result"
}
recover_operation() {
  [ -e "$DATA/active-job" ] || [ -L "$DATA/active-job" ] || return 0
  ordinary "$DATA/active-job" && owned "$DATA/active-job" || { ERROR=path_link_rejected; return 1; }
  previous_boot=$(sed -n '1p' "$DATA/active-job")
  if [ "$previous_boot" != "$BOOT" ]; then rm -f "$DATA/active-job"; return; fi
  job=$(sed -n '2p' "$DATA/active-job")
  # An unknown handle can never be cancelled. The module's own operations end within minutes, so a marker this old
  # no longer protects anything and must not lock every later task until reboot.
  if [ "$job" = unknown ] && [ -n "$(find "$DATA/active-job" -mmin +15 2>/dev/null)" ]; then rm -f "$DATA/active-job"; return 0; fi
  [ "${#job}" = 36 ] || { DIAGNOSTIC=active_unknown; ERROR=compile_cancel_failed; return 1; }
  case "$job" in *[!0-9a-fA-F-]*) ERROR=compile_cancel_failed; return 1;; esac
  ACTIVE_JOB=$job
  if cancel_active; then rm -f "$DATA/active-job"; ACTIVE_JOB=
  else ACTIVE_JOB=; ERROR=compile_cancel_failed; return 1; fi
}
restore_all() {
  # Restore only packages this module may have modified; a native speed result merely reused is untouched.
  for file in "$DATA"/managed.*; do
    [ -e "$file" ] || [ -L "$file" ] || continue
    ordinary "$file" && owned "$file" || { fail path_link_rejected; continue; }
    package=${file##*/managed.}; valid_package "$package" || { fail module_id_rejected; continue; }
    RESTORE_FILTER=$(cat "$file")
    case "$RESTORE_FILTER" in verify|speed-profile|speed) ;; *) RESTORE_FILTER=native;; esac
    PACKAGES=$((PACKAGES + 1))
    if operation restore "$package"; then
      COMPILED=$((COMPILED + 1)); rm -f "$file" "$DATA/package.$package" || fail restore_failed
    else
      STATE=failed
      if ordinary "$DATA/active-job"; then break; fi
    fi
  done
}

case "$MODDIR" in /data/adb/modules/c17_performance|/data/adb/modules_update/c17_performance) ;; *) echo error=module_path_rejected; exit 20;; esac
safe_dir "$MODDIR" && owned "$MODDIR" && ordinary "$MODDIR/module.prop" && owned "$MODDIR/module.prop" && no_links "$DATA" || { echo error=path_link_rejected; exit 20; }
grep -qx 'id=c17_performance' "$MODDIR/module.prop" || { echo error=module_id_rejected; exit 20; }
MODVERSION=$(sed -n 's/^version=//p' "$MODDIR/module.prop" | head -n 1)
MODCODE=$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" | head -n 1)
case "$MODVERSION" in ''|*[!a-zA-Z0-9._+-]*) echo error=module_version_rejected; exit 20;; esac
case "$MODCODE" in ''|*[!0-9]*) echo error=module_version_rejected; exit 20;; esac
case "$MODE" in boot|auto|run|uninstall|enable|disable|lockcheck|lockstatus) ;; *) echo error=mode_rejected; exit 20;; esac
[ "$(id -u)" = 0 ] || { echo error=root_required; exit 21; }
if [ "$MODE" != uninstall ] && [ "$MODE" != lockstatus ] && [ "$MODE" != lockcheck ]; then
  for flag in disable remove; do [ ! -L "$MODDIR/$flag" ] || { echo error=path_link_rejected; exit 20; }; done
  [ ! -e "$MODDIR/remove" ] || { echo error=module_inactive; exit 27; }
  [ "$MODE" = enable ] || [ "$MODE" = disable ] || [ ! -e "$MODDIR/disable" ] || { echo error=module_inactive; exit 27; }
fi
[ ! -e "$DATA" ] || { safe_dir "$DATA" && owned "$DATA"; } || { echo error=data_path_rejected; exit 20; }
if ! select_flock; then
  [ "$MODE" != lockstatus ] || { echo unavailable; exit 0; }
  echo error=lock_unavailable; exit 28
fi
[ "$MODE" != lockcheck ] || exit 0
if [ "$MODE" = lockstatus ]; then
  [ -e "$DATA" ] || { echo 0; exit 0; }
  guard_lock 2>/dev/null || { echo 1; exit 0; }
  lock_state
  case "$LOCK_STATE" in free|stale) echo 0;; live) echo 1;; *) echo unknown;; esac
  flock_call -u 9; exec 9<&-; exit 0
fi
mkdir -p "$DATA" && chmod 0700 "$DATA" || { echo error=data_create_failed; exit 22; }
guard_lock 2>/dev/null || { echo error=busy; exit 23; }
trap cleanup EXIT
take_lock || { [ "$ERROR" != lock_incomplete ] || { echo error=lock_incomplete; exit 29; }; echo error=busy; exit 23; }
interrupted() {
  cleanup
  fail interrupted
  if ordinary "$DATA/active-job"; then ERROR=compile_cancel_failed; fi
  status >/dev/null
  exit 24
}
trap interrupted HUP INT TERM
trace "start module=$MODCODE"

# Management takes the same task lock; H5 never edits flags directly and cannot race compilation.
if [ "$MODE" = enable ] || [ "$MODE" = disable ]; then
  for candidate in /data/adb/modules/c17_performance /data/adb/modules_update/c17_performance; do
    [ -e "$candidate" ] || [ -L "$candidate" ] || continue
    safe_dir "$candidate" && owned "$candidate" && ordinary "$candidate/module.prop" && owned "$candidate/module.prop" && grep -qx 'id=c17_performance' "$candidate/module.prop" || { echo error=path_link_rejected; exit 20; }
    [ ! -L "$candidate/disable" ] && [ ! -L "$candidate/remove" ] && [ ! -e "$candidate/remove" ] || { echo error=module_inactive; exit 27; }
  done
  for candidate in /data/adb/modules/c17_performance /data/adb/modules_update/c17_performance; do
    [ -d "$candidate" ] || continue
    if [ "$MODE" = enable ]; then rm -f "$candidate/disable" || exit 22
    else touch "$candidate/disable" || exit 22; fi
  done
  echo state=$MODE
  if ordinary "$MODDIR/cache-service.sh" && owned "$MODDIR/cache-service.sh"; then
    if [ "$MODE" = enable ]; then sh "$MODDIR/cache-service.sh" start || :
    else sh "$MODDIR/cache-service.sh" stop || :; fi
  fi
  exit 0
fi

# Preserve legacy migration evidence before publishing the boot waiting state.
if ordinary "$DATA/fingerprint" || { ordinary "$DATA/status" && ! grep -q '^module_version_code=' "$DATA/status"; }; then
  [ -e "$DATA/migration-complete" ] || write_file migration-pending legacy || { echo error=migration_failed; exit 22; }
fi
# The package manager may not answer UID queries when KSU starts service.sh.
# Wait for boot readiness before the one-shot cache start; this is independent
# of ART capabilities, thermal conditions and the later idle/charging gates.
if [ "$MODE" = boot ]; then
  STATE=waiting; status >/dev/null || { echo error=status_write_failed; exit 22; }
  count=0
  while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$count" -lt 300 ]; do sleep 2; count=$((count + 1)); done
  if [ "$(getprop sys.boot_completed)" != 1 ]; then fail boot_timeout; status; exit 22; fi
fi
# Cache startup is independent of ART readiness and environmental deferral.
if ordinary "$MODDIR/cache-service.sh" && owned "$MODDIR/cache-service.sh"; then
  if [ "$MODE" = uninstall ]; then sh "$MODDIR/cache-service.sh" purge || { fail cache_cleanup_failed; status; exit 22; }
  else sh "$MODDIR/cache-service.sh" start || trace "cache=start_unavailable"; fi
fi

if ! capabilities; then
  # Only verified native ART may bypass compile-only capabilities. Recovery/migration/removal
  # can change artifacts or cancel jobs, so retain their original strict gate.
  if [ "$TIMED" = 1 ] && [ "$MODE" != uninstall ] && [ ! -e "$DATA/active-job" ] && [ ! -L "$DATA/active-job" ] &&
      [ ! -L "$DATA/fingerprint" ] && [ ! -L "$DATA/migration-pending" ] && [ ! -L "$DATA/migration-complete" ] &&
      { [ ! -e "$DATA/migration-pending" ] || { ordinary "$DATA/migration-pending" && ordinary "$DATA/migration-complete"; }; }; then
    COMPILE_CAPABILITY_ERROR=$ERROR
    ERROR=none
  else
    STATE=deferred
    if [ "$MODE" = uninstall ]; then STATE=failed; status; exit 22; fi
    if [ "$TIMED" = 1 ]; then
      for package in $(packages); do valid_package "$package" && PACKAGES=$((PACKAGES + 1)); done
    else PACKAGES=1; fi
    DEFERRED=$PACKAGES
    status; exit 0
  fi
fi
# A legacy aggregate OR legacy running record proves the old script may have changed native code.
# Only a complete, successful package enumeration marks the migration complete.
if ordinary "$DATA/migration-pending" && ! ordinary "$DATA/migration-complete"; then
  migration_list=$(timeout -k 2 12 cmd package list packages -f 2>/dev/null) || { fail migration_failed; status; exit 22; }
  printf '%s\n' "$migration_list" | grep -q '^package:' || { fail migration_failed; status; exit 22; }
  migration_targets=$(printf '%s\n' com.android.systemui; printf '%s\n' "$migration_list" | while IFS= read -r line; do
    case "$line" in package:*/SystemUIPlugin/*=*) package=${line##*=}; valid_package "$package" && printf '%s\n' "$package";; esac
  done)
  for package in $migration_targets; do
    valid_package "$package" || continue
    ordinary "$DATA/managed.$package" || write_file "managed.$package" native || { fail migration_failed; status; exit 22; }
  done
  write_file migration-complete done && rm -f "$DATA/migration-pending" || { fail migration_failed; status; exit 22; }
fi
if ! recover_operation; then fail "$ERROR"; status; exit 22; fi
if [ "$MODE" = uninstall ]; then
  restore_all
  # Retain failures and remaining package markers for diagnosis/retry; never overwrite them with removed.
  if [ "$STATE" = failed ]; then status; exit 22; fi
  # Only our own immediate files; no recursive delete and no dereference.
  for file in "$DATA"/* "$DATA"/.[!.]* "$DATA"/..?*; do
    [ "$file" = "$DATA/.lock" ] && continue
    [ -e "$file" ] || [ -L "$file" ] || continue
    if [ -f "$file" ] || [ -L "$file" ]; then rm -f "$file" || fail uninstall_failed; else fail unexpected_directory; fi
  done
  if [ "$STATE" = failed ]; then status; exit 22; fi
  unlock
  rmdir "$DATA" || { echo error=uninstall_incomplete; exit 22; }
  echo state=removed
  exit 0
fi
ledger_load
BUILD_ID=$(getprop ro.build.fingerprint)
TARGETS=$(packages) || { fail package_query_failed; status; exit 22; }
TARGETS=$(printf '%s\n' "$TARGETS" | sort -u)
pending=
pending_filters=
for package in $TARGETS; do
  valid_package "$package" || continue
  PACKAGES=$((PACKAGES + 1))
  art_state "$package"
  current=$(fingerprint "$package") || current=
  if [ "$ART" = ready ]; then
    trace "package=$package result=reuse proof=native_art"
    REUSED=$((REUSED + 1)); VERIFIED=$((VERIFIED + 1)); ledger_reuse "$package"
    if [ -n "$current" ]; then write_file "package.$package" "$current" || fail fingerprint_write_failed; fi
  elif [ "$ART" = unknown ] && [ -n "$current" ] && ordinary "$DATA/package.$package" && owned "$DATA/package.$package" && [ "$(sed '/^versionCode=[0-9][0-9]*$/d' "$DATA/package.$package")" = "$current" ]; then
    # Compatible ROM fallback: avoid endless recompilation, but explicitly report no ART verification.
    REUSED=$((REUSED + 1)); ledger_reuse "$package"
    trace "package=$package result=reuse proof=identity_only art=unknown"
  else
    trace "package=$package result=pending art=$ART filter=$ART_FILTER"
    pending="$pending $package"
    pending_filters="$pending_filters
$package=$ART_FILTER"
  fi
done
[ "$PACKAGES" -gt 0 ] || fail no_package
if [ "$STATE" != failed ] && [ -n "$COMPILE_CAPABILITY_ERROR" ] && [ "$VERIFIED" != "$PACKAGES" ]; then
  STATE=deferred; ERROR=$COMPILE_CAPABILITY_ERROR
  DEFERRED=$((PACKAGES - VERIFIED))
  status; exit 0
fi
if [ "$STATE" != failed ] && [ -n "$pending" ]; then
  waited=0; wait_started=$(date +%s)
  while ! device_ready; do
    # Unknown sensors and manual requests never loop; automatic waits are bounded to four minutes.
    case "$ERROR" in *_unknown) break;; esac
    { [ "$MODE" = boot ] || [ "$MODE" = auto ]; } && [ "$waited" -lt 16 ] && [ $(( $(date +%s) - wait_started )) -lt 240 ] || break
    STATE=waiting; status >/dev/null || { echo error=status_write_failed; exit 22; }
    sleep 15; waited=$((waited + 1))
  done
  if [ "$ERROR" != none ]; then
    STATE=deferred
    for package in $pending; do DEFERRED=$((DEFERRED + 1)); done
  else
    STATE=running; status >/dev/null || { echo error=status_write_failed; exit 22; }
    STATE=ok; RUN_FAILURE=
    for package in $pending; do
      if ! device_ready; then STATE=deferred; DEFERRED=$((DEFERRED + 1)); continue; fi
      # Written before the operation: a partially modified artifact also needs restoration on removal.
      # Recheck after an idle wait, not twice back-to-back in the same inspection.
      if [ "$waited" -gt 0 ]; then
        art_state "$package"
        if [ "$ART" = ready ]; then
          REUSED=$((REUSED + 1)); VERIFIED=$((VERIFIED + 1)); ledger_reuse "$package"
          trace "package=$package result=reuse proof=art_after_wait"; continue
        fi
      else ART_FILTER=$(printf '%s\n' "$pending_filters" | awk -F= -v p="$package" '$1==p {print $2;exit}'); fi
      if ! ordinary "$DATA/managed.$package"; then write_file "managed.$package" "$ART_FILTER" || { RUN_FAILURE=fingerprint_write_failed; break; }; fi
      if operation compile "$package"; then
        ledger_compile "$package"
        current=$(fingerprint "$package") || current=
        art_state "$package"
        if [ "$ART" = ready ]; then VERIFIED=$((VERIFIED + 1)); COMPILED=$((COMPILED + 1))
        elif [ "$ART" = stale ]; then RUN_FAILURE=art_unverified; fi
        if [ "$ART" = unknown ]; then COMPILED=$((COMPILED + 1)); fi
        if [ "$ART" != stale ] && [ -n "$current" ]; then write_file "package.$package" "$current" || RUN_FAILURE=fingerprint_write_failed; fi
      else
        STATE=failed
        RUN_FAILURE=$ERROR
        # Invalidate only this package's old success; successful neighbours keep their own identities.
        rm -f "$DATA/package.$package"
        if ordinary "$DATA/active-job"; then RUN_FAILURE=compile_cancel_failed; break; fi
        case "$ERROR" in compile_cancel_failed) break;; esac
      fi
    done
    if [ -n "$RUN_FAILURE" ]; then fail "$RUN_FAILURE"; fi
  fi
elif [ "$STATE" != failed ]; then STATE=current; fi
sample_runtime
status || { echo error=status_write_failed; exit 22; }
[ "$STATE" != failed ]
