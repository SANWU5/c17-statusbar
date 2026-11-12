#!/system/bin/sh
# Fixed, root-only user-0 SystemUI recovery. No caller-supplied path, package or command.
# The watcher supplies this script on an already verified FD; do not derive MODDIR from $0.
exec 3<&-
umask 077
PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
MODDIR=/data/adb/modules/c17_performance
DATA=/data/adb/c17-performance
PACKAGE=com.android.systemui
DE=/data/user_de/0/com.android.systemui
CE=/data/user/0/com.android.systemui
CE_ALIAS=/data/data/com.android.systemui
EXTERNAL=/data/media/0/Android/data/com.android.systemui
MODE=$1
BOOT=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
STATE=idle
ERROR=none
TIME=0
LOCKED=0
STOPPED=
UID_VALUE=

no_links() {
  check=$1
  while [ -n "$check" ] && [ "$check" != / ]; do
    [ ! -L "$check" ] || return 1
    check=${check%/*}; [ -n "$check" ] || check=/
  done
}
root_owned() {
  no_links "$1" && [ "$(stat -c %u "$1" 2>/dev/null)" = 0 ] || return 1
  perm=$(stat -c %a "$1" 2>/dev/null) || return 1
  case "$perm" in ''|*[!0-7]*|?????*|*[2367][0-7]|*[2367]) return 1;; esac
}
ordinary() { [ -f "$1" ] && root_owned "$1"; }
active() { [ ! -e "$MODDIR/disable" ] && [ ! -L "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] && [ ! -L "$MODDIR/remove" ] && [ ! -e "$DATA/boot-rescue-disabled" ] && [ ! -L "$DATA/boot-rescue-disabled" ]; }
valid_boot() { printf '%s\n' "$BOOT" | grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'; }
same_boot() { [ "$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)" = "$BOOT" ]; }
boot_window() { same_boot && [ "$(getprop sys.boot_completed)" != 1 ] && [ "$(getprop init.svc.bootanim)" = running ]; }
unlocked() { [ "$(getprop sys.user.0.ce_available)" = true ]; }
external_ready() { [ -d /data/media/0/Android/data ] && no_links /data/media/0/Android/data; }

write_file() {
  destination=$1; contents=$2
  case "$destination" in boot-rescue-status|boot-rescue-pending|boot-rescue-disabled) ;; *) return 1;; esac
  [ ! -e "$DATA/$destination" ] || ordinary "$DATA/$destination" || return 1
  temp=$(mktemp "$DATA/.boot-rescue.XXXXXX") || return 1
  if printf '%s\n' "$contents" > "$temp" && chmod 0600 "$temp" && mv -f "$temp" "$DATA/$destination"; then return 0; fi
  rm -f "$temp"; return 1
}
record() {
  STATE=$1; ERROR=$2; TIME=$(date +%s)
  case "$TIME" in ''|*[!0-9]*) TIME=0;; esac
  write_file boot-rescue-status "$(printf 'boot=%s\nstate=%s\nerror=%s\ntime=%s\n' "$BOOT" "$STATE" "$ERROR" "$TIME")"
}
known_error() {
  case "$1" in none|path_rejected|input_unavailable|status_write_failed|reset_failed|package_unavailable|clear_unknown|user_locked|external_unavailable|boot_gate_rejected|authorization_rejected|pending_expired|unsupported) return 0;; *) return 1;; esac
}
load_status() {
  if ordinary "$DATA/boot-rescue-status"; then
    status_boot=$(sed -n 's/^boot=//p' "$DATA/boot-rescue-status" | head -n 1)
    value=$(sed -n 's/^state=//p' "$DATA/boot-rescue-status" | head -n 1)
    case "$value" in idle|watching|triggered|waiting_unlock|cleared|failed|cancelled) STATE=$value;; esac
    value=$(sed -n 's/^error=//p' "$DATA/boot-rescue-status" | head -n 1)
    if known_error "$value"; then ERROR=$value; fi
    value=$(sed -n 's/^time=//p' "$DATA/boot-rescue-status" | head -n 1)
    case "$value" in ''|*[!0-9]*|????????????*) ;; *) TIME=$value;; esac
  fi
}
watching() {
  ordinary "$DATA/boot-rescue-watch.pid" || return 1
  watcher=$(sed -n '1p' "$DATA/boot-rescue-watch.pid"); watcher_boot=$(sed -n '2p' "$DATA/boot-rescue-watch.pid")
  case "$watcher" in ''|*[!0-9]*|??????????*) return 1;; esac
  [ "$watcher" -gt 1 ] && [ "$watcher_boot" = "$BOOT" ] && kill -0 "$watcher" 2>/dev/null || return 1
  executable=$(tr '\000' '\n' < "/proc/$watcher/cmdline" 2>/dev/null | head -n 1)
  case "$executable" in "$MODDIR/bin/c17bootrescue-arm64-v8a"|"$MODDIR/bin/c17bootrescue-armeabi-v7a"|"$MODDIR/bin/c17bootrescue-x86_64"|"$MODDIR/bin/c17bootrescue-x86") return 0;; *) return 1;; esac
}
status() {
  load_status
  if active; then enabled=1; else enabled=0; fi
  # A watcher file alone is not proof: validate its current boot and exact process command.
  if [ "$STATE" = watching ]; then STATE=idle; fi
  if [ "$enabled" = 1 ] && watching && boot_window; then
    [ "$status_boot" = "$BOOT" ] || STATE=idle
    case "$STATE" in triggered|waiting_unlock|cleared|failed|cancelled) ;; *) STATE=watching;; esac
  fi
  printf 'rescue_enabled=%s\nrescue_state=%s\nrescue_error=%s\nrescue_time=%s\n' "$enabled" "$STATE" "$ERROR" "$TIME"
}

# Root-controlled operation lock independent of the long-running ART and native watch locks.
lock() {
  if mkdir "$DATA/.boot-rescue-operation" 2>/dev/null; then
    printf '%s\n%s\n' "$$" "$BOOT" > "$DATA/.boot-rescue-operation/owner"
    LOCKED=1; return 0
  fi
  root_owned "$DATA/.boot-rescue-operation" && ordinary "$DATA/.boot-rescue-operation/owner" || return 1
  previous=$(sed -n '1p' "$DATA/.boot-rescue-operation/owner"); previous_boot=$(sed -n '2p' "$DATA/.boot-rescue-operation/owner")
  case "$previous" in ''|*[!0-9]*|??????????*) return 1;; esac
  if [ "$previous_boot" = "$BOOT" ] && kill -0 "$previous" 2>/dev/null; then return 1; fi
  rm -f "$DATA/.boot-rescue-operation/owner" && rmdir "$DATA/.boot-rescue-operation" || return 1
  mkdir "$DATA/.boot-rescue-operation" || return 1
  printf '%s\n%s\n' "$$" "$BOOT" > "$DATA/.boot-rescue-operation/owner"
  LOCKED=1
}
unlock() {
  [ "$LOCKED" = 1 ] || return 0
  rm -f "$DATA/.boot-rescue-operation/owner"; rmdir "$DATA/.boot-rescue-operation" 2>/dev/null
  LOCKED=0
}
resume_processes() {
  for stopped in $STOPPED; do
    pid=${stopped%:*}; birth=${stopped#*:}
    [ "$(awk '{print $22}' "/proc/$pid/stat" 2>/dev/null)" = "$birth" ] || continue
    kill -CONT "$pid" 2>/dev/null || :
    kill -TERM "$pid" 2>/dev/null || :
  done
  STOPPED=
}
cleanup() { resume_processes; unlock; }
trap cleanup EXIT
trap 'exit 23' HUP INT TERM

identity() {
  UID_VALUE=
  if command -v timeout >/dev/null 2>&1; then
    UID_VALUE=$(timeout -k 1 5 cmd package list packages -U --user 0 "$PACKAGE" 2>/dev/null | awk '$1=="package:com.android.systemui" && $2 ~ /^uid:[0-9]+$/ {sub(/^uid:/,"",$2);print $2}')
  fi
  if [ -z "$UID_VALUE" ] && [ -f /data/system/packages.list ] && no_links /data/system/packages.list; then
    file_owner=$(stat -c %u /data/system/packages.list 2>/dev/null)
    file_perm=$(stat -c %a /data/system/packages.list 2>/dev/null)
    case "$file_perm" in ''|*[!0-7]*|?????*|*[2367][0-7]|*[2367]) return 1;; esac
    case "$file_owner" in 0|1000)
      UID_VALUE=$(awk '$1=="com.android.systemui" && $2 ~ /^[0-9]+$/ {print $2}' /data/system/packages.list);;
    esac
  fi
  case "$UID_VALUE" in ''|*[!0-9]*|??????*) return 1;; esac
  [ "$UID_VALUE" -ge 1000 ] && [ "$UID_VALUE" -lt 100000 ]
}
stop_processes() {
  for pid in $(pidof com.android.systemui 2>/dev/null); do
    case "$pid" in ''|*[!0-9]*|??????????*) return 1;; esac
    [ "$pid" -gt 1 ] || return 1
    name=$(tr '\000' '\n' < "/proc/$pid/cmdline" 2>/dev/null | head -n 1)
    [ "$name" = "$PACKAGE" ] || return 1
    [ "$(awk '/^Uid:/ {print $2;exit}' "/proc/$pid/status" 2>/dev/null)" = "$UID_VALUE" ] || return 1
    birth=$(awk '{print $22}' "/proc/$pid/stat" 2>/dev/null)
    case "$birth" in ''|*[!0-9]*) return 1;; esac
    kill -STOP "$pid" 2>/dev/null || return 1
    STOPPED="$STOPPED $pid:$birth"
  done
}
ce_path() {
  # Only the AOSP user-0 alias is accepted; arbitrary links are not a cleanup target.
  if [ -L /data/user/0 ]; then
    [ "$(readlink /data/user/0)" = /data/data ] && no_links /data/data || return 1
    [ "$(stat -Lc %d:%i /data/user/0)" = "$(stat -c %d:%i /data/data)" ] || return 1
    CE=$CE_ALIAS
  else CE=/data/user/0/com.android.systemui; fi
}
clean_dir() {
  target=$1
  case "$target" in "$DE"|"$CE"|"$EXTERNAL") ;; *) return 1;; esac
  no_links "$target" || return 1
  [ -e "$target" ] || return 0
  [ -d "$target" ] && [ "$(stat -c %u "$target")" = "$UID_VALUE" ] || return 1
  # Refuse mount/bind-mount roots and descendants, including same-device bind mounts.
  awk -v root="$target" '$5==root || index($5,root "/")==1 {found=1} END {exit found?1:0}' /proc/self/mountinfo || return 1
  parent=${target%/*}
  [ "$(stat -c %d "$parent")" = "$(stat -c %d "$target")" ] || return 1
  inode=$(stat -c %d:%i "$target") || return 1
  # The app cannot rename its root in a system-owned parent. Never remove that root:
  # its CE encryption policy, SELinux label, owner and PM-recorded inode stay intact.
  (cd -P "$target" && [ "$(pwd -P)" = "$target" ] && [ "$(stat -c %d:%i .)" = "$inode" ] && find . -xdev -depth -mindepth 1 -delete) || return 1
  [ "$(stat -c %d:%i "$target")" = "$inode" ]
}
authorization() {
  stamp=$DATA/boot-rescue.$BOOT
  ordinary "$stamp" && [ "$(stat -c %a "$stamp")" = 600 ] || return 1
  [ "$(wc -c < "$stamp" | tr -d ' ')" = 48 ] || return 1
  [ "$(sed -n '1p' "$stamp")" = authorized ] && [ "$(sed -n '2p' "$stamp")" = "$BOOT" ]
}
claimed() { [ -d "$DATA/boot-rescue-claim.$BOOT" ] && root_owned "$DATA/boot-rescue-claim.$BOOT"; }
pending() {
  ordinary "$DATA/boot-rescue-pending" || return 1
  pending_boot=$(sed -n '1p' "$DATA/boot-rescue-pending"); pending_uid=$(sed -n '2p' "$DATA/boot-rescue-pending"); pending_stage=$(sed -n '3p' "$DATA/boot-rescue-pending")
  case "$pending_stage" in ce|ce_in_progress|external|external_in_progress) ;; *) return 1;; esac
  [ "$pending_boot" = "$BOOT" ] && authorization && claimed || return 1
  [ "$pending_uid" = "$UID_VALUE" ]
}
defer() {
  stage=$2
  write_file boot-rescue-pending "$(printf '%s\n%s\n%s\n' "$BOOT" "$UID_VALUE" "$stage")" || { record failed status_write_failed; return 1; }
  record waiting_unlock "$1" || return 1
  # This is an already authorized bounded continuation, never another key listener.
  unlock
  sh "$MODDIR/boot-rescue.sh" --resume </dev/null >/dev/null 2>&1 &
}
finish_private() {
  if [ "$pending_stage" != external ]; then
    # Persist intent before touching CE. A crash or failed phase commit is unknown,
    # not permission to delete freshly recreated CE data on a later invocation.
    write_file boot-rescue-pending "$(printf '%s\n%s\nce_in_progress\n' "$BOOT" "$UID_VALUE")" || { record failed status_write_failed; return 1; }
    ce_path && clean_dir "$CE" || { record failed path_rejected; return 1; }
    write_file boot-rescue-pending "$(printf '%s\n%s\nexternal\n' "$BOOT" "$UID_VALUE")" || { record failed status_write_failed; return 1; }
    pending_stage=external
  else
    write_file boot-rescue-pending "$(printf '%s\n%s\nexternal\n' "$BOOT" "$UID_VALUE")" || { record failed status_write_failed; return 1; }
  fi
  if ! external_ready; then record waiting_unlock external_unavailable; return 2; fi
  write_file boot-rescue-pending "$(printf '%s\n%s\nexternal_in_progress\n' "$BOOT" "$UID_VALUE")" || { record failed status_write_failed; return 1; }
  clean_dir "$EXTERNAL" || { record failed path_rejected; return 1; }
  rm -f "$DATA/boot-rescue-pending" || { record failed status_write_failed; return 1; }
  record cleared none
}

case "$MODE" in --status|--enable|--disable|--start|--trigger|--resume) ;; *) exit 20;; esac
[ "$#" = 1 ] || exit 20
[ "$(id -u)" = 0 ] && valid_boot || exit 21
no_links /data && no_links /data/adb && root_owned /data/adb && root_owned /data/adb/modules && root_owned "$MODDIR" && ordinary "$MODDIR/module.prop" && ordinary "$MODDIR/boot-rescue.sh" && grep -qx 'id=c17_performance' "$MODDIR/module.prop" || exit 20
if [ "$MODE" = --status ] && [ ! -e "$DATA" ] && [ ! -L "$DATA" ]; then status; exit 0; fi
if [ -e "$DATA" ] || [ -L "$DATA" ]; then [ -d "$DATA" ] && root_owned "$DATA" || exit 20
else mkdir "$DATA" && chmod 0700 "$DATA" || exit 22; fi
if [ -e "$DATA/boot-rescue-disabled" ] || [ -L "$DATA/boot-rescue-disabled" ]; then ordinary "$DATA/boot-rescue-disabled" || exit 20; fi
for state_file in boot-rescue-status boot-rescue-pending boot-rescue-watch.pid; do
  if [ -e "$DATA/$state_file" ] || [ -L "$DATA/$state_file" ]; then ordinary "$DATA/$state_file" || exit 20; fi
done

case "$MODE" in
  --status) status; exit 0;;
  --enable) rm -f "$DATA/boot-rescue-disabled" || exit 22; status; exit 0;;
  --disable) write_file boot-rescue-disabled disabled || exit 22; status; exit 0;;
  --start)
    active || exit 0
    [ "$(getprop sys.boot_completed)" != 1 ] || exit 0
    abi=$(getprop ro.product.cpu.abi)
    case "$abi" in arm64-v8a|armeabi-v7a|x86_64|x86) ;; *) record failed unsupported; exit 25;; esac
    binary=$MODDIR/bin/c17bootrescue-$abi
    root_owned "$MODDIR/bin" && ordinary "$binary" && [ -x "$binary" ] || { record failed input_unavailable; exit 25; }
    if command -v setsid >/dev/null 2>&1; then setsid "$binary" --watch 3<&- 9<&- </dev/null >/dev/null 2>&1 &
    else "$binary" --watch 3<&- 9<&- </dev/null >/dev/null 2>&1 & fi
    exit 0;;
esac

active || { record cancelled none; exit 0; }
lock || exit 0
if [ "$MODE" = --trigger ]; then
  authorization || { record failed authorization_rejected; exit 20; }
  boot_window || { record failed boot_gate_rejected; exit 20; }
  if claimed; then exit 0; fi
  mkdir "$DATA/boot-rescue-claim.$BOOT" || exit 20
  # A new current-boot physical gesture supersedes an expired continuation only.
  # Existing current-boot claims returned above and can never re-authorize deletion.
  rm -f "$DATA/boot-rescue-pending" || { record failed status_write_failed; exit 22; }
  record triggered none || exit 22
  identity || { record failed package_unavailable; exit 26; }
  ce_path || { record failed path_rejected; exit 20; }
  # PackageManager owns Android's full clearing semantics. A timed-out Binder request is
  # unknown (possibly still active in system_server): never race it with raw deletion.
  command -v timeout >/dev/null 2>&1 || { record failed unsupported; exit 25; }
  services=$(timeout -k 1 5 cmd -l 2>/dev/null); services_exit=$?
  [ "$services_exit" = 0 ] || { record failed clear_unknown; exit 23; }
  # AOSP indents service names. A valid bare list can confirm package is present;
  # only a complete list with the official header can prove that it is absent.
  service_available=$(printf '%s\n' "$services" | LC_ALL=C awk '
    {
      sub(/^[[:space:]]+/, ""); sub(/[[:space:]]+$/, "")
      if (NR == 1 && $0 == "Currently running services:") { header=1; next }
      if ($0 == "") next
      if ($0 !~ "^[A-Za-z0-9_.@:/-]+$") invalid=1
      else { count++; if ($0 == "package") found=1 }
    }
    END { if (invalid || !count || (!found && !header)) print "unknown"; else print found ? 1 : 0 }
  ')
  case "$service_available" in 0|1) ;; *) record failed clear_unknown; exit 23;; esac
  if [ "$service_available" = 1 ]; then
    response=$(timeout -k 2 20 pm clear --user 0 com.android.systemui 2>&1); pm_exit=$?
    case "$pm_exit" in 124|137|143) record failed clear_unknown; exit 23;; esac
    if [ "$pm_exit" = 0 ] && printf '%s\n' "$response" | grep -qx Success; then
      if ! unlocked; then defer user_locked ce; exit $?; fi
      if ! external_ready; then defer external_unavailable external; exit $?; fi
      stop_processes || { record failed reset_failed; exit 23; }
      pending_stage=external
      finish_private; exit $?
    fi
    # Only an explicit PackageManager failure or this precise pre-execution
    # protected-package rejection proves the remote clear is no longer pending.
    if ! printf '%s\n' "$response" | grep -Eq '^(Failed|Failure)$|^java\.lang\.SecurityException: Cannot clear data of protected package: com\.android\.systemui$'; then
      record failed clear_unknown; exit 23
    fi
  fi
  # A missing service or a definite rejection uses only this package's fixed verified roots.
  stop_processes || { record failed reset_failed; exit 23; }
  clean_dir "$DE" || { record failed path_rejected; exit 20; }
  if ! unlocked; then resume_processes; defer user_locked ce; exit $?; fi
  finish_private; finish_exit=$?
  if [ "$finish_exit" = 2 ]; then resume_processes; defer external_unavailable external; exit $?; fi
  exit "$finish_exit"
fi

# --resume can only complete CE/external from a current-boot native authorization.
# It never clears DE again after a successful early stage.
ordinary "$DATA/boot-rescue-pending" || exit 0
identity || { record failed package_unavailable; exit 26; }
if ! pending; then record cancelled pending_expired; exit 20; fi
case "$pending_stage" in ce_in_progress|external_in_progress) record failed clear_unknown; exit 23;; esac
attempt=0
while [ "$attempt" -lt 450 ]; do
  same_boot || { record cancelled pending_expired; exit 0; }
  active || { record cancelled none; exit 0; }
  if unlocked && external_ready; then
    # Re-check the exact package's user-0 UID before touching the remaining roots.
    identity && pending || { record failed authorization_rejected; exit 20; }
    stop_processes || { record failed reset_failed; exit 23; }
    finish_private; exit $?
  fi
  attempt=$((attempt + 1)); sleep 2
done
if unlocked; then record failed external_unavailable; else record failed user_locked; fi
exit 23
