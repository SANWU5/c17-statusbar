# Status C17 · 2.1.0

A SystemUI customization module for ColorOS 17, primarily adapted using OnePlus 13. Customize the status bar, notifications, Control Center, Fluid Cloud and lock screen, with a choice of Miuix, COUI or Material for the settings app, whose interface is available in 30 languages. Uses LibXposed API 101; package name: `dev.puitheme.iosstatusbar`.

**Stable release 2.1.0.** Compared with 1.8.3 it adds extension plugins, a 30-language interface, System UI crash rescue and the privacy capsule, and fixes a set of status bar issues; see the [2.1.0 release notes](docs/RELEASE-2.1.0.zh-CN.md) (Chinese). Extension plugins can be written against the [plugin specification](https://github.com/SANWU5/c17-plugins).

[Download APK](https://github.com/SANWU5/c17-statusbar/releases) · [中文](README.md) · [Changelog](CHANGELOG.md) · [Icon-pack specification and examples](https://github.com/SANWU5/c17-icon-packs) · [Plugin specification and templates](https://github.com/SANWU5/c17-plugins)

## Features

| Area | Available settings |
| --- | --- |
| Status-bar order and spacing | Place the clock, notification icons, carrier, network speed, Wi-Fi, cellular, battery and individual system indicators on either side or follow the system. Drag, move between regions, undo and save a draft. Physical left/right spacing is independent; Wi-Fi and network labels can share a position. Ordering remains experimental. |
| Clock and fonts | Single- or double-row clocks, format presets and preview, tabular digits, digit spacing and colon alignment. Apply system, PingFang, imported or downloadable fonts to seven independent text scopes while preserving each feature's size and weight. |
| Network, signal and battery | Customize 16 network labels, native badges, primary-signal and stacked layouts, network-speed updates and appearance, and upload/download arrows. Retains system and existing PUI battery styles, following the system percentage mode. |
| Icon management | Apply a suite or select individual targets/states across system indicators, network, battery, supported quick toggles, brightness/volume, footer and supported static media/device icons. Adjust size from 25% to 200%, background-dependent and per-state colors, opacity and named pack palettes. Preview and manage libraries; supported toggles can use pack animations. |
| Notification clock | Separate portrait/landscape settings, native fonts, digit-spacing and time-animation controls, lock-screen-like materials, glass, background inside glyphs, masks, outlines and lighting. Portrait supports rebound blur and app-launch actions. |
| Text components and weather | Two portrait side components with independent appearance, position, row swapping and actions. Weather, date, battery, signal and network presets support conditional templates. Weather reads the system's cached located city without relying on lock-screen widgets. |
| Notification icons and stacks | Follow the launcher's current icon pack and system icon shape, with per-app replacements taking priority. Same-app mini stacks and whole-list stacking are independent; hide counts or covered rear cards while retaining native gestures and clearing. |
| Control Center and media | Full-fill 2×1 tiles, header-button visibility, tile colors and gradient ranges, cover-derived or custom media backgrounds and transitions. Unified corners cover tiles, press layers, detail buttons and brightness/volume sliders. |
| Expanded Fluid Cloud | Solid or native-gradient fills, opacity, media-color following and transitions, with 13 independently selectable business categories. |
| Camera-area capsule | Backgrounds, blur, borders, animated border colors, inward glow, cover/corner settings, rebound and live music spectrum. Optional persistence retains manual dismissal and normal task cleanup. |
| Lock screen and AOD | Separate fills, masks, brightening and media colors, supported white-glass notification backgrounds, bottom-capsule colors, top wallpaper blur and lock-icon hiding. |
| Shade wallpaper | Separate portrait/landscape images for notification, control and classic panels, including cropping, blur, brightness and large-clock-region effects. Experimental and not fully adapted. |
| Cache and optional optimization | Built-in font/icon resource caching. An optional Root companion performs on-demand optimization of SystemUI and recognized Control Center plugins, reuses valid results and provides manual/automatic rules, records and a web control panel. |
| Settings app and thanks | COUI/Miuix, 10 color schemes, Material 3/Catppuccin, dark-background adjustment, text scaling, scroll fading and optional liquid-glass navigation on Android 13+. Includes a thanks wall and themed Easter eggs. |
| Configuration and diagnostics | Import, export or share selected feature pages without changing unselected pages. Search and reset by page. A dedicated diagnostics page shows runtime status, findings and searchable/filterable logs, with report export and sharing. |
| Display and animation fixes | Improvements to network visibility, initial measurement, shade clipping, capsule spacing, seconds updates, speed-text clipping, font caches and large-clock measurement while retaining native interaction and animations. |

### Icons and fonts

Open **Other → Icon management** to import **v3 ZIP or unencrypted, single-volume RAR4/RAR5**, or fetch a pack from GitHub. Static assets support transparent PNG and restricted VectorDrawable XML. External animations are limited to supported quick toggles. Preview the selected scope before applying; missing or unsupported states retain native artwork. [Pack specification](https://github.com/SANWU5/c17-icon-packs) · [Color palettes](docs/ICON-COLOR-PRESETS.zh-CN.md)

The public build includes the original **C17 example library**, without a bundled PUI icon suite. You can import v3 packs you are entitled to use. PUI compatibility mappings and the existing PUI battery style remain available. Separate partial iOS27 test packs are not a built-in library or a complete iPhone interface.

Font settings also live under **Other**. The seven scopes are status-bar clock, shade clock, network-type text, network speed, shade replacement text, lock-screen replacement text, and large notification clock with supporting text. They do not include the system lock-screen main clock or ordinary notification bodies. The [font catalog](docs/FONT-CATALOG.zh-CN.md) lists 10 downloadable variable-font designs and their licenses; local imports support TTF, OTF and TTC.

## Installation and upgrade

1. Download the APK from [GitHub Releases](https://github.com/SANWU5/c17-statusbar/releases). Export settings you want to retain before updating.
2. Install over the existing app with the same signer, then enable the module and **SystemUI** scope in a compatible LSPosed manager.
3. Restart SystemUI or the phone to load the new code. Ordinary setting changes notify SystemUI without requiring a restart each time.

For older signing identities, see the [upgrade notes](docs/PARAMETER-UPGRADE-1.6.0.zh-CN.md). Adaptation is primarily based on ColorOS 17 devices; the app's minimum Android version does not imply support for every ROM.

- Legacy v1/v2 icon packs, library-priority layers and theme-ZIP import are retired. Select sources again in the new icon manager; other module settings are retained.
- Configuration files carry settings, not fonts, icons or wallpaper images. Import those resources separately on another device. Settings-app colors and text scale are separate from module configuration.
- Updating the APK does not update an installed Root companion. Update it separately under **About → Companion optimization module**. It is optional for ordinary appearance customization.
- Status-bar ordering and shade wallpaper remain experimental. Intermittent movement, disappearing icons, collapse transitions and native-material differences still require scenario-specific device verification.

## Settings and maintenance

Settings are grouped into status bar, notifications, Control Center, lock screen and other features, with global search and page-specific reset. Sliders offer recommended ranges. Valid manual values within the range save directly; values outside the range enter a 20-second confirmation trial and revert on cancellation or timeout. Unified corners have a hard limit of 0–30dp.

Safety mode on the home page pauses modifications while retaining settings. **About → Configuration management** provides backup, sharing, import and confirmed deletion; **About → Diagnostics** provides logs and reports. Hide the launcher icon at the bottom of About, then reopen and restore it through a manager supporting the module-settings entry. The launcher name defaults to Status C17; the old name can be restored in appearance settings.

## Author and support

**aiingjie** · GitHub [sanwu5](https://github.com/sanwu5) · Coolapk **konwo** · Feedback, collaboration and donations: **QQ 2726344450**.

The module and all features are free. Donations are voluntary and do not affect access. Please do not buy paid resales. Donation images appear in the app only after you choose to open them.

[WeChat](docs/donation/wechat.png) · [Alipay](docs/donation/alipay.jpg)

## Building and licensing

Use JDK 17, the Gradle wrapper and Android SDK 37. Java targets Java 8 bytecode and Kotlin targets JVM 11; minSdk 26/targetSdk 35. See the [Windows build guide](docs/WINDOWS-BUILD.zh-CN.md) and [Compose build guide](docs/COMPOSE-BUILD.zh-CN.md). Build checks do not establish complete device-level visual, frame-rate or battery validation.

Software is licensed under [GPL-3.0-only](LICENSE); see [COPYRIGHT.md](COPYRIGHT.md). Fonts, existing PUI battery resources and third-party components retain their own rights and attribution, documented in [third-party notices](THIRD_PARTY_NOTICES.md) and [PingFang sources](FONT.md). Signing keys, personal settings, raw device materials and private development records are excluded from public source distributions.
