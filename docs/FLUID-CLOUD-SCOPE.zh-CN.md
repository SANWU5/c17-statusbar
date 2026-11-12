# 流体云样式的适配范围

2026-10-18，code185。适配范围位于“流体云样式”页，按系统的业务标识选择本页样式适用于哪些卡片。13 类开关默认全部开启，旧配置继续保持原有行为。

## 作用与恢复

关闭某一类后，C17 停用“流体云样式”页对这类卡片的样式调整，并撤销本页已写入的样式，让原生材质重新接管。它不禁止系统发送、展示或更新该业务，也不关闭卡片原有按钮、点击操作和动画。

这里的业务范围与“悬浮展开”“锁屏与 AOD”等场景选择共同决定本页样式是否应用。独立的媒体强调色、C17 高光去除、锁屏材质等功能仍遵循各自开关；关闭本页某个业务开关，不代表强制覆盖这些独立功能的选择。

## 已核实的业务映射

以下数字均为 OEM 静态代码或随系统 APK 打包的配置中明确声明的 `serviceId`，由 `FluidCloudScope.SERVICES` 精确匹配。未使用订单文字、取件码内容、通知标题或正文推测业务。

| 页面类别 | 已核实的 `serviceId` | 对应业务与边界 |
| --- | --- | --- |
| 音乐媒体 | `268452006`、`268451900`、`268451910`、`268451911` | SystemUI 媒体控制与音频播控；另识别已绑定的原生媒体视图。 |
| 打车 | `536875051`、`536877203`、`536877202` | 支付宝网约车、高德打车、滴滴打车履约卡。 |
| 外卖 | `536875052`、`536877201`、`536878642`、`536878973` | 支付宝外卖、饿了么、淘宝闪购及更名后的饿了么卡片。 |
| 取件码与快递 | `536878265`、`536878933`、`268452041` | 两版快递提醒服务、小布记忆快递取件码；快递提醒不保证每张卡都有取件码。 |
| 取餐码与到店自提 | `268451931`、`536875050` | AI 取单码提醒、支付宝到店自提；到店自提不限定餐饮订单。 |
| 倒计时 | `268451854`、`268451943` | 时钟计时器及其对应渠道版本。游戏预约倒计时不据此归入时钟计时器。 |
| 闹钟 | `268451917`、`268451944`、`268451937`、`268451953` | 闹钟响铃提醒、秒抢闹钟及对应渠道版本。 |
| 导航 | `268452031`、`536879312`、`536879377`、`536879383` | 离车步行导航、百度/高德/腾讯红绿灯提醒。 |
| 交通出行 | `536871053`、`536871054`、`536871055`、`536871056`、`536879177` | 高铁行程、航班行程与智慧地铁。 |
| 低电量与充电 | `268451902`、`268451924` | 省电提醒 `POWER_SAVE`、充电卡片 `CHARGE`。 |
| 耳机 | `268451845` | MyDevices 的耳机连接 `HEADSET_CONNECTION`。 |
| 情景建议 | `268451855`、`536874238`、`536879148`、`536874236`、`536879151`、`536874237` | 旧版及新版开启 WLAN、观影静音提醒、关闭手电筒建议。 |
| 其他与未识别 | 无穷举表 | 未列入以上业务、缺少业务标识或当前 ROM 无法安全识别的流体云，包括当前未单列的秒表、录音、运动健康等。默认开启；关闭此项会停用本页对这些卡片的调整。 |

同一个发布包可以同时提供打车、外卖、快递、交通出行和情景建议等业务。因此不把整个美团、支付宝、`com.coloros.sceneservice` 或 UMS 包归入某一类。包名只作为业务身份元数据，不替代精确业务标识。

`default_com.android.systemui_268452006` 是插件配置明确声明的媒体统一标识，单独按该完整字符串识别。普通 `default_{package}_{number}` 的末段可能只是通知编号，不能通用剥离后当作 `serviceId`；`laid_...` 也不按任意后缀猜测。当前没有把布局 `templateId` 当作业务分类依据。

## 静态证据来源

