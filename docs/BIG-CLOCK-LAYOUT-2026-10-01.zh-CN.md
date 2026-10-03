# 通知页大时钟布局升级

**历史实现记录**：下方1.15.0及更早布局、数量和设备状态仅记录当时迭代。当前本地1.6.1/code58使用独立竖/横屏大时钟与独立整体/同类堆叠，操作见 [code58说明](LOCAL-1.6.1-CODE58.zh-CN.md)，新版构建和实机待最终复核。

## 1.15.0 本地构建结果

native28 已完成构建与签名/对齐核对，版本 `1.15.0` / `30`，SHA256 `2d0f958dd88502da6451a7085bc02e2315cb6f6f129d47de86c56ce80dd17828`。可见薄窗路径、背景与 Spotlight 共用圆角 tuple；没有临时清零 clipTop/overlap，恢复失败保留弱所有权直到原生路径成功重算。折叠完整通知最多 3，后方最多 3 层宽度可调底边，保留所有真实通知。

**本版未安装到手机核对缺角或动画，native27 的复现结论继续有效。** 以下是历史迭代过程；新构建的交付入口见 [1.15.0](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.15.0)，不将编译或旧版静态恢复作为实机通过证据。

## 以下为发布前迭代记录

日期：2026-10-01。基于迁移包保留的本地未提交源码开发。

## 最新构建与设备状态

**手机当前为已安装、已激活的 native27，SystemUI `6055`→`15947`。** 该开发包沿用 `1.14.1` / `29` 和匹配手机开发包的本机测试签名；安装与激活不代表瞬态交互通过。下一发布目标为 `1.15.0`，最终版本、哈希与发布链接由根代理回填。

**用户最新反馈 native27 仍复现尾角截断，尚无实机修复通过结论。** human26 的 PTS `27.1192`、`27.1278`、`27.2106` 仍保留为历史连续过程证据。新薄窗解算仅在本地源码，native19 的有限观察和静态恢复图不能替代对新方案的核对。

| 状态 | 标识与证据 |
| --- | --- |
| 当前 native27 | 已构建、安装并激活；用户仍反馈尾角截断 |
| 当前 APK SHA256 | `8bdb62989896aeb6fef38855f5a61d577303065fee215461d9d8f0b7b7cdccc7` |
| 产物路径 | `app/build/bigclock-v2/c17-statusbar-1.14.1-standalone.apk`；后续构建覆盖同一路径，不能沿用旧哈希 |
| 当前 SystemUI | native27，PID `6055`→`15947`，已激活 |
| 本地后续候选 | 独立解算可见薄窗的 OEM 圆角；源码已落盘，尚无实机核对 |
| 手机临时设置 | diagnostics 已恢复 `false`，stay-awake 已恢复 `0`；用户已离开，不再操作手机 |
| 清除按钮材质证据 | native23 日志 `selected=2/3`、`prepared=true`，四项 CLEAR_ALL 的前景槽已命中 |
| 有限人工观察 | native19 六次实际快下拉（`2+4`）连续帧未再观察到缺角；不作彻底结论 |
| 近期构建历史 | native16、native17 已安装；native18 仅构建；native20、native21 仅构建未安装；native22 PID `29859`→native23 `7179`→native24 `7407`→native25 `4052`→native26 `6055`→当前 native27 `15947` |

新薄窗方案直接采用原生 `getRoundedRectPath(int,int,int,int,float,float,Path)`，窗口以真实 `clipBounds`、`actualHeight - clipBottomAmount - getActualClipHeight()` 取交集，圆角限制到半高与半宽；路径、背景玻璃与 Spotlight 使用同一 tuple。已取消旧的临时 clipTop/overlap 清零，未知接口及非薄窗保持本次原生结果；异常恢复只有在原生重算成功后才释放弱所有权。这是静态源码说明，**不能写为 native27 的实机修复结论**。

