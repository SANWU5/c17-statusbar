# 卸载与数据归属

本文同步本地1.6.1/code58能力，尚未公开发布；最终构建、实机与卸载结果待复核，源码合同不等于实际卸载验收。当前操作见 [code58用户说明](LOCAL-1.6.1-CODE58.zh-CN.md)。

配置主存储、最后有效快照和迁移标记使用应用自己的设备保护 SharedPreferences。字体在私有 `files/fonts/`，通知图标在私有 `files/notification-icons/`，诊断日志在私有 `files/diagnostics/`，分享快照在应用 `cache/diagnostic-shares/`。SystemUI 实时读取使用限定 UID 的只读 Provider，开机快照通过官方 libxposed remote preferences读取，不在SystemUI数据目录另存配置或日志。

`allowBackup=false`、`hasFragileUserData=false` 与两套明确排除规则同时生效；旧备份规则和 Android 12+ 云备份/设备迁移规则均排除 CE/DE 数据。[Android 官方备份说明](https://developer.android.com/identity/data/autobackup)说明部分设备的设备迁移需要单独配置排除规则。

加载在 SystemUI 中的 `ModuleLifecycle` 接收系统保护的 `PACKAGE_REMOVED` / `PACKAGE_FULLY_REMOVED` 广播，仅匹配本包，并排除 `EXTRA_REPLACING`，覆盖升级不清配置。真正卸载时清除进程内已应用快照、停止诊断队列，并在主线程应用原生安全配置，释放圆角、清除按钮、状态栏副本、字体和图标的运行所有权。Provider 暂时失败会保留上一次配置；仅确认包已不存在时执行卸载释放。[Intent 官方文档](https://developer.android.com/reference/android/content/Intent)定义了这些卸载与升级标记。

模块阶段日志只通过私有、默认关闭的诊断通道写入，不再调用框架持久化日志。不会清除 LSPosed 的共享历史日志，也不会删除用户通过文档选择器明确导出的配置/字体/日志。正常卸载由 Android 删除本应用私有数据。

## 应用内维护与保留身份

关于页“清空历史日志”经确认删除本模块私有诊断历史和分享副本，不清配置、不清共享框架日志。“删除所有配置”经确认取消待确认数值试用，清主配置、恢复备份和旧框架镜像，删除私有导入字体/通知图片后恢复默认；框架暂未连接时，默认快照等待重连同步，失败或部分完成明确提示，不将暂存当作已同步。

删除配置保留免费声明确认、Root请求标识及桌面图标组件状态。免费确认在应用独立私有preferences；Launcher alias状态由PackageManager保存，不进入运行配置或导出。用户自行导出的文件、其他应用数据和共享目录不在维护删除范围。

应用内更新只在已授予Root且用户显式点击时执行，下载本项目正式更高版本，经大小、摘要、包名、版本及当前签名校验后覆盖安装；本模块缓存与Root安装暂存APK在结束/失败后清理，不保留长期更新文件。

官方框架镜像使用本模块独立group。本机历史只读schema已见module_configs到modules的ON DELETE CASCADE；这只是存储合同证据，没有为验证卸载用户模块，不宣称框架卸载清理已实测。

发布验证使用真实编译类检查卸载精确匹配、覆盖升级排除、原生恢复、缓存清空与日志关闭。未为验证而卸载用户当前模块，避免删除当前配置与作用域设置；这部分不能声称已经实机执行完整卸载。
