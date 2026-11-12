# 本地构建与正式分发

## code261 标准包发布前检查

1. 新作者先运行 `python tools/pack.py standard <源码目录>`，再 build；旧 API 1 可继续 lint/build。
2. 动态表单在真实宿主执行后导出 descriptor.json，执行 `python tools/pack.py descriptor descriptor.json --manifest plugin.json --standard`。该命令不执行 JavaScript，不验证完整 settings/hooks 语义，也不代替真实 ZIP 导入。
3. 安装上一版并保存非默认值，升级新包；核对 ID、配置、排序、独立存储，确认 API 2 升级默认停用的宿主语义，重新启用后继续生效。
4. 实机核对说明页申请/重启作用域、设置及管理分类、返回路径、运行状态、停用恢复。只发布已明确适配的 ROM 和场景。
5. 运行全部工具与 Schema 测试；CI 安装 jsonschema，不允许把跳过结构测试当成全通过。生成哈希、审阅差异，再按原分发步骤发布。标准模板不进入下载目录。


## 开发模板

`templates/` 是教学源码，不是公开插件列表。当前 root `index.json` 的 `plugins` 必须保持为空，`packages/` 没有发行包。不要把模板的 ID、作者或 ROM 适配说明直接当成自己的正式插件。

复制某个模板到仓库外的开发目录，使用属于自己的命名空间并修改 metadata；本地导入调试仍要经过宿主完整校验。`build/build-all` 只输出开发包和逐包 SHA-256，不写公开目录：

```powershell
python -m unittest discover -s tests -v
python tools/pack.py build-all --out dist
python tools/pack.py inspect dist/dev.c17.clock-seconds-1.zip
python tools/pack.py catalog index.json
```

同一源码的 ZIP 按名称排序，固定时间与权限；相同 Python/zlib 工具链可以逐字节重建。不同压缩库版本的压缩字节可能不同，发布者应固定构建环境；相同 ZIP 的 size/SHA-256 必须一致。

## 显式准备正式包

完成源码、版权和目标 ROM 审查后，开发目录必须含 LICENSE、README.md、plugin.json、main.js 与真实作者。README 应列出来源、许可、最低宿主/API、适配版本、限制、撤销方法和变更记录。

使用 `action/status` 表单或 `C17.onAction/publishStatus` 时，最低宿主须写为 2.1.0_beta.008 / code206 或更高；不能仅写 API 2，因为 code205 不接受这两类控件。code210 起可在 manifest 与真实描述中一致声明整数 `minimumHost`、所需 `features`、`usage/scopes` 及 API 2 `nativeSettings`；这些新字段不放进旧目录。控件 `section/group` 也需要识别新契约的宿主。旧宿主继续使用不含新字段/控件的兼容包，清楚标注适用范围；普通业务更新只增加插件自己的版本，不应无故要求同步升级宿主。

发布前核对详情三段：使用说明与声明作用域（实际勾选无法读取时为未知）、完整功能与设置、管理。当前 runtime 与系统规则只在 SystemUI 执行；框架勾选其他应用不会让插件自动拥有该应用的执行引擎。原生设置 DEX/JAR 必须实际随包提供，固定 View/异步状态桥接 ABI 见 [SDK](SDK.zh-CN.md#原生设置入口)，仅列出字段或通过 schema 不等于实际界面验收。

```powershell
python tools/pack.py lint D:/my-plugin
python tools/pack.py publish D:/my-plugin --repository .
```

`publish` 拒绝直接发布本仓库 templates，要求正式包 LICENSE/README/作者，生成 `packages/<id>-<versionCode>.zip` 与校验和，更新本地根 `index.json`、`SHA256SUMS`。已有相同 ID 的升级需要更大的 versionCode；同版本只允许逐字节相同的内容，不能偷偷替换。该命令不会创建远端、提交、推送或建立 Release。

索引 `file` 使用相对于索引的 `packages/...zip`，拒绝绝对地址、路径穿越、反斜杠、查询和片段；最多 128 项。每项记录真实字节数和小写 SHA-256，索引保留 README/CHANGELOG 链接。发布前请再核对目录和包内容，由维护者通过审查后提交。

发布目录必须与实际包一致：`id/name/version/versionCode/apiVersion` 逐项一致，目录非空白作者与包内作者一致，`capabilities` 按集合完全一致。`description` 仅用于展示，当前客户端没有按描述做一致性比较，但发布者仍需真实说明用途。目录 capabilities 省略时等价于 `[]`；包有 settings/systemui 能力而目录漏报、少报或误报时，客户端会拒绝导入，哈希正确也不会放行。保留工具从同一源码元数据生成的完整能力声明，不要手工删去或弱化它；更新能力时应增加插件版本，重新生成包、哈希和目录。包 manifest 可选字段的省略规则不代表在线目录可以省略包实际使用的能力。

## 仓库与撤回

公开仓库是 [SANWU5/c17-plugins](https://github.com/SANWU5/c17-plugins)，默认索引 `index.json`，README 和 CHANGELOG 可供宿主浏览。宿主下载后仍需 preview、确认安装和主动启用，不会因目录收录而自动运行。

发现严重问题时先撤回相应目录条目，并在允许披露的范围内记录原因；撤回不会远程移除已安装副本。修复版本增加 versionCode，更新安装默认停用。不要提交 APK、主程序私有源码、签名、账号凭据、OEM 资源或设备资料。完整规范、贡献和安全流程见仓库根文档。