用户最新授权同时包括配置持久化、消除不必要计算、媒体背景与反色 glow、整套 HyperOS 风格 UI，以及完成后 GitHub 发布和微信通知。集成与发布由根代理完成；运行时计算去重、已有息屏调度与持久化边界见 [持久化与功耗优化](PERSISTENCE-PERFORMANCE-2026-10-01.zh-CN.md)。本轮没有实测功耗百分比，也不增加测试脚本或自动测试。

此前曾出现“正常→缺角→恢复”，已纠正仅凭静态恢复图判断修复的结论。native19 的六次连续过程只能支持当前有限观察，**不能写为瞬态缺角彻底修复或所有场景过关**。native19 底部回弹录屏混有 USB 弹窗，未定位白线像素，不能写为白线核对通过；native21/22 白线与完整 mask 修正已进入当前安装包，完整底部后续录屏仍待根代理核对。native24 静态图不能代替动态宽度与尾缘的连续过程。native15 横滑记录混有用户操作与长按上下文，没有侧滑场景通过结论，也没有为验证删除通知。

历史上 native13 的触摸 hook 获取继承字段 `mView` 失败，native14 改为沿父类查找并构建安装；native12 曾实际安装激活。上述状态不能替代当前构建的场景核对。

native12 的私人 17 秒录屏 `native12-manual.mp4` 支持有限观察：安全区完整通知清晰；卡片停在边缘过渡带仍有对应效果；一次实际分钟数字使用原生切换；最终关闭后底层应用清晰。后续用户仍报告入场缺失、尾层同宽/截断、右侧图标位置及通知侧滑被整页抢走，**不能把这些记录写成全部问题已修复**。

## 设计约束

- 仅接管竖屏通知页，横屏、控制中心、锁屏保持原行为。
- 大钟采用设备原生插件的独立数字视图和切换控制器，不挪用锁屏实例；不兼容时回退文字时钟并记录原因。
- 原生字体上划用已确认的 `HGHT` 轴改变真实轮廓高度，`wght` 始终为用户设置的同一字重；未知轴字体自然等比收起，不非均匀缩放外层字形。
- 网速、蜂窝、Wi-Fi、电池按最新要求固定在真实 Phone 几何锚点，下拉、收起和切页不再增加整体位移；保留两来源各自的原生尺寸、内部位置与背景配色，并复用原生透明度交接。
- 完整通知数量可配置，当前用户保存值为 `4`；后方最多三层只露底边，每层宽度独立配置，默认 `96%/92%/88%`。通知点击、分组、展开、侧滑和清除继续使用原生实现。
- 时钟/日期入场、通知空间边缘和清除按钮外观分别配置，不把模糊应用到顶部状态图标或整个页面。
- 数值支持两位小数及直接输入，超建议范围使用既有十秒确认。预览不代表真实原生动画或材质验证结果。

## 当前实现

### 字形与原生数字动画

`NotificationNativeClock` 从已安装的 `com.oplus.keyguard.personality.clocks` 插件创建独立数字组件，调用原生 `DigitalTimeChangeAnimationController`，保留分钟和跨小时变化的弹簧缩放、透明度、模糊与错峰。时间仍由 `TextControls` 共享可见调度器提供；没有新增独立定时器。

`NotificationClockFont` 核对字体真实 `fvar` 中的 `HGHT` / `wght`，创建私有 Typeface，不改锁屏共享缓存或复制字体。高度轴含固定基底，设置比例不能直接当作最终像素高度比例。位置和通知留白使用实际 ink 边界；日期、底部文字使用正常文字字体。

数字 Slot 按当前字体/字号缓存 0–9 外框、宽度、基线和原生偏移，新旧数字显隐不改变外框，不在动画中重写支点。字体和测量由绘制前单一帧回调处理，不在绘制中反复改字体、测量和 layout。展开/收起固定使用 `WEIGHT`；旧 `COMPACT_WEIGHT` 键和用户值兼容保留，运行、预览不使用。

