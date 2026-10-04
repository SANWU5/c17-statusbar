# 1.8.1 / code74 本地修复

本轮处理状态栏时间省略号和锁屏顶部模糊。按最新要求移除时间的模块宽度限制、槽位适配缩小和模块裁切；保留格式、字号、字重、位置与旧比例兼容，不重置用户配置。

## 状态栏时间

ColorOS `StatClock.onMeasure` 直接把资源像素写入 `TextPaint`。Android `TextView` 的相同字号 setter 不会清空已有文字布局，这使原来的 `Layout/BoringLayout` 可能继续使用旧宽度和省略结果。此前用历史槽位宽度反算字号又增加了限制。

现在先执行原生测量并采集原生 px，再应用一次用户样式；完整字宽由实际 Paint 测量，字距只算一次，并计入 compound padding。仅在已开启自定义的 StatClock 二次测量路径清空文字布局缓存。关闭其省略策略和自动字号适配，完整文本按请求大小测量；关闭配置后恢复原生策略。绘制与时间刷新不增加循环重测量。

完整回归发现未开启大小调整的原生文字也进入了自定义字号范围守卫，现已恢复原生数值直通；旧比例补偿也只在自定义大小开启时应用。保留原测试期望，并增加关闭字号和总开关时的检查。

`100%` 继续对应确认的 ROM 原生字号。旧低基线大比例使用已有证据兼容，不把历史 `380%` 强制改为 `250%`，也不按新基线盲目重置。取消模块限制后，过大的字号或过长格式仍可能占用其他图标空间；本包不声称父容器和屏幕的物理边界消失。

诊断提供 `nativePx`、`resourcePx`、`capturedPx`、`appliedPx`、`nativeMeasuredWidth`、`nativeActualWidth`、`desiredWidth`、`outgoingWidth`、`measuredWidth`、`layoutCacheRebuilt` 和 `widthPolicy=natural`。`availableWidth` 只用于观察，不再影响字号和测量宽度。不记录实际时间和通知内容。

## 锁屏模糊

移除多段模糊层和均匀模糊原型。缓存锁屏壁纸区域的硬件图像，用系统 `RenderEffect` 模糊后，通过连续渐变 alpha 过渡到下方原壁纸。上端保持模糊，向下平滑变清晰；颜色遮罩采用相同过渡。设置中的“由虚到实过渡高度”保留原配置键。

取样严格限于真实壁纸子树，排除模块自有层和其他窗口，不读取前景、CPU 像素或保存截图。输出使用 RGBA 非 opaque buffer，仅位于壁纸窗口内，时间、通知和状态图标继续由系统绘制。

壁纸原生提交新帧、设置或几何变化时更新缓存；锁屏显隐进度只改变合成器 alpha。原生冻结前隐藏自有层，防止重复烘入。无有效硬件源时等待下一次原生壁纸帧，不随 scrim 连续重试；不支持的接口和映射保留系统画面。

## 手机范围与待确认

手机实际安装 code73，本轮只读配置、SystemUI 布局和原生 framework 接口。没有切页、修改设置、安装或重启。原始设备资料留在忽略目录，不上传。本包是 code74，BUILD_TOKEN 为 `c17-runtime-20261004-clock-unrestricted-continuous-wallpaper-code74`。

安装后由用户确认：

1. 时间分别使用 `HH:mm`、`HH:mm:ss`，开关格式并改变字号；全文应完整，`100%` 应为原生基准，不再出现 `02:...`。较长格式和大字号按实际需求检查占位。
2. 锁屏顶部应从模糊平滑过渡到清晰，没有水平条纹；时间、日期、通知与图标保持清晰。
3. 检查息屏/AOD、唤醒和解锁时没有漏亮、残留或前景模糊。视觉、GPU 时延和耗电需实机验证，桌面检查不替代这些验收。

## 最终验证结果

- Debug/Release、lintVitalRelease 通过。
- 101 组真实 Release 生产 classes 检查通过。重点专项包含 StatClockSizing 13558、TextControls 12014、NativeClockMeasurement 7、NativeClockBudget 385、CarrierPanels 254、连续渐变 Renderer 85113、WallpaperBlurLayer 1525 和几何 16182 项。循环断言数不等于独立场景数。
- 1192 个 Debug/Release Java class 均为 major52；v2 签名及 16KB 对齐通过。
- APK：`artifacts/本地修复-1.8.1-code74/C17-1.8.1-code74.apk`，22326560 字节。
- SHA-256：`c4abe0a736057b5cf66c8318a09c5e88699dfe21ac5bcff146f3918db1be5096`。

本地检查确认测量、连续 shader、缓存和释放合同，不替代实机视觉、GPU 时延或耗电验收。没有安装、发布、QQ 同步或电源操作。
