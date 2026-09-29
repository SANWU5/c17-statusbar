# 验证记录

版本：1.12.2 · libxposed API 101

## 构建与本地检查

发布构建通过 Android 资源编译、Java 与 DEX 编译、APK 签名及对齐检查。内置可变字体与源码字体一致，保留所需字符和 wght 100–900 字重轴。

32,793 项本地逻辑检查覆盖网络名称、颜色透明度、时间与信号等级、设置精度、电池充电状态、HSB/RGB/ARGB 转换和网络绘制容器识别。另有 Android 软件 Canvas 检查入口，见 `validation/native-canvas/README.md`。

网络容器检查包含 OnePlus/Oplus SystemUI 使用的 `stacked_mobile` 槽位。修复前，回归检查能复现生命周期识别漏掉该槽位；补全之后，网络主控件、ComposeView 和 AndroidComposeView 的边界检查通过。

调色盘检查覆盖 10,000 组可重复生成的颜色，与 JDK 独立 HSB 实现进行对照，并检查透明度模式、无效输入和小数精度。调色盘布局为指示圈保留边缘空间，数值卡片之间留有间距。

本地状态检查使用 Android 桩，不能模拟所有 ROM 的硬件绘制层。当前实现以 OnePlus/Oplus 系统栏为适配目标；其他系统需单独验证。

源码检查入口见 `validation/README.md`。验证脚本不读取设备数据，也不需要设备标识、偏好设置备份或屏幕截图。
