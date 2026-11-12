# 图标管理 v3：当前 OEM 原生宿主与动态桥接合同

日期：2026-10-18。本文主体为本地静态技术审计，末尾另记非 Tile 与 QS 适配器的本地实现与检查；**没有运行外部 composition 的实机样例**。“签名存在 / 静态可接入”不等于该角色或动画已交付、已验收。

## 1. 证据来源与适用范围

`.local-private/device-before/SystemUI.apk` 与 `.local-private/device95-readonly/SystemUI.apk` 字节相同：98,883,595 bytes，SHA-256 `DA83AE63B6CFC43A4CE57F21EE7A54444E14DADA85B24AD36D2FE74181F0A927`。下述类、R8 字段和资源均来自此 APK，不混用其他 ROM 的 Lottie 文档或通用 SDK 猜测。

可复核的补充证据在忽略目录 `build/c17-dev/icon-v3-audit/`：

- `lottie/CONTRACT.md`：完整 Lottie / Effective API、构造器、R8 标记、跟色、Alpha、异步任务、末态与回收的逐项行号。
- `non-tile/metadata.json`、`non-tile/*.txt`：滑块、媒体子类、设备卡片、页脚的实际字段与方法体。
- `non-tile-extra/metadata.json`、`non-tile-extra/*.txt`：ClipBrightnessView、滑块二级页及详细磁贴、PanelDevice。
- `native-enums/metadata.json`：DeviceType 等真实枚举、QsDetailTileBaseView 的完整方法签名。
- `factories/tileimpl.OplusQSFactoryImpl.txt`、`spec-candidates.json`：47 个真实 stock spec → provider 分支及精确行号。
- `factories/widget.QsDetailTileBaseView.txt`：二级页也有独立 iconViewProxy，而非所有二级页都是普通 QSTileView。

OEM APK、反汇编代码及 OEM 图形不进入规范仓库。本文只保存契约和事实摘要。后续遇到类、方法、字段、宿主层次或枚举不符时，关闭该适配器并保留原生，不能按中文标题或附近 ImageView 猜对象。

## 2. QS 外部 Lottie 的准确反射接口

以下全部从**真实宿主 OEM ClassLoader**加载，不能把 App ClassLoader 中的 LottieComposition、KeyPath 或 callback 对象传入 OEM。反射查找放在能力初始化阶段，draw / measure 不查方法、不解析 JSON。

| 类 | 本机确实保留的签名 |
| --- | --- |
| `com.airbnb.lottie.LottieCompositionFactory` | `public static fromJsonInputStreamSync(java.io.InputStream, String): LottieResult` |
| `com.airbnb.lottie.LottieResult` | `public getValue(): Object`、`getException(): Throwable` |
| `com.airbnb.lottie.LottieAnimationView` | `public setComposition(LottieComposition): void`、`getComposition(): LottieComposition` |
| 同类 | `cancelAnimation(): void`、`playAnimation(): void`、`pauseAnimation(): void`、`resumeAnimation(): void` |
| 同类 | `getProgress(): float`、`setProgress(float): void`、`setSpeed(float): void`、`reverseAnimationSpeed(): void` |
| 同类 | `setRepeatCount(int): void`、`setRepeatMode(int): void` |
| 同类 | `public final cancelLoaderTask(): void`；没有 `cancelOldCompositionTask` 这个方法 |
| 同类 | `addValueCallback(KeyPath, Object, LottieValueCallback): void`、`clearValueCallback(KeyPath, Object): void`、`resolveKeyPath(KeyPath): java.util.List` |
| 同类 | `addAnimatorListener(Animator.AnimatorListener): void`、`removeAnimatorListener(...)`；`addAnimatorUpdateListener(ValueAnimator.AnimatorUpdateListener): void`、`removeUpdateListener(...)` |
| 同类 | `addLottieOnCompositionLoadedListener(LottieOnCompositionLoadedListener): boolean`、`removeLottieOnCompositionLoadedListener(...): boolean` |
| `com.airbnb.lottie.model.KeyPath` | `public KeyPath(String[] keys)` |
| `com.airbnb.lottie.value.LottieValueCallback` | `public LottieValueCallback(Object value)`；`public final setValue(Object): void` |
| `com.airbnb.lottie.LottieDrawable` | `public final clearComposition(): void`、`public setAlpha(int): void` |
| 同类，当前自有 Drawable 桥接实际使用 | public 无参构造器、`setComposition(LottieComposition):boolean`、`playAnimation():void`、`setRepeatCount(int)`、`setSpeed(float)`、`setProgress(float)`、`setRenderMode(RenderMode)`、`resolveKeyPath(KeyPath):List`、`addValueCallback(KeyPath,Object,LottieValueCallback)`；public final `animator:LottieValueAnimator` |
| `LottieValueAnimator` / `BaseLottieAnimator` | `addListener(Animator.AnimatorListener)`、`removeListener(...)`、`cancel()`；不能把 AnimationView 上的 `addAnimatorListener` / `cancelAnimation` 方法猜到 R8 后的 LottieDrawable 上 |

