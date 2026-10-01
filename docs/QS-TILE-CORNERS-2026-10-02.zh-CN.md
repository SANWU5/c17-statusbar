# ColorOS 17 的统一磁贴圆角

当前适配目标为 OnePlus 13 的 Android 17 / ColorOS 17。`qs_tile_corners_enabled` 默认关闭，`qs_tile_corner_radius` 默认 `24dp`，硬范围 `0..80dp`。已有用户保存的开关和半径优先，不重置偏好。开启时按控件密度换算像素，以实际背景宽高较小值的一半约束半径；关闭时恢复各控件自己的原生圆角。

最终code40的正式Gradle classes复验面板圆角743项、磁贴圆角655项通过；code38的专用圆角实机观察与此版使用相同圆角实现，覆盖0dp与关闭后的各类原生形状恢复。完整构建哈希与有限实机范围见 [VALIDATION.md](../VALIDATION.md)。

## 快捷开关磁贴

`QsTileCorners` 覆盖 `OplusQSResizeableTileView` 和目标系统全部四种子类：`OneXOne`、`TwoXOne`、`TwoXTwo`、`OplusQSResizeableThreeStageView`。

每个实例单独拥有背景 provider。系统 `QSConstant.getSmoothRoundRectOutlineProvider(Context,float)` 会把设计半径映射为更大的 smooth 半径；本实现保留其原生 smooth weight，再通过 `RoundRectOutlineProvider.update(float,Float)` 规范为已经按实际尺寸约束的像素值，避免再次放大造成形状及内容边缘异常。

目标 ROM 的 `TileDrawableDelegate.getPathProvider()` 优先返回非空的 `blockPathProvider`，因此只替换普通 provider 无法完整覆盖背景。实现分别读取并跟踪 delegate 的静态路径和临时路径。原生 fixed-tile `TileDeformOutlineProvider` 使用实例专属的同类型 provider，保留其 span 和描边实现；未知的 launch/dialog provider 继续由原生拥有。关闭、安全模式及回收时恢复最新的原生静态路径和 fixed provider。不会修改共享资源池或共享 provider。

每种磁贴的 `getCornerRadius()`、`getCornerWeight()` 和 `getViewRadius()` 只在当前实例背景确实由本功能拥有时返回相匹配的数值。`TileLayerDrawable.invalidatePath()` 继续向原生子 drawable、玻璃及描边传播路径。背景和外层 View 的尺寸变化另通过弱引用布局监听同步，避免初始化、重新布局或换绑后只有部分控件更新。

## 亮度、音量、音乐和设备卡

`QsPanelCorners` 对不属于快捷开关基类的控件单独适配，不裁剪整个控件。

- 亮度、音量的五个原生 radius 字段保持不变。真实 `COUIVerticalSeekBar.draw()` 使用半径计算轨道 top、height、thumb 位置和 clipping rectangle，所以不能在整次 draw 中替换这些字段。新的作用域只记录当前 `OplusQsVerticalSeekBar` 的真实 Path/Adapter/Paint 和 Canvas 身份；OEM smooth path 及普通 roundrect 的最终曲率参数按原生按压比例调整，矩形坐标、权重、材料、thumb/input 几何不变。Android Path/Canvas fallback 必须同时命中当前原生 draw 与实际路径/paint 身份，图标、其他内容和普通 COUI 控件不参与。
- 滑块玻璃同时接入 `QsSeekBarBlurManager.createSeekBarBlurDrawable()`、`applySeekBarBgBlurConfig()` 和 `applySeekBarActiveBlurConfig()`，仅修改实际原生矩形内的 corner radius。目标 ROM 的首次 factory 不调用后两者，单独 hook 更新接口会漏掉首次创建。`updateBaseMixColorDrawableRadius()` 另记录最新原生半径，并用作用域避免 factory/setter 转发时重复施加按压比例。关闭与安全模式恢复该原生值，异常路径释放作用域并恢复已经修改的 blur。
- 音乐卡同时同步 `OplusQsBaseMediaPanelView` 的静态 provider 与 `OplusQsMediaBackgroundDrawable.setCornerParams(radius,weight)` 的光照路径。原生临时 block provider 存在时光照匹配当前有效路径；清除后恢复当前配置。关闭动画途中仍观察 block 清除，使光照最终恢复静态原生路径。新测量尺寸优先于尚未完成布局的旧尺寸。
- 设备卡覆盖 separate-card 基类 `d` 的全部五种设备/入口子类，由 `QsDeviceCorners` 单独拥有各真实 native surface。实际 DEX 的 inner body 是 `y/s/v/s/q` 字段，不再要求资源属于固定 package。分别跟踪当前 body background、`getBlurDrawable()`、现有 body/outer outline 及 smooth provider：`d.h()` 在 `g()` 换背景之前可能把旧 blur provider 绑定外层，旧外层与新背景必须同时更新。原生 `PluginDrawable` 是 `DrawableWrapper` 子类，setter 更新独立的 `RoundRectOutlineProvider` 并调用 `invalidatePath()`；每个 surface 单独保存真实原生 radius/weight，包含 OnePlus 的 null weight，不统一用资源默认值覆盖。换绑退休对象、关闭、安全模式与 detach 都恢复各自最新原生值。主题 GradientDrawable 保留原对象、颜色、shader 与原生非对称角数组。不会添加 outline、设置 clipToOutline 或改变 View/content/input 布局。
- 原生 `EditablePluginViewHolder$realPluginContainer$2$1` 另有独立高光路径。仅当它包含已跟踪的面板控件时，窄范围调整其 `getViewRadius()` 返回值。不会改写 final radius 字段、设置新的 clip、移动内容或调整点击布局。未知插件、关闭、安全模式和失去所有权时保留原生值。

