# 图标管理 v3：QS stock 身份与真实状态合同

日期：2026-10-18。本次只读核对当前 OEM APK 的 `newTileState()` 与 `handleUpdateState()`，不改手机、不把普通 BooleanState 自动归类为开关，也不把资源数量视为适配数量。APK 摘要和 ClassLoader 边界沿用 [原生宿主合同](ICON-V3-NATIVE-CONTRACT.zh-CN.md)。本表的“可登记”表示已具备身份/状态证据，实际 `supported` 仍由本版运行时绑定、恢复与检查是否完成决定。

## 1. 证据与共同护栏

忽略目录 `build/c17-dev/icon-v3-audit/tile-states/` 保存从已有 `.local-private/device95-readonly/dump/index.json` 精确字节区间提取的 82 个相关类及 `metadata.json`；本文行号均指其中完整类 `.txt`，不是删去日志后的摘要行。Factory 的 47 项 spec/provider 对照见 `factories/spec-candidates.json`。没有重新拉取 APK、运行手机命令或联网安装工具。

角色只能由真实 Tile spec / 已绑定 proxy 的身份取得。标题、内容描述、drawable 名字、View 相邻位置均不作为运行时身份；第三方 `custom(...)` 与额外 state、非零未知三段模式保持原生。宿主原生的 `isTransient`、`disabledByPolicy`、点击、无障碍、View tint / alpha 与可见性不由图标角色更改。

数字 `0 / 1 / 2` 在普通开关上是不可用 / inactive / active；它们本身不能证明“功能关闭/开启”。支付、计算器、锁屏等类虽然也创建 BooleanState，却固定 `value=true,state=1` 并使用 Button 无障碍类型，必须登记 `.default` 操作入口。三段铃声另读真实 `threeStageMode`，不能压成 on/off。

## 2. 原有 13 个目标的第一批确认

下表中，普通开关的规范角色沿用 `.unavailable / .off / .on`；`.off/.on` 表示对应功能的原生 inactive/active 末态。只有 raw state 在 `0/1/2` 内且已核对的宿主形状成立时才适用；瞬态动画仍受原生许可限制。摄像头、麦克风与旋转的 UI 文案必须按下表修正，不能照统一“关闭/开启”误解释。

`Oplus*` 类在 `com.oplus.systemui.qs.tiles`，Android 基类在 `com.android.systemui.qs.tiles`；完整名字均包含于证据文件名。

