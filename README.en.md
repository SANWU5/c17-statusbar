# C17 Status Bar 1.14.1

Author: aiingjie · Package: `dev.puitheme.iosstatusbar` · versionCode: 29 · LibXposed API 101

[中文](README.md) · [Repository](https://github.com/SANWU5/c17-statusbar) · [Official release](https://github.com/SANWU5/c17-statusbar/releases/tag/v1.14.1)

## This release

- Compact category entries with consistent line icons and a two-column layout on wider screens.
- Consistent switches, numeric inputs, setting rows and spacing. Independent feature sections retain their expansion state.
- Grouped HSB color controls with clearer cards and safer spacing.
- A settings UI update that preserves existing status bar configuration.

## Features

Independent controls for network speed, cellular signal, Wi-Fi, network labels, time, fonts, battery, carrier labels and tile paging. Position, size, weight, spacing and color controls support two-decimal input and a timed confirmation for values outside the recommended slider range.

Notification, control-center and lock-screen carrier labels have separate configurations. Battery percentage and a cut-out charging bolt transition inside the battery. Shape, text and bolt colors are independent. Font options include system, bundled PingFang and a user-selected font.

Tile paging retains native motion with configurable edge fading and separate portrait/landscape switches. Global active tile fill supports light/dark colors, opacity and gradients while retaining ColorOS glass highlights. JSON configuration import/export, optional diagnostic logs and GitHub update checks are available.

## Installation and updates

Install the same-signed APK over the existing version, enable the module in LSPosed and select SystemUI as its scope. Restart SystemUI after a module code update. Normal setting changes notify the scope immediately.

The settings app checks [official GitHub releases](https://github.com/SANWU5/c17-statusbar/releases/latest); installation remains a user action. An APK signed with a different key cannot replace the existing installation.

Adaptation currently targets OnePlus/Oplus SystemUI. Other ROM structures may differ. Large positions or sizes can overlap other elements and require device evaluation.

## Build and verification

Android Gradle Plugin 8.7.3, compileSdk 35, minSdk 26, targetSdk 35, Java 8, and compile-only `io.github.libxposed:api:101.0.0`.

Resource, production Java, DEX and independent source compilation were completed; APK signature, alignment and archive contents were checked. **No check suites or device UI verification were performed in this release cycle.** Previous results are not counted as results for this version. See [verification record](VALIDATION.md).

The source archive includes production code, resources, public documentation and reusable check sources. The desktop JSON runtime is excluded from the APK. Private screenshots, settings, logs, original system APKs and signing keys are excluded from the source archive.

## License and resources

Module source is licensed under **GNU GPLv3 (GPL-3.0-only)**; see [LICENSE](LICENSE) and [copyright notice](COPYRIGHT.md). Distribution of modified versions must meet GPLv3's corresponding-source, copyright and license requirements.

The APK includes the complete GPLv3 text, available offline through the app's license entry.

Third-party resources retain their own terms. See [third-party notices](THIRD_PARTY_NOTICES.md), [font sources](FONT.md) and [PUI battery evidence](docs/PUI-battery-evidence.md).
