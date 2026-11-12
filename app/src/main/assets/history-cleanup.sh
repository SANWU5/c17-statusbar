#!/system/bin/sh
# Explicit destructive maintenance; invoked only after the app's two confirmations
# and a durable framework reset. No exports, other apps or native SystemUI DBs.
umask 077
user=$1
case "$user" in ''|*[!0-9]*|??????*) echo error=invalid_user; exit 20;; esac
[ "$(id -u)" = 0 ] || { echo error=root_required; exit 21; }
no_links() {
  p=$1
  while [ "$p" != / ] && [ -n "$p" ]; do
    [ ! -L "$p" ] || return 1
    p=${p%/*}; [ -n "$p" ] || p=/
  done
}
trusted() {
  no_links "$1" && [ -f "$1" ] && [ "$(stat -c %u "$1")" = 0 ] || return 1
  permission=$(stat -c %a "$1")
  case "$permission" in ''|*[!0-7]*|?????*|*[2367][0-7]|*[2367]) return 1;; esac
}
module=/data/adb/modules/c17_performance
update=/data/adb/modules_update/c17_performance
data=/data/adb/c17-performance
for path in "$module" "$update" "$data"; do no_links "$path" || { echo error=path_link; exit 20; }; done
# Never drop ownership markers before restoring the exact artifacts managed by us.
worker=
for path in "$module" "$update"; do
  [ -e "$path" ] || continue
  trusted "$path/module.prop" && grep -qx 'id=c17_performance' "$path/module.prop" && trusted "$path/common.sh" && no_links "$path/disable" || { echo error=module_identity; exit 20; }
  worker=$path
done
if [ -d "$data" ] && [ -z "$worker" ]; then echo error=orphan_performance_records; exit 22; fi
for path in "$module" "$update"; do [ ! -d "$path" ] || touch "$path/disable" || exit 22; done
if [ -n "$worker" ]; then
  sh "$worker/common.sh" uninstall || { echo error=performance_restore; exit 22; }
fi
# A legacy backup module owns an opaque backup format. Let its audited uninstaller
# remove it; do not guess at files or silently erase restoration evidence.
legacy=/data/adb/modules/c17_resident_cache
if [ -e "$legacy" ] || [ -L "$legacy" ]; then
  trusted "$legacy/module.prop" && grep -qx 'id=c17_resident_cache' "$legacy/module.prop" && trusted "$legacy/uninstall.sh" && no_links "$legacy/disable" && no_links "$legacy/remove" || { echo error=legacy_identity; exit 20; }
  touch "$legacy/disable" && sh "$legacy/uninstall.sh" && touch "$legacy/remove" || { echo error=legacy_cleanup; exit 22; }
fi
targets=
for storage in /data/user_de /data/user; do
  host=$storage/$user/com.android.systemui
  for name in c17-resident-cache c17_plugins; do
    target=$host/files/$name
    no_links "$target" || { echo error=host_path_link; exit 20; }
    targets="$targets $target"
  done
  for name in c17_plugin_freeedit_v2 c17_qs_more_private; do
    for suffix in .xml .xml.bak; do
      target=$host/shared_prefs/$name$suffix
      no_links "$target" || { echo error=host_path_link; exit 20; }
      targets="$targets $target"
    done
  done
done
# Additional preferences come only from the validated plugin ownership inventory.
shift
for name in "$@"; do
  case "$name" in ''|*[!a-zA-Z0-9_.-]*|.*|-*) echo error=preference_name; exit 20;; esac
  [ "${#name}" -le 120 ] || exit 20
  for suffix in .xml .xml.bak; do
    target=/data/user_de/$user/com.android.systemui/shared_prefs/$name$suffix
    no_links "$target" || { echo error=host_path_link; exit 20; }
    targets="$targets $target"
  done
done
stopped=
resume_host() {
  for pid in $stopped; do kill -CONT "$pid" 2>/dev/null; kill -TERM "$pid" 2>/dev/null; done
}
trap resume_host EXIT
trap 'exit 24' HUP INT TERM
for pid in $(pidof com.android.systemui); do
  case "$pid" in ''|*[!0-9]*) continue;; esac
  kill -STOP "$pid" || { echo error=host_pause; exit 22; }
  stopped="$stopped $pid"
done
for target in $targets; do
  # Fixed roots + fixed basenames (or validated preference basenames). rm does not
  # dereference child symlinks. Never remove the host's parent or database folder.
  no_links "$target" || { echo error=host_path_link; exit 20; }
  rm -rf -- "$target" || { echo error=host_cleanup; exit 22; }
done
# Package manager removes CE/DE private files, databases, cache, configuration,
# imported assets and plugin library atomically for this user; the app then exits.
resume_host
stopped=
pm clear --user "$user" dev.puitheme.iosstatusbar || { echo error=app_cleanup; exit 22; }
echo state=cleared