完整行号见 `lottie/CONTRACT.md` §2。Factory 的同步解析方法从 InputStream 构造 UTF-8 JsonReader；Exception 放入 LottieResult。使用 `null` cacheKey 可绕过 OEM 全局 composition cache，C17 自己按摘要、ClassLoader 和格式合同管理缓存。不要改 OEM 全局缓存容量，不要把 App 路径传给 `setAnimation(String)` 冒充 OEM assets。

### 2.1 跟色无需重播，但不能照通用 SDK 字段写

本机 R8 已移除 `LottieProperty.COLOR / STROKE_COLOR / OPACITY` 字段。原生 `QSLottieAnimationView.tintLottieJsonColor(Integer,Integer)` 用 **`Integer.valueOf(1)` 表示 fill color、`Integer.valueOf(2)` 表示 stroke color**；FillContent / StrokeContent 使用同一 boxed integer 的 identity 比较。该数值只属于已核对的当前 OEM 能力，不能冒充所有 ROM 的标准常量。

`LottieProperty.COLOR_FILTER` 字段存在，但 `LottieDrawable.setColorFilter(ColorFilter)` 方法体只有 warning，不着色。不能因为宿主是 ImageView 就假设 `setImageTintList` 已能着色外部 Lottie。

适配器应在 composition 绑定后解析 KeyPath，为每个 resolved target 保留独立 callback。颜色变化只调用 `callback.setValue(newRgb)`：方法只更新值并通知关联 animation，无 play、无重绑、无进度归零。不要直接写 `.value`；同一个 callback 只保留一个关联 animation，不应跨多个 resolved target 共用。

原生自动 tint 只识别路径名包含 `startFill/endFill/startStroke/endStroke` 的图层。任意外部图层不会自动跟色；原色素材若碰巧使用这些名字又可能被 OEM 加载回调改色，需明确命名与回调所有权。`getCallbackListener / setCallbackListener` 的类型是 `LottieOnCompositionLoadedListener`，不是颜色 callback。

### 2.2 Alpha 单独处理

FillContent 绘制时丢弃 color callback 整数的 A，用父 alpha × shape opacity 重建 Alpha。因此往 fill callback 填半透明 ARGB 不能代替正确透明度。

原生 `QSLottieAnimationView.setTintColorfulConfig` 会给 ImageView.alpha 安装原生过渡监听；重复调用还会移除 update listeners，不应作为 C17 每次跟色的刷新方法。

可实测验证的路径是：保留原生 View.alpha；C17 只对**自己拥有的 LottieDrawable**通过 `setAlpha(int)` 施加一次用户透明度。此 setter 不重播。若原生 imageAlpha 或其他路径也写同一 Drawable alpha，应跟踪最新原生基线，不能写死 255，也不能把 tint 中的同一原生 Alpha 再乘一次。这仍需非 100% 原生 Alpha 的实机样例。

### 2.3 外部 composition 的绑定与恢复约束

不能只在 `setIcon` 后调用一次 `setComposition`：

1. `cancelAnimation` 会关 autoPlay、清 lazy tasks、取消 animator，但**不取消 compositionTask**；取消可能触发旧 end listener，先建立所有权与重入保护。
2. `cancelLoaderTask` 只脱钩旧任务的 success / failure listeners，不停止后台解析。同步外部 parse 完成后仍要核对 generation、宿主、真实 spec / role、来源摘要。
3. `setComposition` 会设置 drawable callback、可能 autoPlay、requestLayout 并同步通知既有 compositionLoadedListeners；不是纯字段赋值。
4. `QSIconViewProxy$setIcon$1.onAnimationEnd` 在 transaction 相符时调用原生 `updateIcon`，会把自定义图形盖回。自定义末态也必须在同一精确宿主绑定链中受控接管；不能全局删除 OEM listeners。
5. `QSIconViewProxy.onRecycle` 只 `cancelAnimation + setImageDrawable(null)`，内部 composition 仍可能保留。仅对 C17 自己拥有的 composition 执行 clearComposition，并移除自有 listeners / callback 引用，恢复原生 glyph。
6. Drawable 的默认不可见行为会 pause，重新可见又 resume；detach / 收起 / 息屏要求的“停止且不自动复播”需要明确适配器门控。

首次绑定、换 spec / 来源、回收后复用只显示正确静态末态。稳定转场需保留原生动画许可、isShown、全局许可及真实瞬态；不替换整个磁贴、不改点击与无障碍。

## 3. 非普通 Tile 的覆盖矩阵

下列 role 是可登记的**语义候选**，最终名称须与 IconRoleRegistry 保持一致。只有实际宿主能力检测成功、实现该绑定/恢复合同后才显示“支持”；不因列入表格就算已适配。

