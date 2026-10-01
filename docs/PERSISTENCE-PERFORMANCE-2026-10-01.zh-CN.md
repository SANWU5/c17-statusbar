# 设置持久化与运行时计算优化

日期：2026-10-01。本文记录 1.15.0 源码、构建与静态审查证据，不代表手机已安装这些新增修改。

## 当前状态与证据边界

- 手机当前为 native27，已安装激活，SHA256 `8bdb62989896aeb6fef38855f5a61d577303065fee215461d9d8f0b7b7cdccc7`，SystemUI PID `6055`→`15947`。用户仍反馈尾角截断；新可见薄窗解算仅在后续本地源码，尚无实机修复通过结论。
- 临时诊断已恢复 `false`，stay-awake 已恢复 `0`。用户已离开，本轮不再使用手机。
- 本轮依据 Java 源码与本机 SystemUI 原生接口证据修正重复工作，没有运行自动测试或新增测试脚本，没有实测功耗、CPU、帧时或电量百分比。不能把静态减少调用写成“实测降耗”。
- 本版包含媒体背景与反色 glow、整套 HyperOS 风格 UI。`1.15.0` 最终构建信息见文末与 VALIDATION；发布和微信通知按实际结果记录。

## 设置持久化：已落盘的机制

### 完整快照与失败保留

`SettingsSnapshot` 按 `StatusBarSettings` 注册表校验完整快照：数值须为有限 Float、颜色为 Integer、alpha/开关为 Boolean、文本为 String。合法的 `false`、`0`、透明色和空文本不会被当作缺失；空、缺项或错误类型的 IPC 结果不会转换成一套默认值覆盖用户配置。

运行时 `StatusBarModule` 使用 `SettingsSnapshot.read(context)`。全部消费者应用成功后才调用 `rememberApplied(context,bundle)`，保留该进程最后成功应用的完整快照；失败不记录新快照。已有进程在读取失败时可返回先前快照；新 SystemUI 进程尚无缓存时返回 `null`，保留其当下状态，等待下一次完整读取。Provider 侧保留 last-good 与持久备份的回退。

根代理已提取 `applyStyleSettings(Bundle)`：应用异常时读取先前 `lastApplied`，仅重放一次完整旧快照，独立处理恢复失败，不递归读取或记住失败的新快照；没有旧快照时准确记录部分应用未完成，不刷默认配置。`matchesApplied` 逐键比较值，只让 ContentObserver 驱动的重复通知跳过完整 configure 与后续 invalidate；初始化、Battery 新组件绑定和用户解锁仍强制应用。此恢复避免把部分失败误报为保留全部状态，仍不承诺所有视图在应用过程具有数据库式原子性。

### DE 存储与旧值迁移

新源码的主设置与恢复快照都使用设备加密存储（DE）：

- 主设置：`/data/user_de/0/dev.puitheme.iosstatusbar/shared_prefs/statusbar_settings.xml`。
- 恢复快照：`/data/user_de/0/dev.puitheme.iosstatusbar/shared_prefs/statusbar_settings_last_good.xml`。

旧 CE 值在用户解锁后，通过公开 `createPackageContext(packageName, 0)` 获取本应用的默认 CE 上下文，并检查 `isDeviceProtectedStorage()` 后读取；不依赖 SDK 未公开的 CE 创建接口。迁移仅补 DE 缺少的键。已经存在的 DE 值优先，不用 CE 同名文件或旧导出的 XML 全量覆盖。恢复备份仅在原始主存储为空时使用，绝不覆盖非空主存储；没有在手机复现 XML 损坏，因此这属于确定的代码保护与持久化改进，不是已证明的数据丢失根因。

### 保留即时 apply，后台确认落盘

`ActivationGuardPreferences` 先执行普通 `apply()`，保持即时内存更新。连续编辑停止约 `500ms` 后，单一后台 worker 用空 editor 的 `commit()` 等待已排队写盘；它不重放较早的编辑值。备份仅在当前值发生变化时更新，限制为最多 `2048` 项及 `512KiB`。`onPause` 请求后台 flush，不在 UI 线程同步等待磁盘，不备份字体二进制。

