# C17 插件 SDK · API 1/2

新开发基线为 **code261 统一详情页**，见 [页面规范](DETAIL-UI.zh-CN.md) 与 [API 2 模板](../templates/standard-runtime/README.md)。`ui.management-groups` 使管理页采用与设置页相同的分类导航；作用域申请/重启、启用总开关及公共管理操作由宿主提供，不是需要插件注册的动作。


宿主起始版本：Status C17 2.0 / code192。插件自身的 `version` 和 `versionCode` 独立递增。API 1 是隔离的声明式规则；API 2 是完整运行扩展，需要支持 API 2 的宿主。

**本文的 `action/status` 表单及 `C17.onAction/publishStatus` 最低需要 2.1.0_beta.008 / code206。code205 虽支持 API 2，也不能导入这两类控件。** API 编号不能代替宿主能力检查。code210 起的可选详情、分组、能力探测和原生设置契约见下文；不使用新字段的旧 API 1/2 插件继续兼容。本文不表示 code210 已发布、安装或通过 ROM 验收。

公开仓库目录接入用于 2.0.1 / code193。仓库 `templates/` 仅供开发参考，不列入公开可安装目录；当前目录为空。格式、命名空间、版权来源、适配和分发规则见根 [SPEC.md](../SPEC.md)，使用边界见 [免责声明](../DISCLAIMER.zh-CN.md)。

## 文件与入口

可导入单个 UTF-8 `.js`，或根目录含 `plugin.json` 与其 `entry` 指向脚本的 ZIP。ZIP 中不应再套一层目录。`plugin.json` 只有元数据与 `entry`，不能包含 `ui/settings/hooks`；这些由脚本提供。

API 1 只执行 `entry` 这一份脚本。包里可以附带其他 JS 源码供阅读，但不会自动执行它们；没有 `require`、`import`、模块加载器、文件读取或相对路径资源加载桥。需要共用代码时，请先在开发端合并到入口脚本，再打包。

API 2 的 `entry` 仍是导入时用于身份、表单和预览的 `main.js`。它必须提供 `runtime` 能力，并在 manifest 和元数据中声明相同的 `runtime.entry`（ZIP 内另一份 `.js`）或 `runtime.source`（非空运行脚本）。运行入口只会在用户主动启用后，由宿主装入 SystemUI。ZIP 可附带 DEX、图片、字体、数据文件和其他二进制资源；API 2 不使用 API 1 的 32 项文件、1MiB 总容量、256KiB 单项文件/脚本配额，`entry` 路径可至 4096 字符。运行入口不套用 API 1 执行预算；导入元数据仍在隔离进程中编译并有计算和时间预算，不能在此入口执行插件主体。仍需遵守有效 ZIP 路径和 ROM/Android 文件系统能力。

```json
{
  "id": "io.github.yourname.systemui-extension",
  "name": "我的 SystemUI 扩展",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 2,
  "author": "yourname",
  "capabilities": ["runtime"],
  "minimumHost": 210,
  "features": ["runtime.java", "scope.systemui"],
  "usage": "在 SystemUI 调整声明的功能；先启用插件，再修改设置。停用时恢复插件拥有的界面。",
  "scopes": ["com.android.systemui"],
  "entry": "main.js",
  "runtime": {"entry": "runtime.js"}
}
```

```json
{
  "id": "io.github.yourname.clock-seconds",
  "name": "我的时钟插件",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "description": "独立控制秒数和字号比例，不改写原设置。",
  "author": "yourname",
  "capabilities": ["settings"],
  "entry": "main.js"
}
```

脚本必须且只能调用一次 `C17.define(普通对象)`。其结果只允许 JSON 数据，不接受函数、`undefined`、循环引用或 Java 对象。未知字段会被拒绝。

标准构建器采用开发模板的写法：`var plugin = {严格 JSON 元数据};`，之后以 `plugin.ui = [严格 JSON 控件声明];` 声明完整表单，再赋值 `plugin.settings` 与 `plugin.hooks`，最后 `C17.define(plugin)`。构建器读取元数据头与 manifest 比对，并静态检查唯一的表单声明、类型、默认值和至少一个可编辑项；它不执行脚本，因此不能证明后续脚本没有改写表单或每个参数都关联功能，宿主仍会对实际输出强制检查。应用也接受直接 `C17.define({...})` 的独立脚本；一般动态脚本可直接交宿主验证导入，标准构建器不负责解析所有 JavaScript 语法。

manifest 中声明的 `id/name/version/versionCode/apiVersion/description/author/capabilities` 必须与脚本一致，capabilities 的顺序也必须一致；新 `minimumHost/features/usage/scopes/nativeSettings/configurationAliases` 也必须在 manifest 与实际描述之间一致。宿主允许旧可选字段按既有规则省略；标准构建器要求 manifest 与源码元数据头完全一致，包括可选字段是否出现。元数据不能随表单状态变化。

从在线仓库下载还有独立的目录一致性检查：目录与脚本的 `id/name/version/versionCode/apiVersion` 逐项相同，目录非空白作者与脚本作者相同，能力按集合完全相同；`description` 仅用于展示，不参与这一层的包一致性比较。**目录缺少 capabilities 按空数组处理**；若脚本声明 settings 或 systemui，就必须在目录如实列出，否则下载内容即使通过 size/SHA-256 也会取消导入。这与 ZIP manifest 的可选字段省略规则是不同层次的检查。

## 详情与宿主能力声明

code210 起，插件详情按三段展示：①使用说明与概况，包含声明作用域及实际可核实的框架勾选状态；②功能与设置，保留全部业务开关和入口；③插件管理，包含删除、更新、恢复默认及其他管理设置。声明、框架勾选、插件启用、运行就绪是不同信息。勾选读取失败或框架不提供结果时显示“未知”，不能从已启用推断“已勾选”。

descriptor 与 ZIP manifest 可同时声明以下字段，旧插件无需补填：

