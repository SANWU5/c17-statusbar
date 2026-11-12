# 图标管理 v3 数据合同与文件归属

2026-10-18，基于作者已确认的 [v3 方案](PLAN-ICON-MANAGEMENT-V3.zh-CN.md)。code186 正式封包后已开始 code187 实施。角色清单以原生审计交付为准，本文不把设计示例当作已适配事实。

## 1. 配置记录

新增一个便携字符串键 `icon_management_v3_records`，默认值：

```json
{"version":3,"scopePacks":{},"overrides":{},"appearances":{},"animations":{}}
```

- `scopePacks`：功能范围 → 内容摘要形式的套装 ID。
- `overrides`：完整角色 → `Override`；缺记录即跟随套装。
- `appearances`：`scope:<范围>`、`target:<目标>` 或 `role:<完整角色>` → `Appearance`。
- `animations`：目标 → 是否允许使用套装动画；缺记录默认允许，实际播放仍受原生许可约束。
- 记录只包含数据。禁止包提交原生类名、反射方法、任意表达式或宿主定位规则。

`Override`：

```text
mode = system | pack
packId = 64 位小写 SHA-256，仅 pack 必填
sourceRole = 包内静态完整角色，仅 pack 必填
```

`Appearance`：

```text
mode = inherit | system | custom | artwork
lightColor / darkColor = 不透明 ARGB，仅 custom 有效
opacity = Float，可空；null 继承，显式值为 0–100
```

颜色来源与透明度分别按 `role > target > scope > 默认` 查找；颜色继承不妨碍单独设置透明度。没有任何显式颜色选择时，`render=color` 保留素材原色，`render=mask` 使用宿主实际前景色。`artwork` 不把蒙版素材伪装成彩色素材。用户透明度不混进色盘 Alpha，运行时根据真实宿主合同只乘一次。

## 2. 固定角色与支持状态

类：`IconRoleRegistry`。

```java
List<Target> targets(String scope);
Target target(String id);
Role role(String fullRole);
String scopeOfRole(String fullRole);
```

`Target` 字段：`id`、`title`、`scope`、`roles`、`stateLabels`、`supported`、`reason`、`dynamicSupported`。`Role` 提供完整角色、目标、状态、显示名称与准入状态。

范围固定为：`hint`、`wifi`、`cellular`、`battery`、`tile`、`slider`、`media`、`device`、`footer`。目标和状态分别来自审计事实；例如三段音量、相机/麦克风许可与非开关入口不得强行使用通用 on/off。

未知但语法安全的包内角色可以导入并计入“当前版本未适配”，不进入运行时原生匹配表。已登记目标的非法状态必须拒绝。第三方 TileService 默认原生；显式指派身份只作为真实 spec 数据比较，不允许包定义任意宿主查找。

`supported` 是本版适配器能力，不是手机视觉验收结果；覆盖表分别记录原生证据、源码静态验证和实机结果。

## 3. 设置与纯计划 API

类：`IconManagementSettings`。

```java
State parse(String records) throws IOException;
String encode(State state) throws IOException;
String applyPack(String records, String packId, Collection<String> scopes,
                 boolean clearOverrides) throws IOException;
String applyTarget(String records, String targetId,
                   Map<String,Override> roleOverrides,
                   Appearance appearance,
                   Map<String,Appearance> roleAppearances,
                   boolean animationEnabled) throws IOException;
String removePack(String records, String packId) throws IOException;
String clearScopePack(String records, String scope) throws IOException;
String reset();
```

`State` 的所有集合只读；对外暴露 `scopePacks`、`overrides`、`appearances`、`animations`。

- `applyPack` 只改所选范围的套装；明确要求清除时才删除这些范围的来源覆盖，不清颜色、透明度或动态选择。
- `applyTarget` 输入完整目标草稿，一次替换该目标的来源和外观记录，目标之外不变。`appearance=null`、角色记录缺项分别表示移除相应显式设置。
- `removePack` 删除该来源的范围引用和单项引用，保留独立外观；资源删除事务另行执行。
- `clearScopePack` 只移除一个范围的套装来源；单项图案、颜色、透明度和动态选择均保留。
- 所有函数只返回新记录，不写偏好或文件。返回取消草稿时不调用保存。

类：`IconManagementSelection`，生产与检查共用的纯计划层。

```text
输入：State、已经验证的 manifest 集合、目标角色
输出：显式系统回退标记、候选 AssetRef、解析后 ColorPlan、动画许可
顺序：角色明确系统 → 明确角色来源 → 范围套装中的完全相同角色 → 当前原生/功能默认
```

不借用其他开关状态或充电类别。缺失/损坏候选允许沿同一计划安全回退。`ColorPlan` 只携带颜色来源、RGB 与用户透明度；宿主原生 Alpha 的位置由运行时已核对的适配器负责。

另提供套装应用预览统计：将替换、缺失、保留的角色清单，以及不受支持目标。单项覆盖默认保留，不因预览改变配置。

## 4. manifest 与资源模型

类：`IconManagementManifest`，只接受 version 3；旧 `IconPackManifest` 保留原有历史合同。

```text
Manifest:
  id/name/author/license/version
  icons: Map<String,Asset>
  transitions: Map<String,Transition>
  unsupportedRoles: Set<String>

Asset:
  source/render/format
  sha256（导入时计算；运行时规范化 manifest 必须携带）

Transition:
  source/from/to/reversible
  sha256（导入时计算）
```

`format` 由实际受检资源格式确定，不信任扩展名。外部声明摘要时必须与实际字节一致；内部规范化 manifest 保留资源摘要，使 SystemUI 独立核验 Provider 与缓存的字节。

JSON 使用拒绝重复字段、无效 Unicode、未知已知字段、非有限数和超限深度的严格读取。存储 ID 根据规范化 manifest 与有序资源字节计算，不根据包名覆盖。

