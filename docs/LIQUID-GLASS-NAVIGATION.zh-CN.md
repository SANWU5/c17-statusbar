# 普通底栏与历史方案迁移

本文对应正式版 `1.6.0` / versionCode 51。设置应用已完全移除液态玻璃底栏，改用正式 [Miuix v0.9.4](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4) 的 `NavigationBar` 与 `NavigationBarItem`。

底栏保留“主页、配置、关于”，使用明暗主题对应的实色表面和主题色选中项，贴近系统导航区域，没有悬浮间隙或阴影。没有背景取样、模糊、折射、特殊拖动、弹性透镜或降级捕获。三个 `SettingsGlass*.java` 生产类及 `assets/liquidglass/` 的着色器和许可资产均已移除。

`ModernMainActivity` 使用正文 `ComposeView`、普通导航 `ComposeView`、全屏弹窗 `ComposeView` 三个原生兄弟层。弹窗仍在最高层，绘制、触摸和无障碍顺序一致；详情页及键盘打开时隐藏底栏。

底栏宿主高72dp，不设置额外左右或底部悬浮边距，仅避让系统报告的左右安全区域和底部导航 inset。横屏侧边导航或屏幕缺口出现时，正文与底栏分别应用左右安全 inset；全屏弹窗层仍覆盖最高层。内部使用原版 Miuix 导航项目并关闭重复的系统 inset 填充，底部 inset 只由原生宿主应用一次。页面底部留白用于让最后一项滚动至底栏上方，详情页与键盘打开时隐藏底栏。

配置页四个分类使用官方 `TabRowWithContour`，均分可用宽度。字体根据实际测量宽度适配，用于在窄屏和较大系统字号下完整展示“状态栏、通知栏、控制中心、锁屏”；仅分类标签使用该局部文字样式，其他内容继续尊重系统字号。

历史液态玻璃方案曾提取 QWEA0/Liquid-Glass-Android v2.0.11 的完整 AGSL，提交 `fc6bd34fa3290638c3594cc39386f549d1316ab5`，作者 pandadog，MIT License。历史版权与许可保存在 `docs/third-party/liquidglass-LICENSE-MIT-history.txt`，当前 APK 不打包或运行该方案。此前酷安交互仅作为本地录屏参考，未复制或移植其 APK 代码。

配置页所有实时预览区域已移除；手动参数通过 20 秒试用确认，不能把设置页的示意图当作 SystemUI 实际效果。

私有截图、研究安装包和录屏继续只放在 `.local-private/`，不随源码或发布 APK 分发。本文件记录当前实现与历史来源，验证范围以 [VALIDATION.md](../VALIDATION.md) 为准，不将静态接线审阅或 beta1 通知爱心模式的观察视为本版底栏实机测试。