| 字段 | 类型与含义 |
|---|---|
| `minimumHost` | 整数 1–2147483647，所需 App `versionCode`，不是 API 编号或插件版本 |
| `features` | 至多 64 个唯一字符串，每项最多 80 字符，格式 `[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*`；列出需要的宿主能力 |
| `usage` | 最多 4096 字符，说明使用步骤、影响区域、适配条件及恢复方式 |
| `scopes` | 至多 64 个唯一 Android 包名，每项最多 240 字符，或 `android`；只声明应用作用域，不授权、自动勾选或创建进程 |
| `nativeSettings` | API 2 的原生设置入口对象，见下节 |
| `configurationAliases` | code265 起，API 2 的旧配置键到当前持久控件键的对象，最多 512 项；需 `minimumHost:265` 或更新版本与 `config.aliases` |

运行入口可读取对象属性 `C17.host`，包含 `apiVersion/versionCode/features/runtimePackage`；`features` 是当前宿主实际支持的标识数组，`runtimePackage` 是实际运行所在进程的包名。`C17.hasFeature(name)` 查询某项能力是否存在。code210 声明的能力为 `runtime.java`、`runtime.dex`、`runtime.hooks`、`runtime.main`、`runtime.actions`、`runtime.status`、`ui.groups`、`ui.native-settings`、`scope.systemui`；code246 增加 `scope.apps` 和 `ui.collapsible-groups`；code249 增加 `ui.group-paths`、`ui.color`、`ui.numeric-input` 与 `ui.presets`；code252 增加 `ui.unbounded-input`；code254 增加 `ui.action-state`；code265 增加 `config.aliases`；code290 增加 `runtime.values`；code291 增加 `ui.i18n`。`ui.edit-enables`、`ui.sort`、`ui.sort-removal`、`ui.app`、`ui.action-confirmation` 与 `ui.management-groups` 的用法见“表单与状态”及下文各节。能力名不能替代目标 ROM 方法核验，也不会授予额外权限。

```javascript
// 新字段和探测接口需要 code210；兼容旧宿主的源码应先检查接口是否存在。
if (typeof C17.hasFeature === "function" && C17.hasFeature("runtime.main")) {
  C17.runOnMain(function () { C17.log("运行于 " + C17.host.runtimePackage); });
}
```

只有依赖新增宿主能力时才提高 `minimumHost`；普通功能调整在现有 Java/DEX/Hook 接口内完成，递增插件自身 `versionCode` 即可。新字段不被旧宿主识别；需要继续支持旧宿主时，应保留不使用新字段的兼容包，不能仅删除 `minimumHost` 却保留未知字段。三份现有 API 1 模板因此保持旧源码字段和原最低宿主。

code246 起可按下节声明 API 2 多应用作用域；更早的宿主仍只执行 SystemUI。旧包没有 `scopes` 时继续沿用原有 SystemUI 环境。状态栏、控制中心、锁屏等 ROM 组件区域写在 `usage`，不写成应用包名。

### 旧配置键迁移（code265）

插件合并或更名设置后，可在元数据头与 `plugin.json` 同时声明别名：

```json
{
  "minimumHost": 265,
  "features": ["runtime.java", "config.aliases"],
  "configurationAliases": {"old_strength": "strength"}
}
```

这只是元数据片段；完整包仍须提供 API 2、运行入口和当前 `strength` 持久控件。别名对象可为空，最多 512 项。源键和目标键均遵循表单 `key` 的 1–64 字符语法，禁止 `constructor`、`prototype`。源键不得与当前任何控件键重叠，目标必须是 `switch/slider/input/color/app/select/sort/button` 中的一项；禁止同键映射、别名链、循环及重复目标。JSON Schema 校验对象结构、数量与宿主声明；跨控件引用、串联及重复目标由 `pack.py` 和真实宿主继续校验。

只有配置文件的 `pluginVersionCode` 小于当前插件版本时才采用别名。宿主先验证每个旧值是否符合目标控件的值约定，再生成只含当前键的导入状态；原配置文件不会被改写。同一文件同时含 `old_strength` 和 `strength` 时，`strength` 优先，与字段顺序无关，旧值仍须通过验证。只含旧键时迁移到目标，再与当前设置合并；未导入的其他设置与插件启用状态保持。当前版本配置中的旧键、未声明的未知键及来自更新插件版本的配置均拒绝导入。

别名不会创建隐藏控件，不计入持久设置数量，不在当前导出配置中保留旧键，也不会改变普通编辑、预设或运行状态的键。新增该声明后，须验证旧 JSON 导入、目标冲突优先级、错误类型拒绝和未知键拒绝；仅验证插件 ZIP 升级不等于完成旧配置兼容。元数据不随表单状态改变，目录条目不携带该字段。

### 多应用运行（code246）

新的导入形式仍是一个插件 ZIP，不需要为每个应用安装 APK。示例元数据：

```json
{
  "apiVersion": 2,
  "minimumHost": 246,
  "capabilities": ["runtime"],
  "features": ["runtime.java", "runtime.hooks", "scope.apps", "ui.collapsible-groups"],
  "scopes": ["com.android.systemui", "com.example.launcher", "com.example.sidebar"],
  "runtime": {"entry": "runtime.js"}
}
```

上面只展示与作用域有关的字段，身份、完整设置等仍按完整插件契约提供。包名必须逐项精确声明；不支持通配符、`android` 系统服务或 C17 自身包。`scope.apps` 只增加执行能力，不会替用户勾选框架作用域、启动目标进程或给予 Root。含外部作用域的插件必须使用 API 2 runtime，`settings` 和 `hooks` 输出保持空；独立的 `ui`、`C17.state` 和配置保存照常使用。不同 ROM 实际包名与目标类方法仍需核验。

每个已加载模块的目标进程取得自己的 `C17.context`、`C17.host.runtimePackage`、主线程、原生类加载器和插件生命周期。按实际包名选择适配器；全局静态字段、JS 变量与已装 Hook 都是进程内的，不能假定它们与 SystemUI 共享。表单状态由宿主统一分发。外部引擎以框架提供的稳定进程名分配独立的资源、`C17.dataDir/save/load` 和清理记录目录，同一包的主进程与 `:overlay` 等子进程也互相隔离；相同进程名重启后仍能读取自己的独立数据。这些接口不是跨应用或跨进程共享存储。SystemUI 独占旧插件的目录不变。

