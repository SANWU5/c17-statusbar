# 更好的C17状态栏 · 1.15.1-beta.1

作者：aiingjie · 包名：`dev.puitheme.iosstatusbar` · versionCode：40 · LibXposed API 101

**此为测试版，不要随意更新。** 本次重写涉及设置界面与 SystemUI 绘制，主要适配依据为 OnePlus 13 / ColorOS 17。更新前自行导出需要保留的配置，正式用户可继续使用 [1.15.0 稳定版](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.15.0)。

[English](README.en.md) · [更新记录](CHANGELOG.md) · [验证记录](VALIDATION.md)

## 本版变化

设置页使用与 LSPosed 管理器相同的 Miuix 组件体系，三个普通底栏入口为主页、配置、关于。液态玻璃实现已移除。配置按状态栏、通知栏、控制中心、锁屏整理，四个名称完整显示；关于页保留真实应用图标、名称和作者信息，弹窗位于导航上方。

统一圆角支持 0–80dp，包含磁贴、音乐、亮度、音量和设备卡片。重写后只调节实际曲率，保留滑块轨道高度、原生点击范围和动画；清除按钮复用缓存，保留独立动效与通知安全距离。状态栏右上角位置使用真实锚点，横向离页向上淡出、返页向下淡入。

状态栏新增通知图标区域的位置、大小、颜色和显示方式，可在有通知时只显示一个爱心，也可使用自定义文字或图片。PNG、JPEG、WebP 最多 2MB，导入后自动归一至最多128像素，保存于应用私有目录；图片与字体文件不嵌入配置 JSON。

除蜂窝数据箭头始终隐藏外，新安装的功能默认关闭；明确保存的开关和参数保留，开机自动加载。授予 Root 后允许未激活 LSPosed 时修改配置，安全模式恢复原生显示而保留用户配置。正常卸载清除本应用的配置与日志，不恢复云端/迁移备份，SystemUI 内存中仍加载的模块也释放样式。

## 使用与构建

同签名覆盖安装后，在 LSPosed 中启用模块并选中 SystemUI，重启系统界面以加载新代码。日常参数修改会即时通知作用域应用。设置页的更新检测仍面向正式版，不主动推荐测试版。

构建使用 JDK 17、Gradle wrapper、Android SDK 37、AGP 9.4.1、Kotlin/Compose 2.4.20 与 Miuix 0.9.4；Java hooks 保持 Java 8 字节码，Compose Kotlin 为 JVM 11，minSdk26/targetSdk35。Windows 可使用 `tools/Build-Compose.ps1`，具体见 [构建说明](docs/COMPOSE-BUILD.zh-CN.md)。

生产源码、公开资源、许可与检查源码可以公开。签名私钥、手机截图、录屏、原始 SystemUI、私人配置与日志不进入仓库。完整变化、验证范围和第三方版权分别见 [更新记录](CHANGELOG.md)、[验证记录](VALIDATION.md)、[第三方声明](THIRD_PARTY_NOTICES.md)。