时间填充与玻璃描边各自处理颜色、alpha。native12 对完整数字填充路径归一后描边，去掉数字“6”自交处的内部曲线，同时保留字孔与原生进出动画；不是逐轮廓合并，也不替换完整原生材质控制器。

### 下拉、回收与左右切页

入口和回弹统一读取原生 `PanelExpandedFraction.getRawFraction()`。有界 `headerMotion` 包住实际日期/时钟，`footerMotion` 包住可见底部内容，登记到 `HorizontalProgressiveAnimController` 原生横向组。全屏 `ClockView` 不登记，避免覆盖层 bounds 改变原生手势区域排序。内容为空时注销，解绑只操作模块自己的容器。

关闭稳定后设为 `GONE`；原生弹簧取消/重启的间隙保持现状。垂直关闭判断不借用横向动画运行状态，避免页面已关而覆盖层仍保持。分离式通知页切向控制中心时，不因 QS 开始展开而提前隐藏大钟或重置通知留白；恢复保留当帧原生 fake 变换。

独立入场距离默认 `32dp`，与留白高度分开，和模糊、渐显随双向手势变化；可设为 `0`。用户在 native15 后要求保留更自然的原生纵向弹簧，不能把当前入口或回弹写为已通过。

native16 审查确定：原生 `NotificationStackScrollLayoutExtImpl.startRebound()` 的 parallax 分支启动 `scrollGridChain` 后立刻 `resetOverDistance`；旧 Model 读取并压缩 `overDistance`，会在原生通知行仍回弹时把时钟偏移直接归零。现接入 `getLayeringReboundTrans(int index,int count)` 返回的同链 `getCurrentSpringY(index)`；该值是通知行原生 Y 之外的额外位移，不包含滚动量。分离面板本身还有 `OplusPanelAnimationExImpl$PanelTranslationYValue`（原生 damping `0.6`）：经 `mPanelAnimationEx.getTranslationYValue()` 取得对象，再调用 `getTranslationYValue():float` / `setTranslationYValue(float)` 读写值，沿用该原生位移来源，不另建弹簧或定时器。实现已进入后续安装构建，交互观感仍需逐场景核对。

原生背景模糊与模块入口模糊是不同层。仅在大钟接管的竖屏通知场景内同步原生 blur/raw 进度，避免前景退出后背景弹簧继续遮住底层应用。切页、锁屏或配置改变时仅恢复仍属于本次写入的值，保留其他系统绘制。native12 录屏最终关闭后底层应用清晰，只支持该次关闭观察。

### 顶部图标与副本

native15 用 `StatusBarFixedIcons` 在 shade root 的有界 `ViewOverlay` 中绘制原生 QS 右组，以绑定 fake child 的 `getMView()` 所指向真实 Phone 右组为几何锚点。两轴统一用 `getLocationOnScreen` 映射到 root 本地坐标，按右边界（RTL 左边界）和中心 Y 对齐。没有触摸 View、全屏动画组登记或强制图标缩放；原生源组和 fake 副本只按所有权抑制重复显示。用户已确认位置固定，旧“real/fake 共享 Y 上移”是历史方案，已被最新要求替代。

native16 起的自然交接实现：`setNativeAppearance(qsSource,nativeSourceAlpha,fakeCopy,nativeCopyAlpha)` 接收压为 `0` 前保存的原生 alpha，单一 root `OnPreDraw` 用缓存方法读取双方 `transitionAlpha`。原生 `resetAllViewsTransitionAlpha` 在展开端为 QS `1` / fake `0`，收起端相反；原生 appear spring 回调更新中间权重。真实 Phone 与 QS 各自原生绘制，在同一固定锚点交接配色和淡显；透明的图标区域小层按权重合成，保留字体/图标真实尺寸和子视图变化，不新建颜色动画、额外位移或整屏模糊。