| 类别 / role 候选 | 精确宿主与绑定点 | 状态与可换边界 | 恢复与未知边界 |
| --- | --- | --- | --- |
| `slider.brightness.default` | `com.oplus.systemui.qs.base.seek.ClipBrightnessView extends View`，`brightnessIcon:Drawable`，public `getBrightnessIcon / setBrightnessIcon(Drawable)`；`updateIconColor():void`、`updateIconConfig():void` | 主亮度图形；不是 ImageView，也没有已证明的 low / medium / high 图形等级。OEM 根据真实 autoMode、主题和区域状态改变资源/颜色 | `updateIconColor` 直接写字段，绕 setter；只 hook setter 会漏。保留 bounds、原生 degree / clip / rotation；退出恢复最新原生 Drawable |
| `slider.brightness_auto.default` | 同类 private `getBrightnessIconMode():Drawable`；`handleDraw(Canvas)` 先画主图形，再在真实 autoMode 且非 global theme 时画 overlay | 独立的自动模式 overlay，不是强制打开自动亮度；不得在新主图形里重复画 A 再叠原生 A | 不改变 autoMode；getBrightnessIconMode / Lazy 归属需精确处理，未知 ROM 保留原生 overlay |
| 亮度二级页 | `OplusQsDetailHookClipBrightnessView extends ClipBrightnessView`，覆写 `handleDraw(Canvas)`、`updateIconColor()`、`updateRotation(float,int)` | 复用语义，宿主是不同 subclass | 不能只 hook 基类覆写点就宣布二级页覆盖；逐类核验后接入 |
| `slider.volume.normal.*` | `OplusQsVolumeIconView extends QSLottieAnimationView`；`updateIconState(QSVolume.VolumeState,boolean)`、`updateIconColor(boolean,boolean)`，controller 的精确字段 `volumeIconView` | route=2。mute 为 level=0；low 为 `0<level<=int(maxLevel*.33)`；medium 为 `<=int(maxLevel*.66)`；high 为其余正数 | level<0 或 route=-1 不更新；ZenMuted 另保留策略语义；不能按 native tint 或 `isAboveThreshold` 推测音量档位 |
| `slider.volume.bluetooth.*` / `slider.volume.wired.*` | 同一 VolumeIconView，真实 route=1 / 0；对应 route state | 均只有 mute / unmute 图形证据；不虚造耳机三档。bluetooth 有原生独立 JSON，有线当前状态类无播放证据 | 其他 route 是 idle，未有独立导入图案合同，保留 native；具体动画帧区间尚未冻结 |
| `media.play_pause.*` | `OplusQsBaseMediaPanelView.playOrPauseBtn` → `OplusQsPlayPauseButton extends OplusLottieButton` → `getImageView()` | 按真实 `setPlaybackState(int)` / PlayData 确认播放、暂停；当前静态字形宿主准确 | `setImageResourceInternal(int)` 直接写内部 ImageView，绕父 setter；需要状态与末态绑定。媒体动画用第二引擎，不能直接用 QS composition |
| `media.previous.default` / `media.next.default` | 同 base media view 的 preBtn / nextBtn；`setSemanticButton(OplusLottieButton,int,MediaAction,MediaData)` 与 `bindViewWithAction(...)` | 只在语义确认是标准上一首/下一首时替换。`MediaButton.getPrevOrCustom / getNextOrCustom` 明确允许 custom action | 自定义动作与通知动作图标保留真实来源，不能把所有按钮按位置假定上一首/下一首；不改 Runnable、点击/长按、contentDescription |
| `media.output.default` | base media view 的 mediaOutputBtn:ImageButton；`bindMediaDataInner(MediaData,String)`、`updateMediaOutputBtn(int)` | OEM 默认输出入口可换；MediaDeviceData 的真实设备/品牌 drawable 不自动改成统一符号 | 需要区分 `getIconResId` 与 `getIcon` 分支及设备来源；未确认默认分支保留 native |
| 媒体更多、收藏、歌词 | base media 的独立 placeholder / button 路径 | 不能由 pre/next/play 三个按钮推导全部媒体图标已覆盖 | 本轮没有冻结所有目标的 role 与 binder，继续 native，UI 不应宣称完整媒体覆盖 |
| `device.entrance.default` | `SquareEntranceCardView.m:ImageView`；`RectangleEntranceCardView.p:ImageView`；其 `g()` / `i()` / `onConfigurationChanged` 重建字形 | “我的设备/连接设备”入口，非 on/off | 精确字段 + 祖先形状确认；保留原生入口图案 tint、布局和点击。Rectangle / Square 分别检测 |
| `device.<type>.default` 候选 | SquareDeviceCardView 的 `p.f`、RectangleDeviceCardView 的 `p.f`，`p:BatteryCircularProgressLayout`、`f:ImageView`；`setDeviceIcon(int)`、`setDeviceIconWithAnimation(int)`、`a(Drawable)`、`a(int,int)` | PanelDevice 有真实 `getDeviceType()`、`getIconUri()` 与 square / rectangle default / inUse / notInUse 资源 getter；只有核对出的系统默认资源分支可换通用图形 | 用户/插件 iconUri、品牌照片、远端图像保留；不动电池环 `c`、充电图标 `d`、状态/文字。已知字段不等于全部 DeviceType 映射已完成 |
| 无设备卡片 | `NoDeviceEntranceCardView` 的 p/s/t/u 多 ImageView | 当前不是一个 ImageView；静态方法里部分用 setBackground 设置图形 | 不一概换所有子图；未冻结四个图形各自 role，保留 native |
| `footer.settings.default` | `OplusQSFooterImpl.mSettingsButtonIcon:ImageView`，onFinishInflate 用 ID `0x7f0a0d27` 获得 | 默认设置入口，非 toggle | `refreshFooterButtonIconColor()`、`updateTextColor(int)` 按原生 ColorStateList 跟色；主题/配置/政策可见性保留 |
| `footer.more.default` | 同类 `mMoreIcon:ImageView`，ID `0x7f0a08a5` | 默认更多入口，非 toggle | 同上；它不是编辑/电源等弹出菜单每个子项都已覆盖的证据 |
| 用户头像 / 运营商 / 时钟 | Footer 的 mMultiUserAvatar / carrier / clock 等 | 保持内容语义，不作为通用符号替换 | 不扫描并改写所有 Footer ImageView；用户头像不是“通用用户”glyph |
| 二级页 header / footer | `QSDetailHeaderBinder.bind(...)` / `QSDetailFooterBinder.bind(...)` 使用 repeatWhenAttached；header collector 有 COUISwitch | 主开关、标题/按钮，不是已证实的返回箭头图标宿主 | 没有核对到 back / edit / power 的准确字形绑定时，不给相应 alias 声称支持 |
| 亮度/音量二级页小磁贴 | `QsDetailTileBaseView extends LinearLayout`，`init(QsDetailTile)`、`getIconViewProxy():QSIconViewProxy`、`onStateChanged(State)` | 真实模型具有 BooleanState；能在 init 参数取 `getTileSpec()` 缓存身份，再把独立 proxy 与该角色关联 | 不假设 State.spec 总是已经由普通 QSTile host 填好；不根据标题识别。Sep / Std 子类有各自 updateIconTint 来源 |

