# API 2 统一详情页模板

以宿主 code261 为基线。此示例真正读写插件独立的配置记录，不修改系统外观，不进入公开下载目录。

1. 复制目录，修改 `id/name/author`，同步 `plugin.json` 与 `main.js` 元数据。
2. 将记录功能替换为自己的业务；每个可调参数都声明真实控件，保持升级后的 ID 和 key 稳定。
3. `python tools/pack.py standard templates/standard-runtime` 检查统一页面契约。
4. `python tools/pack.py build templates/standard-runtime --out dist` 生成开发 ZIP。
5. 在 C17 导入，检查说明页的一键申请作用域、一键重启作用域；启用后验证设置、管理、停用及升级保留。

说明/设置/管理、启用总开关、作用域申请/重启、恢复默认、更新、删除均由宿主提供。不要在 `ui` 重复创建这些控件。

需要 DEX 时，在包内添加自己的编译产物并声明 `runtime.dex/runtime.java` 等实际依赖；运行入口加载一次，状态变更只更新既有实例，清理时释放资源。不可借用“更好的玻璃”或其他插件的内部类、私有设置键来伪造通用 API。详见 [开发规范](../../docs/DETAIL-UI.zh-CN.md) 与 [SDK](../../docs/SDK.zh-CN.md)。

`i18n/en.json` 演示界面翻译：键是本模板写的中文原文，宿主 code291 起在英文界面下显示译文，运行脚本发布的固定状态文字和动作提示也会被翻译。改动原文时同步更新翻译表，详见 [SDK · 界面翻译](../../docs/SDK.zh-CN.md#界面翻译code291)。

MIT；模板和 SDK 不包含业务插件源码、原厂资源或签名材料。
