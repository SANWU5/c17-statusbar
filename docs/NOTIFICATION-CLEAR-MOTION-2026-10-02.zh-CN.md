# 通知清除按钮独立动效：2026-10-02 历史验证记录

**历史记录：本文保留 preview5 至 code40 的实现、问题与阶段性检查，不作为本地code58的行为或验收说明。** 当前操作以 [1.6.1/code58说明](LOCAL-1.6.1-CODE58.zh-CN.md) 为准，最终范围见 [验证记录](../VALIDATION.md)。下面原始计数、未通过画面与待复验说明保留其发生时的含义。

实现包含独立的通知清除按钮位移、淡入和通知安全距离逻辑。本文保留 `1.6.0-preview5` 阶段验证，并追加其后性能改写；不代表后续包的实机视觉或性能验收通过。公开源码不包含设备录屏、通知截图、私人配置与调试日志；这些材料保存在被忽略的 `.local-private` 中。当前构建结果以根目录 `VALIDATION.md` 为准。

最终code40的正式Gradle classes已复验：清除按钮384项、状态图标790项通过。控制中心自己的纵向spring、外层通知/QS横切与内层磁贴翻页分别读取；内层使用真实 `StaggeredPagerLayoutManager.layoutState.scrollX` 和当前宽度，精确核对窗口与布局管理器身份，不缓存重开前的页内相位，也不将这一路进度送入大时钟或清除按钮。实机录像已观察磁贴区左右翻页时右槽向上淡出、向下淡入、保持Phone终点；直接下拉控制中心的固定位置、通知页终点与收起原生恢复也已观察。性能检查证明稳态无重复布局/自驱重绘，未做设备帧率或功耗测量。以下旧数量与失败画面属于分阶段记录。

## 配置与行为

整体开关沿用 `notification_clear_custom_enabled`。独立动效使用 `notification_clear_motion_enabled`，默认关闭，不依赖通知大时钟开关。三个数值配置为 `notification_clear_safe_distance`（默认 18 dp）、`notification_clear_entry_travel`（默认 32 dp）、`notification_clear_offset_y`（默认 0 dp）。安全模式和关闭动效均恢复原生按钮属性。

动效读取真实面板展开进度，在 85% 处完成入场，并跟随面板纵向 spring 和最后一条通知的原生 rebound；回滑与取消使用同一进度计算，不增加定时动画。原生横向清除按钮动画 manager、X、按压 scale、背景和点击处理继续由原生维护。

安全距离根据真实可见通知行的实际高度、裁剪范围与全局矩阵计算，映射到按钮父坐标系。按钮当前 scale 参与几何计算；修改真实 translationY，所以触摸位置与画面一致。空间不足时淡出，只拒绝新的 `ACTION_DOWN`，已经开始的手势继续交给原生。通过原生底部空白和滚动范围为按钮预留空间，不裁掉通知或修改父窗口。

## 实际 OEM 接口依据

本机 SystemUI DEX 确认 `ClearAllController.bindViews(ViewGroup, NotificationStackScrollLayout)` 的 `panelView` 是 `NotificationPanelViewController.mView`：`onFinishInflate` 传给 `NotificationPanelViewControllerExImp.onFinishInflateExt` 后原样绑定。动效只接管同一 controller、panel 与窗口中的 `OplusClearAllButton`。

原生更新入口包括 `onHeightUpdated`、`updateClearAllPosition`、`setVisible`、`onStateChanged`、`updateQsExpansion` 与材质更新/绑定。实际读取按钮 alpha 启动动画的 `setVisible` / `updateQsExpansion` 在 scope 内暂还原其真实 alpha，结束后重组合已缓存属性；其它更新只标记几何需要刷新。`View.setAlpha` / `setTranslationY` 仅针对精确清除按钮跟踪原生写入，安全模式仍保留观察，因此原生新写入的零值也能正确恢复。

已核实该 OEM 的 `getScrollRange()` 不调用 `getEmptyBottomMarginInternal()`。两处分别接线，滚动预留只补原生已有空白之外的差额，并与大时钟 footer 预留取最大值，避免叠加两份空间。预留改变才请求一次布局；detach 清零。每帧 alpha/Y 只在值变化时写入，避免稳态反复重绘。