包含外部作用域的包不能声明非空 `runtime.preferences`。Android 原生 SharedPreferences 名称属于整个应用，不能安全地当作某个插件进程的独占数据清理。需要保存插件独立内容时使用 `C17.save/load/dataDir`；`C17.context` 仍是真实原生 Context，不通过改写它的偏好访问语义伪造隔离。

外部引擎通过专用 provider 读取计划与资产：宿主核验 Binder UID 包归属，只发往该包作用域内的启用 runtime 与最小安装清单；不交付宿主设置覆盖、其他插件、主设置、Root 管理命令或宿主私有路径。资产按当前启用版本、路径与作用域校验。归属记录、删除文件和准备素材共用串行后台队列，目标应用主线程只处理快照与生命周期，不执行这些文件扫描或等待跨进程锁。配置更新采用事件通知，重复通知合并，过期的异步读取结果丢弃；停用和安全模式会调用当前进程的清理。配置读取或更新观察不可用时暂停，暂时失败仅有限启动重试，没有常驻轮询。

`C17.publishStatus` 可在各进程报告已声明的 `status` 键。插件详情保留 SystemUI 状态，并按包名与进程追加外部最近报告及时间；安装代次/版本改变后不显示旧报告。它是最近一次观察，不能证明一个休眠、被杀或未重新启动的进程此刻仍在运行。插件应在初始化和确有状态变化的事件后发布，避免每帧或定时刷新。`C17.onAction` 注册仍可在运行脚本中使用，但宿主的表单动作只投递到 SystemUI 实例，不会广播或唤醒其他包；仅含外部作用域的包若实际提供 `action` 或通过 `optionsAction` 获取选项的 `sort` 控件，会被阻止启用。仅声明 `runtime.actions` 而没有依赖该通道的表单控件仍兼容；含 SystemUI 的混合作用域照常使用动作。

普通 Hook、主线程任务和代理仍由宿主随停用撤销；插件的 View 改动、组件回调、原生资源以及自己的线程必须通过 `C17.onCleanup` 恢复。系统直接结束进程时不能承诺执行清理代码。新增跨应用支持不会改变 API 2 的可信代码模型，代码取得所在应用本身可用的权限。

`ui.collapsible-groups` 同样从 code246 起提供。声明后，功能和管理分组默认收起，点开查看；控件、持久状态、动作和状态报告不因折叠而删减。未声明此能力的旧插件维持原有展示。

公开目录仍只允许原有基本元数据和 `file/sha256/size`，不接收上述新字段、表单、原生入口或脚本。标准构建器把新详情元数据保留在 ZIP 与描述中，生成目录时只复制基本元数据；下载后仍须核对 manifest 与真实描述，不以目录省略新字段为由绕过检查。

## 表单与状态

三段页面的字段映射与第三方开发示例见[详情三段布局](DETAIL-UI.zh-CN.md)。前置条件使用元数据，功能和管理使用 `ui.section/group`；复杂设置使用下文的通用原生入口，无需为单个插件修改宿主。

API 1 的 `ui` 包含 1–64 项，API 2 不设表单总项数配额。普通表单必须至少有一个真实关联功能的 `switch/slider/input/color/app/select/sort`，完整呈现插件全部可调参数；只有说明文字、预设或按钮会被拒绝导入。API 2 如提供有效 `nativeSettings`，可用它承载完整设置，`ui` 可为空数组；使用宿主状态的参数仍须有对应持久控件声明。每项 `title` 必填，最多 120 字符，`description` 可选且最多 512 字符。可编辑控件需要唯一 `key`，格式 `[a-zA-Z][A-Za-z0-9_.-]{0,63}`，禁止保留对象键。旧版已安装无配置插件保留原记录，不自动删除或改启用状态，作者应发升级包补全。

| type | 数据与默认值 |
|---|---|
| `text` | 说明文字，无状态 key |
| `switch` | 布尔值；缺省 `default:false` |
| `slider` | 数字；必填 `min/max`，范围在 ±100000 内且 min < max；step > 0 且不大于范围，缺省 1；default 缺省 min；2.0.4 / code196 起可选 `unit` 显示单位（最多 16 字符）；可选 `unboundedInput:true` 让手动输入与预设不受 min/max 限制（需宿主 `ui.unbounded-input`） |
| `input` | 文字；缺省空串，最多 4096 字符 |
| `color` | code249 起，字符串 `#RRGGBB` 或 `#AARRGGBB`；缺省 `#FFFFFF`；使用宿主颜色面板，完整保留显式 alpha |
| `app` | 字符串，空串或完整应用包名；缺省空串，宿主提供应用选择，声明 ui.app |
| `sort` | API 2 动态排序；default 是 JSON 字符串（缺省 `"[]"`），最长65536字符；必填唯一 optionsAction，由 C17.onAction 返回实时目录，声明 ui.sort；支持移除时另声明 ui.sort-removal |
| `select` | `options:[{value,label}]`，1–64 个唯一字符串值；default 缺省第一项 value |
| `button` | 点击后把 `value` 写入该 key 并重新编译；value 缺省 true，可用字符串/数字/布尔值；不是任意回调 |
| `preset` | code249 起；`values` 是 1–512 项对象（code255 起，code249–254 最多64项），键须引用同表单的持久控件、值须符合其类型和范围；只声明展示字段和唯一 key，不接受 default/value，不进入 `C17.state` 或运行时动作通道 |
| `action` | API 2、code206 起提供的一次性运行操作；必填唯一 key，不接受 default/value，也不进入 C17.state |
| `status` | API 2、code206 起提供的只读运行文字；必填唯一 key，由 C17.publishStatus 提供，不进入 C17.state |

超过64项的整套预设须声明 `minimumHost:255`。预设仅由用户点击后一次性应用，不会在导入或启用插件时自动套用；它不能引用动作、只读状态或其他预设。API 1 的表单总项数和 JSON 预算仍按原有限制校验。

code210 起，每项还可有 `section:"features"` 或 `section:"management"`，默认 `features`；`group` 为最多 120 字符的分组名称。这两项也适用于 `text/action/status`，不进入 `C17.state`，不改变动作与只读状态语义。不要用分组、折叠或原生界面迁移删除既有参数。