| 安全 target → 真实 spec | 实际模型与状态语义 | 具体证据类与行号 | 未知边界 |
| --- | --- | --- | --- |
| `tile.wifi → wifi` | BooleanState；1→off，2→on，on 是 Wi-Fi 开关可用/开启，不能说必然已连接网络 | `OplusWifiTile` new 828–850；update 419–574，特别是 430–444 的 UiState/CallbackInfo.enabled、534 state=2、542 value、574 state=1 | 连接中、无互联网、SSID 及信号格另有原生状态；不虚造 connected 角色 |
| `tile.bluetooth → bt` | BooleanState；1→off，2→on；value 来自真实 Bluetooth enabled / 原生 UiState，连接状态是另一个字段 | `OplusBluetoothTile` new 472–494；update 227–277、370–376 | on 不等于耳机已连接；connecting/connected 不能仅由 raw state 区分 |
| `tile.mobile_data → cell` | BooleanState；0→unavailable，1→off/inactive，2→on/active；原生还按 SIM、飞行模式、虚拟通信和定制限制重写 inactive/unavailable | `OplusCellularTile` new 1393–1415；update 1046、1056、1077、1089、1142–1175 | 不以 value 单独覆盖 raw state：特定政策分支可令 value 与 raw active 不同；不读运营商文字识别 |
| `tile.hotspot → hotspot` | BooleanState；0→unavailable，1→off，2→on；启动瞬态也可 active | `OplusHotspotTile` new 876–898；update 514–548、562–595 | 数据节省、LocalHotspot、用户限制可使 unavailable；不能把启动中当最终稳定 on 动画 |
| `tile.airplane → airplane` | BooleanState；1→off，2→on；value 来自 AirplaneModeRepository.isAirplaneMode | `OplusAirplaneModeTile` new 355–377；update 286–321 | 保留原生限制，不把蜂窝信号图案推导成飞行模式 |
| `tile.nfc → nfc` | BooleanState；0→unavailable，1→off，2→on；标准分支 value 来自 adapter enabled，adapter 缺失时 raw0 | `OplusNfcTile` new 412–434；update 142–198；`OplusNfcTileFelicaEx` 209–241 | Felica 为独立委托：锁状态→raw0，RW/P2P 12/13→value=true，其余 false；不能以 value 覆盖锁状态 raw0 |
| `tile.cast → cast` | **本 factory 无 cast 分支，维持不支持** | 47 项 factory 的完整 spec 集合；APK 虽另有 AOSP CastTile，但不能据类存在声称当前 OEM stock 接入 | 外部/备用 factory 与 custom tile 另行审计 |
| `tile.flashlight → flashlight` | BooleanState；0→unavailable，1→off，2→on；value 来源真实手电控制器/状态参数 | `OplusFlashlightTile` new 613–635；update 433–568，特别是 484–491、523–526 与 568 | 相机占用/企业禁止等不可用图保持原生语义；不按颜色猜状态 |
| `tile.rotation → rotation` | BooleanState；1→off=**旋转锁定**，2→on=**自动旋转**；value=`!rotationLocked` | `OplusRotationLockTile` new 294–316；update 236–240、263–266 | 不能把 on 写成锁定开启；方向与姿态仍由原生负责 |
| `tile.dark → darkmode` | BooleanState；1→off=浅色模式，2→on=深色模式；value=Configuration.isNightModeActive | `DarkModeTile` new 482–501；update 422–429、449–454 | 不把节电/主题/壁纸深浅当实际 night mode |
| `tile.location → location` | BooleanState；0→unavailable，1→off，2→on；正常 value 来自 isLocationEnabled | `OplusLocationTile` new 380–402；update 261–281（隐身模式不可用）、293–311、343–348 | 隐身/政策状态不因用户图源改变；on 不等于某 App 正在定位 |
| `tile.camera → cameratoggle` | 继承 BooleanState；0→unavailable，1→off=**阻止相机访问**，2→on=**允许相机访问**；value=`!isSensorBlocked` | `SensorPrivacyToggleTile` new 366–379；update 251–252、267–294；`OplusCameraToggleTile` 229 调用此 super 后不反转 | “隐私屏蔽开启”对应 off，不是 on；硬件/隐身不可用另保留 raw0 |
| `tile.microphone → mictoggle` | 继承 BooleanState；0→unavailable，1→off=**阻止麦克风访问**，2→on=**允许麦克风访问**；value=`!isSensorBlocked` | 同 `SensorPrivacyToggleTile`；`OplusMicrophoneToggleTile` 229 调用此 super 后不反转 | 与相机同理；on 不等于正在录音 |

因此本批可接的 stock target 为 12 个，cast 继续未支持。PUI 来源资源或原创图案的 on/off 必须遵从这里的功能语义；资源作者写了 on/off 并不能改变原生 state 的含义。

## 3. 其余 35 项 factory spec 的状态分类

以下是新增 target 的**安全命名建议**，由后端集中冻结，不在本文直接改 Registry 或宣布支持。除明确写出的 0 分支外，不替类凭空补“不可用”图形；若运行时出现本表没有的 raw state，继续原生。所有新目标均先 `dynamicSupported=false`；三段、动作和录屏不能自动获得普通 on/off Lottie 转场。

### 3.1 已证实为二值功能 / 状态的 18 项

