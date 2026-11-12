# 锁屏顶部模糊：只作用壁纸

## code74 当前实现：连续模糊到原壁纸

code72 的分段 surface 产生横条，code73 的单个均匀模糊区域缺少由虚到实的过渡，均已替换。当前 `WallpaperBlurLayer` 使用真实 Android 17 的 `ScreenCaptureInternal.captureLayers` 获取壁纸子树的区域硬件 buffer，并强制 `childrenOnly`、当前 SystemUI UID、排除整个模块自有子树。没有调用 `captureDisplay`、采集前景、CPU 读取像素或保存截图。

`WallpaperBlurRenderer` 用系统 RenderEffect 在只含壁纸的 RenderNode 上生成模糊，再通过连续 LinearGradient/DST_IN alpha 混合回原壁纸。渐变采样点在单个 shader 中连续插值，不是相邻 crop 横条，也不声称改变每个像素的原生 blur radius。颜色遮罩同样平滑渐隐。输出是单个 RGBA8888、非 opaque buffer，低于全部锁屏/AOD 前景窗口。

缓存只在壁纸原生提交新帧、设置或尺寸变动时更新；锁屏和 scrim 进度只更新合成器 alpha，不逐帧重新采样和计算图像模糊。冻结前同步隐藏自有层，采集显式排除自有层，避免重复模糊。异常资源按所有权释放；硬件源未准备好等待下一次原生壁纸帧，不随动画不断重试。

只读核对的原生 API 包括 Surface/RenderNode/RenderEffect、ScreenCaptureInternal 的真实构造参数和返回值，以及 CanvasEngine.drawFrameOnCanvas(Bitmap) 提交路径。保留默认显示屏、静态 CanvasEngine、一比一全屏、零 insets 的映射限制；未知缩放和外部动态壁纸保留原生画面，不回退到前景窗口模糊。原生 dim、scrim 与 doze/dismiss 同步规则仍保留。

当前桌面契约检查与待实机验证见 [code74](LOCAL-CODE74.zh-CN.md)。以下为 code72–73 历史方案，涉及“无 bitmap/取样”或均匀模糊的表述不适用于当前 GPU 缓存实现。

---

## code72 层级修复

code71 在 Keyguard/AOD ViewRoot 中绘制 BackgroundBlurDrawable。先于子 View 绘制只控制该窗口内部的顺序，无法限制 SurfaceFlinger 的窗口级背景采样，因此仍可能模糊其他窗口的前景。code72 完全移除这些窗口中的模糊和颜色遮罩绘制。

真实 framework DEX 的壁纸层级为 `Engine.mSurfaceControl → mTransformSurfaceControl → mBbqSurfaceControl`。新增 `WallpaperBlurLayer` 只在原生 ImageWallpaper CanvasEngine 的壁纸窗口下创建自有子树，与 transform 并列。正常时以 transform 为相对层级锚点；冻结快照时以 snapshot 为锚点，均为 +1。整个效果仍低于所有锁屏/AOD 前景窗口。

透明 effect layer 使用标准 `setBackgroundBlurRadius` 和 crop；其透明来自原生 NO_COLOR_FILL。颜色遮罩使用独立 color layer，避免把遮罩 alpha 当作模糊强度。上部及渐变过渡复用现有最多 9 段的几何。没有截图取色、壁纸 bitmap、CPU 模糊、定时刷新或自有动画；状态、参数和几何未变化时不提交重复事务。

只修改和释放自有句柄，不写原生壁纸的 alpha、裁剪、缩放、背景模糊或布局。原生 dim、前景黑色 scrim 和锁屏组件继续由系统绘制。

## code73 连续模糊修复

code72 的过渡区拆成多个相邻 effect/mask surface，ColorOS 合成时可见水平接缝。code73 改为一个连续的 native background-blur region 加一个均匀颜色遮罩，使用和通知卡片同类的 SurfaceFlinger 后景模糊原语；不再创建分段横条，也不逐帧做 CPU/GPU 图片模糊。范围参数控制主要高度与下沿延展高度，延展区域也属于同一块完整模糊面。

层级、锁屏前景隔离、静态壁纸与一比一映射限制、原生快照前后处理均沿用 code72。code73 尚未在手机合成器上验收：需要确认没有条纹、前景组件清晰，以及 AOD/唤醒/解锁期间不漏亮。

## 生命周期与原生冻结

观察 Engine 内部 `updateSurface`、初始化、可见性、ambient、唤醒、freeze 与快照清理事件。销毁/解绑前释放自有层。不能只 Hook 基类 `onSurfaceCreated/Changed/Destroyed`：CanvasEngine 会覆盖它们且不调用 super。

原生 `showScreenshotOfWallpaper` 会捕获整个壁纸子树。调用前只同步隐藏自有效果，避免模糊和遮罩被烘入后重复应用；结束后重新选择原生 transform/snapshot 锚点。采集失败保留原生画面；接口或提交异常则停止该 Engine 的效果，不改写系统窗口。模块没有新增截图或 freeze 请求。

原生冻结可能留下 `reportedVisible=false` 而 `mVisible=true`，有效 snapshot 使用原生请求可见性补充判断。关闭开关、安全模式和模块退休释放自有层，不复活过期注册。

## 熄屏同步

继续观察已注册的 behind/front ScrimDrawable 实际 opacity，以及原生连续 doze、dismiss 进度。未知或脱离层级的 scrim 不授权亮色 AOD。后台 scrim 回调合并到主线程，前景根节点的既有 draw 只观察实际值，既不画背景也不额外 invalidate。

## 支持边界

本轮手机只读数据确认静态 ImageWallpaper、默认显示屏、壁纸 window/requested 尺寸一致、scale=1、offset=0，属于已核对路径。仅锁屏 bit 生效；系统专用壁纸与 preview 不应用效果。

暂只接受默认显示屏、一比一全屏 frame、零 surfaceInsets、没有 WMS wallpaper zoom 的已确认映射。图片 buffer 不当作显示屏宽高。旋转中尺寸不一致时临时隐藏，原生事件恢复后重新生效；未知缩放、外部动态壁纸或接口缺失保留系统画面，不退回前景窗口模糊。

## 验证

最终 Release classes 的 `WallpaperBlurLayerCheck` 2761 项通过，覆盖父层、相对锚点、同步冻结、可见性、mask/blur 独立、关闭与回退、身份映射恢复、借用句柄零写入、重复事件零事务和释放。既有几何检查 6404 项通过。它们验证代码和原生接口合同，不能替代设备合成器视觉、AOD 或性能验收。

本轮按用户要求只读取手机，没有安装、切页、锁屏或重启。需用户安装 code72 后确认：只模糊顶部壁纸，时间/通知/状态图标仍清晰，熄屏不漏亮，唤醒过渡连续。原始 dump、APK 和私人画面留在忽略目录，不公开。