code249 起，可用 `groupPath:["作用区域","桌面","Dock"]` 声明 1–3 层菜单；每个名称为非空字符串，最多 120 字符，不含 NUL。显式路径优先于 `group`；旧 `group` 仍是单层原名，不按斜杠或其他符号拆分。设置页支持该路径；管理页的实际层级导航从 code261 起提供，须声明 ui.management-groups，不因此更改持久状态。使用新路径、颜色或预设的插件应声明对应能力及 `minimumHost:249`。

数字行可点击数值打开与主模块一致的输入框；支持有限小数与科学计数，手输保留精度并校验插件声明的硬范围，拖动才按 `min + n × step` 吸附。滑块声明 `unboundedInput:true` 时，min/max 只约束拖动范围和 `default`；手动输入与预设可填任意能以单精度表示的有限数值，超出范围时滑块停在端点、数值行照常显示已保存的值。插件运行端须自行处理极端值，不能假定状态落在 min/max 内。该字段需声明能力 `ui.unbounded-input` 及 `minimumHost:252`；旧宿主会按能力拒绝导入，不会把超范围值静默夹回。不要把 step 当作手工输入的小数位限制。把旧 `input` 颜色控件改为同 key 的 `color` 时，合法的已保存字符串（包括 alpha 和大小写）继续保留。

预设以一次配置事务同时应用全部 `values`，重新编译和合并校验成功后才保存；失败不留下部分修改，也不改变插件启用状态。它可以设置多项颜色、开关和数值，套用后各项仍能独立编辑，不是持续锁定参数的模式。预设不能引用动作、状态或另一预设。

```javascript
plugin.ui = [
  {"type":"switch","key":"enabled","title":"启用功能","default":false,"section":"features","group":"外观"},
  {"type":"action","key":"capture","title":"保存数据","section":"management","group":"数据管理"},
  {"type":"status","key":"saved_at","title":"保存时间","section":"management","group":"数据管理"}
];
```

读取 `C17.state.<key>`。首次评估状态可能为空，脚本应给出回退值；宿主取得声明后补齐默认值和已保存的状态，再评估一次。每次更改表单重新编译规则，脚本修改临时 `C17.state` 对象不会写回保存值。不要让元数据或能力声明依赖状态。

code248 起，升级预览还会在归一化之前，以旧插件完整状态评估一次新脚本。因此合并或重命名控件时，新控件的 `default` 可先读取同名新值，其次读取旧键，最后回退出厂值；不必保留两套可编辑控件。身份和能力仍与空状态声明核对；安装只使用已预览的默认值，并继续拒绝预览后状态改变、身份变化或不可重复的声明。脚本须在补齐新状态后产生相同默认值，恢复默认仍从空状态解析。

完整配置应逐项声明在 `ui` 中；宿主不会把一个模式选择自动展开成多个未声明的设置。API 1 的当前控件键和类型必须保持稳定，不支持通过增删有状态控件保存隐藏配置。可根据已保存的状态调整说明文字与输出规则，但不能让现有状态因控件类型、选项或范围变化而失效。`unit` 只用于滑块显示，不参与数值换算；需要兼容 code192–195 时请省略该字段。

名称和说明宜简短，详细操作与诊断可链接到发行文档。整理文案时保留现有键、类型、选项值、默认值和有效范围，并用已有非默认值验证升级前后仍然一致；不能通过移除控件实现“精简”。完整参数要求与简洁界面应同时满足。

2.0.4 / code196 起，插件详情可将该插件的所有独立配置一次恢复为声明的默认值。宿主从空状态解析默认值，最多四轮稳定生成且重复结果一致后才提交；身份、权限或默认规则异常、与其他插件冲突时保留原配置及启用状态。恢复默认不写入用户的 C17 原设置，也不撤销其他插件的配置。

## 原生设置入口

code210 起，API 2 ZIP 可在 manifest 与描述中同时声明 `nativeSettings:{"dex":"settings/classes.dex","className":"example.plugin.SettingsView"}`。`dex` 必须是包内已导入的安全相对 `.dex` 或 `.jar` 路径，最多 4096 字符，路径只含 ASCII 字母、数字、下划线、点、斜杠和短横线；`className` 是最多 240 字符的完整 Java 类名，支持 `$` 内部类。单 JS 不能声明不存在的 DEX 资源。入口类提供固定方法：

```java
public static android.view.View createView(
    android.content.Context context,
    String stateJson,
    java.lang.reflect.InvocationHandler bridge);
```

返回 View 由宿主显示。`stateJson` 是进入界面时的表单状态 JSON；需要最新状态时调用 bridge，避免把旧快照当作最新值。桥接调用格式为 `bridge.invoke(null, null, new Object[]{操作, ...})`：

| 操作与参数 | 行为 |
|---|---|
| `{"state"}` | 返回当前缓存状态的 JSON 字符串 |
| `{"host"}` | 返回与 `C17.host` 同结构的宿主探测 JSON 字符串；包名反映当前 App 进程 |
| `{"set", key, value, callback}` | 异步保存已声明的持久状态键；`callback` 是 `java.util.function.BiConsumer<String,String>`，在主线程接收 `(最新stateJson,null)` 或 `(null,error)` |
| `{"onCleanup", runnable}` | 注册界面销毁时执行的 `java.lang.Runnable` 清理任务 |
| `{"close"}` | 关闭此设置界面 |

`set` 不能凭空创建私有宿主字段；对应 key 必须由 `ui` 的 `switch/slider/input/color/select/button` 声明，并沿用其类型、范围和默认值。宿主异步完成隔离编译和保存，原生页面不应阻塞 App 主线程等待结果。复杂界面可组合这些公开字段；已声明参数仍应完整可调整。动作使用已有 `action` 通道，不能以写计数器模拟执行。

原生设置 Activity 不对外导出。它运行已导入、受信任的 API 2 代码，所在进程是 C17 App；插件停用时也可编辑配置，更新、删除、退出会使旧回调失效。插件应通过清理回调释放监听器与任务，不能保留已销毁页面。这个入口是可信代码界面，不能被描述成隔离沙箱、跨应用注入或自动获得 Root。

安全模式禁止打开原生设置代码入口，并关闭已打开的原生页面、执行清理；普通声明式表单仍可编辑，不能通过原生页面绕过暂停。