静态原生结构还证明 `OplusQsVerticalSeekBar / COUIVerticalSeekBar` 主要画轨道、底色及材质；亮度和音量字形由上表的独立 ClipBrightnessView / VolumeIconView 负责。把滑块 onDraw 的整个输出替换为图案会破坏轨道，不应作为字形替换入口。

### 3.1 设备类型的真实边界

PanelDevice 的 `getDeviceType()` 是 `com.oplus.deviceplugin.sdk.entity.DeviceType`。本 APK 枚举确有 `PHONE/PAD/COMPUTER/HEADSET/TV/WATCH/WRISTBAND/BTDEVICE`，也有 `IPHONE/IMAC/MACBOOK/BT_AIRPODS/BT_SONY/...` 及家电等品牌/类别。枚举存在只证明可以获得结构身份，**尚未逐一核对哪个 glyph 分支是通用默认图、哪个是品牌图/自持素材**，不能立即把所有这些类型合并成通用设备图片。DeviceState / ConnectState 的全部语义也未冻结，首轮不造 connected/disconnected 全套状态。

### 3.2 媒体的第二动画引擎

`OplusLottieButton` 是 FrameLayout 包装，内部静态 `ImageView` 与 `com.oplus.anim.EffectiveAnimationView` 是两层不同 View。`getImageView()` / `getLottieAnimationView()` 是 public final；`setImageDrawable` 只转发静态层；`hideLottieView()` 只改可见性，**不停止动画**。

Effective 引擎确有自身 factory / composition / cancel API，但不是 `com.airbnb.lottie` 类型；媒体状态队列与完整回收未核完，不把 QS 动态支持直接扩展成媒体动态支持。当前可先接真实静态 glyph 与准确末态，媒体外部动态能力保持未验收。

## 4. 标准 Tile spec 证据与状态护栏

`OplusQSFactoryImpl.createTile(String)` 的逐分支对比提供以下 **47 个 stock spec 候选**。这里保留 OEM 原始拼写，不按英文纠错，也不把 `custom(...)` 或 innerFake 复合卡片当普通固定角色。逐项 provider 与行号在 `factories/spec-candidates.json`。

| 类别 | 当前 factory 中的真实 spec |
| --- | --- |
| 常见连接/状态 | `wifi`、`bt`、`cell`、`airplane`、`location`、`hotspot`、`nfc`、`vpn`、`otg`、`rotation`、`dnd`、`nodisturb`、`vibration` |
| 显示/电量/隐私 | `darkmode`、`autobrightness`、`autoscreentemperature`、`battery`、`superpowersave`、`cameratoggle`、`mictoggle`、`saver` |
| 工具/入口 | `flashlight`、`screenrecord`、`screenshot`、`lockscreen`、`shutdown`、`calculator`、`qr_code_scanner`、`controls`、`wallet`、`work`、`inputmethod`、`clear`、`smallscreen`、`multisystem`、`googlenow` |
| OEM 特性/三段模式 | `ringermode`、`dobly`、`osie`、`lris`、`breathinglight`、`phoneantitheft`、`envelope` |
| 明确的支付/扫码入口 | `alipay`、`aliscan`、`weixinpay`、`weixinscan` |

