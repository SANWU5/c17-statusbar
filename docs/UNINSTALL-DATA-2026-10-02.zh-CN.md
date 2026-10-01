# 卸载与数据归属

配置主存储、最后有效快照和迁移标记使用应用自己的设备加密 SharedPreferences。字体在私有 `files/fonts/`，通知图标在私有 `files/notification-icons/`，诊断日志在私有 `files/diagnostics/`，分享快照在应用 `cache/diagnostic-shares/`。SystemUI 仅通过限定 UID 的只读 Provider 读取，不在 SystemUI 数据目录落盘配置或日志。

`allowBackup=false`、`hasFragileUserData=false` 与两套明确排除规则同时生效；旧备份规则和 Android 12+ 云备份/设备迁移规则均排除 CE/DE 数据。[Android 官方备份说明](https://developer.android.com/identity/data/autobackup)说明部分设备的设备迁移需要单独配置排除规则。

加载在 SystemUI 中的 `ModuleLifecycle` 接收系统保护的 `PACKAGE_REMOVED` / `PACKAGE_FULLY_REMOVED` 广播，仅匹配本包，并排除 `EXTRA_REPLACING`，覆盖升级不清配置。真正卸载时清除进程内已应用快照、停止诊断队列，并在主线程应用原生安全配置，释放圆角、清除按钮、状态栏副本、字体和图标的运行所有权。Provider 暂时失败会保留上一次配置；仅确认包已不存在时执行卸载释放。[Intent 官方文档](https://developer.android.com/reference/android/content/Intent)定义了这些卸载与升级标记。

模块阶段日志只通过私有、默认关闭的诊断通道写入，不再调用框架持久化日志。不会清除 LSPosed 的共享历史日志，也不会删除用户通过文档选择器明确导出的配置/字体/日志。正常卸载由 Android 删除本应用私有数据。

发布验证使用真实编译类检查卸载精确匹配、覆盖升级排除、原生恢复、缓存清空与日志关闭。未为验证而卸载用户当前模块，避免删除当前配置与作用域设置；这部分不能声称已经实机执行完整卸载。
