# 界面翻译工具（App291 起）

主模块和两个自带插件的界面文字以**简体中文为原文**，按整条原文查表翻译成另外 29 种语言（`C17Language.OPTIONS`）。

- App：`app/src/main/assets/i18n/<语言>.json`，`C17Text.tr()` 在显示时查表。缺条目时非中文语言回退英文，再回退原文；某种语言整张表都没有时同样显示英文。
- 插件：`plugins/<插件>/i18n/<语言>.json`，随插件包打包；宿主 `CustomPluginLocalization` 在显示插件文字时查插件自己的表。
- 只在**显示的地方**翻译：程序里比较、保存、导出的仍是原文，所以 `message.contains("未获得 Root")` 这类逻辑不受影响。

## 代码怎么写

- 封装组件（`UiRow`、`UiSwitchRow`、对话框、`C17TextButton`、顶栏、导航、`toast`）和同包的 `Text(...)` 都会自动翻译传入的整条文字，页面里直接写中文原文即可。
- 带变量的文字用模板：`trf("删除 %s", name)`、`trf("共 %1$s 个应用 · 第 %2$s / %3$s 页", a, b, c)`。不要写 `"删除 $name"`，Kotlin 字符串模板无法查表。`trf` 也会翻译作为参数传入的字符串（如「导入」「导出」）。
- 绘制在 Canvas 上或不经过上述组件的文字，自己调用 `tr(...)`。
- 用户要输入的记号（`{周}`、`（天气）`、`M月d日`）、保存或比较用的值不要翻译。

## 新增或修改文案后

```bash
python tools/i18n/extract.py app                                   # 重新抽取 app 文案 -> tools/i18n/app-source.json
python tools/i18n/extract.py plugin plugins/liquid-glass           # 插件 -> plugins/<插件>/i18n-source.json
python tools/i18n/prepare.py en --size 500                         # 只列出英文还缺的条目 -> build/i18n-work/en/in-NN.json
```

运行时拼出的设置目录标题和插件表单文字先用 `I18nDump`（`tools/i18n/I18nDump.java`，编译后对照 release 类运行）导出到 `build/c17-dev/i18n/`，`extract.py` 会合并进来。

翻译交给译者（这次用 Haiku 子代理，按 `TRANSLATING.md` 工作），每块写 `out-NN.json`，并用 `validate_chunk.py` 自查。先译英文，其他语言用 `--reference en` 生成带英文参照的分块：

```bash
python tools/i18n/prepare.py ja --size 500 --reference en --no-context
python tools/i18n/validate_chunk.py build/i18n-work/ja/in-01.json build/i18n-work/ja/out-01.json ja
python tools/i18n/merge.py ja                                      # 合并进 app 与插件的翻译表
```

`prepare.py` 每开一轮会把上一轮的 `in/out` 归档到 `round-N/`，只列出表里还缺的条目，所以补译和重翻都是同一个流程。`merge.py` 拒收有问题的条目（写入 `build/i18n-work/<语言>-problems.txt`），并去掉原文已不存在的旧条目；只合并了一部分块也可以，缺的条目由英文兜底，一条都没有的表不写文件。

新增一种语言：在 `C17Language.OPTIONS` 和 `i18n_common.LANGUAGES` 里登记，翻译完成前把它放进 `AppTranslationsCheck.IN_PROGRESS`（表可以不全），三张表都齐了再拿掉。

## 检查

- `validate_chunk.py`：条目齐全；占位符、`{…}` 记号、格式记号、换行与原文一致；不残留中文（原文里括号、引号中的记号与名字除外）；不含别的语言的文字体系（只看字母，标点不算：孟加拉文句末的「।」在 Unicode 里属于天城文区）。
- `merge.py`：同一块里与同一字母体系的另一语言大量相同（≥10 条）视为文件串了，整块相关条目拒收。印尼文和马来文本来就有很多相同说法，这一对不互相比较。
- `AppTranslationsCheck`（宿主检查）：29 种翻译语言的表都存在且覆盖全部原文（`IN_PROGRESS` 里的语言除外，目前为空），没有过期条目，占位符与记号一致。
- `CustomPluginLocalizationCheck`：插件翻译表的文件名、格式、回退与查表。

## 这次踩过的坑

- 多个译者同时工作时**不能共用临时文件夹**：草稿目录是共享的，`merge.py`、`p09b.json` 这类通用文件名互相覆盖，日文和韩文串了一块。现在提示词要求各自用 `build/i18n-work/<语言>/tmp-<块>/`，校验和合并也会拦下串了的文件。
- 校验规则曾把「0% de」「Android-」「przód-」误判为占位符或日期记号；规则已改为只认真正的 printf 占位符，日期记号前面不能是字母。
- Windows 下用 heredoc 写含 `\\0`、`\\n` 的补丁脚本会被改坏（写出 NUL 字节），补丁脚本一律用文件写入。
- 孟加拉文的「।」曾被当成天城文字母，译者只好全改成英文句点，有一块因此被整块丢弃重翻；规则修正后句点已改回「।」。印尼文与马来文相同的 35 条曾被当成串文件拒收，已排除这一对。
- 同时开 20 个代理连续跑几个小时会碰到账号的 5 小时用量上限，在跑的代理全部中断。恢复时先对已写出的 `out-NN.json` 逐个跑 `validate_chunk.py`，通过的直接合并，删掉被中断代理的临时目录，只给缺的块重新派。