稳定别名建议保留已审核的 `tile.bluetooth → bt`、`tile.wifi → wifi`，其余从显式表映射，别让包提交任意 native spec / 反射类。`envelope` 的 provider 是尾部分支共享目标，自动临近行提取记录为 null，但同一方法末尾确返回 mEnvelopeTileProvider；不能因此把中间日志字符串当成其他 spec。

Factory 只证明身份，不证明该 ROM 所有候选都实际可用、都可见或都是开关。状态必须从真实模型读取：

- 普通已核对开关的 `state=0/1/2` 分别对应 unavailable/off/on；额外 state、threeStageMode、未知复合模式保持 native。
- calculator / pay / scan / lock / power 等动作入口只提供 default；不能把资源 state=1 当“关闭”。
- `ringermode` 的 provider 明确是 `ThreeStageRingerModeTileProvider`，未核对响铃/振动/静音字段前不压成 on/off。
- 语义开关相反时按真实功能命名：camera/mic 是允许/阻止，rotation 是自动旋转/锁定，不能只用一套“开/关”字样糊过去。
- 当前工厂没有 `cast` 这个直接分支，不据通用 Android 名称虚造当前 ROM 的 stock Cast 支持。外部 TileService / `custom(...)` 默认原生，需要另有经过审计的映射或用户显式指派。

二级页模型明确的额外 spec 为 `autobrightness`、`autoscreentemperature`、`darkmode`、`mediumute`（OEM 原始拼写）、`noisereduction`、`spatialaudio`。其类的 newTileState / handleUpdateState 是 BooleanState；这些图标通过独立 QsDetailTileBaseView 的 proxy 绘制。`mediumute` 不等于铃声音量的 ringermode。

## 5. 可以实现与尚未验收的区分

目前已有足够静态证据来实现：QS 背景解析 / OEM ClassLoader composition 绑定；自有 KeyPath callback 无重播跟色；精确宿主静态图形替换；受控静态末态与回收；上表真实角色的能力探测。

仍须保留未完成标记：外部 composition 在真实宿主的可见效果、原色与 mask 跟色、非 100% 原生 Alpha、快切/反播、不可见后无复播与无后台帧、回收恢复；媒体 Effective 外部动态；所有设备类型/品牌分支；无设备四图；更多菜单中的 edit / power / back；所有可选第三方 detail 内容。

后端登记表需要分别记录 `身份已核对 / 静态宿主已接入 / 动态 API 已探测 / 本地回放 / 实机验收`。只存在资源、类或方法时不得填“已适配所有控制中心图标”。

## 6. 非 Tile 适配器的安装合同

`IconNativeControlCenter.java` 仅处理下列固定宿主。所有事件在原生方法正常完成后调用；唯独 getter 的返回值交给 `brightnessOverlay` 后替换结果。适配器不改原生点击、内容描述、布局、View alpha / tint，不删除原生动画监听。`configure(active)`、`refresh()` 与所有回调在 UI 线程运行；模块释放时调用其 `releaseRuntime()`，再由统一 Runtime 单独释放自己的缓存。安装 hook 由 `StatusBarModule` 负责，不能仅凭适配器源码存在即标记实机可用。

以下简写均对应源码中同名 String 常量；完整类名在 §3 及适配器常量中固定，不按包名模糊匹配。`Configuration`、`Resources`、`Drawable` 分别指 Android 原类型；`MediaAction`、`MediaData`、`OplusLottieButton`、`PanelDevice`、`VolumeState` 均须从真实 OEM ClassLoader 解析。

