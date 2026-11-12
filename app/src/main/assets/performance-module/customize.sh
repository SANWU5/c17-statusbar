#!/system/bin/sh
# No system overlay or mount; the cache is a bounded native process.
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/common.sh" 0 0 0755
set_perm "$MODPATH/webui.sh" 0 0 0755
set_perm "$MODPATH/cache-service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/boot-rescue.sh" 0 0 0755
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
# The former cache-backup module is replaced: let its manager run its own uninstall (backup removal) at reboot.
OLD=/data/adb/modules/c17_resident_cache
if [ -d "$OLD" ] && [ ! -L "$OLD" ] && [ ! -L "$OLD/remove" ] && grep -qx 'id=c17_resident_cache' "$OLD/module.prop" 2>/dev/null; then
  touch "$OLD/remove" && ui_print 'C17: the old cache-backup module will be removed at reboot.'
fi
ui_print 'Status C17：系统UI防炸 · 常驻缓存 · 按需优化'
ui_print '防炸按键用法见 App 或 WebUI。触发会清空 SystemUI 数据。'
