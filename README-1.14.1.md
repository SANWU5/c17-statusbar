# 更好的C17状态栏 1.14.1

作者：aiingjie · 包名：`dev.puitheme.iosstatusbar` · versionCode：29 · LibXposed API 101

[English](README.en.md) · [项目主页](https://github.com/SANWU5/c17-statusbar) · [下载正式版](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.14.1)

## 本版更新

- 主界面改为紧凑分类入口，宽屏使用双列，分类使用统一的线性图标。
- 统一开关、数值输入与设置行的外观和间距。独立功能的展开状态保持，返回页面后继续调节。
- HSB 颜色编辑按用途分组，改善卡片排版和安全间距。
- 本轮为设置界面升级，现有状态栏配置继续保留。

## 功能

实时网速、蜂窝信号、Wi-Fi、网络制式文字、时间、字体、电池、运营商文字及磁贴翻页均可独立启停。支持精细位置、大小、字重、间距与颜色调节，两位小数输入，以及超出滑条建议范围后的限时确认。

通知页、控制中心与锁屏的运营商内容分别配置。电池内电量与镂空闪电平滑切换，外形、文字与闪电颜色独立。字体支持系统、内置苹方与自选字体。

磁贴翻页保留原生滑动，边缘渐隐支持横竖屏独立配置。全局磁贴活动填充支持浅深主题、颜色、透明度与渐变，并保留 ColorOS 原有玻璃高光。支持 JSON 配置导入、导出，可选诊断日志及 GitHub 更新检测。

## 安装与更新

覆盖安装同签名 APK，在 LSPosed 中启用模块并选择 SystemUI 作用域。模块代码更新后重启 SystemUI；日常参数修改即时通知作用域应用。

设置页可检查 [GitHub 正式发布](https://github.com/SANWU5/c17-statusbar/releases/latest)，由用户选择下载安装。源码重新签名的 APK 不能覆盖现有不同签名的安装。

当前适配依据为 OnePlus/Oplus SystemUI。不同 ROM 的结构可能不同，大幅位移或字号可能与其他元素重叠，具体效果需在设备上确认。

## 构建与验证

Android Gradle Plugin 8.7.3、compileSdk 35、minSdk 26、targetSdk 35、Java 8；`io.github.libxposed:api:101.0.0` 为编译依赖。

本版已完成资源、生产源码、DEX 及独立源码编译，并核对 APK 签名、对齐和归档内容。**本轮未运行检查套件，未连接设备验证 UI**，此前的检查结果不计为本版通过记录。详见 [验证记录](VALIDATION.md)。

源码包包含生产源码、资源、公开说明和可复用检查源码。桌面 JSON 检查运行库不进入 APK；手机截图、私人配置、日志、原始系统 APK 及签名私钥不进入源码包。

## 许可证与资源

模块源码采用 **GNU GPLv3（GPL-3.0-only）**，见 [LICENSE](LICENSE) 和 [版权说明](COPYRIGHT.md)。分发修改版本须遵守 GPLv3 的对应源码、版权和许可要求。

安装包内附完整 GPLv3 副本，可从应用的许可入口离线查看。

第三方资源按各自许可处理，见 [第三方声明](THIRD_PARTY_NOTICES.md)、[字体来源](FONT.md) 与 [PUI 电池资源依据](docs/PUI-battery-evidence.md)。
