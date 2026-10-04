# 流体云展开面强调色

本功能使用系统已经计算好的媒体强调色，替换流体云展开卡片背景原有的灰黑色混色和渐变。默认关闭。当前支持经过查验的媒体展开卡片；没有可靠原生取色结果的内容保留系统背景。

## 原生实现依据

查验对象是设备的 `SystemUI.apk`、`SystemUIPlugin.apk`，没有将 APK 或反编译输出加入公开仓库。源码中的混淆签名只适用于本次已查验的实现；运行时同时校验类、参数与字段类型，校验失败会保留原生效果。

- 展开卡片由 `seedling.card.ui.view.CardView` 管理，背景是其内部的 `CardBackgroundView`。
- 原生背景初始化器是 `com.heytap.log.nx.obus.a.b(View, ViewRootManager)`。它设置 `BackgroundBlurDrawable`、`OplusBlurParam` 的混色材料，以及原生渐变。
- 媒体区 `shared.template.section.media.s1` 持有原生 `K0.N` 取色状态流，其媒体颜色模型 `shared.data.media.model.a.a` 为 `primaryColor`。原生媒体进度条也读取这一颜色。
- 取色更新回调是原生静态方法 `s1.r(s1)`。初始值在 `s1` 原生构造完成后读取。媒体释放走 `s1.dispose()`。
- 背景透明度与模糊半径仍由原生 `CardBackgroundView.setBlurRadiusFollowAlpha(float)` 控制。

没有对所有黑色进行替换。仅在已验证的背景初始化调用期间，替换同一插件 `Resources` 中的六个背景颜色：`card_background_mix_color`、`card_background_blend_color`、`card_background_blend_color_old`、`card_background_gradient_top_color`、`card_background_gradient_middle_color`、`card_background_gradient_bottom_color`。替换保留每个原始颜色的透明度，完全透明颜色不改。

## 生命周期与开销

配置键是 `fluid_cloud_accent_enabled`。`FluidCloudAccent.configure(Bundle)` 检查安全模式；`release()` 恢复原生材料并移除监听。媒体解绑、展开卡片解绑、关闭功能都不会继续沿用旧歌曲的取色。

原生已完成封面解析，本功能不再次遍历像素、不重复计算模糊、不启动定时轮询。背景只在开启状态或原生强调色变化时重建；同一颜色的重复通知不会重建背景。缓存使用弱引用记录具体卡片与背景所属关系。

## 插件首次加载入口

必须在插件实例构造前安装特定插件 Hook，否则可能漏掉首次背景初始化。已查验的宿主入口为：

1. `PluginInstance$PluginFactory.createClassLoader(): ClassLoader` 的返回值；只处理 `pluginAppInfo.packageName` 为 `com.oplus.systemui.plugins` 的实例。
2. `PluginInstance$Factory.access$getCacheClassLoader(Factory, String): ClassLoader` 的返回值；只处理第二个参数为上述插件包名的调用。

原生 `createPlugin(ProtectedPluginListener)` 先取缓存 ClassLoader，缓存不存在则调用 `createClassLoader()`，之后才执行插件 `Class.forName()` 和实例构造。两个入口分别覆盖首次与缓存加载，不需要 Hook 全局 `ClassLoader.loadClass()`。

## 检查范围

`FluidCloudAccentCheck` 使用最小原生契约替身，检查背景所有权、资源作用域、原始透明度、未变化颜色的重建次数、安全模式、解绑恢复和异常后作用域清理。替身没有实现或分发 OEM 模糊代码。

Java 编译和桌面检查不代表实机视觉验证。仍需在设备上检查媒体展开卡片初次显示、换曲、关闭开关及折叠动画，确认不同固件版本的真实接口仍符合结构校验。
