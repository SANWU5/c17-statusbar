# StatClock 原生字号和布局

## code74 当前实现：按完整字宽测量

用户最新要求解除时间限制。现在不按父槽位缩小字号、不裁切自定义时间、不用剩余宽度或历史 `clock_for_fake` 宽度限制测量。`availableWidth` 仅用于诊断，最终宽度为实际 `TextPaint.measureText`（包含原生 em 字距）加 compound padding 的向上取整结果。`widthPolicy=natural` 表示这条路径。

原生 `StatClock.onMeasure` 直接改 Paint，而 `TextView.setTextSize` 在字号相同时不会清理已有 Layout。二次 super 测量前通过缓存的 `TextView.nullLayouts()` 清理旧文字布局及 BoringLayout，避免完整格式沿用旧省略结果；只在开启的自定义 StatClock 测量中调用，不在 draw 或时间调度器中反复调用。自定义开启时拥有并关闭该时钟的 ellipsize/auto-size，关闭时恢复其原始策略。原生像素基准、一次比例应用和有证据的旧值兼容保持不变。

新增诊断为 `layoutCacheRebuilt`、`desiredWidth`、`outgoingWidth`、`nativePx` 和 `appliedPx`。不再提供 `fitScale` 或 `requestedWidth`；完整文本占位可能超过父容器剩余空间，不宣称取消物理屏幕边界。见 [code74 修复说明](LOCAL-CODE74.zh-CN.md)。

以下保留 code70–73 的排查证据与历史方案。涉及“预算上限”“自动适配”“自身裁切”的段落已被本节取代，不是当前行为。

---

`TextControls` 为状态栏时间保留原生像素基线。100% 表示当前 ROM 的原生字号，格式开关 `clock_enabled` 只负责替换文字；关闭格式不隐藏时间。

## 已确认的原生路径

现有原始 SystemUI DEX 中，`com.oplus.systemui.statusbar.widget.StatClock.onMeasure(int,int)` 从自己的 `Context.getResources()` 调用 `getDimensionPixelSize(stat_clock_size)`，直接写入 `TextPaint`，再测量文字并调用 `TextView.onMeasure`。该资源是 dp 资源，读取后的结果已经是 px，不能再乘 density 或 scaledDensity。

旧实现只在存在 `actualWidth` 字段时安装自定义 measure。没有这个可选缓存字段的 ROM 会保留 OEM Paint 写入，但模块的 Entry 仍可能保留初始化时的字号，绘制前再用它覆盖原生字号。这条路径可导致 100% 异常小。现在 measure 的安装不依赖该字段，基线来自真正的原生像素资源；可选字段只用于同步宽度缓存。

2026-10-04 的 PJZ110 只读采集确认当前原生 APK 与已有样本哈希一致，下面的父链和零宽证据适用于这台设备。PLK110 是此前另一台反馈设备，仍不能据此声称已确认其实际运行时字号；该设备需单独核对 `nativePx`、`resourcePx` 和测量日志。

## 测量接线

每个 StatClock 的 onMeasure：

1. `TEXT.beginClockMeasure(view)` 暂时恢复原生样式。
2. 执行原生 `chain.proceed()`，在 finally 中关闭 scope，原生样式采集后只应用一次比例。
3. 二次测量前调用 `TEXT.onNativeClockMeasure(view, incomingWidthSpec, nativeMeasuredWidth, actualWidthOrNegative)`，采集原生测量结果。缺少可选缓存字段时第四项为 `-1`。
4. 已开启该时钟配置时，`TEXT.prepareClockMeasure(view, originalWidthSpec)` 得到实际字宽与可用空间限制后的 spec。
5. 用原有 invokeSpecial 调用 `TextView.onMeasure`，存在 `actualWidth` 才更新它。
6. `TEXT.afterClockMeasure(view)` 记录最终布局宽度；原生测量、上限或最终宽度未变化时不重复记录本项日志。

字号不由剩余宽度反算，避免缩放相乘或自动适配导致越来越小。开启自定义大小期间隔离平台 uniform auto-size，关闭后恢复原有 preset。已调整的 StatClock 只在文字超出预算时裁自己的槽位，通知图标、整个左容器与正常字号的自由位置不被裁切。

