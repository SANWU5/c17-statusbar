#!/system/bin/sh
# Fixed WebUI operations only. No app-controlled path, package name or shell fragment is accepted.
umask 077
MODDIR=${0%/*}
DATA=/data/adb/c17-performance
MODE=$1
case "$MODDIR" in /data/adb/modules/c17_performance|/data/adb/modules_update/c17_performance) ;; *) echo error=module_path_rejected; exit 20;; esac
case "$MODE" in status|run|auto|enable|disable|rescue-enable|rescue-disable) ;; *) echo error=mode_rejected; exit 20;; esac
no_links() { p=$1; while [ "$p" != / ] && [ -n "$p" ]; do [ ! -L "$p" ] || return 1; p=${p%/*}; [ -n "$p" ] || p=/; done; }
owned() {
  [ ! -L "$1" ] && [ "$(stat -c %u "$1")" = 0 ] || return 1
  permission=$(stat -c %a "$1") || return 1
  case "$permission" in ''|*[!0-7]*|?????*|*[2367][0-7]|*[2367]) return 1;; esac
}
trusted() { [ -f "$1" ] && owned "$1"; }
valid() { [ -d "$1" ] && no_links "$1" && owned "$1" && trusted "$1/module.prop" && grep -qx 'id=c17_performance' "$1/module.prop"; }
[ "$(id -u)" = 0 ] && valid "$MODDIR" && trusted "$MODDIR/common.sh" && no_links "$DATA" || { echo error=path_link_rejected; exit 20; }
if [ -e "$DATA" ]; then
  [ -d "$DATA" ] && owned "$DATA" || { echo error=data_path_rejected; exit 20; }
fi
BOOT=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
busy=0; active_job=0
if [ -e "$DATA/.lock" ] || [ -L "$DATA/.lock" ]; then
  busy=unknown
  if [ -d "$DATA/.lock" ] && owned "$DATA/.lock" && trusted "$DATA/.lock/pid" && trusted "$DATA/.lock/boot"; then
    previous=$(cat "$DATA/.lock/pid"); previous_boot=$(cat "$DATA/.lock/boot")
    case "$previous" in ''|*[!0-9]*|??????????*) ;; *) if [ "$previous_boot" = "$BOOT" ] && kill -0 "$previous" 2>/dev/null; then busy=1; else busy=0; fi;; esac
  fi
fi
lock_available=1
module_code=$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" | head -n 1)
case "$module_code" in ''|*[!0-9]*) ;; *)
  if [ "$module_code" -ge 10 ]; then
    lock_report=$(sh "$MODDIR/common.sh" lockstatus)
    case "$lock_report" in unavailable) lock_available=0; busy=unknown;; 0|1|unknown) busy=$lock_report;; *) busy=unknown;; esac
  fi;; esac
if [ -e "$DATA/active-job" ] || [ -L "$DATA/active-job" ]; then
  active_job=unknown
  if trusted "$DATA/active-job"; then
    previous_boot=$(sed -n '1p' "$DATA/active-job"); job=$(sed -n '2p' "$DATA/active-job")
    if [ "$previous_boot" != "$BOOT" ]; then active_job=0
    elif printf '%s\n' "$job" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'; then active_job=1
    elif [ "$job" = unknown ] && [ -n "$(find "$DATA/active-job" -mmin +15 2>/dev/null)" ]; then active_job=0; fi
  fi
fi
if [ "$MODE" = status ]; then
  installed=0; pending=0; disabled=0; remove=0; chosen=
  for candidate in /data/adb/modules/c17_performance /data/adb/modules_update/c17_performance; do
    [ -e "$candidate" ] || [ -L "$candidate" ] || continue
    valid "$candidate" || { echo error=path_link_rejected; exit 20; }
    [ ! -L "$candidate/disable" ] && [ ! -L "$candidate/remove" ] && [ ! -L "$candidate/update" ] || { echo error=path_link_rejected; exit 20; }
    if [ "$candidate" = /data/adb/modules/c17_performance ]; then installed=1; else pending=1; fi
    [ ! -e "$candidate/disable" ] || disabled=1
    [ ! -e "$candidate/remove" ] || remove=1
    [ ! -e "$candidate/update" ] || pending=1
    chosen=$candidate
  done
  printf 'C17_WEB:begin\ninstalled=%s\npending=%s\ndisabled=%s\nremove=%s\nboot=%s\nbusy=%s\nactive_job=%s\n' "$installed" "$pending" "$disabled" "$remove" "$BOOT" "$busy" "$active_job"
  printf 'lock_available=%s\n' "$lock_available"
  if trusted "$MODDIR/cache-service.sh"; then sh "$MODDIR/cache-service.sh" status; fi
  if trusted "$MODDIR/boot-rescue.sh"; then sh "$MODDIR/boot-rescue.sh" --status | sed -n '/^rescue_/p'; fi
  if [ -n "$chosen" ]; then sed -n -e 's/^version=/module_version=/p' -e 's/^versionCode=/module_version_code=/p' "$chosen/module.prop"; fi
  if trusted "$DATA/status"; then
    echo record_present=1
    last_boot=$(sed -n 's/^boot=//p' "$DATA/status" | head -n 1)
    if [ "$last_boot" = "$BOOT" ] && [ -n "$BOOT" ]; then echo current_boot=1; else echo current_boot=0; fi
    head -c 2048 "$DATA/status" | sed 's/^/last_/'
    printf '\n'
  else printf 'record_present=0\ncurrent_boot=0\n'; fi
  if trusted "$DATA/schedule"; then head -c 512 "$DATA/schedule" | sed 's/^/schedule_/'; printf '\n'
  else printf 'schedule_kind=unknown\nschedule_state=unknown\nschedule_next_after=0\n'; fi
  if trusted "$DATA/ledger"; then head -c 512 "$DATA/ledger" | sed 's/^/total_/'; printf '\n'; fi
  if trusted "$DATA/runtime"; then head -c 256 "$DATA/runtime" | sed 's/^/runtime_/'; printf '\n'; fi
  if trusted "$DATA/trace"; then printf trace_hex=; tail -c 2048 "$DATA/trace" | od -An -v -tx1 | tr -d ' \n'; printf '\n'; fi
  if trusted "$DATA/operation-last"; then printf failure_hex=; tail -c 512 "$DATA/operation-last" | od -An -v -tx1 | tr -d ' \n'; printf '\n'; fi
  printf 'C17_WEB:end\n'
  exit 0
fi
if [ "$MODE" = rescue-enable ] || [ "$MODE" = rescue-disable ]; then
  [ "$MODDIR" = /data/adb/modules/c17_performance ] && [ ! -e "$MODDIR/remove" ] || { echo error=module_inactive; exit 27; }
  trusted "$MODDIR/boot-rescue.sh" || { echo error=unsupported; exit 26; }
  if [ "$MODE" = rescue-enable ]; then exec sh "$MODDIR/boot-rescue.sh" --enable
  else exec sh "$MODDIR/boot-rescue.sh" --disable; fi
fi
[ "$lock_available" = 1 ] || { echo error=lock_unavailable; exit 28; }
[ "$busy" = 0 ] && [ "$active_job" = 0 ] || { printf '已有任务或未确认的任务记录，请先查看详情。\n'; exit 23; }
if [ "$MODE" = enable ] || [ "$MODE" = disable ]; then
  sh "$MODDIR/common.sh" "$MODE" || exit $?
  [ "$MODE" = enable ] || exit 0
  MODE=auto
fi
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || { printf '模块已停用或待卸载。\n'; exit 27; }
# The real worker owns the task lock and all cancellation/timeout rules. The WebView receives a short acknowledgement.
if command -v setsid >/dev/null 2>&1; then setsid sh "$MODDIR/common.sh" "$MODE" </dev/null >/dev/null 2>&1 &
else sh "$MODDIR/common.sh" "$MODE" </dev/null >/dev/null 2>&1 & fi
printf '已提交，请刷新查看实际执行状态。\n'
