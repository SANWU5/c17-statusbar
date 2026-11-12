# 通知背景写入链梳理与仲裁方案

> **实现状态（code91，2026-10-06）**：用户随后给出明确方向（媒体强调色优先级最高且必须始终生效、跟随媒体色第二、自定义底色只保留两个来源、通知类不动、锁屏要能改样式并跟随媒体色、通知也要跟随媒体色、修好 AOD），仲裁方案按这个方向在 code91 实现，**没有等待逐条确认**。与下面草案的差异：
>
> - 颜色顺序是 强调色 > 跟随媒体色 > 自定义底色，通知类只提供材质配方（与草案 6.3 的分层一致，强调色不再受场景开关限制）。
> - 自定义底色的三个场景开关默认：悬浮展开开、通知栏关、锁屏与 AOD 开（草案默认只开悬浮；用户反馈锁屏没有样式，所以锁屏默认开）。AOD 随锁屏，没有单独开关。
> - “唯一的场景状态对象”没有新建类：原生状态事件到达时立刻同步发布给流体云样式、场景材质和强调色三个控制器，统一颜色层不再依赖场景，所以不需要第四份副本。
> - 统一通知颜色（F6）覆盖通知栏、锁屏和 AOD 里所有通知卡片，不再随流体云开关；悬浮展开的流体云卡片仍不盖（草案默认值，用户未提出异议）。
> - 跟随媒体色（F4）仍会染没有音乐的卡片（如秒表）；用户的截图显示正是希望秒表卡片跟随音乐颜色。
> - 新增“通知跟随媒体强调色”，复用 F6 的颜色层。
>
> 细节、检查与未验证的部分见 [code91](LOCAL-CODE91.zh-CN.md)。**以下是 code90 时的只读梳理，保留作历史依据。**

---

# （历史）通知背景写入链梳理与仲裁方案草案

状态：只读梳理，未改任何代码。本文的“成因”均由源码推导，手机当时离线（adb 显示 offline），没有拿到新的日志和 c17.log，**未经实机验证**。

依据版本：code89 源码（与 code88 的注入代码相同）。手机配置取自 `.local-private/device88-readonly/preferences.xml`（13:00 采集，当时手机装的还是 code87），之后可能有变化，需要手机在线时用只读采集脚本重读。

## 1. 先说结论

1. 六个功能不是“互相覆盖同一个对象”这么简单，而是**分属四套互相独立的控制器**，各自保存一份“当前在哪个场景”的状态，更新时机不同，没有统一的仲裁者：
   - `FluidCloudStyle`（场景状态立即更新）；
   - `FluidCloudSceneMaterial`（场景状态要等 `FluidCloudStyle` 在主线程里 post 的刷新才同步，见 `FluidCloudStyle.java:793-800`）；
   - `FluidCloudAccent`（立即更新，`FluidCloudStyle.java:182`）；
   - `C17HighlightRemoval`（自己**没有**场景状态，只能靠 `FluidCloudStyle` 经 styler 写进去的标志间接得知，`C17HighlightRemoval.java:143-164`）。
2. 进出锁屏/AOD 的瞬间，这四处对“现在是不是锁屏、是不是 AOD”的判断会短暂不一致，而且 `C17HighlightRemoval` 的统一颜色层是“有/无”二选一地切换，不是渐变，这与原生的 AOD 交叉淡入淡出叠在一起，是锁屏/AOD 颜色跳变最有嫌疑的结构性原因。
3. 当前配置下，“通知类跟随系统样式”在**悬浮展开**场景被静默停用（见 3.4），而“自定义底色”在**通知栏/锁屏/AOD** 被主动撤销（code87 起），所以这两项在用户看来“没按设计工作”是符合代码的。

## 2. 六个功能分别写什么

“写”指它改动的对象和手段。你的配置值取自上述 13:00 快照。