可用空间优先由实际 `status_bar_start_side_content` 的两条重叠分支计算：总宽减去 `status_bar_start_side_content_for_fake` 通知分支、双方 margin/padding 和时钟横排父节点中的可见同级项。ColorOS 把时间与通知图标放在 FrameLayout 的叠放分支里，不能只按 LinearLayout 遍历，否则会把通知图标那一段也算给时间，造成时间越界、被系统截成省略号或挤占通知图标。布局不提供这对分支时，再读取专用 `clock_for_fake` 槽位；结构未知时保留原生 MeasureSpec。

格式或字号使时间文本超过真实槽位时，测量会按未缩放的基准宽度算一次适配比例，在当前槽位内完整显示；适配比例不写入用户配置，文本或槽位变化后可以恢复。日志同时记录 `requestedWidth`（适配前）、`desiredWidth`（适配后）、`availableWidth`、`outgoingWidth` 和 `fitScale`，不记录时间内容。

安全模式和模块移除状态下，根 Hook 直接执行原生 `onMeasure`；关闭时钟配置后不进行二次 super 测量，也不覆写原生宽度缓存。原生测量抛出异常时 scope 仍关闭，后续诊断、二次测量和缓存同步不执行。独立开启的全局字体功能仍按其自身设置工作。

## 旧比例兼容

- `clock_scale_basis_version=2`：当前原生 px 基线。新安装和用户主动编辑 `clock_scale` 使用此版本。
- `clock_scale_basis_version=1`：旧记录。只有缺少旧 `actualWidth`，且实际采集到的初始字号比确认资源字号至少小两倍、旧比例超过 250% 时，才推导初始 px / 资源 px 的兼容因子。不是按 density 或百分比猜测。
- `clock_scale_legacy_factor=0`：尚无证据；正数：已确认的兼容因子。版本 2 忽略该因子。

例如检查模型明确采集到初始 13px、原生 46px、旧比例 380% 时，保留 `13 × 3.8 = 49.4px`，不会再放大到 `46 × 3.8`。存入精确因子 `13/46` 后进程重建仍按 49.4px；用户编辑比例后同一事务切换版本 2 并清除因子。没有确认的低基线或存在原有测量缓存时不迁移。

旧 XML / 导入若有 `clock_scale` 而没有基线 metadata，在补全默认值前标为版本 1。显式导入同时提供版本与因子时保留原值；试用回退必须恢复两项原来的存在性和值。不要清空配置。

`TEXT.clockLegacyScaleFactor(view)` 返回确证因子或 0。SystemUI 不直接写 app 私有配置；若持久化采集结果，只能由现有已授权且版本一致的配置通道交回应用，并核对报告对应的原始 scale / revision，避免覆盖用户新编辑的版本 2。

## 检查与诊断

`TEXT.summary()` 包含 `nativePx`、`resourcePx`、`capturedPx`、`appliedPx`、`measuredWidth`、`availableWidth`、`legacyMeasureMissing` 和 `legacyFactor`。新增测量链字段为：

- `incomingMode` / `incomingWidth`：传入原生 StatClock 的 spec mode 和 size。
- `nativeMeasuredWidth`：原生 `onMeasure` 返回后的实际宽度，尚未进行模块二次测量。
- `nativeActualWidth`：原生缓存字段值；字段不存在或诊断读取失败时为 `-1`。
- `outgoingWidth`：模块选择的第二次 EXACTLY 测量宽度。
- `desiredWidth`：实际字体、字距和 compound padding 下显示完整文字所需的宽度。
- `widthLimited`：完整字宽是否超出已确认的可用预算。

`afterClockMeasure` 的尺寸日志同样包含这些新增字段，只在测量数据变化时输出。诊断不记录时间文字、格式文本或通知内容，不能将未知的 `-1` 当作实际零宽。

`StatClockSizingCheck` 验证原生比例、2000 次 OEM Paint 直接改写、1000 次稳定绘制不重读资源/不重排、晚到小字号 setter、density 变更、auto-size 恢复、旧比例有证据的兼容、显式 metadata 跨进程恢复、极大字号保留通知宽度及自身裁切。此检查应加入 `validation/run-checks.ps1`。

## PJZ110 原生证据和零宽修复

只读采集设备为 PJZ110，ColorOS 17 / Android 17，1440×3168，density 640（4.0），安装版本 1.8.1/code70。原生 APK 的 SHA-256 已与本地逆向样本核对：

