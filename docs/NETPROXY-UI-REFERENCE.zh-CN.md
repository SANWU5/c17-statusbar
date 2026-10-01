# NetProxy 设置界面参考与 C17 实现

本次依据用户手机里的 NetProxy 首页、设置页截图，以及从该手机提取的 APK 进行 UI 分析。只核对布局、文字、颜色、圆角和浮动底栏；没有使用其网络代理代码，也没有整体移植第三方界面实现。

## 参考版本与分析范围

- 包名：`com.fanjv.netproxy`。
- APK 版本：`8.0.0`，`versionCode=877`；`minSdkVersion=31`、`targetSdkVersion=37`。
- APK SHA-256：`27FCB6296A0C3E270959D85209DAFB288928A65C741CB79B7E624B84030020C8`。
- 本地提取文件：`app/build/ui-consistency-fix/netproxy-reference/netproxy.apk`。
- 截图：`app/build/ui-consistency-fix/clock-reference/netproxy-home.png`、`netproxy-settings.png`。
- 使用 JADX 1.5.6，按 UI 调用链单类导出。产物保存在上述 `netproxy-reference` 目录，属于构建诊断文件。

该 APK 经过 R8 混淆。部分被引用的方法无法完整反编译，JADX 会以非零状态退出；下表仅记录已恢复的方法中可以直接核对的值，不把混淆后的不确定变量或截图估算写成原代码参数。

## 从 APK 直接确认的结构

`com.fanjv.netproxy.MainActivity.onCreate()` 创建 Compose 容器 `x00`，设置窗口边缘布局、切口模式和导航栏对比度；主界面并不是传统的 XML 布局。资源主题继承 `Theme.Material.Light.NoActionBar`，资源中的 XML 布局主要是其他辅助页面。

界面调用链为 `MainActivity → f.case18 → n.case20 → z43.a → ah1 → sg3.i → sg3.e`。`sg3.e` 的四个主页面通过枚举 `ae.n` 和 Compose 页面容器切换。设置根页面是 `vf2.a()`，标题由 `gq → mq.case1 → xh2.f → xh2.h` 绘制；设置列表由 `nl2.case0 → vr2 → fu5.l/fu5.a` 分组绘制。

`z43.a()` 读取的 UI 默认值为：`enable_smooth_corner=true`、`enable_blur=true`、`enable_floating_bottom_bar=true`、`enable_floating_bottom_bar_blur=true`。资源说明将平滑圆角标为 Miuix 效果，将浮动底栏标为 Apple 风格。

## 可核对的参数与 C17 对应

