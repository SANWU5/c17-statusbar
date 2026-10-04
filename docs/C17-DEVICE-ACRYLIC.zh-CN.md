# C17 设备卡片亚克力底色

## 原生链路

本次审计使用工作区已保存的 ColorOS 17 `SystemUI.apk`、`SystemUIPlugin.apk`。这些文件用于核对原生契约，不代表已在用户本轮反馈的机型实测。

设备卡片类位于 `com.oplus.deviceplugin.sdk.ui.view.separatecardview`。`getContentView()` 返回外层整卡，不能拿它覆盖背景；背景属于以下内层 View：

| 原生类 | SystemUI 内层 ID |
| --- | --- |
| RectangleDeviceCardView | rectangleCoLayout |
| SquareDeviceCardView | square_device_constraintlayout |
| NoDeviceEntranceCardView | no_device_constraintLayout |
| RectangleEntranceCardView | rectangle_entrance_constraintLayout |
| SquareEntranceCardView | square_entrance_constraintLayout |

原始资源表确认这些 ID 属于 `com.android.systemui`。SDK 工厂 `com.oplus.deviceplugin.sdk.ui.view.drawable.b.a(Context, View, c)` 根据状态选择原生 separate/standard QS builder，返回 `PluginDrawable`。常规硬件链路是 `MixColorPluginDrawable` → `AutoBlurDrawable` → `ViewBlurProxy.getBlurDrawable(...)` → 原生 `BaseDrawable`/`BlendDrawable`。主题或非硬件模糊路径可以使用内层 `GradientDrawable`。

真实 SDK `g()` 给这些 body 使用 `drawable.c.a`，工厂随后调用 `getSepQSInactiveBlurDrawableBuilder`，该 builder 直接使用 `QSBlurConfigProvider.getSepInactiveBlurConfig`。连接设备的 active 状态用于另一图标材质路径，不能据此把整张设备 body 当成 active 磁贴。原生 body 与分离控制中心未激活磁贴具有同一调色来源。

## 修复范围

- 仅将上述原生内层背景纳入设备底色规则。必须同时匹配类、ID 名、资源包、当前背景实例和所属 View；图标、文字、外层卡片及前景不参与底色替换。
- 在高光去除的控制中心分区启用时，设备 body 和普通未激活磁贴使用相同的原生混色列表、base/common 上传和 RGB 着色策略。关闭自定义背景时只去除光学层；启用时按对应用户 ARGB 的 alpha 混合 RGB，原生 alpha 保留。
- 硬件材质通过同一次原生 `getBlurDrawable` 结果识别所有权，不二次获取模糊实例，不截屏、不计算 CPU 模糊、不定时轮询。
- 移除旧的设备专用 flatBase。它直接把设备 alpha 改成配置颜色的 alpha，与普通磁贴只混 RGB 的策略不一致，造成独立透明度。现在统一保留原生抗锯齿、圆角、附加裁剪和最终 `outputCol*shape`，原生 paintAlpha 及 View 动画不改。
- `GradientDrawable` 降级分支只在 draw paint 临时混合 RGB 和去掉描边，保留原生 paint alpha、形状及 Drawable alpha；不写入 GradientState。结束或异常时恢复原色、shader 和描边透明度。
- 磁贴颜色的 active 规则只作用于实际 active 磁贴，不再强行覆盖 SDK inactive body。已准备的其他自定义材质保留优先级。设备图标/前景不使用 body 的 RGB tint。关闭、关闭控制中心分区、安全模式、脱离及模块释放回到原生路径；未知结构直接保留原生绘制。

## 原生后台更新

PJZ110 的只读日志确认 `QsTileAppearance.registerView → View.invalidate` 从 `SysUiTileBg` 调用，触发 `CalledFromWrongThreadException`。注册现在不再无条件请求绘制；tile/device/state/detach 更新按同一 View 合并到主线程，连续 1000 次更新只保留一次待处理任务，执行时读取最新原生状态。后台首次玻璃引擎注册仅标记 native contentDirty，并通过所属 View 的线程安全 `postInvalidate` 请求主线程绘制；主线程保留原有 native Drawable 回调。

同样加固 C17 材质绑定的失效路径，避免该原生后台链的另一个回调写入 View。日志直接证明的是 QsTileAppearance；C17 路径属于同类防护，不能当成已经观察到第二个实机崩溃。

## 接线

1. 原有五种卡片的 `g()`、`onAttachedToWindow()`、`onConfigurationChanged(...)` 完成后调用 `C17_HIGHLIGHTS.refreshDeviceCard(view)`。脱离时调用 `C17_HIGHLIGHTS.detach(view)`。
2. 原有 `ViewBlurProxy.getBlurDrawable(...)` 返回观察继续调用 `C17_HIGHLIGHTS.onNativeBlurResult(proxy, drawable)`。新增的设备识别在方法内部完成。
3. 原有 `BlendDrawable.onDrawContent(Canvas)` 中 `beginRecord` → 自定义磁贴/媒体准备 → `drawContent` → 原生绘制的顺序保持不变。
4. `GradientDrawable.draw(Canvas)` 包入 `drawDeviceBackground(drawable, canvas, target -> chain.proceed(new Object[]{target}))`。该 hook 是平台公共方法，只有精确设备背景通过后才改变 paint。
5. 在现有检查入口加入 `C17DeviceAcrylicCheck` 和 `QsUiDispatchCheck`。

## 验证与边界

已通过 Android 35、Java 8 编译。设备检查 24097 项覆盖普通 inactive 磁贴与五种 body 在浅/深色下的 shader 源、native blend/common/shape 上传和 tint 一致性、原生 alpha、图标/前景排除、错误资源包、材质优先级、千次重复绘制缓存、异常恢复和关闭/安全模式/脱离。线程检查 20 项覆盖 SysUiTileBg 合并派发、最新状态、detach 与重用、未知附着状态及两种引擎的安全失效。现有高光、磁贴颜色和原生玻璃材质检查同时通过。

这些检查验证原生契约及绘制所有权，不替代手机上的视觉检查。PJZ110 当前仅只读获取了已有屏幕/状态与日志，没有切换控制中心或安装候选包，不能宣称本机设备卡片视觉验证已通过。后续需确认设备卡片底色一致、交互透明度与圆角正常、关闭恢复原生。
