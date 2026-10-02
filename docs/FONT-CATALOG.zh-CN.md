# 开源可变字体库

字体库提供10种成熟的不同字体设计，不代表全网热门排名。Noto 与 Adobe 思源属于同源设计，因此不重复作为不同款计数。每款均从官方 Google Fonts GitHub 分发仓库取得，原项目入口、完整 OFL 1.1 许可、固定下载版本与 SHA256 保存在目录元数据中。

| 展示名 | 目录 ID | 实际 `wght` 范围 | 中文覆盖 | 下载大小 |
| --- | --- | --- | --- | --- |
| Noto Sans SC · 思源黑体 | notosanssc | 100–900 | 字体自身支持简体中文 | 17,772,300字节 |
| Noto Serif SC · 思源宋体 | notoserifsc | 200–900 | 字体自身支持简体中文 | 25,125,512字节 |
| Inter | inter | 100–900 | 拉丁字母、数字；中文使用系统字体 | 876,576字节 |
| Roboto | roboto | 100–900 | 拉丁字母、数字；中文使用系统字体 | 488,584字节 |
| Montserrat | montserrat | 100–900 | 拉丁字母、数字；中文使用系统字体 | 744,936字节 |
| Manrope | manrope | 200–800 | 拉丁字母、数字；中文使用系统字体 | 164,700字节 |
| Open Sans | opensans | 300–800 | 拉丁字母、数字；中文使用系统字体 | 532,636字节 |
| JetBrains Mono | jetbrainsmono | 100–800 | 拉丁字母、数字；中文使用系统字体 | 187,208字节 |
| Nunito | nunito | 200–1000 | 拉丁字母、数字；中文使用系统字体 | 276,932字节 |
| Oswald | oswald | 200–700 | 拉丁字母、数字；中文使用系统字体 | 172,088字节 |

下载源固定于 [google/fonts 提交 9710da1](https://github.com/google/fonts/tree/9710da1eacb3be272583c3224dcb70f9da6eadbb)。Noto 的原项目为 [notofonts/noto-cjk](https://github.com/notofonts/noto-cjk)；其他原项目分别为 [Inter](https://github.com/rsms/inter)、[Roboto](https://github.com/googlefonts/roboto-classic)、[Montserrat](https://github.com/JulietaUla/Montserrat)、[Manrope](https://github.com/googlefonts/manrope)、[Open Sans](https://github.com/googlefonts/opensans)、[JetBrains Mono](https://github.com/JetBrains/JetBrainsMono)、[Nunito](https://github.com/googlefonts/nunito)、[Oswald](https://github.com/googlefonts/OswaldFont)。全部10个实际文件已读取 `fvar` 核对字重轴范围；不能以多个静态字重或合成粗体代替可变字体。

APK只加入约55KB（未压缩）的目录元数据和完整许可，不打包这10个字体文件。首次选择时按需下载，缓存位于应用的设备保护私有目录 `files/fonts/catalog/`，已下载字体可离线复用。每次复用前仍校验精确大小、SHA256和真实字重轴，选中后通过现有字体导入事务保存为 `files/fonts/custom.font`；保持中文系统回退。

下载仅允许固定提交的官方 HTTPS 地址，不跟随重定向。单文件上限30MiB，连接与单次读取超时各12秒、整体下载预算180秒；支持取消和进度回调。普通失败或取消删除临时文件，进程意外退出留下的旧下载片段在后续选择时清理。字体缓存预算64MiB，按使用时间淘汰，保留当前正在准备的文件。缓存和字体随正常卸载应用数据清除，不进入配置JSON、云备份或设备迁移。

字重配置的末端物理范围为1–1000；已选字体库文件还会限制到它自身的 `wght` 范围。例如 Oswald 请求1000时实际使用700，Nunito可使用1000。原生时钟字体读取自身轴范围，避免假定所有字体都支持100–900。选用一个字体不会自动开启全局字体主开关；下载失败、取消或提交失败保留原字体和保存配置。

## 主界面接入

`FontCatalog.entries()` 返回不可变列表。`Entry` 提供 `id`、`displayName`、`coverage`、`style`、`bytes`、`minWeight`、`maxWeight`、`sourceUrl`、`licenseName`、`licenseUrl`、`licenseAsset` 和 `sha256`。`FontCatalog.forRevision(font_revision)` 可找到已安装的目录字体。完整离线许可位于 `assets/fonts/catalog/licenses/`。

在主界面自己的工作线程调用：

```java
FontDownloadRepository.Cancellation cancellation = new FontDownloadRepository.Cancellation();
FontRepository.PreparedFont prepared = FontDownloadRepository.prepare(
    context, entry,
    (received, total) -> { /* 在调用者自己的主线程更新界面 */ },
    cancellation
);
```

`prepare` 不改设置。调用者回到主线程后用 `prepared.commit(canCommit, saveOptions, restoreOptions)`，复用现有恢复逻辑，并在 `close()` / `use` 中释放暂存文件。只提交 `font_mode=custom`、`font_name=entry.displayName`、`font_revision=prepared.revision`；不改 `font_enabled`。Activity失效或用户取消时调用 `cancellation.cancel()`，提交前后继续检查生命周期和编辑权限。`FontRepository.releaseRuntime()` 用于卸载时释放SystemUI持有的字体及模块Context引用。

当前仅完成官方文件/许可/fvar核对、源码检查和桌面回归；不将其视作手机字体显示、联网下载或所有ROM兼容性实测。