| # | 功能 | 你的配置 | 写入对象 | 手段 | 门槛 |
|---|------|----------|----------|------|------|
| F1 | 流体云样式·通知类跟随系统样式 | 开（总开关开） | ① 通知栏/锁屏里的流体云卡片（`OplusCustomRow` 的 `NotificationBackgroundView`）；② 悬浮展开卡片（`CardBackgroundView`） | ① `FluidCloudSceneMaterial`：换 BlurProxy 的混合配方（通知栏借同场景普通通知的配方，锁屏用自己的 WALLPAPER_A/B 配方），并清掉 MetaBall 底色和 tempDrawable 底板；② 取 HEADS_UP 配方并改写 `OplusBlurParam` | ② 仅当“自定义底色”和“跟随媒体色”**都关**时才生效（`FluidCloudStyle.java:452`） |
| F2 | 流体云样式·自定义底色 | 开，来源“系统默认”，未明确改过颜色，透明度 57.6% | 悬浮展开卡片 | 改写资源色/参数色的 alpha（或 RGB+alpha） | 只作用悬浮展开；通知栏/锁屏明确撤销（`FluidCloudStyle.java:872-876`） |
| F3 | 媒体强调色（含作用于锁屏，遮罩 45%） | 开，锁屏开 | ① 悬浮里的音乐卡；② 锁屏里的音乐卡 | ① 资源色 RGB 换成取色+遮罩；② 在锁屏用私有 TOP_BOTTOM 渐变副本替换 immersive tempDrawable，改写 MetaBall，替换 AOD 黑色遮罩，并画 `mediaPage` 的强调色 | 通知栏**不写**（`FluidCloudStyle.java:857` 只在 keyguard 且锁屏开关开时取强调色） |
| F4 | 流体云跟随媒体强调色 | 开 | 悬浮展开的**所有**流体云卡片 | 填充色 = 当前媒体色（本卡优先，否则取最近绑定的媒体来源）叠加遮罩，alpha 再乘“填充透明度” | 不受流体云样式总开关控制（`FluidCloudStyle.java:639-640`） |
| F5 | 亚克力底色（浅 `0x1FFFFFFF` / 深 `0x0DFFFFFF`） | 颜色开，但 **C17 高光去除总开关为关**（`c17_highlight_removal_enabled=false`） | 通知/悬浮/控制中心的光学引擎、设备卡片 | `C17AcrylicMaterial` 换 shader 底色 | 受总开关控制，所以当前**不生效**；但它的颜色被 F6 借用 |
| F6 | 统一通知颜色 | 开 | 所有“非悬浮”的通知卡片（通知栏、锁屏、AOD 下的普通通知和流体云卡片） | `drawNotificationBackground` 里 `saveLayer` + `SRC_ATOP` 颜色滤镜，盖在整张卡片原生绘制之上，颜色取 F5 的浅/深色 | **不受**总开关控制（`C17HighlightRemoval.java:931-937` 没有 `enabled` 判断）；当 F1 的 styler 对该卡片写了“不跟随”或“原生强调色”时被关掉 |

要点：F5 在你的配置里实际只是 F6 的调色板；你若期望“亚克力底色”还影响磁贴和控制中心，需要打开总开关。

## 3. 四个场景的写入顺序与优先级（现状）

### 3.1 通知栏（下拉）

- 普通通知：只有 F6（盖一层 12%/5% 白）。
- 流体云卡片：F1（借同场景普通通知的配方）→ F6（盖在上面）。F2/F3/F4 不写。
- 结论：同一屏上普通通知和流体云卡片最终颜色一致，无冲突。**例外**：F1 关闭时，styler 对流体云卡片写“不应用”，`fluidNotifications=false`，F6 随之被关掉（`C17HighlightRemoval.java:932`），于是用户开着“统一通知颜色”，流体云卡片却没有被统一。

### 3.2 锁屏（亮屏）

- 普通通知：只有 F6。
- 流体云卡片，无媒体强调色：F1（锁屏 WALLPAPER 配方，清 MetaBall 底色/底板）→ F6。
- 音乐卡且 F3 锁屏开：F3 渐变副本 + 替换 MetaBall；F1 退到“用原生配置”（`FluidCloudSceneMaterial.java:491`），F6 被关闭（`fluidNativeAccents=true`）。F3 > F1 > F6。
- F2、F4 在这里被撤销或不适用。