| 建议 target → spec | newTileState 与 update 的具体证据 | 原生状态 → 建议规范状态 / 文案 | 必须保留的边界 |
| --- | --- | --- | --- |
| `tile.auto_brightness → autobrightness` | `AutoBrightnessTile` new 244–260；update 170–183 | 1→off 手动亮度；2→on 自动亮度 | 只读真实 `AutoBrightnessController.mAutomatic`，不由亮度值推测 |
| `tile.auto_color_temperature → autoscreentemperature` | `AutoScreenTemperatureTile` new 419–439；update 308–351 | 1→off；2→on；value 为真实 setting==1 | setting=-1 会触发原生内部 TileService 路径；不伪造 unavailable 或更改该调用 |
| `tile.data_saver → saver` | `com.android.systemui.qs.tiles.DataSaverTile` new 225–241；update 184–199 | 1→off；2→on 流量节省开启 | value 来自真实 getRestrictBackground / 原生参数，不等于禁用所有网络 |
| `tile.dolby → dobly` | `DolbyTile` new 351–367；update 269–302 | 1→off；2→on；真实 secure setting==1 | 保留 OEM 原始 spec 拼写 `dobly`；setting=-1 的内部服务路径不由图标修改 |
| `tile.breathing_light → breathinglight` | `BreathingLightTile` new 922–938；update 697–719 | 1→off；2→on 呼吸灯总开关 | 真实 `customize_breath_light_master_switch==1`；不声称此刻灯正在发光 |
| `tile.work → work` | `OplusWorkModeTile` 未覆写更新，继承 Android `WorkModeTile`；后者 new 199–215；update 130–165 | 1→off 工作资料暂停；2→on 工作资料开启 | 无工作资料由原生 isAvailable/移除 Tile 负责，不把按钮存在等同已建工作资料 |
| `tile.osie → osie` | `OsieTile` new 366–382；update 257–294 | 1→off；2→on OSIE 图像增强 | value=OsieUtil.isOsieSwitch；设备功能差异/资源保持原生上下文 |
| `tile.iris → lris` | `Iris5Tile` new 551–567；update 381–383、405–479 | 0→unavailable 温控等受限；1→off；2→on 视频增强 | raw spec 首字母为小写 L：`lris`；温控分支会覆盖 raw0，即使 value=true，不能只用 value |
| `tile.vpn → vpn` | `FlavorTwoVPNTile` new 237–253；update 183–205 | 1→off 未连接；2→on VPN 已启用 | 这是连接状态入口，不意味着点击即可无交互切换任意 VPN；不把服务品牌 artwork 擅改为其它角色 |
| `tile.otg → otg` | `FlavorOneOtgTile` new 532–548；update 400–412 | 1→off；2→on OTG 开关 | value=getOtgEnable；不等于外接 USB 设备当前已连接 |
| `tile.dnd → dnd` | `OplusDndTile` new 797–819；update 515–538 | 1→off；2→on 勿扰模式（zen!=0） | zen1/2/3 有原生不同资源/说明，本轮只确认总启用状态；不造三个普通开关 |
| `tile.no_disturb → nodisturb` | `NoDisturbTile` new 333–349；update 247–279；`NoDisturbTile$setting$1` 24 | 1→off；2→on，setting `qs_no_disturb==1` | 与 `dnd` 是不同 spec/observer，不合并成同一个运行时身份；本轮名称沿原生 no-disturb 语义 |
| `tile.battery_saver → battery` | `OplusBatterySaverTile` new 258–280；update 170–195 | 1→off；2→on 省电模式 | mPowerSave 为真才 active；不把电池电量低/充电图当此开关 |
| `tile.phone_anti_theft → phoneantitheft` | `PhoneAntiTheftTile` new 1178–1192；update 890–912 | 1→off；2→on 防盗报警开关（alarmValue==1） | 不是状态栏普通报警/闹钟；不依据标题/文字合并 |
| `tile.super_power_saver → superpowersave` | `SuperPowerSaveModeTile` new 484–500；update 374–417 | 0→unavailable；1→off；2→on 超级省电模式 | relatedTileAvailable / 高温安全状态可覆盖 raw0；不以 value 绕过 |
| `tile.vibration → vibration` | `VibrationModeTile` new 480–496；update 369–413 | 1→off；2→on，当前铃声模式对应的振动开关 | value 根据 AudioManager 模式选择 vibrateWhenSilent 或 vibrateWhenRinging observer；**不等于**三段铃声的 vibrate 状态 |
| `tile.small_screen → smallscreen` | `SmallScreenTile` new 440–453；update 358–403 | 0→unavailable；1→off；2→on 单手小屏已启用 | raw2来自 OplusScreenDragUtil.isDragState；锁屏、横屏、分屏、小窗等会限制；不是“任意应用悬浮小窗”开关 |
| `tile.envelope → envelope` | `EnvelopeTile` new 420–438；update 319–368 | 1→off；2→on 红包助手 | value=真实 EnvelopeHelper.getEnvelopeAssistantStatus()==1；helper 缺失会隐藏，不接管其可见性 |

二值状态不保证每个功能都在作者手机上可用；本地只证明当前 APK 对应更新路径的含义。尤其 `vpn` 表达启用/连接状态，`vibration` 是当前模式下的振动设置，不能据此承诺普通一键开关或统一转场。

### 3.2 固定默认操作的 13 项

下列前 10 项虽创建 BooleanState，却固定 `state=1`（多数还固定 `value=true`），按钮不是关闭态。只建议登记 `.default`，运行时只接受已核对的 raw1；出现新状态时保留原生。