启动时不再写六个 alias/default 值；旧 tiles-blur 迁移只在目标键缺失时补 `false`。直接数值输入已取消旧的 `180ms` 延后保存，确认后立即接收数值。权限门禁保留：导入 worker 真正提交前若 Activity 已暂停而运行时验证失效，会拒绝该次导入、保留旧配置；不会放宽为后台绕过验证写入。

## 运行时：计算去重已落盘

| 位置 | 可证明的重复工作 | 当前修正与保留行为 |
| --- | --- | --- |
| `NotificationBigClock.onNativeClockUpdated` | 多种原生 `updateClock` 与共享 tick 对同一秒/分钟重复强制格式化并重置测量 | 普通更新复用原有时间桶；明确校时、时区、日期和语言广播走 `onTimeChanged()` 强制刷新，实际跨桶数字仍使用原生动画 |
| `ClockView.refreshText` | 内容未变时仍调用三个文字 setter | 只在内容改变时赋值；配置或显式时间事件仍失效测量缓存 |
| `ClockView.widgetFrame/updateWidgets` | dirty 帧在 preDraw 与 updateWidgets 读取同一回弹两次 | preDraw 读取最终原生快照一次并交给当帧布局；onLayout 的独立更新仍按需读原生值 |
| 原生回弹来源 | 每帧复制 stacks 集合，单 grid 的首尾重复读取 | 弱引用缓存当前 NSSL，每次验证 attach、所属 panel 与注册身份；单 grid 首尾共享同一读值，不另造弹簧 |
| 页脚 | 关闭页脚时仍扫描所有原生 FooterView 并分配坐标数组 | 不具备显示条件时直接跳过碰撞扫描；开启时沿用原生控制优先规则并复用坐标 buffer |
| 脏标记与安全边界 | 重复 panelY 写入、重复 scheduleWidgets 仍请求相同更新；每帧解析固定资源 ID | 相同 panelY 与已 pending 的 widget 更新合并；资源 ID 缓存，配置变化重置，insets 与实际图标高度仍实时读取 |
| 入场模糊 | 同一半径为 header/footer 分别创建相同 RenderEffect | 沿用原有半径变化缓存，同次两容器共享同一不可变 effect，不改变模糊范围或进度 |
| `TextControls.schedule` | 每次重启调度都重新 parse pattern 判断是否含秒 | 含秒信息在配置时计算一次；秒/分刷新间隔与精确边界调度保持 |
| `TextControls` 共享 tick | 文字及样式不变仍额外 invalidate 和写 description | 依赖实际变化的原生 setter 重绘；配置等显式 public refresh 继续重绘，以应用 beforeDraw 中的位置设置 |
| `NotificationClockEdge` | 边界移动时重建两份常量颜色数组，每次 draw 新建 clip Rect | 缓存相同颜色 stop 与每栈临时 Rect；gradient 坐标、原生 clip、像素预算与回退均保留 |
| `StatusBarModule` 配置观察 | 相同 typed 快照的通知仍重建各组件配置并全量 invalidate | Observer 调用 `readStyleSettings(false)`，`matchesApplied` 相同时直接结束；初始化、新组件绑定及解锁仍走强制应用 |
| `StatusBarModule` 应用失败 | 一部分组件已经改变时，旧日志仍笼统声称保留当前状态 | 提取纯应用函数，失败仅一次恢复 `lastApplied`；恢复成功才刷新旧样式，失败新快照不记为成功，无递归重试 |
| `MainActivity.changed` | 一次显示帧内多次保存分别发送 Provider 通知，运行时多次全量应用 | 偏好值仍立即保存，只把通知合并为每帧一次；`onPause` 立即发送尚待通知并请求后台落盘，保留最终值 |
| 设置页旧底栏 | 旧浮动玻璃底栏按页面变化抓取实时背景 | 新 UI 已移除 MainActivity 对旧实时玻璃抓取组件的使用，使用新的主题/卡片导航绘制；不取消通知和媒体的用户玻璃功能 |