| 精确类 | 精确方法签名 | after 回调 |
| --- | --- | --- |
| `BRIGHTNESS` | `init():void`、`updateIconColor():void`、`updateIconConfig():void`、`disableTheme(boolean):void`、`setBrightnessIcon(Drawable):void`、`setAutoMode(boolean):void`、`setIconColorFilter(int,PorterDuff.Mode):void` | `onBrightnessUpdated(receiver)` |
| `BRIGHTNESS` | private `getBrightnessIconMode():Drawable` | `result = brightnessOverlay(receiver, result)` |
| `DETAIL_BRIGHTNESS` | `updateIconColor():void`，此方法为子类覆写，独立安装 | `onBrightnessUpdated(receiver)` |
| `VOLUME` | `updateIconState(VolumeState):void`、`updateIconState(VolumeState,boolean):void` | `onVolumeState(receiver,arg0)` |
| `VOLUME` | `updateIconColor(boolean,boolean):void`、`disableTheme(boolean):void` | `onVolumeUpdated(receiver)` |
| `FOOTER` | `onFinishInflate():void`、`updateResources$11():void`、`updateTextColor(int):void`、`refreshFooterButtonIconColor():void`、`updateQsMoreButton(Resources,boolean):boolean` | `onFooterUpdated(receiver)` |
| `MEDIA` | `setSemanticButton(OplusLottieButton,int,MediaAction,MediaData):void` | `onMediaSemantic(receiver,arg0,arg1,arg2,arg3)` |
| `MEDIA` | `bindViewWithAction(OplusLottieButton,MediaAction,MediaData):void` | `onMediaCustom(receiver,arg0)` |
| `MEDIA` | `updateMediaOutputBtn(int):void` | `onMediaOutput(receiver,arg0)` |
| `MEDIA` | `bindMediaDataInner(MediaData,String):void` | `onMediaBound(receiver,arg0)` |
| `MEDIA` | `unBindMediaData():void` | `onMediaUnbound(receiver)` |
| `PLAY_PAUSE` | `setPlaybackState(int):void`、`handleAnimationEnd():void`、private `setImageResourceInternal(int):void` | `onMediaPlayPause(receiver)` |
| `SQUARE_ENTRANCE`、`RECTANGLE_ENTRANCE` | `g():void`、`i():void`、`onConfigurationChanged(Configuration):void` | `onDeviceUpdated(receiver)` |
| `SQUARE`、`RECTANGLE` | `d(PanelDevice):void`、`g():void` | `onDeviceUpdated(receiver)` |
| `RECTANGLE` | `onConfigurationChanged(Configuration):void`；本 APK 的 `SQUARE` 未声明此覆写，不虚报 | `onDeviceUpdated(receiver)` |
| `PROGRESS` | `setDeviceIcon(int):void`、`setDeviceIconWithAnimation(int):void` | `onDeviceIcon(receiver,arg0)` |
| `PROGRESS` | `a(Drawable):void` | `onDeviceDrawable(receiver)` |
| `PROGRESS` | `a(int,int):void`、`onConfigurationChanged(Configuration):void` | `onDeviceUpdated(receiver)` |

补充的真实 API：`ClipBrightnessView.getAutoMode():boolean`，**不是**返回 ObservableField；其字段 `autoMode` 才是 Observable。适配器仅在事件回调缓存此布尔，overlay getter 不再每帧反射。`setIconColorFilter` 同时影响主图和 Lazy overlay，必须接入此事件才能可靠继承实时前景色。`PorterDuffColorFilter.getColor()` 在当前 SDK stub 中隐藏，生产代码以缓存反射读取；不可变 ColorFilter 的色值以弱 key 缓存，稳定 overlay 不每帧反射、重建 wrapper 或请求资产。无法取得 metadata 时保留既有原生 filter。

动态权限的边界：音量原生 `isAnimating()` 为 true 时不覆盖动画，适配器只加自己的结束监听，原生结束后 post 一次读取最新状态；设备 `k:AnimatorSet` 同理。detach、关闭、替换资源或释放会移除自有监听与待执行 post。媒体只包 `getImageView()` 的静态层，保留 Effective 动画层及可见性，不声称支持外部媒体动态。

设备首轮仅接 `PHONE/PAD/COMPUTER/HEADSET/TV/WATCH/WRISTBAND` 七种的 `.default`，还须同时满足：真实 `PanelDevice`、空 iconUri、真实 DeviceType enum、`setDeviceIcon*` 入参等于当前 Resources 内 `device_card_<type>` 的准确默认资源。品牌类型、BTDEVICE、照片 URI、未知资源/Drawable overload 均保留原生。后者会清除旧资源身份，防卡片复用误换照片。设备状态、充电图、电池环不归该 adapter 改写。

媒体同样保留边界：只有标准 `isStandardAction` 且非空 Runnable 的语义绑定可接入；pre/next 还比对宿主真实资源 getter；play/pause 的原生 `state=2/3` 分别是播放图/暂停图。输出仅 route=2、非分享且未被 `MediaDeviceData` 的品牌/真实设备图覆盖时接 `media.output.default`。未知动作、route、状态或签名保持原生。

本地验证：`IconNativeControlCenterCheck` **405 项**通过，使用真实生产 adapter 与当前 `IconManagementRuntime` 源码一起编译，包含准确原生类型/字段、标准/自定义动作、原生动画结束后绑定、detach / 关闭 / 重建 / 原生新资源 / 释放恢复，以及实际黑白前景 metadata、100 次稳定更新无重复 wrap/request/tint、真实 `drawNative` 的浅深配色选择与 50% 透明度、绘制后原生 filter / alpha 恢复。测试不把 APK 安装 hook 或手机观感作为已验证。既有共享 fixture 检查 `QsPanelCornersCheck` 903 项、`QsMediaAppearanceCheck` 3137 项、`QsTileIconSizeCheck` 245 项也全部通过；新媒体 fixture 保留这些既有合同，未缩小检查。