宿主没有 `C17.ui.runMain` 或 `C17.openNativeSettings` 运行接口；SystemUI 主线程任务使用已有 `C17.runOnMain`，原生设置页通过详情中的原生入口打开。不要依赖未公布的类或反射私有宿主字段来模拟稳定 SDK。

## settings 能力

声明 `capabilities:["settings"]` 后，通过旧声明式 `settings` 通道输出最多 64 项可见的 C17 设置覆盖。API 2 如使用此兼容通道也遵循相同约定；任意 Java/Android 扩展由 `runtime` 实现。键必须在当前宿主真实 `SettingsCatalog` 中，类型和范围与内置设置一致。数字超范围会拒绝，不自动截断。隐藏项、诊断/安全模式、资源导入和内部配置不能覆盖。

例如默认时钟需要 `clock_controls_enabled:true`、`clock_enabled:true` 与 `clock_pattern:"HH:mm:ss"`；字号还需要 `clock_size_enabled:true` 与 `clock_scale:100`。`clock_scale` 是 25–250 的百分比，不是 sp 字号。

code202 起，宿主移除控制中心自由编辑的页面和业务实现。已发布的 `qs_card_*` 声明式输出只保留旧库读取、配置维护和升级兼容，不再修改宿主运行布局；旧别名与行数键仍参与冲突校验。新实现应由 API 2 插件自带 DEX、独立状态与恢复逻辑。

### 控制中心自由编辑的旧输出

以下表格记录旧 API 1 输出的含义，供已有插件升级使用。code202 的控制中心布局、音乐 4×2 和更多磁贴完全由独立 API 2 插件实现；宿主只提供通用运行时和图片资源。表内同名键可继续作为插件独立 `state` 使用，但经 `settings` 输出已不再激活宿主功能。

code197 为试验宿主，实机编辑前的尺寸查询暴露了扩展索引进入原生网格的风险；code198 补齐前置尺寸查询的来源与范围保护，新插件不应面向 code197 启用。API 1 尚无独立的最低宿主版本字段，请在插件首行说明、README 和发布说明中明确所需版本；这些说明不代表设备效果已验收。

| 设置键 | 类型与范围 | 行为 |
|---|---|---|
| `qs_card_grid_rows` | 整数 4–7 | 顶部固定 4 列，可编辑行数；受屏幕可用空间和适配结果限制，不能承诺所有设备都能显示 7 行 |
| `qs_card_large_media_enabled` | bool | 解锁音乐卡片的手动尺寸调整；启用时不自动放大，用户在原生编辑界面拖拽为 4×2 |
| `qs_card_native_resize_enabled` | bool | 解锁已适配原生卡片的手动尺寸调整，包括音乐卡片；未知卡片保持原生 |
| `qs_card_small_tiles_2x1_enabled` | bool | 将原生小磁贴添加到顶部扩展区后显示为 2×1 |
| `qs_card_small_tiles_mode` | `always/hidden/more` | 原生底部始终显示、隐藏，或使用顶部「更多磁贴」入口进入独立页面 |
| `qs_card_hide_idle_controls_enabled` | bool | 没有媒体时隐藏音乐播放控件 |
| `qs_card_wallpaper_enabled` | bool | 使用宿主中已配置的音乐卡片壁纸；插件不能读取或导入文件 |
| `qs_card_wallpaper_opacity` | 数字 0–100 | 壁纸不透明度百分比 |

`more` 模式进入控制中心时显示顶部编辑区域，点击「更多磁贴」才打开原生小磁贴页面。更多页保留原生磁贴点击、长按和横向分页；页面返回入口与系统返回键回到顶部，原生弹窗和编辑返回优先。上划关闭控制中心后再次下拉从顶部页开始，不把普通滑动解释为打开更多页。code196 的旧值 `swipe` 在 code197 运行副本中兼容为 `more`，不重写保存值；新插件的选项应列 `more`，不能向 code196 承诺此新交互。

顶部顺序与扩展尺寸只保存到宿主独立配置；原生状态、缓存、Room、原生备份必须保留原几何。关闭、未生效、安全模式、横屏、适配失败或空间不足时，应撤回扩展并恢复原生可访问界面。更多入口只有在原生 UI 消费者实际提交成功后才能隐藏底部小磁贴；入口或页面失效时必须保留原生访问路径。插件 README 应分别记录这些回退、尺寸拖拽、返回与重新下拉的真实设备验证结果，不能以导入成功或桌面检查替代。

覆盖在运行时合并到配置副本，不写用户保存的偏好。多个已启用插件不能覆盖相同设置键；冲突会拒绝启用或更新，不会按加载顺序覆盖另一插件。升级必须用更大的 `versionCode`，保留仍符合新控件约束的表单状态，其余使用新默认值，并默认停用。关闭、删除和安全模式可撤销覆盖。

## systemui 能力

声明 `systemui` 后使用旧声明式 `hooks` 通道，每插件最多 32 条，全部启用插件合计最多 64 条。API 2 如使用此兼容通道也遵循相同约定；`runtime` 中的任意方法 Hook 不使用这里的数量、目标类或参数限制。每条共同字段如下：

```json
{"id":"am-label","className":"com.android.systemui.statusbar.policy.Clock","method":"updateClock","argCount":0,"kind":"text","find":"AM","replace":"上午"}
```

`className` 必须为精确类名，限 `com.android.systemui.`、`com.oplus.systemui.`、`com.oplusos.systemui.`、`com.oplus.keyguard.` 前缀。`method` 是该类实际声明的 Java 方法，`argCount` 为 0–8；同名同参数数量的重载有歧义时拒绝，也不支持 native/abstract/bridge/synthetic 或 Object 生命周期方法。没有 `process`、通配符、任意 Java 代码或 dex 加载字段。

| kind | 专属字段 | 边界 |
|---|---|---|
| `text` | `find/replace` 成对，另可有 `prefix/suffix` | 操作当前 TextView 文字；纯字面替换，非正则。find 非空，每字段 ≤256 字符；至少一项动作 |
| `property` | `property`、数字 `value` | View 的 alpha 0–1、scaleX/scaleY 0.1–4、translationX/translationY ±200dp |
| `result` | `value` | 先执行原方法，再把返回结果替换为 bool/有限数字/单字符字符串；必须匹配真实非 void 基本返回类型与数值边界 |