QS 配色继续来自 `OplusSimpleQSManagerImpl` 的 color state 通知、header `applyIconDarkness`、`TintedIconManager.setTint` 及电池的原生 `onDarkChanged`；Phone 配色来自真实来源。fake 副本缺失或透明度读取不可用时回退 QS 表示；真实来源不可用时撤槽、保留原生表示。root 在真实绘制帧刷新槽；可见子视图溢出纳入局部绘制范围，不另加 source 大小裁剪。draw 异常移除槽、通知原生回退，并限制诊断次数；`hide()` 不再反复清失败锁，来源替换或显式 `resetFailure()` 才重试。自然交接仍需独立核对，不能以右上固定的确认替代。

原时钟、运营商及精确命中的左侧通知图标副本按所有权隐藏；真实状态栏左侧图标不移动。右上 `settings_button` 在原生 `updateClickAbilities` 后精确隐藏并保留恢复状态；隐私指示副本不参与替换。

native19 新增 `StatusBarClosingIcons`，复制真实 Phone 的小钟和通知图标；收起最后 `20%` 从真实锚点上方 `8dp` 渐显至锚点。源 alpha 按所有权恢复，真实来源不移动；这与固定右组分开处理。收起末段的自然交接仍待人工核对。

native21 修正其更新入口：使用当次原生 fraction，而不让旧成员进度覆盖；小钟与通知图标换绑各自释放，不清掉另一槽；空/GONE 来源在测量和绘制处均安全跳过。native21 本身未安装，上述修正已进入后续安装包；末段自然交接仍待具体场景核对。

### 时钟与通知安全间距

独立设置分组的自动保留开关 `notification_big_clock_notification_gap_enabled` 默认开启；数值复用 `notification_big_clock_notification_gap`，默认 `18dp`，用户最新已保存 `50.0dp`，旧 `31.66dp` 不再是当前值。关闭时只令运行间距为 `0`，再次开启恢复保存值；支持两位小数、直接输入及超范围确认，预览同步。此值与已经退役的尾层 gap/inset 分别处理。

初始留白按已测得的时钟/日期实际底边安排，跟随字号、位置和日期布局变化。这是时钟到通知的布局距离，独立于下面的边缘安全距离。不会禁止通知滚动进入过渡带，也不把滚动中的通知锁死在固定位置。

### 通知空间安全距离

native12 起不再依据“正在拖动/静止”决定边缘效果，而依据实际卡片位置：

- `H`：实际可见日期/时钟底边，通过 panel 的 `transformMatrixToGlobal` 和 stack 的 `transformMatrixToLocal` 映射。
- `B = H + 边缘安全距离`，默认边缘安全距离 `18dp`。
- 默认范围 `24dp`、模糊半径 `8dp`；带的区间为 `[max(H, B - 范围), B]`。
- 完整卡片顶边位于安全区时清晰；进入带内逐渐模糊和淡出；停下后仍按当前位置保留效果。范围为 `0` 时不增加边缘效果。
- 真实顶部边界和安全距离分别处理，不先把安全距离整段裁掉，也不把首条安全卡片默认模糊。

`NotificationClockEdge` 使用通知实际高度、原生上下 clip、视图 clip 和矩阵。模块只限制 Y，保留原生左右 Canvas 边界，不另造方形左右边墙。横向切页不离屏捕获全栈；需要补充效果时保持原生每次绘制一次。精准命中的 OEM fade 只在模块拥有的原生绘制调用内临时置 `0`，结束在 `finally` 恢复；未知渲染分支保留原生行为。

native21 的白线修正改用实际 clock/date 子视图矩阵与描边半宽计算可见边界；过渡区采用单个连续输出层与完整渐隐 mask，保留原生 clip、像素预算和失败回退，避免以分段输出制造额外接缝。native22 继续补齐入口 blur 的完整输出 mask。底部 `p=1` 已有恢复分支，本轮未改变该逻辑。这些代码已进入 native26/27，但 native19 底部回弹录屏混有 USB 弹窗且未定位白线像素，不能作为修正的通过证据；完整底部后续录屏仍待根代理核对。

