#!/system/bin/sh
# Start independently and return immediately: never delay the early boot stage.
MODDIR=/data/adb/modules/c17_performance
case "$0" in "$MODDIR/post-fs-data.sh") ;; *) exit 20;; esac
for path in /data /data/adb /data/adb/modules "$MODDIR" "$MODDIR/boot-rescue.sh"; do [ ! -L "$path" ] || exit 20; done
[ -f "$MODDIR/boot-rescue.sh" ] || exit 20
for file in "$MODDIR" "$MODDIR/boot-rescue.sh"; do
  [ "$(stat -c %u "$file")" = 0 ] || exit 20
  permission=$(stat -c %a "$file") || exit 20
  case "$permission" in ''|*[!0-7]*|?????*|*[2367][0-7]|*[2367]) exit 20;; esac
done
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || exit 0
sh "$MODDIR/boot-rescue.sh" --start </dev/null >/dev/null 2>&1 &
exit 0
