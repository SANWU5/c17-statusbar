# PUI 横向电池资源依据

由用户授权从手机中读取已经停用的 PUI Theme For OPlus17 模块。原模块版本：17.0.0.119。读取资源用于本模块的横向电池样式，不启用、修改或重新分发整个原模块。

资源 APK：`PuiThemeBatteryHorizontal.apk`，包名 `com.android.systemui.PuiThemeBatteryHorizontal`，overlay 目标 `com.android.systemui`。

APK SHA256：`c8955b51c7139d7fa262817d9f91f8330586a0fd47a7d20d2ec52ef8ac8c2200`。

## 实际保留的资源

| 内置资源 | 原始资源 | 源码 SHA256 |
|---|---|---|
| `c17_pui_battery_bg.xml` | `stat_battery_horizontal_bg.xml` | `6eca32dfb83f0d6ec5e10dca240dc8875d157cee923dda589eb92bd8defc00db` |
| `c17_pui_battery_frame.xml` | `stat_battery_horizontal_frame.xml` | `4a56eb73eafccc8f4cd104828beb7b2211a4ec6c732184c524862e57dffa696f` |
| `c17_pui_battery_outer_bg.xml` | `stat_battery_horizontal_percent_out_bg.xml` | `fab4282733c81e6bf09b9f05468bf35fa6ffb7add7b1f1415335df6c1aa0af48` |
| `c17_pui_battery_outer_fill.xml` | `stat_battery_horizontal_percent_out_no_padding_progress_bg.xml` | `c93c6e273351bb25178f8e35de1f73415198e37d5d76003ed30796812bc1c6ab` |

`stat_battery_horizontal_progress_bg.xml` 与 background 资源逐字节相同。`stat_battery_horizontal_percent_out_no_padding_bg.xml` 与外部百分比 background 资源也相同。仅保留各自一份文件；绘制时创建独立的 Drawable 实例，避免颜色和电量互相影响。

## 尺寸与字体

原 PUI 横向电池显示尺寸：29 × 17 dp。图标内部百分比字号为 10 dp。PUI 没有覆盖 `status_bar_battery_horizontal_padding_left/right/top/bottom`，因此继续使用当前系统的这些 padding。

已对手机最新 `SystemUI-v20.apk` 的 `classes4.dex` 单独读取电池类。`HorizontalBatteryContentDrawable` 仅在构造时把 `battery_percent_in_text_size` 资源赋给 percentInPaint，没有乘 native scaleFactor。`BatteryViewBinder.bind$updateBatteryIconStyle` 的 scaleFactor 只乘 ImageView 宽高。模块依照该行为保持 10 × density 字号、29 × 17 × density × native scaleFactor 图标尺寸；用户设置的整体缩放通过 Canvas 同时缩放图标和内容。

原 PUI 还覆盖了进度 corner 与几个 progress 边距资源。当前系统的横向进度 Drawable 实际是一个 LayerDrawable 包裹横向 ClipDrawable，直接引用 progress_bg；这一绘制路径没有使用上述边距。模块使用相同包装，真实电量以 level × 100 输入。

## 接入与恢复

保留系统电池 Drawable、其 RTL 绘制、独立 saveLayer、实时电量和现有内部数字／闪电渐变逻辑。只替换横向 Drawable 的 outside、inside、frame 及外部百分比的两个形状字段。系统重新创建这些字段后，模块先保存最新的原生版本，再应用 PUI。切到系统样式、关闭电池样式或电池总开关时恢复原生字段、字号和 ImageView 尺寸。

当前 PUI 资源适配覆盖横向电池。竖向、圆形和其他系统电池形态继续由系统绘制。

## 已完成的本地检查

`PuiBatteryStyleCheck` 使用当前 native 字段结构和编译后的生产类，验证默认 PUI、精确资源选择、实时电量、原图标／字号／尺寸恢复、native 字段重建、独立样式开关、总开关、释放和 native scaleFactor，31 项通过。图像实际效果由主任务另行进行实机核对。