native12 停止滑动截图与录屏支持“位置决定效果、停止不撤销”的观察；不代表不同通知高度、分组、快速切页及所有范围配置已经核对。

### 通知尾层与原生裁剪

`NotificationBigClockStack` 在原生目标状态计算后调整尾层目标，由 SystemUI 应用或执行动画；不在绘制时临时移动整张视图，不新建列表或修改通知数据。前方完整通知保持原生大小，后方最多三层，使用互不重叠的可见窗口避免露出被遮挡文字。媒体、页脚、清除等非通知卡片保持原生目标。

native13/14 的每层 `8dp` 内收和旧 gap/inset 属于历史方案；最新方案已从运行和 UI 移除这组参数，导入忽略，不读 legacy 值。卡片间隔采用原生系统 gap 资源，不改写 ScaleX，Z/scale 留给原生，不用旧内收规则恢复用户列表。

native24 按用户最新要求固定各尾层的目标宽度，并分别提供 `notification_big_clock_tail_width_1/2/3`，默认 `96%/92%/88%`；百分比以完整锚点卡片的原生宽度为基准。只保留最多三层的底沿窗口，不露出侧边和卡片内容。推荐滑条为 `50%～100%`，两位小数、点击输入和十秒超范围确认复用现有数值流程；零与有限超范围值不改写，计算先用 double，实际宽度限制在原生物理行宽内。三键已自动进入 Provider、导出/导入和本组默认重置，静态预览同步三个比例。新宽度配置与退役 gap/inset 无关，不重新读取旧值。

原生宽度 getter/setter 的精确窄 hook 已成功接入，仅作用于模块拥有的尾行；展开时随既有进度返回原生 clipWidth，退出只取消宽度属性的原生 tag，并从当前 raw source 恢复，不取消原生 Y、height 或内容动画。由于 OEM 宽度裁剪同时改变 `getActualClipHeight`，投影按已确认的原生耦合公式补偿实际高度，确保使用真正的底沿。NCV 及精确 `OplusNotificationChildrenContainer.dispatchDraw(Canvas)` 分组路径隐藏拥有尾边中的文字，保留原生内容和分组来源；没有全局隐藏内容或人工 ScaleX。已安装和入口接入仍不能证明全部瞬态宽度、圆角和交互均正确。

native16 审查定位到几条可证路径：`mExpandingNotification`（普通通知展开）、单张 row 的 `userLocked` / `groupChanging` 被当作整栈退出条件，会在交互开始全列表展开、结束又折回；`ViewState` / `ExpandableViewState` 的父子 apply hook 同次 pass 重复释放/投影；瞬时 Y 排序在 overlap/spring 中可能交换锚点。OEM 在 apply/animate 前把 Z 清为 `0`，反复写负 Z 也不能建立可靠深度。

现采用原生 `notGoneIndex` 稳定排序、同次原生 application depth 一次投影、跨 pass 校验真实 native target，部分更新以 native baseline 事务处理；普通通知展开只释放对应尾卡，后卡不穿越正在交互的通知，每 pass 只读一次 decor。最多三条尾卡保留，原生展开、分组、guts、清除和启动操作仍有原生所有权。native16、native17 已安装，后续继续修正瞬态尾窗；不能写为全部跳动已解决。