### 3.3 AOD（熄屏 / 进出过程）

- 普通通知：只有 F6，且 F6 不看场景，AOD 里照常叠。
- 流体云卡片：`FluidCloudStyle.updateNotification` 的 `apply = (materialAllowed() || holdingAod) && (follow || usesAccent)`（`:869`）。`holdingAod` 只对“F1 单独生效、无强调色、无填充”的卡片为真（`FluidCloudSceneMaterial.java:199-201`）。
  - 满足：保留 F1 配方，F6 保持，AOD 黑色遮罩换成带当前亮屏通知色的私有副本（`aodMask`，`:208-229`）。
  - 不满足（例如音乐卡、或任一填充/强调色参与）：`apply=false`，styler 写 `fluidNotifications=false`，**F6 被整层关掉**并重走一遍光学引擎恢复，AOD 遮罩副本仍用 `accent` 或亮屏通知色。
- 结果：同一时刻，普通通知带 F6，流体云卡片不带，AOD 进出时 F6 在流体云卡片上是硬开关。

### 3.4 悬浮

- 普通悬浮横幅：六项都不写（F6 对 HEADS_UP 只在“fluid”标志下才生效，而 styler 只给流体云卡片写标志）。
- 悬浮展开的流体云卡片（`CardBackgroundView`）：`FluidCloudAccent.resourceColor`（F3）→ `FluidCloudStyle.resourceColor`（F2/F4 的填充，替换 RGB、alpha 相乘一次）→ `blurParams`。
  - F4 对所有流体云卡片生效，所以**没有音乐的秒表卡片，只要后台绑定着一首歌，也会被染成歌曲颜色**。
  - 明确保存过颜色（`customized`）的 F2 会在 F3 之后覆盖 RGB（`FluidCloudStyle.java:458-459` 的注释写明“Explicit colour edits are applied after Accent”），与 `FLUID-CLOUD-STYLE.zh-CN.md` 自定义底色一节里“已开启的独立媒体强调色优先”的旧描述不一致（你当前配置未编辑过颜色，所以这一条暂时没有发生）。
  - F1 在这里因 F2/F4 开着而被静默停用。

## 4. 已确认的结构性冲突（代码层面，附位置）

1. **场景状态四处各存一份，更新时机不同**：`FluidCloudStyle.setKeyguard/onNativeState`（立即）、`FluidCloudAccent.scene`（立即）、`FluidCloudSceneMaterial.policy`（在 `scheduleRefresh` 的 post 里，`FluidCloudStyle.java:800`）、`C17HighlightRemoval`（无状态，靠标志）。进出锁屏/AOD 瞬间会出现至少一帧各写各的。
2. **F6 对流体云卡片是“有/无”切换**：`setFluidNotificationBackground` 会 `restoreEngine → prime → invalidate`（`C17HighlightRemoval.java:156-163`），在 AOD 边界上与原生交叉淡入淡出相叠，不是渐变。
3. **F6 的开关语义不统一**：普通通知常开；流体云卡片在 F1 关闭、或 AOD 里不满足 `holdingAod` 时因 `fluidNotifications=false` 被排除，在锁屏有媒体强调色时则因 `fluidNativeAccents=true` 被排除。
4. **F1 与 F2/F4 在悬浮是互斥而非叠加**，且界面上没有任何提示（`SettingsCatalog` 的说明只提了“媒体色优先”）。
5. **F2 在通知栏/锁屏被硬撤销**，没有任何入口能让它作用于这两个场景。
6. **F4 作用域过宽**：对所有流体云卡片取“最近绑定的媒体色”（`FluidCloudAccent.java:118-128`）。
7. **AOD 遮罩的底色取 `notification_material_background_color_compass`**（亮屏时采样的通知色，`FluidCloudSceneMaterial.java:380`），随系统明暗主题而变；若在浅色主题下被带进 AOD，会得到浅色遮罩。这是有嫌疑但未验证的一点。
8. **F5 总开关与 F6 借色的依赖在界面上看不出来**（`SettingsCatalog.java:680-692`）。