静态资源：透明 PNG、受限 VectorDrawable XML。SVG/WebP/APNG/AVD 等明确拒绝。矢量解析为自有不可变节点模型，再渲染；不得直接 inflate 外部 XML。XML、PathData 的命令、节点、尺寸、深度与数值预算由检查固定，禁 DTD、实体、资源引用及外链。

动态资源：受限纯图形 Lottie JSON。`IconAnimationAsset` 负责验证与只读元数据，不在后台解析器里反射 OEM 类。仅接受 shape layer、group/path/fill/stroke/transform/ellipse/rectangle；禁止图片、文字、表达式、遮罩、特效、3D、父层与时间重映射。至少一个属性须含时间递增且数值或路径真正变化的两帧，静态 JSON 或不变的关键帧不能冒充动态。外部 composition 的绑定、真实状态与生命周期由运行时适配器负责。最终可播目标待原生桥接审计确认；不能把静态替换称为动画通过。

统一解码类 `IconAssetDecoder`：后台核验摘要、解析与按尺寸栅格化，返回 `Decoded{bitmap,mask,pixels}`；实际 draw/measure 只读取已发布缓存。预览与运行时使用同一解析/解码路径。

Vector 根 `alpha` 对最终合成层应用一次，单个 path 的颜色 Alpha 与 fill/stroke Alpha 在其绘制时应用；用户透明度保持独立。PNG 的元数据块在校验 CRC 后剥离，不交给图像解码器解压 ICC/文字载荷；APNG 明确拒绝。

## 5. 资源库与 Provider API

类：`IconManagementRepository`。

```java
List<Pack> list(Context context) throws IOException;
PreparedPack prepare(Context context, Uri uri, String name) throws Exception;
PreparedPack prepare(Context context, File archive, String name) throws Exception;
Pack rename(Context context, String id, String name) throws Exception;
void delete(Context context, String id) throws IOException;
Bitmap preview(Context context, String id, String role, int size) throws Exception;
Pack installOriginal(Context context) throws Exception;
Uri manifestUri(String id);
Uri assetUri(String id, String source);
File providerFile(Context context, Uri uri) throws IOException;
```

`Pack{id,name,author,license,iconCount,manifest}`，附静态格式/动态数与覆盖统计。`PreparedPack` 保留 `pack`、`commit(BooleanSupplier)` 和 `close()`，导入只入库、不改设置。原包仅在准备阶段读取；取消清理受控暂存，不删除已有库。重复摘要可复用，不同字节不会因同名覆盖。

新目录为 `files/icon-management-v3/`，Provider 为 `/icon-management-v3/<hash>/manifest.json` 与对应已登记资源路径。路径和摘要分别校验，不能访问旧目录或任意文件。运行时驻留缓存使用独立 v3 前缀，并复验资源摘要；不能命中旧 v1/v2 图片缓存。

成功新增、同摘要重新导入、重命名、删除及清空资源库后，Repository 在发布完成且退出文件锁后对根 `URI_PREFIX` 发 `ContentResolver.notifyChange`。取消、准备失败或发布门控失败不通知；同摘要恢复资源也触发运行时重载，不依赖配置字符串发生变化，不用绘制时 I/O 或常驻轮询。Observer 通知失败仅记诊断，不把已成功落盘的资源事务误报为失败。

`IconManagementArchive` 支持 ZIP 和未加密单卷 RAR4/5；校验后统一管线，重复/大小写冲突、链接、穿越、加密、超限和实际不支持格式整包拒绝。默认 GitHub 下载只寻找明确的 `icon-pack-v3.zip` Release 附件，缺失时明示未发布，不退回仓库源码归档。

## 6. 迁移、版本与文件归属

新增 schema47 的便携键只有 `icon_management_v3_records`。旧三个字段继续隐藏且不参与 v3 运行时：

```text
status_icon_pack_enabled
status_icon_pack_layers
status_icon_pack_assignments
```

便携导出排除旧字段；旧备份导入跳过这些字段并通过 `PreparedImport.skippedLegacyIcons` 明确报告，其余合法设置照常。App 私有独立迁移 prefs 保存完成/提示状态，不进 SystemUI 快照。旧资源迁往受控 retired 区域后才标记完成；不能在 SystemUI 删除旧库，不支持旧规则自动恢复。

实施归属：

| 归属 | 文件/工作 |
| --- | --- |
| 字体/图标后端代理 | 本文新增的 Java 模型、角色登记、严格 parser、归档、矢量、动画准入、统一解码、资源库、纯计划及对应 checks；`ConfigTransfer`、`SettingsProvider`、`ResidentCache` 的 v3 接口 |
| 根代理 | `StatusBarSettings`、`SettingsFrameworkMirror`、`SettingsCatalog`、`SettingsOrganization`、Upgrade/schema/版本/注册计数；运行时原生 hook、颜色和生命周期接入；统一构建与封包 |
| UI 代理 | `ModernIconSettings` 及 `ModernMainActivity` 图标流程、功能页快捷入口、离线套装入口与草稿 |
| 原生审计代理 | `ICON-V3-NATIVE-CONTRACT` 覆盖矩阵、真实 spec/状态、Lottie composition 与回收能力证据 |

code186 正式封包完成前，后端只读生产代码，独立实验限忽略目录；待根代理明确通知后开始 v3 产品实现。

后端桌面检查采用真实 ZIP、ImageIO 像素和 JDK XML tokenizer 执行生产准入逻辑；不以其替代 Android Skia 栅格视觉、RAR JNI 或 OEM Lottie 实机验收。`IconManagementOriginalCheck` 使用完整生产资源库管线核验 App 内置离线包，不写用户配置。