## 7. 状态栏仅修改颜色 / 透明度的绘制合同

状态栏 `hint / wifi / cellular / battery` 没有替换图源时，也通过既有绘制入口应用显式外观。`IconPackDrawing` 缓存完整 role，从 `IconManagementRuntime.bitmapForRole` 直接读取缓存；无位图时转 `drawNativeRole`，不通过每帧新建 wrapper、解析配置或拼接角色字符串实现。`StatusBarModule` 的既有 `ScaledDrawable.drawSignal`、`StatusBarIconView.getIcon` 返回值包装、电池 `draw` 周围 `beginBattery` scope 即可接入，无需增加全局 draw hook 或更改布局。

临时着色统一委托 `IconNativeAppearance`：原生 `LayerDrawable` 和 `DrawableWrapper` 保持自己的 draw、bounds、层次与顺序，helper 只在准确叶节点临时施加外观，随后 `finally` 恢复每个节点的独立 alpha / filter。不会给整个 Layer 设置一个 alpha 后再以 null filter 恢复；那会使原生背景、外框、进度的相对透明度丢失。共享节点只捕获/施加一次；嵌套调用由 Core nativeDrawing guard 防重复叠加；绘制异常也逐节点清理。未审计的 DrawableContainer 动画和 Ripple 遮罩保持原生，不能为未知组合硬改父层属性。

纯透明度沿用原 filter，仅乘用户 opacity；显式 custom RGB 替换已知原生 filter 时只保留其中的 Alpha 一次，不再乘同一个 native tint Alpha。没有显式 filter 而有真实 tint metadata 时，保留该 tint Alpha 一次。未知 filter 不臆造其语义：保留 filter，用户 opacity 仍独立处理。浅/深 palette 优先由真实前景 filter RGB 选择，其次才是宿主传入的 tint metadata。

电池仍使用已审计的五个外形槽位代理，数字、字体、充电闪电与独立 charging drawable 不进入外观事务。图片替换继续保留原有百分比设置门槛；**只有外观设置时，关闭百分比也能调整原生 bg/progress 的颜色和透明度**。充电 family 与普通电池 family 独立选择，不偷用其它类别的配置。当前 OEM95 的 `BatteryBarDrawableBase.draw` 253–267 和 `HorizontalBatteryContentDrawable.draw` 484–516 只读取各 shape 并绘制、对空槽位检查，不在该段更新 bounds/level；因此按原生允许 inactive slots 为 null，只包装实际存在的 shape，退出恢复完全相同的字段对象。证据在忽略目录 `build/c17-dev/icon187-appearance/native/`。

热路径预算：helper 树以弱 drawable 引用缓存，快照数组与 ColorPlan/filter 缓存复用；角色在 binder/资源变化时计算；电池三个真实 public int getter 使用绑定阶段建立的 MethodHandle，避免 Method.invoke 每帧参数数组/Integer；BatteryScope 按有界嵌套深度复用。树/身份/配置变化允许重建，稳定 draw 不执行文件、Provider、解码、反射查找或新增资产工作。

验证与边界：`IconNativeAppearanceCheck` **80 项**、`IconPackAppearanceCheck` **170 项**、既有 `IconPackDrawingCheck` **18,422 项**通过。原绘制类的真实已编译负对照在首项 appearance-only Wi-Fi 返回 false；旧 Core Layer 事务的真实负对照把应得 alpha20 画成128，新事务通过。覆盖 filter/tint Alpha 不重复、未知 filter、子层不同 alpha、Wrapper 内嵌 Layer、共享节点、换子图、异常恢复、100次复用、关闭恢复、百分比关闭/null槽位及电池数字/闪电保留。

另以 JDK17 ThreadMXBean 暖机观察 10,000 次真实调用：helper 与 battery begin/close scope 的样本为 0B；hint/signal 样本为总计 0–16B，未见逐帧分配趋势。该测量使用桌面 Android 边界 fixture，只佐证本地分配预算，**不代表手机 Skia 像素、ART 开销、帧率或实机视觉验收**。本批未操作手机；统一正式构建与手机验收另记。

## 8. code187 QS 接入实现与本地边界

`IconNativeQs` 以 `QSIconViewProxy` 为唯一普通/二级页 icon binder，`IconNativeQsHooks.install(ClassLoader, adapter, Install)` 安装精确方法。没有再 hook OplusQSIconView / Impl 的委托 `setIcon`，避免同一次原生更新重复接管。所有绑定、可见性与生命周期回调在 UI 线程；只有字节校验与 OEM composition 同步解析在专用工作线程。