## 已完成验证与限制

`NotificationClearMotion.java` 与相关 registry 已通过 Java 8 编译。对正式 Gradle debug Java classes 运行 `NotificationClearMotionCheck`，实际通过 **362 项**，覆盖默认/整体/安全开关、85% 完成、纵向 spring、回滑、通知间距、空间不足淡出、零值所有权、外部原生写入、退出/重新启用、嵌套 scope 和滚动预留溢出保护。已加入 `validation/run-checks.ps1`，共 39 组检查由根任务统一执行。

同时正式 classes 的 `StatusIconTransitionCheck` 通过 **639 项**，新增动态宽高检查证明同一图标组重新测量时仍保持展开前 Phone 右边缘与垂直中心。真实横切重影是否消除仍需新包的 `C17IconIdentity` 有界身份日志与实机画面核实，本文不以桌面模型检查替代设备验证。

后续实机验证应先启用清除按钮整体开关和独立动效、关闭大时钟，再检查慢拉、末端回弹、通知滚动、安全空间不足与安全模式/关闭后的原生恢复。不要为了验证触摸而真的清除用户通知。未知 OEM 几何/绑定接口或 setter 异常时恢复原生按钮并记录一次错误。

## preview5 之后的性能审计

用户报告清除按钮性能问题后，移除了每次 preDraw 无条件遍历所有 State、复制 ArrayList 和扫描通知的路径。真实面板进度、纵向 spring、滚动、原生 stack application/layout 与 controller 更新标记 dirty；同一次遍历合并处理。稳定画面只比较按钮/父容器尺寸、scale、可见性、通知数量与已缓存的原生动画标志，直接跳过矩阵、通知行与 rebound 计算。原生通知动画运行时继续刷新真实几何。关闭/安全模式解除 preDraw 观察；关闭后全局 View setter 过滤首先走一个 boolean 快速分支。

Matrix、RectF、结果 Frame、State 遍历容器和原生通知行反射元数据均复用。按钮滚动预留根据未按压尺寸、用户偏移与间距计算，脱离 spring/按压的瞬时几何，避免 reservation→requestLayout→新动画几何→reservation 的反馈。预留数值变化才请求一次布局；不对所有 controller 事件执行 restore+完整 refresh。

实际 DEX 中只有 `setVisible` 和 `updateQsExpansion` 读取按钮 `getAlpha()` 来启动原生动画，且不读取 translationY。因此只有这两类入口保留嵌套 native scope，暂还原必要的 alpha 输入，结束时仅重组合已缓存属性；不再往复恢复 Y、不扫描通知、不请求布局。高频 `onHeightUpdated` / 位置更新只标记 dirty；普通 View setter 不做类继承链反射，返回值不变时也不分配新的参数数组。未知原生几何或 setter 失败后保留原生按钮并暂停反复重试，等配置或重新绑定再尝试。

修改后的 Java8 独立编译检查实际通过 `NotificationClearMotionCheck` **384 项**，其中对真实 private State 连续执行 **10,000 次**稳定几何检查，确认结果 Frame 身份不变、没有通知反射缓存填充、没有 stack layout 请求；同时覆盖原生动画标志、scale/新增通知变化、native scope 无 Y 折返及原生零值恢复。**384 这一轮尚待最终 Gradle classes 复验；桌面检查不等于设备帧率测试。**

右上角另补缓存副本边界：原生父 RenderNode 可直接复用子节点，完全不进入 Java dispatchDraw。现在在已验证 shadeRoot 内仅于接管/横切开始发现精确 native fake 类，缓存其 getMView 绑定，对当前 right source/Phone anchor 所属副本控制节点 alpha；own QS row 内的副本保留供 overlay 组成，未知来源或移到另一 root 立即恢复。通用 `drawChild` 护栏与框架 caller deopt 已删除，避免影响全局 View 绘制，只保留精确 native fake 类入口。