薄尾层圆角仅作用于模块拥有的 `getClipPath` / `getSpotLightClipSpec` 调用，临时将 `clipTop` / `topOverlap` 归零，完成后在 `finally` 按所有权恢复；真实 row/background 窗口裁剪保留，已限定归属的完整卡片圆角继续保留。尾层结束以实际 clip 与原生动画生命周期判断，不以目标状态 `hidden` 提前释放；归属变化主动 `invalidateOutline()` 刷新原生缓存。标准 `ExpandableOutlineView.drawChild`、匿名 outline provider 的 `getOutline`、普通 Spotlight provider 的 `getClipSpec` 已进行 caller deopt。`NotificationContentView` 内容 alpha 仅作用于模块拥有的行，不改变全局内容透明度，也不重画假通知背景。退出通知场景时恢复模块拥有的属性。

native19 六次实际快下拉（先两次、再四次）连续帧未再观察到缺角，仍是有限观察。此前“正常→缺角→恢复”的反馈已说明静态图不足以判断瞬态修复，真实 height/clip 弹簧、反向快滑与不同通知仍需连续过程证据。

native25 human 影片尚未定位具体问题帧，raw 探针在启动阶段耗尽预算。native26 已加入原生宽度变化后的 `invalidateOutline()`，但用户仍明确复现；human26 的 PTS `27.1192`、`27.1278`、`27.2106` 已定位尾层 1 裸直上边、尖角及尾层 3 宽弧片。以上视频、帧图和日志均为私有证据，不能用最终静态恢复图覆盖这一事实。

native26 的两份 rawLogcat 抓取为空，APP 日志数字经发送端和保存端 sanitizer 掩码；启动阶段累计的 `2400` 事件预算耗尽后不再记录，现有探针不能与影片问题帧可靠关联。空 raw 文件不证明手势期间没有调用。native27 的更窄圆角越界 gate 已构建、安装并激活，但用户仍复现。本地后续薄窗解算保留所有父/目标 identity、interaction、excluded、custom outline 限制，取消临时清零真实 clip 字段；新方案的实际行为与修复结果尚无手机连续过程核对。

### 通知横滑触摸归属

用户报告展开后的第一、第二条通知无法侧滑，整页随左右切换。原生列表中这两条通知记录为可移除，不能仅凭常驻字段推断其不可移除。

目前实现会在通知页切页热区起手判断中，将原生手势坐标通过真实视图矩阵映射到 stack，再结合原生 `getChildAtPosition`、可见卡片边界和裁剪判断命中。命中通知时不让分页热区抢走该起手，继续使用原生通知触摸、分组和删除逻辑；空白位置仍走原生切页，已经进行的横向切页不被中途夺回。没有改 `canDismiss` 或通知 flags，也没有模拟侧滑删除。native15 录制混有用户操作和长按上下文，不能作为通过证据；首两条通知的触摸归属仍待具体场景核对。

### 设置、日期与清除按钮

设置页包含时间、填充与玻璃边框、下拉入口、日期、布局、时钟与通知安全间距、通知堆叠和底部内容；通知清除按钮为独立入口。入口距离、布局间距、边缘安全距离/范围/模糊分别配置；三层底边宽度在现有堆叠分组内独立设置，不增加复杂页面。退役 gap/inset 不再显示，旧导入值兼容登记但运行忽略。填充和边框分别有浅/深色、透明度，复用 HSB 调色盘。目标设备缺少自选字体时回退系统。

默认日期为 `M月d日{周} {干支}{农历日期}`。支持 `{lunar}` / `{农历}`、`{lunar_date}` / `{农历日期}`、`{ganzhi}` / `{干支}`，农历使用 Android ICU ChineseCalendar，支持闰月；`{农历}` 带前缀，`{农历日期}` 不带。农历不可用时明确显示公历。底部 `{text}` / `{文本}` 在日期格式化后插入，英文、单引号、emoji 不被误当格式。

`NotificationClearAppearance` 只登记 `ClearAllController.clearAll/showingView` 的实际 `OplusClearAllButton` 与 owned 背景，替换两层原生玻璃 base fill，保留描边、阴影、纹理、spotlight、图标、按压和清除监听。支持浅/深色、渐变、透明度；临时 tint/filter/Shader 在 `finally` 恢复，关闭重新记录原生材质。用户已在调试中修改清除按钮配置，不用旧备份覆盖。