| 项目 | NetProxy APK 的直接证据 | C17 本次实现 |
| --- | --- | --- |
| 强调色 | `rx.a()` 第一个颜色 `4281631487L`，由 `qx.k()` 取出，即 `#3482FF` | 使用相同蓝色 |
| 页面背景 | `rx.a()` 第 34 个颜色 `4294440951L`，由 `qx.m()` 取出，即 `#F7F7F7` | 使用相同浅灰 |
| 白色卡片 | `fu5.a()` 使用 `qx.n()`，对应 `vw.d` 的白色 | 白色、无硬描边 |
| 大标题 | `xh2.h()` 使用 `p43.k` 和 `op0.l`；`mt2.case5` 对应 32sp，`op0.l=400` | 竖屏 32sp、常规字重；横屏 28sp 以节省高度 |
| 卡片圆角 | `fu5.a()` 的首尾圆角 `16.0f` | 分组卡片统一 16dp |
| 列表侧边留白 | `nl2.case0` 对滚动列表传入水平 12dp | 内容外侧 12dp；标题另有 12dp 内侧留白 |
| 浮动底栏 | `ni3.e → n21 → o21.a()` | 独立的四入口导航，实际切换页面 |
| 底栏左右边距 | `o21.a()` 的 `nm.V(...24,0,24,f,2)` | 屏幕安全区域内左右各 24dp |
| 底栏高度与内距 | `o21.a()`：64dp 高、四边 4dp 内距 | 相同尺寸 |
| 选中胶囊 | `o21.a()`：56dp 高、水平 4dp 内缩；`jb2.a` 的圆角为 50% | 56dp 高、胶囊形状、200ms 滑动切换 |
| 底栏图标 | `l21` 主列表的 `zp2.k(...22f)` | 22dp，独立清晰的前景绘制 |
| 底栏文字 | `l21` 使用 `jf2.d(11)`、`op0.l`、单行 | 11sp、400、单行 |
| Android 底部距离 | `n21` 的 `g12` 枚举映射确认 Android 走 `inset > 0 ? inset + 8dp : 36dp`；20dp 是 iOS 分支 | 根布局提供导航 inset + 8dp；无 inset 时补至 36dp，避免额外重复加 16dp |
| 玻璃底色 | `o21.a()` 在玻璃开启时将表面色 alpha 设为 `.4f` | 40% 白色底色，并添加柔和边缘高光 |
| 底栏阴影 | `o21.a()` 的 `vl2` 是 Shadow：半径 10dp、偏移 0、alpha `.2f` | 10dp 柔和阴影与 Android 原生轮廓阴影 |
| 原 APK 玻璃处理 | `f90.case22` 调用 `sg3.m(density * 4, density * 4)`，再使用 `zc1.a(density * 24, density * 24, ...)` 的 `LiquidGlassLens` | 自己实现局部背景模糊和浅边缘折射，不复用其 Compose/Shader 实现 |

`sg3.m()` 是独立的两方向模糊管线，可看到 `in_blurOffset`、`in_blurWeight`、`in_maxCoord` uniform、按强度降采样，以及尺寸/参数未变时复用计算结果。这确认参考底栏处理了背景，而不是只添加一个白色圆角框。

## C17 的实现取舍

C17 沿用原项目的 Android View/Java 技术，不增加 Compose 或 Miuix 依赖。`MainActivity.java` 提供“总览 / 状态栏 / 通知中心 / 更多”四个实际页面；总览展示激活状态、功能计数和常用设置，详细页保留现有全部功能。菜单图标为黑色，总览功能图标与选中入口使用蓝色。品牌图标仅在关于区域使用新 `ic_c17_launcher` 资源。

`SettingsGlassPanel.java` 的背景源是滚动内容，底栏是其兄弟视图，所以不会把底栏前景再次截入背景。捕获范围只覆盖底栏，降采样比例最多 `.16`、宽度最多 220 像素；像素缓存重复使用。只在滚动、切页、设置或预览变化时更新，32ms 限制连续更新频率，不设静止轮询。模糊后对胶囊边缘做轻微双线性重采样，并叠加透明底色、高光和阴影。暂停时取消动画和延迟更新，销毁时回收缓存。

原 APK 的 `LiquidGlassLens` 使用 GPU Shader，C17 使用低分辨率 CPU 局部重采样；折射强度与高光并非逐像素相同。这是本项目为了保持依赖与运行开销受控而采用的独立实现。200ms 选中动画也属于 C17 自己选择的参数，未将其标成 APK 原值。

“通知栏大时钟”新增独立页面，使用 `NotificationBigClockSettings` 的正式配置键，并提供明暗场景、展开/收起演示。收起预览宽度比例跟随运行时的 `.78`，垂直位移范围对应运行时的 `-80..160dp`。原通知栏时钟与通知页运营商设置继续显示冲突说明，不清除横屏或控制中心保存值。

## 验证边界

本次 UI 代码已通过 Android 35 + Xposed API 101 的定向 Java 8 编译，`git diff --check` 无空白错误。底栏生命周期、兄弟视图捕获和大钟键/默认恢复已由另一个代理只读复核。手机上的最终视觉、滚动和触控由根代理在统一构建后验证；不能把定向编译称为整包或最终实机验证。