| 精确方法 | 调用时序与职责 |
| --- | --- |
| `QSIconViewProxy.setIcon(QSTile.State,boolean)` | 原生前取得 Ticket；finally 根据原生是否正常完成收敛；从真实 State.spec/state/isTransient/lottiePrefix/policy 取身份与许可 |
| `QSIconViewProxy.updateIcon(QSTile.Icon,boolean,boolean)` | 原生后维护静态末态，或将晚到的原生 end callback 覆盖恢复成同一个自有动画 Drawable，不重播 |
| `QSIconViewProxy.updateIconTint()` | 原生后只更新静态 metadata 或自有颜色 callback / Drawable alpha |
| `QSIconViewProxy.onRecycle()` | 原生前停止并清理自有动画、监听、attach listener 和弱绑定 |
| `QsDetailTileBaseView.init(QsDetailTile)` | 原生后用 getTileSpec / getIconViewProxy 建立空 State.spec 时的明确身份；非空 State.spec 优先 |
| `View.onVisibilityAggregated(boolean)`、`onWindowVisibilityChanged(int)` | 原生后仅处理已登记的精确 QS ImageView；隐藏立即停止，不在再次显示时自动复播 |
| `ImageView.setImageAlpha(int)` | 原生后仅对正在播放的已登记宿主重算自有 Drawable alpha |

统一模块另调用 `configure(active)`、`refresh()`、`setInteractive(boolean)`、`releaseRuntime()`。关闭、安全模式、换源、回收、detach、收起、息屏与模块释放均停止自有 animator、移除自有 listener、clearComposition 并恢复最新原生 glyph；只操作自有 Drawable，不删除 OEM listeners，不改 OEM 全局缓存。正常动画完成后恢复真正当前状态对应的静态图案。未加载图案时仍请求该真实 role；资源到达后的失效事件补上静态图案。role/sourceRevision 的请求去重防止稳定刷新重复发起请求。

来源改变会推进 `sourceRevision`，取消未完成绑定；颜色、透明度和动画许可变化不推进来源版本，不重解码同一 composition。解析缓存按 **SHA-256 + 宿主真实 ClassLoader** 隔离，最多 8 项 / 2 MiB；失败摘要最多 16 项，显式来源/资源事件清除失败缓存以允许恢复，不进行计时轮询。工作开始、主线程发布和 release 后均检查 token；过期排队任务不执行解析，release 期间完成的解析不能重新填回缓存。弱绑定的 value 不强持 View、proxy、State 或 Drawable；resolved callback 亦以弱引用保留，避免 Drawable.Callback 反向留住 View。

动态只在同 spec / 前缀 / 来源的稳定更新上尝试；首次绑定、换身份、换来源及回收复用显示静态末态。真实 caller 动画许可、宿主可见/附着/屏幕唤醒、原生全局 QS 许可、弹窗限制、实际 native Lottie asset 可用性、policy 与系统 Animator 许可均须通过。瞬态只跟随真实 isTransient；状态完成后停止循环。转场必须有同源真实起止静态资源，不能借默认主图案拼状态。`reversible=true` 时可借用同一已校验来源的反向 transition，以负速率和末帧起播；采用当前真实静态末态的 mask/color 规则。不可逆、错误 target/from/to、缺图、未知身份与未知状态保持静态/原生。快切取消旧会话与过时解析，最终停在最新目标。

当前从 [QS 状态合同](ICON-V3-QS-STATE-CONTRACT.zh-CN.md) 的 47 个真实 stock spec 中实现 45 个静态计划：30 个二值功能/状态、13 个固定 default 动作、三段铃声和录屏。ringermode 必须 rawstate=2 且 threeStageMode 为 0/1/2，分别为 silent/vibrate/ring；screenrecord 用 raw0/1/2 对应 unavailable/idle/recording，不读固定 value 判断录屏。controls / wallet 的复杂服务、卡片和品牌内容仍不支持；cast 没有该 factory 身份，继续原生。仅原有 12 个已核常用目标声明 QS 外部动态能力，其余静态目标不自动套普通 toggle 动画。非 Tile 媒体仍保留 Effective 原生动画。

蒙版动画为每个真实 FillContent / StrokeContent 安装独立 Integer1 / Integer2 callback；主题只调用 setValue，不重新 setComposition / play。原色动画默认不安装着色 callback，切到自定义颜色后安装、恢复原色时只移除自有 callback。Alpha 使用 OEM 静态 updateIcon 已稳定后的 ColorStateList Alpha × ImageView.imageAlpha × 用户 opacity，一次写入自有 Drawable；不把同一透明度再写 View.alpha。原始 shape opacity 仍由 Lottie 自身计算。

本地 `IconNativeQsCheck` 执行真实生产 adapter 与 8 个实际 hook 安装/拦截路径，使用当前 OEM 保留的反射 API 形状和真实准入 JSON。覆盖同状态更新/晚到回调、异步末态图案、异常 finally、ClassLoader 隔离与 LRU、首绑/复用、快切/反播/瞬态、隐藏/息屏/释放、解析失败与资源恢复、无重播跟色及原色回切。此检查不代替真实 OEM 解码、帧绘制、系统调度或肉眼效果验收；非 100% 原生 Alpha、后台无帧和真实动画效果仍须装机确认。supplier-only Icon 不二次执行 supplier，无法取得安全同步 Icon 时不强制建立外部动画。