上述路径保留原生颜色、透明度、着色器和玻璃材质。安全模式仍执行原生资源、尺寸、背景重建和 attach/detach 方法；观察回调用于恢复和退出安全模式后的重新绑定，不阻止原生方法。

## 配置、迁移与加载

旧 `qs_tile_2x1_corner_radius` 仅作为迁移读取键，新统一键优先。导出只写新键；旧键导入转换为新键。迁移只复制旧半径与记录迁移标记，不自动开启功能；旧配置的显式关闭值不再被覆盖。导入与运行时均约束 `0..80dp`。

所有功能主开关默认关闭，已有保存配置优先并继续开机加载。运营商的通知页、控制中心、锁屏三个场景分别默认关闭，单场景开启不依赖隐藏的全局开关。唯一默认行为例外为蜂窝数据箭头强制隐藏；安全模式只禁用运行效果，保留偏好。

preview5 加载标识为 `c17-runtime-20261002-layout-polish-preview5`；逐卡绑定阶段的修复标识为 `c17-runtime-20261002-native-binding-beta1`（versionCode 37）。安装新代码后须重启 SystemUI，随后配置变更即时更新已经跟踪的控件。后续正式核验结论见 versionCode 38 记录。

技术参考来自 MCGA 的 `TwoXOneTileHook.kt`，作者 Zhuangzhi Meng（Gustate XiaoMeng），许可 GPL-3.0-or-later；项目 GPL-3.0-only。根据目标系统签名重新实现为实例所有权方案，完整署名见第三方许可声明。

## 验证与实机边界

本轮生产源码（FeatureOptions、QsTileCorners、QsPanelCorners、ConfigTransfer、StatusBarModule）已独立以 Java 8 编译通过。定向检查使用本轮独立编译目录，尚不等于最终 Gradle 产物全套回归：

- `QsTileCornersCheck`：655 项，覆盖所有磁贴尺寸、默认关闭、迁移不改开关、0..80dp、真实像素归一化、临时/固定 block provider 所有权、布局监听、共享对象、主题、换绑、关闭、安全模式与异常恢复。
- `QsPanelCornersCheck`：170 项，覆盖滑块绘制与 blur 恢复、音乐背景/光照/高光一致性、临时 provider 动画途中关闭恢复、首次测量及尺寸变化、五类设备 native outline/PluginDrawable/材质、外层 outline invalidation、不改 clip 或布局、默认关闭、安全模式和 detach。
- `FeatureOptionsCheck`：7870 项；`CarrierPanelSettingsCheck`：244 项；`ConfigTransferCheck`：634 项；`SafeConfigCheck`：55 项。覆盖功能默认关闭、场景独立启用、类型回退、旧值保留、导入导出、强制蜂窝箭头与安全模式。

随后最终 Gradle Debug 的正式 Java classes 完整回归通过 39 组、52,947 项检查，classpath 未混入独立生产覆盖目录；`StatusBarModule` class major version 为 52（Java 8）。本轮只修正旧 Appearance/Scene/BatteryAppearance/PuiBatteryStyle 测试默认开启假设：先保留默认关闭原生行为断言，再明确开启相关主开关验证效果，没有修改生产代码或手机配置。测试编译输出为 `.local-private/preview5-full-checks`；完整日志及正式类 SHA256 记录见 `validation/build-checks/preview5-final-formal-results.txt` 和 `preview5-final-formal-metadata.txt`。正式包配置检查因新增清除动效注册键为 638 项。