| 建议 target → spec | 实际类：new / update 行号 | 安全角色 |
| --- | --- | --- |
| `tile.alipay → alipay` | `AliPayTile` 277–293 / 208–222 | `.default` 支付宝付款 |
| `tile.ali_scan → aliscan` | `AliScanTile` 260–276 / 189–203 | `.default` 支付宝扫一扫 |
| `tile.wechat_pay → weixinpay` | `com.oplusos.systemui.qs.tiles.WeChatPayTile` 268–284 / 181–195 | `.default` 微信付款 |
| `tile.wechat_scan → weixinscan` | `WeChatScanTile` 269–285 / 206–220 | `.default` 微信扫一扫 |
| `tile.calculator → calculator` | `CalculatorTile` 329–345 / 266–280 | `.default` 计算器 |
| `tile.clean → clear` | `CleanTile` 340–356 / 190–204 | `.default` 清理 |
| `tile.google_now → googlenow` | `GoogleNowTile` 237–253 / 177–183 | `.default` 语音/助手入口；不按标题识别品牌 |
| `tile.lock_screen → lockscreen` | `LockTile` 231–247 / 189–195 | `.default` 锁屏 |
| `tile.input_method → inputmethod` | `InputMethodTile` 280–296 / 221–222 | `.default` 输入法选择 |
| `tile.shutdown → shutdown` | `OplusShutDownTile` 286–304 / 228–242 | `.default` 电源操作入口 |
| `tile.qr_scanner → qr_code_scanner` | `OplusQRCodeScannerTile` 171–183 创建普通 State / 131–132 固定 raw1 | `.default` 系统扫码；不与两个品牌扫码合并 |
| `tile.multi_system → multisystem` | `MultiSystemTile` 322–338 创建 BooleanState / 217–231 | raw1→`.default` 系统切换入口；raw0为锁屏不可用，保持原生 |
| `tile.screenshot → screenshot` | `ScreenshotTile` 552–568 创建 BooleanState / 488–520 | raw1→`.default` 截屏；raw0为定制禁止录屏/截屏的不可用图，保持原生 |

### 3.3 三段铃声、录屏和两类复杂入口的 4 项

| 建议 target → spec | 真实模型与具体证据 | 可登记的状态合同 | 首轮运行时边界 |
| --- | --- | --- | --- |
| `tile.ringer_mode → ringermode` | `ThreeStageRingerModeTile` new 435–457 为 BooleanState；update 309 从 getRingerModeInternal 读取模式，336固定 value=false，379固定 rawstate=2，407直接写 threeStageMode；341–356 的真实 lottiePrefix 显式标注 normal/vibrate/silent | 必须读 threeStageMode：0→`.silent` 静音，1→`.vibrate` 振动，2→`.ring` 响铃；未知保持原生 | 单看 raw2 或 BooleanState 会把三态误归成 on；单守 threeStageMode!=0 也挡不住 silent0。未有明确三态 adapter 前维持不支持，不套普通 toggle Lottie |
| `tile.screen_record → screenrecord` | `ScreenRecorderTile` new 1081–1099 为 BooleanState；848固定 value=true；864–869禁用 raw0；876–997按真实 record_tile_service_state 及录屏进程存活核对 raw1/2 | raw0→`.unavailable`；raw1→`.idle` 可开始；raw2→`.recording` 录屏活动 | value不表示录屏已开；录屏服务活动不证明每一帧都在采集（暂停/启动仍归原生控制），不把 raw1 命名“关闭了录屏功能” |
| `tile.controls → controls` | `OplusDeviceControlsTile` new 179–193 为普通 State；update 84–148按 controls app / 可见性 / 已选设备结构设置 raw0/1/2 | 操作入口；仅建议 `.default`，不提供 off/on | raw0无可用服务；raw1还区分锁屏/空结构；raw2有可用结构。首轮维持未支持，除非 adapter 明确保留锁/禁用与结构语义，不能拿 BooleanState 策略处理 |
| `tile.wallet → wallet` | Android `QuickAccessWalletTile` new 391–403 为普通 State；update 269–314按服务/功能、卡片、是否解锁设置 raw0/1/2，并带 sideViewCustomDrawable | 操作入口；仅建议 `.default`，不提供 off/on | 原生可能带当前支付卡片、服务图与自有 artwork。首轮维持未支持，不能把所有 raw2 当“钱包开关开启”或覆盖用户/品牌卡片 |

本表共覆盖 factory 的 47 项：§2 中 12 个实际 spec + §3.1 的 18 项 + §3.2 的 13 项 + §3.3 的 4 项。`tile.cast` 仅是原登记表中的额外未支持候选，不计入 47。