## 5. 你反馈的“锁屏/AOD 卡片和锁屏界面颜色异常”的候选成因

按嫌疑从高到低（均为推测，需要实机日志证实）：

1. 上面第 4 节 1+2：进出 AOD 时多个控制器不同步、F6 硬切换。
2. 上面第 4 节 7：AOD 遮罩使用亮屏通知色。
3. 流体云卡片与普通通知在 AOD 里一个带 F6 一个不带，色差来自 F6。
4. “锁屏界面”整体颜色：这部分我没有读到证据。`LockscreenStatusBlur`（顶部壁纸模糊，已开，半径 40，遮罩色 alpha=0）不在这六项里，但它与原生 scrim/dim 同步，也可能造成整屏色差，需要实机数据区分。

要证实或排除，需要你在“关于 → 诊断日志”开启后复现，我再用只读脚本读取。相关诊断行：`notification material: keyguard=…, doze=…, aodTransition=…, follow=…, accent=…`、`AOD transition: known=…, clear=…, retainedNotification=…`、`native AOD mask: isolated=…, media=…`、`native lockscreen material: no WALLPAPER recipe`。每个背景每类最多记 8–12 条。

## 6. 仲裁方案（按你的回答定稿，待你确认后才实现）

### 6.1 你的决定

- 与媒体色：**媒体色优先**。媒体色（F3 本卡强调色、F4 跟随媒体色）决定 RGB；“填充透明度”始终再乘一次，不因媒体色失效。
- “统一通知颜色”：**都盖，各场景一致**。普通通知和流体云卡片在通知栏、锁屏、AOD 都盖同一层，不再按 F1/F3 的状态开关。

### 6.2 交给我判断的两项，以及理由

**自定义底色作用于哪些场景：悬浮展开 + 通知栏 + 锁屏，三者各一个独立开关；AOD 不单设，跟随锁屏。默认值保持现状（只开悬浮）。**

- 你的反馈前后有两种说法：code87 之前反馈“外观配置误改了通知栏”，现在又反馈“实际行为和设计不一致”。仅凭代码无法确定你想要哪一种，再硬编码一次容易第三次猜错，所以把“通知栏”“锁屏”做成可选，默认不改变你现在的观感。
- AOD 不单设：AOD 是锁屏经原生 `panoramicAodProgress` 连续过渡来的，同一张卡片在亮屏与熄屏之间必须是同一种底色，否则就是你看到的“进出 AOD 跳色”。所以锁屏开关同时管 AOD。
- 这需要新增两个开关（通知栏、锁屏）及配置迁移（schema 14；schema 13 已被 code90 的时钟批次占用），旧配置原样保留。

**与“通知类跟随系统样式”：叠加，不再互斥。**

- 通知类跟随（F1）只负责“材质配方”（模糊、描边、混合、阴影）；自定义底色（F2）只负责“颜色与填充透明度”，写在 F1 配方的混色上。两者职责不重叠，所以可以同时开，不需要谁让位。
- 现状里悬浮展开因 F2/F4 开着而静默停用 F1，是实现时的保守选择（`FluidCloudStyle.java:452`），不是设计，会被取消。

### 6.3 统一后的分层规则

每张流体云卡片在每个场景，从下到上按同一张表取值：

| 层 | 内容 | 通知栏 | 锁屏 | AOD | 悬浮展开 |
|----|------|:------:|:----:|:---:|:--------:|
| L0 材质配方 | F1 通知类跟随 | 开则用（借同场景普通通知） | 开则用（WALLPAPER） | 同锁屏，保持到进度归零 | 开则用（HEADS_UP） |
| L1 基色与填充透明度 | F2 自定义底色 | 仅“通知栏”开关开时 | 仅“锁屏”开关开时 | 同锁屏 | 开则用 |
| L2 媒体色（RGB 优先于 L1） | F3 本卡 / F4 跟随 | 不适用（原生） | F3（作用于锁屏开关开时） | 同锁屏 | F3、F4 |
| L3 统一叠色 | F6（借 F5 颜色） | 盖 | 盖 | 盖 | 不盖（见下） |

