# Translating Status C17

Status C17 is a root module for ColorOS phones (Android). It customizes the status bar, the pull-down panels
(Control Center and the notification shade), the lock screen and more. The app is its settings screen. Readers are
phone enthusiasts; the tone is that of a system settings app: short, plain, precise, no marketing.

The source language is **Simplified Chinese**. Every string is looked up by its exact Chinese text, so a translation
replaces one whole string. Some strings are fragments of a sentence the program joins (they start or end with a space,
a dot or a colon); translate them as fragments and keep that leading or trailing piece.

## Files

- `build/i18n-work/<language>/in-NN.json`: `{"id": {"zh": "...", "ctx": "...", "en": "..."}}`.
  `zh` is the text to translate. `ctx` says where it appears (a source file, a settings page `catalog:<page>:<field>`,
  or a plugin); it is a hint, never translate it. `en` (when present) is the reviewed English translation: use it to
  understand the meaning and to keep terms consistent, but translate the meaning of the Chinese.
- You write `build/i18n-work/<language>/out-NN.json`: `{"id": "translation", ...}`, every id of the input, nothing else.
  Valid JSON, UTF-8, `\n` for line breaks inside a string, `\"` for a quote.
- Check every file you write: `python tools/i18n/validate_chunk.py <in-NN.json> <out-NN.json> <language>`.
  Fix every problem it prints and check again until it prints `OK`.
- `app/src/main/res/<values-xx>/strings.xml` holds the welcome-page translations already reviewed for your language.
  Reuse its terms and tone (for example how it says Control Center, plugin, safe mode, About → Language).

## Must stay exactly as in the source

- printf placeholders: `%s`, `%d`, `%1$s`, `%2$d`, `%.1f`, `%%`. Reorder them only by keeping their numbers.
- Anything in curly braces: `{周}`, `{text}`, `{电量}`, `{0}`. These are tokens the user types into format fields.
- Format tokens in full-width parentheses: `（天气）`, `（日期）`, `（电池:{电量}%）`, `（网络:{类型}）`; date patterns
  such as `M月d日`, `yyyy年`, `HH:mm`. They are syntax the user types, so they stay Chinese even inside a translated sentence.
- The number of line breaks and where they fall.
- Numbers, units (`dp`, `%`, `ms`, `MB`, `°`), symbols (`·`, `→`, `×`, `–`, `✓`), emoji, URLs, file and package names,
  `owner/repo`, code words, version names (`code290`, `v3`).
- Names: C17, Status C17, ColorOS, HyperOS, OPPO, OnePlus, Android, iOS, LSPosed, Xposed, KernelSU, Magisk, APatch,
  Miuix, COUI, Material, GitHub, Telegram, Ethereum, ERC20, Tron, TRC20, Wi-Fi, 5G, 4G, AOD, Lottie, Vulkan, GPL-3.0.

## Style

- Labels and titles: short, sentence case in Latin scripts, no final period. Full sentences keep their punctuation.
- Use the normal quotation marks of the language when the Chinese uses “ ” or 「 」 around the name of a setting or
  button, and translate that name the same way the setting itself is translated.
- No explanations, notes or alternatives in the output. Do not leave Chinese in the translation except the tokens above.
- One term per concept across all your files. Keep a short term list for yourself and reuse it.

## Terms

| Chinese | English | Notes |
| --- | --- | --- |
| 状态栏 | status bar | |
| 控制中心 | Control Center | ColorOS name of the quick settings page |
| 通知中心 / 通知栏 / 通知页 | notification center / notification shade / notification page | the notification half of the pull-down |
| 下拉面板 / 下拉页 | pull-down panel | |
| 锁屏 | lock screen | |
| 息屏 / AOD | AOD (Always-On Display) | |
| 流体云 | Fluid Cloud | OPPO's live-activity capsules and cards |
| 磁贴 | tile | quick settings tile |
| 胶囊 | capsule | |
| 大时钟 / 双排时钟 / 显秒 | big clock / two-row clock / show seconds | |
| 堆叠通知 / 小堆叠 | stacked notifications / mini stack | |
| 媒体卡片 / 音乐卡片 | media card / music card | |
| 原生 / 原厂 | stock (system's own) | "恢复原生" = restore stock |
| 系统界面 | System UI | the com.android.systemui process |
| 重启系统界面 | restart System UI | |
| 安全模式 | safe mode | |
| 系统UI防炸 | System UI crash rescue | |
| 附属模块 | companion module | the root (KernelSU/Magisk) part |
| 扩展插件 / 插件 | extension plugin / plugin | |
| 作用域 | scope | LSPosed scope |
| 预设 | preset | |
| 自定义 | custom | |
| 跟随系统 | follow system | |
| 单独调节 | adjust separately | |
| 浅色背景 / 深色背景 | light background / dark background | |
| 透明度 | opacity | 100% = fully visible unless the text says otherwise |
| 模糊 | blur | |
| 遮罩 | scrim | the tint layer over a blurred background |
| 高光 | highlight | |
| 黑线 | dark line | the thin dark edge line of the glass |
| 折射 | refraction | |
| 乳白 | frost | |
| 鲜艳度 | vibrancy | |
| 液态玻璃 | liquid glass | |
| 内光 / 内阴影 | inner glow / inner shadow | |
| 描边 | outline | |
| 圆角 | corner radius | |
| 字重 / 字距 | font weight / letter spacing | |
| 边距 / 偏移 | margin / offset | |
| 图标库 / 图标包 / 套装 | icon library / icon pack / set | |
| 网速 | network speed | |
| 运营商 | carrier | |
| 制式 | network type | 5G, 4G, … label |
| 电量 | battery level | |
| 农历 / 干支 | lunar calendar / sexagenary year | |
| 鸣谢墙 | Thanks Wall | |
| 酷安 | Coolapk | |
| 更好的玻璃 | Better Glass | plugin name; translate the meaning |
| 更好的编辑控制中心 | Better Control Center Editor | plugin name; translate the meaning |