preview5 实机已确认并实际加载新运行代码：0dp 时快捷开关和音乐成为方形，但设备卡仍为原生圆角，滑块轨道缩短且图标落在背景外。39 组逻辑回归不能替代这些失败证据。此前设备 fixture 只模拟 inner background 和 smooth outline，没有模拟独立的旧 outer provider；滑块 fixture 也没有约束真实 DEX 的 radius/endpoint 耦合，现已补上。

本轮 preview6 两个 panel/device 类以 Java 8 定向编译通过，`QsPanelCornersCheck` 新增的实际几何、factory 首次创建、nested 参数去重、真实 Path/Canvas/Paint 排除、旧 outer/new inner、各 surface 不同半径/权重、主题数组、换绑、关闭和安全恢复检查当前为 249 项通过。使用 `.local-private/corners-preview6-classes` 的独立生产覆盖目录，不是最终 Gradle 或实机通过结论。最终产物由主任务统一重新构建并跑全套。实机应在 `0/24/用户当前值/80dp` 下检查所有磁贴与面板控件的形状、完整轨道和图标位置、按压/点击、主题/尺寸变化、展开与编辑、开关及安全模式恢复；用户当前值最新为 34.5dp，不得为测试回写旧值。

随后 beta1 实机 `0dp` 已证实滑块高度与内部图标恢复正常，快捷开关、音乐和右侧入口成为方形；左侧耳机卡与中间笔记本入口仍为胶囊。这是当时待修复的失败证据，不能用逻辑回归取代。实际 `MyDevices.apk`、`SystemUIPlugin.apk`、`SubsysInterfacePlugin.apk` 没有该 QS SDK 的渲染类，SystemUI 内置 `SysUiPluginService` 实现 `QsPanelPlugin`，设备应用提供数据而不是据此推定绘制类。

本次修复增加精确原生绑定入口：`EditablePluginViewHolder.bindData()` 完成插件 `onCreateView()` 后，通过可访问的 private `getRealPluginContainer()` 逐一注册全部已识别原生子控件；原生 `SysUiPluginService.onCreateView()` 的 `MyDevicePanel` 是另一条已核对 DEX 的设备入口。遍历限于这两个真实容器，最多 10 层、96 个节点，实际子 View 的 ClassLoader 单独安装生命周期 hooks；再安排一次有界主线程注册，覆盖首次绑定尚未完成的子项。回收先逐卡恢复，原生尺寸、颜色和 listening 更新后重新绑定，安全模式保留这些原生观察方法。没有全局 `addView` 拦截、整体裁剪或图标/文本内容读取。类型诊断仅在主动开启 diagnostics 时记录有界的 native child/background/outline 类型，关闭时不消耗记录预算；不记录设备名称、标题或音乐内容。

逐卡绑定补充检查包含超过旧 4 层限制的 6 层原生容器、3 张不同设备卡、全部命中、0dp、独立安全恢复、回收监听释放及内容/点击几何保留；当时 `QsPanelCornersCheck` 共 **264 项通过**，完整 Module/Panel/Device 源独立 Java 8 编译通过，日志为 `validation/build-checks/beta1-device-bind-targeted.txt`。这是独立生产目录 `.local-private/beta1-device-bind-classes` 的定向验证，不能作为 versionCode 37 的设备卡实机通过证据。

随后 versionCode 37 的实机诊断已经确认逐卡绑定命中全部实际控件。耳机卡为 `RectangleDeviceCardView`，笔记本卡为 `SquareDeviceCardView`，右侧为 `SquareEntranceCardView`；三者的实际 inner background 都是 `MixColorPluginDrawable`，原生轮廓已写入 `0px`，但截图仍为胶囊。此前未知 `qs_corners` source 被日志工具归入 `app`，过滤造成的“没有命中”推断已撤销。这里不能再用增加遍历或假定主题 wrapper 来解释失败。

真实 DEX 进一步说明背景有第二份独立曲率：`MixColorPluginDrawable.autoBlurDrawable.getViewBlurProxy().getBlurConfig()` 的 scalar `cornerRadius` 与 `leftTopCornerRadius/rightTopCornerRadius/rightBottomCornerRadius/leftBottomCornerRadius` 是五个实际字段。`ViewBlurProxy` 的静态、窗口背景和平台模糊路径直接读取这些字段；普通 `RoundRectOutlineProvider.update(0,weight)` 不会自行同步它们。`getBlurConfig()` 只返回本实例字段，既不复制配置也不修改材质；`BlurConfig.setCornerRadius(float)` 同时更新全部五项。`GradientStrokeLineAdapter.adaptGradientStrokeParams()` 在模板为空或尺寸未就绪时仅清除 stroke 参数；模板有效时也只更新独立的 gradient-stroke `CornerParams`，不是主 blur 五字段的同步入口。这是 DEX 接口事实，不据此猜测每张实机卡片的 stroke 开关状态。