用户随后反馈颜色调节未生效。真 APK 确认清除按钮使用独立 `MaskBlurDrawable`，它不继承 `AutoBlurDrawable`，旧 Auto-only hook/gate 因此漏接。native21 补精确 Mask draw 入口、Platform 的 `blurDrawable` 与 BlendWallpaper 的 `blendDrawable`；native22 实机进一步发现 CLEAR_ALL 的 Platform multi/summary 均为四项，原先 size==2 限制令 `prepared=false`。

native23 已修正精确槽归属：公共背景在 `0/1`，mode 为 `5/2`；CLEAR_ALL 自有前景在 `2/3`，mode 为 `5/3`。确认 `BlurMixMultiWithShader` 的 config/list 身份、前景原色与 summary 项身份后只替换 `2/3`，保留公共背景和其余材质；两项 CLOSE_ALL 的旧路径仍单独匹配。BlendWallpaper 按 native `bgBlendParam` 与 summary 的模式/颜色匹配底色（日 `5/3`、夜 `1/2`）。实机日志已记录 `selected=2/3`、`prepared=true`。背景/来源切换和退出请求缓存重录，map 锁外取 native dataLock，避免录制线程锁顺序反转；临时材质在 finally 恢复。诊断用临时红色已恢复为当前用户白色，最新透明度 `79.12`；不恢复旧 `37.66`、旧 ARGB 或整份 XML。完整配色、透明度和关闭恢复仍按具体场景核对，入口命中日志不等于全部通过。

## 历史观察和证据边界

- layout3/layout4：通知堆叠、退出恢复、横竖屏的早期观察。
- native6：原生组件接入、日期、图标上移、隐藏按钮和三层尾边的截图。
- native7：私人视频 4s 画面有一次 11:47→11:48 原生数字切换、高度轴和父面板横移；后续仍有快滑和截断反馈。
- native9：已安装，只有有限快切录屏。
- native11：已安装过并取得有限观察，旧等待安装结论失效；录屏混入相册操作，不能作为稳定通知页验证。
- native12：实际安装、激活及本文开头列出的有限录屏观察。
- native13：已安装激活，但继承字段查找失败导致触摸热区 hook 未接入。
- native14：已全量构建、覆盖安装；入场、尾层、图标副本及横滑场景仍待核对。
- native15：已安装激活，SystemUI `7564`→`16372`，激活有截图；用户确认右上固定。横滑记录不足以证明场景通过。
- native16、native17：均已安装；自然图标交接、原生纵向弹簧与堆叠生命周期修正已进入后续构建，未作所有场景通过结论。
- native18：仅构建，未安装。
- native19：已构建、安装 `Success`，SystemUI PID `7735` 已由 MainActivity 核对激活；六次实际快下拉（`2+4`）连续帧未再缺角，但不作彻底修复结论；底部回弹录屏不能证明白线已修复。
- native20：已构建，因断线从未安装。
- native21：已完整构建、签名并验证，未安装；后续包继承收起副本、清除按钮入口与白线修正。
- native22：已安装，SystemUI PID `29859`；补入口 blur 完整 mask，实机确认清除按钮四项 multi 导致旧槽匹配失败。
- native23：已安装，SystemUI PID `7179`；CLEAR_ALL 前景槽修正后有 `selected=2/3`、`prepared=true` 实机日志。
- native24：已完整构建、签名验证、安装 `Success`，SystemUI `7179`→`7407`，MainActivity 已核对激活；新增三层独立底边宽度及原生宽高耦合适配。静态图不代表瞬态通过。
- native25：已安装，后续升级前 SystemUI PID `4052`；human 影片尚未定位具体问题帧，raw 启动探针预算耗尽。
- native26：已构建、安装 `Success`，SystemUI `4052`→`6055`，MainActivity 已核对激活；原生宽度变化后刷新 outline 已进入，但用户明确复现，human26 上述三个 PTS 可定位尾层异常。APP 数值被掩码、raw 抓取为空，不能关联问题帧。
- native27：已构建、安装并激活，SHA256 `8bdb62989896aeb6fef38855f5a61d577303065fee215461d9d8f0b7b7cdccc7`，SystemUI `6055`→`15947`；用户仍复现。新薄窗方案在后续本地源码，不写实机修复通过。

