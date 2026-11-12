# 2.1.0 发布验证

日期：2026-11-12。正式身份：**2.1.0 / versionCode 293**；Runtime 为 `c17-runtime-20261112-2.1.0-code293`。Root 附属模块为 2.3/code14。

| 验证 | 结果 |
| --- | --- |
| Gradle | Debug Java 编译、Release 打包、lintVitalRelease 通过 |
| Java hooks 字节码 | 2584 个类，均为 major 52 |
| 桌面检查 | 共 319 组。首次全量 318 组通过；IconManagementRuntimeCheck 因仍会读取公开版不附带的 PUI 图标包而失败，改为只检查实际内置的图库后，该组 902 项与 PublicIconPackageCheck 63 项重跑通过 |
| 插件相关检查 | 公开仓库不含作者的扩展插件；CustomPluginContractCheck、CustomPluginUnboundedInputCheck、AppTranslationsCheck 中依赖插件源码的部分在缺少插件时跳过，其余照常运行 |
| 签名 | APK Signature Scheme v2 验证通过，证书与公开 1.8.3 一致 |
| APK 对齐 | zipalign 16KB 核对通过 |
| 安装包内容 | 版本与 Runtime 一致；含原创C17示例库，不含整套PUI图标ZIP，不含扩展插件 |

APK：`c17-statusbar-2.1.0.apk`，34,919,925 字节。

SHA-256：`a826ed06e91ee7791aab1b9934918e4bdfeb017a2af0586a4de26d1781d08a2b`

现用签名证书 SHA-256：`fa5825bd1a0ed867432f71c57bf40ce2445d14f4c961a65edb7157ae040a9966`

构建方法见 [Windows构建](docs/WINDOWS-BUILD.zh-CN.md)，检查入口见 [validation/README.md](validation/README.md)。构建产物、完整日志、签名配置及用户自备素材留在本机，不加入公开源码。

真机：与本包代码相同、另含PUI图标包的开发构建已在 OnePlus 13（ColorOS 17）上同签名覆盖安装，系统界面未重启、插件与设置不变，打开 App 显示更新后的欢迎页；公开包本身未单独装机。桌面检查与签名核对不代表所有机型、系统版本及动画观感均已真机验收；已知问题见 [更新说明](docs/RELEASE-2.1.0.zh-CN.md)。