`text/property` 仅适用于目标类自己的非 static View 实例，text 还必须为 TextView。规则不保证任何 ROM 都有同一方法；无法核验的目标保持原生，不表示修复成功。

所有规则在原方法正常返回后应用，不跳过原方法及其副作用。规则作用位置由 `className + method + argCount + kind` 决定；property 还包含属性名。同一作用位置不能同时启用多条规则，启用或更新时明确拒绝冲突。不同 kind 或不同属性不视作同一位置，但仍需开发者核验组合效果。

示例 Clock/updateClock 取自 [AOSP Clock.java](https://android.googlesource.com/platform/frameworks/base/+/ac387ca9f3c2a304ff0b19c143c60517c916b1b8/packages/SystemUI/src/com/android/systemui/statusbar/policy/Clock.java)。AOSP 的实现不是对 Oplus/ColorOS 或后续系统版本的兼容承诺。平移规则由宿主把 dp 换算为 Android [View 平移属性](https://developer.android.com/reference/android/view/View#setTranslationY(float))需要的像素。

## 运行与容量

API 1 的入口脚本在 `CustomPluginService` 隔离 UID 中运行。它不获授主应用权限或 Root 身份，也不能直接调用 Android/Java、文件、网络、计时器或第三方库。SystemUI 只接收宿主核验过的 JSON 设置和有限规则。

API 2 在 SystemUI 进程内执行完整 JavaScript，直接使用 Rhino LiveConnect、Java/Android 对象、反射、任意类和方法、构造器/方法 Hook、DEX 加载、接口代理、文件和线程等宿主进程可用能力。`C17.context`、`C17.pluginDir`、`C17.state`、`C17.findClass`、`C17.hook/hookAll/hookAllConstructors/hookNative`、`C17.deoptimize`、`C17.proxy`、`C17.loadDex`、`C17.require`、`C17.onClassLoader`、`C17.onCleanup`、`C17.runOnMain`、`C17.dataDir`、`C17.save/load` 和 `C17.log` 是便捷 API；API 2 不对脚本的 Java 类或系统功能做白名单限制。

运行入口可使用以下接口。`C17.state` 是本次启用的完整表单状态；只更新表单状态时保留运行实例、DEX 和 Hook，通过 `onStateChanged` 通知新旧状态。`C17.context` 是 SystemUI Context，`C17.moduleClassLoader` 是宿主模块 ClassLoader，`C17.pluginDir` 是本版本经过校验的只读资源目录（`java.io.File`）。

| 接口 | 参数与返回 |
|---|---|
| `findClass(name[, loader])` | 查找类，返回 `java.lang.Class`；省略 loader 时搜索宿主、系统和已加载 DEX |
| `hook(executable, callback)` | Hook 反射 `Method/Constructor`，返回取消函数 |
| `hook(className, method, parameterTypeNames, callback)` | 精确参数类型列表，例如 `["int", "java.lang.String"]`；构造器用 `"<init>"`；返回取消函数 |
| `hookAll(className, method, callback)` | Hook 目标类声明的同名重载；返回取消函数 |
| `hookAllConstructors(className, callback)` | Hook 目标类声明的构造器；返回取消函数 |
| `hookJava(executable, invocationHandler[, retainOnDisable])` | code205 起使用标准 JDK `InvocationHandler` 接收原生调用，返回 JDK `Runnable`；调用 `run()` 取消 Hook。不要求动态 DEX 引用框架 API |
| `hookNative(executable, javaHooker[, retainOnDisable])` | 接受 Java `XposedInterface.Hooker`，返回原生 `HookHandle`；默认为停用时 unhook |
| `deoptimize(executable)` | 请求框架反优化指定方法或构造器 |
| `proxy(interfaceClassOrName, callbacks[, propagateErrors])` | 创建 Java 接口代理；callbacks 可为单函数或按方法名映射的函数对象；默认捕获异常并停用实例。code201 起第三参数 `true` 将异常交回 Java 调用者，由其处理可选接口回退；未处理异常仍由外层运行入口隔离。类继承应使用 DEX |
| `loadDex(path)` | 相对资源目录或绝对路径，返回 ClassLoader，并供后续类查找和 Hook 使用 |
| `require(path)` | 执行并缓存 CommonJS 模块，支持 `exports/module.exports`、相对 require；省略 `.js` 可补全 |
| `onClassLoader(callback)` | 接收已有及后续发现的 ClassLoader，返回取消函数 |
| `onStateChanged(callback)` | 表单状态改变时在主线程接收 `(next, previous)`，保留当前实例；返回取消函数 |
| `onAction(key, callback)` | code206 起注册一个已声明 action 的主线程回调，接收 `{requestId, instanceId, action}`；返回取消函数 |
| `publishStatus(values)` | code206 起合并已声明 status 的只读文字，例如 `{saved_at:"2026-10-20 15:30"}`；每项最多4096字符，单次更新及合并后状态各最多64KiB UTF-8 |
| `runOnMain(callback)` | 排入 Android 主线程，返回取消函数；停用时取消未执行任务 |
| `host` | code210 起的只读宿主探测对象属性：`apiVersion/versionCode/features/runtimePackage` |
| `hasFeature(name)` | code210 起查询当前宿主实际能力；不改变权限、作用域或 ROM 适配情况 |
| `onCleanup(callback)` | 注册按后进先出顺序调用的恢复函数；应使用事先捕获的 Java 对象完成恢复 |
| `dataDir()` | 返回本插件跨版本保留的可写数据目录（`java.io.File`） |
| `save(name, value)` / `load(name[, fallback])` | 保存 JSON 值，或读取已保存值；不存在时返回 fallback |
| `log(message)` | 写入带插件 ID 的模块诊断日志 |

### 一次性动作与只读状态

表单动作由用户每次点击生成新的随机 requestId，并绑定正在运行的实例 instanceId。结果报告重试只返回该次已记录的结果，不再次调用插件；插件替换、停用、安全模式或 SystemUI 重载后，旧实例的请求会被拒绝。请求和结果不写进插件配置或磁盘命令文件；宿主沿用现有主线程命令分发通道，不为动作创建服务或工作线程。安全模式仍暂停整个插件，动作不能绕过它。

回调可以返回字符串，或包含 `message` 的 JSON 对象作为完成提示，结果最多64KiB UTF-8；抛出的异常会显示本次操作失败，这次请求仍已消费。需要保存的数据由插件自行原子提交，保存成功后再发布实际保存时间。只读状态本身不持久化，插件应在运行入口读取自己的已保存数据并发布初始状态，再在动作完成后更新；不要用设置计数器模拟按钮或在 `onStateChanged` 中重放命令。例如，元数据入口除完整身份与 runtime 声明外提供：

```javascript
plugin.ui = [
  {"type":"switch","key":"enabled","title":"启用功能","default":false},
  {"type":"action","key":"capture","title":"保存数据"},
  {"type":"status","key":"saved_at","title":"保存时间","description":"尚未保存"}
];
```

运行入口注册回调，并从插件自己的已保存记录初始化只读文字：

```javascript
C17.publishStatus({saved_at: C17.load("saved-meta", {displayTime:"尚未保存"}).displayTime});
C17.onAction("capture", function (request) {
  var saved = captureAndCommit(); // 插件自身负责采集条件、原子覆盖与错误恢复
  C17.publishStatus({saved_at: saved.displayTime});
  return {message: "已保存"};
});
```

动作可以声明 `confirmation`（能力 `ui.action-confirmation`，1–1024 字符）：用户点击后宿主先显示这段确认文字，确认后才发起请求。只给会清除数据、重置状态等不可撤销的操作使用。

声明 `ui.action-state`（code254 起）后，动作回调还可以返回 `{"message":"…","state":{"key":值,…}}`：宿主把 `state` 中的每一项当作用户编辑写入对应的持久控件，按控件类型、范围和选项逐项校验，写入方式与在设置页修改相同；未声明此能力时 `state` 被忽略。适合把一次测量或校准的结果保存为设置，例如测得的方向角。键必须是表单中已声明的持久控件，不能借此写入未声明的数据；需要大量或私有数据时仍用 `C17.save`。

`action/status` 最低需要 code206，code205 不支持。状态或动作之间及与可编辑设置之间的 key 必须唯一；code206 只允许 `type/key/title/description`，code210 起还可用展示字段 `section/group`，不接受持久值、数值范围或选项字段。它们不能替代真实功能设置入口。操作只在启用、已就绪且非安全模式的当前实例上执行；业务开关关闭是否允许某个操作，由插件校验自己的业务条件。旧 `button` 的持久赋值语义保持不变。

JS Hook 回调接收 Chain：`getExecutable()`、`getThisObject()`、`getArgs()`、`getArg(index)`、`proceed()`、`proceed(Object[])`、`proceedWith(receiver)`、`proceedWith(receiver,Object[])`。回调返回新结果；`proceed` 使用原方法结果并保留原异常，重复调用不会重复执行原方法。要传入 Java 数组，可通过 Java 反射创建匹配参数类型的数组。类暂未出现时，命名 Hook 会在后续 ClassLoader 到达后重试。

`hookJava` 直接调用 Java `InvocationHandler.invoke(receiver, method, frame)`，不经 Rhino 解释器。`method` 是目标 `Method`，构造器时为 null；`frame` 为四项 `Object[]`：原 receiver、参数快照 `Object[]`、continuation `InvocationHandler`、目标 `Executable`。调用 `continuation.invoke(selectedReceiver, null, replacements)` 继续原生链；replacements 为 null 时沿用原参数，传入新数组可替换参数，selectedReceiver 可替换 receiver。只允许在当前同步回调线程、回调返回前使用；重复继续读取同一次原生执行的缓存结果，原异常身份保持。回调结束会撤销并释放宿主 continuation 对原生链的引用。返回值和异常直接交给原生调用链；插件须自行正确处理 Java 返回类型。普通 handle 同时由宿主管理，在停用时取消；保留选项与下段的旧对象保护语义相同。

部分框架禁止反射或动态代码直接解析其 API 类，此时 `hookNative` 及带框架 API 类型的自定义代理接口不可用，应使用 `hookJava` 的 JDK 边界。宿主不改变框架的保护策略。该运行属性由 [libxposed API](https://github.com/libxposed/api/blob/master/api/src/main/java/io/github/libxposed/api/XposedInterface.java) 的 `PROP_RT_API_PROTECTION` 描述。

`hookNative(..., true)` 专用于停用后仍须保护已交给系统的旧视图或扩展索引。此模式的 Hook 保留到 SystemUI 重启；Java Hooker 必须自行判断启用状态、恢复扩展布局，并在停用后只保留必要的兼容保护。宿主不会替任意原生 Hooker 自动实现这些语义。插件普通 Hook 和任务应使用默认生命周期；发行 README 必须说明是否使用保留保护及其退出条件。

API 2 与 API 1 有意采用不同的信任模型。启用 API 2 等同于允许该作者在 SystemUI 权限下执行代码。安全模式、停用和卸载会撤销普通受管 Hook，并让普通代理回调失效；显式保留的原生兼容保护遵循上一段约定。宿主无法撤销插件直接写入的系统数据、终止插件自行创建的线程或恢复它自行修改的进程状态。任何 JS 在主线程无限循环、内存耗尽或破坏系统界面都可能让 SystemUI 重启。代码只获得 SystemUI 本身拥有的权限，不会自动变成 Root。

插件的异步布局与恢复逻辑须独立遵守对象归属和实例生命周期。测量尚未就绪时保留原生界面，后续按相关几何变化重试；几何不变的连续帧不得反复提交布局。已停用、解绑或属于旧实例的回调不得恢复扩展，成功应用后应结束无用等待。视图被复用时同时核验外层布局和内部子视图是否回到当前原生状态，恢复只作用于插件拥有的对象。宿主清理 Hook 不会替插件完成任意 View 属性与监听器恢复。

API 1 继续使用单脚本 256KiB、输出 JSON 128KiB/4096 节点/12 层/256 数组项。导入元数据求值对两个 API 都采用隔离 UID、200000 条指令、约 600ms 解释器检查点、64 层解释栈和 1.5 秒独立看门狗；API 2 的独立运行入口不受这些预算约束。API 2 元数据 JSON 不使用 API 1 的字节、节点、数组项数配额，但仍限制 64 层数据嵌套。

API 1 ZIP 最多 32 项、总解压 1MiB、单项 256KiB；大于 64KiB 的条目压缩比不得超过 200。API 1 只接受 `.js/.md/.txt`、根 `plugin.json` 与 `LICENSE` 文本。两个 API 都拒绝 ZIP 路径穿越、重复规范路径、链接、加密或分卷；API 2 资源可为二进制。当前导入器使用经典 ZIP 的 16/32 位字段，不支持 ZIP64；这是档案格式边界。在线下载还受 Java 字节数组可表示的包长度和实际可用内存约束。路径、磁盘和 Android 运行机制同样不能由插件协议解除。

### 插件间共享值（code290）

声明 `runtime.values` 的插件可以在同一进程内发布和读取少量共享值，用于让多个插件协同显示（例如把媒体卡片算出的强调色交给另一个插件）：

```javascript
C17.publishValue("media_accent", 0xFF336699);          // 发布到自己名下：<插件 id>/media_accent
var accent = C17.readValue("host/media_accent");       // 读取宿主或其他插件的值；没有时为 null
var stop = C17.observeValue("dev.example.other/level", function (value) {
  // 主线程、插件锁内回调；值变化时才通知，所有者停止时收到 null
});
// stop(); 可提前取消；插件停止时宿主自动取消
```

- 键为 `所有者/名称`：所有者是 `host` 或插件 id，插件只能发布到自己名下；名称为 1–40 个小写字母、数字或下划线。
- 值只能是 null、布尔、有限数字或不超过 256 字符的字符串；发布 null 即清除。值不变时不通知。
- 值只存在于当前进程内存，不持久化、不跨进程；插件停止时它发布的值全部清除，观察者收到 null。
- 宿主当前在 SystemUI 发布 `host/media_accent`：原厂按封面计算的媒体主色。

## 界面翻译（code291）

规则与边界见[分发规范](../SPEC.md#界面翻译code291)。插件以自己的原文编写，另在包内附带翻译表：

```text
my-plugin/
  plugin.json
  main.js
  runtime.js
  i18n/
    en.json        {"自动记录配置变化": "Record configuration changes automatically", ...}
    zh-Hant.json   {"自动记录配置变化": "自動記錄設定變化", ...}
```

语言标签与 C17 一致：`en`、`ja`、`ko`、`zh-Hans`、`zh-Hant`、`es`、`pt`、`fr`、`de`、`it`、`nl`、`pl`、`ru`、`uk`、`tr`、`el`、`cs`、`ro`、`sv`、`ar`、`fa`、`he`、`ur`、`hi`、`bn`、`th`、`vi`、`id`、`ms`、`sw`。表中只需列出需要翻译的原文；宿主对每条显示文字做整条精确匹配，找不到时回退到不带地区的语言、`en`（`zh-Hant` 回退 `zh-Hans`），最后显示原文。

建议的做法：

1. 列出所有会显示的原文：manifest 的 `name/description/usage`，`plugin.ui` 中每个控件的 `title/description/unit/confirmation/group/groupPath` 和选项 `label`，以及运行脚本里 `publishStatus` 的固定文字和动作返回的 `message`。表单随状态变化时，要覆盖所有可能出现的控件。
2. 保持原文稳定。原文改动后旧译文不再匹配，会回到原文显示，发布前应同步更新各语言的表。
3. 拼接文字无法匹配：`"已保存 " + count + " 项"` 这样的状态应改为固定句子加单独的数字状态项，或在运行脚本中按语言生成。
4. 占位符、`{…}` 记号、版本号、包名等在译文中保持原样。
5. 用 `python tools/pack.py standard <目录>` 检查；导入 C17 后切换“关于 → 语言”逐页核对。

`templates/standard-runtime/i18n/en.json` 是一个完整的示例。

## 验收

先导入并审查能力，确认安装后再手动启用。检查表单默认值、修改状态、与已有设置冲突、停用/移除/安全模式的恢复行为，再在目标 ROM 测试。导入成功、schema 通过或文件哈希一致均不能代替系统界面的实际效果验收。

[运行与交互验收指南](VALIDATION.zh-CN.md) 给出配置兼容、异步测量、视图复用、媒体原生拖动、背景复用和文字显示的黑盒步骤。指南不提供业务实现，也不表示新增 API 或任何设备已经通过验收。

## 表单补充与存储归属

动态排序值是稳定标识数组的 JSON 字符串，或 `{"version":1,"order":[...],"hidden":[...]}` 的 JSON 字符串。标识必须唯一、非空、最长4096字符；hidden 必须包含于 order。optionsAction 与全部控件 key 共用唯一命名空间。目录动作返回 `{available:true,revision:"版本",items:[{value:"稳定标识",title:"名称"}]}`；支持移除时增加 `allowRemoval:true`。不可读取时返回 available:false 和具体 message，不能用空列表覆盖用户排序。

`action.confirmation` 是1–1024字符的非空确认文案，仅作用于一次性动作，不接受于 switch/status。应写实际影响，避免每个普通刷新动作都弹确认。

`runtime.preferences` 是可选的自有原生 SharedPreferences 文件名列表，最多32个、不可重复，格式 `[a-zA-Z][a-zA-Z0-9_.-]{2,119}`。声明文件表示插件承担其归属与卸载清理，不能填写其他功能或宿主拥有的文件。跨应用插件禁止非空此字段；独立数据优先使用 C17.save/load/dataDir。不要在停用清理时删除用户持久设置。

`enableOnEdit` 可用于 switch/slider/color/select，引用同一表单内1–4个唯一且不同于自身的 switch。编辑后自动开启这些开关；不能引用未声明键或动作。能力为 ui.edit-enables。未声明时不自动联动。

## 事件与性能约定

运行入口在一次生命周期中只加载一次 DEX、解析一次固定类契约；配置变化通过 C17.onStateChanged 更新现有对象，停用通过 C17.onCleanup 恢复自有 View、回调和资源。不要为状态行常驻轮询、逐帧发布状态、改设置就重启进程或重新安装所有 Hook。只缓存稳定契约，不把旧几何或旧运行结果当成实时状态。进程被系统直接终止时不能保证清理回调执行。