历史构建标识保留供对照，不代表当前同名 APK：

| 版本 | 运行时标识 | 历史 SHA256 与进程记录 |
| --- | --- | --- |
| native12 | `c17-runtime-20261001-bigclock-native12-5e39b6a2` | `67a43ec8851280f6a753cfa742c06244552a62cd0990a777f71b97e95288337d`；SystemUI `29481`→`16623`，已激活 |
| native14 | `c17-runtime-20261001-bigclock-native14-734f9ce2` | `1ce4251d97553dcbfee4f3af4d8288a455f11e857fd1c855ae279950e2694392`；覆盖安装记录，安装前 PID `25703`，该轮后续场景结论未补齐 |
| native15 | `c17-runtime-20261001-bigclock-native15-80f47a31` | `6f89e6f2b731cf2ec2c70d71fc2df52c2e4d367063fe3684b27be4a0ee7af0c1`；SystemUI `7564`→`16372`，已激活，用户确认右上固定 |
| native24 | `c17-runtime-20261001-bigclock-native24-607cbed3` | `881e4ec4c1c9e341ea45d83d7b23917189dfcdebf7c76600355c1527eeea3510`；SystemUI `7179`→`7407`，已激活，用户仍报告尾角截断 |

系统参考、截图、视频、字体与签名均只保留在本地私有目录，不公开或加入发布包。相册旧图不等于当前 SystemUI 实时状态。

原生数字组件与动画来自设备插件；模块布局、玻璃填充与描边仍是适配层，不表示复制完整锁屏材质控制器、壁纸材质链、主体抠图或深度分层。新机型/系统更新需要核对类、字段、方法；不兼容时回退并记录诊断原因。

## 待完成

1. 当前 native27 已安装激活且用户仍复现；本地新薄窗圆角仅有静态源码审查。用户已离开，不再操作手机，不把安装、激活或静态恢复图写为动态通过。根代理同时整合最新授权的持久化、功耗、媒体和整套 HyperOS UI。
2. 清除按钮颜色/透明度与关闭恢复、白线完整底部后续录屏、双向入口/原生回弹、固定右组自然交接、收起图标、侧滑和空白切页保留为后续待核项目；避免删除用户通知，不更改其可移除规则。native19 六次快下拉仅为有限观察，不能否定 native26/27 的后续复现。
3. 保留当前日期粗细 `601`、完整卡片数 `4`、时钟与通知安全间距 `50.0dp`，以及清除按钮当前白色、透明度 `79.12`、最新 alpha/渐变/开关、三层宽度、日期格式及全部最新颜色。生效配置是设备保护目录 `/data/user_de/0/dev.puitheme.iosstatusbar/shared_prefs/statusbar_settings.xml`，不要恢复整份旧 XML；临时红色已恢复，旧 `37.66` 不再是当前透明度。
4. 临时诊断已恢复 `diagnostics_enabled=false`，`stay_on_while_plugged_in` 已恢复并读回 `0`，两项均已完成。用户已离开，本轮不再操作手机。
5. 根代理回填最终 `1.15.0` 构建哈希与发布链接；本文手机当前哈希属于 native27，后续同名产物覆盖后重新核对。native20、native21 从未安装的历史保留，不改写为已安装。

本轮没有运行自动化回归检查，没有提交或推送。迁移前回归结果属于历史版本；安装后可通过模块“重启系统 UI”载入新注入代码。
