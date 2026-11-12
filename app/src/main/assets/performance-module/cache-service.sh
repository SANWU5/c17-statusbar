#!/system/bin/sh
# Independent from ART: no interaction, thermal or battery wait is needed to serve existing bytes.
umask 077
MODDIR=${0%/*}
DATA=/data/adb/c17-performance
case "$MODDIR" in /data/adb/modules/c17_performance|/data/adb/modules_update/c17_performance) ;; *) exit 20;; esac
case "$1" in start|stop|status|purge) ;; *) exit 20;; esac
[ "$(id -u)" = 0 ] || exit 21
trusted() {
  p=$1
  while [ -n "$p" ] && [ "$p" != / ]; do
    [ ! -L "$p" ] || return 1
    p=${p%/*}
  done
  [ "$(stat -c %u "$1")" = 0 ] || return 1
  permission=$(stat -c %a "$1") || return 1
  case "$permission" in ''|*[!0-7]*|?????*|*[2367][0-7]|*[2367]) return 1;; esac
}
trusted "$MODDIR" && trusted "$MODDIR/module.prop" && grep -qx id=c17_performance "$MODDIR/module.prop" || exit 20
[ ! -e "$DATA" ] || trusted "$DATA" || exit 20
abi=$(getprop ro.product.cpu.abi)
case "$abi" in arm64-v8a|armeabi-v7a|x86_64|x86) ;; *) echo cache_running=0; exit 25;; esac
BIN=$MODDIR/bin/c17cached-$abi
trusted "$MODDIR/bin" && trusted "$BIN" && [ -x "$BIN" ] || { echo cache_running=0; exit 25; }
case "$1" in
  status) "$BIN" --status 2>/dev/null | sed -n '/^cache_[a-z_]*=[a-z0-9_-]*$/p'; exit 0;;
  stop|purge)
    "$BIN" --stop >/dev/null 2>&1 || :
    for attempt in 1 2 3 4 5; do
      "$BIN" --status >/dev/null 2>&1 || break
      sleep 1
    done
    if "$BIN" --status >/dev/null 2>&1; then exit 23; fi
    if [ "$1" = purge ]; then
      # No recursive delete; reject unexpected nested directories or links.
      if [ -e "$DATA/cache-v1" ]; then
        trusted "$DATA/cache-v1" && [ -d "$DATA/cache-v1" ] || exit 20
        for f in "$DATA/cache-v1"/* "$DATA/cache-v1"/.new.*; do
          [ -e "$f" ] || [ -L "$f" ] || continue
          [ -f "$f" ] && trusted "$f" || exit 20
          rm -f "$f" || exit 22
        done
        rmdir "$DATA/cache-v1" || exit 22
      fi
    fi
    exit 0;;
esac
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || exit 27
mkdir -p "$DATA" && chmod 0700 "$DATA" || exit 22
# Pipe PackageManager output. Passing an adb_data_file stdout FD to Binder is rejected on some ROMs.
temp=$(mktemp "$DATA/.clients.XXXXXX") || exit 22
cmd package list packages -U 2>/dev/null | awk '
  $1=="package:com.android.systemui" || $1=="package:com.android.launcher" || $1=="package:dev.puitheme.iosstatusbar" {
    sub(/^package:/,"",$1); sub(/^uid:/,"",$2); if($2 ~ /^[0-9]+$/) print $2 " " $1
  }' > "$temp"
if ! grep -Eq '^[0-9]+ com.android.systemui$' "$temp"; then rm -f "$temp"; exit 26; fi
chmod 0600 "$temp" && mv -f "$temp" "$DATA/cache-clients" || { rm -f "$temp"; exit 22; }
if "$BIN" --status >/dev/null 2>&1; then exit 0; fi
# common.sh owns FD 9 while checking ART. A resident child must not retain that
# guard after its caller dies; close it even when launched from another manager.
if command -v setsid >/dev/null 2>&1; then setsid "$BIN" --serve 9<&- </dev/null >/dev/null 2>&1 &
else "$BIN" --serve 9<&- </dev/null >/dev/null 2>&1 & fi