- 同一场景里层数越大越靠后写，下层不覆盖上层；“媒体色优先”只发生在 L2 对 L1 的 RGB 上，L1 的填充透明度仍然生效。
- L3 与 L0–L2 不再互相开关：去掉 `fluidNotifications=false` 和 `fluidNativeAccents` 对 F6 的关闭作用，F6 对 `SRC_ATOP` 只给已有像素叠色，不会补出透明区域，叠在媒体渐变上也不会盖掉渐变。
- 悬浮展开的流体云卡片是另一种宿主（`CardBackgroundView`），现状不属于“通知卡片”，所以 F6 不盖。**这一条我没有你的明确答复，默认不盖，需要盖请告诉我。**

### 6.4 场景状态与 AOD 过渡

1. 新增一个唯一的 `NotificationSceneState`（场景：通知栏 / 锁屏 / AOD 过渡中 / AOD / 悬浮），只在原生状态事件里同步更新（`onNativeState`、`onNativeKeyguard`、`setPanoramicAodProgress`）。`FluidCloudStyle`、`FluidCloudSceneMaterial`、`FluidCloudAccent`、`C17HighlightRemoval` 全部只读这一份，删除各自保存的 keyguard/dozing/aodTransition 副本，消除“各控制器同一帧状态不一致”。
2. AOD 过渡里不切换任何层的开关：把现在只对“F1 单独、无强调色、无填充”成立的 `holdingAod` 推广为“保持亮屏组合到进度归零”，对所有组合一致。进度回到 0（稳定）才允许改层。
3. AOD 遮罩副本的颜色不再取“亮屏时采样的通知色”（随明暗主题变化，见第 4 节第 7 点），改为取当前锁屏组合的实际底色（L1/L2 结果，没有则原生遮罩色），避免浅色主题下 AOD 变浅。
4. 每个背景每类最多 8–12 条诊断，新增场景状态变化日志，用于实机核对；稳定帧不轮询、不遍历树。

### 6.5 实施步骤（确认后，编号 code91）

1. 新增 `NotificationSceneState` 并改造四个控制器的场景读取。
2. F6 解除与 F1/F3 的联动，AOD 保持亮屏组合。
3. F2：新增“通知栏”“锁屏”两个开关，F2 在对应场景的 L1 生效；取消“F2/F4 使 F1 停用”。
4. AOD 遮罩副本改色源。
5. schema 14 迁移 + 导入导出 + 恢复默认；设置页文案同步（去掉“不改变通知栏与锁屏卡片”等已不成立的描述，补依赖说明：F5 总开关与 F6 借色）。
6. 更新 `BUILD_TOKEN`，补回归检查（场景状态一致性、F6 不随 F1/F3 开关、AOD 过渡无层切换、叠加不互斥、媒体色优先且透明度生效）。

### 6.6 实机需要确认的（编译与桌面检查替代不了）

- 进出 AOD 连续五次，首尾帧是否闪灰或跳色；
- 浅色/深色主题下 AOD 卡片的亮度；
- 通知栏、锁屏打开新开关后的观感，是否符合你的设计；
- 秒表卡片在后台有音乐时是否仍被染成歌曲色（取决于你对 F4 的取舍，见第 7 节）。

## 7. 仍待你确认

1. 6.2 的两个判断（自定义底色三场景各自开关、AOD 随锁屏；F1 与 F2 叠加）是否同意。
2. F6 不盖悬浮展开的流体云卡片，是否同意。
3. F4“跟随媒体色”目前会把歌曲色染到**没有音乐的卡片**（如秒表）上。要保持，还是改成只染音乐卡片？（只影响悬浮展开。）