本轮修复从当前实际 `MixColorPluginDrawable` 读取同一 proxy/config，在原生 provider 全部更新后调用 `BlurConfig.setCornerRadius(实际有界像素半径)`，再调用该 proxy 的 `applyBlurConfig()` 刷新真正显示的背景。各卡片分别保存五字段原值，包含不对称角与原生负值哨兵；原生更新只改变一个角时分别合并，其他字段仍恢复原来值。配置或 proxy 被原生替换时恢复退休对象并拥有新实例。恢复先完整读取独立快照，再恢复 provider，最后恢复 blur config 并原生 apply，防止前一层 stroke 副作用覆盖后一层原值。异常回滚预先拥有整次事务，覆盖 provider 写入后抛错、config 部分写入和 native apply 已改显示后抛错。实际 provider 与其 PluginDrawable 不重复拥有，避免新 outer 引用把功能写入值误当成原生恢复值。材料、颜色、light 模板、blur amount、weight、内容和点击布局均不改动。

主动开启诊断时，短消息使用合法 `qs_style` source，分别记录实际 child/background 类型、body 尺寸，以及每个 config 的弱引用序号、scalar 和四角读回值、原生同步完成。实际几何设置改变后允许再次记录；默认关闭、预算有界、不记录控件内容，诊断读取失败不阻断功能或恢复。

当前 `QsPanelCornersCheck` 为 **743 项通过**，新增 provider 已为 `0` 而 visible blur 仍胶囊的旧实现复现，以及全部五种设备卡的五字段和实际 apply、`0/34.5/80dp` 与真实尺寸约束、原生单角更新、独立 config/proxy 更换、stroke 副作用、三阶段部分失败、安全模式/关闭/detach 恢复。复用 AutoBlur fixture 的 `QsTileAppearanceCheck` 另有 **514 项通过**。完整 Module/Panel/Device 源使用 API 37、Java 8 独立编译，class major version 为 52，日志为 `validation/build-checks/beta1-device-blur-targeted.txt`，生产目录为 `.local-private/beta1-device-blur-classes`。这一阶段属于定向逻辑证据；随后 versionCode 38 正式产物及实机核验记录如下。

## versionCode 38 的正式产物与实机核验

主任务已完成 versionCode 38 的 Debug/Release 构建、关键 lint、签名与对齐检查，正式 Gradle Debug classes 全套 **41 组、53,699 项通过**，其中 `QsPanelCornersCheck` 为 743 项。正式配置目录审计使用同一 Gradle classes，无独立生产覆盖：**16 个板块、288 项 UI 设置**，所有默认值通过各项自身校验；16 个板块主开关均默认关闭，115 项数值的滑块元数据有效。48 个颜色项目的配对与透明度映射完整，默认表和可导入导出配置没有未映射项；统一圆角只保留主开关与 `0..80dp` 半径两项，蜂窝箭头强制隐藏未暴露为可关闭开关。数值的普通滑块区间只是 UI 建议范围，圆角的 `0..80dp` 为硬校验，不能据审计把所有滑块建议区间误称为硬限制。

实机 versionCode 38 已重新加载本轮代码。真实控制中心在 `0dp` 下，快捷开关、音乐、完整高度的亮度/音量滑块和三张设备卡都变为矩形；耳机 `RectangleDeviceCardView`、笔记本 `SquareDeviceCardView`、右侧 `SquareEntranceCardView` 均已由私有截图确认。对应日志分别读回 config 1/2/3 的 scalar 与四角全部为 `0.0`，证明三张卡拥有不同原生配置并完成同步。关闭圆角主开关后，再次实机确认以上全部控件恢复原生圆角。这与 versionCode 37 的“绑定和轮廓写入成功，但三张设备卡仍为胶囊”失败证据有明确区别。

测试结束后已恢复用户当时实际保存的 `34.5dp`、圆角主开关开启、诊断关闭和原来的屏幕常亮设置；主配置测试前后 XML 的语义差异为 `{}`。手机截图与原始诊断日志保留在私有验证目录，不随公开文档发布。此处确认的是本轮 `0dp` 与关闭恢复场景；`80dp`、安全模式与异常恢复另有逻辑回归覆盖，不能把它们扩写为本轮全部实机通过。通知/控制中心图标位置等其他功能仍由主任务另行修复与验证，本记录不代表整版已经完成。