本轮运行时计算去重集中于 `StatusBarModule.java`、`NotificationBigClock.java`、`TextControls.java` 与 `NotificationClockEdge.java`；根代理随后补充快照去重、应用恢复及 MainActivity 通知合并和导航替换。保留其他代理的未提交迁移和尾层修改，没有取消用户功能或降低配置的时间刷新频率；通知合并仅覆盖同一显示帧，偏好保存仍即时。

## 已有机制：不是本轮新增的降耗结果

- `TextControls` 的共享 timer 已由 `interactive`、attached/shown 视图和含秒设置门控；息屏移除 tick，亮屏立即 refresh。没有给大钟新增 timer。
- `BatteryControls` 已有息屏取消与可见性调度门控，本轮保留。
- 大钟的常驻 preDraw 仅在 active/eligible 时采样实际原生回弹，没有循环 `postOnAnimation`；原生通知/面板的更新驱动当次绘制，布局仍使用同一 spring owner。
- 固定与收起状态图标仍在真实绘制帧读取原生来源与透明度，反射和几何 buffer 已缓存；本轮没有用低频 timer 代替实时图标变化。
- 设置读取由初始化、解锁与 ContentObserver 事件触发，未发现周期 Provider 轮询。现有网速适配使用原生 NetworkSpeedView；没有发现模块新增的 TrafficStats 采样 timer，本轮不改原生网速采样频率。

## 限制与后续人工核对

以下是待核事项，不表示本轮已经执行，也不要求现在恢复手机调试。

1. 新发布包连续下拉、回收、回弹与切页时，三层尾边的窗口、宽度、圆角、玻璃和 Spotlight 是否一致；分组、展开、清除与横竖屏退出是否恢复。不能以最终静态图证明瞬态通过。
2. 保存设置后正常退后台、关闭 Activity、重开应用及重启 SystemUI，是否保留最新值；合法零值、透明色、空文本和有限超建议范围数值是否保留。旧 CE/空主存储恢复只按实际场景核对，不删除现有用户设置模拟。
3. 导入完成后的通知及界面是否一致；暂停时拒绝导入的门禁提示是否明确。首次新组件绑定、用户解锁与字体访问状态变化仍需配置，不能被“快照相同”误跳过。
4. 普通秒/分钟变化、跨小时原生数字动画、校时、时区与语言事件是否即时更新；关闭日期/页脚后再开启是否正确测量。
5. 固定右组配色交接、收起左侧小钟及通知图标是否保留实时响应且无重复副本；白线仍需完整底部过程核对。
6. 息屏/亮屏与全部相关功能关闭时是否撤销无用调度；实际功耗效果需要后续同条件测量，不能用本轮静态审查代替。

断电或杀进程若早于异步 apply 的落盘与后台确认，最新一批编辑仍可能丢失；恢复备份不能承诺零窗口数据丢失。清除应用数据或卸载会同时删除主设置和私有备份，不提供跨清数据恢复承诺。原生材质与动画接口随 OEM 版本变化，未知接口保持回退，不假设所有机型通用。

## 1.15.0 构建

`1.15.0` / `30` / native28 已完成资源、Java、DEX、对齐与 v2/v3 签名验证，APK 为 `538472` 字节，SHA256 `2d0f958dd88502da6451a7085bc02e2315cb6f6f129d47de86c56ce80dd17828`。构建标识 `c17-runtime-20261001-ui-power-native28-2e93dbc6`，证书与 native27 开发包一致。没有安装或实机功耗测量。

整套设置 UI 已替换为原生控件实现的 HyperOS 设计语言：首页、状态栏、下拉面板、设置四页，功能搜索、分组详情、可折叠参数卡片、明暗主题与独立 HSB/alpha 调色弹窗。未引入 Compose/Miuix 运行库。音乐封面过滤由单一有界后台任务按内容与配置变化执行，绘制保留原生玻璃及高光。具体代码与限制见 README、VALIDATION。

交付入口：[1.15.0](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.15.0)。发布与微信通知以实际服务端和会话记录为准，构建成功不代表交互通过。
