# 更好的 C17 状态栏 1.12.2

[English](README.en.md)

作者：aiingjie · 包名：`dev.puitheme.iosstatusbar` · libxposed API 101

专为 SystemUI 设计的 LSPosed 状态栏模块。

[许可证](LICENSE) · [第三方声明](THIRD_PARTY_NOTICES.md) · [字体来源与说明](FONT.md) · [验证记录](VALIDATION.md)

## 构建

在 Android Studio 中打开仓库根目录。使用 JDK 17、Android SDK Platform 35 和 Gradle 8.9；构建任务为 `:app:assembleDebug`。Gradle 会下载 Android Gradle Plugin 8.7.3 及 libxposed API 101 编译依赖，需要 Google Maven、Maven Central 与 Gradle Plugin Portal。

## 功能

- 网速、蜂窝、Wi-Fi、网络制式文字、时钟、下拉文字、字体与电池分别设置。
- 位置、大小与颜色；HSB 调色盘；颜色透明度跟随系统或自行设置。
- 真实蜂窝信号、单排布局、统一网络制式文字。
- 自定义状态栏时间格式与下拉运营商文字。
- 系统字体、内置可变苹方字体和导入个人字体。
- 保留原电池外形，提供电量文字与充电闪电动画及独立颜色控制。

适配实现面向 OnePlus/Oplus SystemUI；其他系统的内部控件和状态栏实现可能不同。

## 隐私

设置储存在本机，仅由模块与 SystemUI 读取。源码仓库不包含设备序列号、个人设置导出、屏幕截图、调试日志、签名密钥或密码。

## 许可证

模块源码按根目录 MIT 许可证发布。内置苹方字体受其单独来源说明约束，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。