映射调研阶段只读取系统 APK 和其中的代码、声明及打包资源，没有读取应用私有数据库。后续经作者许可安装并验证了倒计时、秒表范围与原生渐变对照，测试设置逐字节还原，详见 [code185记录](LOCAL-CODE185.zh-CN.md)；这些实测不代表下表所有类别均已触发验收。

| 来源 | 核实内容 |
| --- | --- |
| `Clock.apk`，`com.coloros.alarmclock` 17.6.94 | `assets/ClockTimer.upk`、`ClockTimer15.upk`、`ClockSnooze.upk`、`ClockGarbAlarm.upk` 的 `config.json` 将业务 ID 与 `TIMER`、`ALARM`、`GARBALARM` action 绑定；时钟 Helper 的静态字节码及 UMS 优先级表补齐对应渠道 ID。 |
| `UMS.apk`，`com.oplus.pantanal.ums` 17.20.25 | `assets/serviceId/service_info_config_v2.json` 版本 `20097`、`assets/host_less_card_config.json` 版本 `202512011421`、`assets/serviceGroupPriority` 版本 `10048`：提供服务 ID、发布包、业务说明及业务分组。 |
| `OplusExSystemService.apk`，`com.oplus.exsystemservice` 1.4.6 | `assets/AIFluidCloud.upk/config.json` 声明 `268451931` 与 `SMART_MEAL_PICKUP_CODE`；固定功能说明明确为餐饮小程序/APP 的取单码提醒。 |
| `SystemUI.apk`、`SystemUIPlugin.apk` | `MediaLiveAlertController.MEDIA_SERVICE_ID` 为 `268452006`；插件 `oplus_default_service_id_mapping` 明确其完整统一标识。插件原始 Live Alert model 的 `q()` 是业务 ID，`e()` 是数据源业务包；卡片 `ViewItem.b` 与通知 `CustomRowEntry.getServiceId()` 提供当前绑定业务。 |
| `Battery.apk` | `assets/powersave.upk` 为 `268451902`/`POWER_SAVE`；`ChargeSeedling.upk` 为 `268451924`/`CHARGE`。`PowerSaveSeedlingCardProvider`、`ChargeSeedlingCardProvider` 与发送 Seedling 的静态代码确认其卡片通路。 |
| `MyDevices.apk` | `assets/DeviceSeedlingCard.upk`、`DeviceSeedling2Card.upk` 声明 `268451845`/`HEADSET_CONNECTION`。 |
| `SceneService.apk` | `OpenWLAN.upk` 声明 `536874238`/`FLUID_WLAN_ON`，`TurnOffFlashlight.upk`、`capsule_movie_reminder.upk` 分别核实关闭手电筒和观影静音；三家地图 UPK 核实红绿灯提醒。UMS 静态场景配置还记录到家/到公司场景关联开启 WLAN 建议。 |

省电、充电、耳机与开启 WLAN 均有明确的 UPK host/provider，属于 Seedling 卡片业务。SystemUI 的 `LowBatteryUiController` 所显示的独立 `LowBatteryDialog` 是系统警告窗口，不是本页流体云样式的对象。UMS 注册表中的 `268454150` 电池电量组件、`268454151` 耳机电量组件属于 `serviceCategory=4` 小组件，也未混入流体云业务表。卖场演示服务未加入正式业务分类。

## 识别与验证边界

读取范围限于业务 ID、业务包和已有原生媒体绑定。诊断只记录这些身份字段、分类与开关结果，不读取或记录通知正文、订单、验证码、取件码、取餐码或卡片原始内容。

上述业务映射已经由本机 OEM APK 静态证据核实；这不等于各业务的实际外观、切换恢复、锁屏/AOD 或所有 ROM 版本都已通过实机验收。系统或服务更新后，新增或改变的业务标识会先进入“其他与未识别”，保留默认开启行为，待有证据后再补充精确映射。

本地审计材料位于被 Git 忽略的 `build/c17-dev/scope185-audit/`。APK、个人资料、原始手机数据及审计缓存均不加入 Git；仓库只保存实现、验证代码和本说明。
