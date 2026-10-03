# C17 Status Bar · 1.7.2

A SystemUI customization module for ColorOS 17, primarily developed against OnePlus 13. Uses LibXposed API 101; package: `dev.puitheme.iosstatusbar`.

**1.7.2 / versionCode 64.** Get the APK from [GitHub Releases](https://github.com/SANWU5/c17-statusbar/releases). This update fixes cellular positioning and network labels in some single-SIM layouts, and prevents shade clocks from inheriting status-bar position offsets. See the [1.7.2 release notes](docs/RELEASE-1.7.2.zh-CN.md) for changes since 1.7.1 and the [validation record](VALIDATION.md) for actual verification scope.

[中文](README.md) · [GitHub Releases](https://github.com/SANWU5/c17-statusbar/releases) · [Changelog](CHANGELOG.md)

## Features

- **Status bar:** Independent controls for time, battery, Wi-Fi, cellular signal, network labels, notification icons and fonts. Swap Wi-Fi / 4G / 5G with cellular signal. Native network badges have their own font, position, size and weight controls, effective only when the custom network label is off.
- **System hints:** Set X/Y, maximum count and spacing for Bluetooth, location, alarm and similar native hints. Drag the named entries to set priority. Only hints permitted by the system are selected; network and battery areas are excluded.
- **Speed and activity arrows:** Preserve the native speed number and unit. Refresh intervals use milliseconds, with a recommended range of 1–500 ms. Cellular activity arrows are hidden by default but can be enabled independently, with position, size and light/dark color controls.
- **Notification shade:** Portrait and landscape large clocks have independent settings. Landscape places the clock above notifications, offers notification-width controls and preserves native notification text and icon sizes. Landscape Clear All has separate position and color controls. Whole-list stacking uses native scrolling and scaling, with 1–5 complete cards recommended. Landscape requires an additional opt-in, off by default. Same-group stacking is hidden and forcibly disabled at runtime.
- **Control center and materials:** Unified corners, 1×1 tile and recognized device-card icon sizing, tile fill and media-cover backgrounds. C17 highlight removal has separate notification/control-center scopes, light/dark acrylic colors and optional uniform notification colors, while retaining custom tile fills and media backgrounds. New artwork supplies a sampled solid color before prepared blurred artwork fades in; unchanged results reuse caches.
- **Lock screen:** Customize year/month/day and weekday formatting; hide only the small lock's visual icon while retaining unlock and fingerprint interaction. Date rendering still requires device verification in this final build.

The [font catalog](docs/FONT-CATALOG.zh-CN.md) contains 10 variable-font designs with source and license details. Some use system Chinese fallback. Fonts are downloaded on demand; importing a custom font is also supported.

## Settings and maintenance

Home, Configuration and About use a regular bottom bar covering the system gesture area. Settings are organized into status bar, notification shade, control center, lock screen and other categories. Live previews are removed. **Hide launcher icon is at the bottom of Home**; a compatible LSPosed manager's module-settings entry can reopen the app and restore it.

Feature masters default off; explicitly saved settings are retained. Sliders show recommendations. Valid manual values within those ranges save immediately; **only values outside the recommended range start a 20-second confirmation trial**, with cancellation or timeout restoring the prior value. Unified corners have a hard 0–30dp limit. Short sampling intervals, gradients and complex motion increase work; enable them as needed.

Startup reads complete framework snapshots and the app's configuration provider. Incomplete or temporarily unavailable data and failed writes retain the last confirmed configuration and retry; unconfirmed trials are not boot settings. Granted Root permits saving without LSPosed activation. Safe mode pauses modifications without deleting settings.

About provides diagnostic export, log-history clearing and a confirmed reset of all settings. Reset also clears recovery copies and private imported resources, leaving user-exported files intact. With Root granted and a newer project release available, an explicit in-app update downloads and verifies the package, version, signer and file before installation, then removes temporary APKs on completion or failure. Normal uninstall removes app-private data; shared LSPosed logs belong to the framework.

## Installation

Download published APKs from [GitHub Releases](https://github.com/SANWU5/c17-statusbar/releases). Export settings you wish to retain. Replace-install with the same signer, enable the module and SystemUI scope in LSPosed, then restart SystemUI or the phone to load new code. Ordinary parameter changes notify SystemUI without requiring a restart each time.

See the historical [upgrade notes](docs/PARAMETER-UPGRADE-1.6.0.zh-CN.md) for old-signature migration. Android 26/27 old-signature replacement is not guaranteed. Build checks, device observations and support for other ROMs are recorded separately; desktop checks do not establish complete device acceptance.

## Author and donations

Author **aiingjie** · GitHub [sanwu5](https://github.com/sanwu5) · Coolapk **konwo** · Feedback, cooperation and donations: **QQ 2726344450**.

The module and all features are permanently free. Donations are voluntary and unlock nothing; do not pay resellers. First use requires reading the free-use notice and manually entering confirmation. Donation QR codes appear in the app only after tapping their entries.

[WeChat payment QR](docs/donation/wechat.png) · [Alipay payment QR](docs/donation/alipay.jpg)

## Building and licensing

Build with JDK 17, Gradle wrapper, Android SDK 37, AGP 9.4.1, Kotlin/Compose 2.4.20 and Miuix 0.9.4. Java hooks target Java 8 bytecode, Kotlin targets JVM 11, and minSdk/targetSdk are 26/35. See the [Windows Compose build guide](docs/COMPOSE-BUILD.zh-CN.md).

Licensed under [GPL-3.0](LICENSE). Component and font attribution is in [third-party notices](THIRD_PARTY_NOTICES.md). Signing keys, personal settings and logs, phone captures and raw SystemUI files are excluded from the public repository.