| 样本 | SHA-256 |
| --- | --- |
| `SystemUI.apk` | `da83ae63b6cfc43a4ce57f21ee7a54444e14dada85b24ad36d2fe74181f0a927` |
| `SystemUIPlugin.apk` | `2cdd17e165fcfd2e257bf863c70d85f3978534381f7019038f1a5fad912667eb` |

`SystemUIService` 的现场 dump 显示 StatClock 为 `VISIBLE`、`alpha=1`，但 `actualWidth=0`、`measuredWidth=0`、已布局宽度为 0；直接父节点 `clock_for_fake` 也为 0 宽。外层 `status_bar_start_side_container` 已分配 666px，左 padding 为 78px，通知分支占 230px，时钟实际可用预算为 `666 - 78 - 230 = 358px`。因此时钟消失来自零宽反馈，并非透明度或真实剩余空间为零。现场比例 `110.52%`、基线版本 2、字重 824 不需要迁移或重置。

哈希一致的 `classes4.dex` 中，`StatClock.onMeasure(II)V` 位于 code offset `5041180`：普通和秒钟分支都从原生资源像素测量字宽并写入 `actualWidth`；指令 `00cc` 读该字段，`00d0` 用它生成 EXACTLY spec，`00d4` 调用 `TextView.onMeasure`。原传入的 widthSpec 不作为宽度上限。这是该原生类的测量约定，不能在模块二次测量时又把历史 WRAP_CONTENT child spec 当作第二个上限，否则一次传入零宽后就会把零写回原生缓存，并持续消失。

本轮只读 dump 证明实际零宽和仍有 358px 的外层预算；当时未安装新增诊断，不包含 live incoming spec。`AT_MOST 0` 反馈路径由原生 DEX、模块的第二次限制代码和匹配现场父链的回归模型共同确认，不能把回归模型的 spec 值称为已采集的实时值。新增字段用于下一次授权实机安装后直接核对。

## 状态栏宽度预算补充

原生资源表和 DEX 核对后的实际链路为 `StatClock` → `clock_for_fake`（FrameLayout）→ `status_bar_start_side_except_heads_up` → `status_bar_start_side_content`（WRAP_CONTENT FrameLayout）→ `status_bar_start_side_container`（width=0、weight=1 FrameLayout）。`status_bar_left_side` 是 tag，不是这版资源表中的 ID。`StartSideExceptHeadsUpLayout` 原生继承水平 LinearLayout，没有重写 onMeasure；onLayout 最终也调用 LinearLayout。

已直接读取哈希一致 APK 的 `res/layout/status_bar.xml`，并核对以下原生参数。表内 `-2` 为 WRAP_CONTENT，`-1` 为 MATCH_PARENT；这些是 XML 参数，不能与 dump 中测量后的 0px 混为一谈。

| XML 行 | 节点 / ID | layout_width | layout_height | 其他 |
| --- | --- | --- | --- | --- |
| 108 | `StatClock` / `0x7f0a0306` | -2 | -1 | `singleLine=true` |
| 102 | `clock_for_fake` / `0x7f0a0307` | -2 | -1 | FrameLayout，gravity 为垂直居中 |
| 89 | `status_bar_start_side_except_heads_up` / `0x7f0a0dfe` | -1 | -1 | 原生水平 LinearLayout，`clipChildren=false` |
| 77 | `status_bar_start_side_content` / `0x7f0a0dfc` | -2 | -1 | FrameLayout，`clipChildren=false` |
| 65 | `status_bar_start_side_container` / `0x7f0a0dfb` | 0dp | -1 | FrameLayout，`layout_weight=1`，`clipChildren=false` |

因此 `clock` 和直接父 FrameLayout 并没有固定 width=0 的 OEM 约定，只有最外层按权重分配宽度。外层已分配 666px 后，内层原生 FrameLayout/水平 LinearLayout 可重新测量 WRAP_CONTENT 槽位，使用恢复后的子测量宽度合并通知分支占位。DEX 的 `StartSideExceptHeadsUpLayout.onLayout(ZIIII)V` 指令 `001d` 直接调用 `LinearLayout.onLayout`，没有把子宽度强制写回 0 的自定义布局步骤。