同时修正固定右侧图标每次 preDraw 无条件 `invalidateSelf()` 的稳态循环。位置、过渡 alpha、边界实际变化才请求刷新；原生 QS 内容 dirty 时更新当前图标。完全淡出或 alpha 四舍五入为零时不因无法绘制清掉的 dirty 标志继续请求帧，返回页面时仍更新隐藏期间变化的内容。修改后的完整 Module、图标和清除按钮源已通过 Java8 独立编译；`StatusIconTransitionCheck` 实际通过 **657 项**，包含 10,000 次稳定刷新检查不请求下一帧、内容 dirty 更新、透明阶段停止、返回页刷新与所有权释放。**本轮 657/384 仍待最终 Gradle classes 复验和新版设备画面，不代表已经通过实机性能或横切重影验收。**

身份日志只记录类、资源 ID 与来源关系，不读取通知文字。现有 runtime handshake 中，只有 provider 接受当前挑战响应才在主线程重置本轮有界日志，直接输出已登记副本 inventory，即使缓存完全没有绘制回调也可核实来源。重复同一 nonce 不重复重置；卸载后不再产生新的 `C17IconIdentity` 输出。

## 发布前补充审计

只读复核又发现 `StatusBarClosingIcons` 的旧收起槽在完整展开、完全透明时仍保留 preDraw 并请求下一帧。后续最小修复只修改该类：最终 alpha 为零（包含四舍五入为零）直接移除槽与观察，但 `show` 返回成功以保留调用方对 Phone 小钟/通知源 alpha 的所有权；最后 20% 实际显露时，只在真实位置、尺寸、线性矩阵、opacity、边界或来源 dirty 变化时刷新。绘制使用复用的两来源快照容器；来源在绘制途中换绑时不再画旧实例。空槽释放不分配快照、不提升 generation，也不清除 failedRoot。

本次 Java8 独立编译后 `StatusIconTransitionCheck` 实际通过 **681 项**，新增 10,000 次透明更新无绘制观察或 generation 增长、10,000 次可见稳态刷新不请求新帧，以及小钟/通知来源分别变脏、原生 alpha 不被 helper 修改、释放/安全边界与失败重试所有权。最终 Gradle classes 复验和实机结论由根任务后续回填，本文仍不将桌面定向检查等同设备性能验收。

卸载路径核对了 `ModuleLifecycle`→安全运行配置→清除按钮/大时钟配置恢复，以及移除后 `SettingsSnapshot` 只返回原生安全配置。精确清除按钮 alpha/Y setter 仍作为安全观察入口，支持恢复前原生新零值；图标槽与滚动预留退出所有权。最后补齐了 `StatusBarFixedIcons.releaseRuntime()`：仅实际卸载后永久移除 Phone preDraw/layout/attach 观察，释放已抑制 native alpha、nativeCopies、trace、失败及 baseline 缓存、progress reader 与相关监听引用。后续原生 init/bind/回调不能再注册；普通 hide 与安全模式保留重新启用能力。

本次专用清理源码通过 Java8 编译；`StatusIconTransitionCheck` 独立编译后实际通过 **706 项**，新增实际三类监听注册→普通 hide 保留→实际卸载全部解除、原生 alpha 恢复、缓存引用清除、卸载后迟到回调不能重建等 25 项。新的被动监听 fixture 下清除按钮 **384 项**继续通过。最终构建与设备验证仍由根任务统一完成。

## code37 实机位置未通过与下一包诊断

根任务提供的 code37 右侧私有截图裁剪仍显示图标组位于原生控制中心标题高度；对应 XML 的 `quick_qs_status_icons` 边界为 `[910,384][1300,464]`，未显示展开前 Phone 顶端最终槽。该次实机结果不能写为固定位置或过渡验收通过。原生横向 driver 接入已有诊断，但本次原始 `C17IconIdentity` 抓取为空；空抓取不能区分锚点尚未绑定、baseline 未捕获或 eligibility 拒绝。

源码核对不存在 QSOnly 或大时钟主开关门控，且实际 DEX 的 `StatusBarFakeFrame.getMView`、绑定与精确 dispatchDraw 接口均存在。未据此猜测修改接管、位置或动画逻辑。下一包增加 source 为 `hook` 的 opt-in 短结构诊断，分别记录 gate、锚点/测量、baseline/窗口/边界拒绝及分段进度，按 stage 变化去重、总计最多 40 条，不含通知、标签内容或屏幕坐标。关闭诊断时首先快读 volatile 开关，不生成结构字符串、不消费记录预算；启用诊断或已有 Provider 接受的新挑战重置预算。原始 C17 输出也改为仅 opt-in。