边界仍需保留：若某一轮父 FrameLayout 自身拿到 EXACTLY 0 或 AT_MOST 0，它当轮最终宽度仍可能受该父 spec 限制，不能因为子 `actualWidth` 为正就宣称整个父链已显示。修复解除的是持久零宽反馈，后续恢复沿原生父测量和布局链完成，不额外排队反复 `requestLayout` 或递归测量。最终实机验收除时钟 `actualWidth` / measuredWidth 外，还须核对 `clock_for_fake`、`status_bar_start_side_except_heads_up`、`status_bar_start_side_content` 的 measured/laid-out width，以及最外层 start-side 实际分配宽度；直接父 0 宽仍可能裁掉正宽子 View。

时钟预算现在从最外层 `status_bar_start_side_container` 的已分配宽度计算，优先当前 measuredWidth，缺失时回退 layout width。内层 WRAP_CONTENT 上一帧宽度不参与上限，避免 100%→200% 时被旧槽位卡住。沿时钟路径扣除 padding、相对/绝对 margin 和水平 LinearLayout 中真正占位的兄弟；FrameLayout 的 overlay 不额外相加。code72 的通知分支优先当前正测量宽度，既不取旧布局宽度的较大值，也不把隐藏通知的子宽度相加。

允许不再使用 incoming spec 作第二次上限的范围，严格限定为上述连续四层父链：每层资源包均须为 `com.android.systemui`，资源名顺序一致，`clock_for_fake`、`status_bar_start_side_content`、`status_bar_start_side_container` 均须为 FrameLayout，`status_bar_start_side_except_heads_up` 必须为水平 LinearLayout；时钟本身必须是 StatClock 或其子类，外层可用预算已确认。最终宽度为真实自定义字宽与剩余预算的较小值，预算 0 仍得到 0，不凭最小宽度或屏幕百分比强行展开。

没有可确认的外层分配、未知父结构、纵向父链及首轮未分配布局继续由原生 MeasureSpec 约束。新增回归包含真实资源 ID 的四层嵌套、100%/200%/400% 连续扩大缩小、通知零宽及重叠子 View、当前测量已缩小但旧 layout 宽更大、相对 margin、RTL 与未知结构回退，以及 PJZ110 的 `666 - 78 - 230` 预算。原生测量收到 `AT_MOST 0`、`AT_MOST 1` 和 `EXACTLY 0` 时，该特定链连续 300 次恢复合理正宽；真实预算 0、未知容器、未分配外层和纵向链仍保持硬零限制。

本次本地检查通过 `StatClockSizingCheck` 13264 项、`TextControlsCheck` 12014 项和 `NativeClockMeasurementCheck` 7 项。它们验证原生约定、字号与宽度预算；未代替新包安装后的实际动画和视觉检查。原始 dump、截图、配置与 logcat 保存在 `.local-private/device71`，含私人内容，不应上传公开仓库。

## code72：通知溢出挤占时间的修复

code71 的预算代码对已有正宽通知容器仍递归取全部子项的总宽。原生 NotificationIconContainer 保留大量 Java VISIBLE 的通知子项，再用 `StatusBarIconView.getVisibleState()` 的 HIDDEN 状态隐藏它们；Java visibility 不是显示数量。这个总宽可远大于容器实际分配，错误压缩时间槽位，触发 TextView 的原生省略号。

最新只读 dump 确认手机安装 code71：原生时间文字仍完整，通知容器实际 230px、上限 3 个，并有大量原生 HIDDEN 项。这支持上述错误路径，但当前采集处于横屏全屏应用，不能称为已现场复现竖屏省略号。

修复以当前正测量宽度为准。仅在通知容器真实零宽时，用原生 `mMaxIcons`、实际 start/end padding 和 ICON/DOT/HIDDEN 恢复；HIDDEN 不占位，最多计一个 DOT。状态未知或接口不匹配则返回未知预算，保留原生测量约束，不猜数量。字体和用户配置未重置，旧比例兼容逻辑保持不变；没有关闭系统省略号来掩盖宽度问题。

新增 `NativeClockBudgetCheck` 372 项，覆盖 87 个 HIDDEN 不挤占时间、当前测量优先于旧布局、零宽原生恢复和未知状态回退。完整字宽额外计入 compound padding；尺寸诊断只记录数值，不记录实际时间和通知文字。新包仍需在竖屏确认完整时间与通知图标共存。