相关完整 Module 源通过 Java8 独立编译，`StatusIconTransitionCheck` 实际通过 **712 项**，新增 10,000 次关闭诊断回调不形成 stage/预算、重复 stage 仅记录一次、40 条上限与重置行为。此为诊断准备证据，固定位置的实机阻碍仍待下一包真实记录定位，不能用 712 项桌面检查替代。

## code38 纯控制中心缺失驱动与 code39 源码修复

code38 实机短结构诊断证明 Phone baseline 曾成功捕获，通知页可在有效 baseline 上获得固定槽；单独首次展开控制中心时，原始精确副本记录却仍为 `closed` / 未接管 / 零进度。横向切换到通知页之后才出现完整右槽进度与正确顶端位置。因此阻碍来自纯控制中心的展开驱动与真实图标行缺失，不能继续归因于 baseline 未捕获，也不能仅靠放宽副本抑制消除。

实际设备 DEX 存在两套独立展开 spring：通知页使用 `OplusPanelAnimationExImpl$PanelExpandedFraction`；纯控制中心使用 `QSPanelExpandFraction`，通过 `OplusQSRootViewComponent$qsPanelExpandFractionListener$1` 更新该组件的 `curRawFraction`。原代码只观察前一套。控制中心真实图标行由独立 header factory 创建，由 `SeparateQSFakeStatusController.statusIconsView` 持有；并不是通知页的 `OplusQSSimpleHeader` 图标行。

code39 源码新增 `StatusBarQsIconAccess`，只读取上述组件/监听器和真实图标行，结合原生 tracking、spring running、final position、keyguard 判断右槽所有权。它不把 QS 进度送入通知大时钟或清除按钮。两页开合分别保持状态，一页已关闭不会释放另一页仍展开的右槽。精确同一 shade 窗口内的另一页右图标行作为 companion 恢复/抑制，保留原有未知来源、不同窗口、锁屏和安全模式走原生的边界。最终位置仍来自展开前 Phone 右边缘与垂直中心，横切仍为离页向上淡出、返页向下淡入。

组件 detach/换绑、native getter 失败、非有限进度和实际卸载均释放精确所有权；读取替换组件成功后才提交新身份，避免失败候选留下旧 overlay。实际 COUI DEX 也确认结束动画先将 `mRunning=false`，之后才通知 end listeners，所以 after `onFractionEnd` 能判断原生已经结束。普通安全模式保留 native 观察，允许关闭安全模式后重新接管。

收尾 DEX 审阅发现 `QSPanelExpandFraction.skipToEnd(false)` 和 `tryAnimateToFinalPosition` 直接修改实际 spring，未更新 `curCalcFinalPosition`。因此不能以 manager 的缓存目标判断完全收起。现读取其 `qsPanelExpandFraction` → `getFractionAnimation()` → `getSpring().getFinalPosition()`；若 `mPendingPosition` 不为原生无待切换哨兵 `Float.MAX_VALUE`，优先使用这个原生待切换目标。仍须原始进度为零、未 tracking、spring 未 running 才释放，既覆盖无动画关闭，也不打断跨零或等待切换的原生动画。

本次完整 Module 与相关源码通过 Java8 独立编译，`StatusIconTransitionCheck` 实际通过 **755 项**：包含实际 QS 字段/方法名的 fixture、两套进度独立、跨零/取消/结束、真实 `skipToEnd(false)` 后陈旧目标仍为 1 的无动画关闭、反向陈旧目标、待切换终点与异常恢复、锁屏与恢复、退役/换绑/读取失败、卸载，以及两页真实来源/缓存副本的精确恢复；10,000 次未变化 QS 回调复用绑定且不请求模块重绘。**此为 code39 源码与桌面定向证据，首次纯控制中心实机右槽位置尚待根任务新包确认，不能写为设备验收已通过。**
